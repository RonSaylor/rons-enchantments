package com.ronsmods.harvesting.gametest;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.ronsmods.harvesting.RonsHarvesting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Test stand-ins: a claim/protection mod that cancels breaks or placements at listed positions, and a drop counter
 * that records what blocks drop before Harvesting takes its seeds. Tests run side by side, so everything is by
 * absolute position.
 */
@EventBusSubscriber(modid = RonsHarvesting.MOD_ID)
public final class TestHooks {
    static final Set<BlockPos> NO_BREAK = ConcurrentHashMap.newKeySet();
    static final Set<BlockPos> NO_PLACE = ConcurrentHashMap.newKeySet();

    record Drop(BlockPos pos, Item item, int count) {}

    /** Items dropped by broken blocks before Harvesting removes any seed. */
    static final Queue<Drop> RAW_DROPS = new ConcurrentLinkedQueue<>();

    private TestHooks() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (NO_BREAK.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (NO_PLACE.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(BlockDropsEvent event) {
        for (ItemEntity drop : event.getDrops()) {
            RAW_DROPS.add(new Drop(event.getPos().immutable(), drop.getItem().getItem(), drop.getItem().getCount()));
        }
    }

    static int rawDrops(AABB area, Item item) {
        return RAW_DROPS.stream().filter(d -> d.item() == item && area.contains(d.pos().getCenter())).mapToInt(Drop::count).sum();
    }

    static void clearDrops(AABB area) {
        RAW_DROPS.removeIf(d -> area.contains(d.pos().getCenter()));
    }
}
