package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotItems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Fells trees with the axe it carries: it walks to a trunk, takes the tree down log by log from the top, replants a
 * sapling where it stood, picks up what the leaves drop and carries everything to a chest. It only touches trunks
 * that grow out of the ground and have natural leaves, so log walls and planted hedges are left alone. It works in a
 * square around home, or in an area marked with two Field Markers.
 */
public class LumberjackRobot extends RobotEntity {
	private static final int MAX_LOGS = 200, MAX_SPREAD = 5, SAPLINGS_KEPT = 16;

	private @Nullable BlockPos treeBase;
	/** Logs of the tree being felled, highest first; filled when the robot reaches the trunk. */
	private final List<BlockPos> logs = new ArrayList<>();
	private @Nullable BlockPos plantPos;
	private @Nullable ItemEntity pickup;
	private @Nullable BlockPos chestPos;
	private @Nullable BlockPos supplyPos;
	/** Where felled trees stood and no sapling has been planted yet. */
	private final List<BlockPos> replant = new ArrayList<>();
	/** A tree it started on stays a tree even once its leaves are gone (its axe may have broken half-way). */
	private @Nullable BlockPos unfinished;

	public LumberjackRobot(EntityType<? extends LumberjackRobot> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item robotItem() {
		return RobotItems.LUMBERJACK_ROBOT;
	}

	@Override
	protected int energyPerOperation() {
		return RobotConfig.lumberEnergyPerLog;
	}

	@Override
	protected void resetTask() {
		treeBase = null;
		logs.clear();
		plantPos = null;
		pickup = null;
		chestPos = null;
		supplyPos = null;
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("lumber_radius", RobotConfig.lumberRadius, 1, RobotConfig.lumberRadius, 1));
	}

	@Override
	protected boolean usesMarkers() {
		return true;
	}

	@Override
	protected int[] workArea() {
		return areaOr(setting("lumber_radius"));
	}

	@Override
	protected Component statusDetail() {
		int[] area = workArea();
		return Component.translatable(field != null ? "message.robots.lumber.area" : "message.robots.farm.home_area",
				area[2] - area[0] + 1, area[3] - area[1] + 1);
	}

	/** It keeps its axes and some saplings of each kind for replanting. */
	@Override
	protected int keeps(ItemStack stack) {
		return isAxe(stack) ? Integer.MAX_VALUE : isSapling(stack) ? SAPLINGS_KEPT : 0;
	}

	private static boolean isAxe(ItemStack stack) {
		return stack.is(ItemTags.AXES);
	}

	private static boolean isSapling(ItemStack stack) {
		return stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem;
	}

