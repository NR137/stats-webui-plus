package webui.stats.mc.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import webui.stats.mc.McStatsWebui;
import webui.stats.mc.Platform;

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
		return version(McStatsWebui.MOD_ID);
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
