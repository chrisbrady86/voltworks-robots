package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Walks to ripe crops, harvests and replants them, and carries the harvest to a chest. It also cuts pumpkins and
 * melons off their stems, sugar cane and cactus above the bottom block, and ripe nether wart. With seeds in its
 * inventory it plants empty farmland; with a hoe as well it tills the dirt and grass of a field marked with two Field
 * Markers. When it runs out of seeds or its hoe breaks it takes more from a chest near home. Without a marked field it
 * works in a square around its home spot.
 */
public class FarmRobot extends RobotEntity {
	private enum Job { HARVEST, PLANT, TILL }

	/** The block the crop grows in (one above the soil) and what to do there. */
	private @Nullable BlockPos target;
	private @Nullable Job job;
	private @Nullable BlockPos chestPos;
	/** A chest to take seeds (or else a hoe) from. */
	private @Nullable BlockPos supplyPos;
	private boolean supplySeeds;
	/** Set by the last scan: there was land to plant or till, but no seeds or no hoe to do it with. */
	private boolean needSeeds;
	private boolean needHoe;

	public FarmRobot(EntityType<? extends FarmRobot> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item robotItem() {
		return RobotItems.FARM_ROBOT;
	}

	@Override
	protected int energyPerOperation() {
		return Math.max(RobotConfig.farmEnergyPerHarvest, Math.max(RobotConfig.farmEnergyPerTill, RobotConfig.farmEnergyPerPlant));
	}

	@Override
	protected void resetTask() {
		target = null;
		job = null;
		chestPos = null;
		supplyPos = null;
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("farm_radius", RobotConfig.farmRadius, 1, RobotConfig.farmRadius, 1));
		settings.add(new Setting("seeds_kept", RobotConfig.farmSeedsKept, 0, 64, 4));
	}

	@Override
	protected boolean usesMarkers() {
		return true;
	}

	@Override
	protected int[] workArea() {
		return areaOr(setting("farm_radius"));
	}

	@Override
	protected Component statusDetail() {
		int[] area = workArea();
		return Component.translatable(field != null ? "message.robots.farm.field" : "message.robots.farm.home_area",
				area[2] - area[0] + 1, area[3] - area[1] + 1);
	}

	/** It keeps its hoes and a few seeds of each kind for planting. */
	@Override
	protected int keeps(ItemStack stack) {
		return isHoe(stack) ? Integer.MAX_VALUE : isSeed(stack) ? setting("seeds_kept") : 0;
	}

	// ---------------- choosing work ----------------

	@Override
	protected void work(ServerLevel level) {
		if (chestPos != null) {
			unload(level, chestPos);
		} else if (supplyPos != null) {
			fetchSupplies(level, supplyPos);
		} else if (target != null && job != null) {
			doJob(level, target, job);
		} else if (workCooldown == 0) {
			chooseTask(level);
		}
	}

	private void chooseTask(ServerLevel level) {
		workCooldown = RobotConfig.farmWorkInterval;
		BlockPos[] found = scan(level);
		if (found[Job.HARVEST.ordinal()] != null) {
			start(found[Job.HARVEST.ordinal()], Job.HARVEST);
			return;
		}
		boolean surplus = hasSurplus();
		if (surplus && !hasEmptySlot()) {
			chestPos = findUnloadChest(level);
			if (chestPos != null) {
				report("unloading", null);
				return;
			}
		}
		for (Job next : new Job[]{Job.PLANT, Job.TILL}) {
			if (found[next.ordinal()] != null) {
				start(found[next.ordinal()], next);
				return;
			}
		}
		if (surplus) {
			chestPos = findUnloadChest(level);
		}
		if (chestPos != null) {
			report("unloading", null);
			return;
		}
		// Out of seeds, or the hoe is gone: fetch more from a chest
		if ((needSeeds || needHoe) && hasEmptySlot()) {
			supplySeeds = needSeeds;
			supplyPos = findChestWith(level, needSeeds ? FarmRobot::isCropSeed : FarmRobot::isHoe);
			if (supplyPos != null) {
				report("fetching_supplies", null);
				return;
			}
		}
		report("idle", surplus ? "no_chest_room" : needSeeds ? "needs_seeds" : needHoe ? "needs_hoe" : hasUnreachable() ? "cant_reach" : null);
		idleAtHome();
	}

