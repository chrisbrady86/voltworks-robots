package dev.voltworks.robots.item;

import dev.voltworks.robots.entity.RobotEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/** A packed-up robot. Use it on a block to stand the robot there; that spot becomes its home. */
public class RobotItem extends Item {
	private final Supplier<? extends EntityType<? extends RobotEntity>> type;

	public RobotItem(Properties properties, Supplier<? extends EntityType<? extends RobotEntity>> type) {
		super(properties);
		this.type = type;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = context.getClickedPos();
		BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
				? clicked : clicked.relative(context.getClickedFace());
		RobotEntity robot = type.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (robot == null) {
			return InteractionResult.FAIL;
		}
		ItemStack stack = context.getItemInHand();
		robot.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getRotation() + 180.0F, 0.0F);
		robot.setHome(pos);
		net.minecraft.nbt.CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		robot.setEnergy(data.getIntOr("energy", 0));
		robot.loadSettings(data.getCompoundOrEmpty("settings"));
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		if (name != null) {
			robot.setCustomName(name);
		}
		level.addFreshEntity(robot);
		if (context.getPlayer() == null) {
			stack.shrink(1);
		} else {
			stack.consume(1, context.getPlayer());
		}
		return InteractionResult.SUCCESS;
	}
}
