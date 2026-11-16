package dev.voltworks.robots.network;

import dev.voltworks.robots.Robots;
import dev.voltworks.robots.entity.RobotEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server -> client: the robots in range of a Robot Terminal, for its screen. {@code open} is true when the player
 * has just right-clicked the terminal (the screen opens); otherwise it only refreshes a screen that is already open.
 */
public record TerminalViewPayload(BlockPos terminal, boolean open, List<Robot> robots) implements CustomPacketPayload {
	/** One setting of one robot, as shown in a row of the screen. */
	public record Setting(String key, int value, int min, int max, int step) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Setting> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Setting::key,
				ByteBufCodecs.VAR_INT, Setting::value,
				ByteBufCodecs.VAR_INT, Setting::min,
				ByteBufCodecs.VAR_INT, Setting::max,
				ByteBufCodecs.VAR_INT, Setting::step,
				Setting::new);
	}

	/** State of the robot as packed flags: {@link #RUNNING}, {@link #HAS_FIELD}. */
	public record Robot(int id, ItemStack icon, Component name, Component status, int energy, int maxEnergy, int flags, int distance,
			List<Setting> settings) {
		public static final int RUNNING = 1, HAS_FIELD = 2;
		public static final StreamCodec<RegistryFriendlyByteBuf, Robot> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Robot::id,
				ItemStack.STREAM_CODEC, Robot::icon,
				ComponentSerialization.STREAM_CODEC, Robot::name,
				ComponentSerialization.STREAM_CODEC, Robot::status,
				ByteBufCodecs.VAR_INT, Robot::energy,
				ByteBufCodecs.VAR_INT, Robot::maxEnergy,
				ByteBufCodecs.VAR_INT, Robot::flags,
				ByteBufCodecs.VAR_INT, Robot::distance,
				Setting.CODEC.apply(ByteBufCodecs.list()), Robot::settings,
				Robot::new);

		public boolean running() {
			return (flags & RUNNING) != 0;
		}

		public boolean hasField() {
			return (flags & HAS_FIELD) != 0;
		}

		public static Robot of(RobotEntity robot, BlockPos terminal) {
			List<Setting> settings = robot.getSettings().stream()
					.map(each -> new Setting(each.key(), each.value(), each.min(), each.max(), each.step())).toList();
			int flags = (robot.isRunning() ? RUNNING : 0) | (robot.hasField() ? HAS_FIELD : 0);
			return new Robot(robot.getId(), new ItemStack(robot.getRobotItem()), robot.getName(), robot.getActivityText(), robot.getEnergy(),
					dev.voltworks.robots.RobotConfig.maxEnergy, flags, (int) Math.round(Math.sqrt(robot.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(terminal)))), settings);
		}
	}

	public static final Type<TerminalViewPayload> TYPE = new Type<>(Robots.id("terminal_view"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalViewPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, TerminalViewPayload::terminal,
			ByteBufCodecs.BOOL, TerminalViewPayload::open,
			Robot.CODEC.apply(ByteBufCodecs.list()), TerminalViewPayload::robots,
			TerminalViewPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
