package com.ronsmods.felling.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.ronsmods.felling.Felling;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;

/** Helpers shared by the Felling game tests. Coordinates passed in are relative to the test arena. */
final class Arena {
    static final int SIZE = 32;
    static final int HEIGHT = 48;
    /** Relative position trees are grown at (on top of the grass floor at y=0). */
    static final BlockPos CENTER = new BlockPos(16, 1, 16);

    private Arena() {}

    /** Reset the arena to air over a grass floor and stop leaf decay so leaf counts are stable. */
    static void reset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    BlockState want = y == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    if (level.getBlockState(pos) != want) {
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        level.getEntitiesOfClass(ItemEntity.class, bounds(helper)).forEach(ItemEntity::discard);
    }

    static AABB bounds(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        return new AABB(min.getX() - 2, min.getY() - 2, min.getZ() - 2, min.getX() + SIZE + 2, min.getY() + HEIGHT + 2, min.getZ() + SIZE + 2);
    }

    /** Grow a vanilla configured tree feature with a fixed seed. Returns false if worldgen refused to place it. */
    static boolean growTree(GameTestHelper helper, ResourceKey<ConfiguredFeature<?, ?>> feature, long seed, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        ConfiguredFeature<?, ?> configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(feature);
        return configured.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), helper.absolutePos(relative));
    }

    /** Absolute positions of every block of {@code block} in the arena. */
    static List<BlockPos> find(GameTestHelper helper, Block block) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(SIZE - 1, HEIGHT - 1, SIZE - 1)))
                .forEach(pos -> {
                    if (helper.getLevel().getBlockState(pos).is(block)) {
                        found.add(pos.immutable());
                    }
                });
        return found;
    }

    static int countLeaves(GameTestHelper helper) {
        int[] count = {0};
        BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(SIZE - 1, HEIGHT - 1, SIZE - 1)))
                .forEach(pos -> {
                    if (helper.getLevel().getBlockState(pos).is(BlockTags.LEAVES)) {
                        count[0]++;
                    }
                });
        return count[0];
    }

    /** Total count of dropped {@code item} entities around the arena. */
    static int droppedCount(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds(helper), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    static Holder<Enchantment> felling(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Felling.ENCHANTMENT);
    }

    static ItemStack fellingAxe(GameTestHelper helper) {
        return fellingAxe(helper, 1);
    }

    static ItemStack fellingAxe(GameTestHelper helper, int level) {
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        axe.enchant(felling(helper), level);
        return axe;
    }

    /** Absolute positions of every leaf block in the arena. */
    static java.util.Set<BlockPos> leaves(GameTestHelper helper) {
        java.util.Set<BlockPos> found = new java.util.HashSet<>();
        BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(SIZE - 1, HEIGHT - 1, SIZE - 1)))
                .forEach(pos -> {
                    if (helper.getLevel().getBlockState(pos).is(BlockTags.LEAVES)) {
                        found.add(pos.immutable());
                    }
                });
        return found;
    }

    /**
     * A real (non-fake) survival-mode ServerPlayer for the test level, like vanilla's mock player but
     * without its creative override, so durability, drops and protection events behave as in survival.
     *
     * <p>Unlike vanilla's mock it is not run through the login flow ({@code PlayerList.placeNewPlayer}):
     * other mods' login hooks (e.g. FAUNA Engine) push custom payloads that a mock connection never
     * negotiated, which crashes the test server. Breaking blocks only needs a game connection.
     */
    static ServerPlayer survivalPlayer(GameTestHelper helper, ItemStack mainHand) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "felling-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie); // sets player.connection
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(CENTER.offset(3, 0, 3));
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        return player;
    }

    static void removePlayer(ServerPlayer player) {
        player.discard();
    }
}
