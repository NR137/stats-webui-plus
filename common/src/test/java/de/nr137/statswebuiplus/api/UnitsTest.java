package de.nr137.statswebuiplus.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import de.nr137.statswebuiplus.web.LeaderboardCatalog;

/** Numbers, units and the two ID forms (openapi.yaml, "IDs in paths", "Numbers"). */
class UnitsTest {
	@Test
	void numbersAreRoundedToTwoDecimals() {
		assertEquals(69.67, Units.round(69.666_666));
		assertEquals(0.35, Units.round(0.35));
		assertEquals(-1.5, Units.round(-1.499));
	}

	@Test
	void wholeNumbersStayWhole() {
		assertEquals(31L, Units.round(31.0));
		assertEquals(268L, Units.round(268.001));
		assertEquals(0L, Units.round(0.004));
	}

	@Test
	void whatJsonCantHoldIsZero() {
		assertEquals(0L, Units.round(Double.NaN));
		assertEquals(0L, Units.round(Double.POSITIVE_INFINITY));
	}

	@ParameterizedTest(name = "{0} {1} -> {3} {2}")
	@CsvSource({
		"custom, minecraft:walk_one_cm,          150000, 1.5,  km",
		"custom, minecraft:damage_dealt,         45,     2.25, hearts",
		"custom, minecraft:damage_blocked_by_shield, 20, 1,    hearts",
		"custom, minecraft:play_time,            108000, 1.5,  hours",
		"custom, minecraft:time_since_death,     72000,  1,    hours",
		"custom, minecraft:time_since_rest,      36000,  0.5,  hours",
		"custom, minecraft:sneak_time,           72000,  1,    hours",
		"custom, minecraft:total_world_time,     72000,  1,    hours",
		"custom, minecraft:jump,                 177130, 177130, count",
		"custom, minecraft:deaths,               3,      3,    count",
		"mined,  minecraft:stone,                500,    500,  count",
		"killed, minecraft:zombie,               7,      7,    count",
		// Only the custom group has units: a block named like a distance is still a count.
		"mined,  somemod:fake_one_cm,            100000, 100000, count",
	})
	void statisticsInTheirUnits(String group, String id, long raw, double value, String unit) {
		assertEquals(unit, Units.ofStatistic(group, id));
		assertEquals(value, Units.statistic(group, id, raw).doubleValue(), 1e-9);
	}

	@Test
	void skillBoardsAreSkills() {
		assertEquals("skill", Units.of(LeaderboardCatalog.find("skill-mining")));
		assertEquals("level", Units.of(LeaderboardCatalog.find("legends")));
		assertEquals("km", Units.of(LeaderboardCatalog.find("distance-total")));
	}

	@Test
	void unknownTimesAreNull() {
		assertNull(Units.time(0));
		assertEquals(1784418248000L, Units.time(1784418248000L));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
		"jump,                  minecraft:jump",
		"story/root,            minecraft:story/root",
		"minecraft:jump,        minecraft:jump",
		"somemod:thing,         somemod:thing",
		"somemod:quests/first,  somemod:quests/first",
	})
	void pathIdsBecomeFull(String path, String full) {
		assertEquals(full, Units.fullId(path));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
		"minecraft:jump,        jump",
		"minecraft:story/root,  story/root",
		"somemod:quests/first,  somemod:quests/first",
	})
	void fullIdsBecomePathIds(String full, String path) {
		assertEquals(path, Units.pathId(full));
		assertEquals(full, Units.fullId(Units.pathId(full)));
	}
}
