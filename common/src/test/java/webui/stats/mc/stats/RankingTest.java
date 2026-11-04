package webui.stats.mc.stats;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The rules on the "How we rank" page (about.* texts in web/src/lib/i18n/messages/en.ts). */
class RankingTest {
	private static final List<String> AREAS = List.of("combat", "milestones", "mining", "explore", "produce", "survive");
	private static final double EPSILON = 1e-6;

	private static List<PlayerRecord> rank(PlayerRecord... players) {
		return ChampionScorer.apply(new ArrayList<>(Arrays.asList(players)));
	}

	private static PlayerRecord named(List<PlayerRecord> ranked, String name) {
		return ranked.stream().filter(player -> player.name.equals(name)).findFirst().orElseThrow();
	}

	private static double grade(List<PlayerRecord> ranked, String name, String area) {
		Map<String, Double> grades = named(ranked, name).grades;
		assertTrue(grades.containsKey(area), "no grade for " + area + " in " + grades);
		return grades.get(area);
	}

	private static List<PlayerRecord> sampleServer() {
		return rank(
			Stats.player().allRounder(1).compute("Alex"),
			Stats.player().allRounder(3).compute("Steve"),
			Stats.player().hours(40).mined("dirt", 50_000).compute("Digger"),
			Stats.player().hours(1).killed("zombie", 400).compute("Hunter"),
			Stats.player().compute("Newcomer")
		);
	}

	@Test
	void indexIsBetween0And100() {
		for (PlayerRecord player : sampleServer()) {
			assertTrue(player.championScore >= 0 && player.championScore <= 100, player.name + " " + player.championScore);
			assertFalse(Double.isNaN(player.scorePerHour), player.name);
		}
	}

	@Test
	void leaderOfEachAreaGets100AndNobodyMore() {
		List<PlayerRecord> ranked = sampleServer();
		for (String area : AREAS) {
			double best = ranked.stream().mapToDouble(player -> grade(ranked, player.name, area)).max().orElseThrow();
			assertEquals(100, best, 0.01, area);
		}
	}

	@Test
	void soleAllRounderLeadsEverythingAndScores100() {
		PlayerRecord only = rank(Stats.player().allRounder(1).compute("Alex")).getFirst();
		assertEquals(100, only.championScore, 0.01);
	}

