package dev.voltworks.robots.test;

import dev.voltworks.robots.block.RobotTerminalBlock;
import dev.voltworks.robots.block.entity.ChargingPadBlockEntity;
import dev.voltworks.robots.network.TerminalActionPayload;
import dev.voltworks.robots.network.TerminalViewPayload;
import dev.voltworks.robots.entity.BreederRobot;
import dev.voltworks.robots.entity.FarmRobot;
import dev.voltworks.robots.entity.OrganizerRobot;
import dev.voltworks.robots.entity.RobotEntity;
import dev.voltworks.robots.registry.RobotBlocks;
import dev.voltworks.robots.registry.RobotEntities;
import dev.voltworks.robots.registry.RobotItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * In-world test: three platforms in the air above spawn, one robot each (farm, breeder, organizer), runs the
 * game and checks what they did. Run with: ../voltworks/gradlew -p . runClientGameTest
 */
public class RobotsClientGameTest implements FabricClientGameTest {
	private static final int FARM_X = 0, BREED_X = 30, SORT_X = 60, SHOW_X = 90, FIELD_X = -30, LABEL_X = 30, LABEL_Z = 30, LUMBER_X = 0, LUMBER_Z = 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runOnServer(RobotsClientGameTest::build);
			context.waitTicks(100);
			view(world, FARM_X + 3, 4, -2, 30, 25);
			context.waitTicks(20);
			context.takeScreenshot("robots-farm");
			view(world, SHOW_X, 0, 4, 180, 5);
			context.waitTicks(20);
			context.takeScreenshot("robots-lineup");
			view(world, SHOW_X + 3, 1, -3, 35, 15);
			context.waitTicks(20);
			context.takeScreenshot("robots-lineup-back");
			context.waitTicks(500);
			view(world, BREED_X + 4, 4, -3, 30, 25);
			context.waitTicks(20);
			context.takeScreenshot("robots-breeder");
			view(world, SORT_X + 3, 4, -3, 20, 25);
			context.waitTicks(20);
			context.takeScreenshot("robots-organizer");
			context.waitTicks(700);
			view(world, FIELD_X + 4, 5, -4, 35, 35);
			context.waitTicks(20);
			context.takeScreenshot("robots-field");
			view(world, FIELD_X + 3, 2, -8, 0, 20);
			context.waitTicks(20);
			context.takeScreenshot("robots-charging");
			view(world, LABEL_X + 5, 5, LABEL_Z - 7, 35, 30);
			context.waitTicks(20);
			context.takeScreenshot("robots-labels");
			view(world, LUMBER_X + 5, 6, LUMBER_Z - 9, 25, 25);
			context.waitTicks(20);
			context.takeScreenshot("robots-lumberjack");
			view(world, BREED_X, 3, -4, 0, 20); // back to the middle so every platform is loaded for the checks
			context.waitTicks(40);

			List<String> failures = new ArrayList<>(world.getServer().computeOnServer(RobotsClientGameTest::check));

			// Robot Terminal: its actions, then its screen
			failures.addAll(world.getServer().computeOnServer(RobotsClientGameTest::checkTerminal));
			context.waitTicks(30);
			if (!context.computeOnClient(client -> client.gui.screen() instanceof dev.voltworks.robots.client.TerminalScreen)) {
				failures.add("Right-clicking the terminal should open its screen");
			}
			context.takeScreenshot("robots-terminal");
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(5);

