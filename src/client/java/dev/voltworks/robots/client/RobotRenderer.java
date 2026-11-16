package dev.voltworks.robots.client;

import dev.voltworks.robots.Robots;
import dev.voltworks.robots.entity.RobotEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Robots are drawn as a player-shaped body with an antenna and ear bolts; each kind has its own skin. */
public class RobotRenderer extends HumanoidMobRenderer<RobotEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robots.id("robot"), "main");

	private final Identifier texture;

	public RobotRenderer(EntityRendererProvider.Context context, String name) {
		super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5F);
		this.texture = Robots.id("textures/entity/" + name + ".png");
	}

	/** The standard 64x64 humanoid skin layout; the extra head parts use the free strip at (56,16)-(64,32). */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition head = mesh.getRoot().getChild("head");
		head.addOrReplaceChild("antenna", CubeListBuilder.create()
				.texOffs(56, 16).addBox(-0.5F, -11.0F, -0.5F, 1.0F, 3.0F, 1.0F)
				.texOffs(56, 20).addBox(-1.0F, -13.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
		head.addOrReplaceChild("right_bolt", CubeListBuilder.create()
				.texOffs(56, 24).addBox(-5.0F, -5.5F, -1.0F, 1.0F, 3.0F, 2.0F), PartPose.ZERO);
		head.addOrReplaceChild("left_bolt", CubeListBuilder.create()
				.texOffs(56, 24).mirror().addBox(4.0F, -5.5F, -1.0F, 1.0F, 3.0F, 2.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return texture;
	}
}
