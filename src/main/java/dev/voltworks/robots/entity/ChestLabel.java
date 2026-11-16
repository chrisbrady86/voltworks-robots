package dev.voltworks.robots.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * What a chest is for, read from the signs and item frames on it. The Organizer Robot sorts by these.
 *
 * <p>A sign counts when it hangs on a side of the chest or stands on top of it. Each line is one label:
 * <ul>
 * <li>{@code input}: this is the chest to sort out of.</li>
 * <li>{@code misc}, {@code other}, {@code rest}, {@code everything} or {@code *}: takes whatever has no other home.</li>
 * <li>{@code @voltworks}: every item of that mod.</li>
 * <li>{@code #c:ores}: every item in that item tag.</li>
 * <li>anything else, like {@code ores}, {@code iron}, {@code oak log}, {@code wood}, {@code food}: items whose name
 * contains those words, or that are in an item tag of that name.</li>
 * </ul>
 * An item frame on the chest adds the exact item it shows.
 */
public record ChestLabel(boolean input, boolean catchAll, List<String> terms, List<Item> exact) {
	public static final ChestLabel NONE = new ChestLabel(false, false, List.of(), List.of());

	private static final Set<String> CATCH_ALL = Set.of("*", "misc", "other", "others", "rest", "everything", "anything");
	/** Everyday words that mean several kinds of item. */
	private static final Map<String, List<String>> ALIASES = Map.of(
			"wood", List.of("wood", "log", "plank"),
			"metal", List.of("ingot", "nugget", "raw_material"),
			"stone", List.of("stone", "cobblestone"),
			"plant", List.of("seed", "sapling", "flower", "crop"));
	private static final String[] TAG_NAMESPACES = {"minecraft", "c"};

	/** True when the chest has any sign text or item frame saying what goes in it. */
	public boolean restricts() {
		return catchAll || !terms.isEmpty() || !exact.isEmpty();
	}

	/** 0 = named by an item frame, 1 = named by a sign, -1 = not named here (the catch-all does not count). */
	public int match(ItemStack stack) {
		if (exact.contains(stack.getItem())) {
			return 0;
		}
		for (String term : terms) {
			if (matches(term, stack)) {
				return 1;
			}
		}
		return -1;
	}

	// ---------------- reading ----------------

	public static ChestLabel read(ServerLevel level, BlockPos chestPos) {
		BlockState state = level.getBlockState(chestPos);
		List<BlockPos> halves = new ArrayList<>(List.of(chestPos));
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			halves.add(ChestBlock.getConnectedBlockPos(chestPos, state));
		}
		boolean input = false;
		boolean catchAll = false;
		List<String> terms = new ArrayList<>();
		List<Item> exact = new ArrayList<>();
		for (BlockPos half : halves) {
			for (String line : signLines(level, half)) {
				if (line.equals("input") || line.equals("in")) {
					input = true;
				} else if (CATCH_ALL.contains(line)) {
					catchAll = true;
				} else {
					terms.add(line);
				}
			}
			for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, new AABB(half).inflate(1.0))) {
				if (!frame.getItem().isEmpty() && frame.getPos().relative(frame.getDirection().getOpposite()).equals(half)) {
					exact.add(frame.getItem().getItem());
				}
			}
		}
		if (!input && !catchAll && terms.isEmpty() && exact.isEmpty()) {
			return NONE;
		}
		return new ChestLabel(input, catchAll, List.copyOf(terms), List.copyOf(exact));
	}

	/** Lower-case, trimmed, non-empty lines of every sign on this chest block. */
	private static List<String> signLines(ServerLevel level, BlockPos chest) {
		List<String> lines = new ArrayList<>();
		for (Direction side : Direction.values()) {
			if (side == Direction.DOWN) {
				continue;
			}
			BlockPos pos = chest.relative(side);
			if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			// On the sides only signs hanging on the chest itself count; on top any sign does
			if (side != Direction.UP && !(state.getBlock() instanceof WallSignBlock && state.getValue(WallSignBlock.FACING) == side)) {
				continue;
			}
			for (SignTextSlot slot : SignTextSlot.values()) {
				for (Component message : sign.getText(slot).getMessages(false)) {
					String line = message.getString().trim().toLowerCase(Locale.ROOT);
					if (!line.isEmpty()) {
						lines.add(line);
					}
				}
			}
		}
		return lines;
	}

	// ---------------- matching ----------------

	static boolean matches(String term, ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (term.startsWith("@")) {
			return id.getNamespace().equals(term.substring(1).trim());
		}
		if (term.startsWith("#")) {
			Identifier tag = Identifier.tryParse(term.substring(1).trim());
			return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
		}
		String name = term.replaceAll("[\\s-]+", "_");
		for (String word : ALIASES.getOrDefault(singular(name), List.of(name))) {
			if (inTagNamed(word, stack) || containsWords(id.getPath(), word)) {
				return true;
			}
		}
		return false;
	}

	/** An item tag called this (as written, singular or plural) in the vanilla or common namespace. */
	private static boolean inTagNamed(String name, ItemStack stack) {
		for (String variant : new java.util.LinkedHashSet<>(List.of(name, singular(name), singular(name) + "s"))) {
			for (String namespace : TAG_NAMESPACES) {
				Identifier tag = Identifier.tryBuild(namespace, variant);
				if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
					return true;
				}
			}
		}
		return false;
	}

	/** True when the item id has these words next to each other: "iron_ore" in "deepslate_iron_ore", "log" in "oak_log". */
	private static boolean containsWords(String path, String words) {
		String[] have = path.split("_");
		String[] want = words.split("_");
		for (int start = 0; start + want.length <= have.length; start++) {
			boolean all = true;
			for (int i = 0; i < want.length && all; i++) {
				all = singular(have[start + i]).equals(singular(want[i]));
			}
			if (all) {
				return true;
			}
		}
		return false;
	}

	/** "ores" -> "ore", "planks" -> "plank"; "glass" stays. Used on both sides of a comparison, so it only has to be consistent. */
	private static String singular(String word) {
		return word.length() > 3 && word.endsWith("s") && !word.endsWith("ss") ? word.substring(0, word.length() - 1) : word;
	}
}
