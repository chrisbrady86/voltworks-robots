package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.block.FieldMarkerBlock;
import dev.voltworks.robots.registry.RobotBlocks;
import dev.voltworks.robots.registry.RobotItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Walks to ripe crops, harvests and replants them, and carries the harvest to a chest. With seeds in its inventory it
 * plants empty farmland; with a hoe as well it tills the dirt and grass of a field marked with two Field Markers.
 * Without a marked field it works in a square around its home spot.
 */
public class FarmRobot extends RobotEntity {
	private enum Job { HARVEST, PLANT, TILL }

	private static final int MARKER_SCAN_INTERVAL = 40;

	/** The block the crop grows in (one above the soil) and what to do there. */
	private @Nullable BlockPos target;
	private @Nullable Job job;
	private @Nullable BlockPos chestPos;
	/** Field set with Field Markers: {minX, minZ, maxX, maxZ, y of the crops}; null = the square around home. */
	private int @Nullable [] field;
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
	}

	/** {minX, minZ, maxX, maxZ, y} of the marked field, or null when none is marked. */
	public int @Nullable [] getField() {
		return field == null ? null : field.clone();
	}

	/** {minX, minZ, maxX, maxZ, y} of where the robot works: the marked field, or the square around home. */
	private int[] workArea() {
		if (field != null) {
			return field;
		}
		BlockPos home = getHome();
		int radius = setting("farm_radius");
		return new int[]{home.getX() - radius, home.getZ() - radius, home.getX() + radius, home.getZ() + radius, home.getY()};
	}

	@Override
	protected Component statusDetail() {
		int[] area = workArea();
		return Component.translatable(field != null ? "message.robots.farm.field" : "message.robots.farm.home_area",
				area[2] - area[0] + 1, area[3] - area[1] + 1);
	}

	/** Draws the edge of the work area in particles. */
	@Override
	public void showWorkArea(ServerLevel level) {
		int[] area = workArea();
		double y = area[4] + 0.3;
		for (int x = area[0]; x <= area[2] + 1; x++) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, area[1], 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, area[3] + 1, 1, 0, 0, 0, 0);
		}
		for (int z = area[1] + 1; z <= area[3]; z++) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, area[0], y, z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, area[2] + 1, y, z, 1, 0, 0, 0, 0);
		}
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("farm_radius", RobotConfig.farmRadius, 1, RobotConfig.farmRadius, 1));
		settings.add(new Setting("seeds_kept", RobotConfig.farmSeedsKept, 0, 64, 4));
	}

	@Override
	public boolean hasField() {
		return field != null;
	}

	@Override
	public void clearField() {
		field = null;
		resetTask();
	}

	// ---------------- field markers ----------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (tickCount % MARKER_SCAN_INTERVAL == 0) {
			linkMarkers(level);
		}
	}

	/**
	 * A marker standing next to the home spot, paired with another marker in range, sets the field: the rectangle
	 * between them. The robot takes both markers.
	 */
	private void linkMarkers(ServerLevel level) {
		BlockPos home = getHome();
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-2, -1, -2), home.offset(2, 1, 2))) {
			if (!level.getBlockState(pos).is(RobotBlocks.FIELD_MARKER)) {
				continue;
			}
			BlockPos marker = pos.immutable();
			BlockPos partner = FieldMarkerBlock.findPartner(level, marker, RobotConfig.farmMaxFieldSize);
			if (partner == null) {
				continue;
			}
			int[] r = FieldMarkerBlock.rectangle(marker, partner);
			field = new int[]{r[0], r[1], r[2], r[3], marker.getY()};
			for (BlockPos corner : List.of(marker, partner)) {
				level.removeBlock(corner, false);
				carry(level, new ItemStack(RobotBlocks.FIELD_MARKER));
			}
			for (int x : new int[]{r[0], r[2]}) {
				for (int z : new int[]{r[1], r[3]}) {
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5, marker.getY() + 0.6, z + 0.5, 12, 0.3, 0.4, 0.3, 0.0);
				}
			}
			level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.6F, 1.6F);
			resetTask();
			return;
		}
	}

	// ---------------- choosing work ----------------

	@Override
	protected void work(ServerLevel level) {
		if (chestPos != null) {
			unload(level, chestPos);
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
			chestPos = findChest(level);
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
			chestPos = findChest(level);
		}
		if (chestPos != null) {
			report("unloading", null);
			return;
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
		BlockPos from;
		BlockPos to;
		int height = RobotConfig.farmVerticalRange;
		if (field != null) {
			from = new BlockPos(field[0], field[4] - height, field[1]);
			to = new BlockPos(field[2], field[4] + height, field[3]);
		} else {
			int radius = setting("farm_radius");
			from = getHome().offset(-radius, -height, -radius);
			to = getHome().offset(radius, height, radius);
		}
		boolean canHarvest = hasEmptySlot();
		boolean canPlant = !findSeed().isEmpty();
		boolean canTill = field != null && canPlant && !findHoe().isEmpty();

		needSeeds = false;
		needHoe = false;
		BlockPos[] nearest = new BlockPos[Job.values().length];
		double[] distance = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			// What could be done here with a free slot, seeds and a hoe; then whether the robot has what it takes
			Job possible = jobAt(level, pos, true, true, field != null);
			if (possible == null || (possible == Job.HARVEST && !canHarvest)) {
				continue;
			}
			if (possible != Job.HARVEST && !canPlant) {
				needSeeds = true;
				continue;
			}
			if (possible == Job.TILL && !canTill) {
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

	private static @Nullable Job jobAt(ServerLevel level, BlockPos pos, boolean canHarvest, boolean canPlant, boolean canTill) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof CropBlock) {
			return canHarvest && isRipe(state) ? Job.HARVEST : null;
		}
		if (!canPlant || !isClear(state)) {
			return null;
		}
		BlockState soil = level.getBlockState(pos.below());
		if (soil.is(Blocks.FARMLAND)) {
			return state.isAir() ? Job.PLANT : null;
		}
		return canTill && isTillable(soil) ? Job.TILL : null;
	}

	private static boolean isRipe(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	private static boolean isTillable(BlockState soil) {
		return soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.DIRT_PATH);
	}

	/** Air, or a plant the robot may clear away (short grass, flowers, snow), but never water. */
	private static boolean isClear(BlockState state) {
		return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
	}

	private static boolean isSeed(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock;
	}

	private ItemStack findSeed() {
		for (ItemStack stack : inventory.getItems()) {
			if (!stack.isEmpty() && isSeed(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private ItemStack findHoe() {
		for (ItemStack stack : inventory.getItems()) {
			if (stack.is(ItemTags.HOES)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	// ---------------- field work ----------------

	private void doJob(ServerLevel level, BlockPos pos, Job current) {
		boolean canPlant = !findSeed().isEmpty();
		if (!level.hasChunkAt(pos) || jobAt(level, pos, true, canPlant, canPlant && !findHoe().isEmpty()) != current) {
			resetTask();
			return;
		}
		if (!walkTo(pos, 1.7)) {
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
		List<ItemStack> drops = Block.getDrops(state, level, pos, null);
		if (!level.setBlock(pos, ((CropBlock) state.getBlock()).getStateForAge(0), Block.UPDATE_ALL)) {
			return;
		}
		level.levelEvent(2001, pos, Block.getId(state));
		for (ItemStack drop : drops) {
			carry(level, drop);
		}
		useEnergy(RobotConfig.farmEnergyPerHarvest);
	}

	private void plant(ServerLevel level, BlockPos pos) {
		workCooldown = RobotConfig.farmFieldWorkInterval;
		ItemStack seed = findSeed();
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
		ItemStack hoe = findHoe();
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

	// ---------------- unloading ----------------

	/**
	 * How many items of each slot go into a chest: everything except hoes and a few seeds of each kind
	 * (the "seeds kept" setting), which the robot keeps for planting.
	 */
	private int[] surplus() {
		int[] surplus = new int[inventory.getContainerSize()];
		Map<Item, Integer> kept = new HashMap<>();
		for (int slot = 0; slot < surplus.length; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || stack.is(ItemTags.HOES)) {
				continue;
			}
			int keep = 0;
			if (isSeed(stack)) {
				int already = kept.getOrDefault(stack.getItem(), 0);
				keep = Math.max(0, Math.min(stack.getCount(), setting("seeds_kept") - already));
				kept.put(stack.getItem(), already + keep);
			}
			surplus[slot] = stack.getCount() - keep;
		}
		return surplus;
	}

	private boolean hasSurplus() {
		for (int count : surplus()) {
			if (count > 0) {
				return true;
			}
		}
		return false;
	}

	/** The nearest chest to home that can take at least one of the things to unload. */
	private @Nullable BlockPos findChest(ServerLevel level) {
		int[] surplus = surplus();
		for (NearbyChests.Chest chest : NearbyChests.find(level, getHome(), RobotConfig.chestRange, RobotConfig.chestVerticalRange)) {
			if (isSkipped(chest.pos())) {
				continue;
			}
			for (int slot = 0; slot < surplus.length; slot++) {
				if (surplus[slot] > 0 && NearbyChests.hasRoomFor(chest.container(), inventory.getItem(slot))) {
					return chest.pos();
				}
			}
		}
		return null;
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
		int[] surplus = surplus();
		for (int slot = 0; slot < surplus.length; slot++) {
			if (surplus[slot] <= 0) {
				continue;
			}
			ItemStack stack = inventory.getItem(slot);
			ItemStack rest = NearbyChests.insert(chest, stack.copyWithCount(surplus[slot]));
			stack.shrink(surplus[slot] - rest.getCount());
		}
		inventory.setChanged();
		chest.setChanged();
		chestPos = null;
		workCooldown = 10;
	}

	// ---------------- saving ----------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (field != null) {
			output.putIntArray("robot_field", field);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		field = input.getIntArray("robot_field").filter(saved -> saved.length == 5).orElse(null);
	}
}
