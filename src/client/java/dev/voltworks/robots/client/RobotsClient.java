package dev.voltworks.robots.client;

import dev.voltworks.robots.registry.RobotEntities;
import dev.voltworks.robots.network.TerminalViewPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class RobotsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The Voltworks Guide gains the robots' pages ("guide.robots.page.1", "...2", ..., from tools/guide.txt) at the end.
		dev.voltworks.client.GuideScreen.addPages("guide.robots.page.");
		// Robot Terminal: the server's robot list opens the screen (after a right-click) or refreshes the open one
		ClientPlayNetworking.registerGlobalReceiver(TerminalViewPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			if (client.gui.screen() instanceof TerminalScreen screen && screen.terminal().equals(payload.terminal())) {
				screen.update(payload.robots());
			} else if (payload.open()) {
				client.gui.setScreen(new TerminalScreen(payload));
			}
		});
		ModelLayerRegistry.registerModelLayer(RobotRenderer.LAYER, RobotRenderer::createLayer);
		EntityRenderers.register(RobotEntities.FARM_ROBOT, context -> new RobotRenderer(context, "farm_robot"));
		EntityRenderers.register(RobotEntities.BREEDER_ROBOT, context -> new RobotRenderer(context, "breeder_robot"));
		EntityRenderers.register(RobotEntities.ORGANIZER_ROBOT, context -> new RobotRenderer(context, "organizer_robot"));
	}
}
