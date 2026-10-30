package webui.stats.mc.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import webui.stats.mc.McStatsWebui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First join and last logout per player. Minecraft records neither, so the mod writes them from join/leave events to
 * {@code <world>/stats_webui_plus/sessions.json}. For players it hasn't seen join or leave, {@link #times} falls back to
 * estimates from Minecraft's own files.
 */
public final class SessionLog {
	private static final SessionLog INSTANCE = new SessionLog();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Times in epoch milliseconds (UTC); 0 means unknown. */
	public record Times(long firstJoin, boolean firstJoinEstimated, long lastLogout, boolean lastLogoutEstimated, boolean online) {
	}

	// firstJoin is only recorded when exact; 0 means "not recorded".
	private record Entry(long firstJoin, long lastLogout) {
	}

	private static final Entry NONE = new Entry(0, 0);

	private final Object lock = new Object();
	private final Set<UUID> online = ConcurrentHashMap.newKeySet();
	private final Map<UUID, Entry> entries = new HashMap<>();
	private Path worldRoot;
	private Path file;

	private SessionLog() {
	}

	public static SessionLog get() {
		return INSTANCE;
	}

	public void boot(Path worldRoot) {
		synchronized (lock) {
			this.worldRoot = worldRoot;
			this.file = McStatsWebui.worldDataDir(worldRoot).resolve("sessions.json");
			entries.clear();
			online.clear();
			read();
		}
	}

	public void onJoin(UUID uuid) {
		online.add(uuid);
		synchronized (lock) {
			Entry entry = entries.getOrDefault(uuid, NONE);
			// Only a player without saved stats is joining for the first time; anyone else joined before the mod
			// was installed, so this join is not their first one (they keep the estimate).
			if (worldRoot == null || entry.firstJoin() > 0 || StatsService.hasStatsFile(worldRoot, uuid)) {
				return;
			}
			entries.put(uuid, new Entry(System.currentTimeMillis(), entry.lastLogout()));
			save();
		}
	}

	public void onLeave(UUID uuid) {
		online.remove(uuid);
		synchronized (lock) {
			if (worldRoot == null) {
				return;
			}
			Entry entry = entries.getOrDefault(uuid, NONE);
			entries.put(uuid, new Entry(entry.firstJoin(), System.currentTimeMillis()));
			save();
		}
	}

	public Times times(UUID uuid) {
		Path root;
		Entry entry;
		synchronized (lock) {
			root = worldRoot;
			entry = entries.getOrDefault(uuid, NONE);
		}
		long firstJoin = entry.firstJoin();
		long lastLogout = entry.lastLogout();
		boolean firstEstimated = false;
		boolean lastEstimated = false;
		if (root != null && firstJoin == 0) {
			firstJoin = StatsService.firstActivity(root, uuid);
			firstEstimated = firstJoin > 0;
		}
		if (root != null && lastLogout == 0) {
			lastLogout = StatsService.lastSaved(root, uuid);
			lastEstimated = lastLogout > 0;
		}
		return new Times(firstJoin, firstEstimated, lastLogout, lastEstimated, online.contains(uuid));
	}

	private void read() {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				return;
			}
			for (Map.Entry<String, JsonElement> item : root.getAsJsonObject().entrySet()) {
				if (!item.getValue().isJsonObject()) {
					continue;
				}
				JsonObject times = item.getValue().getAsJsonObject();
				try {
					entries.put(UUID.fromString(item.getKey()), new Entry(millis(times, "firstJoin"), millis(times, "lastLogout")));
				} catch (IllegalArgumentException ignored) {
					// not a UUID
				}
			}
		} catch (Exception e) {
			McStatsWebui.LOGGER.warn("Could not read {}", file, e);
		}
	}

	private void save() {
		Map<String, Map<String, Long>> body = new TreeMap<>();
		for (Map.Entry<UUID, Entry> item : entries.entrySet()) {
			Map<String, Long> times = new TreeMap<>();
			if (item.getValue().firstJoin() > 0) {
				times.put("firstJoin", item.getValue().firstJoin());
			}
			if (item.getValue().lastLogout() > 0) {
				times.put("lastLogout", item.getValue().lastLogout());
			}
			body.put(item.getKey().toString(), times);
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(body, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			McStatsWebui.LOGGER.warn("Could not save {}", file, e);
		}
	}

	private static long millis(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0;
	}
}
