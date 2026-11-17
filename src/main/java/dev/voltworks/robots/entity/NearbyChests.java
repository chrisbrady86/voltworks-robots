package dev.voltworks.robots.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/** Chest lookups shared by the robots. A double chest counts once, at its left half; locked chests and unopened loot chests do not count. */
public final class NearbyChests {
	private NearbyChests() {
	}

	/** Chests around the centre, nearest first. */
	public static List<Chest> find(ServerLevel level, BlockPos center, int horizontalRange, int verticalRange) {
		List<Chest> chests = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-horizontalRange, -verticalRange, -horizontalRange),
				center.offset(horizontalRange, verticalRange, horizontalRange))) {
			Container container = at(level, pos);
			if (container != null) {
				chests.add(new Chest(pos.immutable(), container));
			}
		}
		chests.sort(Comparator.comparingDouble(chest -> center.distSqr(chest.pos())));
		return chests;
	}

	/** The chest inventory at this position, or null when there is no chest (or only a double chest's right half). */
	public static @Nullable Container at(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.RIGHT) {
			return null;
		}
		if (!usable(level, pos) || (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE && !usable(level, ChestBlock.getConnectedBlockPos(pos, state)))) {
			return null;
		}
		return HopperBlockEntity.getContainerAt(level, pos);
	}

	/**
	 * Robots keep out of a chest whose loot has not been rolled yet (looking inside would roll it: a dungeon or village
	 * chest nobody has opened) and out of a chest locked with a key item.
	 */
	private static boolean usable(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return false;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity instanceof RandomizableContainer loot && loot.getLootTable() != null) {
			return false;
		}
		return !(entity instanceof BaseContainerBlockEntity container && container.isLocked());
	}

	/** True when at least one item of the stack fits. */
	public static boolean hasRoomFor(Container container, ItemStack stack) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack existing = container.getItem(slot);
			if (existing.isEmpty()) {
				return true;
			}
			if (ItemStack.isSameItemSameComponents(existing, stack)
					&& existing.getCount() < Math.min(existing.getMaxStackSize(), container.getMaxStackSize())) {
				return true;
			}
		}
		return false;
	}

	public static boolean contains(Container container, ItemStack stack) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack existing = container.getItem(slot);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				return true;
			}
		}
		return false;
	}

	/** Moves as much of the stack as fits; returns what is left over. */
	public static ItemStack insert(Container container, ItemStack stack) {
		return HopperBlockEntity.addItem(null, container, stack, null);
	}

	public record Chest(BlockPos pos, Container container) {
	}
}
