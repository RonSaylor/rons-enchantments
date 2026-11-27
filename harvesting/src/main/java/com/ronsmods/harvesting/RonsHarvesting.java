package com.ronsmods.harvesting;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Ron's Harvesting: adds the Harvesting hoe enchantment.
 *
 * <p>The enchantment itself is data-driven ({@code data/ronsharvesting/enchantment/harvesting.json});
 * this class only wires up the gameplay handler.
 */
@Mod(RonsHarvesting.MOD_ID)
public final class RonsHarvesting {
    public static final String MOD_ID = "ronsharvesting";

    public RonsHarvesting() {
        NeoForge.EVENT_BUS.register(HarvestingHandler.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
