package com.ronsmods.veinbreaking.gametest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ronsmods.veinbreaking.RonsVeinbreaking;

import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Stand-in for a claim/protection mod: cancels breaks at listed positions, as such mods do. */
@EventBusSubscriber(modid = RonsVeinbreaking.MOD_ID)
public final class TestProtection {
    static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();

    private TestProtection() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (PROTECTED.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }
}
