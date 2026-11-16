package dev.voltworks.robots.block;

import dev.voltworks.robots.RobotConfig;
import dev.voltworks.robots.entity.RobotEntity;
import dev.voltworks.robots.network.TerminalActionPayload;
import dev.voltworks.robots.network.TerminalViewPayload;
import dev.voltworks.robots.registry.RobotBlocks;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Right-click to open a screen listing every robot in range: what each is doing, with buttons to pause it, show its
 * work area and change its settings.
 */
public class RobotTerminalBlock extends Block {
	/** How far from the terminal a player may be to use its screen. */
	private static final double USE_DISTANCE = 8.0;

	public RobotTerminalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			ServerPlayNetworking.send(serverPlayer, view(serverLevel, pos, true, serverPlayer));
		}
		return InteractionResult.SUCCESS;
	}

	/** The robots in range, nearest first. */
	public static List<RobotEntity> robotsInRange(ServerLevel level, BlockPos terminal) {
		AABB area = new AABB(terminal).inflate(RobotConfig.terminalRange, RobotConfig.terminalVerticalRange, RobotConfig.terminalRange);
		List<RobotEntity> robots = level.getEntitiesOfClass(RobotEntity.class, area, RobotEntity::isAlive);
		robots.sort(Comparator.comparingDouble(robot -> robot.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(terminal))));
		return robots;
	}

	public static TerminalViewPayload view(ServerLevel level, BlockPos terminal, boolean open, ServerPlayer viewer) {
		return new TerminalViewPayload(terminal, open,
				robotsInRange(level, terminal).stream().map(robot -> TerminalViewPayload.Robot.of(robot, terminal, viewer)).toList());
	}

	/**
	 * Carries out what a player did in the terminal's screen and sends the fresh list back. Nothing happens unless
	 * the player is standing at a real terminal and the robot is in that terminal's range.
	 */
	public static void handle(ServerPlayer player, TerminalActionPayload action) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos terminal = action.terminal();
		if (!level.isLoaded(terminal) || !level.getBlockState(terminal).is(RobotBlocks.ROBOT_TERMINAL)
				|| player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(terminal)) > USE_DISTANCE * USE_DISTANCE) {
			return;
		}
		// Other players' robots are listed, but only their owner (or an operator) can change them
		List<RobotEntity> robots = robotsInRange(level, terminal).stream().filter(each -> each.mayControl(player)).toList();
		RobotEntity robot = robots.stream().filter(each -> each.getId() == action.robot()).findFirst().orElse(null);
		switch (action.action()) {
			case TerminalActionPayload.PAUSE_ALL -> robots.stream().filter(RobotEntity::isRunning).forEach(RobotEntity::toggleRunning);
			case TerminalActionPayload.RESUME_ALL -> robots.stream().filter(each -> !each.isRunning()).forEach(RobotEntity::toggleRunning);
			case TerminalActionPayload.TOGGLE -> {
				if (robot != null) robot.toggleRunning();
			}
			case TerminalActionPayload.SET -> {
				if (robot != null) robot.setSetting(action.key(), action.value());
			}
			case TerminalActionPayload.CLEAR_FIELD -> {
				if (robot != null) robot.clearField();
			}
			case TerminalActionPayload.SHOW_AREA -> robotsInRange(level, terminal).stream().filter(each -> each.getId() == action.robot())
					.forEach(each -> each.showWorkArea(level));
			default -> {
			}
		}
		ServerPlayNetworking.send(player, view(level, terminal, false, player));
	}
}
