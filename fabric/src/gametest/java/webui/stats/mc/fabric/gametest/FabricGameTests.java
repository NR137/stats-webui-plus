package webui.stats.mc.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import webui.stats.mc.gametest.LiveTests;

/** Registers the shared {@link LiveTests} with Fabric's game test API, one method per test. */
public class FabricGameTests {
	private static final int MAX_TICKS = 200;

	@GameTest(maxTicks = MAX_TICKS)
	public void webServerStarted(GameTestHelper helper) {
		LiveTests.all().get("web_server_started").accept(helper);
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

	@GameTest(maxTicks = MAX_TICKS)
	public void saveIntervalAndBusyServer(GameTestHelper helper) {
		LiveTests.all().get("save_interval_and_busy_server").accept(helper);
	}
}
