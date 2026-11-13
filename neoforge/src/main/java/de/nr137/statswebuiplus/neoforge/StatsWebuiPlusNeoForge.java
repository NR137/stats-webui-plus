package de.nr137.statswebuiplus.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import de.nr137.statswebuiplus.StatsWebuiPlus;

@Mod(StatsWebuiPlus.MOD_ID)
public class StatsWebuiPlusNeoForge {
	public StatsWebuiPlusNeoForge() {
		StatsWebuiPlus.init(new NeoForgePlatform());
		NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> StatsWebuiPlus.onServerStarted(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> StatsWebuiPlus.onServerStopping());
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> StatsWebuiPlus.onServerTick(event.getServer()));
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> StatsWebuiPlus.onPlayerJoin(event.getEntity().getUUID()));
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> StatsWebuiPlus.onPlayerLeave(event.getEntity().getUUID()));
	}
}
