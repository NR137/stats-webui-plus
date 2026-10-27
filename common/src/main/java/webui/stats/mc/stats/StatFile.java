package webui.stats.mc.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class StatFile {
	private final JsonObject stats;

	public StatFile(JsonObject root) {
		JsonElement stats = root.get("stats");
		this.stats = stats != null && stats.isJsonObject() ? stats.getAsJsonObject() : new JsonObject();
	}

	/** Stats as Minecraft holds them in memory, grouped the way the stats file stores them. */
	public static StatFile of(Object2IntMap<Stat<?>> values) {
		JsonObject groups = new JsonObject();
		for (Object2IntMap.Entry<Stat<?>> entry : values.object2IntEntrySet()) {
			Stat<?> stat = entry.getKey();
			Identifier type = BuiltInRegistries.STAT_TYPE.getKey(stat.getType());
			Identifier key = key(stat);
			if (type == null || key == null) {
				continue;
			}
			JsonObject group = groups.getAsJsonObject(type.toString());
			if (group == null) {
				group = new JsonObject();
				groups.add(type.toString(), group);
			}
			group.addProperty(key.toString(), entry.getIntValue());
		}
		JsonObject root = new JsonObject();
		root.add("stats", groups);
		return new StatFile(root);
	}

	private static <T> Identifier key(Stat<T> stat) {
		return stat.getType().getRegistry().getKey(stat.getValue());
	}

	public long custom(String name) {
		return value("minecraft:custom", namespaced(name));
	}

	public long killed(String name) {
		return value("minecraft:killed", namespaced(name));
	}

	public long killedBy(String name) {
		return value("minecraft:killed_by", namespaced(name));
	}

	public long pickedUp(String name) {
		return value("minecraft:picked_up", namespaced(name));
	}

	public long mined(String name) {
		return value("minecraft:mined", namespaced(name));
	}

	public long sum(String type) {
		return sum(type, key -> true);
	}

	public long sum(String type, Set<String> keys) {
		return sum(type, keys::contains);
	}

	public long sum(String type, Predicate<String> keys) {
		JsonObject group = group(type);
		if (group == null) {
			return 0;
		}
		long total = 0;
		for (Map.Entry<String, JsonElement> entry : group.entrySet()) {
			if (keys.test(entry.getKey())) {
				total += asLong(entry.getValue());
			}
		}
		return total;
	}

	public Map<String, Long> groupValues(String type) {
		Map<String, Long> values = new java.util.LinkedHashMap<>();
		JsonObject group = group(type);
		if (group == null) {
			return values;
		}
		for (Map.Entry<String, JsonElement> entry : group.entrySet()) {
			values.put(entry.getKey(), asLong(entry.getValue()));
		}
		return values;
	}

	public long value(String type, String key) {
		JsonObject group = group(type);
		if (group == null || !group.has(key)) {
			return 0;
		}
		return asLong(group.get(key));
	}

	private JsonObject group(String type) {
		JsonElement element = stats.get(type);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static long asLong(JsonElement element) {
		try {
			return element.getAsLong();
		} catch (Exception e) {
			return 0;
		}
	}

	private static String namespaced(String name) {
		if (name.indexOf(':') >= 0) {
			return name;
		}
		return "minecraft:" + name.toLowerCase(Locale.ROOT);
	}
}
