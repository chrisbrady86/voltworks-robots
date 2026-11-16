import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Generates the Robots mod's textures, item/block models and translations. All art is original.
 * Usage: java tools/GenAssets.java src/main/resources
 * Recipes, the terminal's loot table and tags are plain JSON files kept by hand.
 */
public class GenAssets {
	static Path res;
	static final String NS = "robots";

	interface Painter {
		int at(int x, int y);
	}

	public static void main(String[] args) throws Exception {
		res = Paths.get(args[0]);
		robot("farm_robot", rgb(96, 190, 70), rgb(226, 190, 70), Emblem.WHEAT);
		robot("breeder_robot", rgb(236, 110, 160), rgb(250, 232, 238), Emblem.HEART);
		robot("organizer_robot", rgb(70, 150, 235), rgb(214, 160, 90), Emblem.CRATE);
		core();
		terminal();
		fieldMarker();
		chargingPad();
		lang();
		System.out.println("done");
	}

	enum Emblem { WHEAT, HEART, CRATE }

	// Steel palette
	static final int LIGHT = rgb(206, 212, 220), MID = rgb(168, 176, 188), DARK = rgb(112, 120, 134),
			DEEP = rgb(62, 68, 80), VISOR = rgb(24, 28, 38);

	// ---------------- robot skin (64x64, classic humanoid layout) + item icon ----------------
	static void robot(String name, int accent, int second, Emblem emblem) throws Exception {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

		// Head (8x8x8 at 0,0)
		face(img, 8, 0, 8, 8, (x, y) -> (x == 0 || y == 0 || x == 7 || y == 7) ? MID : (x >= 3 && x <= 4 && y >= 3 && y <= 4) ? DEEP : LIGHT); // top
		face(img, 16, 0, 8, 8, (x, y) -> DARK); // bottom
		Painter side = (x, y) -> {
			if (y == 0) return LIGHT;
			if (y == 7) return DARK;
			if (y >= 3 && y <= 5 && x >= 3 && x <= 5) return (x == 4 && y == 4) ? accent : DEEP; // ear socket
			return (x == 0 || x == 7) ? DARK : MID;
		};
		face(img, 0, 8, 8, 8, side);
		face(img, 16, 8, 8, 8, side);
		face(img, 8, 8, 8, 8, (x, y) -> { // front: visor with two glowing eyes, mouth grille
			if (y == 0) return LIGHT;
			if (y >= 2 && y <= 4 && x >= 1 && x <= 6) {
				if (y == 3 && (x == 2 || x == 5)) return brighten(accent, 60);
				if (y == 3 && (x == 1 || x == 6)) return VISOR;
				return (y == 2 || y == 4) && (x == 2 || x == 5) ? shade(accent, 0.45) : VISOR;
			}
			if (y == 6 && x >= 2 && x <= 5) return x % 2 == 0 ? DEEP : DARK;
			if (y == 7) return DARK;
			return (x == 0 || x == 7) ? MID : LIGHT;
		});
		face(img, 24, 8, 8, 8, (x, y) -> { // back: vent slats
			if (y == 0) return LIGHT;
			if (y == 7) return DARK;
			if (x >= 2 && x <= 5 && (y == 3 || y == 5)) return DEEP;
			return (x == 0 || x == 7) ? DARK : MID;
		});

		// Body (8x12x4 at 16,16)
		face(img, 20, 16, 8, 4, (x, y) -> (x >= 3 && x <= 4) ? DEEP : MID); // top: neck joint
		face(img, 28, 16, 8, 4, (x, y) -> DEEP); // bottom
		Painter bodySide = (x, y) -> y <= 1 ? accent : y >= 10 ? DEEP : (y == 5 ? DARK : MID);
		face(img, 16, 20, 4, 12, bodySide);
		face(img, 28, 20, 4, 12, bodySide);
		face(img, 20, 20, 8, 12, (x, y) -> { // front: coloured shoulders, chest plate with emblem, belt
			if (y <= 1) return (x == 0 || x == 7) ? shade(accent, 0.75) : accent;
			if (y >= 10) return (y == 10 && x >= 3 && x <= 4) ? second : DEEP;
			if (x >= 1 && x <= 6 && y >= 3 && y <= 8) {
				if (x == 1 || x == 6 || y == 3 || y == 8) return DARK;
				return emblem(emblem, x - 2, y - 4, accent, second);
			}
			return (x == 0 || x == 7) ? MID : LIGHT;
		});
		face(img, 32, 20, 8, 12, (x, y) -> { // back: power pack with a charge light
			if (y <= 1) return accent;
			if (y >= 10) return DEEP;
			if (x >= 2 && x <= 5 && y >= 3 && y <= 8) {
				if (x == 2 || x == 5 || y == 3 || y == 8) return DEEP;
				return (y == 4 && x == 3) ? rgb(120, 255, 120) : (y % 2 == 0 ? DARK : rgb(84, 92, 106));
			}
			return (x == 0 || x == 7) ? DARK : MID;
		});

		// Arm (4x12x4 at 40,16): shoulder cap, elbow joint, dark hand
		limb(img, 40, 16, accent, 5, true);
		// Leg (4x12x4 at 0,16): hip, knee joint, dark boot
		limb(img, 0, 16, accent, 5, false);

		// Antenna and ear bolts (free strip at 56,16)
		fill(img, 56, 16, 4, 4, DARK);
		fill(img, 56, 20, 8, 4, brighten(accent, 30));
		fill(img, 56, 24, 6, 5, DEEP);
		fill(img, 58, 26, 1, 3, accent);

		save(img, "assets/" + NS + "/textures/entity/" + name + ".png");

		// Item icon: the robot's head and shoulders
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		fill(icon, 7, 0, 2, 1, brighten(accent, 30)); // antenna tip
		fill(icon, 7, 1, 2, 2, DARK);
		fill(icon, 3, 3, 10, 8, LIGHT);
		fill(icon, 3, 3, 10, 1, brighten(LIGHT, 20));
		fill(icon, 3, 10, 10, 1, DARK);
		fill(icon, 12, 4, 1, 6, MID);
		fill(icon, 2, 5, 1, 3, DEEP); // ear bolts
		fill(icon, 13, 5, 1, 3, DEEP);
		fill(icon, 4, 5, 8, 3, VISOR);
		fill(icon, 5, 6, 2, 1, brighten(accent, 60));
		fill(icon, 9, 6, 2, 1, brighten(accent, 60));
		fill(icon, 6, 9, 4, 1, DEEP);
		fill(icon, 6, 11, 4, 1, DEEP); // neck
		fill(icon, 1, 12, 14, 4, accent);
		fill(icon, 1, 12, 14, 1, brighten(accent, 30));
		fill(icon, 1, 15, 14, 1, shade(accent, 0.7));
		fill(icon, 5, 13, 6, 3, MID);
		fill(icon, 7, 14, 2, 1, second);
		outline(icon);
		save(icon, "assets/" + NS + "/textures/item/" + name + ".png");
		itemModel(name);
	}

