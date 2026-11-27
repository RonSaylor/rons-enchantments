package com.ronsmods.veinbreaking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the rest of an ore vein.
 *
 * <p>A vein is every ore block of the same kind connected to the mined block, including through edges and
 * corners (worldgen ore blobs often touch only diagonally). "Same kind" means the same block, or blocks that
 * share a {@code c:ores/<kind>} tag, so stone and deepslate variants of one ore mine together, but iron next
 * to coal does not. At most {@link #MAX_EXTRA} extra blocks are taken, nearest first.
 */
final class VeinFinder {
    /** Most extra blocks one break can take. Larger than any normal vanilla ore blob. */
    static final int MAX_EXTRA = 64;

    private VeinFinder() {}

    /** The other blocks of the vein containing {@code origin}, nearest first, excluding {@code origin}. */
    static List<BlockPos> find(Level level, BlockPos origin, BlockState originState) {
        Set<TagKey<Block>> kinds = oreKinds(originState);
        Block block = originState.getBlock();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> vein = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        // Breadth-first, so the list is already roughly nearest first; collect a margin then sort exactly.
        int collectLimit = MAX_EXTRA * 4;
        while (!queue.isEmpty() && vein.size() < collectLimit) {
            BlockPos pos = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (!seen.add(next) || level.isOutsideBuildHeight(next) || !level.isLoaded(next)) {
                            continue;
                        }
                        if (sameOre(level.getBlockState(next), block, kinds)) {
                            vein.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        vein.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return vein.size() > MAX_EXTRA ? List.copyOf(vein.subList(0, MAX_EXTRA)) : vein;
    }

    static boolean sameOre(BlockState state, Block block, Set<TagKey<Block>> kinds) {
        if (!state.is(Veinbreaking.VEIN_ORES)) {
            return false;
        }
        if (state.is(block)) {
            return true;
        }
        for (TagKey<Block> kind : kinds) {
            if (state.is(kind)) {
                return true;
            }
        }
        return false;
    }

    /** The {@code c:ores/<kind>} tags of a block, e.g. {@code c:ores/iron} for both iron ores. */
    static Set<TagKey<Block>> oreKinds(BlockState state) {
        Set<TagKey<Block>> kinds = new HashSet<>();
        state.getTags().forEach(tag -> {
            if (tag.location().getNamespace().equals("c") && tag.location().getPath().startsWith("ores/")) {
                kinds.add(tag);
            }
        });
        return kinds;
    }
}
