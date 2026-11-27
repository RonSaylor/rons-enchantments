package com.ronsmods.veinbreaking.gametest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.ronsmods.veinbreaking.Veinbreaking;

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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.AABB;

/**
 * Helpers shared by the Veinbreaking game tests. Coordinates passed in are relative to the test arena.
 *
 * <p>The arena is solid rock: deepslate below {@link #DEEPSLATE_TOP}, stone up to {@link #ROCK_TOP}, air above.
 * Ore features grown at the boundary produce mixed stone/deepslate veins, as in the real world around y=0.
 */
final class Arena {
    static final int SIZE = 32;
    static final int HEIGHT = 48;
    static final int DEEPSLATE_TOP = 12;
    static final int ROCK_TOP = 24;
    /** Where ore features are grown: the middle of the arena, at the stone/deepslate boundary. */
    static final BlockPos CENTER = new BlockPos(16, DEEPSLATE_TOP, 16);

    private Arena() {}

    /** Reset the arena to rock and remove dropped items and experience. */
    static void reset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    BlockState want = (y < DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < ROCK_TOP ? Blocks.STONE : Blocks.AIR).defaultBlockState();
                    if (level.getBlockState(pos) != want) {
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        clearEntities(helper);
    }

    static void clearEntities(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds(helper)).forEach(ItemEntity::discard);
        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, bounds(helper)).forEach(ExperienceOrb::discard);
    }

    static AABB bounds(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        return new AABB(min.getX() - 2, min.getY() - 2, min.getZ() - 2, min.getX() + SIZE + 2, min.getY() + HEIGHT + 2, min.getZ() + SIZE + 2);
    }

    /** Grow a vanilla configured ore feature with a fixed seed. Returns false if worldgen placed nothing. */
    static boolean growOre(GameTestHelper helper, ResourceKey<ConfiguredFeature<?, ?>> feature, long seed, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        ConfiguredFeature<?, ?> configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getOrThrow(feature);
        return configured.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), helper.absolutePos(relative));
    }

    /** Absolute positions of every block in {@code blocks} in the arena. */
    static Set<BlockPos> find(GameTestHelper helper, Set<Block> blocks) {
        Set<BlockPos> found = new HashSet<>();
        BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(SIZE - 1, HEIGHT - 1, SIZE - 1)))
                .forEach(pos -> {
                    if (blocks.contains(helper.getLevel().getBlockState(pos).getBlock())) {
                        found.add(pos.immutable());
                    }
                });
        return found;
    }

    static void set(GameTestHelper helper, BlockPos relative, Block block) {
        helper.getLevel().setBlock(helper.absolutePos(relative), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Total count of dropped {@code item} entities around the arena. */
    static int droppedCount(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds(helper), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    static int droppedExperience(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, bounds(helper)).stream().mapToInt(ExperienceOrb::getValue).sum();
    }

    static Holder<Enchantment> veinbreaking(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Veinbreaking.ENCHANTMENT);
    }

    static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    /** A netherite pickaxe (harvests every ore) with Veinbreaking. */
    static ItemStack veinPickaxe(GameTestHelper helper) {
        return veinPickaxe(helper, Items.NETHERITE_PICKAXE);
    }

    static ItemStack veinPickaxe(GameTestHelper helper, Item item) {
        ItemStack pickaxe = new ItemStack(item);
        pickaxe.enchant(veinbreaking(helper), 1);
        return pickaxe;
    }

    /**
     * A real (non-fake) survival-mode ServerPlayer for the test level, without the login flow (other mods'
     * login hooks, e.g. FAUNA Engine, crash on a mock connection). Breaking blocks only needs a game connection.
     */
    static ServerPlayer survivalPlayer(GameTestHelper helper, ItemStack mainHand) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "vein-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie); // sets player.connection
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(new BlockPos(16, ROCK_TOP, 16));
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        return player;
    }

    static void removePlayer(ServerPlayer player) {
        player.discard();
    }
}
