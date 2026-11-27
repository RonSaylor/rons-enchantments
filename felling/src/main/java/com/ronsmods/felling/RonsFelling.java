package com.ronsmods.felling;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Ron's Felling: adds the Felling axe enchantment.
 *
 * <p>The enchantment itself is data-driven ({@code data/ronsfelling/enchantment/felling.json}); this
 * class only wires up the gameplay handler.
 */
@Mod(RonsFelling.MOD_ID)
public final class RonsFelling {
    public static final String MOD_ID = "ronsfelling";

    public RonsFelling() {
        NeoForge.EVENT_BUS.register(FellingHandler.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
