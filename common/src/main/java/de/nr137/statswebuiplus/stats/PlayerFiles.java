package de.nr137.statswebuiplus.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.nr137.statswebuiplus.StatsWebuiPlus;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A world's player files: {@code <uuid>.json} with each player's statistics and their advancements, read into
 * {@link PlayerRecord}s, and the few facts about them the session log needs.
 */
public final class PlayerFiles {
	private static final String JSON = ".json";

	/** Advancements file -> its oldest criterion time, as of the file's last modification. */
	private static final Map<Path, FirstActivity> FIRST_ACTIVITY = new ConcurrentHashMap<>();

	private record FirstActivity(long modified, long time) {
	}

	private PlayerFiles() {
	}

	/** {@code players/stats/} (current versions) or {@code stats/} (older ones), whichever exists; null for no world. */
	public static Path statsDirectory(Path worldRoot) {
		return directory(worldRoot, "stats");
	}

	/** {@code players/advancements/} or {@code advancements/}, like {@link #statsDirectory}. */
	public static Path advancementsDirectory(Path worldRoot) {
		return directory(worldRoot, "advancements");
	}

	/**
	 * Every player of the world, ranked: each {@code <uuid>.json} in the stats folder that is a JSON object, with the
	 * player's advancements. Names the directory doesn't know yet are looked up in the background meanwhile.
	 */
	public static List<PlayerRecord> load(Path worldRoot, Map<UUID, String> knownNames) {
		return load(worldRoot, nameDirectory(knownNames));
	}

	static List<PlayerRecord> load(Path worldRoot, NameDirectory names) {
		Path stats = statsDirectory(worldRoot);
		if (stats == null || !Files.isDirectory(stats)) {
			return List.of();
		}
		Map<UUID, StatFile> files = new LinkedHashMap<>();
		try (DirectoryStream<Path> listing = Files.newDirectoryStream(stats, "*" + JSON)) {
			for (Path file : listing) {
				UUID uuid = uuidOf(file);
				if (uuid == null) {
					continue;
				}
				StatFile statFile = readStats(file);
				if (statFile != null) {
					files.put(uuid, statFile);
				}
			}
		} catch (IOException | RuntimeException e) {
			StatsWebuiPlus.LOGGER.error("Could not list the player stats in {}", stats, e);
			return List.of();
		}

		Set<UUID> unknown = new LinkedHashSet<>();
		for (UUID uuid : files.keySet()) {
			if (!names.knows(uuid)) {
				unknown.add(uuid);
			}
		}
		if (!unknown.isEmpty()) {
			names.resolveMissingAsync(unknown);
		}

		Path advancements = advancementsDirectory(worldRoot);
		List<PlayerRecord> players = new ArrayList<>(files.size());
		files.forEach((uuid, statFile) -> players.add(withAdvancements(
			PlayerMetrics.compute(uuid, names.get(uuid), statFile),
			AdvancementProgressFile.read(advancements.resolve(uuid + JSON))
		)));
		return Skills.apply(players);
	}

	/** One player read from their files, ranked; null if their stats file is missing or unreadable. */
	static PlayerRecord loadOne(Path worldRoot, UUID uuid, NameDirectory names) {
		Path stats = statsDirectory(worldRoot);
		if (stats == null) {
			return null;
		}
		Path file = stats.resolve(uuid + JSON);
		if (!Files.isRegularFile(file)) {
			return null;
		}
		StatFile statFile = readStats(file);
		if (statFile == null) {
			return null;
		}
		if (!names.knows(uuid)) {
			names.resolveMissingAsync(Set.of(uuid));
		}
		PlayerRecord player = withAdvancements(
			PlayerMetrics.compute(uuid, names.get(uuid), statFile),
			AdvancementProgressFile.read(advancementsDirectory(worldRoot).resolve(uuid + JSON))
		);
		return Skills.apply(List.of(player)).getFirst();
	}

	/**
	 * A player whose statistics were copied out of the running game. Their advancements come from the live progress
	 * if it was copied too (it only is after it changed); otherwise they are the previous record's.
	 */
	static PlayerRecord fromMemory(
		UUID uuid,
		NameDirectory names,
		StatFile stats,
		AdvancementProgressFile advancements,
		PlayerRecord previous
	) {
		PlayerRecord player = PlayerMetrics.compute(uuid, names.get(uuid), stats);
		if (advancements != null) {
			return withAdvancements(player, advancements);
		}
		if (previous != null) {
			return player.withAdvancements(previous.advancements(), previous.advancementTimes(), previous.advancementCriteria());
		}
		return player;
	}

