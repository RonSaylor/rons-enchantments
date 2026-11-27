package com.ronsmods.harvesting.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.ronsmods.harvesting.RonsHarvesting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game checks for Harvesting. Run with {@code gradlew :harvesting:runGameTestServer}.
 *
 * <p>Every break goes through {@code ServerPlayerGameMode.destroyBlock}, the same path a player's left-click takes.
 * Seed accounting compares what the blocks dropped before Harvesting ({@link TestHooks#RAW_DROPS}) with what is left
 * on the ground: exactly one seed per replanted plant must be missing.
 */
@GameTestHolder(RonsHarvesting.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarvestingGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARENA = "arena";
    /** The harvested plant plus at most 80 more. */
    private static final int MAX_TOTAL = 81;

    // ---------------------------------------------------------------- crops harvest and replant

    /**
     * A full 9x9 plot around its water: all 80 wheat harvested and replanted at age 0, 80 wheat dropped, one seed
     * per crop taken from the drops, and no hoe durability (vanilla hoes take none on crops).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void harvestsWheatPlot(GameTestHelper helper) {
        ItemStack hoe = Arena.harvestingHoe(helper);
        harvest(helper, () -> {
            for (int x = 12; x <= 20; x++) {
                for (int z = 12; z <= 20; z++) {
                    if (x == 16 && z == 16) {
                        Arena.set(helper, new BlockPos(16, Arena.GROUND - 1, 16), Blocks.WATER.defaultBlockState());
                    } else {
                        Arena.ripeCrop(helper, x, z, Blocks.WHEAT);
                    }
                }
            }
        }, hoe, Arena.at(13, 13), p -> {}, h -> {
            assertCount(h, "replanted wheat", Arena.cropsAtAge(h, Blocks.WHEAT, 0).size(), 80);
            assertCount(h, "wheat blocks", Arena.find(h, Blocks.WHEAT).size(), 80);
            assertCount(h, "wheat dropped", Arena.droppedCount(h, Items.WHEAT), 80);
            assertSeedsTaken(h, Items.WHEAT_SEEDS, 80);
            assertCount(h, "hoe damage", hoe.getDamageValue(), 0);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200) public static void harvestsCarrots(GameTestHelper h) { harvestsCropSquare(h, Blocks.CARROTS, Items.CARROT); }
    @GameTest(template = ARENA, timeoutTicks = 200) public static void harvestsPotatoes(GameTestHelper h) { harvestsCropSquare(h, Blocks.POTATOES, Items.POTATO); }
    @GameTest(template = ARENA, timeoutTicks = 200) public static void harvestsBeetroot(GameTestHelper h) { harvestsCropSquare(h, Blocks.BEETROOTS, Items.BEETROOT_SEEDS); }

    /** A 3x3 square of a ripe crop: all 9 replanted at age 0, one seed (carrot, potato...) taken per crop. */
    private static void harvestsCropSquare(GameTestHelper helper, Block crop, Item seed) {
        harvest(helper, () -> square(helper, 15, 15, 3, (x, z) -> Arena.ripeCrop(helper, x, z, crop)), Arena.harvestingHoe(helper), Arena.at(16, 16), p -> {}, h -> {
            assertCount(h, "replanted", Arena.cropsAtAge(h, crop, 0).size(), 9);
            assertSeedsTaken(h, seed, 9);
        });
    }

    /** A 3x3 square of ripe nether wart on soul sand: all replanted at age 0, one wart taken per plant. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void harvestsNetherWart(GameTestHelper helper) {
        harvest(helper, () -> square(helper, 15, 15, 3, (x, z) -> Arena.netherWart(helper, x, z, NetherWartBlock.MAX_AGE)),
                Arena.harvestingHoe(helper), Arena.at(15, 15), p -> {}, h -> {
                    assertCount(h, "replanted wart", Arena.find(h, s -> s.is(Blocks.NETHER_WART) && s.getValue(NetherWartBlock.AGE) == 0).size(), 9);
                    assertSeedsTaken(h, Items.NETHER_WART, 9);
                });
    }

    /**
     * Ripe cocoa on all four sides of a 3-high jungle trunk (12 pods): all replanted at age 0 on the same side,
     * including the far side of the log. Pods on a second trunk three blocks away are left.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void harvestsCocoa(GameTestHelper helper) {
        BlockPos log = Arena.at(16, 16);
        BlockPos other = Arena.at(20, 16);
        harvest(helper, () -> {
            for (BlockPos trunk : List.of(log, other)) {
                for (int y = 0; y < 3; y++) {
                    Arena.set(helper, trunk.above(y), Blocks.JUNGLE_LOG.defaultBlockState());
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        Arena.cocoa(helper, trunk.above(y), side, CocoaBlock.MAX_AGE);
                    }
                }
            }
        }, Arena.harvestingHoe(helper), log.north(), p -> {}, h -> {
            for (int y = 0; y < 3; y++) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockState pod = Arena.get(h, log.above(y).relative(side));
                    h.assertTrue(pod.is(Blocks.COCOA) && pod.getValue(CocoaBlock.AGE) == 0 && pod.getValue(CocoaBlock.FACING) == side.getOpposite(),
                            "pod " + side + " at height " + y + " should be replanted facing the log, was " + pod);
                    BlockState far = Arena.get(h, other.above(y).relative(side));
                    h.assertTrue(far.is(Blocks.COCOA) && far.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE, "the other trunk's pods should be left");
                }
            }
            assertSeedsTaken(h, Items.COCOA_BEANS, 12);
        });
    }

    /**
     * A row of 10 melon stems, three of them with a melon (at either end and in the middle). Harvesting one melon takes
     * the other two, connected through the stems. Every stem stays. A hand-placed melon (no stem) next to a fruit,
     * and a pumpkin grown by a pumpkin stem at the end of the row, are left.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void harvestsMelonsThroughStems(GameTestHelper helper) {
        harvest(helper, () -> {
            for (int x = 8; x <= 17; x++) {
                if (x == 8 || x == 12 || x == 17) {
                    Arena.attachedFruit(helper, x, 16, Direction.SOUTH, Blocks.MELON);
                } else {
                    Arena.stem(helper, x, 16, Blocks.MELON, StemBlock.MAX_AGE);
                }
            }
            Arena.set(helper, Arena.at(12, 18), Blocks.MELON.defaultBlockState()); // placed by hand, beside the middle melon
            Arena.attachedFruit(helper, 18, 16, Direction.SOUTH, Blocks.PUMPKIN);
        }, Arena.harvestingHoe(helper), Arena.at(8, 17), p -> {}, h -> {
            assertCount(h, "melons left", Arena.find(h, Blocks.MELON).size(), 1);
            h.assertTrue(Arena.get(h, Arena.at(12, 18)).is(Blocks.MELON), "the hand-placed melon should be left");
            h.assertTrue(Arena.get(h, Arena.at(18, 17)).is(Blocks.PUMPKIN), "the pumpkin should be left");
            assertCount(h, "melon stems", Arena.find(h, s -> s.is(Blocks.MELON_STEM) || s.is(Blocks.ATTACHED_MELON_STEM)).size(), 10);
            h.assertTrue(Arena.droppedCount(h, Items.MELON_SLICE) >= 9, "three melons should drop at least 9 slices");
            h.assertTrue(Arena.droppedCount(h, Items.MELON_SEEDS) == 0, "no stem should be broken");
        });
    }

    /** Pumpkins on a row of stems are all harvested; nothing is planted where they stood. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void harvestsPumpkins(GameTestHelper helper) {
        harvest(helper, () -> {
            for (int x = 12; x < 16; x++) {
                Arena.attachedFruit(helper, x, 16, Direction.NORTH, Blocks.PUMPKIN);
            }
        }, Arena.harvestingHoe(helper), Arena.at(12, 15), p -> {}, h -> {
            assertCount(h, "pumpkins left", Arena.find(h, Blocks.PUMPKIN).size(), 0);
            assertCount(h, "pumpkins dropped", Arena.droppedCount(h, Items.PUMPKIN), 4);
            for (int x = 12; x < 16; x++) {
                h.assertTrue(Arena.get(h, Arena.at(x, 15)).isAir(), "nothing should be planted where a pumpkin was");
            }
        });
    }

    /** Breaking a melon no stem points at (placed by hand) is a normal one-block break, even beside a melon field. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void handPlacedMelonBreaksAlone(GameTestHelper helper) {
        harvest(helper, () -> {
            for (int x = 12; x < 16; x++) {
                Arena.attachedFruit(helper, x, 16, Direction.SOUTH, Blocks.MELON);
            }
            Arena.set(helper, Arena.at(12, 18), Blocks.MELON.defaultBlockState());
        }, Arena.harvestingHoe(helper), Arena.at(12, 18), p -> {}, h -> assertCount(h, "melons left", Arena.find(h, Blocks.MELON).size(), 4));
    }

    // ---------------------------------------------------------------- Harvesting I

    /** Harvesting I harvests the field but replants nothing, and every seed the crops dropped stays on the ground. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void levelOneHarvestsWithoutReplanting(GameTestHelper helper) {
        harvest(helper, () -> square(helper, 15, 15, 3, (x, z) -> Arena.ripeCrop(helper, x, z, Blocks.WHEAT)),
                Arena.harvestingHoe(helper, Items.NETHERITE_HOE, 1), Arena.at(16, 16), p -> {}, h -> {
                    assertCount(h, "wheat left", Arena.find(h, Blocks.WHEAT).size(), 0);
                    assertCount(h, "wheat dropped", Arena.droppedCount(h, Items.WHEAT), 9);
                    assertSeedsTaken(h, Items.WHEAT_SEEDS, 0);
                    h.assertTrue(Arena.rawDrops(h, Items.WHEAT_SEEDS) > 0, "the crops should have dropped seeds");
                });
    }

    /** Harvesting I on cocoa and nether wart: harvested, not replanted. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void levelOneLeavesWartAndCocoaUnplanted(GameTestHelper helper) {
        BlockPos log = Arena.at(10, 10);
        Object[] state = new Object[1];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            square(helper, 15, 15, 3, (x, z) -> Arena.netherWart(helper, x, z, NetherWartBlock.MAX_AGE));
            Arena.set(helper, log, Blocks.JUNGLE_LOG.defaultBlockState());
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Arena.cocoa(helper, log, side, CocoaBlock.MAX_AGE);
            }
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.harvestingHoe(helper, Items.NETHERITE_HOE, 1));
            state[0] = player;
            player.gameMode.destroyBlock(helper.absolutePos(Arena.at(15, 15)));
            player.gameMode.destroyBlock(helper.absolutePos(log.north()));
        }).thenIdle(2).thenExecute(() -> {
            assertCount(helper, "nether wart left", Arena.find(helper, Blocks.NETHER_WART).size(), 0);
            assertCount(helper, "cocoa left", Arena.find(helper, Blocks.COCOA).size(), 0);
            assertSeedsTaken(helper, Items.NETHER_WART, 0);
            assertSeedsTaken(helper, Items.COCOA_BEANS, 0);
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- drop rates match vanilla

    /*
     * Vanilla's crop loot tables give a ripe wheat 1 wheat and 1 + binomial(3 + Fortune level, 4/7) seeds, and a ripe
     * carrot 2 + binomial(3 + Fortune, 4/7) carrots (one from each loot pool; the bonus adds to the second). Fortune comes from whatever tool breaks the crop, so a Fortune hoe
     * and a Fortune pickaxe drop the same. Each test harvests 12 fields of 81 ripe crops (972 crops) and checks the mean
     * against that formula, within 4 standard errors. The pickaxe and plain-hoe runs are the vanilla controls.
     */
    private static final int RATE_ROUNDS = 12;
    private static final double BONUS_CHANCE = 0.5714286;

    @GameTest(template = ARENA, timeoutTicks = 1200) public static void wheatSeedsVanillaPickaxeFortune3(GameTestHelper h) { dropRate(h, "vanilla pickaxe, Fortune III", Items.NETHERITE_PICKAXE, 0, 3, Blocks.WHEAT, Items.WHEAT_SEEDS, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void wheatSeedsVanillaHoeFortune3(GameTestHelper h) { dropRate(h, "plain hoe, Fortune III", Items.NETHERITE_HOE, 0, 3, Blocks.WHEAT, Items.WHEAT_SEEDS, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void wheatSeedsHarvesting1(GameTestHelper h) { dropRate(h, "Harvesting I", Items.NETHERITE_HOE, 1, 0, Blocks.WHEAT, Items.WHEAT_SEEDS, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void wheatSeedsHarvesting1Fortune3(GameTestHelper h) { dropRate(h, "Harvesting I, Fortune III", Items.NETHERITE_HOE, 1, 3, Blocks.WHEAT, Items.WHEAT_SEEDS, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void carrotsHarvesting1Fortune3(GameTestHelper h) { dropRate(h, "Harvesting I, Fortune III", Items.NETHERITE_HOE, 1, 3, Blocks.CARROTS, Items.CARROT, 2); }
    /** Harvesting II drops exactly the vanilla amount minus the one seed per crop it replants with. */
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void wheatSeedsHarvesting2Fortune3(GameTestHelper h) { dropRate(h, "Harvesting II, Fortune III", Items.NETHERITE_HOE, 2, 3, Blocks.WHEAT, Items.WHEAT_SEEDS, 0); }

    /**
     * Harvest {@link #RATE_ROUNDS} 9x9 fields of ripe {@code crop} and compare the mean {@code item} on the ground per
     * crop with {@code base + binomial(3 + fortune, 4/7)}. With {@code harvesting == 0} every crop is broken by hand
     * with {@code tool}; otherwise one break harvests the whole field.
     */
    private static void dropRate(GameTestHelper helper, String label, Item tool, int harvesting, int fortune, Block crop, Item item, int base) {
        int[] totals = new int[2]; // crops harvested, items on the ground
        GameTestSequence sequence = helper.startSequence();
        for (int round = 0; round < RATE_ROUNDS; round++) {
            Object[] state = new Object[1];
            sequence.thenExecute(() -> {
                Arena.reset(helper);
                square(helper, 12, 12, 9, (x, z) -> Arena.ripeCrop(helper, x, z, crop));
                ItemStack stack = harvesting > 0 ? Arena.harvestingHoe(helper, tool, harvesting) : new ItemStack(tool);
                if (fortune > 0) {
                    stack.enchant(Arena.enchantment(helper, Enchantments.FORTUNE), fortune);
                }
                ServerPlayer player = Arena.survivalPlayer(helper, stack);
                state[0] = player;
                if (harvesting > 0) {
                    player.gameMode.destroyBlock(helper.absolutePos(Arena.at(16, 16)));
                } else {
                    square(helper, 12, 12, 9, (x, z) -> player.gameMode.destroyBlock(helper.absolutePos(Arena.at(x, z))));
                }
            }).thenIdle(2).thenExecute(() -> {
                int ripeLeft = Arena.cropsAtAge(helper, crop, ((CropBlock) crop).getMaxAge()).size();
                helper.assertTrue(ripeLeft == 0, label + ": " + ripeLeft + " ripe crops were not harvested");
                totals[0] += 81;
                totals[1] += Arena.droppedCount(helper, item);
                Arena.removePlayer((ServerPlayer) state[0]);
            });
        }
        sequence.thenExecute(() -> {
            int rolls = 3 + fortune;
            double expected = base + rolls * BONUS_CHANCE;
            double standardError = Math.sqrt(rolls * BONUS_CHANCE * (1 - BONUS_CHANCE) / totals[0]);
            double mean = (double) totals[1] / totals[0];
            LOGGER.info("[harvest-test] drop rate {} on {}: {} {} from {} crops = {} per crop (vanilla formula {}, +/- {})", label,
                    crop.getName().getString(), totals[1], item, totals[0], String.format("%.3f", mean), String.format("%.3f", expected),
                    String.format("%.3f", 4 * standardError));
            helper.assertTrue(Math.abs(mean - expected) <= 4 * standardError, label + ": " + String.format("%.3f", mean) + " " + item
                    + " per crop, vanilla gives " + String.format("%.3f", expected));
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- field shape rules

    /** In a row aged 7,7,3,0,7,5,7 the ripe crops are harvested across the unripe ones, which keep their age. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void unripeLeftRipeAcrossThemTaken(GameTestHelper helper) {
        int[] ages = {7, 7, 3, 0, 7, 5, 7};
        int[] after = {0, 0, 3, 0, 0, 5, 0};
        harvest(helper, () -> {
            for (int i = 0; i < ages.length; i++) {
                Arena.crop(helper, 10 + i, 16, Blocks.WHEAT, ages[i]);
            }
        }, Arena.harvestingHoe(helper), Arena.at(10, 16), p -> {}, h -> {
            for (int i = 0; i < ages.length; i++) {
                BlockState s = Arena.get(h, Arena.at(10 + i, 16));
                h.assertTrue(s.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(s) == after[i], "crop " + i + " should be age " + after[i] + ", was " + s);
            }
            assertCount(h, "wheat dropped", Arena.droppedCount(h, Items.WHEAT), 4);
        });
    }

    /** Breaking an unripe crop is a normal break: it isn't replanted and the ripe crops beside it stay. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void unripeCropBreaksAlone(GameTestHelper helper) {
        harvest(helper, () -> {
            Arena.crop(helper, 10, 16, Blocks.WHEAT, 3);
            for (int x = 11; x < 15; x++) {
                Arena.ripeCrop(helper, x, 16, Blocks.WHEAT);
            }
        }, Arena.harvestingHoe(helper), Arena.at(10, 16), p -> {}, h -> {
            h.assertTrue(Arena.get(h, Arena.at(10, 16)).isAir(), "the unripe crop should break and not be replanted");
            assertCount(h, "ripe wheat left", Arena.cropsAtAge(h, Blocks.WHEAT, 7).size(), 4);
        });
    }

    /** Crops touching only at a corner are one field, including a crop one block up a terrace step. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void diagonalAndTerracedCropsAreOneField(GameTestHelper helper) {
        harvest(helper, () -> {
            Arena.ripeCrop(helper, 10, 10, Blocks.WHEAT);
            Arena.ripeCrop(helper, 11, 11, Blocks.WHEAT);
            Arena.set(helper, new BlockPos(12, Arena.GROUND, 12), Blocks.FARMLAND.defaultBlockState());
            Arena.set(helper, new BlockPos(12, Arena.GROUND + 1, 12), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        }, Arena.harvestingHoe(helper), Arena.at(10, 10), p -> {}, h -> {
            assertCount(h, "replanted wheat", Arena.cropsAtAge(h, Blocks.WHEAT, 0).size(), 3);
        });
    }

    /** One empty farmland between two crops separates them. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void gapEndsField(GameTestHelper helper) {
        harvest(helper, () -> {
            Arena.ripeCrop(helper, 10, 16, Blocks.WHEAT);
            Arena.ripeCrop(helper, 11, 16, Blocks.WHEAT);
            Arena.farmland(helper, 12, 16);
            Arena.ripeCrop(helper, 13, 16, Blocks.WHEAT);
        }, Arena.harvestingHoe(helper), Arena.at(10, 16), p -> {}, h -> {
            assertCount(h, "replanted wheat", Arena.cropsAtAge(h, Blocks.WHEAT, 0).size(), 2);
            h.assertTrue(Arena.cropsAtAge(h, Blocks.WHEAT, 7).equals(Set.of(h.absolutePos(Arena.at(13, 16)))), "the crop past the gap should be left");
        });
    }

    /** A ripe carrot row beside a wheat field is a different crop and is left. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void otherCropsAreLeft(GameTestHelper helper) {
        harvest(helper, () -> {
            square(helper, 10, 10, 3, (x, z) -> Arena.ripeCrop(helper, x, z, Blocks.WHEAT));
            for (int x = 10; x < 13; x++) {
                Arena.ripeCrop(helper, x, 13, Blocks.CARROTS);
            }
        }, Arena.harvestingHoe(helper), Arena.at(10, 10), p -> {}, h -> {
            assertCount(h, "replanted wheat", Arena.cropsAtAge(h, Blocks.WHEAT, 0).size(), 9);
            assertCount(h, "ripe carrots left", Arena.cropsAtAge(h, Blocks.CARROTS, ((CropBlock) Blocks.CARROTS).getMaxAge()).size(), 3);
        });
    }

    /** A 15x15 ripe field (225 crops) gives the harvested crop plus the 80 nearest. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void hugeFieldIsCapped(GameTestHelper helper) {
        BlockPos target = Arena.at(16, 16);
        harvest(helper, () -> square(helper, 9, 9, 15, (x, z) -> Arena.ripeCrop(helper, x, z, Blocks.WHEAT)), Arena.harvestingHoe(helper), target, p -> {}, h -> {
            Set<BlockPos> harvested = Arena.cropsAtAge(h, Blocks.WHEAT, 0);
            Set<BlockPos> left = Arena.cropsAtAge(h, Blocks.WHEAT, 7);
            assertCount(h, "harvested", harvested.size(), MAX_TOTAL);
            assertCount(h, "left", left.size(), 225 - MAX_TOTAL);
            BlockPos t = h.absolutePos(target);
            double nearestLeft = left.stream().mapToDouble(p -> p.distSqr(t)).min().orElseThrow();
            for (BlockPos p : harvested) {
                h.assertTrue(p.distSqr(t) <= nearestLeft, "harvested " + p + " while a nearer crop was left");
            }
        });
    }

    // ---------------------------------------------------------------- when it does not apply

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void ordinaryHoeBreaksOne(GameTestHelper helper) {
        breaksOnlyOne(helper, new ItemStack(Items.NETHERITE_HOE), p -> {});
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void sneakingBreaksOne(GameTestHelper helper) {
        breaksOnlyOne(helper, Arena.harvestingHoe(helper), p -> p.setShiftKeyDown(true));
    }

    /** Harvesting forced onto a shovel does nothing. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void notAHoeBreaksOne(GameTestHelper helper) {
        breaksOnlyOne(helper, Arena.harvestingHoe(helper, Items.NETHERITE_SHOVEL), p -> {});
    }

    /** The broken crop breaks normally (not replanted) and the 4 ripe crops beside it stay. */
    private static void breaksOnlyOne(GameTestHelper helper, ItemStack tool, Consumer<ServerPlayer> setup) {
        harvest(helper, () -> {
            for (int x = 10; x < 15; x++) {
                Arena.ripeCrop(helper, x, 16, Blocks.WHEAT);
            }
        }, tool, Arena.at(10, 16), setup, h -> {
            h.assertTrue(Arena.get(h, Arena.at(10, 16)).isAir(), "the broken crop should not be replanted");
            assertCount(h, "ripe wheat left", Arena.cropsAtAge(h, Blocks.WHEAT, 7).size(), 4);
        });
    }

    // ---------------------------------------------------------------- durability, protection, game modes

    /** Melons cost durability (they have hardness). A hoe with 3 uses left takes three melons, then breaks and stops. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void stopsWhenHoeBreaks(GameTestHelper helper) {
        ItemStack hoe = Arena.harvestingHoe(helper, Items.IRON_HOE);
        hoe.setDamageValue(hoe.getMaxDamage() - 3);
        harvest(helper, () -> {
            for (int x = 10; x < 20; x++) {
                Arena.attachedFruit(helper, x, 16, Direction.SOUTH, Blocks.MELON);
            }
        }, hoe, Arena.at(10, 17), p -> {}, h -> {
            Set<BlockPos> expected = new HashSet<>();
            for (int x = 13; x < 20; x++) {
                expected.add(h.absolutePos(Arena.at(x, 17)));
            }
            h.assertTrue(Arena.find(h, Blocks.MELON).equals(expected), "expected the 7 farthest melons left, found " + Arena.find(h, Blocks.MELON).size());
            h.assertTrue(hoe.isEmpty(), "hoe should have broken");
        });
    }

    /** A protection mod canceling one crop's break keeps it ripe; the rest of the field is harvested. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void skipsProtectedCrop(GameTestHelper helper) {
        BlockPos guarded = Arena.at(12, 16);
        harvest(helper, () -> {
            for (int x = 10; x < 15; x++) {
                Arena.ripeCrop(helper, x, 16, Blocks.WHEAT);
            }
            TestHooks.NO_BREAK.add(helper.absolutePos(guarded));
        }, Arena.harvestingHoe(helper), Arena.at(10, 16), p -> {}, h -> {
            TestHooks.NO_BREAK.remove(h.absolutePos(guarded));
            h.assertTrue(Arena.cropsAtAge(h, Blocks.WHEAT, 7).equals(Set.of(h.absolutePos(guarded))), "only the protected crop should be left ripe");
            assertCount(h, "replanted wheat", Arena.cropsAtAge(h, Blocks.WHEAT, 0).size(), 4);
        });
    }

    /** A protection mod refusing one replant leaves that spot empty and its seed on the ground. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void refusedReplantDropsTheSeed(GameTestHelper helper) {
        BlockPos guarded = Arena.at(11, 16);
        harvest(helper, () -> {
            for (int x = 10; x < 13; x++) {
                Arena.ripeCrop(helper, x, 16, Blocks.WHEAT);
            }
            TestHooks.NO_PLACE.add(helper.absolutePos(guarded));
        }, Arena.harvestingHoe(helper), Arena.at(10, 16), p -> {}, h -> {
            TestHooks.NO_PLACE.remove(h.absolutePos(guarded));
            h.assertTrue(Arena.get(h, guarded).isAir(), "the refused spot should stay empty");
            assertCount(h, "replanted wheat", Arena.cropsAtAge(h, Blocks.WHEAT, 0).size(), 2);
            assertSeedsTaken(h, Items.WHEAT_SEEDS, 2);
        });
    }

    /** In creative nothing drops, and the field is still replanted. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void creativeReplantsWithoutSeeds(GameTestHelper helper) {
        harvest(helper, () -> {
            for (int x = 10; x < 13; x++) {
                Arena.ripeCrop(helper, x, 16, Blocks.WHEAT);
            }
        }, Arena.harvestingHoe(helper), Arena.at(10, 16), p -> p.setGameMode(GameType.CREATIVE), h -> {
            assertCount(h, "replanted wheat", Arena.cropsAtAge(h, Blocks.WHEAT, 0).size(), 3);
            assertCount(h, "wheat dropped", Arena.droppedCount(h, Items.WHEAT), 0);
        });
    }

    // ---------------------------------------------------------------- registration / acquisition

    @GameTest(template = ARENA)
    public static void enchantmentIsRegisteredForHoes(GameTestHelper helper) {
        Holder<Enchantment> harvesting = Arena.harvesting(helper);
        helper.assertTrue(harvesting.value().getMaxLevel() == 2, "Harvesting should have two levels");
        // Vanilla's Fortune already supports hoes (#minecraft:enchantable/mining_loot); make sure nothing changed that.
        helper.assertTrue(Arena.enchantment(helper, Enchantments.FORTUNE).value().canEnchant(new ItemStack(Items.DIAMOND_HOE)), "Fortune should apply to hoes");
        helper.assertTrue(Arena.enchantment(helper, Enchantments.FORTUNE).value().isPrimaryItem(new ItemStack(Items.DIAMOND_HOE)),
                "Fortune should be offered for hoes at the enchanting table");
        helper.assertTrue(harvesting.value().canEnchant(new ItemStack(Items.IRON_HOE)), "Harvesting should apply to hoes");
        helper.assertTrue(!harvesting.value().canEnchant(new ItemStack(Items.IRON_SHOVEL)), "Harvesting should not apply to shovels");
        helper.assertTrue(harvesting.is(EnchantmentTags.IN_ENCHANTING_TABLE), "Harvesting should be in the enchanting table pool");
        helper.assertTrue(harvesting.is(EnchantmentTags.TRADEABLE), "Harvesting should be tradeable by librarians");
        helper.assertTrue(harvesting.is(EnchantmentTags.ON_RANDOM_LOOT), "Harvesting should appear in random loot");
        helper.assertTrue(!harvesting.is(EnchantmentTags.TREASURE), "Harvesting should not be a treasure enchantment");
        helper.succeed();
    }

    /** Roll level-30 enchanting table offers for a diamond hoe and confirm Harvesting can come up. */
    @GameTest(template = ARENA)
    public static void enchantingTableCanOfferHarvesting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Enchantment> harvesting = Arena.harvesting(helper);
        var pool = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow();
        RandomSource random = RandomSource.create(42);
        int hits = 0;
        int level2 = 0;
        int fortune = 0;
        int rolls = 2000;
        for (int i = 0; i < rolls; i++) {
            List<EnchantmentInstance> offer = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_HOE), 30, pool.stream());
            if (offer.stream().anyMatch(e -> e.enchantment.equals(harvesting))) {
                hits++;
            }
            if (offer.stream().anyMatch(e -> e.enchantment.equals(harvesting) && e.level == 2)) {
                level2++;
            }
            if (offer.stream().anyMatch(e -> e.enchantment.is(Enchantments.FORTUNE))) {
                fortune++;
            }
        }
        LOGGER.info("[harvest-test] enchanting table (level 30, diamond hoe): Harvesting in {} of {} offers, Harvesting II in {}, Fortune in {}",
                hits, rolls, level2, fortune);
        helper.assertTrue(hits > 0, "Harvesting never offered in " + rolls + " level-30 rolls");
        helper.assertTrue(level2 == hits, "Harvesting II should replace Harvesting I at table level 30 (mid-game and up)");
        helper.assertTrue(fortune > 0, "Fortune never offered for a hoe");
        // Early game: a table with no bookshelves (level 5) offers Harvesting I, never Harvesting II.
        int early = 0;
        for (int i = 0; i < rolls; i++) {
            List<EnchantmentInstance> offer = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_HOE), 5, pool.stream());
            if (offer.stream().anyMatch(e -> e.enchantment.equals(harvesting))) {
                early++;
            }
            helper.assertTrue(offer.stream().noneMatch(e -> e.enchantment.equals(harvesting) && e.level == 2), "Harvesting II offered at level 5");
        }
        LOGGER.info("[harvest-test] enchanting table (level 5, diamond hoe): Harvesting I in {} of {} offers", early, rolls);
        helper.assertTrue(early > 0, "Harvesting I never offered in " + rolls + " level-5 rolls");
        // Harvesting II stays mid-game (it needs level 20+): never at table level 10.
        for (int i = 0; i < 500; i++) {
            List<EnchantmentInstance> offer = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_HOE), 10, pool.stream());
            helper.assertTrue(offer.stream().noneMatch(e -> e.enchantment.equals(harvesting) && e.level == 2), "Harvesting II offered at level 10");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** Reset, build the scene, break {@code target} with {@code tool}, wait, then run {@code check}. */
    private static void harvest(GameTestHelper helper, Runnable scene, ItemStack tool, BlockPos target, Consumer<ServerPlayer> setup,
                                Consumer<GameTestHelper> check) {
        Object[] state = new Object[1];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            scene.run();
            ServerPlayer player = Arena.survivalPlayer(helper, tool);
            setup.accept(player);
            state[0] = player;
            player.gameMode.destroyBlock(helper.absolutePos(target));
        }).thenIdle(2).thenExecute(() -> {
            check.accept(helper);
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    private interface Planter {
        void plant(int x, int z);
    }

    /** {@code size} x {@code size} columns from {@code (x0, z0)}. */
    private static void square(GameTestHelper helper, int x0, int z0, int size, Planter planter) {
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                planter.plant(x, z);
            }
        }
    }

    /** Exactly {@code replanted} of {@code seed} must be missing from the ground compared with what the plants dropped. */
    private static void assertSeedsTaken(GameTestHelper helper, Item seed, int replanted) {
        int raw = Arena.rawDrops(helper, seed);
        int ground = Arena.droppedCount(helper, seed);
        helper.assertTrue(raw - ground == replanted, seed + ": plants dropped " + raw + ", " + ground + " on the ground; expected "
                + replanted + " taken for replanting, was " + (raw - ground));
    }

    private static void assertCount(GameTestHelper helper, String what, int actual, int expected) {
        helper.assertTrue(actual == expected, what + ": expected " + expected + ", was " + actual);
    }
}