	private void start(BlockPos pos, Job next) {
		target = pos;
		job = next;
		report(switch (next) {
			case HARVEST -> "harvesting";
			case PLANT -> "planting";
			case TILL -> "tilling";
		}, null);
	}

	/** The nearest place for each job, indexed by {@link Job#ordinal()}; null where there is nothing to do. */
	private BlockPos[] scan(ServerLevel level) {
		int[] area = workArea();
		int height = RobotConfig.farmVerticalRange;
		BlockPos from = new BlockPos(area[0], area[4] - height, area[1]);
		BlockPos to = new BlockPos(area[2], area[4] + height, area[3]);
		boolean canHarvest = hasEmptySlot();
		boolean hasSeed = carries(FarmRobot::isCropSeed);
		boolean hasWart = carries(FarmRobot::isWart);
		boolean hasHoe = carries(FarmRobot::isHoe);

		needSeeds = false;
		needHoe = false;
		BlockPos[] nearest = new BlockPos[Job.values().length];
		double[] distance = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			// What could be done here with a free slot, seeds and a hoe; then whether the robot has what it takes
			Job possible = jobAt(level, pos, field != null);
			if (possible == null || (possible == Job.HARVEST && !canHarvest)) {
				continue;
			}
			if (possible == Job.PLANT && level.getBlockState(pos.below()).is(Blocks.SOUL_SAND)) {
				if (!hasWart) {
					continue; // nether wart is only planted when the robot happens to carry some
				}
			} else if (possible != Job.HARVEST && !hasSeed) {
				needSeeds = true;
				continue;
			}
			if (possible == Job.TILL && !hasHoe) {
				needHoe = true;
				continue;
			}
			if (isSkipped(pos)) {
				continue;
			}
			double d = pos.distSqr(blockPosition());
			if (d < distance[possible.ordinal()]) {
				distance[possible.ordinal()] = d;
				nearest[possible.ordinal()] = pos.immutable();
			}
		}
		return nearest;
	}

	/** What a robot with a free slot, seeds and a hoe could do at this block. Tilling only happens in a marked field. */
	private static @Nullable Job jobAt(ServerLevel level, BlockPos pos, boolean mayTill) {
		BlockState state = level.getBlockState(pos);
		if (isHarvestable(level, pos, state)) {
			return Job.HARVEST;
		}
		if (!isClear(state)) {
			return null;
		}
		BlockState soil = level.getBlockState(pos.below());
		if (soil.is(Blocks.FARMLAND) || soil.is(Blocks.SOUL_SAND)) {
			return state.isAir() ? Job.PLANT : null;
		}
		return mayTill && isTillable(soil) ? Job.TILL : null;
	}

	/**
	 * Ripe crops and nether wart; pumpkins and melons that sit next to their stem; and the second block of a sugar
	 * cane or cactus (so the bottom block stays and regrows).
	 */
	private static boolean isHarvestable(ServerLevel level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (block instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}
		if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockState stem = level.getBlockState(pos.relative(side));
				if (stem.getBlock() instanceof AttachedStemBlock && stem.getValue(AttachedStemBlock.FACING) == side.getOpposite()) {
					return true;
				}
			}
			return false;
		}
		if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)) {
			return level.getBlockState(pos.below()).is(block) && !level.getBlockState(pos.below(2)).is(block);
		}
		return false;
	}

	private static boolean isTillable(BlockState soil) {
		return soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.DIRT_PATH);
	}

	/** Air, or a plant the robot may clear away (short grass, flowers, snow), but never water. */
	private static boolean isClear(BlockState state) {
		return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
	}

	private static boolean isCropSeed(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock;
	}

	private static boolean isWart(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof NetherWartBlock;
	}

	private static boolean isSeed(ItemStack stack) {
		return isCropSeed(stack) || isWart(stack);
	}

	private static boolean isHoe(ItemStack stack) {
		return stack.is(ItemTags.HOES);
	}

	private ItemStack find(Predicate<ItemStack> wanted) {
		for (ItemStack stack : inventory.getItems()) {
			if (!stack.isEmpty() && wanted.test(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	// ---------------- field work ----------------

	private void doJob(ServerLevel level, BlockPos pos, Job current) {
		if (!level.hasChunkAt(pos) || jobAt(level, pos, field != null) != current) {
			resetTask();
			return;
		}
		if (!walkTo(pos, current == Job.HARVEST ? 2.6 : 1.7)) {
			if (gaveUpWalking()) {
				skip(pos);
				resetTask();
			}
			return;
		}
		resetTask();
		switch (current) {
			case HARVEST -> harvest(level, pos);
			case PLANT -> plant(level, pos);
			case TILL -> till(level, pos);
		}
	}

	private void harvest(ServerLevel level, BlockPos pos) {
		workCooldown = RobotConfig.farmWorkInterval;
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		List<ItemStack> drops = new ArrayList<>();
		if (block instanceof CropBlock crop) {
			drops.addAll(Block.getDrops(state, level, pos, null));
			level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
		} else if (block instanceof NetherWartBlock) {
			drops.addAll(Block.getDrops(state, level, pos, null));
			level.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), Block.UPDATE_ALL);
		} else if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)) {
			// Everything from this block up, taken from the top so nothing breaks off and falls
			BlockPos top = pos;
			while (level.getBlockState(top.above()).is(block)) {
				top = top.above();
			}
			for (BlockPos each = top; each.getY() >= pos.getY(); each = each.below()) {
				drops.addAll(Block.getDrops(level.getBlockState(each), level, each, null));
				level.removeBlock(each, false);
			}
		} else {
			drops.addAll(Block.getDrops(state, level, pos, null));
			level.removeBlock(pos, false);
		}
		level.levelEvent(2001, pos, Block.getId(state));
		for (ItemStack drop : drops) {
			carry(level, drop);
		}
		useEnergy(RobotConfig.farmEnergyPerHarvest);
	}

	private void plant(ServerLevel level, BlockPos pos) {
		workCooldown = RobotConfig.farmFieldWorkInterval;
		ItemStack seed = find(level.getBlockState(pos.below()).is(Blocks.SOUL_SAND) ? FarmRobot::isWart : FarmRobot::isCropSeed);
		if (seed.isEmpty()) {
			return;
		}
		BlockState crop = ((BlockItem) seed.getItem()).getBlock().defaultBlockState();
		if (!crop.canSurvive(level, pos) || !useEnergy(RobotConfig.farmEnergyPerPlant)) {
			skip(pos); // too dark for this crop, or out of power
			return;
		}
		level.setBlock(pos, crop, Block.UPDATE_ALL);
		seed.shrink(1);
		inventory.setChanged();
		level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	/** Clears any grass or flowers, turns the soil into farmland and plants it straight away. */
	private void till(ServerLevel level, BlockPos pos) {
		workCooldown = RobotConfig.farmFieldWorkInterval;
		ItemStack hoe = find(FarmRobot::isHoe);
		if (hoe.isEmpty() || !useEnergy(RobotConfig.farmEnergyPerTill)) {
			return;
		}
		BlockState above = level.getBlockState(pos);
		if (!above.isAir()) {
			List<ItemStack> drops = Block.getDrops(above, level, pos, null);
			level.destroyBlock(pos, false);
			for (ItemStack drop : drops) {
				carry(level, drop);
			}
		}
		level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		hoe.hurtAndBreak(1, level, null, broken -> {
		});
		inventory.setChanged();
		plant(level, pos);
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

	/** Takes seeds (as many as it keeps, at least one) or a hoe from the chest. */
	private void fetchSupplies(ServerLevel level, BlockPos pos) {
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
		if (supplySeeds) {
			takeFrom(chest, FarmRobot::isCropSeed, Math.max(1, setting("seeds_kept")));
		} else {
			takeFrom(chest, FarmRobot::isHoe, 1);
		}
	}
}
