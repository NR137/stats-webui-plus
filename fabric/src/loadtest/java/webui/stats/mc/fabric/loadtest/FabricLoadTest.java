package webui.stats.mc.fabric.loadtest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import webui.stats.mc.loadtest.LoadPlayers;

/** Online players for the load test; see common/src/loadtest. */
public final class FabricLoadTest implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(LoadPlayers::start);
		ServerTickEvents.END_SERVER_TICK.register(LoadPlayers::tick);
	}
}
