package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Keeps each kind of animal in its pen at a set number: when there are more (adults and babies counted), it culls
 * adults with its built-in cleaver and collects the meat, leather and wool. It never touches babies, or named, tamed,
 * saddled, armoured, leashed or ridden animals, and always leaves at least two of a kind so the Breeder can carry on.
 */
public class ButcherRobot extends CollectingRobot {
	private static final ItemStack CLEAVER = new ItemStack(Items.IRON_SWORD);
	/** The animals with a switch of their own; everything else falls under "other". */
	private static final List<String> KINDS = List.of("cow", "sheep", "pig", "chicken");
	/** Where an animal wears what a player put on it (not its hands: a fox carries things in its mouth). */
	private static final List<EquipmentSlot> WORN = List.of(EquipmentSlot.SADDLE, EquipmentSlot.BODY, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET);
	private @Nullable Animal target;
	private int hitCooldown;
	/** Why the last look for an animal to cull found none. */
	private @Nullable String idleReason;

	public ButcherRobot(EntityType<? extends ButcherRobot> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item robotItem() {
		return RobotItems.BUTCHER_ROBOT;
	}

	@Override
	protected int energyPerOperation() {
		return RobotConfig.butcherEnergyPerHit;
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("butcher_radius", RobotConfig.butcherRadius, 1, RobotConfig.butcherRadius, 1));
		settings.add(new Setting("animals_kept", RobotConfig.butcherAnimalsKept, 2, 200, 1)); // keeping more is always allowed
		for (String kind : KINDS) {
			settings.add(new Setting("cull_" + kind, 1, 0, 1, 1));
		}
		settings.add(new Setting("cull_other", 0, 0, 1, 1)); // horses, cats, foxes ...: off unless asked for
	}

	@Override
	protected boolean usesMarkers() {
		return true;
	}

	@Override
	protected int[] workArea() {
		return areaOr(setting("butcher_radius"));
	}

	@Override
	protected @Nullable Component statusDetail() {
		return field == null ? null : Component.translatable("message.robots.breeder.pen", field[2] - field[0] + 1, field[3] - field[1] + 1);
	}

	@Override
	protected ItemStack shownItem() {
		return CLEAVER;
	}

	@Override
	protected boolean picksUp(ItemStack stack) {
		return isAnimalProduce(stack) || stack.has(net.minecraft.core.component.DataComponents.FOOD);
	}

	@Override
	protected void resetJob() {
		target = null;
	}

	private boolean culls(Animal animal) {
		String kind = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).getPath();
		return setting("cull_" + (KINDS.contains(kind) ? kind : "other")) == 1;
	}

	/**
	 * Never: babies, named animals, anything tamed or owned (pets, horses, donkeys, llamas), animals wearing a saddle
	 * or armour, and animals on a lead, carrying a rider or sitting in a boat or minecart.
	 */
	private static boolean protectedAnimal(Animal animal) {
		if (animal.isBaby() || animal.hasCustomName() || animal.isLeashed() || animal.isVehicle() || animal.isPassenger()) {
			return true;
		}
		if ((animal instanceof TamableAnimal pet && pet.isTame()) || (animal instanceof AbstractHorse horse && horse.isTamed())
				|| (animal instanceof OwnableEntity owned && owned.getOwnerReference() != null)) {
			return true;
		}
		for (EquipmentSlot slot : WORN) {
			if (!animal.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void work(ServerLevel level) {
		if (hitCooldown > 0) {
			hitCooldown--;
		}
		if (target != null) {
			cull(level, target);
			return;
		}
		if (runErrand(level) || workCooldown > 0) {
			return;
		}
		workCooldown = 20;
		if (unloadIfFull(level)) {
			return;
		}
		if (hasEmptySlot()) {
			target = findSurplusAnimal(level);
			if (target != null) {
				report("culling", null);
				return;
			}
		}
		if (pickUpDrops(level) || unloadIfCarrying(level)) {
			return;
		}
		workCooldown = RobotConfig.butcherWorkInterval;
		idle(idleReason);
	}

	/** The nearest adult of a kind that has more animals in the pen than it should keep. */
	private @Nullable Animal findSurplusAnimal(ServerLevel level) {
		int[] area = workArea();
		List<Animal> animals = level.getEntitiesOfClass(Animal.class,
				new AABB(area[0], area[4] - 4, area[1], area[2] + 1, area[4] + 5, area[3] + 1), Animal::isAlive);
		Map<EntityType<?>, Integer> counts = new HashMap<>();
		for (Animal animal : animals) {
			counts.merge(animal.getType(), 1, Integer::sum);
		}
		int kept = setting("animals_kept");
		Animal nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		idleReason = animals.isEmpty() ? "no_animals_here" : null;
		for (Animal animal : animals) {
			if (counts.get(animal.getType()) <= kept || !culls(animal) || protectedAnimal(animal) || isSkipped(animal)) {
				continue;
			}
			double distance = animal.distanceToSqr(this);
			if (distance < nearestDistance) {
				nearest = animal;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private void cull(ServerLevel level, Animal animal) {
		if (!animal.isAlive() || protectedAnimal(animal)) {
			target = null;
			workCooldown = 20; // let the drops land before picking them up
			return;
		}
		if (!walkTo(animal, 2.2)) {
			if (gaveUpWalking()) {
				skip(animal);
				target = null;
			}
			return;
		}
		if (hitCooldown > 0) {
			return;
		}
		if (!useEnergy(RobotConfig.butcherEnergyPerHit)) {
			target = null;
			return;
		}
		hitCooldown = RobotConfig.butcherHitInterval;
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 1.0F);
		animal.hurtServer(level, damageSources().mobAttack(this), RobotConfig.butcherDamage);
		if (!animal.isAlive()) {
			expectDrops(animal.position());
		}
	}
}
