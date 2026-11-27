package com.ronsmods.felling;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Decides whether a broken log is part of a natural tree's trunk, and if so which logs fall.
 *
 * <p>The broken log may be anywhere in the trunk column. The whole tree, from its real base on the
 * ground, must pass every rule; then everything above the cut falls and the rest stays as a stump.
 *
 * <p>The rules are deliberately conservative: a false negative (the player chops normally) is
 * always preferred over felling part of a build. Any structure that does not clearly look like an
 * untouched natural tree is rejected. See the README for the player-facing description.
 */
public final class TreeDetector {
    /** Max horizontal distance (per axis) of any trunk log from the broken log. */
    public static final int MAX_HORIZONTAL = 8;
    /** Max height of any trunk log above the broken log. */
    public static final int MAX_HEIGHT = 40;
    /** Max logs in one tree, including the broken one. */
    public static final int MAX_LOGS = 256;
    /** Min distinct natural (non-persistent) leaf blocks touching the trunk. */
    public static final int MIN_NATURAL_LEAVES = 5;
    /** Logs at most this many blocks above the base may touch terrain (slopes, cliffs). */
    public static final int GROUND_CONTACT_HEIGHT = 2;

    public enum Verdict {
        /** A supported tree. {@link Result#logs()} holds the logs to fell. */
        TREE,
        /** The broken block is not a vertical fellable log in a trunk column standing on dirt (e.g. a branch). Not an error. */
        NOT_TRUNK_LOG,
        /** The cut is at the top of the tree; nothing above it to fell. */
        NOTHING_ABOVE,
        /** Only the broken log is present; nothing to fell. */
        SINGLE_LOG,
        /** A connected same-species log lies outside the search box. */
        OUT_OF_BOUNDS,
        /** More than {@link #MAX_LOGS} connected logs. */
        TOO_MANY_LOGS,
        /** The search touched an unloaded chunk. */
        UNLOADED,
        /** The trunk base is not a single log or (for 2x2 species) a full 2x2 on dirt. */
        BAD_FOOTPRINT,
        /** A log touches a block that natural trees do not touch (planks, torches, other logs, placed leaves...). */
        FOREIGN_NEIGHBOR,
        /** Too few natural leaves touch the trunk. */
        TOO_FEW_LEAVES,
        /** The highest logs are not touching natural leaves. */
        BARE_CROWN
    }

    /**
     * @param logs   logs to break after the broken one, top-down; empty unless {@code verdict == TREE}
     * @param detail position that caused a rejection, for logging; may be null
     */
    public record Result(Verdict verdict, List<BlockPos> logs, @Nullable BlockPos detail) {
        static Result reject(Verdict verdict, @Nullable BlockPos detail) {
            return new Result(verdict, List.of(), detail);
        }
    }

    private TreeDetector() {}

    /**
     * Inspect the log at {@code brokenPos}, which must still be in the world (call before it is broken).
     * On {@code TREE}, the result lists the logs above the cut: every tree log higher than the broken one,
     * plus, for a 2x2 trunk, the other trunk logs at the cut height. Logs below the cut (stump, low
     * branches) stay.
     */
    public static Result detect(Level level, BlockPos brokenPos) {
        BlockPos cut = brokenPos.immutable();
        BlockState cutState = level.getBlockState(cut);
        if (!cutState.is(Felling.FELLABLE_LOGS) || !isVertical(cutState)) {
            return Result.reject(Verdict.NOT_TRUNK_LOG, cut);
        }
        // Walk down the trunk column to the log standing on the ground.
        BlockPos base = cut;
        while (cut.getY() - base.getY() < MAX_HEIGHT) {
            BlockState below = level.getBlockState(base.below());
            if (!below.is(cutState.getBlock()) || !isVertical(below)) {
                break;
            }
            base = base.below();
        }
        if (!isBottomLog(level, base, level.getBlockState(base))) {
            return Result.reject(Verdict.NOT_TRUNK_LOG, base); // a branch, or a column not standing on dirt
        }

        Result whole = detectFromBase(level, base);
        if (whole.verdict() != Verdict.TREE) {
            return whole;
        }
        List<BlockPos> treeLogs = new ArrayList<>(whole.logs());
        treeLogs.add(base);
        Set<Long> trunkColumns = new HashSet<>();
        for (BlockPos p : treeLogs) {
            if (p.getY() == base.getY()) {
                trunkColumns.add(BlockPos.asLong(p.getX(), 0, p.getZ()));
            }
        }
        List<BlockPos> above = new ArrayList<>();
        for (BlockPos p : treeLogs) {
            boolean higher = p.getY() > cut.getY();
            boolean sameLayerOfTrunk = p.getY() == cut.getY() && trunkColumns.contains(BlockPos.asLong(p.getX(), 0, p.getZ()));
            if (!p.equals(cut) && (higher || sameLayerOfTrunk)) {
                above.add(p);
            }
        }
        if (above.isEmpty()) {
            return Result.reject(Verdict.NOTHING_ABOVE, cut);
        }
        // Fell top-down, outermost first, so an interrupted fell leaves a reachable stump, not a floating crown.
        Comparator<BlockPos> highestFirst = Comparator.comparingInt((BlockPos p) -> p.getY()).reversed();
        Comparator<BlockPos> outermostFirst = Comparator.comparingInt((BlockPos p) -> horizontalDistSqr(cut, p)).reversed();
        above.sort(highestFirst.thenComparing(outermostFirst).thenComparingLong(BlockPos::asLong));
        return new Result(Verdict.TREE, List.copyOf(above), null);
    }