	/** The local names (game folder) with the known names on top. */
	static NameDirectory nameDirectory(Map<UUID, String> knownNames) {
		NameDirectory names = new NameDirectory();
		names.loadLocal(StatsWebuiPlus.platform().gameDir());
		if (knownNames != null) {
			knownNames.forEach(names::put);
		}
		return names;
	}

	static boolean hasStatsFile(Path worldRoot, UUID uuid) {
		Path stats = statsDirectory(worldRoot);
		return stats != null && Files.isRegularFile(stats.resolve(uuid + JSON));
	}

	/**
	 * The oldest criterion time in the player's advancements file, close to their first join (recipes are unlocked in
	 * the first minutes); 0 if none. Parsed again only after the game saved the file.
	 */
	static long firstActivity(Path worldRoot, UUID uuid) {
		Path directory = advancementsDirectory(worldRoot);
		if (directory == null) {
			return 0;
		}
		Path file = directory.resolve(uuid + JSON);
		long modified = modified(file);
		if (modified == 0) {
			FIRST_ACTIVITY.remove(file);
			return 0;
		}
		FirstActivity known = FIRST_ACTIVITY.get(file);
		if (known != null && known.modified() == modified) {
			return known.time();
		}
		long time = AdvancementProgressFile.read(file).firstActivityAt;
		FIRST_ACTIVITY.put(file, new FirstActivity(modified, time));
		return time;
	}

	/** When the game last saved the player's stats or advancements file (the later one); 0 if neither exists. */
	static long lastSaved(Path worldRoot, UUID uuid) {
		Path stats = statsDirectory(worldRoot);
		Path advancements = advancementsDirectory(worldRoot);
		if (stats == null || advancements == null) {
			return 0;
		}
		return Math.max(modified(stats.resolve(uuid + JSON)), modified(advancements.resolve(uuid + JSON)));
	}

	/** The UUID a player file is named after ({@code <uuid>.json}); null for any other file. */
	public static UUID uuidOf(Path file) {
		if (file == null || file.getFileName() == null) {
			return null;
		}
		String name = file.getFileName().toString();
		if (!name.endsWith(JSON)) {
			return null;
		}
		String base = name.substring(0, name.length() - JSON.length());
		try {
			UUID uuid = UUID.fromString(base);
			// UUID.fromString also takes shortened forms ("1-2-3-4-5"); the game only writes the full one.
			return uuid.toString().equalsIgnoreCase(base) ? uuid : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * The player with these advancements: only those the server has count (all while the catalog is still empty),
	 * finished ones with their completion time and the criteria times of those with several, times above 0 only.
	 */
	private static PlayerRecord withAdvancements(PlayerRecord player, AdvancementProgressFile progress) {
		Set<String> known = AdvancementCatalog.get().ids();
		boolean all = known.isEmpty();
		Set<String> done = new LinkedHashSet<>();
		Map<String, Long> times = new LinkedHashMap<>();
		for (String id : progress.done) {
			if (!all && !known.contains(id)) {
				continue;
			}
			done.add(id);
			Long time = progress.obtainedAt.get(id);
			if (time != null && time > 0) {
				times.put(id, time);
			}
		}
		Map<String, Map<String, Long>> criteria = new LinkedHashMap<>();
		progress.criteria.forEach((id, met) -> {
			if (!all && !known.contains(id)) {
				return;
			}
			Map<String, Long> kept = new LinkedHashMap<>();
			met.forEach((criterion, time) -> {
				if (time != null && time > 0) {
					kept.put(criterion, time);
				}
			});
			if (!kept.isEmpty()) {
				criteria.put(id, kept);
			}
		});
		return player.withAdvancements(done, times, criteria);
	}

	private static StatFile readStats(Path file) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			return root.isJsonObject() ? new StatFile(root.getAsJsonObject()) : null;
		} catch (Exception e) {
			StatsWebuiPlus.LOGGER.warn("Could not read stats file {}: {}", file.getFileName(), e.toString());
			return null;
		}
	}

	private static Path directory(Path worldRoot, String kind) {
		if (worldRoot == null) {
			return null;
		}
		Path current = worldRoot.resolve("players").resolve(kind);
		if (Files.isDirectory(current)) {
			return current;
		}
		Path older = worldRoot.resolve(kind);
		return Files.isDirectory(older) ? older : current;
	}

	private static long modified(Path file) {
		try {
			return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
		} catch (IOException e) {
			return 0;
		}
	}
}
