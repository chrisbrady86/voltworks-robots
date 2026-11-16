package dev.voltworks.robots.client;

import dev.voltworks.robots.network.TerminalActionPayload;
import dev.voltworks.robots.network.TerminalViewPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The Robot Terminal's screen: the robots in range on the left; on the right the selected robot's status and energy,
 * buttons to pause it, show its work area and clear its marked area, and its settings: numbers with - and + buttons,
 * on/off switches as buttons. A robot that belongs to another player is listed but its buttons are switched off.
 *
 * <p>Everything shown comes from the server in a {@link TerminalViewPayload}; every button sends a
 * {@link TerminalActionPayload} and the server answers with a fresh list. The list is also refreshed once a second.
 */
public class TerminalScreen extends Screen {
	public static final int WIDTH = 336, HEIGHT = 228;
	private static final int HEADER = 20, LIST_W = 122, ROW_H = 22, ROWS = 8, PAD = 6;
	private static final int FRAME = 0xFF11161C, EDGE = 0xFF3C8FB0, BACK = 0xFF1B2530, PANEL = 0xFF131B24, HEADER_BG = 0xFF24313F;
	private static final int ROW_HOVER = 0xFF223040, ROW_SELECTED = 0xFF2C4A5E;
	private static final int TITLE = 0xFF8FE3FF, TEXT = 0xFFE6EEF5, MUTED = 0xFF8C9BAA;
	private static final int GREEN = 0xFF60BE46, RED = 0xFFDC5046, AMBER = 0xFFE2BE46, BAR_BACK = 0xFF0B0F14;

	private final BlockPos terminal;
	private List<TerminalViewPayload.Robot> robots;
	private int selectedId = -1;
	private int scroll;
	private int refreshTimer;
	private int left, top;

	public TerminalScreen(TerminalViewPayload view) {
		super(Component.translatable("block.robots.robot_terminal"));
		this.terminal = view.terminal();
		this.robots = view.robots();
	}

	public BlockPos terminal() {
		return terminal;
	}

	/** A fresh list from the server: keep the selection if that robot is still in range. */
	public void update(List<TerminalViewPayload.Robot> robots) {
		this.robots = robots;
		rebuildWidgets();
	}

	private TerminalViewPayload.@Nullable Robot selected() {
		for (TerminalViewPayload.Robot robot : robots) {
			if (robot.id() == selectedId) {
				return robot;
			}
		}
		return null;
	}

	private void send(String action, String key, int value) {
		ClientPlayNetworking.send(new TerminalActionPayload(terminal, selectedId, action, key, value));
	}

	// ---------------- layout ----------------

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		if (selected() == null) {
			selectedId = robots.isEmpty() ? -1 : robots.get(0).id();
		}
		scroll = Math.max(0, Math.min(scroll, robots.size() - ROWS));

		int bottom = top + HEIGHT - PAD - 16;
		addRenderableWidget(Button.builder(Component.translatable("gui.robots.terminal.pause_all"),
				b -> send(TerminalActionPayload.PAUSE_ALL, "", 0)).bounds(left + PAD, bottom, 60, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.robots.terminal.resume_all"),
				b -> send(TerminalActionPayload.RESUME_ALL, "", 0)).bounds(left + PAD + 62, bottom, 60, 16).build());

