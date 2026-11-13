package de.nr137.statswebuiplus;

import java.nio.file.Path;

/**
 * The few things the mod needs from the mod loader. Fabric and NeoForge each pass an implementation to
 * {@link StatsWebuiPlus#init(Platform)}.
 */
public interface Platform {
	Path configDir();

	Path gameDir();

	String modVersion();

	String minecraftVersion();
}
