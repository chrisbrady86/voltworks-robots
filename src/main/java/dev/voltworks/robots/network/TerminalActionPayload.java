package dev.voltworks.robots.network;

import dev.voltworks.robots.Robots;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: something the player did in a Robot Terminal's screen. {@code action} is one of the constants
 * below; {@code robot} is the entity id it is about (ignored by the "all" actions and {@link #REFRESH});
 * {@code key} and {@code value} are only used by {@link #SET}.
 */
public record TerminalActionPayload(BlockPos terminal, int robot, String action, String key, int value) implements CustomPacketPayload {
	public static final String REFRESH = "refresh", TOGGLE = "toggle", SET = "set", CLEAR_FIELD = "clear_field", SHOW_AREA = "show_area",
			PAUSE_ALL = "pause_all", RESUME_ALL = "resume_all";

	public static final Type<TerminalActionPayload> TYPE = new Type<>(Robots.id("terminal_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalActionPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, TerminalActionPayload::terminal,
			ByteBufCodecs.VAR_INT, TerminalActionPayload::robot,
			ByteBufCodecs.STRING_UTF8, TerminalActionPayload::action,
			ByteBufCodecs.STRING_UTF8, TerminalActionPayload::key,
			ByteBufCodecs.VAR_INT, TerminalActionPayload::value,
			TerminalActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
