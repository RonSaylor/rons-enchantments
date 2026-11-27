package com.ronsmods.felling;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Server-side Felling behaviour.
 *
 * <p>Flow: when a player breaks a log, {@link #onBlockBreak} decides (before the block is removed)
 * whether it is in the trunk of a supported tree and queues the logs above it. At the end of the server tick
 * {@link #onServerTick} confirms the original break really happened and then breaks each queued log
 * through {@code ServerPlayerGameMode.destroyBlock}, the same path as a normal player break. That
 * gives every extra log its own {@code BreakEvent} (claim/protection mods), normal loot, tool
 * durability, exhaustion and statistics. With Felling II, once every log is down,
 * {@link LeafClearer} removes the leaves that would now decay, with natural-decay drops.
 */
public final class FellingHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Players whose breaks are currently being issued by Felling. Their nested BreakEvents never re-trigger it. */
    private static final Set<UUID> FELLING = new HashSet<>();
    private static final List<PendingFell> PENDING = new ArrayList<>();

    private record PendingFell(ServerPlayer player, ServerLevel level, BlockPos cut, Block species, ItemStack tool, int fellingLevel, List<BlockPos> logs) {}

    private FellingHandler() {}

    // LOWEST so protection mods get to cancel first; canceled events are not delivered to us.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player instanceof FakePlayer) { // automation (deployers, breakers) never fells
            return;
        }
        if (FELLING.contains(player.getUUID())) {
            return; // recursion guard: this break was issued by Felling itself
        }
        BlockState state = event.getState();
        if (!state.is(Felling.FELLABLE_LOGS) || player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        int fellingLevel = Felling.fellingLevel(level, tool);
        if (fellingLevel <= 0) {
            return;
        }

        TreeDetector.Result result = TreeDetector.detect(level, event.getPos());
        if (result.verdict() != TreeDetector.Verdict.TREE) {
            if (result.verdict() != TreeDetector.Verdict.NOT_TRUNK_LOG && result.verdict() != TreeDetector.Verdict.NOTHING_ABOVE) {
                LOGGER.debug("Felling skipped at {}: {} (at {})", event.getPos(), result.verdict(), result.detail());
            }
            return;
        }
        PENDING.add(new PendingFell(player, level, event.getPos().immutable(), state.getBlock(), tool, fellingLevel, result.logs()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<PendingFell> batch = List.copyOf(PENDING);
        PENDING.clear();
        for (PendingFell job : batch) {
            fell(job);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        FELLING.clear();
    }

    private static void fell(PendingFell job) {
        ServerPlayer player = job.player();
        ServerLevel level = job.level();
        if (player.isRemoved() || player.level() != level) {
            return;
        }
        // The BreakEvent can still be followed by a failed or simulated break. Only fell if the cut log is really gone.
        if (level.getBlockState(job.cut()).is(job.species())) {
            return;
        }
        FELLING.add(player.getUUID());
        try {
            for (BlockPos pos : job.logs()) {
                // Stop when the axe broke (stack emptied) or the player is no longer holding the same axe.
                if (job.tool().isEmpty() || player.getMainHandItem() != job.tool()) {
                    return;
                }
                // Stop if the tree changed since it was scanned.
                if (!level.getBlockState(pos).is(job.species())) {
                    return;
                }
                // Vanilla checks spawn protection and the world border before a player break; destroyBlock does not.
                if (!level.mayInteract(player, pos)) {
                    return;
                }
                // Stop if the block could not be broken (protection, adventure mode, another mod canceled it).
                if (!player.gameMode.destroyBlock(pos) || level.getBlockState(pos).is(job.species())) {
                    return;
                }
            }
            // The whole trunk is down. Felling II also clears the leaves it left unsupported.
            if (job.fellingLevel() >= 2) {
                List<BlockPos> trunk = new ArrayList<>(job.logs());
                trunk.add(job.cut());
                LeafClearer.clear(level, player, trunk, job.species());
            }
        } finally {
            FELLING.remove(player.getUUID());
        }
    }
}
