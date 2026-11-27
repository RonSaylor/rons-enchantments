package com.ronsmods.veinbreaking;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Ron's Veinbreaking: adds the Veinbreaking pickaxe enchantment.
 *
 * <p>The enchantment itself is data-driven ({@code data/ronsveinbreaking/enchantment/veinbreaking.json});
 * this class only wires up the gameplay handler.
 */
@Mod(RonsVeinbreaking.MOD_ID)
public final class RonsVeinbreaking {
    public static final String MOD_ID = "ronsveinbreaking";

    public RonsVeinbreaking() {
        NeoForge.EVENT_BUS.register(VeinbreakingHandler.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
