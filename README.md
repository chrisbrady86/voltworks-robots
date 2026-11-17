# Robots

Robots is a Fabric addon for Voltworks Industrial on Minecraft 26.3. It adds humanoid worker robots that run on Voltworks Power Cells: they walk around, carry items and automate jobs around a base.

## Using a robot

- **Place it**: use the robot item on a block. The robot stands there, and that spot becomes its **home**; it works in an area around home and returns there when idle. Whoever places a robot owns it.
- **Charge it**: right-click with a Power Cell (1,000 J each, 10,000 J stored), or give it a Robot Charging Pad (see below).
- **Ask what it is doing**: punch it. It reports its job, anything holding it up ("needs seeds", "no chest with room nearby") and its energy; a Farm Robot also outlines its work area in green particles.
- **Inventory**: right-click with anything else to open its inventory, nine slots like a one-row chest. The robot holds the first item it carries in its hand.
- **Pause / resume**: sneak + right-click, or use a Robot Terminal.
- **Pick it up**: sneak + punch. It drops what it carries and turns back into its item, keeping its energy and name.

Robots cannot be hurt by mobs, falls or players (other than the sneak + punch above). They cannot be put on a lead, do not get into boats or minecarts, do not go through portals and do not trample farmland. A robot that is killed outright (the /kill command, the void) drops what it carried and itself as its item, with its energy, settings and name.

**Owners.** Only the player who placed a robot can open its inventory, pause it, change its settings or pick it up; operators and creative-mode players can too. Anyone can punch it for its status or charge it; only someone who may control it can rename it with a name tag. Robots placed before owners existed belong to nobody and work for everyone, and `robot.ownerOnly=0` in the config turns the lock off.

**Getting unstuck.** A robot that keeps failing to reach things, stands still for 15 seconds while trying to walk, or cannot get home for 20 seconds is put back on its home spot with a puff of smoke.

## Robots

- **Farm Robot**: walks to ripe crops within 8 blocks of home (2 up or down), harvests and replants them (replanting uses up one of the seeds it just harvested), and carries the harvest to the nearest chest when its inventory is full or nothing is left to harvest. It also cuts pumpkins and melons off their stems, sugar cane and cactus above the bottom block, and ripe nether wart. Give it seeds and it plants empty farmland; give it a hoe as well and it tills a marked field (see below). When it runs out of seeds or its hoe breaks, it takes more from a chest near home.
- **Field Marker**: marks a Farm Robot's field, a pen for the Breeder, Rancher or Butcher, or a Lumberjack's tree farm. Crafting gives two.
- **Animal Breeder Robot**: fetches breeding food from a nearby chest, walks to two adult animals of one species and feeds them. It stops breeding a species once 12 of them (adults and babies) are within range. Mark a pen with Field Markers and it only counts and breeds the animals inside. Cows, sheep, pigs, chickens and "others" can each be switched off at a Robot Terminal.
- **Lumberjack Robot**: needs an axe. It fells trees within 8 blocks of home (or in a marked area) log by log from the top, replants a sapling where the tree stood, picks up the saplings, sticks and apples the leaves drop, and carries the wood to the nearest chest. It keeps its axe and 16 saplings of each kind, and takes a new axe from a chest when one breaks. It only cuts trunks that grow out of the ground, are at most 2x2 logs wide at the bottom and have natural leaves touching them and around their top. Logs that touch planks, glass, doors, trapdoors, stairs, slabs, walls or beds, or that are joined to stripped logs, are taken for a building and left alone, as are hedges of placed leaves. A fence against a trunk does not count. Big 2x2 trees are felled but get a single sapling back.
- **Organizer Robot**: sorts an input chest into the other chests around its home. Label chests with signs or item frames to say what goes where (see below). Without labels it carries each stack to a chest that already holds that item, or claims a completely empty chest for a new kind of item.
- **Rancher Robot**: shears the sheep in its pen (8 blocks around home, or an area marked with Field Markers) with its built-in shears, and picks up the wool, eggs, feathers, leather, rabbit hide and scutes lying there (but not what a player threw down). Everything goes to the nearest chest.
- **Butcher Robot**: keeps each kind of animal in its pen at a set number (6 by default, never fewer than 2, adults and babies counted). When there are more, it culls adults with its built-in cleaver and collects the meat, leather and wool. It never touches babies, named animals, pets and anything else tamed or owned (horses, donkeys, llamas), animals wearing a saddle or armour, animals on a lead, or animals carrying a rider or sitting in a boat or minecart. It only picks up what its own culling dropped, never items a player dropped or lost. Cows, sheep, pigs and chickens are on by default; "others" (horses, foxes ...) are off until switched on at a terminal.
- **Guard Robot**: fights hostile mobs within 12 blocks of home with its built-in sword, then picks up the drops of the monsters it killed (bones, string, arrows, rotten flesh, gunpowder ...; never items a player dropped or lost) and takes them to a chest. Endermen and Zombified Piglins are only fought once they are angry, Piglins only while they are attacking someone. Mobs with a name tag or on a lead and zombie villagers that are being cured are left alone. Creepers can be switched off at a terminal: a creeper the robot hits may turn on it and explode. The robot only starts on a creeper when it has the energy to finish it, and does not walk off to recharge in the middle of that fight.
- **Fisher Robot**: give it a home within 6 blocks of open water (a water source with air above) that has a dry bank. It stands on the bank and lands a catch every 10 to 30 seconds from the vanilla fishing loot: fish and junk, no treasure (that needs a real bobber in open water). The catch goes to a chest when its inventory is full; with no chest that has room it stops fishing and waits at home.
- **Robot Terminal**: right-click for a screen listing every robot within 16 blocks (see below).
- **Robot Charging Pad**: charges robots from Voltworks power.
- **Robot Core**: crafting component for the robots and the terminal.

