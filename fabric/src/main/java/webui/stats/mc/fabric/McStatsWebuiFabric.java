package webui.stats.mc.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import webui.stats.mc.McStatsWebui;

public class McStatsWebuiFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		McStatsWebui.init(new FabricPlatform());
		ServerLifecycleEvents.SERVER_STARTED.register(McStatsWebui::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> McStatsWebui.onServerStopping());
		ServerTickEvents.END_SERVER_TICK.register(McStatsWebui::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> McStatsWebui.onPlayerJoin(handler.getPlayer().getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> McStatsWebui.onPlayerLeave(handler.getPlayer().getUUID()));
	}
}
