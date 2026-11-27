package com.ronsmods.harvesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side Harvesting behaviour.
 *
 * <p>Flow (same as Ron's Veinbreaking): when a player breaks a ripe plant, {@link #onBlockBreak} finds the rest of
 * the ripe field before the block is removed and queues it. At the end of the server tick {@link #onServerTick}
 * confirms the original break really happened, replants it, then harvests each queued plant through
 * {@code ServerPlayerGameMode.destroyBlock}, the same path as a normal player break (its own {@code BreakEvent} for
 * protection mods, normal loot with Fortune, durability, exhaustion, statistics), replanting each one as it goes.
 *
 * <p>Harvesting II also replants (Harvesting I leaves the drops and the spot alone). Replanting takes one seed out of that plant's own drops, in {@link #onBlockDrops}. If its drops hold no seed,
 * the spot is left empty. Creative players replant for free, as they place blocks for free. Each replant fires a
 * place event, so a protection mod can refuse it; the seed is then dropped back.
 */
public final class HarvestingHandler {
    /** Players whose breaks are currently being issued by Harvesting. Their nested BreakEvents never re-trigger it. */
    private static final Set<UUID> HARVESTING = new HashSet<>();
    private static final List<PendingField> PENDING = new ArrayList<>();
    /** Harvested plants whose drops have not been seen yet, so one seed can be taken from them for replanting. */
    private static final Map<BlockPos, SeedClaim> AWAITING_DROPS = new HashMap<>();

    private record PendingField(ServerPlayer player, ServerLevel level, BlockPos origin, BlockState originState, Field field,
                                ItemStack tool, boolean replants, SeedClaim originSeed, List<BlockPos> ripe) {}

    /** One seed to take from the drops of the plant at a position. */
    private static final class SeedClaim {
        final ServerLevel level;
        final ServerPlayer player;
        @Nullable final Item seed;
        boolean taken;

        SeedClaim(ServerLevel level, ServerPlayer player, @Nullable Item seed) {
            this.level = level;
            this.player = player;
            this.seed = seed;
        }
    }

    private HarvestingHandler() {}

    // LOWEST so protection mods get to cancel first; canceled events are not delivered to us.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player instanceof FakePlayer) { // automation (harvesters, breakers) never triggers it
            return;
        }
        if (HARVESTING.contains(player.getUUID())) {
            return; // recursion guard: this break was issued by Harvesting itself
        }
        if (player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        int enchantmentLevel = Harvesting.harvestingLevel(level, tool);
        if (enchantmentLevel <= 0) {
            return;
        }
        BlockPos origin = event.getPos().immutable();
        BlockState state = event.getState();
        Field field = Field.of(level, origin, state); // null unless it is a ripe plant we handle
        if (field == null) {
            return;
        }
        boolean replants = enchantmentLevel >= Harvesting.REPLANT_LEVEL;
        SeedClaim originSeed = new SeedClaim(level, player, field.seed(level, origin, state));
        if (replants) {
            AWAITING_DROPS.put(origin, originSeed); // Harvesting I leaves the drops untouched
        }
        PENDING.add(new PendingField(player, level, origin, state, field, tool, replants, originSeed, field.findRipe(level, origin)));
    }

    // LOWEST: take the seed from what is actually about to drop, after other mods have changed the drops.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        SeedClaim claim = AWAITING_DROPS.get(event.getPos());
        if (claim == null || claim.taken || claim.seed == null || claim.level != event.getLevel() || event.getBreaker() != claim.player) {
            return;
        }
        List<ItemEntity> drops = event.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            ItemStack stack = drops.get(i).getItem();
            if (stack.is(claim.seed)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    drops.remove(i);
                }
                claim.taken = true;
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!PENDING.isEmpty()) {
            List<PendingField> batch = List.copyOf(PENDING);
            PENDING.clear();
            for (PendingField job : batch) {
                harvest(job);
            }
        }
        AWAITING_DROPS.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        AWAITING_DROPS.clear();
        HARVESTING.clear();
    }

    private static void harvest(PendingField job) {
        ServerPlayer player = job.player();
        ServerLevel level = job.level();
        Field field = job.field();
        if (player.isRemoved() || player.level() != level) {
            return;
        }
        // The BreakEvent can still be followed by a failed or simulated break. Only continue if the plant is really gone.
        if (level.getBlockState(job.origin()).is(field.block())) {
            return;
        }
        if (job.replants()) {
            replant(player, level, job.origin(), field.replantState(job.originState()), job.originSeed());
        }
        HARVESTING.add(player.getUUID());
        try {
            for (BlockPos pos : job.ripe()) {
                // Stop when the hoe broke (stack emptied) or the player is no longer holding the same hoe.
                if (job.tool().isEmpty() || player.getMainHandItem() != job.tool()) {
                    return;
                }
                // Skip plants that changed since the scan (already harvested, trampled, washed away...).
                BlockState state = level.getBlockState(pos);
                if (!field.isRipe(level, pos, state)) {
                    continue;
                }
                // Vanilla checks spawn protection and the world border before a player break; destroyBlock does not.
                if (!level.mayInteract(player, pos)) {
                    continue;
                }
                if (!job.replants()) {
                    // Harvesting I: a plain player break with vanilla drops. A protected (canceled) plant is skipped.
                    player.gameMode.destroyBlock(pos);
                    continue;
                }
                BlockState replant = field.replantState(state);
                SeedClaim seed = new SeedClaim(level, player, field.seed(level, pos, state));
                AWAITING_DROPS.put(pos, seed);
                try {
                    // A protected (canceled) plant is skipped; the rest of the field is still harvested.
                    if (!player.gameMode.destroyBlock(pos) || level.getBlockState(pos).is(field.block())) {
                        continue;
                    }
                } finally {
                    AWAITING_DROPS.remove(pos);
                }
                replant(player, level, pos, replant, seed);
            }
        } finally {
            HARVESTING.remove(player.getUUID());
        }
    }

    /** Replant a harvested spot with the seed taken from its drops (or for free in creative). */
    private static void replant(ServerPlayer player, ServerLevel level, BlockPos pos, @Nullable BlockState plant, SeedClaim seed) {
        if (plant == null || !(seed.taken || player.isCreative())) {
            return;
        }
        if (!level.getBlockState(pos).isAir() || !plant.canSurvive(level, pos)) {
            refund(level, pos, seed);
            return;
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, plant, Block.UPDATE_ALL);
        if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(); // a protection mod refused the replant
            refund(level, pos, seed);
            return;
        }
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
    }

    private static void refund(ServerLevel level, BlockPos pos, SeedClaim seed) {
        if (seed.taken && seed.seed != null) {
            Block.popResource(level, pos, new ItemStack(seed.seed));
        }
    }
}
