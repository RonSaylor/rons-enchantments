# Ron's enchantments

A small series of opinionated, vanilla-style enchantment mods for **Minecraft Java 1.21.1 / NeoForge**.
Each mod is its own JAR and works on its own. Each enchantment has fixed rules and no player-facing config.

| Mod (mod list name) | In-game name | Item | Status |
| --- | --- | --- | --- |
| Ron's Felling (`ronsfelling`) | Felling I–II | Axe | **1.2.0** |
| Ron's Veinbreaking (`ronsveinbreaking`) | Veinbreaking | Pickaxe | **1.1.0** |
| Ron's Harvesting (`ronsharvesting`) | Harvesting I–II | Hoe | **1.1.0** |

---

## Ron's Felling

Chop a natural tree's trunk with a Felling axe and everything above the cut comes down, branches included. Chop the bottom log to take the whole tree, or higher up to leave a stump.

- **Felling I**: leaves are left alone and decay naturally.
- **Felling II**: once the whole trunk is down, its canopy is cleared at once (see below).

### Getting it

Felling is a normal (non-treasure) enchantment, tagged `#minecraft:non_treasure`, so it shows up wherever Efficiency or Fortune do:

- **Enchanting table**: axes only. Rarity matches Fortune (weight 2). Felling I is early game: it needs enchanting level 5+ (like Unbreaking I), so a table with no bookshelves can offer it. Felling II is mid-game: it needs level 20+, which a table reaches with about 8–10 bookshelves, and from there it replaces Felling I. Felling shows up in roughly 1 in 5 offers at most table levels, and about 3 in 10 at level 30 (always Felling II there).
- **Librarian villagers**: enchanted books, either level.
- **Loot chests and fishing**: random enchanted books and gear.
- **Anvil**: combine a Felling book with any axe (anvil cost 4 per level). Two Felling I combine into Felling II.

It works with every other axe enchantment, including Efficiency, Unbreaking, Fortune, Silk Touch, and Mending.

**Dev / creative shortcut** (cheats on):

```
/give @s minecraft:diamond_axe[minecraft:enchantments={levels:{"ronsfelling:felling":1}}]
/give @s minecraft:diamond_axe[minecraft:enchantments={levels:{"ronsfelling:felling":2}}]
/enchant @s ronsfelling:felling 2      (while holding an axe)
```

The Felling book is also in the creative inventory's *Ingredients* tab, with the other enchanted books.

### How a fell works

- Only a real player triggers it. Fake players from automation mods (deployers, block breakers) never do.
- Sneaking turns it off, so you break just the one log.
- Any log in the **trunk column** triggers it, from the bottom log up. The cut log breaks and everything above the cut height falls; everything below stays as a stump, including the lower part of any branch. On a 2×2 trunk, the other three trunk logs at the cut height fall too.
- Branch logs, and the topmost trunk log (nothing above it), break as usual.
- Each extra log is broken through the vanilla player-break path, one at a time from the top down. So every log:
  - fires its own break event, so claim and protection mods can refuse it;
  - respects adventure mode, spawn protection and the world border;
  - drops its normal loot where it stood;
  - costs normal durability (Unbreaking applies) and normal hunger.
- It stops at once if the axe breaks, if you stop holding that axe, or if any log can't be broken. Because it works from the top down, what's left is a short stump you can reach.
- It can't trigger itself: logs that Felling breaks never start another fell.

### Felling II leaf clearing

- It only runs after a **complete** fell. If the axe breaks or a log is protected, every leaf stays.
- **Which leaves:** natural leaves connected to the felled tree's canopy (including corner/edge contact) that either:
  - **belong to the felled tree**: a leaf type its species grows (oak logs → oak, azalea and flowering azalea leaves; birch → birch; and so on) and strictly nearer, in a straight line, to the felled trunk or branches than to any other log that could have grown it. That clears the canopy even where it touches a neighbour's. Ties go to the standing tree. Other species never compete (a birch can't have grown oak leaves), while unknown or modded logs always do; or
  - **would decay anyway** once the felled tree is gone, by vanilla's rule: more than 6 steps, face to face through leaves, from any remaining log. So nothing is left to decay slowly.
