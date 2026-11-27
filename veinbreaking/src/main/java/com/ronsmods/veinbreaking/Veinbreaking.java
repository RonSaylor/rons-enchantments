package com.ronsmods.veinbreaking;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Keys for the Veinbreaking enchantment and the block tag that decides what counts as ore. */
public final class Veinbreaking {
    public static final ResourceKey<Enchantment> ENCHANTMENT = ResourceKey.create(Registries.ENCHANTMENT, RonsVeinbreaking.id("veinbreaking"));

    /** Blocks that start and make up a vein. Defaults to {@code #c:ores}, so modded ores work too. */
    public static final TagKey<Block> VEIN_ORES = BlockTags.create(RonsVeinbreaking.id("vein_ores"));

    private Veinbreaking() {}

    /** Veinbreaking level on {@code stack} if it is a pickaxe, else 0. */
    public static int veinbreakingLevel(Level level, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.PICKAXES)) {
            return 0;
        }
        Optional<Holder.Reference<Enchantment>> enchantment = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ENCHANTMENT);
        return enchantment.map(stack::getEnchantmentLevel).orElse(0);
    }
}
