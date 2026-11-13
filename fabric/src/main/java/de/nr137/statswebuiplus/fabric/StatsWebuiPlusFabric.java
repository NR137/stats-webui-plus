package de.nr137.statswebuiplus.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import de.nr137.statswebuiplus.StatsWebuiPlus;

public class StatsWebuiPlusFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		StatsWebuiPlus.init(new FabricPlatform());
		ServerLifecycleEvents.SERVER_STARTED.register(StatsWebuiPlus::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> StatsWebuiPlus.onServerStopping());
		ServerTickEvents.END_SERVER_TICK.register(StatsWebuiPlus::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StatsWebuiPlus.onPlayerJoin(handler.getPlayer().getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> StatsWebuiPlus.onPlayerLeave(handler.getPlayer().getUUID()));
	}
}
