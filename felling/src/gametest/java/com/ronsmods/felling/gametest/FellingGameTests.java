package com.ronsmods.felling.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.ronsmods.felling.RonsFelling;
import com.ronsmods.felling.TreeDetector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game checks for Felling. Run with {@code gradlew :felling:runGameTestServer}.
 *
 * <p>Trees are grown with the real vanilla worldgen features, so these exercise genuine natural tree
 * shapes. Every break goes through {@code ServerPlayerGameMode.destroyBlock}, the same path a
 * player's left-click takes.
 */
@GameTestHolder(RonsFelling.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FellingGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARENA = "arena";
    private static final int SEEDS = 12;
    /** How many logs above the base the mid-trunk tests cut. */
    private static final int MID_CUT = 2;

    // ---------------------------------------------------------------- supported trees fell completely

    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsOak(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.OAK, Blocks.OAK_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsFancyOak(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.FANCY_OAK, Blocks.OAK_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsFancyOakWithBees(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.FANCY_OAK_BEES, Blocks.OAK_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsSwampOak(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.SWAMP_OAK, Blocks.OAK_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsAzalea(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.AZALEA_TREE, Blocks.OAK_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsBirch(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.BIRCH, Blocks.BIRCH_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsTallBirchWithBees(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.SUPER_BIRCH_BEES, Blocks.BIRCH_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsSpruce(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.SPRUCE, Blocks.SPRUCE_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsPine(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.PINE, Blocks.SPRUCE_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsMegaSpruce(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.MEGA_SPRUCE, Blocks.SPRUCE_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsMegaPine(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.MEGA_PINE, Blocks.SPRUCE_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsJungle(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.JUNGLE_TREE, Blocks.JUNGLE_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsMegaJungle(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.MEGA_JUNGLE_TREE, Blocks.JUNGLE_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsDarkOak(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.DARK_OAK, Blocks.DARK_OAK_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsAcacia(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.ACACIA, Blocks.ACACIA_LOG, 1); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void fellsCherry(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.CHERRY, Blocks.CHERRY_LOG, 1); }

    // ---------------------------------------------------------------- Felling II: same trees, leaves cleared too

    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesOak(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.OAK, Blocks.OAK_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesFancyOak(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.FANCY_OAK, Blocks.OAK_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesFancyOakWithBees(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.FANCY_OAK_BEES, Blocks.OAK_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesSwampOak(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.SWAMP_OAK, Blocks.OAK_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesAzalea(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.AZALEA_TREE, Blocks.OAK_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesBirch(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.BIRCH, Blocks.BIRCH_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesTallBirchWithBees(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.SUPER_BIRCH_BEES, Blocks.BIRCH_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesSpruce(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.SPRUCE, Blocks.SPRUCE_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesPine(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.PINE, Blocks.SPRUCE_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesMegaSpruce(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.MEGA_SPRUCE, Blocks.SPRUCE_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesMegaPine(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.MEGA_PINE, Blocks.SPRUCE_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesJungle(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.JUNGLE_TREE, Blocks.JUNGLE_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesMegaJungle(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.MEGA_JUNGLE_TREE, Blocks.JUNGLE_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesDarkOak(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.DARK_OAK, Blocks.DARK_OAK_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesAcacia(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.ACACIA, Blocks.ACACIA_LOG, 2); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void clearsLeavesCherry(GameTestHelper h) { fellsEveryTree(h, TreeFeatures.CHERRY, Blocks.CHERRY_LOG, 2); }

    // ---------------------------------------------------------------- cutting partway up the trunk

    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayOak(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.OAK, Blocks.OAK_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayFancyOak(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.FANCY_OAK, Blocks.OAK_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayFancyOakWithBees(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.FANCY_OAK_BEES, Blocks.OAK_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwaySwampOak(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.SWAMP_OAK, Blocks.OAK_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayAzalea(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.AZALEA_TREE, Blocks.OAK_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayBirch(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.BIRCH, Blocks.BIRCH_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayTallBirchWithBees(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.SUPER_BIRCH_BEES, Blocks.BIRCH_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwaySpruce(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.SPRUCE, Blocks.SPRUCE_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayPine(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.PINE, Blocks.SPRUCE_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayMegaSpruce(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.MEGA_SPRUCE, Blocks.SPRUCE_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayMegaPine(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.MEGA_PINE, Blocks.SPRUCE_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayJungle(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.JUNGLE_TREE, Blocks.JUNGLE_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayMegaJungle(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.MEGA_JUNGLE_TREE, Blocks.JUNGLE_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayDarkOak(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.DARK_OAK, Blocks.DARK_OAK_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayAcacia(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.ACACIA, Blocks.ACACIA_LOG); }
    @GameTest(template = ARENA, timeoutTicks = 1200) public static void cutsMidwayCherry(GameTestHelper h) { cutsEveryTreeMidway(h, TreeFeatures.CHERRY, Blocks.CHERRY_LOG); }

    /**
     * Grow the feature with several seeds and cut the trunk {@link #MID_CUT} logs up. Exactly the logs above the cut
     * must fall (plus the rest of a 2x2 trunk layer at the cut); the stump and any low branches stay, leaves stay
     * (Felling I), and durability is one per log broken.
     */
    private static void cutsEveryTreeMidway(GameTestHelper helper, ResourceKey<ConfiguredFeature<?, ?>> feature, Block log) {
        List<String> failures = new ArrayList<>();
        int[] cut = {0};
        GameTestSequence sequence = helper.startSequence();
        for (int i = 0; i < SEEDS; i++) {
            long seed = 1000L + i;
            Object[] state = new Object[4]; // player, axe, expected remaining logs, leaves before
            sequence.thenExecute(() -> {
                state[0] = null;
                Arena.reset(helper);
                if (!Arena.growTree(helper, feature, seed, Arena.CENTER)) {
                    return;
                }
                BlockPos base = helper.absolutePos(Arena.CENTER);
                BlockPos target = base.above(MID_CUT);
                if (!helper.getLevel().getBlockState(target).is(log)) {
                    return; // tree too short for this cut
                }
                cut[0]++;
                List<BlockPos> logs = Arena.find(helper, log);
                Set<Long> columns = new HashSet<>();
                logs.stream().filter(p -> p.getY() == base.getY()).forEach(p -> columns.add(BlockPos.asLong(p.getX(), 0, p.getZ())));
                Set<BlockPos> expected = new HashSet<>();
                for (BlockPos p : logs) {
                    boolean trunkColumn = columns.contains(BlockPos.asLong(p.getX(), 0, p.getZ()));
                    if (p.getY() < target.getY() || (p.getY() == target.getY() && !trunkColumn)) {
                        expected.add(p);
                    }
                }
                ItemStack axe = Arena.fellingAxe(helper, 1);
                ServerPlayer player = Arena.survivalPlayer(helper, axe);
                state[0] = player;
                state[1] = axe;
                state[2] = expected;
                state[3] = new int[] {Arena.countLeaves(helper), logs.size()};
                player.gameMode.destroyBlock(target);
            }).thenIdle(2).thenExecute(() -> {
                if (state[0] == null) {
                    return;
                }
                ItemStack axe = (ItemStack) state[1];
                @SuppressWarnings("unchecked") Set<BlockPos> expected = (Set<BlockPos>) state[2];
                int[] before = (int[]) state[3];
                Set<BlockPos> left = new HashSet<>(Arena.find(helper, log));
                if (!left.equals(expected)) {
                    failures.add("seed " + seed + ": " + left.size() + " logs left, expected " + expected.size()
                            + " (extra " + diffSize(left, expected) + ", missing " + diffSize(expected, left) + ")");
                } else {
                    int broken = before[1] - expected.size();
                    if (axe.getDamageValue() != broken) {
                        failures.add("seed " + seed + ": axe took " + axe.getDamageValue() + " damage, expected " + broken);
                    }
                    if (Arena.countLeaves(helper) != before[0]) {
                        failures.add("seed " + seed + ": leaves changed");
                    }
                }
                Arena.removePlayer((ServerPlayer) state[0]);
            });
        }
        sequence.thenExecute(() -> {
            String name = feature.location().getPath() + " (cut " + MID_CUT + " up)";
            LOGGER.info("[felling-test] {}: cut {} of {} seeds, {} problem(s){}", name, cut[0], SEEDS, failures.size(),
                    failures.isEmpty() ? "" : "\n  " + String.join("\n  ", failures));
            helper.assertTrue(cut[0] > 0, name + ": no seed grew a tall enough tree");
            helper.assertTrue(failures.isEmpty(), name + ": " + String.join("; ", failures));
        }).thenSucceed();
    }

    /**
     * Grow the feature with several seeds; each time, one break of the bottom log must fell every log.
     * Felling I must leave every leaf; Felling II must clear every leaf, dropping no leaf blocks and costing no extra durability.
     */
    private static void fellsEveryTree(GameTestHelper helper, ResourceKey<ConfiguredFeature<?, ?>> feature, Block log, int fellingLevel) {
        List<String> failures = new ArrayList<>();
        int[] grown = {0};
        GameTestSequence sequence = helper.startSequence();
        for (int i = 0; i < SEEDS; i++) {
            long seed = 1000L + i;
            Object[] state = new Object[4]; // player, axe, logsBefore, leavesBefore
            sequence.thenExecute(() -> {
                Arena.reset(helper);
                if (!Arena.growTree(helper, feature, seed, Arena.CENTER)) {
                    state[0] = null;
                    return;
                }
                grown[0]++;
                BlockPos base = helper.absolutePos(Arena.CENTER);
                List<BlockPos> logs = Arena.find(helper, log);
                TreeDetector.Result verdict = TreeDetector.detect(helper.getLevel(), base);
                if (verdict.verdict() != TreeDetector.Verdict.TREE) {
                    failures.add("seed " + seed + ": " + verdict.verdict() + " at " + verdict.detail()
                            + " = " + (verdict.detail() == null ? "-" : helper.getLevel().getBlockState(verdict.detail())));
                }
                ItemStack axe = Arena.fellingAxe(helper, fellingLevel);
                ServerPlayer player = Arena.survivalPlayer(helper, axe);
                state[0] = player;
                state[1] = axe;
                state[2] = logs.size();
                state[3] = Arena.countLeaves(helper);
                player.gameMode.destroyBlock(base);
            }).thenIdle(2).thenExecute(() -> {
                if (state[0] == null) {
                    return;
                }
                ServerPlayer player = (ServerPlayer) state[0];
                ItemStack axe = (ItemStack) state[1];
                int logsBefore = (int) state[2];
                int leavesBefore = (int) state[3];
                int logsLeft = Arena.find(helper, log).size();
                int leavesAfter = Arena.countLeaves(helper);
                int dropped = Arena.droppedCount(helper, log.asItem());
                if (logsLeft != 0) {
                    failures.add("seed " + seed + ": " + logsLeft + "/" + logsBefore + " logs left standing");
                } else {
                    if (fellingLevel == 1 && leavesAfter != leavesBefore) {
                        failures.add("seed " + seed + ": leaves changed " + leavesBefore + " -> " + leavesAfter);
                    }
                    if (fellingLevel >= 2 && leavesAfter != 0) {
                        failures.add("seed " + seed + ": " + leavesAfter + "/" + leavesBefore + " leaves left");
                    }
                    if (droppedLeafBlocks(helper) != 0) {
                        failures.add("seed " + seed + ": leaf blocks were dropped");
                    }
                    if (dropped != logsBefore) {
                        failures.add("seed " + seed + ": dropped " + dropped + " logs, expected " + logsBefore);
                    }
                    if (axe.getDamageValue() != logsBefore) {
                        failures.add("seed " + seed + ": axe took " + axe.getDamageValue() + " damage, expected " + logsBefore);
                    }
                }
                Arena.removePlayer(player);
            });
        }
        sequence.thenExecute(() -> {
            String name = feature.location().getPath() + " (Felling " + fellingLevel + ")";
            LOGGER.info("[felling-test] {}: grew {} of {} seeds, {} problem(s){}", name, grown[0], SEEDS, failures.size(),
                    failures.isEmpty() ? "" : "\n  " + String.join("\n  ", failures));
            helper.assertTrue(grown[0] > 0, name + ": worldgen never placed the tree");
            helper.assertTrue(failures.isEmpty(), name + ": " + String.join("; ", failures));
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- unsupported tree types are left alone

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void ignoresMangrove(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.MANGROVE_LOG, () -> Arena.growTree(helper, TreeFeatures.MANGROVE, 1L, Arena.CENTER), Arena.fellingAxe(helper),
                h -> topmostTrunkBottom(h, Blocks.MANGROVE_LOG), false);
    }

    // ---------------------------------------------------------------- activation rules

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void ordinaryAxeBreaksOneLog(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> oakTree(helper), new ItemStack(Items.DIAMOND_AXE), h -> h.absolutePos(Arena.CENTER), false);
    }

    /** Cutting the top log of the trunk has nothing above it: a normal one-log break. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void topLogBreaksOneLog(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> oakTree(helper), Arena.fellingAxe(helper),
                h -> Arena.find(h, Blocks.OAK_LOG).stream().max((a, b) -> Integer.compare(a.getY(), b.getY())).orElseThrow(), false);
    }

    /** A branch is not part of the trunk column: breaking it is a normal one-log break, the tree stays. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void branchBreaksOneLog(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> {
            for (long seed = 1000; seed < 1040; seed++) {
                Arena.reset(helper);
                if (Arena.growTree(helper, TreeFeatures.FANCY_OAK, seed, Arena.CENTER) && horizontalLog(helper) != null) {
                    return true;
                }
            }
            return false;
        }, Arena.fellingAxe(helper), FellingGameTests::horizontalLog, false);
    }

    /** A cabin corner post cut partway up is still a build: one log. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void cabinPostCutMidwayBreaksOneLog(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> {
            buildCabin(helper, Arena.CENTER);
            return true;
        }, Arena.fellingAxe(helper), h -> h.absolutePos(Arena.CENTER.above(2)), false);
    }

    /** Felling II cut halfway up a mega spruce: the stump keeps its lower canopy, the fallen top is cleared, nothing left decaying. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void felling2MidwayCutKeepsStumpCanopy(GameTestHelper helper) {
        Object[] state = new Object[3];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            helper.assertTrue(Arena.growTree(helper, TreeFeatures.MEGA_SPRUCE, 1000L, Arena.CENTER), "mega spruce did not grow");
            int height = 0;
            while (helper.getLevel().getBlockState(helper.absolutePos(Arena.CENTER.above(height))).is(Blocks.SPRUCE_LOG)) {
                height++;
            }
            BlockPos target = helper.absolutePos(Arena.CENTER.above(height / 2));
            helper.assertTrue(height >= 8, "mega spruce too short: " + height);
            state[1] = target;
            state[2] = Arena.countLeaves(helper);
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper, 2));
            state[0] = player;
            player.gameMode.destroyBlock(target);
        }).thenIdle(40).thenExecute(() -> {
            BlockPos target = (BlockPos) state[1];
            List<BlockPos> logs = Arena.find(helper, Blocks.SPRUCE_LOG);
            helper.assertTrue(logs.stream().allMatch(p -> p.getY() < target.getY()), "logs left above the cut");
            int stumpLayers = target.getY() - helper.absolutePos(Arena.CENTER).getY();
            helper.assertTrue(logs.size() >= 4 * stumpLayers, "stump should be " + stumpLayers + " layers of 2x2, has " + logs.size() + " logs");
            Set<BlockPos> leaves = Arena.leaves(helper);
            LOGGER.info("[felling-test] mega spruce cut halfway ({} up) with Felling II: {} of {} leaves kept by the stump", stumpLayers, leaves.size(), state[2]);
            helper.assertTrue(leaves.size() < (int) state[2], "fallen canopy should be cleared");
            helper.assertTrue(!leaves.isEmpty(), "the stump should keep its lower canopy");
            for (BlockPos p : leaves) {
                helper.assertTrue(p.getY() <= target.getY() + 1, "leaf left high above the cut at " + p);
                helper.assertTrue(helper.getLevel().getBlockState(p).getValue(LeavesBlock.DISTANCE) < 7, "a decaying leaf was left at " + p);
            }
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void sneakingBreaksOneLog(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> oakTree(helper), Arena.fellingAxe(helper), h -> h.absolutePos(Arena.CENTER), true);
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void fellingOnNonAxeDoesNothing(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(Arena.felling(helper), 1); // only possible via commands; must still do nothing
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> oakTree(helper), pickaxe, h -> h.absolutePos(Arena.CENTER), false);
    }

    /** Cutting a small oak 2 logs up fells the logs above and leaves a 2-log stump. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void midTrunkCutFellsOnlyAbove(GameTestHelper helper) {
        Object[] state = new Object[2];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            helper.assertTrue(oakTree(helper), "oak did not grow");
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper));
            state[0] = player;
            state[1] = Arena.countLeaves(helper);
            player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER.above(2)));
        }).thenIdle(2).thenExecute(() -> {
            Set<BlockPos> left = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
            helper.assertTrue(left.equals(Set.of(helper.absolutePos(Arena.CENTER), helper.absolutePos(Arena.CENTER.above()))),
                    "expected a 2-log stump, found " + left.size() + " logs");
            helper.assertTrue(Arena.countLeaves(helper) == (int) state[1], "Felling I should not touch leaves");
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- player builds are never felled

    /** A player's log pillar touching the trunk base: the footprint is no longer a natural one. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void pillarTouchingTrunkBlocksFelling(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> {
            boolean grown = oakTree(helper);
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(Arena.CENTER.offset(1, y - 1, 0), Blocks.OAK_LOG);
            }
            return grown;
        }, Arena.fellingAxe(helper), h -> h.absolutePos(Arena.CENTER), false);
    }

    /** Anything attached to the trunk (here a torch) marks the tree as touched by a player. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void torchOnTrunkBlocksFelling(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> {
            boolean grown = oakTree(helper);
            helper.setBlock(Arena.CENTER.offset(0, 1, -1), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
            return grown;
        }, Arena.fellingAxe(helper), h -> h.absolutePos(Arena.CENTER), false);
    }

    /** A decorative "tree" built from logs and hand-placed (persistent) leaves. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void builtTreeWithPlacedLeavesBlocksFelling(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> {
            for (int y = 0; y < 5; y++) {
                helper.setBlock(Arena.CENTER.above(y), Blocks.OAK_LOG);
            }
            BlockState placedLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 1);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int y = 3; y <= 5; y++) {
                        BlockPos p = Arena.CENTER.offset(dx, y, dz);
                        if (helper.getBlockState(p).isAir()) {
                            helper.setBlock(p, placedLeaves);
                        }
                    }
                }
            }
            return true;
        }, Arena.fellingAxe(helper), h -> h.absolutePos(Arena.CENTER), false);
    }

    /** A bare log post on grass has no canopy. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void barePostBlocksFelling(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> {
            for (int y = 0; y < 4; y++) {
                helper.setBlock(Arena.CENTER.above(y), Blocks.OAK_LOG);
            }
            return true;
        }, Arena.fellingAxe(helper), h -> h.absolutePos(Arena.CENTER), false);
    }

    /** Corner post of a log-frame cabin with plank walls and log beams. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void cabinCornerBlocksFelling(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> {
            buildCabin(helper, Arena.CENTER);
            return true;
        }, Arena.fellingAxe(helper), h -> h.absolutePos(Arena.CENTER), false);
    }

    /** A player beam joined to the canopy and running out past the search box. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void longBeamIntoTreeBlocksFelling(GameTestHelper helper) {
        breakOnlyOneLog(helper, Blocks.OAK_LOG, () -> {
            boolean grown = oakTree(helper);
            for (int x = 1; x <= 12; x++) {
                helper.setBlock(Arena.CENTER.offset(x, 2, 0), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            }
            return grown;
        }, Arena.fellingAxe(helper), h -> h.absolutePos(Arena.CENTER), false);
    }

    /** A cabin standing a few blocks from a tree is untouched while the tree itself still fells. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void nearbyCabinIsUntouched(GameTestHelper helper) {
        Object[] state = new Object[3];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            helper.assertTrue(oakTree(helper), "oak did not grow");
            BlockPos cabinCorner = Arena.CENTER.offset(4, 0, -2);
            // Clear any leaves where the cabin goes, then build it.
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 0; y < 8; y++) {
                helper.setBlock(cabinCorner.offset(x, y, z), Blocks.AIR);
            }
            Set<BlockPos> treeLogs = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
            buildCabin(helper, cabinCorner);
            Set<BlockPos> cabinLogs = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
            cabinLogs.removeAll(treeLogs);
            helper.assertTrue(cabinLogs.size() == 4 * 4 + 12, "cabin should have 28 logs, has " + cabinLogs.size());
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper));
            state[0] = player;
            state[1] = cabinLogs;
            player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
        }).thenIdle(2).thenExecute(() -> {
            @SuppressWarnings("unchecked") Set<BlockPos> cabinLogs = (Set<BlockPos>) state[1];
            Set<BlockPos> left = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
            helper.assertTrue(left.equals(cabinLogs), "expected only the " + cabinLogs.size() + " cabin logs to remain, found " + left.size());
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- durability, protection

    /** An axe with 3 uses left breaks the base and two more logs (top-down), then breaks and stops. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void stopsWhenAxeBreaks(GameTestHelper helper) {
        axeBreaksMidFell(helper, 1);
    }

    /** Felling II only clears leaves after a complete fell; a fell cut short by the axe breaking leaves them all. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void felling2KeepsLeavesWhenAxeBreaks(GameTestHelper helper) {
        axeBreaksMidFell(helper, 2);
    }

    private static void axeBreaksMidFell(GameTestHelper helper, int fellingLevel) {
        Object[] state = new Object[4];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            helper.assertTrue(oakTree(helper), "oak did not grow");
            List<BlockPos> before = Arena.find(helper, Blocks.OAK_LOG);
            helper.assertTrue(before.size() >= 5, "test tree too small: " + before.size());
            ItemStack axe = Arena.fellingAxe(helper, fellingLevel);
            axe.setDamageValue(axe.getMaxDamage() - 3);
            ServerPlayer player = Arena.survivalPlayer(helper, axe);
            state[0] = player;
            state[1] = before;
            state[2] = axe;
            state[3] = Arena.countLeaves(helper);
            player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
        }).thenIdle(2).thenExecute(() -> {
            ServerPlayer player = (ServerPlayer) state[0];
            @SuppressWarnings("unchecked") List<BlockPos> before = (List<BlockPos>) state[1];
            List<BlockPos> left = Arena.find(helper, Blocks.OAK_LOG);
            helper.assertTrue(player.getMainHandItem().isEmpty(), "axe should have broken");
            helper.assertTrue(left.size() == before.size() - 3, "expected exactly 3 logs broken, got " + (before.size() - left.size()));
            helper.assertTrue(Arena.countLeaves(helper) == (int) state[3], "leaves should be untouched after an incomplete fell");
            int base = helper.absolutePos(Arena.CENTER).getY();
            // The 2 extra logs came off the top, so what is left is the contiguous lower trunk above the cut base.
            for (BlockPos p : left) {
                helper.assertTrue(p.getY() > base && p.getY() <= base + left.size(), "unexpected remaining log at " + p);
            }
            Arena.removePlayer(player);
        }).thenSucceed();
    }

    /** A protection mod canceling one log's BreakEvent stops the fell there. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void stopsAtProtectedLog(GameTestHelper helper) {
        Object[] state = new Object[2];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            helper.assertTrue(oakTree(helper), "oak did not grow");
            BlockPos guarded = helper.absolutePos(Arena.CENTER.above(2));
            TestProtection.PROTECTED.add(guarded);
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper));
            state[0] = player;
            state[1] = guarded;
            player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
        }).thenIdle(2).thenExecute(() -> {
            BlockPos guarded = (BlockPos) state[1];
            TestProtection.PROTECTED.remove(guarded);
            Set<BlockPos> left = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
            Set<BlockPos> expected = Set.of(guarded, guarded.below());
            helper.assertTrue(left.equals(expected), "expected only the protected log and the one below it to remain, found " + left.size());
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    // ---------------------------------------------------------------- Felling II leaf clearing

    /** Leaves removed by Felling II drop at natural-decay rates: oak saplings 1 in 20, apples 1 in 200, no leaf blocks. */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void felling2DropsAtDecayRates(GameTestHelper helper) {
        int[] totals = new int[4]; // leaves, saplings, apples, sticks
        GameTestSequence sequence = helper.startSequence();
        for (int i = 0; i < 40; i++) {
            long seed = 5000L + i;
            Object[] state = new Object[2];
            sequence.thenExecute(() -> {
                Arena.reset(helper);
                state[0] = null;
                if (!Arena.growTree(helper, TreeFeatures.FANCY_OAK, seed, Arena.CENTER)) {
                    return;
                }
                state[1] = Arena.countLeaves(helper);
                ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper, 2));
                state[0] = player;
                player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
            }).thenIdle(2).thenExecute(() -> {
                if (state[0] == null) {
                    return;
                }
                helper.assertTrue(Arena.countLeaves(helper) == 0, "seed " + seed + ": leaves left");
                totals[0] += (int) state[1];
                totals[1] += Arena.droppedCount(helper, Items.OAK_SAPLING);
                totals[2] += Arena.droppedCount(helper, Items.APPLE);
                totals[3] += Arena.droppedCount(helper, Items.STICK);
                helper.assertTrue(droppedLeafBlocks(helper) == 0, "leaf blocks dropped");
                Arena.removePlayer((ServerPlayer) state[0]);
            });
        }
        sequence.thenExecute(() -> {
            int leaves = totals[0];
            LOGGER.info("[felling-test] Felling II drops from {} oak leaves: {} saplings (expect ~{}), {} apples (expect ~{}), {} sticks (expect ~{})",
                    leaves, totals[1], leaves / 20, totals[2], leaves / 200, totals[3], leaves * 3 / 100);
            helper.assertTrue(leaves > 2000, "too few leaves to judge rates: " + leaves);
            assertNear(helper, "saplings", totals[1], leaves * 0.05, leaves * 0.05 * 0.95);
            assertNear(helper, "apples", totals[2], leaves * 0.005, leaves * 0.005 * 0.995);
            // Sticks: 2% chance of 1-2 sticks.
            assertNear(helper, "sticks", totals[3], leaves * 0.03, leaves * 0.02 * 2.0);
        }).thenSucceed();
    }

    /**
     * Felling II clears the felled tree's whole canopy, even where it touches a neighbour's, and never makes a
     * neighbour's leaves decay. Three trees with interlocking canopies (fancy oak in the middle, an oak and a
     * birch beside it):
     * <ol>
     *   <li>grow only the two neighbours, to learn which oak logs are the east oak's;</li>
     *   <li>grow the grove, fell the middle tree with Felling I and let vanilla recompute leaf distances to
     *       find the leaves vanilla would decay;</li>
     *   <li>grow the grove again and fell the middle tree with Felling II.</li>
     * </ol>
     * Felling II must remove everything vanilla decays, leave every birch leaf (other species) and the east
     * oak standing, leave only oak leaves nearer the east oak than the felled tree, and leave nothing decaying.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void felling2ClearsCanopyInGrove(GameTestHelper helper) {
        Object[] state = new Object[7];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            growNeighbours(helper);
            Set<BlockPos> eastLogs = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
            state[0] = denseGrove(helper);
            Set<BlockPos> felledLogs = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
            felledLogs.removeAll(eastLogs);
            state[4] = felledLogs;
            state[5] = eastLogs;
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper, 1));
            state[1] = player;
            player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
        }).thenIdle(40).thenExecute(() -> {
            Set<BlockPos> decaying = new HashSet<>();
            for (BlockPos p : Arena.leaves(helper)) {
                BlockState st = helper.getLevel().getBlockState(p);
                if (!st.getValue(LeavesBlock.PERSISTENT) && st.getValue(LeavesBlock.DISTANCE) == 7) {
                    decaying.add(p);
                }
            }
            state[2] = decaying;
            Arena.removePlayer((ServerPlayer) state[1]);
            // Same scene again, Felling II this time.
            Set<BlockPos> before = denseGrove(helper);
            helper.assertTrue(before.equals(state[0]), "grove did not regrow identically");
            state[6] = new HashSet<>(Arena.find(helper, Blocks.BIRCH_LEAVES));
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper, 2));
            state[3] = player;
            player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
        }).thenIdle(40).thenExecute(() -> {
            @SuppressWarnings("unchecked") Set<BlockPos> before = (Set<BlockPos>) state[0];
            @SuppressWarnings("unchecked") Set<BlockPos> decaying = (Set<BlockPos>) state[2];
            @SuppressWarnings("unchecked") Set<BlockPos> felledLogs = (Set<BlockPos>) state[4];
            @SuppressWarnings("unchecked") Set<BlockPos> eastLogs = (Set<BlockPos>) state[5];
            @SuppressWarnings("unchecked") Set<BlockPos> birchBefore = (Set<BlockPos>) state[6];
            Set<BlockPos> after = Arena.leaves(helper);
            Set<BlockPos> removed = new HashSet<>(before);
            removed.removeAll(after);
            List<BlockPos> oakLeft = after.stream().filter(p -> helper.getLevel().getBlockState(p).is(Blocks.OAK_LEAVES)).toList();
            List<BlockPos> wrongSide = oakLeft.stream().filter(p -> nearestSq(p, felledLogs) < nearestSq(p, eastLogs)).toList();
            LOGGER.info("[felling-test] grove: {} leaves, vanilla would decay {}, Felling II removed {}, {} remain ({} birch, {} oak)",
                    before.size(), decaying.size(), removed.size(), after.size(), after.size() - oakLeft.size(), oakLeft.size());
            helper.assertTrue(!oakLeft.isEmpty(), "the east oak should keep its canopy");
            helper.assertTrue(removed.containsAll(decaying), diffSize(decaying, removed) + " leaves vanilla decays were left");
            helper.assertTrue(after.containsAll(birchBefore), diffSize(birchBefore, after) + " birch leaves were removed");
            helper.assertTrue(wrongSide.isEmpty(), wrongSide.size() + " oak leaves left nearer the felled tree than the east oak");
            for (BlockPos p : eastLogs) {
                helper.assertTrue(helper.getLevel().getBlockState(p).is(Blocks.OAK_LOG), "east oak log missing at " + p);
            }
            for (BlockPos p : after) {
                helper.assertTrue(helper.getLevel().getBlockState(p).getValue(LeavesBlock.DISTANCE) < 7, "a decaying leaf was left at " + p);
            }
            Arena.removePlayer((ServerPlayer) state[3]);
        }).thenSucceed();
    }

    /**
     * Two same-species oaks with touching canopies, several seeds and spacings: felling one with Felling II
     * leaves no leaf nearer to it than to the other tree, and the other tree standing with no decaying leaves.
     */
    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void felling2ClearsCanopyTouchingSameSpecies(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int[] totals = new int[4]; // pairs, neighbour leaves, neighbour leaves lost, felled-tree leaves kept by the neighbour
        GameTestSequence sequence = helper.startSequence();
        for (int i = 0; i < 12; i++) {
            long seed = 2000L + i;
            BlockPos east = Arena.CENTER.offset(3 + i % 3, 0, i % 2);
            ResourceKey<ConfiguredFeature<?, ?>> felled = i % 2 == 0 ? TreeFeatures.OAK : TreeFeatures.FANCY_OAK;
            Object[] state = new Object[4];
            sequence.thenExecute(() -> {
                state[0] = null;
                Arena.reset(helper);
                if (!Arena.growTree(helper, TreeFeatures.OAK, seed + 100, east)) {
                    return;
                }
                state[1] = Arena.leaves(helper);
                Set<BlockPos> eastLogs = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
                state[2] = eastLogs;
                Arena.reset(helper);
                if (!Arena.growTree(helper, felled, seed, Arena.CENTER) || !Arena.growTree(helper, TreeFeatures.OAK, seed + 100, east)) {
                    return;
                }
                if (TreeDetector.detect(helper.getLevel(), helper.absolutePos(Arena.CENTER)).verdict() != TreeDetector.Verdict.TREE) {
                    return; // trunks grew into each other; not a fellable tree
                }
                Set<BlockPos> felledLogs = new HashSet<>(Arena.find(helper, Blocks.OAK_LOG));
                felledLogs.removeAll(eastLogs);
                state[3] = felledLogs;
                ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper, 2));
                state[0] = player;
                player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
            }).thenIdle(30).thenExecute(() -> {
                if (state[0] == null) {
                    return;
                }
                @SuppressWarnings("unchecked") Set<BlockPos> eastAlone = (Set<BlockPos>) state[1];
                @SuppressWarnings("unchecked") Set<BlockPos> eastTrunk = (Set<BlockPos>) state[2];
                @SuppressWarnings("unchecked") Set<BlockPos> felledLogs = (Set<BlockPos>) state[3];
                Set<BlockPos> after = Arena.leaves(helper);
                Set<BlockPos> felledLeft = new HashSet<>(after);
                felledLeft.removeAll(eastAlone);
                totals[0]++;
                totals[1] += eastAlone.size();
                totals[2] += diffSize(eastAlone, after);
                totals[3] += felledLeft.size();
                long wrongSide = after.stream().filter(p -> nearestSq(p, felledLogs) < nearestSq(p, eastTrunk)).count();
                if (wrongSide > 0) {
                    failures.add("seed " + seed + ": " + wrongSide + " leaves left nearer the felled tree");
                }
                for (BlockPos p : eastTrunk) {
                    if (!helper.getLevel().getBlockState(p).is(Blocks.OAK_LOG)) {
                        failures.add("seed " + seed + ": neighbour log missing at " + p);
                        break;
                    }
                }
                long decaying = after.stream().filter(p -> helper.getLevel().getBlockState(p).getValue(LeavesBlock.DISTANCE) == 7).count();
                if (decaying > 0) {
                    failures.add("seed " + seed + ": " + decaying + " neighbour leaves left decaying");
                }
                if (after.isEmpty()) {
                    failures.add("seed " + seed + ": neighbour lost its whole canopy");
                }
                Arena.removePlayer((ServerPlayer) state[0]);
            });
        }
        sequence.thenExecute(() -> {
            LOGGER.info("[felling-test] touching oaks: {} pairs felled, neighbours lost {} of {} leaf positions at the seam, "
                            + "{} felled-tree leaves left (nearer the neighbour), {} problem(s){}",
                    totals[0], totals[2], totals[1], totals[3], failures.size(), failures.isEmpty() ? "" : "\n  " + String.join("\n  ", failures));
            helper.assertTrue(totals[0] >= 6, "too few pairs could be felled: " + totals[0]);
            helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
        }).thenSucceed();
    }

    /** Hand-placed (persistent) leaves resting on the canopy are never removed. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void felling2KeepsPlacedLeaves(GameTestHelper helper) {
        Object[] state = new Object[2];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            helper.assertTrue(oakTree(helper), "oak did not grow");
            BlockPos top = Arena.leaves(helper).stream().max((a, b) -> Integer.compare(a.getY(), b.getY())).orElseThrow().above();
            helper.getLevel().setBlock(top, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS);
            state[1] = top;
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper, 2));
            state[0] = player;
            player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
        }).thenIdle(2).thenExecute(() -> {
            Set<BlockPos> left = Arena.leaves(helper);
            helper.assertTrue(left.equals(Set.of((BlockPos) state[1])), "expected only the placed leaf to remain, found " + left.size());
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    /** A protection mod canceling one leaf break keeps that leaf; the rest are cleared. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void felling2RespectsProtectedLeaf(GameTestHelper helper) {
        Object[] state = new Object[2];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            helper.assertTrue(oakTree(helper), "oak did not grow");
            BlockPos guarded = Arena.leaves(helper).iterator().next();
            TestProtection.PROTECTED.add(guarded);
            state[1] = guarded;
            ServerPlayer player = Arena.survivalPlayer(helper, Arena.fellingAxe(helper, 2));
            state[0] = player;
            player.gameMode.destroyBlock(helper.absolutePos(Arena.CENTER));
        }).thenIdle(2).thenExecute(() -> {
            BlockPos guarded = (BlockPos) state[1];
            TestProtection.PROTECTED.remove(guarded);
            helper.assertTrue(Arena.find(helper, Blocks.OAK_LOG).isEmpty(), "trunk should be felled");
            Set<BlockPos> left = Arena.leaves(helper);
            helper.assertTrue(left.equals(Set.of(guarded)), "expected only the protected leaf to remain, found " + left.size());
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }

    /** Three trees with interlocking canopies around the arena center. Returns the leaf positions. */
    private static Set<BlockPos> denseGrove(GameTestHelper helper) {
        Arena.reset(helper);
        helper.assertTrue(Arena.growTree(helper, TreeFeatures.FANCY_OAK, 1003L, Arena.CENTER), "middle tree did not grow");
        growNeighbours(helper);
        return Arena.leaves(helper);
    }

    /** The grove's two neighbour trees: an oak to the east and a birch to the west. */
    private static void growNeighbours(GameTestHelper helper) {
        helper.assertTrue(Arena.growTree(helper, TreeFeatures.OAK, 1000L, Arena.CENTER.offset(5, 0, 1)), "east tree did not grow");
        helper.assertTrue(Arena.growTree(helper, TreeFeatures.BIRCH, 1001L, Arena.CENTER.offset(-3, 0, 4)), "west tree did not grow");
    }

    /** A horizontal (branch) oak log in the arena, or null. */
    private static BlockPos horizontalLog(GameTestHelper helper) {
        return Arena.find(helper, Blocks.OAK_LOG).stream()
                .filter(p -> helper.getLevel().getBlockState(p).getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y)
                .findFirst().orElse(null);
    }

    private static double nearestSq(BlockPos pos, Set<BlockPos> logs) {
        return logs.stream().mapToDouble(pos::distSqr).min().orElse(Double.MAX_VALUE);
    }

    private static int diffSize(Set<BlockPos> a, Set<BlockPos> b) {
        Set<BlockPos> d = new HashSet<>(a);
        d.removeAll(b);
        return d.size();
    }

    private static void assertNear(GameTestHelper helper, String what, int actual, double mean, double variance) {
        double tolerance = Math.max(4 * Math.sqrt(variance), 3);
        helper.assertTrue(Math.abs(actual - mean) <= tolerance,
                what + ": got " + actual + ", expected " + Math.round(mean) + " +/- " + Math.round(tolerance));
    }

    /** Leaf blocks dropped as items (would mean Silk Touch / shears behaviour leaked in). */
    private static int droppedLeafBlocks(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, Arena.bounds(helper),
                e -> e.getItem().getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof LeavesBlock).size();
    }

    // ---------------------------------------------------------------- registration / acquisition

    @GameTest(template = ARENA)
    public static void enchantmentIsRegisteredForAxes(GameTestHelper helper) {
        Holder<Enchantment> felling = Arena.felling(helper);
        helper.assertTrue(felling.value().getMaxLevel() == 2, "Felling should have two levels");
        helper.assertTrue(felling.value().canEnchant(new ItemStack(Items.IRON_AXE)), "Felling should apply to axes");
        helper.assertTrue(!felling.value().canEnchant(new ItemStack(Items.IRON_PICKAXE)), "Felling should not apply to pickaxes");
        helper.assertTrue(felling.is(EnchantmentTags.IN_ENCHANTING_TABLE), "Felling should be in the enchanting table pool");
        helper.assertTrue(felling.is(EnchantmentTags.TRADEABLE), "Felling should be tradeable by librarians");
        helper.assertTrue(felling.is(EnchantmentTags.ON_RANDOM_LOOT), "Felling should appear in random loot");
        helper.assertTrue(!felling.is(EnchantmentTags.TREASURE), "Felling should not be a treasure enchantment");
        helper.succeed();
    }

    /** Roll level-30 enchanting table offers for a diamond axe and confirm Felling can come up. */
    @GameTest(template = ARENA)
    public static void enchantingTableCanOfferFelling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Enchantment> felling = Arena.felling(helper);
        var pool = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow();
        RandomSource random = RandomSource.create(42);
        int hits = 0;
        int level2 = 0;
        int rolls = 2000;
        for (int i = 0; i < rolls; i++) {
            List<EnchantmentInstance> offer = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_AXE), 30, pool.stream());
            if (offer.stream().anyMatch(e -> e.enchantment.equals(felling))) {
                hits++;
            }
            if (offer.stream().anyMatch(e -> e.enchantment.equals(felling) && e.level == 2)) {
                level2++;
            }
        }
        LOGGER.info("[felling-test] enchanting table (level 30, diamond axe): Felling in {} of {} offers, Felling II in {}", hits, rolls, level2);
        helper.assertTrue(hits > 0, "Felling never offered in " + rolls + " level-30 rolls");
        helper.assertTrue(level2 > 0, "Felling II never offered in " + rolls + " level-30 rolls");
        helper.assertTrue(level2 == hits, "Felling II should replace Felling I at table level 30 (mid-game and up)");
        // Early game: a table with no bookshelves (level 5) offers Felling I, never Felling II.
        int early = 0;
        for (int i = 0; i < rolls; i++) {
            List<EnchantmentInstance> offer = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_AXE), 5, pool.stream());
            if (offer.stream().anyMatch(e -> e.enchantment.equals(felling))) {
                early++;
            }
            helper.assertTrue(offer.stream().noneMatch(e -> e.enchantment.equals(felling) && e.level == 2), "Felling II offered at table level 5");
        }
        LOGGER.info("[felling-test] enchanting table (level 5, diamond axe): Felling I in {} of {} offers", early, rolls);
        helper.assertTrue(early > 0, "Felling I never offered in " + rolls + " level-5 rolls");
        // Felling II stays mid-game: never at table level 10.
        for (int i = 0; i < rolls; i++) {
            List<EnchantmentInstance> offer = EnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_AXE), 10, pool.stream());
            helper.assertTrue(offer.stream().noneMatch(e -> e.enchantment.equals(felling) && e.level == 2), "Felling II offered at table level 10");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** A normal small oak (straight trunk, blob canopy). Seed 1000 grows a 5-log oak. */
    private static boolean oakTree(GameTestHelper helper) {
        return Arena.growTree(helper, TreeFeatures.OAK, 1000L, Arena.CENTER);
    }

    private static BlockPos topmostTrunkBottom(GameTestHelper helper, Block log) {
        // Mangrove trunks stand on roots; break the lowest log.
        return Arena.find(helper, log).stream().min((a, b) -> Integer.compare(a.getY(), b.getY())).orElseThrow();
    }

    /** 5x5 log-frame cabin: 4 corner posts, log beams around the top, plank walls. */
    private static void buildCabin(GameTestHelper helper, BlockPos corner) {
        int height = 4;
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean edgeX = x == 0 || x == 4;
                boolean edgeZ = z == 0 || z == 4;
                if (!edgeX && !edgeZ) continue;
                for (int y = 0; y < height; y++) {
                    BlockPos p = corner.offset(x, y, z);
                    if (edgeX && edgeZ) {
                        helper.setBlock(p, Blocks.OAK_LOG);
                    } else if (y == height - 1) {
                        helper.setBlock(p, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, edgeX ? Direction.Axis.Z : Direction.Axis.X));
                    } else {
                        helper.setBlock(p, Blocks.OAK_PLANKS);
                    }
                }
            }
        }
    }

    /**
     * Build a scene, break {@code target} with {@code tool}, and assert exactly one log of {@code log} disappeared.
     */
    private static void breakOnlyOneLog(GameTestHelper helper, Block log, java.util.function.BooleanSupplier scene, ItemStack tool,
                                        java.util.function.Function<GameTestHelper, BlockPos> target, boolean sneaking) {
        Object[] state = new Object[3];
        helper.startSequence().thenExecute(() -> {
            Arena.reset(helper);
            helper.assertTrue(scene.getAsBoolean(), "scene setup failed");
            BlockPos pos = target.apply(helper);
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(log), "target is not a log: " + helper.getLevel().getBlockState(pos));
            int before = Arena.find(helper, log).size();
            TreeDetector.Result verdict = TreeDetector.detect(helper.getLevel(), pos);
            ServerPlayer player = Arena.survivalPlayer(helper, tool);
            player.setShiftKeyDown(sneaking);
            state[0] = player;
            state[1] = before;
            state[2] = pos;
            LOGGER.info("[felling-test] detector verdict for this scene: {} at {}", verdict.verdict(), verdict.detail() == null ? "-" : verdict.detail());
            player.gameMode.destroyBlock(pos);
        }).thenIdle(2).thenExecute(() -> {
            int before = (int) state[1];
            int after = Arena.find(helper, log).size();
            helper.assertTrue(helper.getLevel().getBlockState((BlockPos) state[2]).isAir(), "the targeted log itself should break normally");
            helper.assertTrue(after == before - 1, "expected exactly 1 log broken, got " + (before - after));
            Arena.removePlayer((ServerPlayer) state[0]);
        }).thenSucceed();
    }


}
