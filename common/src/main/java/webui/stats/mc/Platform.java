package webui.stats.mc;

import java.nio.file.Path;

/**
 * The few things the mod needs from the mod loader. Fabric and NeoForge each pass an implementation to
 * {@link McStatsWebui#init(Platform)}.
 */
public interface Platform {
	Path configDir();

	Path gameDir();

	String modVersion();

	String minecraftVersion();
}
