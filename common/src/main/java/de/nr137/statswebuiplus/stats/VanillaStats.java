package de.nr137.statswebuiplus.stats;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The game's statistics as the API lists them: the nine groups, and per group the catalog's statistics plus any a
 * player has (a modded block, a statistic the catalog leaves out), so nothing a player did is ever hidden.
 */
public final class VanillaStats {
	private static final List<String> GROUPS = List.of(VanillaCatalog.GROUPS);

	private VanillaStats() {
	}

	/** The groups in the order the web UI shows them. */
	public static List<String> groups() {
		return GROUPS;
	}

	public static boolean isGroup(String group) {
		return VanillaCatalog.isGroup(group);
	}

	/**
	 * The group's statistic IDs, sorted: the catalog's and every one a player has a value for (also 0, which a stats
	 * file may hold); empty for an unknown group. {@link VanillaRanks#keys} keeps this per snapshot.
	 */
	public static List<String> keys(String group, List<PlayerRecord> players) {
		if (!isGroup(group)) {
			return List.of();
		}
		TreeSet<String> keys = new TreeSet<>(VanillaCatalog.keys(group));
		for (PlayerRecord player : players) {
			Map<String, Long> values = player.vanilla().get(group);
			if (values != null) {
				keys.addAll(values.keySet());
			}
		}
		return List.copyOf(keys);
	}
}
