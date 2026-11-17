package dev.voltworks.robots.registry;

import dev.voltworks.robots.Robots;
import dev.voltworks.robots.entity.BreederRobot;
import dev.voltworks.robots.entity.ButcherRobot;
import dev.voltworks.robots.entity.FarmRobot;
import dev.voltworks.robots.entity.FisherRobot;
import dev.voltworks.robots.entity.GuardRobot;
import dev.voltworks.robots.entity.LumberjackRobot;
import dev.voltworks.robots.entity.OrganizerRobot;
import dev.voltworks.robots.entity.RancherRobot;
import dev.voltworks.robots.entity.RobotEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class RobotEntities {
	public static final EntityType<FarmRobot> FARM_ROBOT = register("farm_robot", FarmRobot::new);
	public static final EntityType<BreederRobot> BREEDER_ROBOT = register("breeder_robot", BreederRobot::new);
	public static final EntityType<OrganizerRobot> ORGANIZER_ROBOT = register("organizer_robot", OrganizerRobot::new);
	public static final EntityType<LumberjackRobot> LUMBERJACK_ROBOT = register("lumberjack_robot", LumberjackRobot::new);
	public static final EntityType<RancherRobot> RANCHER_ROBOT = register("rancher_robot", RancherRobot::new);
	public static final EntityType<ButcherRobot> BUTCHER_ROBOT = register("butcher_robot", ButcherRobot::new);
	public static final EntityType<GuardRobot> GUARD_ROBOT = register("guard_robot", GuardRobot::new);
	public static final EntityType<FisherRobot> FISHER_ROBOT = register("fisher_robot", FisherRobot::new);

	private RobotEntities() {
	}

	private static <T extends RobotEntity> EntityType<T> register(String name, EntityType.EntityFactory<T> factory) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Robots.id(name));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(factory, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
						.clientTrackingRange(10).noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(type, RobotEntity.createAttributes());
		return type;
	}

	public static void init() {
	}
}
