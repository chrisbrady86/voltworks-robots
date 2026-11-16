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

	private RobotItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Robots.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	public static void init() {
	}
}
