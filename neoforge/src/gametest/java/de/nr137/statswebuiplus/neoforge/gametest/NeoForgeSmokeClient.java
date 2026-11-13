package de.nr137.statswebuiplus.neoforge.gametest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.gametest.WorldSwitcher;

/** The singleplayer test's world switch (see {@link WorldSwitcher}), in the smokeClient run. */
@EventBusSubscriber(modid = StatsWebuiPlus.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeSmokeClient {
	private NeoForgeSmokeClient() {
	}

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Post event) {
		WorldSwitcher.onClientTick();
	}
}
