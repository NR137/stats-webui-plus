package de.nr137.statswebuiplus.api;

import de.nr137.statswebuiplus.stats.AdvancementInfo;
import de.nr137.statswebuiplus.stats.AdvancementTab;
import de.nr137.statswebuiplus.stats.PlayerRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * {@code /api/advancements}, one advancement with who has it, and a player's advancements: done ones with their
 * times, progress on the unfinished ones, and the criteria of one of them.
 */
public final class Advancements {
	/** The catalog ID of the board of the most advancements. */
	public static final String MOST = "advancements";

	private Advancements() {
	}

	/** {@code {"total", "tabs", "advancements"}}: the catalog, each advancement with how many players have it. */
	public static Map<String, Object> list(AdvancementView catalog, List<PlayerRecord> players) {
		Map<String, Integer> holders = holders(players);
		List<Map<String, Object>> tabs = new ArrayList<>();
		for (AdvancementTab tab : catalog.tabs()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("id", tab.id());
			entry.put("title", tab.title());
			entry.put("icon", tab.icon());
			entry.put("background", tab.background());
			tabs.add(entry);
		}
		List<Map<String, Object>> advancements = new ArrayList<>(catalog.size());
		for (AdvancementInfo info : catalog.all()) {
			advancements.add(fields(info, holders.getOrDefault(info.id(), 0)));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("total", catalog.size());
		body.put("tabs", tabs);
		body.put("advancements", advancements);
		return body;
	}

	/** One advancement with {@code entries}: everyone who has it, earliest first, unknown times last. */
	public static Map<String, Object> detail(AdvancementInfo info, List<PlayerRecord> players) {
		List<PlayerRecord> holders = new ArrayList<>();
		for (PlayerRecord player : players) {
			if (player.hasAdvancement(info.id())) {
				holders.add(player);
			}
		}
		holders.sort(Comparator.comparingLong((PlayerRecord player) -> {
				long time = player.advancementTime(info.id());
				return time > 0 ? time : Long.MAX_VALUE;
			})
			.thenComparing(player -> player.name().toLowerCase(Locale.ROOT))
			.thenComparing(player -> player.uuid()));
		List<Map<String, Object>> entries = new ArrayList<>(holders.size());
		for (int i = 0; i < holders.size(); i++) {
			PlayerRecord player = holders.get(i);
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("rank", i + 1);
			entry.put("name", player.name());
			entry.put("uuid", player.uuid().toString());
			entry.put("completedAt", Units.time(player.advancementTime(info.id())));
			entries.add(entry);
		}
		Map<String, Object> body = fields(info, holders.size());
		body.put("entries", entries);
		return body;
	}

	/**
	 * The {@code advancements} part of a player's page: how many are done, the rank on the board of the most,
	 * the done ones in catalog order with their times (unknown ones after them, by ID), and how far along the
	 * unfinished ones with several requirements are.
	 */
	public static Map<String, Object> ofPlayer(PlayerRecord player, List<PlayerRecord> players, AdvancementView catalog) {
		Map<String, Object> completed = new LinkedHashMap<>();
		Map<String, Map<String, Object>> progress = new LinkedHashMap<>();
		for (AdvancementInfo info : catalog.all()) {
			String id = info.id();
			if (player.hasAdvancement(id)) {
				completed.put(id, Units.time(player.advancementTime(id)));
				continue;
			}
			List<List<String>> requirements = catalog.requirements().apply(id);
			if (requirements == null || requirements.size() < 2) {
				continue;
			}
			Map<String, Long> met = player.advancementCriteria(id);
			int done = 0;
			for (List<String> requirement : requirements) {
				if (requirement.stream().anyMatch(met::containsKey)) {
					done++;
				}
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("done", done);
			entry.put("total", requirements.size());
			progress.put(id, entry);
		}
		// Done ones the catalog doesn't show (a data pack removed, a hidden helper advancement).
		TreeSet<String> unknown = new TreeSet<>(player.advancements());
		unknown.removeAll(completed.keySet());
		for (String id : unknown) {
			completed.put(id, Units.time(player.advancementTime(id)));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("done", player.advancements().size());
		body.put("total", catalog.size());
		body.put("rank", Ranking.rank(players, player, other -> other.value(MOST)));
		body.put("completed", completed);
		body.put("progress", progress);
		return body;
	}

	/**
	 * One advancement for one player: its fields, whether and when the player did it, and its requirements as the
	 * game keeps them (lists of criteria, any of which meets the requirement) with when each criterion was met.
	 */
	public static Map<String, Object> ofPlayer(PlayerRecord player, AdvancementInfo info, List<PlayerRecord> players,
		AdvancementView catalog) {
		String id = info.id();
		boolean done = player.hasAdvancement(id);
		Long completedAt = done ? Units.time(player.advancementTime(id)) : null;
		Map<String, Long> times = player.advancementCriteria(id);
		List<List<String>> requirements = catalog.requirements().apply(id);
		if (requirements == null || requirements.isEmpty()) {
			// Nothing known about its criteria: each one the player met is a requirement of its own.
			requirements = new ArrayList<>();
			for (String criterion : times.keySet()) {
				requirements.add(List.of(criterion));
			}
		}
		List<List<Map<String, Object>>> listed = new ArrayList<>(requirements.size());
		for (List<String> requirement : requirements) {
			List<Map<String, Object>> criteria = new ArrayList<>(requirement.size());
			for (String criterion : requirement) {
				Long at = times.containsKey(criterion) ? Units.time(times.get(criterion)) : null;
				// The game keeps the criteria times of advancements with several criteria only; with one, its time is
				// the advancement's.
				if (at == null && done && times.isEmpty() && requirement.size() == 1) {
					at = completedAt;
				}
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("criterion", criterion);
				entry.put("at", at);
				criteria.add(entry);
			}
			listed.add(criteria);
		}
		Map<String, Object> body = fields(info, holders(players).getOrDefault(id, 0));
		body.put("player", Ranking.player(player));
		body.put("done", done);
		body.put("completedAt", completedAt);
		body.put("requirements", listed);
		return body;
	}

	/** An advancement as {@code /api/advancements} lists it. */
	static Map<String, Object> fields(AdvancementInfo info, int holders) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", info.id());
		body.put("tab", info.tab());
		body.put("title", info.title());
		body.put("description", info.description());
		body.put("icon", info.icon());
		body.put("frame", info.frame());
		body.put("hidden", info.hidden());
		body.put("experience", info.experience());
		body.put("parent", info.parent());
		body.put("x", Units.round(info.x()));
		body.put("y", Units.round(info.y()));
		body.put("holders", holders);
		return body;
	}

	/** Advancement ID -> how many players have it. */
	private static Map<String, Integer> holders(List<PlayerRecord> players) {
		Map<String, Integer> holders = new HashMap<>();
		for (PlayerRecord player : players) {
			for (String id : player.advancements()) {
				holders.merge(id, 1, Integer::sum);
			}
		}
		return holders;
	}
}
