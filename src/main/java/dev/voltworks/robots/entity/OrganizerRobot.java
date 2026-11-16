package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Sorts the input chest into the other chests around its home spot. Signs and item frames on a chest say what
 * belongs in it (see {@link ChestLabel}); a chest without any takes more of what it already holds, and an empty
 * one is claimed for a new kind of item. The input chest is the one with an "input" sign, or else the unlabelled
 * chest nearest home.
 */
public class OrganizerRobot extends RobotEntity {
	private @Nullable BlockPos pickupPos;
	private @Nullable BlockPos deliverPos;
	private @Nullable BlockPos returnPos;

	private record Destination(NearbyChests.Chest chest, ChestLabel label) {
	}

	private record Layout(NearbyChests.@Nullable Chest input, List<Destination> destinations) {
	}

	public OrganizerRobot(EntityType<? extends OrganizerRobot> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item robotItem() {
		return RobotItems.ORGANIZER_ROBOT;
	}

	@Override
	protected int energyPerOperation() {
		return RobotConfig.organizerEnergyPerTrip;
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("items_per_trip", RobotConfig.organizerMaxItemsPerTrip, 1, RobotConfig.organizerMaxItemsPerTrip, 8));
	}

	@Override
	protected void resetTask() {
		pickupPos = null;
		deliverPos = null;
		returnPos = null;
	}

	@Override
	protected void work(ServerLevel level) {
		if (deliverPos != null) {
			deliver(level, deliverPos);
		} else if (pickupPos != null) {
			pickUp(level, pickupPos);
		} else if (returnPos != null) {
			putBack(level, returnPos);
		} else if (workCooldown == 0) {
			workCooldown = RobotConfig.organizerWorkInterval;
			chooseTask(level);
		}
	}

	/** The chests around home, nearest first, split into the input chest and everything it can be sorted into. */
	private Layout layout(ServerLevel level) {
		List<Destination> all = new ArrayList<>();
		for (NearbyChests.Chest chest : NearbyChests.find(level, getHome(), RobotConfig.chestRange, RobotConfig.chestVerticalRange)) {
			all.add(new Destination(chest, ChestLabel.read(level, chest.pos())));
		}
		NearbyChests.Chest input = null;
		for (Destination each : all) {
			if (each.label().input()) {
				input = each.chest();
				break;
			}
		}
		if (input == null) {
			for (Destination each : all) {
				if (!each.label().restricts()) {
					input = each.chest();
					break;
				}
			}
		}
		NearbyChests.Chest chosen = input;
		all.removeIf(each -> each.chest() == chosen || each.label().input());
		return new Layout(input, all);
	}

	private void chooseTask(ServerLevel level) {
		Layout layout = layout(level);
		NearbyChests.Chest input = layout.input();
		List<Destination> destinations = layout.destinations();
		if (input == null || destinations.isEmpty()) {
			report("idle", "needs_two_chests");
			idleAtHome();
			return;
		}

		// Already carrying something: deliver it, or hand it back to the input chest
		if (!inventory.isEmpty()) {
			for (ItemStack stack : inventory.getItems()) {
				if (!stack.isEmpty()) {
					BlockPos destination = destinationFor(stack, destinations);
					if (destination != null) {
						report("delivering", null);
						deliverPos = destination;
						return;
					}
				}
			}
			report("returning", "no_destination");
			if (!isSkipped(input.pos())) {
				returnPos = input.pos();
			}
			return;
		}

		if (isSkipped(input.pos())) {
			return;
		}
		Container source = input.container();
		for (int slot = 0; slot < source.getContainerSize(); slot++) {
			ItemStack stack = source.getItem(slot);
			if (!stack.isEmpty() && destinationFor(stack, destinations) != null) {
				report("collecting", null);
				pickupPos = input.pos();
				return;
			}
		}
		report("idle", source.isEmpty() ? (hasUnreachable() ? "cant_reach" : null) : "no_destination");
		idleAtHome();
	}

	/**
	 * Where a stack belongs, best first: a chest whose item frame shows it, a chest whose sign names it, an
	 * unlabelled chest that already holds it, a catch-all ("misc") chest, an unlabelled empty chest. Among equals
	 * the one nearest home wins. Null when nothing has room for it.
	 */
	private @Nullable BlockPos destinationFor(ItemStack stack, List<Destination> destinations) {
		BlockPos best = null;
		int bestRank = Integer.MAX_VALUE;
		for (Destination each : destinations) {
			Container chest = each.chest().container();
			ChestLabel label = each.label();
			int rank;
			if (label.restricts()) {
				int match = label.match(stack);
				if (match < 0 && !label.catchAll()) {
					continue;
				}
				rank = match >= 0 ? match : 3;
			} else if (NearbyChests.contains(chest, stack)) {
				rank = 2;
			} else if (chest.isEmpty()) {
				rank = 4;
			} else {
				continue;
			}
			if (rank < bestRank && !isSkipped(each.chest().pos()) && NearbyChests.hasRoomFor(chest, stack)) {
				best = each.chest().pos();
				bestRank = rank;
			}
		}
		return best;
	}

	private void pickUp(ServerLevel level, BlockPos pos) {
		Container source = NearbyChests.at(level, pos);
		if (source == null) {
			pickupPos = null;
			return;
		}
		if (!walkTo(pos, 3.0)) {
			if (gaveUpWalking()) {
				skip(pos);
				pickupPos = null;
			}
			return;
		}
		pickupPos = null;
		List<Destination> destinations = layout(level).destinations().stream().filter(each -> !each.chest().pos().equals(pos)).toList();
		for (int slot = 0; slot < source.getContainerSize(); slot++) {
			ItemStack stack = source.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			BlockPos destination = destinationFor(stack, destinations);
			if (destination == null) {
				continue;
			}
			ItemStack taken = source.removeItem(slot, setting("items_per_trip"));
			ItemStack rest = inventory.addItem(taken);
			if (!rest.isEmpty()) {
				NearbyChests.insert(source, rest);
			}
			source.setChanged();
			report("delivering", null);
			deliverPos = destination;
			return;
		}
	}

	private void deliver(ServerLevel level, BlockPos pos) {
		Container chest = NearbyChests.at(level, pos);
		if (chest == null) {
			deliverPos = null;
			return;
		}
		if (!walkTo(pos, 3.0)) {
			if (gaveUpWalking()) {
				skip(pos);
				deliverPos = null;
			}
			return;
		}
		deliverPos = null;
		workCooldown = RobotConfig.organizerWorkInterval;
		List<Destination> destinations = layout(level).destinations();
		boolean moved = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !pos.equals(destinationFor(stack, destinations))) {
				continue; // this stack belongs somewhere else
			}
			int before = stack.getCount();
			ItemStack rest = NearbyChests.insert(chest, stack.copy());
			if (rest.getCount() != before) {
				inventory.setItem(slot, rest);
				moved = true;
			}
		}
		if (moved) {
			chest.setChanged();
			useEnergy(RobotConfig.organizerEnergyPerTrip);
		}
	}

	private void putBack(ServerLevel level, BlockPos pos) {
		Container chest = NearbyChests.at(level, pos);
		if (chest == null) {
			returnPos = null;
			return;
		}
		if (!walkTo(pos, 3.0)) {
			if (gaveUpWalking()) {
				skip(pos);
				returnPos = null;
			}
			return;
		}
		returnPos = null;
		// Nothing fits back either: wait a while before trying again
		workCooldown = depositAll(chest) ? RobotConfig.organizerWorkInterval : 200;
	}
}
