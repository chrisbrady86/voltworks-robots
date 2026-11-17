package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Keeps hostile mobs away from its home: it fights any monster that comes within its radius with its built-in sword,
 * then picks up what they drop (bones, string, arrows, rotten flesh ...) and takes it to a chest. Neutral mobs such as
 * Endermen and Piglins are left alone unless angry, as are named or leashed mobs and zombie villagers being cured, and
 * creepers can be switched off at a terminal.
 */
public class GuardRobot extends CollectingRobot {
	private static final ItemStack SWORD = new ItemStack(Items.IRON_SWORD);
	private @Nullable Mob target;
	private int hitCooldown;

	public GuardRobot(EntityType<? extends GuardRobot> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item robotItem() {
		return RobotItems.GUARD_ROBOT;
	}

	@Override
	protected int energyPerOperation() {
		return RobotConfig.guardEnergyPerHit;
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("guard_radius", RobotConfig.guardRadius, 1, RobotConfig.guardRadius, 1));
		settings.add(new Setting("fight_creepers", 1, 0, 1, 1));
	}

	@Override
	protected int[] workArea() {
		return areaOr(setting("guard_radius"));
	}

	@Override
	protected ItemStack shownItem() {
		return SWORD;
	}

	@Override
	protected boolean picksUp(ItemStack stack) {
		return isMonsterDrop(stack);
	}

	@Override
	protected void resetJob() {
		target = null;
	}

	@Override
	protected void work(ServerLevel level) {
		if (hitCooldown > 0) {
			hitCooldown--;
		}
		// A monster comes first, even in the middle of an errand
		if (target == null && tickCount % 10 == 0) {
			Mob found = findMonster(level);
			if (found != null) {
				resetTask();
				target = found;
			}
		}
		if (target != null) {
			report("fighting", null);
			fight(level, target);
			return;
		}
		if (runErrand(level) || workCooldown > 0) {
			return;
		}
		workCooldown = 20;
		if (unloadIfFull(level) || pickUpDrops(level) || unloadIfCarrying(level)) {
			return;
		}
		report("guarding", hasSurplus() && !hasEmptySlot() ? "no_chest_room" : null);
		idleAtHome();
	}

	private boolean inGuardArea(Mob mob) {
		int[] area = workArea();
		BlockPos pos = mob.blockPosition();
		return inArea(area, pos) && Math.abs(pos.getY() - area[4]) <= RobotConfig.guardVerticalRange;
	}

	/** A hostile mob in the guard area that the robot fights; {@code engaged} is true for the one it is fighting already. */
	private boolean isTarget(Mob mob, boolean engaged) {
		if (!mob.isAlive() || !(mob instanceof Enemy) || !inGuardArea(mob) || isSkipped(mob) || spared(mob)) {
			return false;
		}
		if (mob instanceof Creeper) {
			// A creeper it hits turns on it, so it only starts on one with the power to finish it
			return setting("fight_creepers") == 1 && (engaged || energy >= hitsToKill(mob) * RobotConfig.guardEnergyPerHit);
		}
		return true;
	}

	/** Somebody's mob (named or on a lead), a mob that is not after anyone, or a zombie villager being cured. */
	private static boolean spared(Mob mob) {
		if (mob.hasCustomName() || mob.isLeashed()) {
			return true;
		}
		if (mob instanceof NeutralMob neutral && !neutral.isAngry()) {
			return true; // Endermen, Zombified Piglins: only once something made them angry
		}
		if (mob instanceof Piglin piglin && piglin.getTarget() == null) {
			return true; // Piglins keep their anger elsewhere: only one that is attacking somebody
		}
		return mob instanceof ZombieVillager villager && villager.isConverting();
	}

	private static int hitsToKill(Mob mob) {
		return Math.max(1, (int) Math.ceil(mob.getHealth() / Math.max(1.0F, RobotConfig.guardDamage)));
	}

	/** It does not leave a creeper it has made angry to go and recharge. */
	@Override
	protected boolean mustFinish() {
		return target instanceof Creeper && target.isAlive();
	}

	/** Gives up on a monster; one that had turned on the robot forgets about it. */
	private void leave(Mob mob) {
		if (mob.getTarget() == this) {
			mob.setTarget(null);
		}
		target = null;
	}

	private @Nullable Mob findMonster(ServerLevel level) {
		int[] area = workArea();
		int height = RobotConfig.guardVerticalRange;
		Mob nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class,
				new AABB(area[0], area[4] - height, area[1], area[2] + 1, area[4] + height + 1, area[3] + 1), mob -> isTarget(mob, false))) {
			double distance = mob.distanceToSqr(this);
			if (distance < nearestDistance) {
				nearest = mob;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private void fight(ServerLevel level, Mob mob) {
		if (!isTarget(mob, true)) {
			leave(mob);
			workCooldown = 20; // let the drops land before picking them up
			return;
		}
		if (!walkTo(mob, 2.4)) {
			if (gaveUpWalking()) {
				skip(mob); // flying or walled off: leave it for a minute, wherever it goes
				leave(mob);
			}
			return;
		}
		if (hitCooldown > 0) {
			return;
		}
		if (!useEnergy(RobotConfig.guardEnergyPerHit)) {
			leave(mob);
			return;
		}
		hitCooldown = RobotConfig.guardHitInterval;
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.6F, 1.0F);
		mob.hurtServer(level, damageSources().mobAttack(this), RobotConfig.guardDamage); // knocks it back too
		if (!mob.isAlive()) {
			expectDrops(mob.position());
		}
	}
}
