package com.ronsmods.veinbreaking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side Veinbreaking behaviour.
 *
 * <p>Flow (same as Ron's Felling): when a player mines an ore, {@link #onBlockBreak} finds the rest of the
 * vein before the block is removed and queues it. At the end of the server tick {@link #onServerTick}
 * confirms the original break really happened and then mines each queued block through
 * {@code ServerPlayerGameMode.destroyBlock}, the same path as a normal player break. That gives every
 * extra block its own {@code BreakEvent} (claim/protection mods), normal loot (Fortune, Silk Touch),
 * experience, tool durability, exhaustion and statistics.
 */
public final class VeinbreakingHandler {
    /** Players whose breaks are currently being issued by Veinbreaking. Their nested BreakEvents never re-trigger it. */
    private static final Set<UUID> MINING = new HashSet<>();
    private static final List<PendingVein> PENDING = new ArrayList<>();

    private record PendingVein(ServerPlayer player, ServerLevel level, BlockPos origin, Block block, Set<TagKey<Block>> kinds,
                               ItemStack tool, List<BlockPos> vein) {}

    private VeinbreakingHandler() {}

    // LOWEST so protection mods get to cancel first; canceled events are not delivered to us.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player instanceof FakePlayer) { // automation (quarries, breakers) never vein-mines
            return;
        }
        if (MINING.contains(player.getUUID())) {
            return; // recursion guard: this break was issued by Veinbreaking itself
        }
        BlockState state = event.getState();
        if (!state.is(Veinbreaking.VEIN_ORES) || player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (Veinbreaking.veinbreakingLevel(level, tool) <= 0) {
            return;
        }
        // Only when this pickaxe actually harvests the ore; a stone pickaxe on diamond would just destroy the vein.
        if (!player.hasCorrectToolForDrops(state, level, event.getPos())) {
            return;
        }
        List<BlockPos> vein = VeinFinder.find(level, event.getPos(), state);
        if (vein.isEmpty()) {
            return;
        }
        PENDING.add(new PendingVein(player, level, event.getPos().immutable(), state.getBlock(), VeinFinder.oreKinds(state), tool, vein));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<PendingVein> batch = List.copyOf(PENDING);
        PENDING.clear();
        for (PendingVein job : batch) {
            mine(job);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        MINING.clear();
    }

    private static void mine(PendingVein job) {
        ServerPlayer player = job.player();
        ServerLevel level = job.level();
        if (player.isRemoved() || player.level() != level) {
            return;
        }
        // The BreakEvent can still be followed by a failed or simulated break. Only continue if the ore is really gone.
        if (level.getBlockState(job.origin()).is(job.block())) {
            return;
        }
        MINING.add(player.getUUID());
        try {
            for (BlockPos pos : job.vein()) {
                // Stop when the pickaxe broke (stack emptied) or the player is no longer holding the same pickaxe.
                if (job.tool().isEmpty() || player.getMainHandItem() != job.tool()) {
                    return;
                }
                // Skip blocks that changed since the scan (another player mined them, water flowed in...).
                BlockState state = level.getBlockState(pos);
                if (!VeinFinder.sameOre(state, job.block(), job.kinds())) {
                    continue;
                }
                // Vanilla checks spawn protection and the world border before a player break; destroyBlock does not.
                if (!level.mayInteract(player, pos)) {
                    continue;
                }
                // A protected (canceled) block is skipped; the rest of the vein is still mined.
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            MINING.remove(player.getUUID());
        }
    }
}