	/** Ground a tree grows out of. */
	private static boolean isSoil(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
				|| state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD);
	}

	/** What a felled tree leaves lying around. */
	private static boolean isTreeDrop(ItemStack stack) {
		return stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LOGS) || stack.is(Items.STICK) || stack.is(Items.APPLE);
	}

	private ItemStack findInInventory(java.util.function.Predicate<ItemStack> wanted) {
		for (ItemStack stack : inventory.getItems()) {
			if (!stack.isEmpty() && wanted.test(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	// ---------------- choosing work ----------------

	@Override
	protected void work(ServerLevel level) {
		if (chestPos != null) {
			unload(level, chestPos);
		} else if (supplyPos != null) {
			fetchAxe(level, supplyPos);
		} else if (treeBase != null) {
			chop(level, treeBase);
		} else if (plantPos != null) {
			plant(level, plantPos);
		} else if (pickup != null) {
			collect(pickup);
		} else if (workCooldown == 0) {
			chooseTask(level);
		}
	}

	private void chooseTask(ServerLevel level) {
		workCooldown = RobotConfig.lumberWorkInterval;
		boolean surplus = hasSurplus();
		boolean room = hasEmptySlot();
		if (surplus && !room) {
			chestPos = findUnloadChest(level);
			if (chestPos != null) {
				report("unloading", null);
				return;
			}
		}
		boolean axe = carries(LumberjackRobot::isAxe);
		if (!axe && room) {
			supplyPos = findChestWith(level, LumberjackRobot::isAxe);
			if (supplyPos != null) {
				report("fetching_supplies", null);
				return;
			}
		}
		if (axe && room) {
			treeBase = findTree(level);
			if (treeBase != null) {
				report("chopping", null);
				return;
			}
		}
		if (carries(LumberjackRobot::isSapling)) {
			plantPos = findReplantSpot(level);
			if (plantPos != null) {
				report("planting", null);
				return;
			}
		}
		if (room) {
			pickup = findDrop(level);
			if (pickup != null) {
				report("collecting_drops", null);
				return;
			}
		}
		if (surplus) {
			chestPos = findUnloadChest(level);
			if (chestPos != null) {
				report("unloading", null);
				return;
			}
		}
		report("idle", !axe ? "needs_axe" : surplus ? "no_chest_room" : hasUnreachable() ? "cant_reach" : null);
		idleAtHome();
	}

	// ---------------- trees ----------------

	/** The nearest trunk in the work area: a log standing on dirt whose tree has natural leaves. */
	private @Nullable BlockPos findTree(ServerLevel level) {
		int[] area = workArea();
		int height = RobotConfig.lumberVerticalRange;
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(area[0], area[4] - height, area[1]), new BlockPos(area[2], area[4] + height, area[3]))) {
			if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(BlockTags.LOGS) || !isSoil(level.getBlockState(pos.below()))) {
				continue;
			}
			double distance = pos.distSqr(blockPosition());
			if (distance >= nearestDistance || isSkipped(pos)) {
				continue;
			}
			if (pos.equals(unfinished) || hasNaturalLeaves(level, treeLogs(level, pos))) {
				nearest = pos.immutable();
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	/** Every log connected to the trunk, at its height or above and no more than a few blocks out to the side. */
	private static List<BlockPos> treeLogs(ServerLevel level, BlockPos base) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(base.immutable());
		seen.add(base.immutable());
		while (!queue.isEmpty() && found.size() < MAX_LOGS) {
			BlockPos current = queue.poll();
			found.add(current);
			for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, 0, -1), current.offset(1, 1, 1))) {
				if (Math.abs(next.getX() - base.getX()) > MAX_SPREAD || Math.abs(next.getZ() - base.getZ()) > MAX_SPREAD) {
					continue;
				}
				if (!seen.contains(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
					BlockPos log = next.immutable();
					seen.add(log);
					queue.add(log);
				}
			}
		}
		return found;
	}

	/** At least three leaf blocks that grew there (not placed by a player) touching the logs. */
	private static boolean hasNaturalLeaves(ServerLevel level, List<BlockPos> tree) {
		Set<BlockPos> leaves = new HashSet<>();
		for (BlockPos log : tree) {
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
				BlockPos pos = log.relative(side);
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
					leaves.add(pos);
					if (leaves.size() >= 3) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void chop(ServerLevel level, BlockPos base) {
		if (logs.isEmpty() && !level.getBlockState(base).is(BlockTags.LOGS)) {
			treeBase = null;
			return;
		}
		if (!walkTo(base, 3.2)) {
			if (gaveUpWalking()) {
				skip(base);
				resetTask();
			}
			return;
		}
		if (workCooldown > 0) {
			return;
		}
		workCooldown = RobotConfig.lumberChopInterval;
		if (logs.isEmpty()) {
			logs.addAll(treeLogs(level, base));
			logs.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
			unfinished = base;
		}
		ItemStack axe = findInInventory(LumberjackRobot::isAxe);
		if (axe.isEmpty() || !hasEmptySlot() || !useEnergy(RobotConfig.lumberEnergyPerLog)) {
			resetTask(); // the axe broke or the robot is full: come back to this tree later
			return;
		}
		BlockPos log = logs.remove(0);
		BlockState state = level.getBlockState(log);
		if (state.is(BlockTags.LOGS)) {
			List<ItemStack> drops = Block.getDrops(state, level, log, null);
			level.destroyBlock(log, false);
			for (ItemStack drop : drops) {
				carry(level, drop);
			}
			axe.hurtAndBreak(1, level, null, broken -> {
			});
			inventory.setChanged();
		}
		if (logs.isEmpty()) {
			unfinished = null;
			if (!replant.contains(base)) {
				replant.add(base);
			}
			treeBase = null;
		}
	}

	// ---------------- replanting and tidying up ----------------

	private @Nullable BlockPos findReplantSpot(ServerLevel level) {
		replant.removeIf(pos -> level.hasChunkAt(pos) && !(level.getBlockState(pos).canBeReplaced() && isSoil(level.getBlockState(pos.below()))));
		for (BlockPos pos : replant) {
			if (!isSkipped(pos)) {
				return pos;
			}
		}
		return null;
	}

	private void plant(ServerLevel level, BlockPos pos) {
		if (!walkTo(pos, 2.0)) {
			if (gaveUpWalking()) {
				skip(pos);
				plantPos = null;
			}
			return;
		}
		plantPos = null;
		replant.remove(pos);
		ItemStack sapling = findInInventory(LumberjackRobot::isSapling);
		if (sapling.isEmpty()) {
			return;
		}
		BlockState state = ((BlockItem) sapling.getItem()).getBlock().defaultBlockState();
		if (level.getBlockState(pos).canBeReplaced() && state.canSurvive(level, pos)) {
			level.setBlock(pos, state, Block.UPDATE_ALL);
			sapling.shrink(1);
			inventory.setChanged();
			level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	/** The nearest sapling, log, stick or apple lying in the work area. */
	private @Nullable ItemEntity findDrop(ServerLevel level) {
		int[] area = workArea();
		AABB box = new AABB(area[0], area[4] - 4, area[1], area[2] + 1, area[4] + 12, area[3] + 1);
		ItemEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, each -> each.isAlive() && each.onGround() && isTreeDrop(each.getItem()))) {
			double distance = item.distanceToSqr(this);
			if (distance < nearestDistance && !isSkipped(item.blockPosition())) {
				nearest = item;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private void collect(ItemEntity item) {
		if (!item.isAlive()) {
			pickup = null;
			return;
		}
		if (!walkTo(item, 1.8)) {
			if (gaveUpWalking()) {
				skip(item.blockPosition());
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

	// ---------------- chests ----------------

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

	private void fetchAxe(ServerLevel level, BlockPos pos) {
		Container chest = NearbyChests.at(level, pos);
		if (chest == null) {
			supplyPos = null;
			return;
		}
		if (!walkTo(pos, 3.0)) {
			if (gaveUpWalking()) {
				skip(pos);
				supplyPos = null;
			}
			return;
		}
		supplyPos = null;
		workCooldown = 10;
		takeFrom(chest, LumberjackRobot::isAxe, 1);
	}

	// ---------------- saving ----------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("lumber_replant", BlockPos.CODEC.listOf(), replant);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		replant.clear();
		replant.addAll(input.read("lumber_replant", BlockPos.CODEC.listOf()).orElse(List.of()));
	}
}
