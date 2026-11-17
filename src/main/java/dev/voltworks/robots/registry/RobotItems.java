package dev.voltworks.robots.registry;

import dev.voltworks.robots.Robots;
import dev.voltworks.robots.item.RobotItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class RobotItems {
	public static final Item ROBOT_CORE = register("robot_core", Item::new);
	public static final Item FARM_ROBOT = register("farm_robot",
			properties -> new RobotItem(properties.stacksTo(1), () -> RobotEntities.FARM_ROBOT));
	public static final Item BREEDER_ROBOT = register("breeder_robot",
			properties -> new RobotItem(properties.stacksTo(1), () -> RobotEntities.BREEDER_ROBOT));
	public static final Item ORGANIZER_ROBOT = register("organizer_robot",
			properties -> new RobotItem(properties.stacksTo(1), () -> RobotEntities.ORGANIZER_ROBOT));
	public static final Item LUMBERJACK_ROBOT = register("lumberjack_robot",
			properties -> new RobotItem(properties.stacksTo(1), () -> RobotEntities.LUMBERJACK_ROBOT));
	public static final Item RANCHER_ROBOT = register("rancher_robot",
			properties -> new RobotItem(properties.stacksTo(1), () -> RobotEntities.RANCHER_ROBOT));
	public static final Item BUTCHER_ROBOT = register("butcher_robot",
			properties -> new RobotItem(properties.stacksTo(1), () -> RobotEntities.BUTCHER_ROBOT));
	public static final Item GUARD_ROBOT = register("guard_robot",
			properties -> new RobotItem(properties.stacksTo(1), () -> RobotEntities.GUARD_ROBOT));
	public static final Item FISHER_ROBOT = register("fisher_robot",
			properties -> new RobotItem(properties.stacksTo(1), () -> RobotEntities.FISHER_ROBOT));

	private RobotItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Robots.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	public static void init() {
	}
}
