package com.ronsmods.harvesting;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/** Key for the Harvesting enchantment. Level I harvests the ripe field; level II also replants it. */
public final class Harvesting {
    public static final ResourceKey<Enchantment> ENCHANTMENT = ResourceKey.create(Registries.ENCHANTMENT, RonsHarvesting.id("harvesting"));

    /** The level from which harvested plants are replanted. */
    public static final int REPLANT_LEVEL = 2;

    private Harvesting() {}

    /** Harvesting level on {@code stack} if it is a hoe, else 0. */
    public static int harvestingLevel(Level level, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.HOES)) {
            return 0;
        }
        Optional<Holder.Reference<Enchantment>> enchantment = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ENCHANTMENT);
        return enchantment.map(stack::getEnchantmentLevel).orElse(0);
    }
}