- **Neighbours:** another tree's logs are never touched, and leaves of a different species never count as the felled tree's. Where two trees of the **same species** have merged canopies, nothing in the world records which tree grew which leaf, so the seam is split by distance. In testing (12 touching oak pairs), the standing tree lost about 6% of its leaves at the seam, and about 4 of the felled tree's leaves per pair stayed because they sat nearer the standing trunk.
- Player-placed (persistent) leaves are never touched.
- **Drops:** exactly what natural decay drops, using vanilla's own code. Oak and dark oak saplings 1 in 20, apples 1 in 200, sticks 1 in 50; other species use their own vanilla rates. Fortune and Silk Touch on the axe don't apply, and leaf blocks never drop.
- **Cost:** no durability or hunger for leaves; only logs cost durability.
- **Protection:** each leaf fires its own break event, so claim mods can keep leaves in their area; spawn protection and the world border are respected.

### Tree detection rules (v1)

Felling only fires when the structure is clearly an untouched natural tree. Anything unclear gets a normal one-log break, because a missed tree costs a few clicks while a wrongly felled build costs a lot more.

**Supported species:** oak (including fancy, swamp and azalea trees), birch (including tall birch), spruce and pine (1×1 and 2×2), jungle (1×1 and 2×2), acacia, dark oak, cherry.
**Not supported in v1:** mangrove (roots and propagules make the shape ambiguous), crimson and warped fungi (no leaves), stripped logs and bark (wood) blocks, and modded logs.

The broken block must pass every rule below:

1. **Trigger:** it's a vertical (`axis=y`) log of a supported species in a straight column of the same logs down to one sitting directly on `#minecraft:dirt` (grass, dirt, podzol, coarse/rooted dirt, moss, mud...). That bottom log is the tree's base; every rule below is checked on the **whole** tree from the base, however high the cut.
2. **Trunk:** the trunk is all same-species logs connected to the base, including diagonally, since natural branches join that way. The search caps are:
   - at most **8 blocks** sideways (per axis) and **40 blocks** up from the base;
   - nothing below the base;
   - at most **256 logs**.
   If a connected log falls outside those limits, nothing is felled.
3. **Footprint:** at the base's height, the trunk must be just the base log, or a complete **2×2** for spruce, jungle and dark oak. Each of those base logs must be vertical and stand on dirt.
4. **Nothing foreign touching:** each side of each trunk log may touch only:
   - air;
   - its own trunk;
   - natural leaves;
   - plants (grass, ferns, flowers, vines, glow lichen, berry bushes, mushrooms, pink petals);
   - cocoa, bee nests and snow;
   - terrain, but only for the bottom three logs. Terrain means dirt, stone, sand, gravel, clay, terracotta, sandstone, snow and powder snow.

   Anything else rules the tree out: planks, glass, torches, ladders, signs, fences, other species' logs, or **player-placed leaves**.
5. **Real canopy:** at least **5 natural leaves** touch the trunk, and its topmost logs touch natural leaves.

#### Natural vs. placed logs: what this can and can't tell

Minecraft doesn't record who placed a log, so no rule can be sure. These rules rely on the signals the game does keep:

- **Leaves are the strongest signal.** Leaves that grew on a tree are saved as non-persistent. Leaves a player places are saved as persistent and never decay. That holds in existing worlds too, so a decorative tree built with placed leaves is never felled.
- **Logs carry no such flag.** A log column you placed by hand, standing on dirt with natural leaves around its top, would look like a tree. In practice that means building a pillar up into a real tree's canopy.
- **Player beams touching a tree** become part of its trunk. A beam that stays inside the search box and touches only air and leaves would be felled with the tree. Longer beams, or beams touching planks or other blocks, stop the fell.
- **Trees you've decorated** won't fell. A torch, sign, ladder or fence on the trunk is enough. That's on purpose.
- **Trees grown from saplings** count as natural, because their leaves are non-persistent.
- **Village houses** sit on cobblestone or touch planks, so their log pillars never trigger.

### Compatibility

- Pure server-side logic plus data. The client only needs the mod for the enchantment's name, so install it on both sides.
- It doesn't touch vanilla classes and has no mixins. It uses NeoForge's `BreakEvent` at lowest priority, so protection mods run first.
- Tested alongside **Book of Dragons 1.4.1** and **FAUNA Engine 1.2**.

---

## Ron's Veinbreaking

Mine one block of an ore vein with a Veinbreaking pickaxe and the rest of the vein comes with it.

### Getting it

