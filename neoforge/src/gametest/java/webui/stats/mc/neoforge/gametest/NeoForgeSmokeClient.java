package webui.stats.mc.neoforge.gametest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import webui.stats.mc.McStatsWebui;
import webui.stats.mc.gametest.WorldSwitcher;

/** The singleplayer test's world switch (see {@link WorldSwitcher}), in the smokeClient run. */
@EventBusSubscriber(modid = McStatsWebui.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeSmokeClient {
	private NeoForgeSmokeClient() {
	}

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Post event) {
		WorldSwitcher.onClientTick();
	}
}
