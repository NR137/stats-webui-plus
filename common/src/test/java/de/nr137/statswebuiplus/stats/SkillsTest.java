package de.nr137.statswebuiplus.stats;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The rules on the "How we rank" page (about.* texts in web/src/lib/i18n/messages/en.ts). */
class SkillsTest {
	private static final double EPSILON = 1e-6;

	@BeforeAll
	static void items() {
		VanillaCatalog.useItemsForTests(
			Set.of("minecraft:bread", "minecraft:apple", "minecraft:cooked_beef"),
			Set.of("minecraft:stone", "minecraft:oak_planks", "minecraft:glass", "minecraft:wheat_seeds")
		);
	}

	@AfterAll
	static void noItems() {
		VanillaCatalog.useItemsForTests(Set.of(), Set.of());
	}

	private static List<PlayerRecord> rank(PlayerRecord... players) {
		return Skills.apply(new ArrayList<>(Arrays.asList(players)));
	}

	private static PlayerRecord one(PlayerRecord player) {
		return rank(player).getFirst();
	}

	private static double skill(PlayerRecord player, String skill) {
		PlayerRecord scored = one(player);
		assertTrue(scored.skills().containsKey(skill), "no skill " + skill + " in " + scored.skills());
		return scored.skills().get(skill);
	}