One level, a normal (non-treasure) enchantment with the same rarity and cost as Felling I: enchanting table (pickaxes, early game: level 5+, so a table with no bookshelves can offer it; roughly 1 in 5 offers at most table levels), librarians, loot, and the anvil (cost 4).

```
/give @s minecraft:netherite_pickaxe[minecraft:enchantments={levels:{"ronsveinbreaking:veinbreaking":1}}]
```

### How it works

- **What counts as ore**: anything in `#ronsveinbreaking:vein_ores`, which is `#c:ores`, so modded ores tagged the usual way work too.
- **The vein**: every ore block of the same kind touching the mined one, through faces, edges or corners (natural ore blobs often connect only diagonally). Same kind means the same block or a shared `c:ores/<kind>` tag, so stone and deepslate iron mine together but coal touching iron stays.
- **Limit**: the mined block plus the 64 nearest. Natural vanilla veins are far smaller.
- **Each block is mined like a normal player break**: Fortune and Silk Touch apply, experience drops, one durability per block, and drops land where each block was.
- **It does nothing** when sneaking, with a pickaxe too weak to harvest the ore (so a vein is never wasted), on non-pickaxes, or for fake players (machines).
- **It stops** if the pickaxe breaks or leaves your hand. A block a protection mod refuses is skipped and the rest of the vein is still mined.

---

## Ron's Harvesting

Harvest one ripe crop with a Harvesting hoe and the rest of the ripe field comes with it.

- **Harvesting I**: the ripe field is harvested and the spots are left empty.
- **Harvesting II**: the field is also replanted.

### Getting it

A normal (non-treasure) enchantment with the same rarity and costs as Felling: enchanting table (hoes; Harvesting I is early game at level 5+, Harvesting II is mid-game at level 20+ and replaces Harvesting I from there; roughly 1 in 5 offers at most table levels, about 3 in 10 at level 30), librarians, loot, and the anvil (cost 4 per level; two Harvesting I combine into Harvesting II).

```
/give @s minecraft:netherite_hoe[minecraft:enchantments={levels:{"ronsharvesting:harvesting":1}}]
/give @s minecraft:netherite_hoe[minecraft:enchantments={levels:{"ronsharvesting:harvesting":2,"minecraft:fortune":3}}]
```

### How it works

