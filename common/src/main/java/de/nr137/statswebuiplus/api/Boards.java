package de.nr137.statswebuiplus.api;

import de.nr137.statswebuiplus.stats.PlayerMetrics;
import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.web.LeaderboardCatalog;
import de.nr137.statswebuiplus.web.LeaderboardInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /api/leaderboards}, one board, and {@code /api/rankings}: the boards of {@link LeaderboardCatalog}. */
public final class Boards {
	private static final List<String> RANKINGS = List.of("legends", "veterans", "grinders");

	private Boards() {
	}

	/** The leaderboards page: its categories with their boards' IDs, and every board once. */
	public static Map<String, Object> overview() {
		List<Map<String, Object>> categories = new ArrayList<>();
		LeaderboardCatalog.listedByCategory().forEach((category, boards) -> {
			List<String> ids = new ArrayList<>(boards.size());
			for (LeaderboardInfo board : boards) {
				ids.add(board.id());
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("id", category);
			entry.put("icon", LeaderboardCatalog.categoryIcon(category));
			entry.put("boards", ids);
			categories.add(entry);
		});
		List<Map<String, Object>> boards = new ArrayList<>();
		for (LeaderboardInfo board : LeaderboardCatalog.all()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("id", board.id());
			entry.put("category", board.category());
			entry.put("unit", Units.of(board));
			entry.put("icon", board.icon());
			entry.put("listed", board.listed());
			entry.put("compare", board.compare());
			entry.put("lowerWins", board.lowerWins());
			boards.add(entry);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("categories", categories);
		body.put("boards", boards);
		return body;
	}

	/**
	 * One board: what it is, which statistics it adds up ({@code parts}, left out for none) and its entries; without
	 * players at 0 or below, except on {@code legends}, where everyone has a level.
	 */
	public static Map<String, Object> board(LeaderboardInfo board, List<PlayerRecord> players) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", board.id());
		body.put("category", board.category());
		body.put("unit", Units.of(board));
		body.put("icon", board.icon());
		body.put("lowerWins", board.lowerWins());
		Map<String, List<String>> parts = PlayerMetrics.parts(board.id());
		if (!parts.isEmpty()) {
			body.put("parts", parts);
		}
		body.put("entries", Ranking.entries(players, player -> player.value(board.id()), board.id().equals(Players.LEGENDS),
			Units::round));
		return body;
	}

	/** {@code {"legends", "veterans", "grinders"}}: first place on each of these boards, or null when nobody is above 0. */
	public static Map<String, Object> rankings(List<PlayerRecord> players) {
		Map<String, Object> body = new LinkedHashMap<>();
		for (String id : RANKINGS) {
			LeaderboardInfo board = LeaderboardCatalog.find(id);
			PlayerRecord best = Ranking.best(players, player -> player.value(id));
			Map<String, Object> leader = null;
			if (best != null) {
				leader = Ranking.player(best);
				leader.put("value", Units.round(best.value(id)));
				leader.put("unit", Units.of(board));
			}
			body.put(id, leader);
		}
		return body;
	}
}
