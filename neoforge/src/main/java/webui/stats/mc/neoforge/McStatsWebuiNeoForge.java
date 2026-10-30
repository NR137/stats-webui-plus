package webui.stats.mc.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import webui.stats.mc.McStatsWebui;

@Mod(McStatsWebui.MOD_ID)
public class McStatsWebuiNeoForge {
	public McStatsWebuiNeoForge() {
		McStatsWebui.init(new NeoForgePlatform());
		NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> McStatsWebui.onServerStarted(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> McStatsWebui.onServerStopping());
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> McStatsWebui.onServerTick(event.getServer()));
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> McStatsWebui.onPlayerJoin(event.getEntity().getUUID()));
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> McStatsWebui.onPlayerLeave(event.getEntity().getUUID()));
	}
}
