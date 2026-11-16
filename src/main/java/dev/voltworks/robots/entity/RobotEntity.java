package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.block.ChargingPadBlock;
import dev.voltworks.robots.block.FieldMarkerBlock;
import dev.voltworks.robots.block.entity.ChargingPadBlockEntity;
import dev.voltworks.robots.registry.RobotBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A humanoid worker robot: walks around its home spot, carries nine stacks of items, runs on energy from
 * Voltworks Power Cells. Right-click opens its inventory, sneak + right-click pauses it, sneak + punch packs
 * it back up into its item.
 */
public abstract class RobotEntity extends PathfinderMob {
	public static final int INVENTORY_SLOTS = 9;
	private static final int GIVE_UP_AFTER_TICKS = 300;
	private static final int SKIP_TICKS = 1200;
	private static final int HOME_WALK_TICKS = 400;
	private static @Nullable Item powerCell;

	protected final SimpleContainer inventory = new SimpleContainer(INVENTORY_SLOTS) {
		@Override
		public boolean stillValid(Player player) {
			return RobotEntity.this.isAlive() && player.distanceToSqr(RobotEntity.this) < 64.0;
		}
	};
	private final Map<BlockPos, Long> skipped = new HashMap<>();
	protected int energy;
	protected int workCooldown;
	private boolean running = true;
	private @Nullable BlockPos home;
	private int walkTicks;
	/** What the robot is doing and what is stopping it, as translation key endings; shown in its status line. */
	private String activity = "idle";
	private @Nullable String problem;
	private @Nullable BlockPos padPos;
	private boolean charging;
	private int padCooldown;
	private int padEmptyTicks;
	/** Per-robot settings changed at a Robot Terminal, by key; anything not in here follows the config file. */
	private final Map<String, Integer> overrides = new HashMap<>();
	/** Area set with Field Markers: {minX, minZ, maxX, maxZ, y}; null = the square around home. */
	protected int @Nullable [] field;
	/** The player who placed the robot; null for robots from before owners existed, which anyone may use. */
	private @Nullable UUID owner;
	private String ownerName = "";
	// Getting unstuck
	private long walkingUntil;
	private long awaySince = -1;
	private @Nullable Vec3 lastSample;
	private int stuckSamples;
	private int failedWalks;

	protected RobotEntity(EntityType<? extends RobotEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
	}

	// ---------------- work loop ----------------

	/** The item this robot packs up into. */
	protected abstract Item robotItem();

	protected abstract int energyPerOperation();

	/** Called every tick while the robot is switched on and has energy for one operation. */
	protected abstract void work(ServerLevel level);

