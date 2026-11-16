package dev.voltworks.robots;

import dev.voltworks.robots.block.RobotTerminalBlock;
import dev.voltworks.robots.network.TerminalActionPayload;
import dev.voltworks.robots.network.TerminalViewPayload;
import dev.voltworks.robots.registry.RobotEntities;
import dev.voltworks.robots.registry.RobotBlocks;
import dev.voltworks.robots.registry.RobotItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

public class Robots implements ModInitializer {
	public static final String MOD_ID = "robots";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		RobotConfig.load();
		RobotItems.init();
		RobotBlocks.init();
		RobotEntities.init();

		// Robot Terminal screen: the robot list goes to the client, button presses come back
		PayloadTypeRegistry.clientboundPlay().register(TerminalViewPayload.TYPE, TerminalViewPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TerminalActionPayload.TYPE, TerminalActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TerminalActionPayload.TYPE, (payload, context) -> RobotTerminalBlock.handle(context.player(), payload));

		ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey =
				ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey,
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.robots"))
						.icon(() -> new ItemStack(RobotItems.FARM_ROBOT))
						.displayItems((params, output) -> {
							output.accept(RobotItems.FARM_ROBOT);
							output.accept(RobotItems.BREEDER_ROBOT);
							output.accept(RobotItems.ORGANIZER_ROBOT);
							output.accept(RobotItems.LUMBERJACK_ROBOT);
							output.accept(RobotBlocks.CHARGING_PAD);
							output.accept(RobotBlocks.FIELD_MARKER);
							output.accept(RobotBlocks.ROBOT_TERMINAL);
							output.accept(RobotItems.ROBOT_CORE);
						})
						.build());
	}
}