			// The robot's inventory screen, opened through the real right-click path
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				FarmRobot robot = robot(server, FarmRobot.class, FARM_X);
				robot.toggleRunning();
				command(server, String.format("tp @a %.2f %.2f %.2f", robot.getX(), robot.getY(), robot.getZ() - 2));
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				robot.interact(player, InteractionHand.MAIN_HAND, robot.position());
			});
			context.waitTicks(30);
			context.takeScreenshot("robots-inventory");
			failures.addAll(world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				boolean open = player.containerMenu != player.inventoryMenu;
				player.closeContainer();
				return open ? List.<String>of() : List.of("Right-clicking a robot should open its inventory");
			}));
			failures.addAll(world.getServer().computeOnServer(RobotsClientGameTest::checkPickUp));

			// The Voltworks Guide includes the robots' pages, and all of them fit
			context.waitTicks(10);
			List<String> titles = context.computeOnClient(client -> dev.voltworks.client.GuideScreen.loadPages().stream().map(p -> p.title()).toList());
			int first = titles.indexOf("Robots");
			if (first < 0 || !titles.contains("Chest Labels")) failures.add("The Voltworks Guide should include the robots' pages: " + titles);
			failures.addAll(context.computeOnClient(client -> dev.voltworks.client.GuideScreen.problems()));
			for (String shot : new String[]{"Robots", "Marking an Area", "Lumberjack Robot"}) {
				int page = Math.max(0, titles.indexOf(shot));
				context.runOnClient(client -> client.gui.setScreen(new dev.voltworks.client.GuideScreen(page)));
				context.waitTicks(10);
				context.takeScreenshot("robots-guide-" + shot.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
			}
			context.runOnClient(client -> client.gui.setScreen(null));

			if (!failures.isEmpty()) {
				throw new AssertionError("Robots checks failed:\n - " + String.join("\n - ", failures));
			}
			System.out.println("[robots-test] ALL CHECKS PASSED");
		}
	}

	private static void view(TestSingleplayerContext world, int dx, int dy, int dz, int yaw, int pitch) {
		world.getServer().runOnServer(server -> {
			BlockPos o = origin(server);
			command(server, String.format("tp @a %d %d %d %d %d", o.getX() + dx, o.getY() + dy, o.getZ() + dz, yaw, pitch));
		});
	}

	private static BlockPos origin(MinecraftServer server) {
		BlockPos spawn = server.getRespawnData().pos();
		return new BlockPos(spawn.getX(), 200, spawn.getZ());
	}

	private static void command(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
	}

	private static void set(ServerLevel level, BlockPos pos, Block block) {
		level.setBlockAndUpdate(pos, block.defaultBlockState());
	}

	/** A 13x13 stone floor with a glass rim so nothing wanders off; the floor is one block below the origin. */
	private static void platform(ServerLevel level, BlockPos center, Block floor) {
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				set(level, center.offset(x, -1, z), floor);
				if (Math.abs(x) == 6 || Math.abs(z) == 6) {
					set(level, center.offset(x, 0, z), Blocks.GLASS);
					set(level, center.offset(x, 1, z), Blocks.GLASS);
				}
			}
		}
	}

	private static <T extends RobotEntity> T spawn(ServerLevel level, EntityType<T> type, BlockPos pos, int energy) {
		T robot = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		robot.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		robot.setHome(pos);
		robot.setEnergy(energy);
		level.addFreshEntity(robot);
		return robot;
	}

	private static Block voltworksBlock(String name) {
		return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("voltworks", name)).orElseThrow().value();
	}

	/** A sign with one line of text: standing when {@code facing} is null, otherwise hanging on the block behind it. */
	private static void sign(ServerLevel level, BlockPos pos, @org.jspecify.annotations.Nullable Direction facing, String line) {
		level.setBlockAndUpdate(pos, facing == null ? Blocks.OAK_SIGN.defaultBlockState()
				: Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING, facing));
		net.minecraft.world.level.block.entity.SignBlockEntity sign = (net.minecraft.world.level.block.entity.SignBlockEntity) level.getBlockEntity(pos);
		List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
		for (int i = 0; i < 4; i++) lines.add(net.minecraft.network.chat.Component.literal(i == 1 ? line : ""));
		sign.setText(new net.minecraft.world.level.block.entity.SignText(lines, lines, net.minecraft.world.item.DyeColor.BLACK, false),
				net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
	}

	/** A small oak: five logs with a crown of leaves that count as grown (not placed by a player). */
	private static void oak(ServerLevel level, BlockPos base) {
		var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1);
		for (int y = 3; y <= 5; y++) {
			int r = y == 5 ? 0 : 1;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					level.setBlockAndUpdate(base.offset(x, y, z), leaves);
				}
			}
		}
		for (int y = 0; y < 5; y++) {
			set(level, base.above(y), Blocks.OAK_LOG);
		}
	}

	private static Container chest(ServerLevel level, BlockPos pos) {

		set(level, pos, Blocks.CHEST);
		return (Container) level.getBlockEntity(pos);
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos o = origin(server);
		command(server, "time set noon");
		command(server, "weather clear");
		command(server, "gamerule spawn_mobs false");
		command(server, "gamemode creative @a");
		server.getPlayerList().getPlayers().forEach(p -> {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		});
		command(server, String.format("tp @a %d %d %d", o.getX(), o.getY() + 3, o.getZ() - 4));

		// Farm: a 3x3 field of ripe wheat on farmland, water in the middle, a chest by the robot's home
		BlockPos farm = o.offset(FARM_X, 0, 0);
		platform(level, farm, Blocks.STONE);
		for (int x = -1; x <= 1; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 0 && z == 2) {
					set(level, farm.offset(x, -1, z), Blocks.WATER);
					continue;
				}
				level.setBlockAndUpdate(farm.offset(x, -1, z), Blocks.FARMLAND.defaultBlockState()
						.setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
				level.setBlockAndUpdate(farm.offset(x, 0, z), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
			}
		}
		chest(level, farm.offset(-4, 0, -2));
		spawn(level, RobotEntities.FARM_ROBOT, farm.offset(0, 0, -2), 2000);
		// Other things it harvests: a pumpkin on its stem (a loose pumpkin must stay) and a cactus above its bottom block
		level.setBlockAndUpdate(farm.offset(3, -1, 1), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
		set(level, farm.offset(4, 0, 1), Blocks.PUMPKIN);
		level.setBlockAndUpdate(farm.offset(3, 0, 1), Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState()
				.setValue(net.minecraft.world.level.block.AttachedStemBlock.FACING, Direction.EAST));
		set(level, farm.offset(4, 0, 3), Blocks.PUMPKIN);
		set(level, farm.offset(-3, -2, 2), Blocks.STONE); // or the sand falls off the sky platform
		set(level, farm.offset(-3, -1, 2), Blocks.SAND);
		set(level, farm.offset(-3, 0, 2), Blocks.CACTUS);
		set(level, farm.offset(-3, 1, 2), Blocks.CACTUS);
		// An unpowered farm robot next to it must do nothing
		spawn(level, RobotEntities.FARM_ROBOT, farm.offset(4, 0, -4), 0);
		set(level, farm.offset(5, 0, -2), RobotBlocks.ROBOT_TERMINAL);

		// Breeder: four cows, wheat in a chest, a limit that allows breeding
		BlockPos breed = o.offset(BREED_X, 0, 0);
		platform(level, breed, Blocks.GRASS_BLOCK);
		for (int i = 0; i < 4; i++) {
			command(server, String.format(java.util.Locale.ROOT, "summon minecraft:cow %.1f %d %d", breed.getX() - 2 + i * 1.5, breed.getY(), breed.getZ() + 3));
		}
		chest(level, breed.offset(-4, 0, -2)).setItem(0, new ItemStack(Items.WHEAT, 32));
		for (int i = 0; i < 2; i++) {
			command(server, String.format(java.util.Locale.ROOT, "summon minecraft:sheep %.1f %d %d", breed.getX() - 1 + i * 1.5, breed.getY(), breed.getZ() + 1));
		}
		spawn(level, RobotEntities.BREEDER_ROBOT, breed.offset(0, 0, -2), 2000).setSetting("breed_sheep", 0); // sheep eat wheat too
		set(level, breed.offset(5, 0, -2), RobotBlocks.ROBOT_TERMINAL);

		// Organizer: input chest by the robot (mixed items), a cobblestone chest, an empty chest and a "junk" chest
		BlockPos sort = o.offset(SORT_X, 0, 0);
		platform(level, sort, Blocks.STONE);
		Container input = chest(level, sort.offset(0, 0, -3));
		input.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		input.setItem(1, new ItemStack(Items.IRON_INGOT, 20));
		input.setItem(2, new ItemStack(Items.COBBLESTONE, 10));
		chest(level, sort.offset(-4, 0, 3)).setItem(0, new ItemStack(Items.COBBLESTONE, 1));
		chest(level, sort.offset(4, 0, 3));
		chest(level, sort.offset(0, 0, 4)).setItem(0, new ItemStack(Items.DIRT, 1));
		spawn(level, RobotEntities.ORGANIZER_ROBOT, sort.offset(0, 0, -2), 2000);

		// Labelled sorting: signs and an item frame say what each chest is for; the input chest is the one marked "input"
		BlockPos label = o.offset(LABEL_X, 0, LABEL_Z);
		platform(level, label, Blocks.STONE);
		Container labelInput = chest(level, label.offset(4, 0, -4));
		sign(level, label.offset(4, 1, -4), null, "Input");
		labelInput.setItem(0, new ItemStack(Items.IRON_ORE, 10));
		labelInput.setItem(1, new ItemStack(Items.OAK_LOG, 12));
		labelInput.setItem(2, new ItemStack(Items.COBBLESTONE, 20));
		labelInput.setItem(3, new ItemStack(Items.BREAD, 3));
		labelInput.setItem(4, new ItemStack(Items.DEEPSLATE_GOLD_ORE, 5));
		labelInput.setItem(5, new ItemStack(Items.BIRCH_PLANKS, 7));
		labelInput.setItem(6, new ItemStack(Items.RAW_IRON, 4));
		chest(level, label.offset(-4, 0, 2));
		sign(level, label.offset(-4, 1, 2), null, "Ores");
		chest(level, label.offset(-1, 0, 4));
		sign(level, label.offset(-1, 0, 3), Direction.NORTH, "wood");
		chest(level, label.offset(2, 0, 4));
		net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level, label.offset(2, 0, 3), Direction.NORTH);
		frame.setItem(new ItemStack(Items.COBBLESTONE), false);
		level.addFreshEntity(frame);
		chest(level, label.offset(4, 0, 2));
		sign(level, label.offset(4, 1, 2), null, "misc");
		chest(level, label.offset(-4, 0, -2)); // unlabelled and empty: must stay empty because the misc chest comes first
		spawn(level, RobotEntities.ORGANIZER_ROBOT, label.offset(0, 0, -2), 2000);

		// Lumberjack: two real oaks, one inside the marked area and one outside; the axe is in the chest
		BlockPos lumber = o.offset(LUMBER_X, 0, LUMBER_Z);
		platform(level, lumber, Blocks.GRASS_BLOCK);
		oak(level, lumber.offset(3, 0, 2));
		oak(level, lumber.offset(-2, 0, 0));
		set(level, lumber.offset(1, 0, -2), RobotBlocks.FIELD_MARKER);
		set(level, lumber.offset(5, 0, 5), RobotBlocks.FIELD_MARKER);
		chest(level, lumber.offset(2, 0, -5)).setItem(0, new ItemStack(Items.IRON_AXE));
		spawn(level, RobotEntities.LUMBERJACK_ROBOT, lumber.offset(0, 0, -3), 3000).getInventory().setItem(0, new ItemStack(Items.OAK_SAPLING));
		// Stuck: a robot walled into a glass cell away from its home has to end up back home
		for (int x = -5; x <= -3; x++) {
			for (int z = 3; z <= 5; z++) {
				if (x != -4 || z != 4) {
					set(level, lumber.offset(x, 0, z), Blocks.GLASS);
					set(level, lumber.offset(x, 1, z), Blocks.GLASS);
				}
			}
		}
		spawn(level, RobotEntities.ORGANIZER_ROBOT, lumber.offset(-4, 0, 4), 2000).setHome(lumber.offset(-4, 0, -4));

		// Line-up for screenshots: one of each, unpowered so they stand still; three of them carry something
		BlockPos show = o.offset(SHOW_X, 0, 0);
		platform(level, show, Blocks.SMOOTH_STONE);
		spawn(level, RobotEntities.FARM_ROBOT, show.offset(-3, 0, 0), 0).getInventory().setItem(0, new ItemStack(Items.WHEAT));
		spawn(level, RobotEntities.BREEDER_ROBOT, show.offset(-1, 0, 0), 0);
		spawn(level, RobotEntities.LUMBERJACK_ROBOT, show.offset(1, 0, 0), 0).getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
		spawn(level, RobotEntities.ORGANIZER_ROBOT, show.offset(3, 0, 0), 0).getInventory().setItem(0, new ItemStack(Items.CHEST));
		set(level, show.offset(5, 0, 0), RobotBlocks.ROBOT_TERMINAL);
		set(level, show.offset(-5, 0, 0), RobotBlocks.FIELD_MARKER);

		// Marked field: grass with two Field Markers at opposite corners of a 4x3 area; the robot has a hoe and seeds
		BlockPos field = o.offset(FIELD_X, 0, 0);
		platform(level, field, Blocks.GRASS_BLOCK);
		set(level, field.offset(0, 0, 2), Blocks.SHORT_GRASS);
		set(level, field.offset(1, -1, 4), Blocks.WATER);
		set(level, field.offset(-1, 0, 1), RobotBlocks.FIELD_MARKER);
		set(level, field.offset(2, 0, 3), RobotBlocks.FIELD_MARKER);
		chest(level, field.offset(-4, 0, -2));
		FarmRobot tiller = spawn(level, RobotEntities.FARM_ROBOT, field.offset(-1, 0, -1), 5000);
		// Its hoe and seeds are in the chest: it has to fetch them itself
		Container supplies = (Container) level.getBlockEntity(field.offset(-4, 0, -2));
		supplies.setItem(0, new ItemStack(Items.IRON_HOE));
		supplies.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 16));

		// Charging: a pad fed by a solar panel through a cable, and a flat robot that has to walk to it
		set(level, field.offset(3, 0, -4), RobotBlocks.CHARGING_PAD);
		set(level, field.offset(4, 0, -4), voltworksBlock("copper_cable"));
		set(level, field.offset(5, 0, -4), voltworksBlock("solar_panel"));
		((ChargingPadBlockEntity) level.getBlockEntity(field.offset(3, 0, -4))).receiveEnergy(3000, false);
		spawn(level, RobotEntities.ORGANIZER_ROBOT, field.offset(2, 0, -1), 0);
	}

	private static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) n += container.getItem(i).getCount();
		}
		return n;
	}

	private static <T extends RobotEntity> T robot(MinecraftServer server, Class<T> type, int dx) {
		BlockPos center = origin(server).offset(dx, 0, 0);
		List<T> robots = server.overworld().getEntitiesOfClass(type, new AABB(center).inflate(7, 4, 7), r -> r.getEnergy() > 0 || dx != FARM_X);
		if (robots.isEmpty()) throw new AssertionError("No " + type.getSimpleName() + " on its platform");
		return robots.get(0);
	}

	private static List<String> check(MinecraftServer server) {
		List<String> fails = new ArrayList<>();
		ServerLevel level = server.overworld();
		BlockPos o = origin(server);

		// Farm
		BlockPos farm = o.offset(FARM_X, 0, 0);
		FarmRobot farmer = robot(server, FarmRobot.class, FARM_X);
		Container farmChest = (Container) level.getBlockEntity(farm.offset(-4, 0, -2));
		int wheat = count(farmChest, Items.WHEAT) + count(farmer.getInventory(), Items.WHEAT);
		int ripe = 0, planted = 0;
		for (int x = -1; x <= 1; x++) {
			for (int z = 1; z <= 3; z++) {
				var state = level.getBlockState(farm.offset(x, 0, z));
				if (state.getBlock() instanceof CropBlock crop) {
					planted++;
					if (crop.isMaxAge(state)) ripe++;
				}
			}
		}
		System.out.println("[robots-test] farm: wheat " + wheat + " (chest " + count(farmChest, Items.WHEAT) + "), ripe left " + ripe
				+ ", planted " + planted + ", energy " + farmer.getEnergy() + ", at " + farmer.blockPosition());
		if (wheat < 8) fails.add("Farm: only " + wheat + " wheat harvested");
		if (count(farmChest, Items.WHEAT) < 1) fails.add("Farm: nothing was carried to the chest");
		int pumpkins = count(farmChest, Items.PUMPKIN) + count(farmer.getInventory(), Items.PUMPKIN);
		int cactus = count(farmChest, Items.CACTUS) + count(farmer.getInventory(), Items.CACTUS);
		System.out.println("[robots-test] farm extras: pumpkins " + pumpkins + ", cactus " + cactus + ", cactus blocks " + level.getBlockState(farm.offset(-3, 0, 2)).getBlock()
				+ " / " + level.getBlockState(farm.offset(-3, 1, 2)).getBlock() + ", status " + farmer.getStatus().getString());
		if (pumpkins != 1 || !level.getBlockState(farm.offset(4, 0, 1)).isAir()) fails.add("Farm: the pumpkin on its stem should be harvested (got " + pumpkins + ")");
		if (!level.getBlockState(farm.offset(4, 0, 3)).is(Blocks.PUMPKIN)) fails.add("Farm: a pumpkin without a stem must be left alone");
		if (cactus != 1 || !level.getBlockState(farm.offset(-3, 0, 2)).is(Blocks.CACTUS) || level.getBlockState(farm.offset(-3, 1, 2)).is(Blocks.CACTUS)) {
			fails.add("Farm: the cactus should be cut above its bottom block (got " + cactus + ")");
		}
		if (planted != 8) fails.add(
"Farm: " + planted + " of 8 crops still planted (harvests must replant)");
		if (farmer.getEnergy() >= 2000) fails.add("Farm: harvesting used no energy");
		List<FarmRobot> unpowered = level.getEntitiesOfClass(FarmRobot.class, new AABB(farm).inflate(7, 4, 7), r -> r.getEnergy() == 0);
		if (unpowered.size() != 1) {
			fails.add("Farm: expected 1 unpowered robot, found " + unpowered.size());
		} else if (unpowered.get(0).blockPosition().distManhattan(farm.offset(4, 0, -4)) > 1 || !unpowered.get(0).getInventory().isEmpty()) {
			fails.add("Farm: the unpowered robot moved or worked");
		}

		// Marked field: all 12 blocks tilled and planted, nothing outside it touched, markers taken
		BlockPos field = o.offset(FIELD_X, 0, 0);
		FarmRobot tiller = robot(server, FarmRobot.class, FIELD_X);
		Container fieldChest = (Container) level.getBlockEntity(field.offset(-4, 0, -2));
		int tilled = 0, sown = 0, outside = 0;
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				boolean farmland = level.getBlockState(field.offset(x, -1, z)).is(Blocks.FARMLAND);
				if (x >= -1 && x <= 2 && z >= 1 && z <= 3) {
					if (farmland) tilled++;
					if (level.getBlockState(field.offset(x, 0, z)).is(Blocks.WHEAT)) sown++;
				} else if (farmland) {
					outside++;
				}
			}
		}
		ItemStack hoe = tiller.getInventory().getItems().stream().filter(s -> s.is(Items.IRON_HOE)).findFirst().orElse(ItemStack.EMPTY);
		int markers = count(fieldChest, RobotBlocks.FIELD_MARKER.asItem()) + count(tiller.getInventory(), RobotBlocks.FIELD_MARKER.asItem());
		int[] marked = tiller.getField();
		System.out.println("[robots-test] field: tilled " + tilled + ", sown " + sown + ", outside " + outside + ", hoe damage " + hoe.getDamageValue()
				+ ", seeds left " + count(tiller.getInventory(), Items.WHEAT_SEEDS) + ", markers " + markers + " (chest " + count(fieldChest, RobotBlocks.FIELD_MARKER.asItem())
				+ "), field " + java.util.Arrays.toString(marked) + ", energy " + tiller.getEnergy());
		if (marked == null || marked[0] != field.getX() - 1 || marked[1] != field.getZ() + 1 || marked[2] != field.getX() + 2 || marked[3] != field.getZ() + 3) {
			fails.add("Field: the markers did not set the 4x3 field");
		}
		if (level.getBlockState(field.offset(-1, 0, 1)).is(RobotBlocks.FIELD_MARKER) || level.getBlockState(field.offset(2, 0, 3)).is(RobotBlocks.FIELD_MARKER)) fails.add("Field: the markers were not picked up");
		if (markers != 2) fails.add("Field: expected the 2 markers back, found " + markers);
		if (tilled != 12) fails.add("Field: " + tilled + " of 12 blocks tilled");
		if (sown != 12) fails.add("Field: " + sown + " of 12 blocks planted");
		if (outside != 0) fails.add("Field: " + outside + " blocks outside the markers were tilled");
		if (!hoe.is(Items.IRON_HOE) || hoe.getDamageValue() != 12) fails.add("Field: the robot should have fetched the hoe from the chest and used it 12 times");
		if (count(fieldChest, Items.WHEAT_SEEDS) != 0) fails.add("Field: the robot unloaded seeds it should keep");

		// Charging pad: the flat robot walked onto it and took the preloaded 3000 J plus what the solar panel sent since
		OrganizerRobot flat = robot(server, OrganizerRobot.class, FIELD_X);
		BlockPos padPos = field.offset(3, 0, -4);
		ChargingPadBlockEntity pad = (ChargingPadBlockEntity) level.getBlockEntity(padPos);
		System.out.println("[robots-test] charging: robot energy " + flat.getEnergy() + ", pad " + pad.getEnergy() + ", robot at " + flat.blockPosition()
				+ ", charging " + flat.isCharging() + ", status " + flat.getStatus().getString());
		if (flat.getEnergy() <= 3000) fails.add("Charging: the robot has " + flat.getEnergy() + " J, expected the pad's 3000 J plus solar power through the cable");
		if (flat.isCharging()) fails.add("Charging: the robot should stop waiting on a pad that only trickles");
		if (pad.receiveEnergy(1_000_000, true) > 4000) fails.add("Charging: the pad accepts more than its capacity");

		// Lumberjack
		BlockPos lumber = o.offset(LUMBER_X, 0, LUMBER_Z);
		dev.voltworks.robots.entity.LumberjackRobot jack = level.getEntitiesOfClass(dev.voltworks.robots.entity.LumberjackRobot.class, new AABB(lumber).inflate(7, 8, 7)).get(0);
		Container lumberChest = (Container) level.getBlockEntity(lumber.offset(2, 0, -5));
		int logsCut = count(lumberChest, Items.OAK_LOG) + count(jack.getInventory(), Items.OAK_LOG);
		int logsLeftInside = 0, logsLeftOutside = 0;
		for (BlockPos pos : BlockPos.betweenClosed(lumber.offset(-5, 0, -5), lumber.offset(5, 9, 5))) {
			if (level.getBlockState(pos).is(Blocks.OAK_LOG)) {
				if (pos.getX() >= lumber.getX() + 1) logsLeftInside++; else logsLeftOutside++;
			}
		}
		ItemStack axe = jack.getInventory().getItems().stream().filter(s -> s.is(Items.IRON_AXE)).findFirst().orElse(ItemStack.EMPTY);
		boolean replanted = level.getBlockState(lumber.offset(3, 0, 2)).is(Blocks.OAK_SAPLING);
		System.out.println("[robots-test] lumberjack: logs cut " + logsCut + " (chest " + count(lumberChest, Items.OAK_LOG) + "), left inside " + logsLeftInside
				+ ", left outside " + logsLeftOutside + ", axe damage " + axe.getDamageValue() + ", replanted " + replanted + ", area "
				+ java.util.Arrays.toString(jack.getField()) + ", status " + jack.getStatus().getString());
		if (jack.getField() == null) fails.add("Lumberjack: the markers did not set its area");
		if (axe.isEmpty()) fails.add("Lumberjack: it should have fetched the axe from the chest");
		if (logsCut < 4 || logsLeftInside != 0) fails.add("Lumberjack: the tree inside its area should be felled (cut " + logsCut + ", left " + logsLeftInside + ")");
		if (logsLeftOutside < 4) fails.add("Lumberjack: the tree outside its area must be left standing");
		if (!axe.isEmpty() && axe.getDamageValue() != logsCut) fails.add("Lumberjack: the axe should wear once per log");
		if (!replanted) fails.add("Lumberjack: it should replant a sapling where the tree stood");
		if (count(lumberChest, Items.OAK_LOG) < 1) fails.add("Lumberjack: the logs should be carried to the chest");

		// Stuck robot: it started walled in at (-4, 4) and its home is (-4, -4)
		OrganizerRobot walledIn = level.getEntitiesOfClass(OrganizerRobot.class, new AABB(lumber).inflate(7, 4, 7)).get(0);
		System.out.println("[robots-test] stuck: robot at " + walledIn.blockPosition() + ", home " + walledIn.getHome());
		if (walledIn.blockPosition().distManhattan(walledIn.getHome()) > 3) fails.add("Stuck: the walled-in robot should have been put back on its home spot");

		// Breeder
		BlockPos breed = o.offset(BREED_X, 0, 0);
		BreederRobot breeder = robot(server, BreederRobot.class, BREED_X);
		List<Animal> animals = level.getEntitiesOfClass(Animal.class, new AABB(breed).inflate(7, 4, 7));
		List<Animal> cows = animals.stream().filter(a -> BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).getPath().equals("cow")).toList();
		long babies = cows.stream().filter(Animal::isBaby).count();
		long lambs = animals.stream().filter(a -> a.isBaby() && !cows.contains(a)).count();
		if (lambs != 0) fails.add("Breeder: sheep are switched off but " + lambs + " lambs were born");
		Container breedChest = (Container) level.getBlockEntity(breed.offset(-4, 0, -2));
		System.out.println("[robots-test] breeder: cows " + cows.size() + " (babies " + babies + "), wheat in chest " + count(breedChest, Items.WHEAT)
				+ ", carried " + count(breeder.getInventory(), Items.WHEAT) + ", energy " + breeder.getEnergy());
		if (babies < 1) fails.add("Breeder: no calves were born");
		if (count(breedChest, Items.WHEAT) >= 32) fails.add("Breeder: took no food from the chest");
		if (breeder.getEnergy() >= 2000) fails.add("Breeder: breeding used no energy");

		// Organizer
		BlockPos sort = o.offset(SORT_X, 0, 0);
		OrganizerRobot organizer = robot(server, OrganizerRobot.class, SORT_X);
		Container input = (Container) level.getBlockEntity(sort.offset(0, 0, -3));
		Container cobble = (Container) level.getBlockEntity(sort.offset(-4, 0, 3));
		Container empty = (Container) level.getBlockEntity(sort.offset(4, 0, 3));
		Container junk = (Container) level.getBlockEntity(sort.offset(0, 0, 4));
		System.out.println("[robots-test] organizer: cobble chest " + count(cobble, Items.COBBLESTONE) + ", iron in empty chest " + count(empty, Items.IRON_INGOT)
				+ ", input left " + (count(input, Items.COBBLESTONE) + count(input, Items.IRON_INGOT)) + ", energy " + organizer.getEnergy());
		if (count(cobble, Items.COBBLESTONE) != 75) fails.add("Organizer: cobblestone chest has " + count(cobble, Items.COBBLESTONE) + ", expected 75");
		if (count(empty, Items.IRON_INGOT) != 20) fails.add("Organizer: the empty chest has " + count(empty, Items.IRON_INGOT) + " iron, expected 20");
		if (count(junk, Items.DIRT) != 1 || count(junk, Items.COBBLESTONE) + count(junk, Items.IRON_INGOT) != 0) fails.add("Organizer: the dirt chest was touched");
		if (!input.isEmpty()) fails.add("Organizer: the input chest is not empty");

		// Labelled sorting
		BlockPos label = o.offset(LABEL_X, 0, LABEL_Z);
		Container labelInput = (Container) level.getBlockEntity(label.offset(4, 0, -4));
		Container ores = (Container) level.getBlockEntity(label.offset(-4, 0, 2));
		Container wood = (Container) level.getBlockEntity(label.offset(-1, 0, 4));
		Container framed = (Container) level.getBlockEntity(label.offset(2, 0, 4));
		Container misc = (Container) level.getBlockEntity(label.offset(4, 0, 2));
		Container spare = (Container) level.getBlockEntity(label.offset(-4, 0, -2));
		OrganizerRobot sorter = level.getEntitiesOfClass(OrganizerRobot.class, new AABB(label).inflate(7, 4, 7)).get(0);
		System.out.println("[robots-test] labels: ores " + count(ores, Items.IRON_ORE) + "+" + count(ores, Items.DEEPSLATE_GOLD_ORE) + ", wood " + count(wood, Items.OAK_LOG)
				+ "+" + count(wood, Items.BIRCH_PLANKS) + ", framed cobble " + count(framed, Items.COBBLESTONE) + ", misc " + count(misc, Items.BREAD) + "+"
				+ count(misc, Items.RAW_IRON) + ", input empty " + labelInput.isEmpty() + ", spare empty " + spare.isEmpty() + ", status " + sorter.getStatus().getString());
		if (count(ores, Items.IRON_ORE) != 10 || count(ores, Items.DEEPSLATE_GOLD_ORE) != 5) fails.add("Labels: the \"Ores\" chest should hold 10 iron ore and 5 gold ore");
		if (count(wood, Items.OAK_LOG) != 12 || count(wood, Items.BIRCH_PLANKS) != 7) fails.add("Labels: the \"wood\" chest should hold 12 logs and 7 planks");
		if (count(framed, Items.COBBLESTONE) != 20) fails.add("Labels: the chest with the cobblestone item frame should hold 20 cobblestone");
		if (count(misc, Items.BREAD) != 3 || count(misc, Items.RAW_IRON) != 4) fails.add("Labels: the \"misc\" chest should hold the bread and raw iron");
		if (!labelInput.isEmpty()) fails.add("Labels: the input chest is not empty");
		if (!spare.isEmpty()) fails.add("Labels: the unlabelled spare chest should stay empty while a misc chest has room");

		return fails;
	}

	/** The Robot Terminal's actions as its screen sends them, then a real right-click that opens the screen. */
	private static List<String> checkTerminal(MinecraftServer server) {
		List<String> fails = new ArrayList<>();
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		BlockPos farm = origin(server).offset(FARM_X, 0, 0);
		BlockPos terminal = farm.offset(5, 0, -2);
		command(server, String.format("tp @a %d %d %d 0 30", terminal.getX(), terminal.getY(), terminal.getZ() - 2));
		FarmRobot farmer = robot(server, FarmRobot.class, FARM_X);
		FarmRobot unpowered = level.getEntitiesOfClass(FarmRobot.class, new AABB(farm).inflate(7, 4, 7), r -> r.getEnergy() == 0).get(0);

		int listed = RobotTerminalBlock.view(level, terminal, true, player).robots().size();
		if (listed != 2) fails.add("Terminal: should list the 2 robots on its platform, lists " + listed);
		boolean before = unpowered.isRunning();
		RobotTerminalBlock.handle(player, new TerminalActionPayload(terminal, unpowered.getId(), TerminalActionPayload.TOGGLE, "", 0));
		if (unpowered.isRunning() == before) fails.add("Terminal: Pause did not pause the selected robot");
		RobotTerminalBlock.handle(player, new TerminalActionPayload(terminal, -1, TerminalActionPayload.RESUME_ALL, "", 0));
		if (!unpowered.isRunning() || !farmer.isRunning()) fails.add("Terminal: Resume all should leave every robot running");
		RobotTerminalBlock.handle(player, new TerminalActionPayload(terminal, farmer.getId(), TerminalActionPayload.SET, "farm_radius", 3));
		if (farmer.setting("farm_radius") != 3) fails.add("Terminal: the work radius should be 3, is " + farmer.setting("farm_radius"));
		if (unpowered.setting("farm_radius") != 8) fails.add("Terminal: a setting must only change on the selected robot");
		RobotTerminalBlock.handle(player, new TerminalActionPayload(terminal, farmer.getId(), TerminalActionPayload.SET, "farm_radius", 999));
		if (farmer.setting("farm_radius") != 8) fails.add("Terminal: the work radius must not go above the config's 8, is " + farmer.setting("farm_radius"));
		RobotTerminalBlock.handle(player, new TerminalActionPayload(terminal.above(3), farmer.getId(), TerminalActionPayload.SET, "farm_radius", 1));
		if (farmer.setting("farm_radius") != 8) fails.add("Terminal: an action for a block that is not a terminal must be ignored");
		RobotTerminalBlock.handle(player, new TerminalActionPayload(terminal, farmer.getId(), TerminalActionPayload.SET, "recharge_percent", 35));
		System.out.println("[robots-test] terminal: listed " + listed + ", radius " + farmer.setting("farm_radius") + ", recharge " + farmer.setting("recharge_percent"));
		if (farmer.setting("recharge_percent") != 35) fails.add("Terminal: the recharge threshold should be 35");

		// Ownership: someone else's robot is out of bounds for a survival player who is not an operator
		farmer.setOwner(java.util.UUID.randomUUID(), "Alex");
		command(server, "gamemode survival @a");
		boolean operator = player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
		boolean allowed = farmer.mayControl(player);
		boolean locked = RobotTerminalBlock.view(level, terminal, true, player).robots().stream().anyMatch(TerminalViewPayload.Robot::locked);
		int radiusBefore = farmer.setting("farm_radius");
		RobotTerminalBlock.handle(player, new TerminalActionPayload(terminal, farmer.getId(), TerminalActionPayload.SET, "farm_radius", 2));
		System.out.println("[robots-test] ownership: operator " + operator + ", may control " + allowed + ", listed as locked " + locked
				+ ", radius " + farmer.setting("farm_radius"));
		if (allowed != operator) fails.add("Ownership: a survival player may control another's robot only as an operator");
		if (locked == allowed) fails.add("Ownership: the terminal should list another player's robot as locked");
		if (!allowed && farmer.setting("farm_radius") != radiusBefore) fails.add("Ownership: the terminal changed a robot the player does not own");
		farmer.setOwner(player);
		if (!farmer.mayControl(player)) fails.add("Ownership: the owner must be able to control their robot");
		command(server, "gamemode creative @a");
		player.getAbilities().flying = true;
		player.onUpdateAbilities();

		// Open the terminal by the breeder for real: its screen shows the on/off switches
		BlockPos breedTerminal = origin(server).offset(BREED_X + 5, 0, -2);
		command(server, String.format("tp @a %d %d %d 0 30", breedTerminal.getX(), breedTerminal.getY(), breedTerminal.getZ() - 2));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(breedTerminal), Direction.UP, breedTerminal, false));
		return fails;
	}

	/** Power Cell charging, then sneak + punch packs the robot into its item with the energy kept. */
	private static List<String> checkPickUp(MinecraftServer server) {
		List<String> fails = new ArrayList<>();
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		BlockPos farm = origin(server).offset(FARM_X, 0, 0);
		FarmRobot robot = level.getEntitiesOfClass(FarmRobot.class, new AABB(farm).inflate(7, 4, 7), r -> r.getEnergy() == 0).get(0);

		Item powerCell = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("voltworks", "power_cell")).orElseThrow().value();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(powerCell));
		robot.interact(player, InteractionHand.MAIN_HAND, robot.position());
		if (robot.getEnergy() != 1000) fails.add("A Power Cell should add 1000 J (robot has " + robot.getEnergy() + ")");
		robot.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 3));

		// A plain punch only asks for the status
		player.attack(robot);
		if (!robot.isAlive()) fails.add("A plain punch must not pack the robot up");

		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(true);
		player.attack(robot);
		player.setShiftKeyDown(false);
		if (robot.isAlive()) {
			fails.add("Sneak + punch should pack the robot up");
			return fails;
		}
		List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, robot.getBoundingBox().inflate(3));
		ItemStack packed = items.stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).filter(s -> s.is(RobotItems.FARM_ROBOT)).findFirst().orElse(ItemStack.EMPTY);
		boolean seeds = items.stream().anyMatch(e -> e.getItem().is(Items.WHEAT_SEEDS));
		if (packed.isEmpty()) fails.add("Packing up should drop the Farm Robot item");
		if (!seeds) fails.add("Packing up should drop what the robot carried");

		// Stand it back up from the item: the energy comes back
		if (!packed.isEmpty()) {
			BlockPos spot = farm.offset(4, -1, -4);
			player.setItemInHand(InteractionHand.MAIN_HAND, packed.copy());
			player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(spot).add(0, 0.5, 0), Direction.UP, spot, false));
			List<FarmRobot> placed = level.getEntitiesOfClass(FarmRobot.class, new AABB(spot.above()).inflate(1), r -> r.isAlive());
			if (placed.isEmpty()) fails.add("Using the Farm Robot item on a block should stand the robot there");
			else if (!placed.get(0).getOwnerName().equals(player.getGameProfile().name())) fails.add("The player who places a robot should become its owner");
			else if (placed.get(0).getEnergy() != 1000) fails.add("A re-placed robot should keep its energy (has " + placed.get(0).getEnergy() + ")");
		}
		return fails;
	}
}
