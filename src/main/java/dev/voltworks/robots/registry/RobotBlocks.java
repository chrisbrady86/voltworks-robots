package dev.voltworks.robots.registry;

import dev.voltworks.robots.Robots;
import dev.voltworks.robots.block.ChargingPadBlock;
import dev.voltworks.robots.block.FieldMarkerBlock;
import dev.voltworks.robots.block.RobotTerminalBlock;
import dev.voltworks.robots.block.entity.ChargingPadBlockEntity;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class RobotBlocks {
	public static final Block ROBOT_TERMINAL = register("robot_terminal", RobotTerminalBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(3.5F, 6.0F).sound(SoundType.METAL));
	public static final Block FIELD_MARKER = register("field_marker", FieldMarkerBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).instabreak().noCollision().noOcclusion().sound(SoundType.METAL)
					.lightLevel(state -> 7).pushReaction(PushReaction.POPPED));
	public static final Block CHARGING_PAD = register("charging_pad", ChargingPadBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).noOcclusion().sound(SoundType.METAL)
					.lightLevel(state -> 4));
	public static final BlockEntityType<ChargingPadBlockEntity> CHARGING_PAD_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Robots.id("charging_pad"), FabricBlockEntityTypeBuilder.create(ChargingPadBlockEntity::new, CHARGING_PAD).build());

	private RobotBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Robots.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Robots.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {
	}
}
