package dev.voltworks.robots.block;

import dev.voltworks.energy.EnergyConnectable;
import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.block.entity.ChargingPadBlockEntity;
import dev.voltworks.robots.registry.RobotBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Robot Charging Pad: a low plate that takes power from Voltworks cables and generators and charges any robot
 * standing on or right beside it. Robots that run low walk to the nearest pad around their home spot by themselves.
 */
public class ChargingPadBlock extends Block implements EntityBlock, EnergyConnectable {
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

	public ChargingPadBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChargingPadBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != RobotBlocks.CHARGING_PAD_ENTITY) {
			return null;
		}
		return (lvl, pos, st, entity) -> ((ChargingPadBlockEntity) entity).serverTick((ServerLevel) lvl, pos);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChargingPadBlockEntity pad) {
			player.sendOverlayMessage(Component.translatable("message.robots.charging_pad.status", pad.getEnergy(), RobotConfig.padCapacity));
		}
		return InteractionResult.SUCCESS;
	}

	/** The pad nearest to {@code center} within range, or null. */
	public static @Nullable BlockPos findNearest(ServerLevel level, BlockPos center, int range, int verticalRange) {
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -verticalRange, -range), center.offset(range, verticalRange, range))) {
			if (level.hasChunkAt(pos) && level.getBlockState(pos).is(RobotBlocks.CHARGING_PAD)) {
				double distance = pos.distSqr(center);
				if (distance < nearestDistance) {
					nearest = pos.immutable();
					nearestDistance = distance;
				}
			}
		}
		return nearest;
	}
}