	static int emblem(Emblem emblem, int x, int y, int accent, int second) { // 4 wide, 4 tall
		String[] rows = switch (emblem) {
			case WHEAT -> new String[]{".b.b", "b.b.", ".a..", ".a.."};
			case HEART -> new String[]{"a.a.", "aaab", "aaa.", ".a.."};
			case CRATE -> new String[]{"bbbb", "baab", "baab", "bbbb"};
		};
		char c = rows[y].charAt(x);
		return c == 'a' ? accent : c == 'b' ? second : rgb(40, 46, 58);
	}

	/** One arm or leg: all four sides alike, with a coloured cap at the top for arms. */
	static void limb(BufferedImage img, int u, int v, int accent, int joint, boolean arm) {
		face(img, u + 4, v, 4, 4, (x, y) -> arm ? accent : MID);
		face(img, u + 8, v, 4, 4, (x, y) -> DEEP);
		for (int i = 0; i < 4; i++) {
			int dark = i == 0 || i == 3 ? 1 : 0; // outer and back faces a little darker
			face(img, u + i * 4, v + 4, 4, 12, (x, y) -> {
				if (arm && y <= 2) return y == 2 ? shade(accent, 0.7) : accent;
				if (y == joint || y == joint + 1) return x % 2 == 0 ? DEEP : DARK;
				if (y >= 10) return y == 10 ? DARK : DEEP;
				if (!arm && y == 0) return DARK;
				return dark == 1 ? MID : (x == 0 ? LIGHT : (x == 3 ? MID : LIGHT));
			});
		}
	}

