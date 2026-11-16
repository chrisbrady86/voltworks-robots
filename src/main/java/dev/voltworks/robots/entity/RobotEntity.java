package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.block.ChargingPadBlock;
import dev.voltworks.robots.block.entity.ChargingPadBlockEntity;
import dev.voltworks.robots.registry.RobotBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
		if (workCooldown > 0) {
			workCooldown--;
		}
		work(level);
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
	public boolean hasField() {
		return false;
	}

	public void clearField() {
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

	/** Shows the player where the robot works; called when it is asked for its status. */
	public void showWorkArea(ServerLevel level) {
	}

	/** Walks towards a block. True once the robot is within reach of it. */
	protected final boolean walkTo(BlockPos pos, double reach) {
		if (position().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach) {
			arrived();
			return true;
		}
		if (getNavigation().isDone() || walkTicks % 20 == 0) {
			getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1, 1.0);
		}
		walkTicks++;
		return false;
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
		return false;
	}

	private void arrived() {
		walkTicks = 0;
		getNavigation().stop();
	}

	/** True when the robot has been walking to one target for too long; the walk timer restarts. */
	protected final boolean gaveUpWalking() {
		if (walkTicks < GIVE_UP_AFTER_TICKS) {
			return false;
		}
		arrived();
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
		if (position().distanceToSqr(Vec3.atBottomCenterOf(spot)) > 9.0 && getNavigation().isDone()) {
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
		overrides.clear();
		ValueInput saved = input.childOrEmpty("robot_settings");
		List<Setting> known = new ArrayList<>();
		defineSettings(known);
		for (Setting each : known) {
			saved.getInt(each.key()).ifPresent(value -> overrides.put(each.key(), value));
		}
	}
}
