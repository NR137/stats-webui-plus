package de.nr137.statswebuiplus.fabric;

import net.fabricmc.loader.api.FabricLoader;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.Platform;

import java.nio.file.Path;

final class FabricPlatform implements Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public String modVersion() {
		return version(StatsWebuiPlus.MOD_ID);
	}

	@Override
	public String minecraftVersion() {
		return version("minecraft");
	}

	private static String version(String modId) {
		return FabricLoader.getInstance()
			.getModContainer(modId)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}
}
