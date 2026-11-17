package dev.voltworks.robots.block;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.registry.RobotBlocks;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 *
 * <p>A marker remembers who placed it, and only pairs with markers placed by the same player, so one player's
 * markers cannot redirect or use up another's. The memory lives on the server until it restarts (a marker normally
 * stands for a couple of seconds before a robot takes it); a marker nobody is known to have placed pairs with other
 * such markers.
 */
public class FieldMarkerBlock extends Block {
	/** Corners may sit at different heights (hills); partners are looked for this far up and down. */
	public static final int VERTICAL_SEARCH = 4;
	private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 12, 10);
	/** Who placed each standing marker. Not saved: after a restart the placer of a marker left standing is unknown. */
	private static final Map<GlobalPos, UUID> PLACERS = new ConcurrentHashMap<>();

	public FieldMarkerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	// ---------------- who placed it ----------------

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && !oldState.is(state.getBlock())) {
			PLACERS.remove(GlobalPos.of(level.dimension(), pos)); // a new marker: whoever stood one here before no longer counts
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && placer instanceof Player player) {
			setPlacer(level, pos, player.getUUID());
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		PLACERS.remove(GlobalPos.of(level.dimension(), pos));
	}

	/** Records who placed the marker at this position. */
	public static void setPlacer(Level level, BlockPos pos, UUID placer) {
		PLACERS.put(GlobalPos.of(level.dimension(), pos.immutable()), placer);
	}

	/** The player who placed the marker at this position, or null when that is not known. */
	public static @Nullable UUID placer(Level level, BlockPos pos) {
		return PLACERS.get(GlobalPos.of(level.dimension(), pos));
	}

	// ---------------- pairing ----------------

	/**
	 * The marker paired with {@code from}: the nearest other marker placed by the same player whose rectangle with it
	 * is at most {@code maxSize} wide and long. Null if there is none.
	 */
	public static @Nullable BlockPos findPartner(Level level, BlockPos from, int maxSize) {
		UUID placer = placer(level, from);
		return findPartner(level, from, maxSize, other -> Objects.equals(placer, placer(level, other)));
	}

	/** The nearest other marker that passes {@code allowed} and whose rectangle with {@code from} is at most {@code maxSize} wide and long. */
	public static @Nullable BlockPos findPartner(Level level, BlockPos from, int maxSize, Predicate<BlockPos> allowed) {
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
					if (level.isLoaded(p) && level.getBlockState(p).is(RobotBlocks.FIELD_MARKER) && allowed.test(p)) {
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