    /** Validate the whole tree standing on {@code base}; on {@code TREE}, list all its logs except the base. */
    private static Result detectFromBase(Level level, BlockPos base) {
        BlockState baseState = level.getBlockState(base);
        Block species = baseState.getBlock();

        // 1. Flood fill same-species logs, 26-connected (natural branches join diagonally).
        Set<BlockPos> trunk = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        trunk.add(base);
        queue.add(base);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (trunk.contains(next)) {
                            continue;
                        }
                        if (!level.isLoaded(next)) {
                            return Result.reject(Verdict.UNLOADED, next);
                        }
                        if (!level.getBlockState(next).is(species)) {
                            continue;
                        }
                        if (!inBounds(base, next)) {
                            return Result.reject(Verdict.OUT_OF_BOUNDS, next);
                        }
                        trunk.add(next);
                        if (trunk.size() > MAX_LOGS) {
                            return Result.reject(Verdict.TOO_MANY_LOGS, next);
                        }
                        queue.add(next);
                    }
                }
            }
        }
        if (trunk.size() == 1) {
            return Result.reject(Verdict.SINGLE_LOG, base);
        }

        // 2. The footprint at base level must be the broken log alone, or a complete 2x2 for species that grow one.
        List<BlockPos> footprint = trunk.stream().filter(p -> p.getY() == base.getY()).toList();
        if (!validFootprint(level, footprint, baseState)) {
            return Result.reject(Verdict.BAD_FOOTPRINT, base);
        }

        // 3. Every face neighbor of every log must be something a natural tree touches.
        Set<BlockPos> naturalLeaves = new HashSet<>();
        for (BlockPos log : trunk) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = log.relative(direction);
                if (trunk.contains(neighbor)) {
                    continue;
                }
                if (!level.isLoaded(neighbor)) {
                    return Result.reject(Verdict.UNLOADED, neighbor);
                }
                BlockState state = level.getBlockState(neighbor);
                if (state.isAir()) {
                    continue;
                }
                if (state.is(BlockTags.LEAVES)) {
                    if (isNaturalLeaves(state)) {
                        naturalLeaves.add(neighbor);
                        continue;
                    }
                    return Result.reject(Verdict.FOREIGN_NEIGHBOR, neighbor); // player-placed leaves
                }
                if (state.is(Felling.NATURAL_TREE_NEIGHBORS)) {
                    continue;
                }
                if (log.getY() - base.getY() <= GROUND_CONTACT_HEIGHT && state.is(Felling.TREE_GROUND)) {
                    continue;
                }
                return Result.reject(Verdict.FOREIGN_NEIGHBOR, neighbor);
            }
        }

        // 4. It must carry a real canopy: enough natural leaves, and leaves at the very top.
        if (naturalLeaves.size() < MIN_NATURAL_LEAVES) {
            return Result.reject(Verdict.TOO_FEW_LEAVES, base);
        }
        int topY = trunk.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        boolean crowned = trunk.stream()
                .filter(p -> p.getY() == topY)
                .anyMatch(p -> touchesAny(p, naturalLeaves));
        if (!crowned) {
            return Result.reject(Verdict.BARE_CROWN, base);
        }

        List<BlockPos> logs = new ArrayList<>(trunk);
        logs.remove(base);
        return new Result(Verdict.TREE, logs, null);
    }

    /** A vertical fellable log sitting directly on dirt: the bottom of a trunk column. */
    public static boolean isBottomLog(Level level, BlockPos pos, BlockState state) {
        return state.is(Felling.FELLABLE_LOGS)
                && isVertical(state)
                && level.getBlockState(pos.below()).is(BlockTags.DIRT);
    }

    private static boolean validFootprint(Level level, List<BlockPos> footprint, BlockState baseState) {
        for (BlockPos pos : footprint) {
            if (!isVertical(level.getBlockState(pos)) || !level.getBlockState(pos.below()).is(BlockTags.DIRT)) {
                return false;
            }
        }
        if (footprint.size() == 1) {
            return true;
        }
        if (footprint.size() != 4 || !baseState.is(Felling.TWO_BY_TWO_LOGS)) {
            return false;
        }
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        // Four distinct positions inside a 2x2 box is exactly the full square.
        return maxX - minX == 1 && maxZ - minZ == 1;
    }

    private static boolean isVertical(BlockState state) {
        return state.hasProperty(BlockStateProperties.AXIS)
                && state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
    }

    /** Leaves generated by a tree are non-persistent; leaves placed by a player are persistent. */
    private static boolean isNaturalLeaves(BlockState state) {
        return state.hasProperty(BlockStateProperties.PERSISTENT) && !state.getValue(BlockStateProperties.PERSISTENT);
    }

    private static boolean touchesAny(BlockPos pos, Set<BlockPos> positions) {
        for (Direction direction : Direction.values()) {
            if (positions.contains(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean inBounds(BlockPos base, BlockPos pos) {
        int dy = pos.getY() - base.getY();
        return dy >= 0 && dy <= MAX_HEIGHT
                && Math.abs(pos.getX() - base.getX()) <= MAX_HORIZONTAL
                && Math.abs(pos.getZ() - base.getZ()) <= MAX_HORIZONTAL;
    }

    private static int horizontalDistSqr(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