	/** Forget the current job; called when the robot is paused, runs out of power or cannot reach its target. */
	protected abstract void resetTask();

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (home == null) {
			home = blockPosition();
		}
		if (tickCount % 10 == 0) {
			showCarriedItem();
		}
		if (usesMarkers() && tickCount % MARKER_SCAN_INTERVAL == 0) {
			linkMarkers(level);
		}
		if (!running) {
			stopWalking();
			return;
		}
		if (padCooldown > 0) {
			padCooldown--;
		}
		if (charging) {
			if (recharge(level)) {
				return;
			}
			charging = false;
			getNavigation().stop();
		} else if (wantsCharge() && padCooldown == 0) {
			padCooldown = 100;
			padPos = ChargingPadBlock.findNearest(level, getHome(), RobotConfig.padRange, RobotConfig.padVerticalRange);
			if (padPos != null) {
				charging = true;
				padEmptyTicks = 0;
				resetTask();
				return;
			}
		}
		if (energy < energyPerOperation()) {
			stopWalking();
			return;
		}
		if (tickCount % 20 == 0 && checkStuck(level)) {
			return;
		}
		if (workCooldown > 0) {
			workCooldown--;
		}
		work(level);
	}

	// ---------------- getting unstuck ----------------

	/**
	 * Looks for a robot that is trying to walk but getting nowhere: three walks in a row given up, or no real
	 * movement for 15 seconds while walking. Such a robot is put back on its home spot. True if that happened.
	 */
	private boolean checkStuck(ServerLevel level) {
		boolean walking = level.getGameTime() <= walkingUntil;
		if (walking && lastSample != null && position().distanceToSqr(lastSample) < 0.0625) {
			stuckSamples++;
		} else {
			stuckSamples = 0;
		}
		lastSample = position();
		if (stuckSamples >= 15 || failedWalks >= 3) {
			return returnHome(level);
		}
		return false;
	}

	/** Puts the robot back on its home spot (it could not get anywhere on foot). False when it is already there. */
	protected final boolean returnHome(ServerLevel level) {
		stuckSamples = 0;
		failedWalks = 0;
		awaySince = -1;
		BlockPos spot = getHome();
		if (position().distanceToSqr(Vec3.atBottomCenterOf(spot)) <= 9.0) {
			return false;
		}
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.9, getZ(), 12, 0.25, 0.5, 0.25, 0.02);
		getNavigation().stop();
		resetTask();
		charging = false;
		teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.9, getZ(), 12, 0.25, 0.5, 0.25, 0.02);
		playSound(SoundEvents.ENDERMAN_TELEPORT, 0.4F, 1.6F);
		return true;
	}

	// ---------------- marked area ----------------

	private static final int MARKER_SCAN_INTERVAL = 40;

	/** True for robots whose work area can be set with two Field Markers. */
	protected boolean usesMarkers() {
		return false;
	}

	/**
	 * A marker standing next to the home spot, paired with another marker in range, sets the area: the rectangle
	 * between them. The robot takes both markers.
	 */
	private void linkMarkers(ServerLevel level) {
		BlockPos spot = getHome();
		for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-2, -1, -2), spot.offset(2, 1, 2))) {
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
			showWorkArea(level);
			level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.6F, 1.6F);
			resetTask();
			return;
		}
	}

	/** {minX, minZ, maxX, maxZ, y} of the marked area, or null when none is marked. */
	public final int @Nullable [] getField() {
		return field == null ? null : field.clone();
	}

	/** {minX, minZ, maxX, maxZ, y} of where the robot works, or null for robots without an area of their own. */
	protected int @Nullable [] workArea() {
		return null;
	}

	/** The marked area, or else a square of this radius around home. */
	protected final int[] areaOr(int radius) {
		if (field != null) {
			return field;
		}
		BlockPos spot = getHome();
		return new int[]{spot.getX() - radius, spot.getZ() - radius, spot.getX() + radius, spot.getZ() + radius, spot.getY()};
	}

	protected final boolean inArea(int[] area, BlockPos pos) {
		return pos.getX() >= area[0] && pos.getX() <= area[2] && pos.getZ() >= area[1] && pos.getZ() <= area[3];
	}

	private void stopWalking() {
		if (!getNavigation().isDone()) {
			getNavigation().stop();
		}
	}

	private boolean wantsCharge() {
		int percent = setting("recharge_percent");
		if (percent <= 0) {
			return false;
		}
		return energy < Math.max(energyPerOperation(), (long) RobotConfig.maxEnergy * percent / 100);
	}

	/**
	 * Walks to the charging pad and waits on it until full. False when the robot should go back to work: it is full,
	 * the pad is gone or out of reach, or the pad has run dry and the robot still has power to work with.
	 */
	private boolean recharge(ServerLevel level) {
		if (padPos == null || energy >= RobotConfig.maxEnergy || !level.getBlockState(padPos).is(RobotBlocks.CHARGING_PAD)) {
			return false;
		}
		if (!walkTo(padPos, 1.0)) {
			if (gaveUpWalking()) {
				padCooldown = SKIP_TICKS;
				return false;
			}
			report("going_to_charge", null);
			return true;
		}
		// A pad that holds less than one tick of charge is only trickling (a weak supply): not worth waiting on
		boolean dry = level.getBlockEntity(padPos) instanceof ChargingPadBlockEntity pad && pad.getEnergy() < RobotConfig.padChargePerTick;
		padEmptyTicks = dry ? padEmptyTicks + 1 : 0;
		report("charging", padEmptyTicks > 20 ? "pad_empty" : null);
		if (padEmptyTicks > 100 && energy >= energyPerOperation()) {
			padCooldown = 600;
			return false;
		}
		return true;
	}

	// ---------------- per-robot settings ----------------

	/** One setting a player can change at a Robot Terminal. */
	public record Setting(String key, int value, int min, int max, int step) {
	}

	/**
	 * Adds this robot's settings with their default values. The defaults come from the config file; for ranges and
	 * amounts the config value is also the most a player may set.
	 */
	protected void defineSettings(List<Setting> settings) {
		settings.add(new Setting("recharge_percent", RobotConfig.rechargeBelowPercent, 0, 100, 5));
	}

	/** Every setting with its current value. */
	public final List<Setting> getSettings() {
		List<Setting> defaults = new ArrayList<>();
		defineSettings(defaults);
		List<Setting> current = new ArrayList<>();
		for (Setting each : defaults) {
			int value = overrides.getOrDefault(each.key(), each.value());
			current.add(new Setting(each.key(), Math.max(each.min(), Math.min(each.max(), value)), each.min(), each.max(), each.step()));
		}
		return current;
	}

	public final int setting(String key) {
		for (Setting each : getSettings()) {
			if (each.key().equals(key)) {
				return each.value();
			}
		}
		throw new IllegalArgumentException("Robot has no setting " + key);
	}

	/** Changes a setting, clamped to its range. False when this robot has no such setting. */
	public final boolean setSetting(String key, int value) {
		for (Setting each : getSettings()) {
			if (each.key().equals(key)) {
				overrides.put(key, Math.max(each.min(), Math.min(each.max(), value)));
				resetTask();
				return true;
			}
		}
		return false;
	}

	/** The changed settings, for carrying over into the robot's item. */
	public final CompoundTag saveSettings() {
		CompoundTag tag = new CompoundTag();
		overrides.forEach(tag::putInt);
		return tag;
	}

	public final void loadSettings(CompoundTag tag) {
		overrides.clear();
		for (String key : tag.keySet()) {
			overrides.put(key, tag.getIntOr(key, 0));
		}
	}

	/** True when the robot works a marked area that {@link #clearField()} would remove. */
	public final boolean hasField() {
		return field != null;
	}

	public final void clearField() {
		field = null;
		resetTask();
	}

	/** Sets what the status line says the robot is doing, and what (if anything) is holding it up. */
	protected final void report(String activity, @Nullable String problem) {
		this.activity = activity;
		this.problem = problem;
	}

	/** True while the robot is ignoring at least one block it could not walk to. */
	protected final boolean hasUnreachable() {
		long now = level().getGameTime();
		skipped.values().removeIf(until -> until <= now);
		return !skipped.isEmpty();
	}

	/** Extra text for the end of the status line (the farm robot's field size); null for none. */
	protected @Nullable Component statusDetail() {
		return null;
	}

	/** Draws the edge of the work area in particles; called when the robot is asked for its status. */
	public final void showWorkArea(ServerLevel level) {
		int[] area = workArea();
		if (area == null) {
			return;
		}
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

	/** Walks towards a block. True once the robot is within reach of it. */
	protected final boolean walkTo(BlockPos pos, double reach) {
		if (position().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach) {
			arrived();
			return true;
		}
		if (getNavigation().isDone() || walkTicks % 20 == 0) {
			BlockPos stand = standNear(pos);
			getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1, 1.0);
		}
		walkTicks++;
		walkingUntil = level().getGameTime() + 2;
		awaySince = -1;
		return false;
	}

	/**
	 * Where to walk to in order to reach a block. A block the robot can stand in (a crop, air) is its own target; for
	 * a solid one (a tree trunk, a cactus, a chest) it is the nearest free spot with a floor beside it, at its height
	 * or a little below.
	 */
	private BlockPos standNear(BlockPos pos) {
		Level level = level();
		net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		if (shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.5) {
			return pos; // nothing in the way, or low enough to step onto (a charging pad, a slab)
		}
		// Spots up to two blocks out: the nearest to the target wins, then the nearest to the robot
		BlockPos best = pos;
		double bestScore = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				for (int down = 0; down <= 2; down++) {
					BlockPos spot = pos.offset(dx, -down, dz);
					boolean free = level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()
							&& level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty();
					boolean floor = !level.getBlockState(spot.below()).getCollisionShape(level, spot.below()).isEmpty();
					if (free && floor) {
						double score = (dx * dx + dz * dz) * 100.0 + spot.distSqr(blockPosition());
						if (score < bestScore) {
							best = spot;
							bestScore = score;
						}
						break;
					}
				}
			}
		}
		return best;
	}

	/** Walks towards another entity. True once the robot is within reach of it. */
	protected final boolean walkTo(Entity target, double reach) {
		if (distanceToSqr(target) <= reach * reach) {
			arrived();
			getLookControl().setLookAt(target);
			return true;
		}
		if (getNavigation().isDone() || walkTicks % 20 == 0) {
			getNavigation().moveTo(target, 1.0);
		}
		walkTicks++;
		walkingUntil = level().getGameTime() + 2;
		awaySince = -1;
		return false;
	}

	private void arrived() {
		walkTicks = 0;
		failedWalks = 0;
		getNavigation().stop();
	}

	/** True when the robot has been walking to one target for too long; the walk timer restarts. */
	protected final boolean gaveUpWalking() {
		if (walkTicks < GIVE_UP_AFTER_TICKS) {
			return false;
		}
		int failed = failedWalks + 1;
		arrived();
		failedWalks = failed;
		return true;
	}

	/** Leave this block alone for a minute (it could not be reached). */
	protected final void skip(BlockPos pos) {
		skipped.put(pos.immutable(), level().getGameTime() + SKIP_TICKS);
	}

	protected final boolean isSkipped(BlockPos pos) {
		Long until = skipped.get(pos);
		if (until == null) {
			return false;
		}
		if (until <= level().getGameTime()) {
			skipped.remove(pos);
			return false;
		}
		return true;
	}

	/** Heads back to the home spot when there is nothing to do. */
	protected final void idleAtHome() {
		BlockPos spot = getHome();
		if (position().distanceToSqr(Vec3.atBottomCenterOf(spot)) <= 9.0) {
			awaySince = -1;
			return;
		}
		long now = level().getGameTime();
		if (awaySince < 0) {
			awaySince = now;
		} else if (now - awaySince > HOME_WALK_TICKS && level() instanceof ServerLevel serverLevel) {
			returnHome(serverLevel); // 20 seconds of trying and still not home: there is no way back on foot
			return;
		}
		if (getNavigation().isDone()) {
			getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1, 1.0);
		}
	}

	protected final boolean useEnergy(int amount) {
		if (energy < amount) {
			return false;
		}
		energy -= amount;
		return true;
	}

	// ---------------- inventory ----------------

	public final SimpleContainer getInventory() {
		return inventory;
	}

	protected final boolean hasEmptySlot() {
		for (ItemStack stack : inventory.getItems()) {
			if (stack.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Puts the stack into the robot's inventory; whatever does not fit is dropped at its feet. */
	protected final void carry(ServerLevel level, ItemStack stack) {
		ItemStack rest = inventory.addItem(stack);
		if (!rest.isEmpty()) {
			spawnAtLocation(level, rest);
		}
	}

	/** How many of this item the robot holds back for its own use when it unloads (its tool, some seeds). */
	protected int keeps(ItemStack stack) {
		return 0;
	}

	/** How many items of each slot go into a chest when unloading: everything except what {@link #keeps} holds back. */
	protected final int[] surplus() {
		int[] surplus = new int[inventory.getContainerSize()];
		Map<Item, Integer> kept = new HashMap<>();
		for (int slot = 0; slot < surplus.length; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			int already = kept.getOrDefault(stack.getItem(), 0);
			int keep = Math.max(0, Math.min(stack.getCount(), keeps(stack) - already));
			kept.put(stack.getItem(), already + keep);
			surplus[slot] = stack.getCount() - keep;
		}
		return surplus;
	}

	protected final boolean hasSurplus() {
		for (int count : surplus()) {
			if (count > 0) {
				return true;
			}
		}
		return false;
	}

	/** The nearest chest to home that can take at least one of the things to unload. */
	protected final @Nullable BlockPos findUnloadChest(ServerLevel level) {
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

	/** Moves the surplus into the chest. */
	protected final void unloadInto(net.minecraft.world.Container chest) {
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
	}

	/** The nearest reachable chest around home holding something the robot wants. */
	protected final @Nullable BlockPos findChestWith(ServerLevel level, Predicate<ItemStack> wanted) {
		for (NearbyChests.Chest chest : NearbyChests.find(level, getHome(), RobotConfig.chestRange, RobotConfig.chestVerticalRange)) {
			if (isSkipped(chest.pos())) {
				continue;
			}
			for (int slot = 0; slot < chest.container().getContainerSize(); slot++) {
				ItemStack stack = chest.container().getItem(slot);
				if (!stack.isEmpty() && wanted.test(stack)) {
					return chest.pos();
				}
			}
		}
		return null;
	}

	/** Takes up to {@code max} of the first wanted stack out of a chest. True if it took anything. */
	protected final boolean takeFrom(net.minecraft.world.Container chest, Predicate<ItemStack> wanted, int max) {
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.isEmpty() || !wanted.test(stack)) {
				continue;
			}
			ItemStack taken = chest.removeItem(slot, max);
			int count = taken.getCount();
			ItemStack rest = inventory.addItem(taken);
			if (!rest.isEmpty()) {
				NearbyChests.insert(chest, rest.copy());
			}
			chest.setChanged();
			return rest.getCount() < count;
		}
		return false;
	}

	protected final boolean carries(Predicate<ItemStack> wanted) {
		for (ItemStack stack : inventory.getItems()) {
			if (!stack.isEmpty() && wanted.test(stack)) {
				return true;
			}
		}
		return false;
	}

	/** Moves everything the chest accepts out of the robot's inventory. True if anything moved. */
	protected final boolean depositAll(net.minecraft.world.Container chest) {
		boolean moved = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
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
		}
		return moved;
	}

	/** The robot visibly holds the first thing it is carrying. */
	private void showCarriedItem() {
		ItemStack carried = ItemStack.EMPTY;
		for (ItemStack stack : inventory.getItems()) {
			if (!stack.isEmpty()) {
				carried = stack;
				break;
			}
		}
		ItemStack shown = getMainHandItem();
		if (carried.isEmpty() ? !shown.isEmpty() : !ItemStack.isSameItemSameComponents(carried, shown)) {
			setItemSlot(EquipmentSlot.MAINHAND, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
		}
	}

	// ---------------- player interaction ----------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.getItem() == powerCell()) {
			charge(player, held);
			return InteractionResult.SUCCESS;
		}
		if (!mayControl(player)) {
			player.sendOverlayMessage(Component.translatable("message.robots.robot.not_owner", ownerName));
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			toggleRunning();
			player.sendOverlayMessage(getStatus());
			return InteractionResult.SUCCESS;
		}
		player.openMenu(new SimpleMenuProvider(
				(id, playerInventory, opener) -> new ChestMenu(MenuType.GENERIC_9x1, id, playerInventory, inventory, 1),
				Component.translatable("message.robots.robot.menu", getName(), energy)));
		return InteractionResult.SUCCESS;
	}

	private void charge(Player player, ItemStack cell) {
		if (energy >= RobotConfig.maxEnergy) {
			player.sendOverlayMessage(Component.translatable("message.robots.robot.cell_full"));
			return;
		}
		cell.consume(1, player);
		energy = Math.min(RobotConfig.maxEnergy, energy + RobotConfig.energyPerPowerCell);
		playSound(SoundEvents.BEACON_ACTIVATE, 0.4F, 1.6F);
		player.sendOverlayMessage(getStatus());
	}

	private static Item powerCell() {
		if (powerCell == null) {
			powerCell = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("voltworks", "power_cell")).orElseThrow().value();
		}
		return powerCell;
	}

	public final void toggleRunning() {
		running = !running;
		if (!running) {
			resetTask();
			charging = false;
			getNavigation().stop();
		}
		playSound(running ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.4F, 1.8F);
	}

	/** Makes this player the robot's owner: with robot.ownerOnly on, nobody else can open, pause, change or take it. */
	public final void setOwner(Player player) {
		setOwner(player.getUUID(), player.getGameProfile().name());
	}

	public final void setOwner(UUID id, String name) {
		owner = id;
		ownerName = name;
	}

	public final String getOwnerName() {
		return ownerName;
	}

	/** The owner, server operators and creative-mode players may control a robot; so may anyone if it has no owner. */
	public final boolean mayControl(Player player) {
		return owner == null || RobotConfig.ownerOnly == 0 || owner.equals(player.getUUID()) || player.isCreative()
				|| player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public final boolean isRunning() {
		return running;
	}

	/** "Farm Robot: running - 3000 / 10000 J" */
	public final Component getStatus() {
		String key = !running ? "paused" : charging ? activity : energy < energyPerOperation() ? "out_of_power" : activity;
		Component state = Component.translatable("message.robots.activity." + key);
		if (running && problem != null && (charging || energy >= energyPerOperation())) {
			state = Component.translatable("message.robots.robot.with_problem", state, Component.translatable("message.robots.problem." + problem));
		}
		Component status = Component.translatable("message.robots.robot.status", getName(), state, energy, RobotConfig.maxEnergy);
		Component detail = statusDetail();
		return detail == null ? status : Component.translatable("message.robots.robot.with_detail", status, detail);
	}

	/** What the robot is doing and what holds it up, without its name or energy: "idle (needs seeds)". */
	public final Component getActivityText() {
		String key = !running ? "paused" : charging ? activity : energy < energyPerOperation() ? "out_of_power" : activity;
		Component state = Component.translatable("message.robots.activity." + key);
		if (running && problem != null && (charging || energy >= energyPerOperation())) {
			state = Component.translatable("message.robots.robot.with_problem", state, Component.translatable("message.robots.problem." + problem));
		}
		Component detail = statusDetail();
		return detail == null ? state : Component.translatable("message.robots.robot.with_detail", state, detail);
	}

	public final Item getRobotItem() {
		return robotItem();
	}

	public final boolean isCharging() {
		return charging;
	}

	public final int getEnergy() {
		return energy;
	}

	public final void setEnergy(int energy) {
		this.energy = Math.max(0, Math.min(RobotConfig.maxEnergy, energy));
	}

	public final BlockPos getHome() {
		return home == null ? blockPosition() : home;
	}

	public final void setHome(BlockPos home) {
		this.home = home.immutable();
	}

	// ---------------- damage, pick-up, saving ----------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player player && isAlive()) {
			if (player.isShiftKeyDown()) {
				if (!mayControl(player)) {
					player.sendOverlayMessage(Component.translatable("message.robots.robot.not_owner", ownerName));
					return false;
				}
				packUp(level);
				return true;
			}
			// A plain punch asks the robot what it is doing
			player.sendOverlayMessage(getStatus());
			showWorkArea(level);
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		return false;
	}

	/** Drops what it carries, then turns back into its item, keeping its stored energy and name. */
	private void packUp(ServerLevel level) {
		dropInventory(level);
		ItemStack item = new ItemStack(robotItem());
		if (energy > 0 || !overrides.isEmpty()) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("energy", energy);
			tag.put("settings", saveSettings());
			CustomData.set(DataComponents.CUSTOM_DATA, item, tag);
		}
		if (hasCustomName()) {
			item.set(DataComponents.CUSTOM_NAME, getCustomName());
		}
		spawnAtLocation(level, item);
		playSound(SoundEvents.ITEM_PICKUP, 0.6F, 0.8F);
		discard();
	}

	private void dropInventory(ServerLevel level) {
		setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		for (ItemStack stack : inventory.removeAllItems()) {
			spawnAtLocation(level, stack);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		dropInventory(level);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("robot_energy", energy);
		output.putBoolean("robot_running", running);
		if (home != null) {
			output.store("robot_home", BlockPos.CODEC, home);
		}
		ContainerHelper.saveAllItems(output.child("robot_inventory"), inventory.getItems());
		if (field != null) {
			output.putIntArray("robot_field", field);
		}
		if (owner != null) {
			output.store("robot_owner", UUIDUtil.CODEC, owner);
			output.putString("robot_owner_name", ownerName);
		}
		ValueOutput saved = output.child("robot_settings");
		overrides.forEach(saved::putInt);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setEnergy(input.getIntOr("robot_energy", 0));
		running = input.getBooleanOr("robot_running", true);
		home = input.read("robot_home", BlockPos.CODEC).orElse(null);
		inventory.clearContent();
		ContainerHelper.loadAllItems(input.childOrEmpty("robot_inventory"), inventory.getItems());
		field = input.getIntArray("robot_field").filter(saved -> saved.length == 5).orElse(null);
		owner = input.read("robot_owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("robot_owner_name", "");
		overrides.clear();
		ValueInput saved = input.childOrEmpty("robot_settings");
		List<Setting> known = new ArrayList<>();
		defineSettings(known);
		for (Setting each : known) {
			saved.getInt(each.key()).ifPresent(value -> overrides.put(each.key(), value));
		}
	}
}
