package com.ronsmods.felling;

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

/** Keys for the Felling enchantment and the block tags that drive tree detection. */
public final class Felling {
    public static final ResourceKey<Enchantment> ENCHANTMENT = ResourceKey.create(Registries.ENCHANTMENT, RonsFelling.id("felling"));

    /** Log blocks that can be the trunk of a supported tree. */
    public static final TagKey<Block> FELLABLE_LOGS = BlockTags.create(RonsFelling.id("fellable_logs"));
    /** Subset of {@link #FELLABLE_LOGS} whose natural trees may grow a 2x2 trunk. */
    public static final TagKey<Block> TWO_BY_TWO_LOGS = BlockTags.create(RonsFelling.id("two_by_two_logs"));
    /** Non-leaf blocks that naturally touch a tree's logs (plants, vines, cocoa, bee nests, snow...). */
    public static final TagKey<Block> NATURAL_TREE_NEIGHBORS = BlockTags.create(RonsFelling.id("natural_tree_neighbors"));
    /** Terrain a trunk may touch near its base (dirt, stone, sand...). */
    public static final TagKey<Block> TREE_GROUND = BlockTags.create(RonsFelling.id("tree_ground"));

    private Felling() {}

    /** Felling level on {@code stack} if it is an axe, else 0. Level I fells the trunk; level II also clears its leaves. */
    public static int fellingLevel(Level level, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.AXES)) {
            return 0;
        }
        Optional<Holder.Reference<Enchantment>> felling = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(ENCHANTMENT);
        return felling.map(stack::getEnchantmentLevel).orElse(0);
    }

    /** True if {@code stack} is an axe carrying Felling. */
    public static boolean isFellingAxe(Level level, ItemStack stack) {
        return fellingLevel(level, stack) > 0;
    }
}