		TerminalViewPayload.Robot robot = selected();
		if (robot == null) {
			return;
		}
		int x = detailX();
		int y = top + HEADER + 62;
		boolean mine = !robot.locked();
		Button pause = addRenderableWidget(Button.builder(Component.translatable(robot.running() ? "gui.robots.terminal.pause" : "gui.robots.terminal.resume"),
				b -> send(TerminalActionPayload.TOGGLE, "", 0)).bounds(x, y, 58, 16).build());
		pause.active = mine;
		addRenderableWidget(Button.builder(Component.translatable("gui.robots.terminal.show_area"),
				b -> send(TerminalActionPayload.SHOW_AREA, "", 0)).bounds(x + 60, y, 62, 16).build());
		if (robot.hasField()) {
			Button clear = addRenderableWidget(Button.builder(Component.translatable("gui.robots.terminal.clear_field"),
					b -> send(TerminalActionPayload.CLEAR_FIELD, "", 0)).bounds(x + 124, y, 68, 16).build());
			clear.active = mine;
		}
		int row = settingsY();
		int switches = 0;
		for (TerminalViewPayload.Setting setting : robot.settings()) {
			if (isSwitch(setting)) {
				continue;
			}
			int right = left + WIDTH - PAD;
			Button minus = Button.builder(Component.literal("-"), b -> change(setting, -1)).bounds(right - 76, row, 16, 16).build();
			Button plus = Button.builder(Component.literal("+"), b -> change(setting, 1)).bounds(right - 16, row, 16, 16).build();
			minus.active = mine && setting.value() > setting.min();
			plus.active = mine && setting.value() < setting.max();
			addRenderableWidget(minus);
			addRenderableWidget(plus);
			row += 18;
		}
		// On/off settings: two buttons to a row under the numbers
		for (TerminalViewPayload.Setting setting : robot.settings()) {
			if (!isSwitch(setting)) {
				continue;
			}
			Component label = Component.translatable("gui.robots.terminal.switch", Component.translatable("gui.robots.setting." + setting.key()),
					Component.translatable(setting.value() == 1 ? "gui.robots.terminal.on" : "gui.robots.terminal.off"));
			Button button = Button.builder(label, b -> send(TerminalActionPayload.SET, setting.key(), 1 - setting.value()))
					.bounds(x + (switches % 2) * 97, row + 2 + (switches / 2) * 16, 95, 14).build();
			button.active = mine;
			addRenderableWidget(button);
			switches++;
		}
	}

	/** A setting that is only ever 0 or 1 is shown as an on/off button. */
	private static boolean isSwitch(TerminalViewPayload.Setting setting) {
		return setting.min() == 0 && setting.max() == 1;
	}

	private int detailX() {
		return left + PAD + LIST_W + 8;
	}

	private int settingsY() {
		return top + HEADER + 96;
	}

	/** One step down or up. Settings with a bigger step move in round numbers (64, 56, ... 8) and stop at their limits. */
	private void change(TerminalViewPayload.Setting setting, int direction) {
		int next = setting.value() + direction * setting.step();
		if (setting.step() > 1) {
			next = Math.round(next / (float) setting.step()) * setting.step();
		}
		send(TerminalActionPayload.SET, setting.key(), Math.max(setting.min(), Math.min(setting.max(), next)));
	}

	@Override
	public void tick() {
		super.tick();
		if (++refreshTimer >= 20) {
			refreshTimer = 0;
			send(TerminalActionPayload.REFRESH, "", 0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------- robot list ----------------

	private int listTop() {
		return top + HEADER + 6;
	}

	/** Index into the robot list of the row under the mouse, or -1. */
	private int rowAt(double x, double y) {
		if (x < left + PAD || x >= left + PAD + LIST_W || y < listTop() || y >= listTop() + ROWS * ROW_H) {
			return -1;
		}
		int index = scroll + (int) ((y - listTop()) / ROW_H);
		return index < robots.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int row = rowAt(event.x(), event.y());
		if (row >= 0) {
			selectedId = robots.get(row).id();
			rebuildWidgets();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0 && robots.size() > ROWS) {
			scroll = Math.max(0, Math.min(robots.size() - ROWS, scroll + (scrollY > 0 ? -1 : 1)));
		}
		return true;
	}

	// ---------------- drawing ----------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		g.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, FRAME);
		g.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, EDGE);
		g.fill(left, top, left + WIDTH, top + HEIGHT, BACK);
		g.fill(left, top, left + WIDTH, top + HEADER, HEADER_BG);
		g.fill(left, top + HEADER, left + WIDTH, top + HEADER + 1, EDGE);
		g.fill(left + PAD, listTop(), left + PAD + LIST_W, listTop() + ROWS * ROW_H, PANEL);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.text(font, title, left + PAD + 2, top + 6, TITLE, true);
		String count = Component.translatable("gui.robots.terminal.count", robots.size()).getString();
		g.text(font, count, left + WIDTH - PAD - 2 - font.width(count), top + 6, MUTED, false);
		drawList(g, mouseX, mouseY);
		TerminalViewPayload.Robot robot = selected();
		if (robot == null) {
			g.textWithWordWrap(font, Component.translatable("message.robots.terminal.no_robot"), detailX(), top + HEADER + 10,
					WIDTH - PAD - (detailX() - left), MUTED, false);
		} else {
			drawDetail(g, robot);
		}
	}

	private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int hovered = rowAt(mouseX, mouseY);
		for (int i = 0; i < ROWS && scroll + i < robots.size(); i++) {
			TerminalViewPayload.Robot robot = robots.get(scroll + i);
			int x = left + PAD, y = listTop() + i * ROW_H;
			if (robot.id() == selectedId) {
				g.fill(x, y, x + LIST_W, y + ROW_H - 1, ROW_SELECTED);
			} else if (scroll + i == hovered) {
				g.fill(x, y, x + LIST_W, y + ROW_H - 1, ROW_HOVER);
			}
			g.item(robot.icon(), x + 2, y + 2);
			g.text(font, font.plainSubstrByWidth(robot.name().getString(), LIST_W - 26), x + 22, y + 2, TEXT, false);
			// A light for its state, then a small energy bar
			g.fill(x + 22, y + 13, x + 26, y + 17, !robot.running() ? RED : robot.energy() == 0 ? AMBER : GREEN);
			int barX = x + 30, barW = LIST_W - 36;
			g.fill(barX, y + 14, barX + barW, y + 16, BAR_BACK);
			g.fill(barX, y + 14, barX + (int) ((long) barW * robot.energy() / Math.max(1, robot.maxEnergy())), y + 16, EDGE);
		}
		if (robots.size() > ROWS) { // scroll bar
			int trackX = left + PAD + LIST_W - 2, trackH = ROWS * ROW_H;
			int thumbH = Math.max(8, trackH * ROWS / robots.size());
			int thumbY = listTop() + (trackH - thumbH) * scroll / (robots.size() - ROWS);
			g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, MUTED);
		}
	}

	private void drawDetail(GuiGraphicsExtractor g, TerminalViewPayload.Robot robot) {
		int x = detailX(), y = top + HEADER + 6;
		int w = left + WIDTH - PAD - x;
		g.item(robot.icon(), x, y);
		g.text(font, font.plainSubstrByWidth(robot.name().getString(), w - 22), x + 22, y, TEXT, true);
		if (robot.locked()) {
			g.text(font, Component.translatable("gui.robots.terminal.owner", robot.owner()), x + 22, y + 10, RED, false);
		} else {
			g.text(font, Component.translatable("gui.robots.terminal.distance", robot.distance()), x + 22, y + 10, MUTED, false);
		}
		g.textWithWordWrap(font, robot.status(), x, y + 22, w, robot.running() ? TEXT : RED, false);

		int barY = y + 44;
		g.fill(x, barY, x + w, barY + 8, BAR_BACK);
		g.fill(x + 1, barY + 1, x + 1 + (int) ((long) (w - 2) * robot.energy() / Math.max(1, robot.maxEnergy())), barY + 7, EDGE);
		String energy = Component.translatable("gui.robots.terminal.energy", robot.energy(), robot.maxEnergy()).getString();
		g.text(font, energy, x + w - font.width(energy), y + 10, MUTED, false);

		int row = settingsY();
		for (TerminalViewPayload.Setting setting : robot.settings()) {
			if (isSwitch(setting)) {
				continue;
			}
			g.text(font, Component.translatable("gui.robots.setting." + setting.key()), x, row + 4, TEXT, false);
			String value = setting.key().endsWith("_percent")
					? (setting.value() == 0 ? Component.translatable("gui.robots.terminal.off").getString() : setting.value() + "%")
					: Integer.toString(setting.value());
			int right = left + WIDTH - PAD;
			g.text(font, value, right - 38 - font.width(value) / 2, row + 4, TITLE, false);
			row += 18;
		}
	}
}