	// ---------------- robot core ----------------
	static void core() throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double d = Math.hypot(x - 7.5, y - 7.5);
				if (d <= 2.6) img.setRGB(x, y, d < 1.2 ? rgb(230, 255, 255) : rgb(70, 220, 240));
				else if (d <= 4.0) img.setRGB(x, y, VISOR);
				else if (d <= 6.4) img.setRGB(x, y, (x + y) % 5 == 0 ? DEEP : (y < 7 ? LIGHT : MID));
				else if (d <= 7.4) img.setRGB(x, y, DARK);
			}
		}
		for (int[] p : new int[][]{{7, 1}, {8, 14}, {1, 8}, {14, 7}}) img.setRGB(p[0], p[1], rgb(226, 190, 70)); // contacts
		save(img, "assets/" + NS + "/textures/item/robot_core.png");
		itemModel("robot_core");
	}

	// ---------------- robot terminal ----------------
	static void terminal() throws Exception {
		BufferedImage side = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		face(side, 0, 0, 16, 16, (x, y) -> {
			if (x == 0 || y == 0 || x == 15 || y == 15) return DARK;
			if (x >= 2 && x <= 13 && y >= 2 && y <= 10) {
				if (x == 2 || x == 13 || y == 2 || y == 10) return DEEP;
				// Screen: a little robot face
				if (y == 5 && (x == 5 || x == 6 || x == 9 || x == 10)) return rgb(120, 240, 255);
				if (y == 8 && x >= 6 && x <= 9) return rgb(70, 190, 220);
				return (y % 2 == 0) ? rgb(18, 40, 58) : rgb(22, 48, 68);
			}
			if (y == 12 || y == 13) { // buttons
				if (x == 3 || x == 4) return rgb(96, 190, 70);
				if (x == 6 || x == 7) return rgb(226, 190, 70);
				if (x == 9 || x == 10) return rgb(220, 80, 70);
			}
			return ((x == 1 || x == 14) && (y == 1 || y == 14)) ? DEEP : MID;
		});
		save(side, "assets/" + NS + "/textures/block/robot_terminal_side.png");
		BufferedImage top = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		face(top, 0, 0, 16, 16, (x, y) -> {
			if (x == 0 || y == 0 || x == 15 || y == 15) return DARK;
			if ((x == 2 || x == 13) && (y == 2 || y == 13)) return DEEP;
			if (x >= 5 && x <= 10 && y >= 5 && y <= 10) return (y % 2 == 0) ? DEEP : DARK; // vent
			return (x + y) % 7 == 0 ? MID : LIGHT;
		});
		save(top, "assets/" + NS + "/textures/block/robot_terminal_top.png");
		write("assets/" + NS + "/models/block/robot_terminal.json",
				"{ \"parent\": \"minecraft:block/cube_column\", \"textures\": { \"end\": \"" + NS + ":block/robot_terminal_top\", \"side\": \""
						+ NS + ":block/robot_terminal_side\" } }");
		write("assets/" + NS + "/blockstates/robot_terminal.json", "{ \"variants\": { \"\": { \"model\": \"" + NS + ":block/robot_terminal\" } } }");
		write("assets/" + NS + "/items/robot_terminal.json", "{ \"model\": { \"type\": \"minecraft:model\", \"model\": \"" + NS + ":block/robot_terminal\" } }");
	}

	// ---------------- field marker ----------------
	static void fieldMarker() throws Exception {
		int green = rgb(96, 190, 70), wheat = rgb(226, 190, 70);
		BufferedImage post = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		face(post, 0, 0, 16, 16, (x, y) -> (y % 4 == 1) ? (x % 2 == 0 ? wheat : shade(wheat, 0.8)) : (x % 2 == 0 ? MID : DARK));
		save(post, "assets/" + NS + "/textures/block/field_marker_post.png");
		BufferedImage base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		face(base, 0, 0, 16, 16, (x, y) -> (x + y) % 6 == 0 ? DEEP : ((x ^ y) % 2 == 0 ? DARK : shade(DARK, 0.85)));
		save(base, "assets/" + NS + "/textures/block/field_marker_base.png");
		BufferedImage lamp = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		face(lamp, 0, 0, 16, 16, (x, y) -> {
			if (x == 0 || y == 0 || x == 15 || y == 15) return shade(green, 0.45);
			return x < 6 && y < 6 ? rgb(215, 255, 200) : ((x + y) % 5 == 0 ? brighten(green, 50) : green);
		});
		save(lamp, "assets/" + NS + "/textures/block/field_marker_lamp.png");
		write("assets/" + NS + "/models/block/field_marker.json", """
			{ "parent": "minecraft:block/block", "ambientocclusion": false,
			  "textures": { "post": "robots:block/field_marker_post", "lamp": "robots:block/field_marker_lamp",
			                "base": "robots:block/field_marker_base", "particle": "robots:block/field_marker_lamp" },
			  "elements": [
			""" + String.join(",\n", List.of(
				box(5, 0, 5, 11, 1, 11, "#base", false),
				box(7, 1, 7, 9, 9, 9, "#post", false),
				box(6, 9, 6, 10, 12, 10, "#lamp", true),
				box(5.5, 12, 5.5, 10.5, 13, 10.5, "#base", false))) + """
			  ],
			  "display": {
			    "gui": { "rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [1.1, 1.1, 1.1] },
			    "ground": { "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5] },
			    "fixed": { "scale": [0.8, 0.8, 0.8] },
			    "thirdperson_righthand": { "rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5] },
			    "firstperson_righthand": { "rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6] } } }
			""");
		write("assets/" + NS + "/blockstates/field_marker.json", "{ \"variants\": { \"\": { \"model\": \"" + NS + ":block/field_marker\" } } }");
		write("assets/" + NS + "/items/field_marker.json", "{ \"model\": { \"type\": \"minecraft:model\", \"model\": \"" + NS + ":block/field_marker\" } }");
	}

	// ---------------- robot charging pad ----------------
	static void chargingPad() throws Exception {
		int bolt = rgb(255, 214, 80), glow = rgb(70, 220, 240);
		String[] emblem = { // lightning bolt, 6 wide, 8 tall, drawn at 5,4
				"...##.", "..##..", ".##...", "#####.", ".#####", "...##.", "..##..", ".##..."};
		BufferedImage top = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		face(top, 0, 0, 16, 16, (x, y) -> {
			if (x == 0 || y == 0 || x == 15 || y == 15) return DARK;
			if (x == 1 || y == 1 || x == 14 || y == 14) return (x + y) % 2 == 0 ? glow : shade(glow, 0.6);
			if (x >= 5 && x <= 10 && y >= 4 && y <= 11 && emblem[y - 4].charAt(x - 5) == '#') return bolt;
			if (x >= 3 && x <= 12 && y >= 3 && y <= 12) return (x + y) % 2 == 0 ? VISOR : rgb(34, 40, 52);
			return MID;
		});
		save(top, "assets/" + NS + "/textures/block/charging_pad_top.png");
		BufferedImage side = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		face(side, 0, 0, 16, 16, (x, y) -> {
			if (y == 13) return x % 4 < 2 ? glow : shade(glow, 0.6); // the model only shows the bottom three rows
			return y >= 15 ? DEEP : (x == 0 || x == 15 ? DEEP : DARK);
		});
		save(side, "assets/" + NS + "/textures/block/charging_pad_side.png");
		write("assets/" + NS + "/models/block/charging_pad.json", """
			{ "parent": "minecraft:block/block",
			  "textures": { "top": "robots:block/charging_pad_top", "side": "robots:block/charging_pad_side",
			                "particle": "robots:block/charging_pad_top" },
			  "elements": [
			    { "from": [0, 0, 0], "to": [16, 3, 16], "faces": {
			      "up": { "texture": "#top" }, "down": { "texture": "#side", "uv": [0, 14, 16, 16], "cullface": "down" },
			      "north": { "texture": "#side", "cullface": "north" }, "south": { "texture": "#side", "cullface": "south" },
			      "west": { "texture": "#side", "cullface": "west" }, "east": { "texture": "#side", "cullface": "east" } } }
			  ] }
			""");
		write("assets/" + NS + "/blockstates/charging_pad.json", "{ \"variants\": { \"\": { \"model\": \"" + NS + ":block/charging_pad\" } } }");
		write("assets/" + NS + "/items/charging_pad.json", "{ \"model\": { \"type\": \"minecraft:model\", \"model\": \"" + NS + ":block/charging_pad\" } }");
	}

	/** One model element with the same texture on all six faces; a lit element glows and is not shaded. */
	static String box(double x0, double y0, double z0, double x1, double y1, double z1, String texture, boolean lit) {
		StringBuilder faces = new StringBuilder();
		for (String side : new String[]{"north", "south", "west", "east", "up", "down"}) {
			faces.append(faces.length() == 0 ? "" : ", ").append("\"").append(side).append("\": { \"texture\": \"").append(texture).append("\" }");
		}
		return String.format(Locale.ROOT, "    { \"from\": [%s, %s, %s], \"to\": [%s, %s, %s],%s \"faces\": { %s } }",
				x0, y0, z0, x1, y1, z1, lit ? " \"shade\": false, \"light_emission\": 15," : "", faces);
	}

	// ---------------- translations ----------------
	static void lang() throws Exception {
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("itemGroup.robots", "Robots");
		lang.put("item.robots.robot_core", "Robot Core");
		lang.put("item.robots.farm_robot", "Farm Robot");
		lang.put("item.robots.breeder_robot", "Animal Breeder Robot");
		lang.put("item.robots.organizer_robot", "Organizer Robot");
		lang.put("entity.robots.farm_robot", "Farm Robot");
		lang.put("entity.robots.breeder_robot", "Animal Breeder Robot");
		lang.put("entity.robots.organizer_robot", "Organizer Robot");
		lang.put("block.robots.robot_terminal", "Robot Terminal");
		lang.put("block.robots.field_marker", "Field Marker");
		lang.put("message.robots.field_marker.none", "No partner marker within %s blocks. Place another at the opposite corner of the field.");
		lang.put("message.robots.field_marker.pair", "Paired with the marker at %s, %s, %s: a %s x %s field.");
		lang.put("message.robots.terminal.no_robot", "No robot is in range of this terminal.");
		lang.put("gui.robots.terminal.count", "%s in range");
		lang.put("gui.robots.terminal.distance", "%s blocks away");
		lang.put("gui.robots.terminal.energy", "%s / %s J");
		lang.put("gui.robots.terminal.pause", "Pause");
		lang.put("gui.robots.terminal.resume", "Resume");
		lang.put("gui.robots.terminal.show_area", "Show area");
		lang.put("gui.robots.terminal.clear_field", "Clear field");
		lang.put("gui.robots.terminal.pause_all", "Pause all");
		lang.put("gui.robots.terminal.resume_all", "Resume all");
		lang.put("gui.robots.terminal.off", "off");
		lang.put("gui.robots.setting.recharge_percent", "Recharge below");
		lang.put("gui.robots.setting.farm_radius", "Work radius");
		lang.put("gui.robots.setting.seeds_kept", "Seeds kept");
		lang.put("gui.robots.setting.animal_range", "Animal range");
		lang.put("gui.robots.setting.max_animals", "Animals per kind");
		lang.put("gui.robots.setting.items_per_trip", "Items per trip");
		lang.put("message.robots.robot.cell_full", "This robot's power cell is already full.");
		lang.put("message.robots.robot.status", "%s: %s - %s / %s J");
		lang.put("message.robots.robot.menu", "%s - %s J");
		lang.put("message.robots.robot.with_problem", "%s (%s)");
		lang.put("message.robots.robot.with_detail", "%s - %s");
		lang.put("block.robots.charging_pad", "Robot Charging Pad");
		lang.put("message.robots.charging_pad.status", "Charging pad: %s / %s J");
		lang.put("message.robots.farm.field", "marked field %s x %s");
		lang.put("message.robots.farm.home_area", "%s x %s around home");
		lang.put("message.robots.activity.paused", "paused");
		lang.put("message.robots.activity.out_of_power", "out of power");
		lang.put("message.robots.activity.idle", "idle");
		lang.put("message.robots.activity.going_to_charge", "walking to its charging pad");
		lang.put("message.robots.activity.charging", "charging");
		lang.put("message.robots.activity.harvesting", "harvesting");
		lang.put("message.robots.activity.planting", "planting");
		lang.put("message.robots.activity.tilling", "tilling");
		lang.put("message.robots.activity.unloading", "unloading");
		lang.put("message.robots.activity.fetching_food", "fetching food");
		lang.put("message.robots.activity.feeding", "feeding animals");
		lang.put("message.robots.activity.collecting", "collecting from the input chest");
		lang.put("message.robots.activity.delivering", "delivering");
		lang.put("message.robots.activity.returning", "returning items");
		lang.put("message.robots.problem.pad_empty", "the pad has no power");
		lang.put("message.robots.problem.no_chest_room", "no chest with room nearby");
		lang.put("message.robots.problem.needs_seeds", "needs seeds");
		lang.put("message.robots.problem.needs_hoe", "needs a hoe");
		lang.put("message.robots.problem.cant_reach", "something is out of reach");
		lang.put("message.robots.problem.no_animals", "no pair of animals ready to breed");
		lang.put("message.robots.problem.animal_limit", "animal limit reached");
		lang.put("message.robots.problem.no_food", "no breeding food in the chests");
		lang.put("message.robots.problem.needs_two_chests", "needs an input chest and a destination chest");
		lang.put("message.robots.problem.no_destination", "no chest takes these items");
		// Pages for the Voltworks Guide, from tools/guide.txt: pages are separated by a line of three dashes
		Path guide = res.toAbsolutePath().getParent().getParent().getParent().resolve("tools/guide.txt");
		String[] pages = Files.readString(guide).replace("\r\n", "\n").strip().split("\n---\n");
		for (int i = 0; i < pages.length; i++) lang.put("guide." + NS + ".page." + (i + 1), pages[i].strip());
		StringBuilder json = new StringBuilder("{\n");
		Iterator<Map.Entry<String, String>> it = lang.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, String> e = it.next();
			json.append("  \"").append(e.getKey()).append("\": \"").append(e.getValue().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append(it.hasNext() ? "\",\n" : "\"\n");
		}
		write("assets/" + NS + "/lang/en_us.json", json.append("}").toString());
	}

	// ---------------- helpers ----------------
	static void itemModel(String name) throws Exception {
		write("assets/" + NS + "/models/item/" + name + ".json",
				"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"" + NS + ":item/" + name + "\" } }");
		write("assets/" + NS + "/items/" + name + ".json", "{ \"model\": { \"type\": \"minecraft:model\", \"model\": \"" + NS + ":item/" + name + "\" } }");
	}

	static void face(BufferedImage img, int u, int v, int w, int h, Painter painter) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) img.setRGB(u + x, v + y, painter.at(x, y));
		}
	}

	static void fill(BufferedImage img, int x0, int y0, int w, int h, int color) {
		face(img, x0, y0, w, h, (x, y) -> color);
	}

	/** A dark one-pixel outline around everything drawn so far, so icons read on any background. */
	static void outline(BufferedImage img) {
		BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		copy.getGraphics().drawImage(img, 0, 0, null);
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if ((copy.getRGB(x, y) >>> 24) != 0) continue;
				boolean near = false;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = x + d[0], ny = y + d[1];
					if (nx >= 0 && ny >= 0 && nx < img.getWidth() && ny < img.getHeight() && (copy.getRGB(nx, ny) >>> 24) != 0) near = true;
				}
				if (near) img.setRGB(x, y, rgb(34, 38, 48));
			}
		}
	}

	static int rgb(int r, int g, int b) {
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	static int shade(int color, double f) {
		return rgb((int) (((color >> 16) & 255) * f), (int) (((color >> 8) & 255) * f), (int) ((color & 255) * f));
	}

	static int brighten(int color, int add) {
		return rgb(Math.min(255, ((color >> 16) & 255) + add), Math.min(255, ((color >> 8) & 255) + add), Math.min(255, (color & 255) + add));
	}

	static void save(BufferedImage img, String path) throws Exception {
		File file = res.resolve(path).toFile();
		file.getParentFile().mkdirs();
		ImageIO.write(img, "png", file);
	}

	static void write(String path, String content) throws Exception {
		Path file = res.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content + "\n");
	}
}
