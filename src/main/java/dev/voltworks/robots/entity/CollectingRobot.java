package dev.voltworks.robots.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A robot whose work leaves things lying on the ground (wool, meat, mob drops) or fills its inventory (fish). It picks
 * up what its own work dropped within its work area and carries everything to the nearest chest when it is full or has nothing
 * else to do. Subclasses do their own job and call the errand helpers from {@link #work}.
 */
public abstract class CollectingRobot extends RobotEntity {
	/** Drops from the robot's own work lie this near where it happened, and appear within this many ticks of it. */
	private static final double DROP_RADIUS = 6.0;
	private static final int DROP_TICKS = 3, DROP_MEMORY_TICKS = 6000;
	private @Nullable BlockPos chestPos;
	private @Nullable ItemEntity pickup;
	/** Where and when this robot's work left things on the ground. */
	private final List<DropSpot> dropSpots = new ArrayList<>();

	private record DropSpot(Vec3 pos, long time) {
	}

	protected CollectingRobot(EntityType<? extends CollectingRobot> type, Level level) {
		super(type, level);
	}

	/** Items lying in the work area that this robot picks up. */
	protected abstract boolean picksUp(ItemStack stack);

	/** Forget this robot's own job (the errands are forgotten by {@link #resetTask}). */
	protected abstract void resetJob();

	@Override
	protected final void resetTask() {
		chestPos = null;
		pickup = null;
		resetJob();
	}

	/** Call right after the robot's work made something drop here (it killed an animal or a monster). */
	protected final void expectDrops(Vec3 pos) {
		dropSpots.add(new DropSpot(pos, level().getGameTime()));
	}

	/**
	 * Whether an item on the ground is this robot's to take: only what its own work dropped, which is an item that
	 * appeared at the moment and the place of one of its kills. Things a player threw down or lost on dying, or that
	 * came from anywhere else, stay where they are.
	 */
	protected boolean mayTake(ItemEntity item) {
		if (item.getOwner() != null) {
			return false; // thrown by someone
		}
		long now = level().getGameTime();
		dropSpots.removeIf(spot -> now - spot.time() > DROP_MEMORY_TICKS);
		long appeared = now - item.getAge();
		for (DropSpot spot : dropSpots) {
			if (Math.abs(appeared - spot.time()) <= DROP_TICKS && item.position().distanceToSqr(spot.pos()) <= DROP_RADIUS * DROP_RADIUS) {
				return true;
			}
		}
		return false;
	}

	/** Carries on with a walk to a chest or to an item on the ground. True while one is under way. */
	protected final boolean runErrand(ServerLevel level) {
		if (chestPos != null) {
			unload(level, chestPos);
			return true;
		}
		if (pickup != null) {
			collect(pickup);
			return true;
		}
		return false;
	}

	/** With no free slot left, heads for a chest. True if it set off. */
	protected final boolean unloadIfFull(ServerLevel level) {
		return !hasEmptySlot() && hasSurplus() && startUnload(level);
	}

	/** Heads for a chest if it carries anything to put away. True if it set off. */
	protected final boolean unloadIfCarrying(ServerLevel level) {
		return hasSurplus() && startUnload(level);
	}

	private boolean startUnload(ServerLevel level) {
		chestPos = findUnloadChest(level);
		if (chestPos == null) {
			return false;
		}
		report("unloading", null);
		return true;
	}

	/** Goes for the nearest wanted item lying in the work area. True if it set off. */
	protected final boolean pickUpDrops(ServerLevel level) {
		if (!hasEmptySlot()) {
			return false;
		}
		int[] area = workArea();
		AABB box = new AABB(area[0], area[4] - 4, area[1], area[2] + 1, area[4] + 6, area[3] + 1);
		ItemEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, each -> each.isAlive() && each.onGround() && picksUp(each.getItem()) && mayTake(each))) {
			double distance = item.distanceToSqr(this);
			if (distance < nearestDistance && !isSkipped(item)) {
				nearest = item;
				nearestDistance = distance;
			}
		}
		pickup = nearest;
		if (pickup == null) {
			return false;
		}
		report("collecting_drops", null);
		return true;
	}

	/** Nothing to do: says why (a full inventory with no chest to empty it into, or something out of reach) and goes home. */
	protected final void idle(@Nullable String reason) {
		report("idle", reason != null ? reason : hasSurplus() && !hasEmptySlot() ? "no_chest_room" : hasUnreachable() ? "cant_reach" : null);
		idleAtHome();
	}

	private void collect(ItemEntity item) {
		if (!item.isAlive()) {
			pickup = null;
			return;
		}
		if (!walkTo(item, 1.8)) {
			if (gaveUpWalking()) {
				skip(item);
				pickup = null;
			}
			return;
		}
		pickup = null;
		workCooldown = 5;
		ItemStack rest = inventory.addItem(item.getItem().copy());
		if (rest.isEmpty()) {
			item.discard();
		} else {
			item.setItem(rest);
		}
		playSound(SoundEvents.ITEM_PICKUP, 0.3F, 1.2F);
	}

	private void unload(ServerLevel level, BlockPos pos) {
		Container chest = NearbyChests.at(level, pos);
		if (chest == null) {
			chestPos = null;
			return;
		}
		if (!walkTo(pos, 3.0)) {
			if (gaveUpWalking()) {
				skip(pos);
				chestPos = null;
			}
			return;
		}
		unloadInto(chest);
		chestPos = null;
		workCooldown = 10;
	}

	// ---------------- what animals and monsters leave behind ----------------

	/** Wool, eggs, feathers, leather, hides and scutes: what farm animals shed or lay. */
	protected static boolean isAnimalProduce(ItemStack stack) {
		return stack.is(ItemTags.WOOL) || stack.is(ItemTags.EGGS) || stack.is(Items.FEATHER) || stack.is(Items.LEATHER)
				|| stack.is(Items.RABBIT_HIDE) || stack.is(Items.RABBIT_FOOT) || stack.is(Items.ARMADILLO_SCUTE) || stack.is(Items.TURTLE_SCUTE)
				|| stack.is(Items.INK_SAC);
	}

	/** What hostile mobs drop. */
	protected static boolean isMonsterDrop(ItemStack stack) {
		return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BONE) || stack.is(ItemTags.ARROWS) || stack.is(Items.STRING)
				|| stack.is(Items.SPIDER_EYE) || stack.is(Items.GUNPOWDER) || stack.is(Items.ENDER_PEARL) || stack.is(Items.SLIME_BALL)
				|| stack.is(Items.PHANTOM_MEMBRANE) || stack.is(Items.BLAZE_ROD);
	}
}
