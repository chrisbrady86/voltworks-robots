package dev.voltworks.robots.block.entity;

import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.entity.RobotEntity;
import dev.voltworks.robots.registry.RobotBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/** Buffers energy from the Voltworks network and hands it to robots standing on or beside the pad. */
public class ChargingPadBlockEntity extends BlockEntity implements EnergyReceiver {
	private int energy;

	public ChargingPadBlockEntity(BlockPos pos, BlockState state) {
		super(RobotBlocks.CHARGING_PAD_ENTITY, pos, state);
	}

	public int getEnergy() {
		return energy;
	}

	@Override
	public int receiveEnergy(int maxAmount, boolean simulate) {
		int accepted = Math.max(0, Math.min(maxAmount, RobotConfig.padCapacity - energy));
		if (!simulate && accepted > 0) {
			energy += accepted;
			setChanged();
		}
		return accepted;
	}

	public void serverTick(ServerLevel level, BlockPos pos) {
		if (energy <= 0) {
			return;
		}
		boolean charged = false;
		for (RobotEntity robot : level.getEntitiesOfClass(RobotEntity.class, new AABB(pos).inflate(0.75, 1.0, 0.75))) {
			int amount = Math.min(Math.min(RobotConfig.padChargePerTick, energy), RobotConfig.maxEnergy - robot.getEnergy());
			if (amount <= 0) {
				continue;
			}
			robot.setEnergy(robot.getEnergy() + amount);
			energy -= amount;
			charged = true;
			if (level.getGameTime() % 10 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, robot.getX(), robot.getY() + 0.9, robot.getZ(), 4, 0.25, 0.5, 0.25, 0.02);
			}
			if (energy <= 0) {
				break;
			}
		}
		if (charged) {
			setChanged();
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("energy", energy);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		energy = Math.max(0, Math.min(RobotConfig.padCapacity, input.getIntOr("energy", 0)));
	}
}
