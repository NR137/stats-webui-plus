package de.nr137.statswebuiplus.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import de.nr137.statswebuiplus.gametest.LiveTests;

/** Registers the shared {@link LiveTests} with Fabric's game test API, one method per test. */
public class FabricGameTests {
	// Ticks, not time: the test server ticks as fast as it can. Enough for a few cache refreshes of up to 5 s each
	// (LiveTests.Refresh); a test that passes ends as soon as its checks do.
	private static final int MAX_TICKS = 4_000;

	@GameTest(maxTicks = MAX_TICKS)
	public void webServerStarted(GameTestHelper helper) {
		LiveTests.all().get("web_server_started").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void craftableItemsFromRecipesAndTrades(GameTestHelper helper) {
		LiveTests.all().get("craftable_items_from_recipes_and_trades").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void joinAndLeave(GameTestHelper helper) {
		LiveTests.all().get("join_and_leave").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void statsReachTheCache(GameTestHelper helper) {
		LiveTests.all().get("stats_reach_the_cache").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void advancementsReachTheCache(GameTestHelper helper) {
		LiveTests.all().get("advancements_reach_the_cache").accept(helper);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void revokedAdvancementLeavesTheCache(GameTestHelper helper) {
		LiveTests.all().get("revoked_advancement_leaves_the_cache").accept(helper);
	}

	// See LiveTests.ISOLATED; the environment is data/stats_webui_plus_gametest/test_environment/isolated.json.
	@GameTest(maxTicks = MAX_TICKS, environment = "stats_webui_plus_gametest:isolated")
	public void saveIntervalAndBusyServer(GameTestHelper helper) {
		LiveTests.all().get("save_interval_and_busy_server").accept(helper);
	}
}
