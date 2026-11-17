package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotItems;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Shears the sheep in its pen with its built-in shears and picks up what the animals leave lying around: wool, eggs,
 * feathers, leather, scutes. Everything goes to the nearest chest.
 */
public class RancherRobot extends CollectingRobot {
	private static final ItemStack SHEARS = new ItemStack(Items.SHEARS);
	private @Nullable Sheep sheep;

	public RancherRobot(EntityType<? extends RancherRobot> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item robotItem() {
		return RobotItems.RANCHER_ROBOT;
	}

	@Override
	protected int energyPerOperation() {
		return RobotConfig.rancherEnergyPerShear;
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("rancher_radius", RobotConfig.rancherRadius, 1, RobotConfig.rancherRadius, 1));
		settings.add(new Setting("shear_sheep", 1, 0, 1, 1));
	}

	@Override
	protected boolean usesMarkers() {
		return true;
	}

	@Override
	protected int[] workArea() {
		return areaOr(setting("rancher_radius"));
	}

	@Override
	protected @Nullable Component statusDetail() {
		return field == null ? null : Component.translatable("message.robots.breeder.pen", field[2] - field[0] + 1, field[3] - field[1] + 1);
	}

	@Override
	protected ItemStack shownItem() {
		return SHEARS;
	}

	@Override
	protected boolean picksUp(ItemStack stack) {
		return isAnimalProduce(stack);
	}

	/** Wool, eggs and feathers lie around a pen without the robot having done anything; it leaves what a player threw down. */
	@Override
	protected boolean mayTake(ItemEntity item) {
		return item.getOwner() == null;
	}

	@Override
	protected void resetJob() {
		sheep = null;
	}

	@Override
	protected void work(ServerLevel level) {
		if (runErrand(level)) {
			return;
		}
		if (sheep != null) {
			shear(level, sheep);
		} else if (workCooldown == 0) {
			workCooldown = RobotConfig.rancherWorkInterval;
			if (unloadIfFull(level)) {
				return;
			}
			if (setting("shear_sheep") == 1 && hasEmptySlot()) {
				sheep = findSheep(level);
				if (sheep != null) {
					report("shearing", null);
					return;
				}
			}
			if (pickUpDrops(level) || unloadIfCarrying(level)) {
				return;
			}
			idle(null);
		}
	}

	private @Nullable Sheep findSheep(ServerLevel level) {
		int[] area = workArea();
		Sheep nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Sheep each : level.getEntitiesOfClass(Sheep.class, new AABB(area[0], area[4] - 4, area[1], area[2] + 1, area[4] + 5, area[3] + 1),
				s -> s.isAlive() && s.readyForShearing())) {
			double distance = each.distanceToSqr(this);
			if (distance < nearestDistance && !isSkipped(each)) {
				nearest = each;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private void shear(ServerLevel level, Sheep target) {
		if (!target.isAlive() || !target.readyForShearing()) {
			sheep = null;
			return;
		}
		if (!walkTo(target, 2.5)) {
			if (gaveUpWalking()) {
				skip(target);
				sheep = null;
			}
			return;
		}
		sheep = null;
		if (useEnergy(RobotConfig.rancherEnergyPerShear)) {
			swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			target.shear(level, SoundSource.NEUTRAL, SHEARS.copy());
		}
		workCooldown = 20; // let the wool land before picking it up
	}
}