- **What it harvests**:
  - crops (wheat, carrots, potatoes, beetroot, and modded crops built on vanilla's crop block) at full growth;
  - nether wart at full growth;
  - cocoa pods at full growth;
  - melons and pumpkins that a stem is attached to. A melon or pumpkin with no stem pointing at it was placed by hand and is never touched.
- **Only ripe plants trigger it.** Breaking an unripe crop is a normal break, and unripe plants are always left standing.
- **The field**: every plant of the same kind connected to the harvested one through faces, edges or corners, ripe or not, so unripe patches and terrace steps don't split a field. An empty block of farmland does. Melons and pumpkins connect through their stems, and cocoa pods connect around their log. Wheat next to carrots stays.
- **Limit**: the harvested plant plus the 80 nearest ripe ones, so one full 9×9 water-centred plot.
- **Replanting (Harvesting II)**: each crop, wart and pod is replanted at age 0 using one seed (wheat seeds, carrot, potato, beetroot seeds, nether wart, cocoa beans) taken from its own drops. No seeds come from nothing. If a plant drops no seed, its spot is left empty. Melon and pumpkin stems stay, so nothing is planted where the fruit was. In creative mode nothing drops and everything is still replanted.
- **Drops are exactly vanilla.** Each plant is harvested like a normal player break, so it uses the vanilla loot table: drops land where each plant was, and durability works as in vanilla. Harvesting I drops exactly what breaking the crops by hand would; Harvesting II drops that minus the one seed per replant.
- **Fortune**: vanilla already allows Fortune on hoes (enchanting table, anvil, books) and the crop loot tables read it from whatever tool breaks the crop, so a Fortune III hoe gives the same extra seeds, carrots and potatoes as a Fortune III pickaxe. A ripe wheat drops 1 + binomial(3 + Fortune level, 4/7) seeds: 2.71 on average, 4.43 with Fortune III. Crops and nether wart cost a hoe nothing; melons, pumpkins and cocoa cost one each.
- **It does nothing** when sneaking, on non-hoes, or for fake players (machines).
- **It stops** if the hoe breaks or leaves your hand. A plant a protection mod refuses to let you break is skipped. A replant a protection mod refuses (place event) is skipped and its seed is dropped back.

---

## Building

You need a JDK 21. The Gradle wrapper downloads everything else.

```
./gradlew build                          # all mods → <mod>/build/libs/*.jar
./gradlew :felling:build                 # one mod
./gradlew :felling:runGameTestServer     # headless in-game test suite (exits non-zero on failure)
./gradlew :felling:runClient             # dev client
./gradlew :felling:runClient -PquickPlay=<world>   # dev client straight into run/saves/<world>
```

Replace `felling` with `veinbreaking` or `harvesting` for the other mods. The output is `<mod>/build/libs/<modid>-<version>+mc1.21.1.jar`.

### Layout

```
build.gradle            shared ModDevGradle setup applied to every subproject
gradle.properties       shared versions (Minecraft, NeoForge, Parchment) and metadata
templates/              shared neoforge.mods.toml template
felling/, veinbreaking/, harvesting/
  gradle.properties     mod_id, mod_name, mod_version, mod_description
  src/main/             the mod (shipped)
  src/gametest/         in-game tests (dev only, never in the JAR)
tools/                  helper scripts (empty game-test arena generator)
```

### Adding another mod

1. Copy `harvesting/gradle.properties` to `<name>/gradle.properties` and change the values.
2. Add `<name>/src/main/...` (an `@Mod` class plus `data/<modid>/enchantment/*.json`). Each mod copies and adapts what it needs from the others (handler, test arena); there is no shared library.
3. Add `include '<name>'` to `settings.gradle`.

### Game tests

`runGameTestServer` boots a headless server, runs the mod's tests, then exits.

**Felling** (74 tests):

- 16 vanilla tree features, each grown from 12 seeds with real worldgen, felled with Felling I and with Felling II;
- Felling II in a grove of touching trees: everything vanilla would decay is cleared, the felled canopy is cleared where it touches the neighbours, the neighbouring birch keeps every leaf, and nothing is left decaying. Also 12 touching same-species oak pairs, and drop rates matching decay over 8,000+ leaves;
- Felling II keeping placed leaves and protected leaves, and leaving all leaves after an incomplete fell;
- the same 16 trees cut 2 logs up (exactly the logs above the cut fall), a small oak cut midway, a mega spruce cut halfway with Felling II (the stump keeps its lower canopy);
- the ordinary-axe, top-log, branch, sneaking and non-axe cases;
- player builds;
- running out of durability and a protected log;
- registration and enchanting-table availability.

**Veinbreaking** (23 tests):

- 8 vanilla ore features (coal, iron, large copper, gold, redstone, lapis, large diamond, emerald), each grown from 12 seeds across the stone/deepslate boundary: exactly the vein is mined, no rock, durability one per block;
- diagonal connections, gaps, stone/deepslate variants joining and other ores not, the 64-block limit (nearest first);
- the ordinary-pickaxe, sneaking, too-weak-pickaxe, non-pickaxe and plain-stone cases;
- drops and experience, Silk Touch, running out of durability and a protected block;
- registration and enchanting-table availability.

**Harvesting** (32 tests):

- a full 9×9 wheat plot around its water, a 3×3 of carrots, potatoes, beetroot and nether wart, 12 cocoa pods round a jungle trunk: everything ripe harvested and replanted at age 0, with exactly one seed per replant missing from the drops;
- melons joined only through their stems, pumpkins, hand-placed melons and a neighbouring pumpkin left;
- unripe crops left and bridged, an unripe crop breaking alone, diagonal and terrace-step crops, a gap, a different crop beside the field, the 80-plant limit (nearest first);
- the ordinary-hoe, sneaking and non-hoe cases;
- running out of durability on melons, a protected crop, a refused replant (seed dropped back), creative mode;
- Harvesting I harvesting crops, nether wart and cocoa without replanting or taking any seed;
- drop rates over 972 crops per run: Harvesting I with and without Fortune III, Harvesting II with Fortune III, and carrots, against vanilla's loot formula and against a Fortune III pickaxe and plain hoe as vanilla controls;
- registration, Fortune on hoes, and enchanting-table availability of both levels.

To check compatibility with other mods, drop their JARs into `<mod>/run/mods/` and rerun.

Based on the official NeoForge MDK (`NeoForgeMDKs/MDK-1.21.1-ModDevGradle`, MIT, see `MDK_TEMPLATE_LICENSE.txt`).
