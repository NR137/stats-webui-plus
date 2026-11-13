package de.nr137.statswebuiplus.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One player's statistics as Minecraft stores them in {@code <world>/stats/<uuid>.json}: groups (statistic types such
 * as {@code minecraft:mined}) of statistic IDs with whole numbers. Lookups never fail; anything missing counts as 0.
 */
public final class StatFile {
	private final Map<String, Map<String, Long>> groups;

	/** From a parsed stats file, {@code {"stats": {group: {id: number}}, "DataVersion": ...}}. */
	public StatFile(JsonObject root) {
		this(parse(root));
	}

	private StatFile(Map<String, Map<String, Long>> groups) {
		this.groups = groups;
	}

	/**
	 * From the game's live statistics (a copy of them: the game changes the live map on the server thread), turned into
	 * the IDs the file would have. A statistic whose type or value has no registry ID can't be saved either; it is left
	 * out.
	 */
	public static StatFile of(Object2IntMap<Stat<?>> stats) {
		Map<String, Map<String, Long>> groups = new LinkedHashMap<>();
		for (Object2IntMap.Entry<Stat<?>> entry : stats.object2IntEntrySet()) {
			Stat<?> stat = entry.getKey();
			Identifier type = BuiltInRegistries.STAT_TYPE.getKey(stat.getType());
			Identifier value = valueId(stat);
			if (type == null || value == null) {
				continue;
			}
			groups.computeIfAbsent(type.toString(), key -> new LinkedHashMap<>()).put(value.toString(), (long) entry.getIntValue());
		}
		return new StatFile(groups);
	}

	public long custom(String key) {
		return value("custom", key);
	}

	public long killed(String key) {
		return value("killed", key);
	}

	public long pickedUp(String key) {
		return value("picked_up", key);
	}

	public long mined(String key) {
		return value("mined", key);
	}

	/** The sum of a whole group. */
	public long sum(String group) {
		long sum = 0;
		for (long value : group(group).values()) {
			sum += value;
		}
		return sum;
	}

	public long sum(String group, Set<String> keys) {
		Map<String, Long> values = group(group);
		long sum = 0;
		for (String key : keys) {
			Long value = values.get(id(key));
			if (value != null) {
				sum += value;
			}
		}
		return sum;
	}

	/** The sum of the statistics of a group whose ID (with namespace) matches. */
	public long sum(String group, Predicate<String> keys) {
		long sum = 0;
		for (Map.Entry<String, Long> entry : group(group).entrySet()) {
			if (keys.test(entry.getKey())) {
				sum += entry.getValue();
			}
		}
		return sum;
	}

	/** A group's statistics, ID -> value, in the file's order. */
	public Map<String, Long> groupValues(String group) {
		return Collections.unmodifiableMap(group(group));
	}

	public long value(String group, String key) {
		Long value = group(group).get(id(key));
		return value == null ? 0 : value;
	}

	private Map<String, Long> group(String group) {
		Map<String, Long> values = groups.get(id(group));
		return values == null ? Map.of() : values;
	}

	/** {@code "Jump"} -> {@code minecraft:jump}; an ID with a namespace stays as it is. */
	private static String id(String name) {
		if (name == null) {
			return "";
		}
		return name.indexOf(':') >= 0 ? name : "minecraft:" + name.toLowerCase(Locale.ROOT);
	}

	private static <T> Identifier valueId(Stat<T> stat) {
		StatType<T> type = stat.getType();
		return type.getRegistry().getKey(stat.getValue());
	}

	private static Map<String, Map<String, Long>> parse(JsonObject root) {
		Map<String, Map<String, Long>> groups = new LinkedHashMap<>();
		JsonElement stats = root == null ? null : root.get("stats");
		if (stats == null || !stats.isJsonObject()) {
			return groups;
		}
		for (Map.Entry<String, JsonElement> group : stats.getAsJsonObject().entrySet()) {
			if (!group.getValue().isJsonObject()) {
				continue;
			}
			Map<String, Long> values = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> stat : group.getValue().getAsJsonObject().entrySet()) {
				// A value that isn't a number counts as 0, like a missing one.
				if (stat.getValue() instanceof JsonPrimitive primitive && primitive.isNumber()) {
					values.put(stat.getKey(), primitive.getAsLong());
				}
			}
			groups.put(group.getKey(), values);
		}
		return groups;
	}
}
