package de.nr137.statswebuiplus.api;

import de.nr137.statswebuiplus.stats.PlayerRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;

/**
 * The order of every board: the highest value first (also where fewer wins on the compare page), on a tie whoever
 * played less, then by name. Equal values get different ranks, in the order the board lists them, so a player's rank
 * is always their place on the board.
 */
public final class Ranking {
	private Ranking() {
	}

	/** Best first by this value. The UUID last only makes two players of the same name and numbers keep one order. */
	public static Comparator<PlayerRecord> order(ToDoubleFunction<PlayerRecord> value) {
		return Comparator.comparingDouble(value).reversed()
			.thenComparingDouble((PlayerRecord player) -> player.playHours())
			.thenComparing(player -> player.name().toLowerCase(Locale.ROOT))
			.thenComparing(player -> player.uuid());
	}

	/** The player's place among all players: one more than the number of players before them. */
	public static int rank(List<PlayerRecord> players, PlayerRecord player, ToDoubleFunction<PlayerRecord> value) {
		Comparator<PlayerRecord> order = order(value);
		int before = 0;
		for (PlayerRecord other : players) {
			if (order.compare(other, player) < 0) {
				before++;
			}
		}
		return 1 + before;
	}

	/** First place, or null when nobody has a value above 0. */
	public static PlayerRecord best(List<PlayerRecord> players, ToDoubleFunction<PlayerRecord> value) {
		PlayerRecord best = null;
		Comparator<PlayerRecord> order = order(value);
		for (PlayerRecord player : players) {
			if (value.applyAsDouble(player) > 0 && (best == null || order.compare(player, best) < 0)) {
				best = player;
			}
		}
		return best;
	}

	/**
	 * A board's entries {@code {"rank", "name", "uuid", "value"}}, best first; without players at 0 or below unless
	 * {@code everyone}. {@code shown} turns the sorted value into the value sent (its unit).
	 */
	public static List<Map<String, Object>> entries(List<PlayerRecord> players, ToDoubleFunction<PlayerRecord> value,
		boolean everyone, DoubleFunction<Number> shown) {
		List<PlayerRecord> listed = new ArrayList<>();
		for (PlayerRecord player : players) {
			if (everyone || value.applyAsDouble(player) > 0) {
				listed.add(player);
			}
		}
		listed.sort(order(value));
		List<Map<String, Object>> entries = new ArrayList<>(listed.size());
		for (int i = 0; i < listed.size(); i++) {
			PlayerRecord player = listed.get(i);
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("rank", i + 1);
			entry.put("name", player.name());
			entry.put("uuid", player.uuid().toString());
			entry.put("value", shown.apply(value.applyAsDouble(player)));
			entries.add(entry);
		}
		return List.copyOf(entries);
	}

	/** {@code {"name", "uuid"}}: how answers name a player. */
	public static Map<String, Object> player(PlayerRecord player) {
		Map<String, Object> ref = new LinkedHashMap<>();
		ref.put("name", player.name());
		ref.put("uuid", player.uuid().toString());
		return ref;
	}
}
