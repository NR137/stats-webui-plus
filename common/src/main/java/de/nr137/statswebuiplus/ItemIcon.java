package de.nr137.statswebuiplus;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.regex.Pattern;

/** The "icon" of a menu entry in the config: a game item ID such as "minecraft:oak_door" (or just "oak_door"). */
final class ItemIcon {
	private static final Pattern ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

	private ItemIcon() {
	}

	/** The entry's icon, with the minecraft: namespace added when it has none; the fallback if missing or invalid. */
	static String parse(JsonObject json, String what, String fallback) {
		if (!json.has("icon") || !json.get("icon").isJsonPrimitive()) {
			return fallback;
		}
		String icon = json.get("icon").getAsString().trim().toLowerCase(Locale.ROOT);
		if (icon.isEmpty()) {
			return fallback;
		}
		if (!ID.matcher(icon).matches()) {
			StatsWebuiPlus.LOGGER.warn("{}: icon must be an item ID like minecraft:oak_door, using {}", what, fallback);
			return fallback;
		}
		return icon.contains(":") ? icon : "minecraft:" + icon;
	}
}