	@Test
	void doingMoreOfEverythingRanksHigher() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Alex"),
			Stats.player().allRounder(2).compute("Steve")
		);
		assertTrue(named(ranked, "Steve").championScore > named(ranked, "Alex").championScore);
	}

	@Test
	void playerWithoutStatsScoresZero() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Alex"),
			Stats.player().compute("Newcomer")
		);
		PlayerRecord newcomer = named(ranked, "Newcomer");
		assertEquals(0, newcomer.championScore, EPSILON);
		assertEquals(0, newcomer.playHours, EPSILON);
		assertFalse(Double.isNaN(newcomer.scorePerHour));
		assertFalse(Double.isInfinite(newcomer.scorePerHour));
	}

	@Test
	void serverWithoutAnyStatsDoesNotBreak() {
		List<PlayerRecord> ranked = rank(Stats.player().compute("A"), Stats.player().compute("B"));
		for (PlayerRecord player : ranked) {
			assertEquals(0, player.championScore, EPSILON);
			player.grades.values().forEach(grade -> assertFalse(Double.isNaN(grade), player.grades.toString()));
		}
		assertTrue(rank().isEmpty());
	}

	@Test
	void silverfishChickensAndFishAreNotCombat() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Fighter"),
			Stats.player().allRounder(1)
				.killed("silverfish", 100_000).killed("chicken", 10_000).killed("cod", 10_000)
				.compute("Farmer")
		);
		assertEquals(grade(ranked, "Fighter", "combat"), grade(ranked, "Farmer", "combat"), EPSILON);
	}

	@Test
	void realHostilesAreCombat() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Alex"),
			Stats.player().allRounder(1).killed("creeper", 200).compute("Steve")
		);
		assertTrue(grade(ranked, "Steve", "combat") > grade(ranked, "Alex", "combat"));
	}

	@Test
	void creativeFlightIsNotExploration() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Walker"),
			Stats.player().allRounder(1).custom("fly_one_cm", 100_000_000).compute("Flyer")
		);
		assertEquals(grade(ranked, "Walker", "explore"), grade(ranked, "Flyer", "explore"), EPSILON);
	}

	@Test
	void elytraAndBoatsAreExploration() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Walker"),
			Stats.player().allRounder(1).custom("aviate_one_cm", 50_000_000).custom("boat_one_cm", 10_000_000).compute("Glider")
		);
		assertTrue(grade(ranked, "Glider", "explore") > grade(ranked, "Walker", "explore"));
	}

	@Test
	void dirtAndStoneBarelyCountAgainstDiamonds() {
		List<PlayerRecord> ranked = rank(
			Stats.player().hours(10).mined("dirt", 20_000).mined("stone", 20_000).compute("Digger"),
			Stats.player().hours(10).mined("diamond_ore", 10).mined("deepslate_diamond_ore", 10).compute("Miner")
		);
		assertTrue(grade(ranked, "Miner", "mining") > grade(ranked, "Digger", "mining"));
	}

	@Test
	void craftingSticksScoresNothing() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Alex"),
			Stats.player().allRounder(1).crafted("stick", 100_000).compute("Stickman")
		);
		assertEquals(named(ranked, "Alex").championScore, named(ranked, "Stickman").championScore, EPSILON);
	}

	@Test
	void breedingFishingAndTradingAreProduction() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Alex"),
			Stats.player().allRounder(1).custom("animals_bred", 500).custom("traded_with_villager", 500).compute("Farmer")
		);
		assertTrue(grade(ranked, "Farmer", "produce") > grade(ranked, "Alex", "produce"));
	}

	@Test
	void milestonesCount() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Alex"),
			Stats.player().allRounder(1).killed("wither", 2).killed("warden", 1).custom("raid_win", 5).compute("Hero")
		);
		assertTrue(grade(ranked, "Hero", "milestones") > grade(ranked, "Alex", "milestones"));
	}

	@Test
	void playTimeCountsLessAndLess() {
		// "26 h is not worth 13 times 2 h"
		List<PlayerRecord> ranked = rank(
			Stats.player().hours(2).compute("Short"),
			Stats.player().hours(26).compute("Long")
		);
		double shortGrade = grade(ranked, "Short", "survive");
		assertTrue(grade(ranked, "Long", "survive") > shortGrade);
		assertTrue(shortGrade > 100.0 * 2 / 26, "2 h grade " + shortGrade);
	}

	@Test
	void dyingNicksButDoesNotWipe() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Careful"),
			Stats.player().allRounder(1).custom("deaths", 1_000).compute("Reckless"),
			Stats.player().allRounder(1).compute("Twin")
		);
		double careful = named(ranked, "Careful").championScore;
		double reckless = named(ranked, "Reckless").championScore;
		assertTrue(reckless < careful, reckless + " vs " + careful);
		// Survival weighs 8 %; at 0 survival the player also loses that area's share of versatility (6 % / 6 areas).
		assertTrue(careful - reckless <= 8 + 1 + EPSILON, reckless + " vs " + careful);
	}

	@Test
	void fewDeathsLowerSurvivalOnly() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("Careful"),
			Stats.player().allRounder(1).custom("deaths", 10).compute("Unlucky")
		);
		double unlucky = grade(ranked, "Unlucky", "survive");
		assertTrue(unlucky > 0 && unlucky < grade(ranked, "Careful", "survive"), "survive " + unlucky);
		for (String area : AREAS.subList(0, 5)) {
			assertEquals(grade(ranked, "Careful", area), grade(ranked, "Unlucky", area), EPSILON, area);
		}
	}

	@Test
	void oneTrickFarmDoesNotBeatAllRounder() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(1).compute("AllRounder"),
			Stats.player().hours(10).killed("zombie", 100_000).compute("Spawner")
		);
		assertTrue(named(ranked, "AllRounder").championScore > named(ranked, "Spawner").championScore);
	}

	@Test
	void playHoursComeFromPlayTime() {
		assertEquals(3, Stats.player().hours(3).compute("Alex").playHours, 0.001);
	}

	@Test
	void mostEfficientIsIndexPerHour() {
		for (PlayerRecord player : sampleServer()) {
			if (player.playHours > 0) {
				assertEquals(player.championScore / player.playHours, player.scorePerHour, 0.001, player.name);
			}
		}
	}

	@Test
	void crownLeaderboardsMatchFields() {
		for (PlayerRecord player : sampleServer()) {
			assertEquals(player.championScore, player.value("champion"), EPSILON, player.name);
			assertEquals(player.playHours, player.value("dedicated"), 0.001, player.name);
			assertEquals(player.scorePerHour, player.value("efficient"), 0.001, player.name);
		}
	}

	@Test
	void rankingIsStable() {
		List<Double> first = sampleServer().stream().map(player -> player.championScore).sorted().toList();
		List<Double> second = sampleServer().stream().map(player -> player.championScore).sorted().toList();
		assertEquals(first, second);
	}
}
