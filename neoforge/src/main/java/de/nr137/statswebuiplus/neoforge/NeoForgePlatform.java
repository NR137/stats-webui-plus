package de.nr137.statswebuiplus.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.Platform;

import java.nio.file.Path;

final class NeoForgePlatform implements Platform {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
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
		return ModList.get()
			.getModContainerById(modId)
			.map(container -> container.getModInfo().getVersion().toString())
			.orElse("unknown");
	}
}
