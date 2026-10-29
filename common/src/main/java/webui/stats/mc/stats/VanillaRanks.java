package webui.stats.mc.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The players' vanilla stats sorted once per stat, so a rank is a binary search instead of a pass over all players.
 * A player page ranks every vanilla stat (thousands of IDs); counting the better players for each costs IDs times
 * players per request. Built per group on first use and kept as long as the players don't change.
 */
public final class VanillaRanks {
	private static final long[] NONE = new long[0];

	private final List<PlayerRecord> players;
	private final Map<String, Group> groups = new ConcurrentHashMap<>();

	public VanillaRanks(List<PlayerRecord> players) {
		this.players = List.copyOf(players);
	}

	/** {@link StatsService#vanillaKeys}, worked out once. */
	public List<String> keys(String group) {
		return group(group).keys;
	}

	/** One more than the number of players with a higher value: the same as counting them one by one. */
	public int rank(String group, String id, long raw) {
		// Only players with a value other than 0 are stored; the others count as 0.
		long[] values = group(group).values.getOrDefault(id, NONE);
		int higher = values.length - upperBound(values, raw);
		if (raw < 0) {
			higher += players.size() - values.length;
		}
		return 1 + higher;
	}

	private Group group(String group) {
		return groups.computeIfAbsent(group, this::build);
	}

	private Group build(String group) {
		Map<String, List<Long>> collected = new HashMap<>();
		for (PlayerRecord player : players) {
			Map<String, Long> values = player.vanilla.get(group);
			if (values == null) {
				continue;
			}
			values.forEach((id, value) -> {
				if (value != null && value != 0) {
					collected.computeIfAbsent(id, key -> new ArrayList<>()).add(value);
				}
			});
		}
		Map<String, long[]> sorted = new HashMap<>(collected.size());
		collected.forEach((id, list) -> {
			long[] values = list.stream().mapToLong(Long::longValue).toArray();
			Arrays.sort(values);
			sorted.put(id, values);
		});
		return new Group(StatsService.vanillaKeys(group, players), sorted);
	}

	/** Index of the first value greater than raw in ascending values. */
	private static int upperBound(long[] values, long raw) {
		int low = 0;
		int high = values.length;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (values[middle] <= raw) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return low;
	}

	private record Group(List<String> keys, Map<String, long[]> values) {
	}
}