	/** With these criteria of an advancement met (biomes, animals). */
	private static PlayerRecord withCriteria(PlayerRecord player, String advancement, int count) {
		Map<String, Long> met = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			met.put("minecraft:criterion_" + i, 1_000L + i);
		}
		return player.withAdvancements(Set.of(), Map.of(), Map.of(advancement, met));
	}

	@Test
	void weightsAddUpToOne() {
		double sum = Skills.ORDER.stream().mapToDouble(Skills::weight).sum();
		assertEquals(1, sum, EPSILON);
	}

	@Test
	void everySkillsPartsAddUpToOne() {
		PlayerRecord player = one(Stats.player().allRounder(1).compute("Alex"));
		assertEquals(Skills.ORDER, List.copyOf(player.skills().keySet()));
		player.skillParts().forEach((skill, parts) ->
			assertEquals(1, parts.stream().mapToDouble(Skills.Part::weight).sum(), EPSILON, skill));
	}

	@Test
	void levelIsTheWeightedSumOfTheSkills() {
		PlayerRecord player = one(Stats.player().allRounder(2).compute("Alex"));
		double sum = 0;
		for (String skill : Skills.ORDER) {
			sum += player.skills().get(skill) * Skills.weight(skill);
		}
		assertEquals(sum, player.level(), EPSILON);
		assertTrue(player.level() > 0 && player.level() < 100, "level " + player.level());
	}

	@Test
	void playerWithoutStatsIsLevelZero() {
		PlayerRecord nobody = one(Stats.player().compute("Newcomer"));
		assertEquals(0, nobody.level(), EPSILON);
		assertEquals(0, nobody.actionsPerHour(), EPSILON);
		nobody.skills().values().forEach(grade -> assertFalse(Double.isNaN(grade) || Double.isInfinite(grade)));
		assertTrue(rank().isEmpty());
	}

	@Test
	void otherPlayersChangeNothing() {
		PlayerRecord alone = one(Stats.player().allRounder(1).compute("Alex"));
		List<PlayerRecord> server = rank(
			Stats.player().allRounder(1).compute("Alex"),
			Stats.player().allRounder(50).killed("zombie", 1_000_000).compute("Steve")
		);
		PlayerRecord withOthers = server.stream().filter(player -> player.name().equals("Alex")).findFirst().orElseThrow();
		assertEquals(alone.level(), withOthers.level(), EPSILON);
		assertEquals(alone.skills(), withOthers.skills());
	}

	@Test
	void doingMoreNeverLowersASkillOutsideSurvival() {
		PlayerRecord before = one(Stats.player().allRounder(1).compute("Alex"));
		PlayerRecord after = one(Stats.player().allRounder(3).compute("Alex"));
		for (String skill : Skills.ORDER) {
			if (!skill.equals("survival")) {
				assertTrue(after.skills().get(skill) >= before.skills().get(skill) - EPSILON, skill);
			}
		}
		assertTrue(after.level() > before.level());
	}

	@Test
	void dyingLowersOnlySurvival() {
		PlayerRecord careful = one(Stats.player().allRounder(1).compute("Alex"));
		PlayerRecord unlucky = one(Stats.player().allRounder(1).custom("deaths", 10).compute("Alex"));
		assertTrue(unlucky.skills().get("survival") < careful.skills().get("survival"));
		for (String skill : Skills.ORDER) {
			if (!skill.equals("survival")) {
				assertEquals(careful.skills().get(skill), unlucky.skills().get(skill), EPSILON, skill);
			}
		}
		// Survival weighs 10 %: even endless deaths cost at most that.
		PlayerRecord reckless = one(Stats.player().allRounder(1).custom("deaths", 100_000).compute("Alex"));
		assertTrue(careful.level() - reckless.level() <= 10 + EPSILON);
	}

	@Test
	void playingLongerWithoutDyingRaisesSurvival() {
		assertTrue(skill(Stats.player().hours(30).custom("deaths", 2).compute("A"), "survival")
			> skill(Stats.player().hours(10).custom("deaths", 2).compute("A"), "survival"));
	}

	@Test
	void survivalIsCappedAt100() {
		PlayerRecord immortal = one(Stats.player().hours(10_000).killed("zombie", 250).killed("skeleton", 250)
			.killed("creeper", 250).compute("A"));
		assertEquals(100, immortal.skills().get("survival"), 0.01);
	}

	@Test
	void breadthIsFullAtItsTargetAndNeverMore() {
		PlayerRecord some = withCriteria(Stats.player().compute("A"), "minecraft:adventure/adventuring_time", Skills.BIOMES_TARGET);
		PlayerRecord all = withCriteria(Stats.player().compute("A"), "minecraft:adventure/adventuring_time", Skills.BIOMES_TARGET + 10);
		assertEquals(50, one(some).skills().get("exploration"), 0.01);
		assertEquals(one(some).skills().get("exploration"), one(all).skills().get("exploration"), EPSILON);
	}

	@Test
	void netherBiomesCountToo() {
		PlayerRecord overworld = withCriteria(Stats.player().compute("A"), "minecraft:adventure/adventuring_time", 10);
		PlayerRecord both = overworld.withAdvancements(Set.of(), Map.of(), Map.of(
			"minecraft:adventure/adventuring_time", overworld.advancementCriteria("minecraft:adventure/adventuring_time"),
			"minecraft:nether/explore_nether", Map.of("minecraft:basalt_deltas", 1L, "minecraft:soul_sand_valley", 2L)
		));
		assertTrue(one(both).skills().get("exploration") > one(overworld).skills().get("exploration"));
	}

	@Test
	void depthGives100AtTheTargetAndKeepsRisingSlowly() {
		assertEquals(100, Skills.curve(Skills.KILLS_TARGET, Skills.KILLS_TARGET), EPSILON);
		double twice = Skills.curve(2 * Skills.KILLS_TARGET, Skills.KILLS_TARGET);
		assertTrue(twice > 110 && twice < 125, "twice the target " + twice);
		assertTrue(Skills.curve(Skills.KILLS_TARGET / 10, Skills.KILLS_TARGET) < 60);
	}

	@Test
	void aFarmCountsLikeAFewHundredKills() {
		double farm = Skills.softKills(one(Stats.player().killed("zombified_piglin", 120_000).compute("A")));
		assertTrue(farm > Skills.KILL_CAP && farm < 700, "farm " + farm);
	}

	@Test
	void farmerDoesNotBeatAllRounder() {
		List<PlayerRecord> ranked = rank(
			Stats.player().allRounder(3).compute("AllRounder"),
			Stats.player().hours(30).killed("zombified_piglin", 200_000).killed("silverfish", 100_000)
				.custom("fish_caught", 100_000).compute("Farmer")
		);
		assertEquals("AllRounder", ranked.getFirst().name());
	}

	@Test
	void manyMobTypesBeatOneMobAFewTimes() {
		Stats varied = Stats.player();
		for (String mob : List.of("zombie", "skeleton", "creeper", "spider", "enderman", "witch", "blaze", "ghast")) {
			varied.killed(mob, 25);
		}
		assertTrue(skill(varied.compute("A"), "combat") > skill(Stats.player().killed("zombie", 200).compute("B"), "combat"));
	}

	@Test
	void animalsAndChickensAreNotCombat() {
		assertEquals(0, skill(Stats.player().killed("chicken", 10_000).killed("cod", 10_000).compute("A"), "combat"), EPSILON);
	}

	@Test
	void creativeFlightIsNotExploration() {
		assertEquals(0, skill(Stats.player().custom("fly_one_cm", 100_000_000).compute("A"), "exploration"), EPSILON);
	}

	@Test
	void minecartsCountAFifth() {
		double walk = skill(Stats.player().custom("walk_one_cm", 100_000_000).compute("A"), "exploration");
		double cart = skill(Stats.player().custom("minecart_one_cm", 500_000_000).compute("A"), "exploration");
		assertEquals(walk, cart, 1e-3);
	}

	@Test
	void dirtAndStoneAreNotMining() {
		assertEquals(0, skill(Stats.player().mined("dirt", 50_000).mined("stone", 50_000).compute("A"), "mining"), EPSILON);
		assertTrue(skill(Stats.player().mined("diamond_ore", 10).compute("A"), "mining") > 0);
	}

	@Test
	void eachOreIsSoftCapped() {
		double strip = skill(Stats.player().mined("coal_ore", 1_000_000).compute("A"), "mining");
		double varied = skill(Stats.player().mined("coal_ore", 500).mined("iron_ore", 500).mined("diamond_ore", 100)
			.mined("gold_ore", 100).compute("B"), "mining");
		assertTrue(varied > strip, varied + " vs " + strip);
	}

	@Test
	void buildingCountsBlockTypesAndPlacedBlocks() {
		double one = skill(Stats.player().used("stone", 10_000).compute("A"), "building");
		double three = skill(Stats.player().used("stone", 5_000).used("glass", 2_500).used("oak_planks", 2_500).compute("B"), "building");
		assertTrue(three > one);
		// Using an item that isn't a block (eating bread) places nothing.
		assertEquals(0, skill(Stats.player().used("bread", 10_000).compute("C"), "building"), EPSILON);
	}

	@Test
	void craftingSticksBarelyCounts() {
		double sticks = skill(Stats.player().crafted("stick", 100_000).compute("A"), "building");
		assertTrue(sticks < 1, "sticks " + sticks);
	}

	@Test
	void farmingCountsFoodAnimalsAndWork() {
		PlayerRecord farmer = withCriteria(Stats.player().used("bread", 5).used("apple", 5).custom("animals_bred", 500)
			.custom("traded_with_villager", 300).mined("wheat", 2_000).compute("A"), Skills.ANIMALS_ADVANCEMENT, 10);
		assertTrue(one(farmer).skills().get("farming") > 40, "farmer " + one(farmer).skills());
		// An AFK fish farm: soft-capped.
		double fisher = skill(Stats.player().custom("fish_caught", 100_000).compute("B"), "farming");
		assertTrue(fisher < 50, "fisher " + fisher);
	}

	@Test
	void milestonesCountOnceAndRepeatsLessAndLess() {
		double one = skill(Stats.player().killed("wither", 1).compute("A"), "milestones");
		double three = skill(Stats.player().killed("wither", 3).compute("A"), "milestones");
		assertTrue(three > one && three < 3 * one, one + " vs " + three);
		// Raids, totems, elytra: only the first counts.
		assertEquals(skill(Stats.player().custom("raid_win", 1).compute("A"), "milestones"),
			skill(Stats.player().custom("raid_win", 600).compute("A"), "milestones"), EPSILON);
		assertEquals(skill(Stats.player().used("totem_of_undying", 1).compute("A"), "milestones"),
			skill(Stats.player().used("totem_of_undying", 150).compute("A"), "milestones"), EPSILON);
		assertEquals(skill(Stats.player().pickedUp("elytra", 1).compute("A"), "milestones"),
			skill(Stats.player().pickedUp("elytra", 50).compute("A"), "milestones"), EPSILON);
	}

	@Test
	void clickingABeaconIsNoMilestone() {
		assertEquals(0, skill(Stats.player().custom("interact_with_beacon", 5_000).custom("open_enderchest", 5_000)
			.compute("A"), "milestones"), EPSILON);
	}

	@Test
	void mostActiveNeedsTwoHoursAndAfkLowersIt() {
		assertEquals(0, one(Stats.player().hours(1).mined("stone", 5_000).compute("A")).actionsPerHour(), EPSILON);
		double busy = one(Stats.player().hours(10).mined("stone", 5_000).compute("A")).actionsPerHour();
		double afk = one(Stats.player().hours(100).mined("stone", 5_000).compute("A")).actionsPerHour();
		assertEquals(500, busy, 1e-6);
		assertTrue(afk < busy);
	}

	@Test
	void boardsReadTheFields() {
		PlayerRecord player = one(Stats.player().allRounder(1).compute("Alex"));
		assertEquals(player.level(), player.value("legends"), EPSILON);
		assertEquals(player.playHours(), player.value("veterans"), EPSILON);
		assertEquals(player.actionsPerHour(), player.value("grinders"), EPSILON);
		for (String skill : Skills.ORDER) {
			assertEquals(player.skills().get(skill), player.value(PlayerRecord.SKILL_BOARD + skill), EPSILON, skill);
		}
	}

	@Test
	void equalLevelsSortByLessPlayTimeThenName() {
		List<PlayerRecord> ranked = rank(
			Stats.player().hours(20).compute("Slow"),
			Stats.player().hours(5).compute("Bea"),
			Stats.player().hours(5).compute("Abe")
		);
		// Play time alone gives survival points; with no deaths, more hours rank higher.
		assertEquals("Slow", ranked.getFirst().name());
		List<PlayerRecord> tied = rank(Stats.player().compute("Bea"), Stats.player().compute("Abe"));
		assertEquals(List.of("Abe", "Bea"), tied.stream().map(player -> player.name()).toList());
	}

	@Test
	void playHoursComeFromPlayTime() {
		assertEquals(3, Stats.player().hours(3).compute("Alex").playHours(), 0.001);
	}
}
