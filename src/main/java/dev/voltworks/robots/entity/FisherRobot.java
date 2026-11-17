package dev.voltworks.robots.entity;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stands on the bank of open water near its home and fishes with its built-in rod: after a wait it lands a catch from
 * the vanilla fishing loot (fish and junk; no treasure, which needs a real bobber in open water). Its catch goes to
 * the nearest chest when its inventory is full.
 */
public class FisherRobot extends CollectingRobot {
	private static final ItemStack ROD = new ItemStack(Items.FISHING_ROD);
	/** How long a robot with a full inventory and no chest to empty it into waits before it looks again. */
	private static final int FULL_WAIT_TICKS = 200;
	private @Nullable BlockPos water;
	private @Nullable BlockPos stand;
	private int biteIn;

	public FisherRobot(EntityType<? extends FisherRobot> type, Level level) {
		super(type, level);
	}

	@Override
	protected Item robotItem() {
		return RobotItems.FISHER_ROBOT;
	}

	@Override
	protected int energyPerOperation() {
		return RobotConfig.fisherEnergyPerCatch;
	}

	@Override
	protected void defineSettings(List<Setting> settings) {
		super.defineSettings(settings);
		settings.add(new Setting("fisher_radius", RobotConfig.fisherRadius, 1, RobotConfig.fisherRadius, 1));
	}

	@Override
	protected int[] workArea() {
		return areaOr(setting("fisher_radius"));
	}

	@Override
	protected ItemStack shownItem() {
		return ROD;
	}

	@Override
	protected boolean picksUp(ItemStack stack) {
		return false; // its catch goes straight into its inventory
	}

	@Override
	protected void resetJob() {
		water = null;
		stand = null;
	}

	@Override
	protected void work(ServerLevel level) {
		if (runErrand(level)) {
			return;
		}
		if (water == null || stand == null) {
			if (workCooldown == 0) {
				workCooldown = 40;
				if (unloadIfFull(level)) {
					return;
				}
				if (!hasEmptySlot()) {
					workCooldown = FULL_WAIT_TICKS; // full and no chest takes the catch: no use looking for water
					idle(null);
					return;
				}
				findSpot(level);
				if (water == null) {
					idle("no_water");
				} else {
					biteIn = nextWait();
				}
			}
			return;
		}
		if (!hasEmptySlot() || !isOpenWater(level, water)) {
			resetJob(); // full, or the water is gone: unload or look again
			return;
		}
		report("fishing", null);
		if (!walkTo(stand, 1.0)) {
			if (gaveUpWalking()) {
				skip(water);
				resetJob();
			}
			return;
		}
		Vec3 surface = Vec3.atCenterOf(water).add(0, 0.4, 0);
		getLookControl().setLookAt(surface);
		if (tickCount % 15 == 0) {
			level.sendParticles(ParticleTypes.FISHING, surface.x, surface.y, surface.z, 1, 0.05, 0, 0.05, 0);
		}
		if (--biteIn > 0) {
			return;
		}
		biteIn = nextWait();
		if (!useEnergy(RobotConfig.fisherEnergyPerCatch)) {
			return;
		}
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.sendParticles(ParticleTypes.SPLASH, surface.x, surface.y, surface.z, 12, 0.2, 0.05, 0.2, 0.1);
		level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, surface)
				.withParameter(LootContextParams.TOOL, ROD.copy())
				.create(LootContextParamSets.FISHING);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
		for (ItemStack caught : table.getRandomItems(params)) {
			carry(level, caught);
		}
		inventory.setChanged();
	}

	private int nextWait() {
		return RobotConfig.fisherMinWait + getRandom().nextInt(RobotConfig.fisherMaxWait - RobotConfig.fisherMinWait + 1);
	}

	/** A water source with air above it. */
	private static boolean isOpenWater(ServerLevel level, BlockPos pos) {
		FluidState fluid = level.getFluidState(pos);
		return fluid.is(FluidTags.WATER) && fluid.isSource() && level.getBlockState(pos.above()).isAir();
	}

	/** The open water nearest home that has a dry bank to stand on beside it. */
	private void findSpot(ServerLevel level) {
		int[] area = workArea();
		int height = RobotConfig.fisherVerticalRange;
		BlockPos home = getHome();
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(area[0], area[4] - height, area[1]), new BlockPos(area[2], area[4] + height, area[3]))) {
			if (!level.hasChunkAt(pos) || !isOpenWater(level, pos) || isSkipped(pos)) {
				continue;
			}
			double distance = pos.distSqr(home);
			if (distance >= best) {
				continue;
			}
			BlockPos bank = bankBeside(level, pos);
			if (bank != null) {
				water = pos.immutable();
				stand = bank;
				best = distance;
			}
		}
	}

	/** A dry spot right beside the water where the robot can stand: level with the surface or one block above it. */
	private static @Nullable BlockPos bankBeside(ServerLevel level, BlockPos water) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int up = 1; up >= 0; up--) {
				BlockPos feet = water.relative(side).above(up);
				boolean free = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
						&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
				boolean floor = !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
				if (free && floor) {
					return feet.immutable();
				}
			}
		}
		return null;
	}
}
