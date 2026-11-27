package com.ronsmods.veinbreaking.gametest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.ronsmods.veinbreaking.RonsVeinbreaking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.OreFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game checks for Veinbreaking. Run with {@code gradlew :veinbreaking:runGameTestServer}.
 *
 * <p>Veins are grown with the real vanilla ore features, so these exercise genuine natural vein shapes.
 * Every break goes through {@code ServerPlayerGameMode.destroyBlock}, the same path a player's left-click takes.
 */
@GameTestHolder(RonsVeinbreaking.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VeinbreakingGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARENA = "arena";
    private static final int SEEDS = 12;
    /** The mined block plus at most 64 more. */
    private static final int MAX_TOTAL = 65;
    private static final Set<Block> ROCK = Set.of(Blocks.STONE, Blocks.DEEPSLATE);

    private static final Set<Block> COAL = Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
    private static final Set<Block> IRON = Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
    private static final Set<Block> COPPER = Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
    private static final Set<Block> GOLD = Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
    private static final Set<Block> REDSTONE = Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
    private static final Set<Block> LAPIS = Set.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
    private static final Set<Block> DIAMOND = Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
    private static final Set<Block> EMERALD = Set.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);

    // ---------------------------------------------------------------- natural veins mine completely

    @GameTest(template = ARENA, timeoutTicks = 1200) public static void minesCoal(GameTestHelper h) { minesEveryVein(h, OreFeatures.ORE_COAL, COAL); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void minesIron(GameTestHelper h) { minesEveryVein(h, OreFeatures.ORE_IRON, IRON); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void minesLargeCopper(GameTestHelper h) { minesEveryVein(h, OreFeatures.ORE_COPPER_LARGE, COPPER); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void minesGold(GameTestHelper h) { minesEveryVein(h, OreFeatures.ORE_GOLD, GOLD); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void minesRedstone(GameTestHelper h) { minesEveryVein(h, OreFeatures.ORE_REDSTONE, REDSTONE); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void minesLapis(GameTestHelper h) { minesEveryVein(h, OreFeatures.ORE_LAPIS, LAPIS); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void minesLargeDiamond(GameTestHelper h) { minesEveryVein(h, OreFeatures.ORE_DIAMOND_LARGE, DIAMOND); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void minesEmerald(GameTestHelper h) { minesEveryVein(h, OreFeatures.ORE_EMERALD, EMERALD); }

    /**
     * Grow the ore feature with several seeds at the stone/deepslate boundary; each time, mine the ore block
     * nearest the center. Exactly its vein must go (every ore of that kind connected through faces, edges or
     * corners, stone and deepslate variants alike, capped at {@link #MAX_TOTAL}), no rock, and durability is
     * one per block.
     */
    private static void minesEveryVein(GameTestHelper helper, ResourceKey<ConfiguredFeature<?, ?>> feature, Set<Block> ore) {
        List<String> failures = new ArrayList<>();
        int[] totals = new int[3]; // veins mined, blocks mined, mixed stone/deepslate veins
        GameTestSequence sequence = helper.startSequence();
        for (int i = 0; i < SEEDS; i++) {
            long seed = 1000L + i;
            Object[] state = new Object[5]; // player, pickaxe, expected removed, ore before, rock before
            sequence.thenExecute(() -> {
                state[0] = null;
                Arena.reset(helper);
                Arena.growOre(helper, feature, seed, Arena.CENTER);
                Set<BlockPos> ores = Arena.find(helper, ore);
                if (ores.isEmpty()) {
                    return;
                }
                BlockPos center = helper.absolutePos(Arena.CENTER);
                BlockPos target = ores.stream().min(Comparator.comparingDouble(p -> p.distSqr(center))).orElseThrow();
                Set<BlockPos> vein = component(ores, target);
                if (vein.stream().map(p -> helper.getLevel().getBlockState(p).getBlock()).distinct().count() > 1) {
                    totals[2]++;
                }
                ItemStack pickaxe = Arena.veinPickaxe(helper);
                ServerPlayer player = Arena.survivalPlayer(helper, pickaxe);
                state[0] = player;
                state[1] = pickaxe;
                state[2] = vein;
                state[3] = ores;
                state[4] = Arena.find(helper, ROCK).size();
                player.gameMode.destroyBlock(target);
            }).thenIdle(2).thenExecute(() -> {
                if (state[0] == null) {
                    return;
                }
                @SuppressWarnings("unchecked") Set<BlockPos> vein = (Set<BlockPos>) state[2];
                @SuppressWarnings("unchecked") Set<BlockPos> before = (Set<BlockPos>) state[3];
                Set<BlockPos> removed = new HashSet<>(before);
                removed.removeAll(Arena.find(helper, ore));
                int damage = ((ItemStack) state[1]).getDamageValue();
                if (vein.size() <= MAX_TOTAL ? !removed.equals(vein) : removed.size() != MAX_TOTAL || !vein.containsAll(removed)) {
                    failures.add("seed " + seed + ": mined " + removed.size() + ", vein has " + vein.size()
                            + " (outside vein " + diffSize(removed, vein) + ", vein left " + diffSize(vein, removed) + ")");
                } else if (damage != removed.size()) {
                    failures.add("seed " + seed + ": pickaxe took " + damage + " damage for " + removed.size() + " blocks");
                } else if (Arena.find(helper, ROCK).size() != (int) state[4]) {
                    failures.add("seed " + seed + ": rock was mined");
                }
                totals[0]++;
                totals[1] += removed.size();
                Arena.removePlayer((ServerPlayer) state[0]);
            });
        }
        sequence.thenExecute(() -> {
            String name = feature.location().getPath();
            LOGGER.info("[vein-test] {}: {} veins, {} blocks mined ({} mixed stone/deepslate), {} problem(s){}", name, totals[0], totals[1], totals[2],
                    failures.size(), failures.isEmpty() ? "" : "\n  " + String.join("\n  ", failures));
            helper.assertTrue(totals[0] > SEEDS / 2, name + ": too few veins grew: " + totals[0]);
            helper.assertTrue(failures.isEmpty(), name + ": " + String.join("; ", failures));
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- vein shape rules

    /** Ores touching only at a corner or an edge are one vein. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void diagonalTouchesAreOneVein(GameTestHelper helper) {
        BlockPos a = new BlockPos(16, 10, 16);
        mine(helper, () -> {
            Arena.set(helper, a, Blocks.IRON_ORE);
            Arena.set(helper, a.offset(1, 1, 1), Blocks.IRON_ORE); // corner
            Arena.set(helper, a.offset(2, 1, 1).offset(0, 0, 1), Blocks.IRON_ORE); // edge of the previous
        }, Arena.veinPickaxe(helper), a, h -> assertLeft(h, IRON, Set.of()));
    }

    /** One block of rock between two ores separates them. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void gapEndsVein(GameTestHelper helper) {
        BlockPos a = new BlockPos(16, 10, 16);
        mine(helper, () -> {
            Arena.set(helper, a, Blocks.IRON_ORE);
            Arena.set(helper, a.east(), Blocks.IRON_ORE);
            Arena.set(helper, a.east(3), Blocks.IRON_ORE);
        }, Arena.veinPickaxe(helper), a, h -> assertLeft(h, IRON, Set.of(h.absolutePos(a.east(3)))));
    }

    /** Stone and deepslate variants of one ore are one vein; a different ore touching it is not. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void variantsJoinOtherOresDoNot(GameTestHelper helper) {
        BlockPos a = new BlockPos(16, 10, 16);
        mine(helper, () -> {
            Arena.set(helper, a, Blocks.IRON_ORE);
            Arena.set(helper, a.above(), Blocks.DEEPSLATE_IRON_ORE);
            Arena.set(helper, a.north(), Blocks.COAL_ORE);
            Arena.set(helper, a.above().north(), Blocks.COPPER_ORE);
        }, Arena.veinPickaxe(helper), a, h -> {
            assertLeft(h, IRON, Set.of());
            assertLeft(h, COAL, Set.of(h.absolutePos(a.north())));
            assertLeft(h, COPPER, Set.of(h.absolutePos(a.above().north())));
        });
    }

    /** A huge vein (5x5x5 = 125 ores) gives the mined block plus the 64 nearest. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void hugeVeinIsCapped(GameTestHelper helper) {
        BlockPos corner = new BlockPos(14, 6, 14);
        BlockPos target = corner.offset(2, 2, 2);
        mine(helper, () -> {
            for (BlockPos p : BlockPos.betweenClosed(corner, corner.offset(4, 4, 4))) {
                Arena.set(helper, p, Blocks.IRON_ORE);
            }
        }, Arena.veinPickaxe(helper), target, h -> {
            Set<BlockPos> left = Arena.find(h, IRON);
            h.assertTrue(left.size() == 125 - MAX_TOTAL, "expected " + (125 - MAX_TOTAL) + " left, found " + left.size());
            BlockPos t = h.absolutePos(target);
            double nearestLeft = left.stream().mapToDouble(p -> p.distSqr(t)).min().orElseThrow();
            for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(corner), h.absolutePos(corner.offset(4, 4, 4)))) {
                if (!left.contains(p)) {
                    h.assertTrue(p.distSqr(t) <= nearestLeft, "mined " + p + " while a nearer ore was left");
                }
            }
        });
    }

    // ---------------------------------------------------------------- when it does not apply

    /** An ordinary pickaxe mines one block. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void ordinaryPickaxeMinesOne(GameTestHelper helper) {
        minesOnlyOne(helper, new ItemStack(Items.NETHERITE_PICKAXE), false);
    }

    /** Sneaking mines one block. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void sneakingMinesOne(GameTestHelper helper) {
        minesOnlyOne(helper, Arena.veinPickaxe(helper), true);
    }

    /** A pickaxe too weak to harvest the ore (stone on diamond) mines one block, so the vein isn't wasted. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void tooWeakPickaxeMinesOne(GameTestHelper helper) {
        BlockPos a = new BlockPos(16, 10, 16);
        mine(helper, () -> line(helper, a, Blocks.DIAMOND_ORE, 4), Arena.veinPickaxe(helper, Items.STONE_PICKAXE), a,
                h -> h.assertTrue(Arena.find(h, DIAMOND).size() == 3, "expected 3 diamond ores left"));
    }

    /** Veinbreaking forced onto a shovel does nothing. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void notAPickaxeMinesOne(GameTestHelper helper) {
        minesOnlyOne(helper, Arena.veinPickaxe(helper, Items.NETHERITE_SHOVEL), false);
    }

    /** Rock is not ore: mining stone with Veinbreaking takes one block. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void rockIsNotAVein(GameTestHelper helper) {
        Object[] state = new Object[2];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            state[1] = Arena.find(helper, ROCK).size();
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.veinPickaxe(helper));
            state[0] = player;
            player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(16, 20, 16)));
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(Arena.find(helper, ROCK).size() == (int) state[1] - 1, "expected exactly one stone mined");
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    private static void minesOnlyOne(GameTestHelper helper, ItemStack tool, boolean sneaking) {
        BlockPos a = new BlockPos(16, 10, 16);
        Object[] state = new Object[1];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            line(helper, a, Blocks.IRON_ORE, 5);
            ServerPlayer player = Arena.survivalPlayer(helper, tool);
            player.setShiftKeyDown(sneaking);
            state[0] = player;
            player.gameMode.destroyBlock(helper.absolutePos(a));
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(a)).isAir(), "the mined ore itself should break normally");
            helper.assertTrue(Arena.find(helper, IRON).size() == 4, "expected 4 iron ores left, found " + Arena.find(helper, IRON).size());
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- drops, durability, protection

    /** Each block drops as if mined by hand: 20 coal ores give 20 coal, and more experience than one ore can (0-2 each). */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void dropsLootAndExperience(GameTestHelper helper) {
        BlockPos a = new BlockPos(6, 10, 16);
        mine(helper, () -> line(helper, a, Blocks.COAL_ORE, 20), Arena.veinPickaxe(helper), a, h -> {
            h.assertTrue(Arena.droppedCount(h, Items.COAL) == 20, "expected 20 coal, got " + Arena.droppedCount(h, Items.COAL));
            h.assertTrue(Arena.droppedExperience(h) > 2, "expected experience from 20 coal ores, got " + Arena.droppedExperience(h));
        });
    }

    /** Silk Touch applies to every block of the vein. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void silkTouchAppliesToVein(GameTestHelper helper) {
        BlockPos a = new BlockPos(16, 10, 16);
        ItemStack pickaxe = Arena.veinPickaxe(helper);
        pickaxe.enchant(Arena.enchantment(helper, Enchantments.SILK_TOUCH), 1);
        mine(helper, () -> line(helper, a, Blocks.COAL_ORE, 6), pickaxe, a, h -> {
            h.assertTrue(Arena.droppedCount(h, Items.COAL_ORE) == 6, "expected 6 coal ore blocks, got " + Arena.droppedCount(h, Items.COAL_ORE));
            h.assertTrue(Arena.droppedCount(h, Items.COAL) == 0, "no coal should drop with Silk Touch");
        });
    }

    /** A pickaxe with 3 uses left mines the first block and the two nearest, then breaks and stops. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void stopsWhenPickaxeBreaks(GameTestHelper helper) {
        BlockPos a = new BlockPos(10, 10, 16);
        ItemStack pickaxe = Arena.veinPickaxe(helper, Items.IRON_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 3);
        mine(helper, () -> line(helper, a, Blocks.IRON_ORE, 10), pickaxe, a, h -> {
            Set<BlockPos> expected = new HashSet<>();
            for (int i = 3; i < 10; i++) {
                expected.add(h.absolutePos(a.east(i)));
            }
            assertLeft(h, IRON, expected);
            h.assertTrue(pickaxe.isEmpty(), "pickaxe should have broken");
        });
    }

    /** A protection mod canceling one block's break keeps that block; the rest of the vein is mined. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void skipsProtectedOre(GameTestHelper helper) {
        BlockPos a = new BlockPos(16, 10, 16);
        BlockPos guarded = a.east(2);
        mine(helper, () -> {
            line(helper, a, Blocks.IRON_ORE, 5);
            TestProtection.PROTECTED.add(helper.absolutePos(guarded));
        }, Arena.veinPickaxe(helper), a, h -> {
            TestProtection.PROTECTED.remove(h.absolutePos(guarded));
            assertLeft(h, IRON, Set.of(h.absolutePos(guarded)));
        });
    }

    // ---------------------------------------------------------------- registration / acquisition

    @GameTest(template = ARENA)
    public static void enchantmentIsRegisteredForPickaxes(GameTestHelper helper) {
        Holder<Enchantment> vein = Arena.veinbreaking(helper);
        helper.assertTrue(vein.value().getMaxLevel() == 1, "Veinbreaking should have one level");
        helper.assertTrue(vein.value().canEnchant(new ItemStack(Items.IRON_PICKAXE)), "Veinbreaking should apply to pickaxes");
        helper.assertTrue(!vein.value().canEnchant(new ItemStack(Items.IRON_AXE)), "Veinbreaking should not apply to axes");
        helper.assertTrue(vein.is(EnchantmentTags.IN_ENCHANTING_TABLE), "Veinbreaking should be in the enchanting table pool");
        helper.assertTrue(vein.is(EnchantmentTags.TRADEABLE), "Veinbreaking should be tradeable by librarians");
        helper.assertTrue(vein.is(EnchantmentTags.ON_RANDOM_LOOT), "Veinbreaking should appear in random loot");
        helper.assertTrue(!vein.is(EnchantmentTags.TREASURE), "Veinbreaking should not be a treasure enchantment");
        helper.succeed();
    }

    /** Roll level-30 enchanting table offers for a diamond pickaxe and confirm Veinbreaking can come up. */
    @GameTest(template = ARENA)
    public static void enchantingTableCanOfferVeinbreaking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Enchantment> vein = Arena.veinbreaking(helper);
        var pool = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow();
        RandomSource random = RandomSource.create(42);
        int hits = 0;
        int rolls = 2000;
        for (int i = 0; i < rolls; i++) {
            List<EnchantmentInstance> offer = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_PICKAXE), 30, pool.stream());
            if (offer.stream().anyMatch(e -> e.enchantment.equals(vein))) {
                hits++;
            }
        }
        LOGGER.info("[vein-test] enchanting table (level 30, diamond pickaxe): Veinbreaking in {} of {} offers", hits, rolls);
        helper.assertTrue(hits > 0, "Veinbreaking never offered in " + rolls + " level-30 rolls");
        // Early game: a table with no bookshelves (level 5) can offer it too.
        int early = 0;
        for (int i = 0; i < rolls; i++) {
            List<EnchantmentInstance> offer = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_PICKAXE), 5, pool.stream());
            if (offer.stream().anyMatch(e -> e.enchantment.equals(vein))) {
                early++;
            }
        }
        LOGGER.info("[vein-test] enchanting table (level 5, diamond pickaxe): Veinbreaking in {} of {} offers", early, rolls);
        helper.assertTrue(early > 0, "Veinbreaking never offered in " + rolls + " level-5 rolls");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** Reset, build the scene, mine {@code target} with {@code tool}, wait, then run {@code check}. */
    private static void mine(GameTestHelper helper, Runnable scene, ItemStack tool, BlockPos target, Consumer<GameTestHelper> check) {
        Object[] state = new Object[1];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            scene.run();
            ServerPlayer player = Arena.survivalPlayer(helper, tool);
            state[0] = player;
            player.gameMode.destroyBlock(helper.absolutePos(target));
        }).thenIdle(2).thenExecute(() -> {
            check.accept(helper);
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    /** {@code length} blocks in a row going east from {@code start}. */
    private static void line(GameTestHelper helper, BlockPos start, Block block, int length) {
        for (int i = 0; i < length; i++) {
            Arena.set(helper, start.east(i), block);
        }
    }

    private static void assertLeft(GameTestHelper helper, Set<Block> ore, Set<BlockPos> expected) {
        Set<BlockPos> left = Arena.find(helper, ore);
        helper.assertTrue(left.equals(expected), "expected " + expected.size() + " ore left, found " + left.size()
                + " (unexpected " + diffSize(left, expected) + ", missing " + diffSize(expected, left) + ")");
    }

    /** The blocks of {@code ores} connected to {@code start} through faces, edges or corners. */
    private static Set<BlockPos> component(Set<BlockPos> ores, BlockPos start) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (ores.contains(n) && found.add(n.immutable())) {
                    queue.add(n.immutable());
                }
            }
        }
        return found;
    }

    private static int diffSize(Set<BlockPos> a, Set<BlockPos> b) {
        Set<BlockPos> d = new HashSet<>(a);
        d.removeAll(b);
        return d.size();
    }
}