Robots look for chests within 8 blocks of home (4 up or down). They leave alone chests locked with a key item and loot chests nobody has opened yet (a dungeon or village chest). A robot that cannot reach a crop, chest, animal or monster gives up after a few seconds (15 at most) and ignores it for a minute.

## Robot Terminal

Right-click the terminal to open its screen. The left side lists every robot within 16 blocks, nearest first, each with a state light (green running, red paused, amber out of power) and an energy bar. Click one to select it; the right side then shows what it is doing, what is holding it up, its energy, and:

- **Pause / Resume** for that robot, and **Pause all** / **Resume all** for every robot in range.
- **Show area**: outlines the robot's work area in particles.
- **Clear field**: forgets a robot's marked field, pen or tree farm, so it works the square around home again.
- **Settings**, changed with the - and + buttons. They belong to that one robot, are saved with it and stay with it when it is picked up and placed again.

| Robot | Setting | Range |
|---|---|---|
| All | Recharge below (when it walks to a charging pad) | off, or 5% to 100% |
| Farm | Work radius (when no field is marked) | 1 to the config's `farm.radius` |
| Farm | Seeds kept when unloading | 0 to 64 |
| Breeder | Animal range | 1 to the config's `breeder.animalRange` |
| Breeder | Animals per kind | 2 to the config's `breeder.maxAnimalsPerSpecies` |
| Breeder | Cows, Sheep, Pigs, Chickens, Others | on or off |
| Lumberjack | Work radius (when no area is marked) | 1 to the config's `lumber.radius` |
| Organizer | Items per trip | 1 to the config's `organizer.maxItemsPerTrip` |
| Rancher | Work radius (when no pen is marked), Shear sheep | 1 to `rancher.radius`; on or off |
| Butcher | Work radius (when no pen is marked) | 1 to the config's `butcher.radius` |
| Butcher | Animals kept per kind | 2 to 200 (starts at `butcher.animalsKept`) |
| Butcher | Cows, Sheep, Pigs, Chickens, Others | on or off |
| Guard | Guard radius, Fight creepers | 1 to `guard.radius`; on or off |
| Fisher | Water search radius | 1 to the config's `fisher.radius` |

A new robot starts with the values from the config file. For ranges and amounts the config value is also the most a player can set at a terminal, so on a server the config still sets the limits. The screen only works while you stand within 8 blocks of the terminal. Other players' robots are listed with "Belongs to ..." and their buttons switched off.

## Labelling chests for the Organizer Robot

Put a sign on a chest (sneak + right-click to hang it on a side, or stand it on top) and write what belongs in it, one label per line. Capitals do not matter.

| Line on the sign | What goes in the chest |
|---|---|
| `ores`, `ingots`, `logs`, `seeds`, `wool`, `glass` ... | Items whose name contains that word, or that are in an item tag of that name. Singular and plural both work. |
| `iron`, `copper`, `oak` ... | Everything of that material: `iron` takes iron ore, raw iron, iron ingots and iron tools. |
| `oak log`, `iron ingot` | That item (and anything else with those words together in its name). |
| `wood`, `metal`, `stone`, `plant`, `food` | Broader groups: logs and planks; ingots, nuggets and raw metals; stone and cobblestone; seeds, saplings and flowers; anything edible. |
| `@voltworks` | Every item from that mod. |
| `#c:ores` | Every item in that exact item tag. |
| `misc`, `other`, `rest`, `everything` or `*` | Whatever has no other home. |
| `input` | This is the chest to sort out of. |

An **item frame** on a chest adds the exact item it shows, with no typing.

