package dev.voltworks.robots.block;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Field Marker: a small post. Stand one next to a Farm Robot's home spot and another at the opposite corner of the
 * field; the robot picks both up and from then on tills, plants and harvests the rectangle between them (corners
 * included). Right-click a marker to see which marker it pairs with.
 */
public class FieldMarkerBlock extends Block {
	/** Corners may sit at different heights (hills); partners are looked for this far up and down. */
	public static final int VERTICAL_SEARCH = 4;
	private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 12, 10);

	public FieldMarkerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/**
	 * The marker paired with {@code from}: the nearest other marker whose rectangle with it is at most {@code maxSize}
	 * wide and long. Null if there is none.
	 */
	public static @Nullable BlockPos findPartner(Level level, BlockPos from, int maxSize) {
		BlockPos best = null;
		long bestDist = Long.MAX_VALUE;
		int r = maxSize - 1;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				long horizontal = (long) dx * dx + (long) dz * dz;
				if (horizontal >= bestDist || (dx == 0 && dz == 0)) { // a marker straight above/below marks no field
					continue;
				}
				for (int dy = -VERTICAL_SEARCH; dy <= VERTICAL_SEARCH; dy++) {
					p.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
					if (level.isLoaded(p) && level.getBlockState(p).is(RobotBlocks.FIELD_MARKER)) {
						best = p.immutable();
						bestDist = horizontal;
						break;
					}
				}
			}
		}
		return best;
	}

	/** {minX, minZ, maxX, maxZ} of the rectangle with corners {@code a} and {@code b}. */
	public static int[] rectangle(BlockPos a, BlockPos b) {
		return new int[]{Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ())};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			BlockPos partner = findPartner(level, pos, RobotConfig.farmMaxFieldSize);
			Component message;
			if (partner == null) {
				message = Component.translatable("message.robots.field_marker.none", RobotConfig.farmMaxFieldSize);
			} else {
				int[] r = rectangle(pos, partner);
				message = Component.translatable("message.robots.field_marker.pair", partner.getX(), partner.getY(), partner.getZ(),
						r[2] - r[0] + 1, r[3] - r[1] + 1);
			}
			player.sendOverlayMessage(message);
		}
		return InteractionResult.SUCCESS;
	}
}
