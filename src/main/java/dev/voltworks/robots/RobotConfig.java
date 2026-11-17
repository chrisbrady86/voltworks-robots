package dev.voltworks.robots;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Robot limits, read once at startup from config/robots.properties. The file is rewritten on every start
 * with the values in use, so new settings show up in it and out-of-range values are visibly corrected.
 */
public final class RobotConfig {
	private static final List<String> LINES = new ArrayList<>();
	private static Properties loaded = new Properties();

	// All robots
	public static int maxEnergy = 10_000;
	public static int energyPerPowerCell = 1_000;
	public static int chestRange = 8;
	public static int chestVerticalRange = 4;
	public static int rechargeBelowPercent = 20;
	public static int ownerOnly = 1;

	// Robot charging pad
	public static int padRange = 16;
	public static int padVerticalRange = 4;
	public static int padChargePerTick = 100;
	public static int padCapacity = 4_000;

	// Farm robot
	public static int farmRadius = 8;
	public static int farmVerticalRange = 2;
	public static int farmWorkInterval = 40;
	public static int farmEnergyPerHarvest = 20;
	public static int farmMaxFieldSize = 17;
	public static int farmFieldWorkInterval = 10;
	public static int farmEnergyPerTill = 10;
	public static int farmEnergyPerPlant = 5;
	public static int farmSeedsKept = 16;

	// Animal breeder robot
	public static int breederAnimalRange = 8;
	public static int breederMaxAnimalsPerSpecies = 12;
	public static int breederWorkInterval = 200;
	public static int breederEnergyPerPair = 20;
	public static int breederFoodPerTrip = 16;

	// Organizer robot
	public static int organizerMaxItemsPerTrip = 64;
	public static int organizerWorkInterval = 20;
	public static int organizerEnergyPerTrip = 10;

	// Lumberjack robot
	public static int lumberRadius = 8;
	public static int lumberVerticalRange = 3;
	public static int lumberWorkInterval = 40;
	public static int lumberChopInterval = 8;
	public static int lumberEnergyPerLog = 10;

	// Rancher robot
	public static int rancherRadius = 8;
	public static int rancherWorkInterval = 40;
	public static int rancherEnergyPerShear = 10;

	// Butcher robot
	public static int butcherRadius = 8;
	public static int butcherAnimalsKept = 6;
	public static int butcherWorkInterval = 100;
	public static int butcherDamage = 10;
	public static int butcherHitInterval = 10;
	public static int butcherEnergyPerHit = 10;

	// Guard robot
	public static int guardRadius = 12;
	public static int guardVerticalRange = 6;
	public static int guardDamage = 8;
	public static int guardHitInterval = 10;
	public static int guardEnergyPerHit = 10;

	// Fisher robot
	public static int fisherRadius = 6;
	public static int fisherVerticalRange = 3;
	public static int fisherMinWait = 200;
	public static int fisherMaxWait = 600;
	public static int fisherEnergyPerCatch = 15;

	// Robot terminal
	public static int terminalRange = 16;
	public static int terminalVerticalRange = 8;

