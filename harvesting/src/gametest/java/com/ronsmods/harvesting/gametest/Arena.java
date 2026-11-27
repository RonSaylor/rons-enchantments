package com.ronsmods.harvesting.gametest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;
import com.ronsmods.harvesting.Harvesting;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Helpers shared by the Harvesting game tests. Coordinates passed in are relative to the test arena.
 *
 * <p>The arena is dirt below {@link #GROUND}, air above. Plants stand at {@code y = GROUND} on the block below,
 * which the helpers turn into wet farmland, soul sand and so on.
 */
final class Arena {
    static final int SIZE = 32;
    static final int HEIGHT = 16;
    static final int GROUND = 4;

    private Arena() {}

    /** Reset the arena to dirt and air and remove dropped items and experience. */
    static void reset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    BlockState want = (y < GROUND ? Blocks.DIRT : Blocks.AIR).defaultBlockState();
                    if (level.getBlockState(pos) != want) {
                        // No shape updates: plants on the old ground must not pop off and drop items.
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
        clearEntities(helper);
        TestHooks.clearDrops(bounds(helper));
    }

    static void clearEntities(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds(helper)).forEach(ItemEntity::discard);
        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, bounds(helper)).forEach(ExperienceOrb::discard);
    }

    static AABB bounds(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        return new AABB(min.getX() - 2, min.getY() - 2, min.getZ() - 2, min.getX() + SIZE + 2, min.getY() + HEIGHT + 2, min.getZ() + SIZE + 2);
    }

    /** The plant position at column {@code (x, z)}. */
    static BlockPos at(int x, int z) {
        return new BlockPos(x, GROUND, z);
    }

    static void set(GameTestHelper helper, BlockPos relative, BlockState state) {
        helper.getLevel().setBlock(helper.absolutePos(relative), state, Block.UPDATE_CLIENTS);
    }

    static BlockState get(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockState(helper.absolutePos(relative));
    }

    static void farmland(GameTestHelper helper, int x, int z) {
        set(helper, new BlockPos(x, GROUND - 1, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
    }

    /** A crop (wheat, carrots...) of the given age on wet farmland. */
    static void crop(GameTestHelper helper, int x, int z, Block crop, int age) {
        farmland(helper, x, z);
        set(helper, at(x, z), ((CropBlock) crop).getStateForAge(age));
    }

    static void ripeCrop(GameTestHelper helper, int x, int z, Block crop) {
        crop(helper, x, z, crop, ((CropBlock) crop).getMaxAge());
    }

    static void netherWart(GameTestHelper helper, int x, int z, int age) {
        set(helper, new BlockPos(x, GROUND - 1, z), Blocks.SOUL_SAND.defaultBlockState());
        set(helper, at(x, z), Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, age));
    }

    /** A cocoa pod hanging on the {@code side} face of the log at {@code log}. */
    static void cocoa(GameTestHelper helper, BlockPos log, Direction side, int age) {
        set(helper, log.relative(side),
                Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, side.getOpposite()).setValue(CocoaBlock.AGE, age));
    }

    /** A ripe melon/pumpkin stem at {@code (x, z)} with its fruit grown on the {@code side}. */
    static void attachedFruit(GameTestHelper helper, int x, int z, Direction side, Block fruit) {
        Block attached = fruit == Blocks.MELON ? Blocks.ATTACHED_MELON_STEM : Blocks.ATTACHED_PUMPKIN_STEM;
        farmland(helper, x, z);
        set(helper, at(x, z).relative(side), fruit.defaultBlockState());
        set(helper, at(x, z), attached.defaultBlockState().setValue(AttachedStemBlock.FACING, side));
    }

    /** A melon/pumpkin stem at {@code (x, z)} with no fruit. */
    static void stem(GameTestHelper helper, int x, int z, Block fruit, int age) {
        Block stem = fruit == Blocks.MELON ? Blocks.MELON_STEM : Blocks.PUMPKIN_STEM;
        farmland(helper, x, z);
        set(helper, at(x, z), stem.defaultBlockState().setValue(StemBlock.AGE, age));
    }

    /** Absolute positions in the arena whose state matches {@code test}. */
    static Set<BlockPos> find(GameTestHelper helper, Predicate<BlockState> test) {
        Set<BlockPos> found = new HashSet<>();
        BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(SIZE - 1, HEIGHT - 1, SIZE - 1)))
                .forEach(pos -> {
                    if (test.test(helper.getLevel().getBlockState(pos))) {
                        found.add(pos.immutable());
                    }
                });
        return found;
    }

    static Set<BlockPos> find(GameTestHelper helper, Block block) {
        return find(helper, s -> s.is(block));
    }

    /** Crops of {@code block} at exactly {@code age}. */
    static Set<BlockPos> cropsAtAge(GameTestHelper helper, Block block, int age) {
        return find(helper, s -> s.is(block) && ((CropBlock) block).getAge(s) == age);
    }

    /** Total count of dropped {@code item} entities around the arena. */
    /** Count of {@code item} the arena's broken blocks dropped before Harvesting took any seed. */
    static int rawDrops(GameTestHelper helper, Item item) {
        return TestHooks.rawDrops(bounds(helper), item);
    }

    static int droppedCount(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds(helper), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    static Holder<Enchantment> harvesting(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Harvesting.ENCHANTMENT);
    }

    static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    /** A netherite hoe with Harvesting II (harvests and replants). */
    static ItemStack harvestingHoe(GameTestHelper helper) {
        return harvestingHoe(helper, Items.NETHERITE_HOE, 2);
    }

    /** {@code item} with Harvesting II. */
    static ItemStack harvestingHoe(GameTestHelper helper, Item item) {
        return harvestingHoe(helper, item, 2);
    }

    static ItemStack harvestingHoe(GameTestHelper helper, Item item, int level) {
        ItemStack hoe = new ItemStack(item);
        hoe.enchant(harvesting(helper), level);
        return hoe;
    }

    /**
     * A real (non-fake) survival-mode ServerPlayer for the test level, without the login flow (other mods'
     * login hooks, e.g. FAUNA Engine, crash on a mock connection). Breaking blocks only needs a game connection.
     */
    static ServerPlayer survivalPlayer(GameTestHelper helper, ItemStack mainHand) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "harvest-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie); // sets player.connection
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(new BlockPos(16, HEIGHT - 2, 16)); // above the field, so it never tramples
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        return player;
    }

    static void removePlayer(ServerPlayer player) {
        player.discard();
    }
}
