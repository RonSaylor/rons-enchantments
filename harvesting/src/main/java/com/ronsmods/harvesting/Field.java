package com.ronsmods.harvesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One kind of plant, and the field of it around a harvested block.
 *
 * <p>Four kinds of plant are handled:
 * <ul>
 *   <li>{@link Type#CROP}: anything extending {@link CropBlock} (wheat, carrots, potatoes, beetroot, most modded crops),
 *       ripe at its max age;</li>
 *   <li>{@link Type#NETHER_WART}: ripe at age 3;</li>
 *   <li>{@link Type#COCOA}: ripe at age 2;</li>
 *   <li>{@link Type#FRUIT}: a melon or pumpkin (or modded equivalent) with a ripe stem attached to it. A fruit block
 *       with no stem pointing at it was placed by hand and is never harvested.</li>
 * </ul>
 *
 * <p>The field is every plant of the same kind connected to the harvested one through faces, edges or corners,
 * ripe or not, so unripe patches don't split a field. For melons and pumpkins the stems of the same kind are part
 * of the field too, since fruits only touch each other through their stems. Cocoa pods on the same log are connected
 * even on opposite sides of it. Only the ripe plants of the field are harvested: at most {@link #MAX_EXTRA}, nearest first.
 */
final class Field {
    /** Most extra plants one harvest can take: a full 9x9 water-centred plot is 80. */
    static final int MAX_EXTRA = 80;
    /** Most field blocks (ripe or not) examined while looking for ripe plants. */
    private static final int MAX_VISITED = 4096;

    enum Type { CROP, NETHER_WART, COCOA, FRUIT }

    private final Type type;
    private final Block block;
    /** FRUIT only: the seed of the stems that grow this fruit, which identifies the stems of the field. */
    @Nullable
    private final Item stemSeed;

    private Field(Type type, Block block, @Nullable Item stemSeed) {
        this.type = type;
        this.block = block;
        this.stemSeed = stemSeed;
    }

    /** The field started by harvesting {@code state}, or null if it isn't a ripe plant Harvesting handles. */
    @Nullable
    static Field of(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        Field field;
        if (block instanceof CropBlock) {
            field = new Field(Type.CROP, block, null);
        } else if (block instanceof NetherWartBlock) {
            field = new Field(Type.NETHER_WART, block, null);
        } else if (block instanceof CocoaBlock) {
            field = new Field(Type.COCOA, block, null);
        } else {
            Item seed = attachedStemSeed(level, pos);
            if (seed == null) {
                return null;
            }
            field = new Field(Type.FRUIT, block, seed);
        }
        return field.isRipe(level, pos, state) ? field : null;
    }

    Type type() {
        return type;
    }

    Block block() {
        return block;
    }

    /** True for a ripe plant of this kind: one Harvesting takes. */
    boolean isRipe(Level level, BlockPos pos, BlockState state) {
        if (!state.is(block)) {
            return false;
        }
        return switch (type) {
            case CROP -> ((CropBlock) block).isMaxAge(state);
            case NETHER_WART -> state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
            case COCOA -> state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
            case FRUIT -> Objects.equals(stemSeed, attachedStemSeed(level, pos));
        };
    }

    /** True for any block of the field, ripe or not, which the search walks through. */
    private boolean isMember(Level level, BlockPos pos, BlockState state) {
        if (type != Type.FRUIT) {
            return state.is(block);
        }
        Block b = state.getBlock();
        if (b instanceof StemBlock || b instanceof AttachedStemBlock) {
            return stemSeed == cloneItem(level, pos, state);
        }
        return isRipe(level, pos, state);
    }

    /**
     * The state to replant after harvesting {@code ripe}, or null if this kind isn't replanted (melons and pumpkins:
     * their stem stays).
     */
    @Nullable
    BlockState replantState(BlockState ripe) {
        return switch (type) {
            case CROP -> ((CropBlock) block).getStateForAge(0);
            case NETHER_WART -> ripe.setValue(NetherWartBlock.AGE, 0);
            case COCOA -> ripe.setValue(CocoaBlock.AGE, 0); // keeps the side of the log it hangs on
            case FRUIT -> null;
        };
    }

    /** The item that plants this kind (wheat seeds, carrot, cocoa beans...), or null if it isn't replanted. */
    @Nullable
    Item seed(Level level, BlockPos pos, BlockState ripe) {
        return type == Type.FRUIT ? null : cloneItem(level, pos, ripe);
    }

    /** The other ripe plants of the field containing {@code origin}, nearest first, excluding {@code origin}. */
    List<BlockPos> findRipe(Level level, BlockPos origin) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> ripe = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && seen.size() < MAX_VISITED) {
            BlockPos pos = queue.poll();
            for (BlockPos next : neighbours(level, pos)) {
                if (!seen.add(next) || level.isOutsideBuildHeight(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (isMember(level, next, state)) {
                    queue.add(next);
                    if (isRipe(level, next, state)) {
                        ripe.add(next);
                    }
                }
            }
        }
        ripe.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return ripe.size() > MAX_EXTRA ? List.copyOf(ripe.subList(0, MAX_EXTRA)) : ripe;
    }

    private List<BlockPos> neighbours(Level level, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(30);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        result.add(pos.offset(dx, dy, dz));
                    }
                }
            }
        }
        if (type == Type.COCOA) {
            // Pods on the far side of the same log are two blocks apart; connect them through the log.
            BlockState state = level.getBlockState(pos);
            if (state.is(block)) {
                BlockPos log = pos.relative(state.getValue(CocoaBlock.FACING));
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    result.add(log.relative(side));
                }
            }
        }
        return result;
    }

    /** The seed of a ripe (attached) stem pointing at {@code fruit}, or null if none does. */
    @Nullable
    private static Item attachedStemSeed(Level level, BlockPos fruit) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos stemPos = fruit.relative(side);
            BlockState stem = level.getBlockState(stemPos);
            if (stem.getBlock() instanceof AttachedStemBlock && stem.getValue(AttachedStemBlock.FACING) == side.getOpposite()) {
                return cloneItem(level, stemPos, stem);
            }
        }
        return null;
    }

    @Nullable
    private static Item cloneItem(Level level, BlockPos pos, BlockState state) {
        ItemStack stack = state.getBlock().getCloneItemStack(level, pos, state);
        return stack.isEmpty() ? null : stack.getItem();
    }
}
