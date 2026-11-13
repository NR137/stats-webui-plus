package de.nr137.statswebuiplus.api;

import de.nr137.statswebuiplus.stats.Skills;
import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.stats.SessionLog;
import de.nr137.statswebuiplus.stats.VanillaRanks;
import de.nr137.statswebuiplus.stats.VanillaStats;
import de.nr137.statswebuiplus.web.LeaderboardCatalog;
import de.nr137.statswebuiplus.web.LeaderboardInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/** {@code /api/players} and one player's page: who they are, their skills, boards, statistics and advancements. */
public final class Players {
	/** The board whose rank is a player's level rank. */
	static final String LEGENDS = "legends";

	private Players() {
	}

	/**
	 * A player by name or UUID: the exact spelling first, then the UUID, then the first name that differs only in case
	 * (Floodgate may give two players names that do); null if none.
	 */
	public static PlayerRecord find(List<PlayerRecord> players, String nameOrUuid) {
		if (nameOrUuid == null || nameOrUuid.isEmpty()) {
			return null;
		}
		for (PlayerRecord player : players) {
			if (player.name().equals(nameOrUuid)) {
				return player;
			}
		}
		UUID uuid = uuid(nameOrUuid);
		if (uuid != null) {
			for (PlayerRecord player : players) {
				if (player.uuid().equals(uuid)) {
					return player;
				}
			}
		}
		for (PlayerRecord player : players) {
			if (player.name().equalsIgnoreCase(nameOrUuid)) {
				return player;
			}
		}
		return null;
	}

	/** The names (lower case) more than one player has, sorted. */
	public static List<String> nameClashes(List<PlayerRecord> players) {
		Map<String, Integer> counts = new HashMap<>();
		for (PlayerRecord player : players) {
			counts.merge(player.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
		}
		TreeSet<String> clashes = new TreeSet<>();
		counts.forEach((name, count) -> {
			if (count > 1) {
				clashes.add(name);
			}
		});
		return List.copyOf(clashes);
	}

	/** {@code {"players": [...]}}: everyone, most play time first, then by name. */
	public static Map<String, Object> list(List<PlayerRecord> players, int advancementsTotal) {
		List<PlayerRecord> sorted = new ArrayList<>(players);
		sorted.sort(Comparator.comparingDouble((PlayerRecord player) -> player.playHours()).reversed()
			.thenComparing(player -> player.name().toLowerCase(Locale.ROOT))
			.thenComparing(player -> player.uuid()));
		List<Map<String, Object>> list = new ArrayList<>(sorted.size());
		for (PlayerRecord player : sorted) {
			Map<String, Object> entry = Ranking.player(player);
			entry.put("playHours", Units.round(player.playHours()));
			entry.put("level", Units.round(player.level()));
			entry.put("actionsPerHour", Units.round(player.actionsPerHour()));
			Map<String, Object> advancements = new LinkedHashMap<>();
			advancements.put("done", player.advancements().size());
			advancements.put("total", advancementsTotal);
			entry.put("advancements", advancements);
			list.add(entry);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("players", list);
		return body;
	}

	/** One player's page; {@code statistics} only has what isn't 0 (the web UI fills in the rest from /api/stats). */
	public static Map<String, Object> detail(PlayerRecord player, List<PlayerRecord> players, VanillaRanks ranks,
		AdvancementView catalog) {
		SessionLog.Times times = SessionLog.get().times(player.uuid());
		Map<String, Object> body = Ranking.player(player);
		body.put("online", times.online());
		body.put("firstJoin", Units.time(times.firstJoin()));
		body.put("firstJoinEstimated", times.firstJoinEstimated());
		body.put("lastLogout", Units.time(times.lastLogout()));
		body.put("lastLogoutEstimated", times.lastLogoutEstimated());
		body.put("playHours", Units.round(player.playHours()));
		body.put("level", Units.round(player.level()));
		body.put("levelRank", Ranking.rank(players, player, other -> other.value(LEGENDS)));
		body.put("actionsPerHour", Units.round(player.actionsPerHour()));
		body.put("trackedPlayers", players.size());
		body.put("sameName", sameName(player, players));
		body.put("skills", skills(player));
		body.put("boards", boards(player, players));
		body.put("statistics", statistics(player, ranks));
		body.put("advancements", Advancements.ofPlayer(player, players, catalog));
		return body;
	}

	private static List<Map<String, Object>> sameName(PlayerRecord player, List<PlayerRecord> players) {
		List<Map<String, Object>> others = new ArrayList<>();
		for (PlayerRecord other : players) {
			if (!other.uuid().equals(player.uuid()) && other.name().equalsIgnoreCase(player.name())) {
				others.add(Ranking.player(other));
			}
		}
		return others;
	}

	/** Each skill (skill) with its value and the parts it is made of. */
	private static Map<String, Object> skills(PlayerRecord player) {
		Map<String, Object> skills = new LinkedHashMap<>();
		player.skills().forEach((skill, value) -> {
			List<Map<String, Object>> parts = new ArrayList<>();
			for (Skills.Part part : player.skillParts().getOrDefault(skill, List.of())) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("id", part.id());
				entry.put("value", Units.round(part.value()));
				entry.put("target", Units.round(part.target()));
				entry.put("weight", Units.round(part.weight()));
				entry.put("score", Units.round(part.grade()));
				parts.add(entry);
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("value", Units.round(value));
			entry.put("parts", parts);
			skills.put(skill, entry);
		});
		return skills;
	}

	/** Every board in catalog order: the player's value there and their rank. */
	private static Map<String, Object> boards(PlayerRecord player, List<PlayerRecord> players) {
		Map<String, Object> boards = new LinkedHashMap<>();
		for (LeaderboardInfo board : LeaderboardCatalog.all()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("value", Units.round(player.value(board.id())));
			entry.put("unit", Units.of(board));
			entry.put("category", board.category());
			entry.put("rank", Ranking.rank(players, player, other -> other.value(board.id())));
			boards.put(board.id(), entry);
		}
		return boards;
	}

	/** Per group, the statistics the player has (not 0) in the statistics page's order, with value, unit and rank. */
	private static Map<String, Object> statistics(PlayerRecord player, VanillaRanks ranks) {
		Map<String, Object> groups = new LinkedHashMap<>();
		for (String group : VanillaStats.groups()) {
			Map<String, Object> statistics = new LinkedHashMap<>();
			Map<String, Long> values = player.vanilla().getOrDefault(group, Map.of());
			if (!values.isEmpty()) {
				for (String id : ranks.keys(group)) {
					Long raw = values.get(id);
					if (raw == null || raw == 0) {
						continue;
					}
					Map<String, Object> entry = new LinkedHashMap<>();
					entry.put("value", Units.statistic(group, id, raw));
					entry.put("unit", Units.ofStatistic(group, id));
					entry.put("rank", ranks.rank(group, id, raw));
					statistics.put(id, entry);
				}
			}
			groups.put(group, statistics);
		}
		return groups;
	}

	private static UUID uuid(String text) {
		if (text.length() != 36) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
