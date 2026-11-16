# Robots

Robots is a Fabric addon for Voltworks Industrial on Minecraft 26.3. It adds humanoid worker robots that run on Voltworks Power Cells: they walk around, carry items and automate jobs around a base.

## Using a robot

- **Place it**: use the robot item on a block. The robot stands there, and that spot becomes its **home**; it works in an area around home and returns there when idle.
- **Charge it**: right-click with a Power Cell (1,000 J each, 10,000 J stored), or give it a Robot Charging Pad (see below).
- **Ask what it is doing**: punch it. It reports its job, anything holding it up ("needs seeds", "no chest with room nearby") and its energy; a Farm Robot also outlines its work area in green particles.
- **Inventory**: right-click with anything else to open its inventory, nine slots like a one-row chest. The robot holds the first item it carries in its hand.
- **Pause / resume**: sneak + right-click, or use a Robot Terminal.
- **Pick it up**: sneak + punch. It drops what it carries and turns back into its item, keeping its energy and name.

Robots cannot be hurt by mobs, falls or players (other than the sneak + punch above).

## Robots

- **Farm Robot**: walks to ripe crops within 8 blocks of home (2 up or down), harvests and replants them, and carries the harvest to the nearest chest when its inventory is full or nothing is left to harvest. Give it seeds and it plants empty farmland; give it a hoe as well and it tills a marked field (see below).
- **Field Marker**: marks the Farm Robot's field. Crafting gives two.
- **Animal Breeder Robot**: fetches breeding food from a nearby chest, walks to two adult animals of one species and feeds them. It stops breeding a species once 12 of them (adults and babies) are within range.
- **Organizer Robot**: sorts an input chest into the other chests around its home. Label chests with signs or item frames to say what goes where (see below). Without labels it carries each stack to a chest that already holds that item, or claims a completely empty chest for a new kind of item.
- **Robot Terminal**: right-click for a screen listing every robot within 16 blocks (see below).
- **Robot Charging Pad**: charges robots from Voltworks power.
- **Robot Core**: crafting component for the robots and the terminal.

Robots look for chests within 8 blocks of home (4 up or down). A robot that cannot reach a crop or chest gives up after 15 seconds and ignores that block for a minute.

## Robot Terminal

Right-click the terminal to open its screen. The left side lists every robot within 16 blocks, nearest first, each with a state light (green running, red paused, amber out of power) and an energy bar. Click one to select it; the right side then shows what it is doing, what is holding it up, its energy, and:

- **Pause / Resume** for that robot, and **Pause all** / **Resume all** for every robot in range.
- **Show area**: outlines a Farm Robot's work area in particles.
- **Clear field**: forgets a Farm Robot's marked field, so it works the square around home again.
- **Settings**, changed with the - and + buttons. They belong to that one robot, are saved with it and stay with it when it is picked up and placed again.

| Robot | Setting | Range |
|---|---|---|
| All | Recharge below (when it walks to a charging pad) | off, or 5% to 100% |
| Farm | Work radius (when no field is marked) | 1 to the config's `farm.radius` |
| Farm | Seeds kept when unloading | 0 to 64 |
| Breeder | Animal range | 1 to the config's `breeder.animalRange` |
| Breeder | Animals per kind | 2 to the config's `breeder.maxAnimalsPerSpecies` |
| Organizer | Items per trip | 1 to the config's `organizer.maxItemsPerTrip` |

A new robot starts with the values from the config file. For ranges and amounts the config value is also the most a player can set at a terminal, so on a server the config still sets the limits. The screen only works while you stand within 8 blocks of the terminal.

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

A robot whose energy drops below 20% walks to the nearest pad within 16 blocks of its home spot (4 up or down), charges until full and goes back to work. A robot with no energy at all can still walk to a pad. If the pad has no power, a robot that can still work leaves after 5 seconds and tries again 30 seconds later; a robot that cannot work waits on the pad. Placing a robot's home on a pad keeps it topped up whenever it is idle.

Recipe: tin ingot, redstone, tin ingot over copper cable, Power Cell, copper cable.

## Marking a field

Field Markers work like the Voltworks Quarry's markers.

1. Stand one Field Marker within 2 blocks of the Farm Robot's home spot, on the ground at one corner of the field.
2. Stand the second at the opposite corner (a field is at most 17 blocks long and wide). Right-click either marker to check the pairing and the field's size.
3. Within a couple of seconds the robot takes both markers and the rectangle between them, corners included, becomes its field. It now works only there instead of in the square around home.

Put a hoe and seeds (wheat seeds, beetroot seeds, carrots, potatoes) in the robot's inventory. In a marked field it clears grass and flowers, tills dirt, grass blocks and dirt paths, and plants each block as it goes. It only tills while it has seeds, and tilling wears the hoe. Without a marked field it never tills, but still plants empty farmland around home.

When it unloads into a chest the robot keeps its hoe and 16 seeds of each kind. The markers go into the chest with the harvest. To mark a different field, place two markers again; to go back to the square around home, pick the robot up and place it again. Fields need water as usual: the robot does not place any.

## Config

`config/robots.properties` is created on first start and lists every limit with its allowed range: energy storage and cost, when robots go to recharge, the charging pad's range, speed and capacity, work intervals, the farm radius, the largest marked field, seeds kept, and tilling and planting costs, the breeder's animal range, animal cap and food per trip, the organizer's items per trip, chest range and terminal range. Edit it and restart the game or server. On a server, only the server's file matters.

## Build

Requires JDK 25 and a built Voltworks jar (`../voltworks/build/libs/voltworks-0.2.0.jar`, see `voltworks_jar` in `gradle.properties`). From this directory, run the Voltworks project's wrapper:

```powershell
..\voltworks\gradlew.bat -p . build
```

Install the resulting `build/libs/robots-0.2.0.jar` alongside Voltworks Industrial and Fabric API.

- In game, the Voltworks Guide book has eight Robots pages after its own. They come from `tools/guide.txt` (pages separated by `---`, first line is the title, `@items` adds icons); the test fails if a page has more text than fits.
- Textures, item and block models, translations and the guide pages are generated: `java tools/GenAssets.java src/main/resources`. Recipes, the terminal's loot table and tags are plain JSON.
- In-world test: `..\voltworks\gradlew.bat -p . runClientGameTest` (prints `[robots-test] ALL CHECKS PASSED`; screenshots land in `build/run/clientGameTest/screenshots/`).

## Upgrading from 0.1.0

0.1.0's robots were blocks. Those blocks no longer exist, so any placed in a world disappear when it is loaded with 0.2.0 (the items in inventories and chests stay and now place a walking robot). The Miner Robot was removed; the Voltworks Quarry does that job.
