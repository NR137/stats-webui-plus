package webui.stats.mc.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import webui.stats.mc.McStatsWebui;
import webui.stats.mc.web.LeaderboardCatalog;
import webui.stats.mc.web.LeaderboardInfo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsService {
	// Advancements file -> {last modified, first activity}: a player page reads it for players the session log never
	// saw, and parsing the file again is only needed after Minecraft saved it.
	private static final Map<Path, long[]> FIRST_ACTIVITY = new ConcurrentHashMap<>();

	private StatsService() {
	}

	public static List<PlayerRecord> load(Path worldRoot, Map<UUID, String> knownNames) {
		NameDirectory names = nameDirectory(knownNames);
		Path directory = statsDirectory(worldRoot);
		List<Path> files = new ArrayList<>();
		if (directory != null && Files.isDirectory(directory)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
				for (Path path : stream) {
					files.add(path);
				}
			} catch (IOException e) {
				McStatsWebui.LOGGER.error("Could not list stats directory {}", directory, e);
			}
		}

		Set<UUID> uuids = new HashSet<>();
		Map<UUID, StatFile> parsed = new HashMap<>();
		for (Path path : files) {
			UUID uuid = uuidFromStatsFile(path);
			if (uuid == null) {
				continue;
			}
			StatFile stats = readStatsFile(path);
			if (stats == null) {
				continue;
			}
			uuids.add(uuid);
			parsed.put(uuid, stats);
		}
		names.resolveMissingAsync(uuids);

		List<PlayerRecord> players = new ArrayList<>();
		for (Map.Entry<UUID, StatFile> entry : parsed.entrySet()) {
			UUID uuid = entry.getKey();
			PlayerRecord player = PlayerMetrics.compute(uuid, names.get(uuid), entry.getValue());
			players.add(withAdvancements(player, readAdvancements(worldRoot, uuid)));
		}
		return ChampionScorer.apply(players);
	}

	static PlayerRecord loadOne(Path worldRoot, UUID uuid, NameDirectory names) {
		Path path = statsFile(worldRoot, uuid);
		if (path == null || !Files.isRegularFile(path)) {
			return null;
		}
		StatFile stats = readStatsFile(path);
		if (stats == null) {
			return null;
		}
		names.resolveMissingAsync(Set.of(uuid));
		PlayerRecord player = PlayerMetrics.compute(uuid, names.get(uuid), stats);
		return withAdvancements(player, readAdvancements(worldRoot, uuid));
	}

	/** A player from stats copied out of memory; without new advancement progress, the previous record's is kept. */
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
		return previous == null ? player : player.withAdvancements(previous.advancements, previous.advancementTimes);
	}

	public static Path statsDirectory(Path worldRoot) {
		if (worldRoot == null) {
			return null;
		}
		List<Path> candidates = new ArrayList<>();
		candidates.add(worldRoot.resolve("players").resolve("stats"));
		candidates.add(worldRoot.resolve("stats"));
		for (Path path : candidates) {
			if (Files.isDirectory(path)) {
				return path;
			}
		}
		return candidates.getFirst();
	}

	static NameDirectory nameDirectory(Map<UUID, String> knownNames) {
		NameDirectory names = new NameDirectory();
		names.loadLocal(McStatsWebui.platform().gameDir());
		for (Map.Entry<UUID, String> entry : knownNames.entrySet()) {
			names.put(entry.getKey(), entry.getValue());
		}
		return names;
	}

	public static Path advancementsDirectory(Path worldRoot) {
		if (worldRoot == null) {
			return null;
		}
		List<Path> candidates = new ArrayList<>();
		candidates.add(worldRoot.resolve("players").resolve("advancements"));
		candidates.add(worldRoot.resolve("advancements"));
		for (Path path : candidates) {
			if (Files.isDirectory(path)) {
				return path;
			}
		}
		return candidates.getFirst();
	}

	private static Path advancementsFile(Path worldRoot, UUID uuid) {
		Path directory = advancementsDirectory(worldRoot);
		if (directory == null) {
			return null;
		}
		return directory.resolve(uuid + ".json");
	}

	private static AdvancementProgressFile readAdvancements(Path worldRoot, UUID uuid) {
		return AdvancementProgressFile.read(advancementsFile(worldRoot, uuid));
	}

	private static PlayerRecord withAdvancements(PlayerRecord player, AdvancementProgressFile progress) {
		Set<String> known = AdvancementCatalog.get().ids();
		Set<String> done = new HashSet<>();
		Map<String, Long> times = new LinkedHashMap<>();
		for (String id : progress.done) {
			if (!known.isEmpty() && !known.contains(id)) {
				continue;
			}
			done.add(id);
			long at = progress.obtainedAt.getOrDefault(id, 0L);
			if (at > 0) {
				times.put(id, at);
			}
		}
		return player.withAdvancements(done, times);
	}

	/** Whether Minecraft has saved stats for this player before, i.e. this is not their first join. */
	static boolean hasStatsFile(Path worldRoot, UUID uuid) {
		Path path = statsFile(worldRoot, uuid);
		return path != null && Files.isRegularFile(path);
	}

	/** Oldest advancement or recipe timestamp of the player; 0 if there is none. */
	static long firstActivity(Path worldRoot, UUID uuid) {
		Path file = advancementsFile(worldRoot, uuid);
		long modified = lastModified(file);
		long[] known = FIRST_ACTIVITY.get(file);
		if (known != null && known[0] == modified) {
			return known[1];
		}
		long first = AdvancementProgressFile.read(file).firstActivityAt;
		if (file != null) {
			FIRST_ACTIVITY.put(file, new long[] {modified, first});
		}
		return first;
	}

	private static long lastModified(Path file) {
		try {
			return file != null && Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
		} catch (IOException e) {
			return 0;
		}
	}

	/** When Minecraft last saved the player's stats or advancements (it saves on logout); 0 if unknown. */
	static long lastSaved(Path worldRoot, UUID uuid) {
		long latest = 0;
		for (Path path : new Path[] {statsFile(worldRoot, uuid), advancementsFile(worldRoot, uuid)}) {
			try {
				if (path != null && Files.isRegularFile(path)) {
					latest = Math.max(latest, Files.getLastModifiedTime(path).toMillis());
				}
			} catch (IOException e) {
				// keep what we have
			}
		}
		return latest;
	}

	private static Path statsFile(Path worldRoot, UUID uuid) {
		Path directory = statsDirectory(worldRoot);
		if (directory == null) {
			return null;
		}
		return directory.resolve(uuid + ".json");
	}

	private static UUID uuidFromStatsFile(Path path) {
		String filename = path.getFileName().toString();
		if (!filename.endsWith(".json")) {
			return null;
		}
		try {
			return UUID.fromString(filename.substring(0, filename.length() - 5));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static StatFile readStatsFile(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			return new StatFile(root);
		} catch (Exception e) {
			McStatsWebui.LOGGER.warn("Could not read stats file {}", path.getFileName(), e);
			return null;
		}
	}

	public static Map<String, Object> playersPayload(List<PlayerRecord> players) {
		List<PlayerRecord> ranked = new ArrayList<>(players);
		ranked.sort(
			Comparator.comparingDouble((PlayerRecord player) -> player.playHours)
				.reversed()
				.thenComparing(player -> player.name, String.CASE_INSENSITIVE_ORDER)
		);
		List<Map<String, Object>> list = new ArrayList<>();
		for (PlayerRecord player : ranked) {
			list.add(playerSummary(player));
		}
		return Map.of("players", list);
	}

	public static Map<String, Object> playerPayload(PlayerRecord player, List<PlayerRecord> players) {
		return playerPayload(player, players, new VanillaRanks(players));
	}

	/** With the ranks of a {@link StatsCache.Snapshot}, shared by all requests until the players change. */
	public static Map<String, Object> playerPayload(PlayerRecord player, List<PlayerRecord> players, VanillaRanks ranks) {
		Map<String, Object> item = playerSummary(player);
		// Epoch milliseconds (UTC); the web UI shows them in the visitor's time zone.
		SessionLog.Times times = SessionLog.get().times(player.uuid);
		item.put("firstJoin", times.firstJoin() > 0 ? times.firstJoin() : null);
		item.put("firstJoinEstimated", times.firstJoinEstimated());
		item.put("lastLogout", times.lastLogout() > 0 ? times.lastLogout() : null);
		item.put("lastLogoutEstimated", times.lastLogoutEstimated());
		item.put("online", times.online());
		item.put("championScore", round(player.championScore));
		item.put("championDisplay", display("score", player.championScore));
		item.put("championRank", rank(players, "champion", player));
		item.put("playHours", round(player.playHours));
		item.put("playHoursDisplay", display("hours", player.playHours));
		item.put("scorePerHour", round(player.scorePerHour));
		item.put("scorePerHourDisplay", display("score_per_hour", player.scorePerHour));
		item.put("trackedPlayers", players.size());
		Map<String, Object> grades = new LinkedHashMap<>();
		for (Map.Entry<String, Double> grade : player.grades.entrySet()) {
			grades.put(grade.getKey(), round(grade.getValue()));
		}
		item.put("grades", grades);
		Map<String, Object> stats = new LinkedHashMap<>();
		for (LeaderboardInfo info : LeaderboardCatalog.all()) {
			double value = player.value(info.id());
			Map<String, Object> stat = new LinkedHashMap<>();
			stat.put("value", round(value));
			stat.put("display", display(info.unit(), value));
			stat.put("unit", info.unit());
			stat.put("category", info.category());
			stat.put("rank", rank(players, info.id(), player));
			stats.put(info.id(), stat);
		}
		item.put("stats", stats);
		Map<String, Object> zeros = new LinkedHashMap<>();
		item.put("vanilla", vanillaPayload(player, ranks, zeros));
		item.put("vanillaZero", zeros);
		item.put("advancements", advancementsPayload(player, players));
		return item;
	}

	private static Map<String, Object> advancementsPayload(PlayerRecord player, List<PlayerRecord> players) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("done", player.advancements.size());
		body.put("total", AdvancementCatalog.get().size());
		body.put("rank", rank(players, "advancements", player));
		List<String> ids = advancementIds(player);
		body.put("ids", ids);
		// Completion time per finished advancement in epoch milliseconds, where Minecraft recorded one.
		Map<String, Long> times = new LinkedHashMap<>();
		for (String id : ids) {
			long at = player.advancementTime(id);
			if (at > 0) {
				times.put(id, at);
			}
		}
		body.put("times", times);
		return body;
	}

	/** The player's advancements in the catalog's order (tab, then title), so the list is the same on every start. */
	private static List<String> advancementIds(PlayerRecord player) {
		List<String> ids = new ArrayList<>(player.advancements.size());
		for (AdvancementInfo info : AdvancementCatalog.get().all()) {
			if (player.advancements.contains(info.id())) {
				ids.add(info.id());
			}
		}
		// Only before the catalog has loaded, or for advancements without a display.
		player.advancements.stream()
			.filter(id -> AdvancementCatalog.get().find(id) == null)
			.sorted()
			.forEach(ids::add);
		return ids;
	}

	/**
	 * The catalog's entries for a stat group plus every ID some player has a non-zero value for. Stats files can
	 * hold IDs the catalog doesn't list (items from a removed mod, renamed IDs, entity types the catalog filters
	 * out); those are never hidden.
	 */
	public static List<String> vanillaKeys(String group, List<PlayerRecord> players) {
		Set<String> keys = new TreeSet<>(VanillaCatalog.keys(group));
		for (PlayerRecord player : players) {
			Map<String, Long> values = player.vanilla.get(group);
			if (values == null) {
				continue;
			}
			for (Map.Entry<String, Long> entry : values.entrySet()) {
				if (entry.getValue() != null && entry.getValue() != 0) {
					keys.add(entry.getKey());
				}
			}
		}
		return List.copyOf(keys);
	}

	/**
	 * Rows of the player's vanilla stats. Most stats are counts the player has never raised; instead of a full row
	 * each, those go into zeros as group -> rank -> namespace -> paths, and the web UI rebuilds the rows from that.
	 */
	private static Map<String, Object> vanillaPayload(
		PlayerRecord player,
		VanillaRanks ranks,
		Map<String, Object> zeros
	) {
		Map<String, Object> body = new LinkedHashMap<>();
		for (String group : VanillaCatalog.GROUPS) {
			List<Map<String, Object>> rows = new ArrayList<>();
			Map<Integer, List<String>> zeroIds = new TreeMap<>();
			for (String id : ranks.keys(group)) {
				long raw = player.vanillaRaw(group, id);
				String unit = vanillaUnit(group, id);
				if (raw == 0 && "count".equals(unit)) {
					zeroIds.computeIfAbsent(ranks.rank(group, id, raw), rank -> new ArrayList<>()).add(id);
					continue;
				}
				double value = vanillaValue(group, id, raw);
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("id", id);
				row.put("value", round(value));
				row.put("display", display(unit, value));
				row.put("unit", unit);
				row.put("rank", ranks.rank(group, id, raw));
				rows.add(row);
			}
			body.put(group, rows);
			Map<Integer, Map<String, List<String>>> grouped = new TreeMap<>();
			zeroIds.forEach((rank, ids) -> grouped.put(rank, byNamespace(ids)));
			zeros.put(group, grouped);
		}
		return body;
	}

	/**
	 * IDs as namespace -> paths ("minecraft" -> ["stone", ...]), so thousands of IDs don't each repeat "minecraft:".
	 * The web UI joins them back together.
	 */
	private static Map<String, List<String>> byNamespace(List<String> ids) {
		Map<String, List<String>> grouped = new TreeMap<>();
		for (String id : ids) {
			int colon = id.indexOf(':');
			grouped.computeIfAbsent(colon < 0 ? "" : id.substring(0, colon), namespace -> new ArrayList<>())
				.add(id.substring(colon + 1));
		}
		return grouped;
	}

	private static String vanillaUnit(String group, String id) {
		if (!"custom".equals(group)) {
			return "count";
		}
		if (id.endsWith("_one_cm")) {
			return "km";
		}
		if (id.contains("damage_")) {
			return "hearts";
		}
		if (id.endsWith("_time") || id.startsWith("minecraft:time_since_") || "minecraft:sneak_time".equals(id)) {
			return "hours";
		}
		return "count";
	}

	private static double vanillaValue(String group, String id, long raw) {
		String unit = vanillaUnit(group, id);
		return switch (unit) {
			case "km" -> raw / 100_000.0;
			case "hours" -> raw / 20.0 / 3600.0;
			case "hearts" -> raw / 20.0;
			default -> raw;
		};
	}

	public static LeaderboardInfo resolve(String path, List<PlayerRecord> players) {
		LeaderboardInfo info = LeaderboardCatalog.find(path);
		if (info != null) {
			return info;
		}
		int slash = path.indexOf('/');
		if (slash <= 0) {
			return null;
		}
		String group = path.substring(0, slash);
		String statId = path.substring(slash + 1);
		if (!VanillaCatalog.isGroup(group) || !vanillaKeys(group, players).contains(statId)) {
			return null;
		}
		return LeaderboardCatalog.vanilla(path, group, vanillaUnit(group, statId));
	}

	public static Map<String, Object> leaderboardPayload(LeaderboardInfo info, List<PlayerRecord> players) {
		List<Map<String, Object>> entries = new ArrayList<>();
		List<PlayerRecord> ranked = new ArrayList<>(players);
		ranked.sort(
			Comparator.comparingDouble((PlayerRecord player) -> boardValue(info, player))
				.reversed()
				.thenComparing(Comparator.comparingDouble((PlayerRecord player) -> player.playHours).reversed())
				.thenComparing(player -> player.name.toLowerCase(Locale.ROOT))
		);
		int rank = 1;
		for (PlayerRecord player : ranked) {
			double value = boardValue(info, player);
			if (value <= 0 && !"champion".equals(info.id())) {
				continue;
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("rank", rank);
			entry.put("name", player.name);
			entry.put("uuid", player.uuid.toString());
			entry.put("value", round(value));
			entry.put("display", display(info.unit(), value));
			entries.add(entry);
			rank += 1;
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", info.id());
		body.put("title", info.id());
		body.put("category", info.category());
		body.put("unit", info.unit());
		body.put("icon", info.icon());
		body.put("lowerWins", info.lowerWins());
		body.put("entries", entries);
		return body;
	}

	public static Map<String, Object> crownsPayload(List<PlayerRecord> players) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("champion", crown(best(players, "champion"), "score"));
		body.put("dedicated", crown(best(players, "dedicated"), "hours"));
		body.put("efficient", crown(best(players, "efficient"), "score_per_hour"));
		return body;
	}

	public static Map<String, Object> catalogPayload(List<PlayerRecord> players) {
		List<Map<String, Object>> boards = new ArrayList<>();
		for (LeaderboardInfo info : LeaderboardCatalog.all()) {
			boards.add(boardPayload(info));
		}
		List<Map<String, Object>> categories = new ArrayList<>();
		for (Map.Entry<String, List<LeaderboardInfo>> entry : LeaderboardCatalog.listedByCategory().entrySet()) {
			List<Map<String, Object>> listed = new ArrayList<>();
			for (LeaderboardInfo info : entry.getValue()) {
				listed.add(boardPayload(info));
			}
			Map<String, Object> category = new LinkedHashMap<>();
			category.put("id", entry.getKey());
			category.put("icon", LeaderboardCatalog.categoryIcon(entry.getKey()));
			category.put("boards", listed);
			categories.add(category);
		}
		Map<String, Object> vanilla = new LinkedHashMap<>();
		for (String group : VanillaCatalog.GROUPS) {
			vanilla.put(group, byNamespace(vanillaKeys(group, players)));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("categories", categories);
		body.put("boards", boards);
		body.put("vanilla", vanilla);
		return body;
	}

	private static Map<String, Object> boardPayload(LeaderboardInfo info) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("id", info.id());
		item.put("category", info.category());
		item.put("unit", info.unit());
		item.put("icon", info.icon());
		item.put("listed", info.listed());
		item.put("compare", info.compare());
		item.put("lowerWins", info.lowerWins());
		return item;
	}

	public static PlayerRecord find(List<PlayerRecord> players, String name) {
		for (PlayerRecord player : players) {
			if (player.name.equalsIgnoreCase(name) || player.uuid.toString().equalsIgnoreCase(name)) {
				return player;
			}
		}
		return null;
	}

	private static Map<String, Object> playerSummary(PlayerRecord player) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("name", player.name);
		item.put("uuid", player.uuid.toString());
		item.put("playHours", round(player.playHours));
		item.put("playHoursDisplay", display("hours", player.playHours));
		item.put("championScore", round(player.championScore));
		item.put("championDisplay", display("score", player.championScore));
		item.put("scorePerHour", round(player.scorePerHour));
		item.put("scorePerHourDisplay", display("score_per_hour", player.scorePerHour));
		Map<String, Object> advancements = new LinkedHashMap<>();
		advancements.put("done", player.advancements.size());
		advancements.put("total", AdvancementCatalog.get().size());
		item.put("advancements", advancements);
		return item;
	}

	private static double boardValue(LeaderboardInfo info, PlayerRecord player) {
		int slash = info.id().indexOf('/');
		if (slash > 0) {
			String group = info.id().substring(0, slash);
			if (VanillaCatalog.isGroup(group)) {
				String statId = info.id().substring(slash + 1);
				return vanillaValue(group, statId, player.vanillaRaw(group, statId));
			}
		}
		return player.value(info.id());
	}

	private static int rank(List<PlayerRecord> players, String id, PlayerRecord target) {
		double value = target.value(id);
		int place = 1;
		for (PlayerRecord player : players) {
			if (player.value(id) > value) {
				place += 1;
			}
		}
		return place;
	}

	private static PlayerRecord best(List<PlayerRecord> players, String id) {
		PlayerRecord best = null;
		double top = 0;
		for (PlayerRecord player : players) {
			double value = player.value(id);
			if (value > top) {
				top = value;
				best = player;
			}
		}
		return best;
	}

	private static Map<String, Object> crown(PlayerRecord player, String unit) {
		if (player == null) {
			return null;
		}
		double value = switch (unit) {
			case "hours" -> player.playHours;
			case "score_per_hour" -> player.scorePerHour;
			default -> player.championScore;
		};
		if (value <= 0) {
			return null;
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", player.name);
		body.put("uuid", player.uuid.toString());
		body.put("score", round(value));
		body.put("display", display(unit, value));
		return body;
	}

	private static String display(String unit, double value) {
		return switch (unit) {
			case "hours" -> format(value, 2) + " h";
			case "km" -> format(value, 2) + " km";
			case "hearts" -> format(value, 1) + " ♥";
			case "kills_per_hour" -> format(value, 1) + "/h";
			case "score_per_hour" -> format(value, 1) + "/h";
			case "score" -> format(value, 1);
			default -> String.valueOf(Math.round(value));
		};
	}

	private static String format(double value, int decimals) {
		return String.format(Locale.US, "%." + decimals + "f", value);
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
