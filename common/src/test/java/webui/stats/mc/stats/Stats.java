package webui.stats.mc.stats;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Builds a player's stats file the way Minecraft writes {@code <world>/stats/<uuid>.json}. */
final class Stats {
	static final long TICKS_PER_HOUR = 72_000;

	private final JsonObject stats = new JsonObject();

	static Stats player() {
		return new Stats();
	}

	Stats add(String type, String key, long value) {
		String group = "minecraft:" + type;
		if (!stats.has(group)) {
			stats.add(group, new JsonObject());
		}
		JsonObject values = stats.getAsJsonObject(group);
		String id = "minecraft:" + key;
		long previous = values.has(id) ? values.get(id).getAsLong() : 0;
		values.addProperty(id, previous + value);
		return this;
	}

	Stats hours(double hours) {
		return add("custom", "play_time", Math.round(hours * TICKS_PER_HOUR));
	}

	Stats custom(String key, long value) {
		return add("custom", key, value);
	}

	Stats killed(String mob, long value) {
		return add("killed", mob, value);
	}

	Stats mined(String block, long value) {
		return add("mined", block, value);
	}

	Stats crafted(String item, long value) {
		return add("crafted", item, value);
	}

	/** A bit of everything the ranking looks at, so every area has a value. */
	Stats allRounder(int factor) {
		return hours(10 * factor)
			.killed("zombie", 50L * factor)
			.killed("skeleton", 50L * factor)
			.killed("ender_dragon", 1)
			.custom("raid_win", factor)
			.custom("enchant_item", 5L * factor)
			.mined("diamond_ore", 20L * factor)
			.mined("iron_ore", 100L * factor)
			.mined("ancient_debris", 4L * factor)
			.custom("walk_one_cm", 2_000_000L * factor)
			.custom("sprint_one_cm", 1_000_000L * factor)
			.custom("open_chest", 50L * factor)
			.custom("animals_bred", 30L * factor)
			.custom("fish_caught", 10L * factor)
			.custom("traded_with_villager", 20L * factor)
			.mined("wheat", 200L * factor)
			.custom("deaths", 2);
	}

	JsonObject json() {
		JsonObject root = new JsonObject();
		root.add("stats", stats.deepCopy());
		root.addProperty("DataVersion", 4000);
		return root;
	}

	PlayerRecord compute(String name) {
		return PlayerMetrics.compute(UUID.nameUUIDFromBytes(name.getBytes()), name, new StatFile(json()));
	}
}
