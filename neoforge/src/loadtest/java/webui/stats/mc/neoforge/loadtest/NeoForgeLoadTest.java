package webui.stats.mc.neoforge.loadtest;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import webui.stats.mc.loadtest.LoadPlayers;

/** Online players for the load test; see common/src/loadtest. */
@Mod("stats_webui_plus_loadtest")
public final class NeoForgeLoadTest {
	public NeoForgeLoadTest() {
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> LoadPlayers.start(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> LoadPlayers.tick(event.getServer()));
	}
}
