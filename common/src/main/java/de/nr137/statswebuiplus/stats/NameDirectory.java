package de.nr137.statswebuiplus.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.web.StatsHttpServer;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Player names by UUID: from the server's own files, a cache file of our own (the server forgets players after a
 * while), and lookups at Mojang (Java players) or GeyserMC (Bedrock players) that run in the background, so a page
 * never waits for them. Until a name is known, the start of the UUID stands in for it.
 */
public final class NameDirectory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long RETRY_AFTER_MS = 3_600_000;
	private static final int FLOODGATE_MAX_NAME = 16;
	private static final String FLOODGATE_PREFIX = ".";
	private static final List<String> SERVER_FILES = List.of("usercache.json", "whitelist.json", "ops.json");

	static volatile String profileBase = "https://sessionserver.mojang.com/session/minecraft/profile/";
	static volatile String gamertagBase = "https://api.geysermc.org/v2/xbox/gamertag/";
	static volatile LongSupplier clock = System::currentTimeMillis;

	// One lookup thread for the whole server: lookups of every directory share it, so a name is asked once at a time.
	private static final ExecutorService LOOKUPS = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, StatsWebuiPlus.MOD_ID + "-names");
		thread.setDaemon(true);
		return thread;
	});
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

	/**
	 * Waits until the lookups handed over so far are done, the cache file saved (tests: nothing may write into a
	 * folder that is being removed). One thread runs them in order, so an empty task done means they are too.
	 */
	static void awaitLookups() throws Exception {
		LOOKUPS.submit(() -> { }).get(30, TimeUnit.SECONDS);
	}
	private static final Map<UUID, Long> FAILED = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

	private final Map<UUID, String> names = new ConcurrentHashMap<>();

	/** Ignores blank names: a name to show is better than none. */
	public void put(UUID uuid, String name) {
		if (uuid != null && name != null && !name.isBlank()) {
			names.put(uuid, name);
		}
	}

	/** The player's name, or the start of their UUID (the end for a Bedrock player) while it is unknown. */
	public String get(UUID uuid) {
		String name = names.get(uuid);
		if (name != null) {
			return name;
		}
		String text = uuid.toString();
		return isFloodgate(uuid) ? text.substring(text.length() - 8) : text.substring(0, 8);
	}

	public boolean knows(UUID uuid) {
		return names.containsKey(uuid);
	}

	/**
	 * Floodgate's UUIDs of Bedrock players: zeros in the upper half and the Xbox ID (XUID) in the lower one. Their
	 * starts are all the same, so their ends tell them apart.
	 */
	public static boolean isFloodgate(UUID uuid) {
		return uuid.getMostSignificantBits() == 0;
	}

	/**
	 * Names from the game folder: our cache file first, then the server's usercache.json, whitelist.json and ops.json
	 * (later ones win: the server's files are fresher). What the server knows and our cache doesn't is added to it:
	 * the server forgets players after a while, and Bedrock or offline-mode names can't be asked again.
	 */
	public void loadLocal(Path gameDir) {
		Map<UUID, String> cached = readCache();
		names.putAll(cached);
		Map<UUID, String> server = new HashMap<>();
		for (String file : SERVER_FILES) {
			readServerFile(gameDir.resolve(file), server);
		}
		names.putAll(server);
		Map<UUID, String> news = new HashMap<>();
		server.forEach((uuid, name) -> {
			if (!name.equals(cached.get(uuid))) {
				news.put(uuid, name);
			}
		});
		if (!news.isEmpty()) {
			LOOKUPS.execute(() -> writeCache(news));
		}
	}

	/**
	 * Queues a lookup of each unknown name, without waiting for it. Java players are only asked about if their UUID
	 * is a real account's (version 4; offline-mode UUIDs are made from the name), Bedrock players only if the config
	 * allows GeyserMC. A pending lookup isn't queued again, a failed one only after an hour.
	 */
	public void resolveMissingAsync(Set<UUID> uuids) {
		Boolean geyser = null;
		for (UUID uuid : uuids) {
			if (uuid == null || knows(uuid)) {
				continue;
			}
			if (isFloodgate(uuid)) {
				if (geyser == null) {
					geyser = StatsHttpServer.getInstance().config().geyserApi();
				}
				if (!geyser) {
					continue;
				}
			} else if (uuid.version() != 4) {
				continue;
			}
			Long failed = FAILED.get(uuid);
			if (failed != null && clock.getAsLong() - failed < RETRY_AFTER_MS) {
				continue;
			}
			if (!PENDING.add(uuid)) {
				continue;
			}
			LOOKUPS.execute(() -> resolve(uuid));
		}
	}

	/**
	 * A Bedrock player's name as Floodgate makes it: its prefix (from its config, {@code .} by default) and the gamertag,
	 * cut to 16 characters in all, spaces as {@code _} unless Floodgate is told not to.
	 */
	static String floodgateName(String gamertag) {
		String prefix = FLOODGATE_PREFIX;
		boolean replaceSpaces = true;
		Path config = floodgateConfig();
		if (config != null && Files.isRegularFile(config)) {
			try {
				for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
					String value;
					if ((value = yamlValue(line, "username-prefix")) != null) {
						prefix = value;
					} else if ((value = yamlValue(line, "replace-spaces")) != null) {
						replaceSpaces = !value.equalsIgnoreCase("false");
					}
				}
			} catch (IOException e) {
				StatsWebuiPlus.LOGGER.debug("Could not read {}", config, e);
			}
		}
		// Floodgate refuses a prefix that leaves no room for the name and uses its default.
		if (prefix.length() >= FLOODGATE_MAX_NAME) {
			prefix = FLOODGATE_PREFIX;
		}
		String name = gamertag.length() > FLOODGATE_MAX_NAME - prefix.length()
			? gamertag.substring(0, FLOODGATE_MAX_NAME - prefix.length())
			: gamertag;
		return prefix + (replaceSpaces ? name.replace(' ', '_') : name);
	}

	private void resolve(UUID uuid) {
		try {
			String name = isFloodgate(uuid) ? gamertag(uuid) : mojangName(uuid);
			if (name == null) {
				FAILED.put(uuid, clock.getAsLong());
				return;
			}
			FAILED.remove(uuid);
			put(uuid, name);
			writeCache(Map.of(uuid, name));
			StatsCache.get().onNameResolved(uuid, name);
		} catch (Exception e) {
			StatsWebuiPlus.LOGGER.debug("Name lookup for {} failed: {}", uuid, e.toString());
			FAILED.put(uuid, clock.getAsLong());
		} finally {
			PENDING.remove(uuid);
		}
	}

	private static String mojangName(UUID uuid) throws IOException, InterruptedException {
		JsonObject profile = fetch(profileBase + uuid.toString().replace("-", ""));
		return text(profile, "name");
	}

	private static String gamertag(UUID uuid) throws IOException, InterruptedException {
		JsonObject answer = fetch(gamertagBase + Long.toUnsignedString(uuid.getLeastSignificantBits()));
		String gamertag = text(answer, "gamertag");
		return gamertag == null ? null : floodgateName(gamertag);
	}

	/** The JSON object of a 200 answer; null for any other status or an empty body. */
	private static JsonObject fetch(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(6))
			.header("User-Agent", StatsWebuiPlus.MOD_ID)
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
			return null;
		}
		JsonElement body = JsonParser.parseString(response.body());
		return body.isJsonObject() ? body.getAsJsonObject() : null;
	}

	private static String text(JsonObject json, String key) {
		if (json == null) {
			return null;
		}
		JsonElement value = json.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return null;
		}
		String text = value.getAsString();
		return text.isBlank() ? null : text;
	}

	private static Path cacheFile() {
		return StatsWebuiPlus.configFile("-names.json");
	}

	private static Path floodgateConfig() {
		try {
			return StatsWebuiPlus.platform().configDir().resolve("floodgate").resolve("config.yml");
		} catch (IllegalStateException e) {
			return null;
		}
	}

	/** {@code key: value # comment} -> value, unquoted; null if the line sets another key. */
	private static String yamlValue(String line, String key) {
		String trimmed = line.trim();
		if (!trimmed.startsWith(key + ":")) {
			return null;
		}
		String value = trimmed.substring(key.length() + 1);
		int comment = value.indexOf(" #");
		if (comment >= 0) {
			value = value.substring(0, comment);
		}
		value = value.trim();
		if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
			value = value.substring(1, value.length() - 1);
		}
		return value;
	}

	private static Map<UUID, String> readCache() {
		Map<UUID, String> cached = new HashMap<>();
		Path file = cacheFile();
		if (!Files.isRegularFile(file)) {
			return cached;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				StatsWebuiPlus.LOGGER.warn("Could not read {}: not a JSON object", file);
				return cached;
			}
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
				UUID uuid = uuid(entry.getKey());
				JsonElement name = entry.getValue();
				if (uuid != null && name.isJsonPrimitive() && !name.getAsString().isBlank()) {
					cached.put(uuid, name.getAsString());
				}
			}
		} catch (Exception e) {
			StatsWebuiPlus.LOGGER.warn("Could not read {}: {}", file, e.toString());
		}
		return cached;
	}

	/** One of the server's files: an array of {@code {"uuid": ..., "name": ...}}. */
	private static void readServerFile(Path file, Map<UUID, String> into) {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonArray()) {
				return;
			}
			for (JsonElement entry : root.getAsJsonArray()) {
				if (!entry.isJsonObject()) {
					continue;
				}
				JsonObject player = entry.getAsJsonObject();
				UUID uuid = player.get("uuid") != null && player.get("uuid").isJsonPrimitive() ? uuid(player.get("uuid").getAsString()) : null;
				String name = text(player, "name");
				if (uuid != null && name != null) {
					into.put(uuid, name);
				}
			}
		} catch (Exception e) {
			StatsWebuiPlus.LOGGER.debug("Could not read {}: {}", file, e.toString());
		}
	}

	/**
	 * Merges names into the cache file. Only ever called on the lookup thread, so two writes can't interleave; written
	 * to a temporary file first and moved over the old one, so nobody reads it half-written.
	 */
	private static void writeCache(Map<UUID, String> news) {
		Path file = cacheFile();
		Map<String, String> body = new TreeMap<>();
		readCache().forEach((uuid, name) -> body.put(uuid.toString(), name));
		news.forEach((uuid, name) -> body.put(uuid.toString(), name));
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(body, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			StatsWebuiPlus.LOGGER.warn("Could not save {}: {}", file, e.toString());
		}
	}

	private static UUID uuid(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
