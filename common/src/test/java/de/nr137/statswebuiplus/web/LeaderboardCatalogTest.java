package de.nr137.statswebuiplus.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The leaderboards overview: three rankings, boards grouped in categories, each with an icon. */
class LeaderboardCatalogTest {
	@Test
	void rankingsExist() {
		for (String id : List.of("legends", "veterans", "grinders")) {
			assertNotNull(LeaderboardCatalog.find(id), id);
		}
	}

	@Test
	void skillsComeFirstWithABoardEach() {
		assertEquals("skills", LeaderboardCatalog.listedCategories().getFirst());
		List<String> boards = LeaderboardCatalog.listedIn("skills").stream().map(LeaderboardInfo::id).toList();
		assertEquals(de.nr137.statswebuiplus.stats.Skills.ORDER.stream().map(skill -> "skill-" + skill).toList(), boards);
	}

	@Test
	void unknownBoardIsNull() {
		assertNull(LeaderboardCatalog.find("no_such_board"));
	}

	@Test
	void idsAreUnique() {
		Set<String> seen = new HashSet<>();
		for (LeaderboardInfo board : LeaderboardCatalog.all()) {
			assertTrue(seen.add(board.id()), "duplicate " + board.id());
		}
	}

	@Test
	void everyListedBoardIsInAListedCategory() {
		List<String> categories = LeaderboardCatalog.listedCategories();
		for (LeaderboardInfo board : LeaderboardCatalog.all()) {
			if (board.listed()) {
				assertTrue(categories.contains(board.category()), board.id() + " in " + board.category());
			}
		}
		for (String category : categories) {
			assertFalse(LeaderboardCatalog.listedIn(category).isEmpty(), category);
			assertTrue(LeaderboardCatalog.listedIn(category).stream().allMatch(LeaderboardInfo::listed), category);
			String icon = LeaderboardCatalog.categoryIcon(category);
			assertTrue(icon != null && !icon.isEmpty(), category);
		}
		assertEquals(Set.copyOf(categories), LeaderboardCatalog.listedByCategory().keySet());
	}

	@Test
	void findReturnsTheSameBoard() {
		for (LeaderboardInfo board : LeaderboardCatalog.all()) {
			assertEquals(board, LeaderboardCatalog.find(board.id()));
		}
	}
}
