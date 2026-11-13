package de.nr137.statswebuiplus.api;

import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.stats.VanillaRanks;
import de.nr137.statswebuiplus.stats.VanillaStats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /api/stats} and the board of one of the game's statistics. */
public final class Statistics {
	private Statistics() {
	}

	/**
	 * Every statistic the statistics page lists, per group, with its leader. About 5,000 rows, most of them counts
	 * nobody has: the unit is left out when it is "count" and the leader when nobody has it, which keeps the answer a
	 * third of the size.
	 */
	public static Map<String, Object> overview(VanillaRanks ranks) {
		Map<String, Object> groups = new LinkedHashMap<>();
		for (String group : VanillaStats.groups()) {
			Map<String, PlayerRecord> leaders = ranks.leaders(group);
			List<Map<String, Object>> statistics = new ArrayList<>();
			for (String id : ranks.keys(group)) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("id", id);
				String unit = Units.ofStatistic(group, id);
				if (!"count".equals(unit)) {
					entry.put("unit", unit);
				}
				PlayerRecord leader = leaders.get(id);
				long raw = leader == null ? 0 : leader.vanillaRaw(group, id);
				// The leaders are whoever has a value other than 0; a board lists values above 0 only.
				if (raw > 0) {
					Map<String, Object> shown = Ranking.player(leader);
					shown.put("value", Units.statistic(group, id, raw));
					entry.put("leader", shown);
				}
				statistics.add(entry);
			}
			groups.put(group, statistics);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("groups", groups);
		return body;
	}

	/**
	 * The board of one statistic ({@code id} in full), or null when the group isn't one of the nine or the statistic
	 * isn't in its list.
	 */
	public static Map<String, Object> board(String group, String id, List<PlayerRecord> players, VanillaRanks ranks) {
		if (!VanillaStats.isGroup(group) || !ranks.keys(group).contains(id)) {
			return null;
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("group", group);
		body.put("id", id);
		body.put("unit", Units.ofStatistic(group, id));
		body.put("entries", Ranking.entries(players, player -> player.vanillaRaw(group, id), false,
			raw -> Units.statistic(group, id, (long) raw)));
		return body;
	}
}