	private RobotConfig() {
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("robots.properties");
		loaded = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded.load(reader);
			} catch (IOException | IllegalArgumentException e) {
				System.err.println("[robots] Could not read " + file + ", using defaults: " + e);
			}
		}
		LINES.clear();
		LINES.add("# Robots configuration. Edit, then restart the game or server.");
		LINES.add("# Distances are in blocks, intervals in ticks (20 ticks = 1 second), energy in J.");
		LINES.add("# Every robot carries 9 item stacks; that size is fixed.");

		section("All robots");
		maxEnergy = value("robot.maxEnergy", maxEnergy, 100, 10_000_000, "Energy a robot can store");
		energyPerPowerCell = value("robot.energyPerPowerCell", energyPerPowerCell, 1, 10_000_000, "Energy one Power Cell adds");
		chestRange = value("robot.chestRange", chestRange, 1, 24, "How far from its home spot a robot looks for chests (sideways)");
		chestVerticalRange = value("robot.chestVerticalRange", chestVerticalRange, 0, 8, "How far up and down a robot looks for chests");
		rechargeBelowPercent = value("robot.rechargeBelowPercent", rechargeBelowPercent, 0, 100,
				"A robot walks to a Robot Charging Pad when its energy drops below this percentage (0 = never)");
		ownerOnly = value("robot.ownerOnly", ownerOnly, 0, 1,
				"1 = only the player who placed a robot (or an operator) can open, pause, change or pick it up; 0 = anyone can");

		section("Robot Charging Pad");
		padRange = value("pad.range", padRange, 1, 32, "How far from its home spot a robot looks for a charging pad (sideways)");
		padVerticalRange = value("pad.verticalRange", padVerticalRange, 0, 8, "How far up and down a robot looks for a charging pad");
		padChargePerTick = value("pad.chargePerTick", padChargePerTick, 1, 100_000, "Energy a pad gives a robot each tick");
		padCapacity = value("pad.capacity", padCapacity, 100, 10_000_000, "Energy a pad can hold");

		section("Farm Robot");
		farmRadius = value("farm.radius", farmRadius, 1, 24, "Without a marked field, the robot works this far from its home spot (sideways)");
		farmVerticalRange = value("farm.verticalRange", farmVerticalRange, 0, 8, "The robot works this far above and below its home spot or marked field");
		farmWorkInterval = value("farm.workIntervalTicks", farmWorkInterval, 5, 12_000, "Pause between harvests");
		farmEnergyPerHarvest = value("farm.energyPerHarvest", farmEnergyPerHarvest, 0, 100_000, "Energy used per harvested crop");
		farmMaxFieldSize = value("farm.maxFieldSize", farmMaxFieldSize, 2, 65, "Longest side of an area marked with Field Markers (fields, pens, tree farms)");
		farmFieldWorkInterval = value("farm.fieldWorkIntervalTicks", farmFieldWorkInterval, 2, 12_000, "Pause after tilling or planting one block");
		farmEnergyPerTill = value("farm.energyPerTill", farmEnergyPerTill, 0, 100_000, "Energy used per tilled block");
		farmEnergyPerPlant = value("farm.energyPerPlant", farmEnergyPerPlant, 0, 100_000, "Energy used per planted seed");
		farmSeedsKept = value("farm.seedsKept", farmSeedsKept, 0, 64, "Seeds of each kind the robot keeps for planting when it unloads into a chest");

		section("Animal Breeder Robot");
		breederAnimalRange = value("breeder.animalRange", breederAnimalRange, 1, 24, "Animals are bred this far from the robot's home spot");
		breederMaxAnimalsPerSpecies = value("breeder.maxAnimalsPerSpecies", breederMaxAnimalsPerSpecies, 2, 200,
				"Stops breeding a species once this many (adults and babies) are in range");
		breederWorkInterval = value("breeder.workIntervalTicks", breederWorkInterval, 20, 72_000, "Pause between breeding pairs");
		breederEnergyPerPair = value("breeder.energyPerPair", breederEnergyPerPair, 0, 100_000, "Energy used per bred pair");
		breederFoodPerTrip = value("breeder.foodPerTrip", breederFoodPerTrip, 2, 64, "Most food items taken from a chest in one trip");

		section("Organizer Robot");
		organizerMaxItemsPerTrip = value("organizer.maxItemsPerTrip", organizerMaxItemsPerTrip, 1, 64, "Most items carried from the input chest in one trip");
		organizerWorkInterval = value("organizer.workIntervalTicks", organizerWorkInterval, 5, 12_000, "Pause between trips");
		organizerEnergyPerTrip = value("organizer.energyPerTrip", organizerEnergyPerTrip, 0, 100_000, "Energy used per delivery");

		section("Lumberjack Robot");
		lumberRadius = value("lumber.radius", lumberRadius, 1, 24, "Without a marked area, trees are felled this far from the robot's home spot (sideways)");
		lumberVerticalRange = value("lumber.verticalRange", lumberVerticalRange, 0, 8, "Trunks are looked for this far above and below the home spot or marked area");
		lumberWorkInterval = value("lumber.workIntervalTicks", lumberWorkInterval, 5, 12_000, "Pause between jobs");
		lumberChopInterval = value("lumber.chopIntervalTicks", lumberChopInterval, 1, 1_200, "Time to cut one log");
		lumberEnergyPerLog = value("lumber.energyPerLog", lumberEnergyPerLog, 0, 100_000, "Energy used per log");

		section("Rancher Robot");
		rancherRadius = value("rancher.radius", rancherRadius, 1, 24, "Without a marked pen, sheep are sheared and drops picked up this far from the robot's home spot");
		rancherWorkInterval = value("rancher.workIntervalTicks", rancherWorkInterval, 5, 12_000, "Pause between jobs");
		rancherEnergyPerShear = value("rancher.energyPerShear", rancherEnergyPerShear, 0, 100_000, "Energy used per sheared sheep");

		section("Butcher Robot");
		butcherRadius = value("butcher.radius", butcherRadius, 1, 24, "Without a marked pen, animals are counted and culled this far from the robot's home spot");
		butcherAnimalsKept = value("butcher.animalsKept", butcherAnimalsKept, 2, 200, "Animals of each kind left alive (adults and babies) for a new robot; players can raise it at a terminal");
		butcherWorkInterval = value("butcher.workIntervalTicks", butcherWorkInterval, 20, 72_000, "Pause between culls");
		butcherDamage = value("butcher.damage", butcherDamage, 1, 1_000, "Damage per hit");
		butcherHitInterval = value("butcher.hitIntervalTicks", butcherHitInterval, 1, 1_200, "Time between hits");
		butcherEnergyPerHit = value("butcher.energyPerHit", butcherEnergyPerHit, 0, 100_000, "Energy used per hit");

		section("Guard Robot");
		guardRadius = value("guard.radius", guardRadius, 1, 32, "Hostile mobs are fought this far from the robot's home spot (sideways)");
		guardVerticalRange = value("guard.verticalRange", guardVerticalRange, 0, 16, "How far up and down the robot looks for hostile mobs");
		guardDamage = value("guard.damage", guardDamage, 1, 1_000, "Damage per hit");
		guardHitInterval = value("guard.hitIntervalTicks", guardHitInterval, 1, 1_200, "Time between hits");
		guardEnergyPerHit = value("guard.energyPerHit", guardEnergyPerHit, 0, 100_000, "Energy used per hit");

		section("Fisher Robot");
		fisherRadius = value("fisher.radius", fisherRadius, 1, 16, "The robot looks for open water this far from its home spot (sideways)");
		fisherVerticalRange = value("fisher.verticalRange", fisherVerticalRange, 0, 8, "How far up and down it looks for water");
		fisherMinWait = value("fisher.minWaitTicks", fisherMinWait, 20, 72_000, "Shortest wait for a bite");
		fisherMaxWait = Math.max(fisherMinWait, value("fisher.maxWaitTicks", fisherMaxWait, 20, 72_000, "Longest wait for a bite"));
		fisherEnergyPerCatch = value("fisher.energyPerCatch", fisherEnergyPerCatch, 0, 100_000, "Energy used per catch");

		section("Robot Terminal");
		terminalRange = value("terminal.range", terminalRange, 1, 64, "How far the terminal reaches (sideways)");
		terminalVerticalRange = value("terminal.verticalRange", terminalVerticalRange, 0, 32, "How far the terminal reaches up and down");

		try {
			Files.createDirectories(file.getParent());
			Files.write(file, LINES, StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[robots] Could not write " + file + ": " + e);
		}
	}

	private static void section(String title) {
		LINES.add("");
		LINES.add("# ---- " + title + " ----");
	}

	private static int value(String key, int fallback, int min, int max, String comment) {
		int result = fallback;
		String raw = loaded.getProperty(key);
		if (raw != null) {
			try {
				result = Integer.parseInt(raw.trim());
			} catch (NumberFormatException e) {
				System.err.println("[robots] " + key + "=" + raw + " is not a whole number, using " + fallback);
			}
		}
		result = Math.max(min, Math.min(max, result));
		LINES.add("# " + comment + " (" + min + " to " + max + ", default " + fallback + ")");
		LINES.add(key + "=" + result);
		return result;
	}
}
