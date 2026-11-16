package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Fetches breeding food from a chest, walks to two adult animals of one species and feeds them.
 * It leaves a species alone once breeder.maxAnimalsPerSpecies of them are in range.
 */
public class BreederRobot extends RobotEntity {
	private @Nullable Animal first;
	private @Nullable Animal second;
	private @Nullable BlockPos chestPos;
	private boolean firstFed;
	/** Why the last look for a pair found none. */
	private @Nullable String idleReason;

	public BreederRobot(EntityType<? extends BreederRobot> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item robotItem() {
		return RobotItems.BREEDER_ROBOT;
	}

	@Override
	protected int energyPerOperation() {
		return RobotConfig.breederEnergyPerPair;
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("animal_range", RobotConfig.breederAnimalRange, 1, RobotConfig.breederAnimalRange, 1));
		settings.add(new Setting("max_animals", RobotConfig.breederMaxAnimalsPerSpecies, 2, RobotConfig.breederMaxAnimalsPerSpecies, 1));
		// Which animals to breed: on/off switches
		for (String kind : KINDS) {
			settings.add(new Setting("breed_" + kind, 1, 0, 1, 1));
		}
		settings.add(new Setting("breed_other", 1, 0, 1, 1));
	}

	/** The animals with a switch of their own; everything else falls under "other". */
	private static final List<String> KINDS = List.of("cow", "sheep", "pig", "chicken");

	private boolean breeds(Animal animal) {
		String kind = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
		return setting("breed_" + (KINDS.contains(kind) ? kind : "other")) == 1;
	}

	/** Two Field Markers turn the square around home into a pen: only animals inside it count and get bred. */
	@Override
	protected boolean usesMarkers() {
		return true;
	}

	@Override
	protected int[] workArea() {
		return areaOr(setting("animal_range"));
	}

	@Override
	protected Component statusDetail() {
		if (field == null) {
			return null;
		}
		return Component.translatable("message.robots.breeder.pen", field[2] - field[0] + 1, field[3] - field[1] + 1);
	}

	@Override
	protected void resetTask() {
		first = null;
		second = null;
		chestPos = null;
		firstFed = false;
	}

	@Override
	protected void work(ServerLevel level) {
		if (first == null || second == null) {
			if (workCooldown == 0) {
				workCooldown = RobotConfig.breederWorkInterval;
				choosePair(level);
				if (first == null) {
					report("idle", idleReason);
					idleAtHome();
				} else {
					report(chestPos != null ? "fetching_food" : "feeding", null);
				}
			}
			return;
		}
		if (!first.isAlive() || !second.isAlive() || (!firstFed && !canBreed(first)) || !canBreed(second)) {
			resetTask();
			return;
		}
		if (chestPos != null) {
			fetchFood(level, chestPos);
			return;
		}
		Animal next = firstFed ? second : first;
		if (!walkTo(next, 2.5)) {
			if (gaveUpWalking()) {
				resetTask();
			}
			return;
		}
		int slot = foodSlot(first, second, firstFed ? 1 : 2);
		if (slot < 0) {
			resetTask();
			return;
		}
		inventory.removeItem(slot, 1);
		next.setInLoveTime(600);
		level.broadcastEntityEvent(next, (byte) 18);
		if (!firstFed) {
			firstFed = true;
			return;
		}
		useEnergy(RobotConfig.breederEnergyPerPair);
		resetTask();
		workCooldown = RobotConfig.breederWorkInterval;
	}

	private static boolean canBreed(Animal animal) {
		return animal.getAge() == 0 && animal.canFallInLove() && !animal.isInLove();
	}

	private void choosePair(ServerLevel level) {
		int limit = setting("max_animals");
		int[] area = workArea();
		List<Animal> animals = level.getEntitiesOfClass(Animal.class,
				new AABB(area[0], area[4] - 4, area[1], area[2] + 1, area[4] + 5, area[3] + 1), this::breeds);
		Map<EntityType<?>, Integer> counts = new HashMap<>();
		for (Animal animal : animals) {
			counts.merge(animal.getType(), 1, Integer::sum);
		}
		List<NearbyChests.Chest> chests = null;
		idleReason = "no_animals";
		for (int i = 0; i < animals.size(); i++) {
			Animal a = animals.get(i);
			if (!canBreed(a)) {
				continue;
			}
			if (counts.get(a.getType()) >= limit) {
				if (!"no_food".equals(idleReason)) {
					idleReason = "animal_limit";
				}
				continue;
			}
			for (int j = i + 1; j < animals.size(); j++) {
				Animal b = animals.get(j);
				if (b.getType() != a.getType() || !canBreed(b)) {
					continue;
				}
				if (foodSlot(a, b, 2) >= 0) {
					first = a;
					second = b;
					return;
				}
				if (chests == null) {
					chests = NearbyChests.find(level, getHome(), RobotConfig.chestRange, RobotConfig.chestVerticalRange);
				}
				for (NearbyChests.Chest chest : chests) {
					if (!isSkipped(chest.pos()) && foodSlot(chest.container(), a, b, 2) >= 0) {
						first = a;
						second = b;
						chestPos = chest.pos();
						return;
					}
				}
				idleReason = "no_food";
				break; // no food for this species anywhere: try the next species
			}
		}
	}

	private int foodSlot(Animal a, Animal b, int needed) {
		return foodSlot(inventory, a, b, needed);
	}

	private static int foodSlot(Container container, Animal a, Animal b, int needed) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.getCount() >= needed && a.isFood(stack) && b.isFood(stack)) {
				return slot;
			}
		}
		return -1;
	}

	private void fetchFood(ServerLevel level, BlockPos pos) {
		Container chest = NearbyChests.at(level, pos);
		if (chest == null) {
			resetTask();
			return;
		}
		if (!walkTo(pos, 3.0)) {
			if (gaveUpWalking()) {
				skip(pos);
				resetTask();
			}
			return;
		}
		report("feeding", null);
		chestPos = null;
		int slot = foodSlot(chest, first, second, 2);
		if (slot < 0) {
			resetTask();
			return;
		}
		ItemStack taken = chest.removeItem(slot, RobotConfig.breederFoodPerTrip);
		ItemStack rest = inventory.addItem(taken);
		if (!rest.isEmpty()) {
			rest = NearbyChests.insert(chest, rest);
			if (!rest.isEmpty()) {
				spawnAtLocation(level, rest);
			}
		}
		chest.setChanged();
		if (foodSlot(first, second, 2) < 0) {
			resetTask(); // its own inventory is too full to carry the food
		}
	}
}