How the robot decides, best match first: a chest whose item frame shows the item, a chest whose sign names it, an unlabelled chest that already holds it, a `misc` chest, an unlabelled empty chest. Among equals the chest nearest the robot's home wins, and a full chest is passed over. A labelled chest never receives anything its labels do not cover. Items with nowhere to go stay in the input chest.

The input chest is the one with an `input` sign. Without one, it is the unlabelled chest nearest the robot's home.

## Charging pad

The Robot Charging Pad is a low plate that connects to Voltworks cables, generators and batteries like any machine. It holds 4,000 J and gives 100 J per tick to any robot standing on it or right beside it. Right-click it to see how much it holds.

A robot whose energy drops below 20% walks to the nearest pad within 16 blocks of its home spot (4 up or down), charges until full and goes back to work. While there is no pad in range it looks again after 5, 10, 20 and 40 seconds and then once a minute, so a newly placed pad can take up to a minute to be noticed. A robot with no energy at all can still walk to a pad. If the pad has no power, a robot that can still work leaves after 5 seconds and tries again 30 seconds later; a robot that cannot work waits on the pad. Placing a robot's home on a pad keeps it topped up whenever it is idle.

Recipe: tin ingot, redstone, tin ingot over copper cable, Power Cell, copper cable.

## Marking a field, pen or tree farm

Field Markers work like the Voltworks Quarry's markers, and the same steps set a Farm Robot's field, an Animal Breeder Robot's pen and a Lumberjack Robot's tree farm.

1. Stand one Field Marker within 2 blocks of the robot's home spot, on the ground at one corner of the area.
2. Stand the second at the opposite corner (a field is at most 17 blocks long and wide). Right-click either marker to check the pairing and the field's size.
3. Within a couple of seconds the robot takes both markers and the rectangle between them, corners included, becomes its field. It now works only there instead of in the square around home.

Markers remember who placed them. A marker only pairs with another marker placed by the same player, and a robot only takes markers placed by someone who may control it (its owner, an operator or a creative-mode player), so nobody else can redirect a robot or use up your markers. If the server restarts while a marker is standing it forgets who placed it; such a marker still works while someone who may control the robot stands within 8 blocks of it, or break it and place it again. A robot that already has a marked area ignores new markers until that area is cleared.

Put a hoe and seeds (wheat seeds, beetroot seeds, carrots, potatoes) in the robot's inventory. In a marked field it clears grass and flowers, tills dirt, grass blocks and dirt paths, and plants each block as it goes. It only tills while it has seeds, and tilling wears the hoe. Without a marked field it never tills, but still plants empty farmland around home.

When it unloads into a chest the robot keeps its hoe and 16 seeds of each kind. The markers go into the chest with the harvest. To mark a different field, press Clear field at a Robot Terminal and then place two markers again; Clear field alone takes the robot back to the square around home. Fields need water as usual: the robot does not place any.

## Config

`config/robots.properties` is created on first start and lists every limit with its allowed range: energy storage and cost, whether robots are locked to their owner, when robots go to recharge, the lumberjack's radius, speed and cost, the charging pad's range, speed and capacity, work intervals, the farm radius, the largest marked field, seeds kept, and tilling and planting costs, the breeder's animal range, animal cap and food per trip, the organizer's items per trip, chest range and terminal range. Edit it and restart the game or server. On a server, only the server's file matters.

## Build

Requires JDK 25 and a built Voltworks jar (`../voltworks/build/libs/voltworks-0.2.0.jar`, see `voltworks_jar` in `gradle.properties`). From this directory, run the Voltworks project's wrapper:

```powershell
..\voltworks\gradlew.bat -p . build
```

Install the resulting `build/libs/robots-0.2.0.jar` alongside Voltworks Industrial and Fabric API.

- In game, the Voltworks Guide book has eight Robots pages after its own. They come from `tools/guide.txt` (eight pages is the limit while the Voltworks and Quarry pages share the contents page; pages separated by `---`, first line is the title, `@items` adds icons); the test fails if a page has more text than fits.
- Textures, item and block models, translations and the guide pages are generated: `java tools/GenAssets.java src/main/resources`. Recipes, the terminal's loot table and tags are plain JSON.
- In-world test: `..\voltworks\gradlew.bat -p . runClientGameTest` (prints `[robots-test] ALL CHECKS PASSED`; screenshots land in `build/run/clientGameTest/screenshots/`).

## Upgrading from 0.1.0

0.1.0's robots were blocks. Those blocks no longer exist, so any placed in a world disappear when it is loaded with 0.2.0 (the items in inventories and chests stay and now place a walking robot). The Miner Robot was removed; the Voltworks Quarry does that job.
