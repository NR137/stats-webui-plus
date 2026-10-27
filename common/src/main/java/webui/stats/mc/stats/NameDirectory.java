package webui.stats.mc.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import webui.stats.mc.McStatsWebui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NameDirectory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
	private static final long RETRY_AFTER_MS = Duration.ofHours(1).toMillis();
	// Mojang lookups run here so the server thread never waits on the network.
	private static final ExecutorService LOOKUPS = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, McStatsWebui.MOD_ID + "-names");
		thread.setDaemon(true);
		return thread;
	});
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> FAILED_AT = new ConcurrentHashMap<>();
	private final Map<UUID, String> names = new HashMap<>();

	public void put(UUID uuid, String name) {
		if (name != null && !name.isBlank()) {
			names.put(uuid, name);
		}
	}

	public String get(UUID uuid) {
		String name = names.get(uuid);
		if (name != null) {
			return name;
		}
		return uuid.toString().substring(0, 8);
	}

	public boolean knows(UUID uuid) {
		return names.containsKey(uuid);
	}

	public void loadLocal(Path gameDir) {
		readUuidNameList(gameDir.resolve("usercache.json"));
		readUuidNameList(gameDir.resolve("whitelist.json"));
		readUuidNameList(gameDir.resolve("ops.json"));
		Path cache = cacheFile();
		if (Files.isRegularFile(cache)) {
			try (Reader reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
				JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
					try {
						put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
					} catch (Exception ignored) {
					}
				}
			} catch (Exception e) {
				McStatsWebui.LOGGER.warn("Could not read name cache", e);
			}
		}
	}

	/**
	 * Queues Mojang lookups for unknown names and returns immediately.
	 * Resolved names are cached on disk and pushed to {@link StatsCache#onNameResolved}.
	 */
	public void resolveMissingAsync(Set<UUID> uuids) {
		long now = System.currentTimeMillis();
		for (UUID uuid : uuids) {
			// Offline-mode UUIDs are version 3; Mojang only knows version 4 (real accounts).
			if (knows(uuid) || uuid.version() != 4) {
				continue;
			}
			Long failedAt = FAILED_AT.get(uuid);
			if (failedAt != null && now - failedAt < RETRY_AFTER_MS) {
				continue;
			}
			if (PENDING.add(uuid)) {
				LOOKUPS.execute(() -> lookup(uuid));
			}
		}
	}

	private static void lookup(UUID uuid) {
		try {
			String name = fetchMojang(uuid);
			if (name == null) {
				FAILED_AT.put(uuid, System.currentTimeMillis());
				return;
			}
			FAILED_AT.remove(uuid);
			persist(uuid, name);
			StatsCache.get().onNameResolved(uuid, name);
		} finally {
			PENDING.remove(uuid);
		}
	}

	// Only called from the LOOKUPS thread, so there is a single writer.
	private static void persist(UUID uuid, String name) {
		Path cache = cacheFile();
		try {
			JsonObject object = new JsonObject();
			if (Files.isRegularFile(cache)) {
				try (Reader reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
					object = JsonParser.parseReader(reader).getAsJsonObject();
				} catch (Exception e) {
					McStatsWebui.LOGGER.warn("Could not read name cache, rewriting it", e);
				}
			}
			object.addProperty(uuid.toString(), name);
			Files.createDirectories(cache.getParent());
			// Write to a temp file and swap it in, so readers never see a half-written file.
			Path tmp = cache.resolveSibling(cache.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(object, writer);
			}
			Files.move(tmp, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			McStatsWebui.LOGGER.warn("Could not save name cache", e);
		}
	}

	private void readUuidNameList(Path path) {
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonArray()) {
				return;
			}
			JsonArray array = root.getAsJsonArray();
			for (JsonElement element : array) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject object = element.getAsJsonObject();
				if (!object.has("uuid") || !object.has("name")) {
					continue;
				}
				try {
					put(UUID.fromString(object.get("uuid").getAsString()), object.get("name").getAsString());
				} catch (Exception ignored) {
				}
			}
		} catch (Exception e) {
			McStatsWebui.LOGGER.warn("Could not read {}", path.getFileName(), e);
		}
	}

	private static String fetchMojang(UUID uuid) {
		String compact = uuid.toString().replace("-", "");
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compact))
			.timeout(Duration.ofSeconds(6))
			.header("User-Agent", McStatsWebui.MOD_ID)
			.GET()
			.build();
		try {
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
				return null;
			}
			JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
			if (!object.has("name")) {
				return null;
			}
			return object.get("name").getAsString();
		} catch (Exception e) {
			McStatsWebui.LOGGER.warn("Could not resolve name for {}", uuid);
			return null;
		}
	}

	private static Path cacheFile() {
		return McStatsWebui.configFile("-names.json");
	}
}
