package de.nr137.statswebuiplus.stats;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Leaderboards: the new boards that combine statistics. */
class NewBoardsTest {
	@Test
	void killsPerDeathCountsHostileMobsWithoutSilverfish() {
		PlayerRecord player = Stats.player().hours(10).killed("zombie", 30).killed("skeleton", 10).killed("silverfish", 500)
			.custom("deaths", 4).compute("Fighter");
		assertEquals(10.0, player.value("kills-per-death"), 1e-9);
	}

	@Test
	void withoutADeathKillsPerDeathAreTheKills() {
		PlayerRecord player = Stats.player().hours(10).killed("zombie", 7).compute("Careful");
		assertEquals(7.0, player.value("kills-per-death"), 1e-9);
	}

	@Test
	void mobTypesCountsEveryKindKilledOnce() {
		PlayerRecord player = Stats.player().hours(1).killed("zombie", 30).killed("cow", 1).killed("silverfish", 500)
			.compute("Hunter");
		assertEquals(3.0, player.value("mob-types"), 1e-9);
	}

	@Test
	void currentLifeIsPlayTimeSinceTheLastDeath() {
		// time_since_death is counted in ticks, 20 per second.
		PlayerRecord player = Stats.player().hours(10).custom("time_since_death", 20L * 3600 * 3).compute("Survivor");
		assertEquals(3.0, player.value("current-life"), 1e-9);
	}

	@Test
	void distancePerHourIsUsefulDistancePerHourPlayed() {
		// 100 000 cm = 1 km.
		PlayerRecord player = Stats.player().hours(2).custom("walk_one_cm", 300_000).custom("boat_one_cm", 100_000)
			.compute("Traveller");
		assertEquals(2.0, player.value("distance-per-hour"), 1e-9);
	}

	@Test
	void totalsAddUpWholeGroups() {
		PlayerRecord player = Stats.player().hours(1).crafted("oak_planks", 12).crafted("stick", 8).mined("stone", 5)
			.compute("Crafter");
		assertEquals(20.0, player.value("items-crafted"), 1e-9);
		assertEquals(5.0, player.value("blocks-mined"), 1e-9);
	}

	@Test
	void hostileMobsCountSilverfishTheOtherBoardNot() {
		PlayerRecord player = Stats.player().hours(1).killed("zombie", 30).killed("silverfish", 500).killed("cow", 4)
			.killed("player", 2).compute("Farmer");
		assertEquals(530.0, player.value("hostile-kills"), 1e-9);
		assertEquals(30.0, player.value("hostile-no-silverfish"), 1e-9);
		// Every kill that isn't a hostile mob; players have their own board.
		assertEquals(4.0, player.value("other-mobs"), 1e-9);
	}
}
