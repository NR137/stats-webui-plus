package webui.stats.mc.fabric.gametest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import webui.stats.mc.gametest.WorldSwitcher;

/** The singleplayer test's world switch (see {@link WorldSwitcher}), in the smokeClient run. */
public class FabricSmokeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> WorldSwitcher.onClientTick());
	}
}
