package com.ronsmods.felling;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Felling II: once a trunk is fully down, clear the felled tree's leaves right away.
 *
 * <p>A natural leaf near the trunk is removed if either:
 * <ul>
 *   <li>it belongs to the felled tree: it is a leaf type the felled species grows ({@link #CANOPY}), and it is
 *       strictly nearer (straight line) to the felled trunk or its branches than to any rival log. Ties go
 *       to the standing tree. Only logs that could have grown that leaf compete: species that grow it, or
 *       unknown (modded) logs. This clears the felled canopy where it touches a neighbour's; or</li>
 *   <li>vanilla would decay it once the felled trunk and canopy are gone: it is more than 6 face-to-face
 *       steps through leaf blocks from any remaining {@code #minecraft:logs} block ({@code LeavesBlock}'s
 *       rule). So nothing is left behind to decay slowly.</li>
 * </ul>
 * Player-placed (persistent) leaves are never touched.
 * Each removed leaf drops exactly what natural decay drops ({@code Block.dropResources} with no tool), so
 * sapling, stick and apple chances are the vanilla ones, with no Fortune or Silk Touch and no durability.
 */
final class LeafClearer {
    /** Vanilla leaf range: distance 1..6 is supported, 7 decays. */
    private static final int MAX_SUPPORT = 6;
    /** How far from the felled trunk to look for its leaves (vanilla range plus a margin for already-decaying ones). */
    private static final int SEARCH_REACH = 8;
    /** Hard cap on leaves examined per fell. */
    private static final int MAX_LEAVES = 4096;

    /** Leaves each supported vanilla log species grows. Azalea trees grow on oak logs. */
    private static final Map<Block, Set<Block>> CANOPY = Map.of(
            Blocks.OAK_LOG, Set.of(Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES),
            Blocks.BIRCH_LOG, Set.of(Blocks.BIRCH_LEAVES),
            Blocks.SPRUCE_LOG, Set.of(Blocks.SPRUCE_LEAVES),
            Blocks.JUNGLE_LOG, Set.of(Blocks.JUNGLE_LEAVES),
            Blocks.ACACIA_LOG, Set.of(Blocks.ACACIA_LEAVES),
            Blocks.DARK_OAK_LOG, Set.of(Blocks.DARK_OAK_LEAVES),
            Blocks.CHERRY_LOG, Set.of(Blocks.CHERRY_LEAVES),
            Blocks.MANGROVE_LOG, Set.of(Blocks.MANGROVE_LEAVES));

    private LeafClearer() {}

    /** Remove the felled {@code trunk}'s leaves and any leaves it left unsupported. Returns how many were removed. */
    static int clear(ServerLevel level, ServerPlayer player, Collection<BlockPos> trunk, Block species) {
        BoundingBox trunkBox = BoundingBox.encapsulatingPositions(trunk).orElse(null);
        if (trunkBox == null) {
            return 0;
        }
        BoundingBox searchBox = trunkBox.inflatedBy(SEARCH_REACH);

        Set<BlockPos> candidates = connectedNaturalLeaves(level, trunk, searchBox);
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        // Every other log close enough to matter (a log more than MAX_SUPPORT steps away can neither support nor own a candidate).
        BoundingBox area = BoundingBox.encapsulatingPositions(candidates).orElseThrow().inflatedBy(MAX_SUPPORT + 1);
        List<BlockPos> otherLogs = logsIn(level, area);
        if (otherLogs == null) {
            return 0; // part of the neighbourhood is unloaded; leave everything to natural decay
        }

        Set<Block> canopyTypes = CANOPY.get(species);
        if (canopyTypes == null) {
            canopyTypes = Set.of(); // unknown species: only clear what vanilla would decay
        }
        // Logs that could have grown this canopy's leaves: species that grow them, or unknown (modded,
        // mangrove...) logs. A birch log never grew oak leaves, so it doesn't compete for them.
        List<BlockPos> rivals = new ArrayList<>();
        for (BlockPos log : otherLogs) {
            Set<Block> grows = CANOPY.get(level.getBlockState(log).getBlock());
            if (grows == null || !java.util.Collections.disjoint(grows, canopyTypes)) {
                rivals.add(log);
            }
        }

        // 1. The felled canopy: leaves its species grows that are nearer (straight line) to the felled trunk
        //    than to any rival log. Ties go to the standing tree.
        Set<BlockPos> ours = new HashSet<>();
        for (BlockPos pos : candidates) {
            if (canopyTypes.contains(level.getBlockState(pos).getBlock())
                    && nearestSq(pos, trunk) < nearestSq(pos, rivals)) {
                ours.add(pos);
            }
        }
        // 2. Whatever vanilla would decay once the trunk and its canopy are gone.
        Map<BlockPos, Integer> support = leafDistances(level, otherLogs, area, ours);

        List<BlockPos> doomed = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (ours.contains(pos) || !support.containsKey(pos)) {
                doomed.add(pos);
            }
        }
        // Top-down, so falling items don't pile into leaves that are about to go anyway.
        doomed.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());

        int removed = 0;
        for (BlockPos pos : doomed) {
            BlockState state = level.getBlockState(pos);
            if (!isNaturalLeaf(state) || !level.mayInteract(player, pos)) {
                continue;
            }
            // Let claim / protection mods veto each leaf, as they would a player break.
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) {
                continue;
            }
            // Exactly what LeavesBlock.randomTick does when a leaf decays, plus break particles.
            Block.dropResources(state, level, pos);
            level.destroyBlock(pos, false, player);
            removed++;
        }
        return removed;
    }

    /**
     * Natural leaves connected to the trunk through other natural leaves, within {@code box}. Steps include
     * diagonals, because some canopies (azalea) grow leaves that touch the rest only at an edge or corner;
     * vanilla already treats those as decaying. Whether a leaf is actually removed is decided in
     * {@link #clear} using vanilla face-only leaf distances.
     */
    private static Set<BlockPos> connectedNaturalLeaves(ServerLevel level, Collection<BlockPos> trunk, BoundingBox box) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> trunkSet = new HashSet<>(trunk);
        for (BlockPos log : trunk) {
            for (BlockPos next : neighbours26(log)) {
                if (!trunkSet.contains(next)) {
                    queue.add(next);
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (found.contains(pos) || !box.isInside(pos) || level.isOutsideBuildHeight(pos)) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                return null;
            }
            if (!isNaturalLeaf(level.getBlockState(pos))) {
                continue;
            }
            found.add(pos);
            if (found.size() > MAX_LEAVES) {
                return null;
            }
            queue.addAll(neighbours26(pos));
        }
        return found;
    }

    private static List<BlockPos> neighbours26(BlockPos pos) {
        List<BlockPos> out = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        out.add(pos.offset(dx, dy, dz));
                    }
                }
            }
        }
        return out;
    }

    /** Every {@code #minecraft:logs} block in {@code area}, or null if part of it is unloaded. */
    private static List<BlockPos> logsIn(ServerLevel level, BoundingBox area) {
        List<BlockPos> logs = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.isOutsideBuildHeight(pos)) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                return null;
            }
            if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                logs.add(pos.immutable());
            }
        }
        return logs;
    }

    /**
     * Vanilla leaf distance from {@code sources}: face-to-face steps through any leaf block (placed leaves
     * pass support on in vanilla too), up to {@link #MAX_SUPPORT}, treating {@code removed} as already gone.
     * Leaves further away are absent.
     */
    private static Map<BlockPos, Integer> leafDistances(ServerLevel level, Collection<BlockPos> sources, BoundingBox area, Set<BlockPos> removed) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos source : sources) {
            if (distance.putIfAbsent(source, 0) == null) {
                queue.add(source);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            int d = distance.get(pos);
            if (d >= MAX_SUPPORT) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (distance.containsKey(next) || !area.isInside(next) || removed.contains(next)) {
                    continue;
                }
                if (!level.getBlockState(next).hasProperty(BlockStateProperties.DISTANCE)) {
                    continue; // only leaf blocks carry support on (vanilla LeavesBlock.getOptionalDistanceAt)
                }
                distance.put(next, d + 1);
                queue.add(next);
            }
        }
        return distance;
    }

    /** Squared straight-line distance from {@code pos} to the nearest of {@code logs}. */
    private static long nearestSq(BlockPos pos, Collection<BlockPos> logs) {
        long best = Long.MAX_VALUE;
        for (BlockPos log : logs) {
            best = Math.min(best, (long) pos.distSqr(log));
        }
        return best;
    }

    private static boolean isNaturalLeaf(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
    }
}
