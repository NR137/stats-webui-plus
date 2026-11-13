package de.nr137.statswebuiplus;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.nr137.statswebuiplus.stats.AdvancementCatalog;
import de.nr137.statswebuiplus.stats.SessionLog;
import de.nr137.statswebuiplus.stats.StatsCache;
import de.nr137.statswebuiplus.stats.VanillaCatalog;
import de.nr137.statswebuiplus.web.StatsHttpServer;

import java.nio.file.Path;
import java.util.UUID;

/** Loader-independent entry point; the Fabric and NeoForge entry classes forward their events here. */
public final class StatsWebuiPlus {
	public static final String MOD_ID = "stats_webui_plus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Platform platform;

	private StatsWebuiPlus() {
	}

	public static void init(Platform loaderPlatform) {
		platform = loaderPlatform;
	}

	public static Platform platform() {
		if (platform == null) {
			throw new IllegalStateException("StatsWebuiPlus.init was not called by the loader entry point");
		}
		return platform;
	}

	public static void onServerStarted(MinecraftServer server) {
		VanillaCatalog.boot(server);
		// A failed bind is logged there; the game runs on without the web UI.
		StatsHttpServer.getInstance().start(server);
	}

	public static void onServerStopping() {
		StatsHttpServer.getInstance().stop();
	}

	/**
	 * After /reload: data packs may have added or removed advancements, and the config is read again for setups
	 * where the file watcher misses changes (network drives). Server thread.
	 */
	public static void onDataPacksReloaded(MinecraftServer server) {
		StatsHttpServer.getInstance().reloadConfig("/reload");
		AdvancementCatalog.get().boot(server);
		VanillaCatalog.boot(server);
		StatsCache.get().markAllDirty();
		LOGGER.info("Data packs reloaded: {} advancements", AdvancementCatalog.get().size());
	}

	public static void onPlayerJoin(UUID player) {
		SessionLog.get().onJoin(player);
	}

	public static void onPlayerLeave(UUID player) {
		SessionLog.get().onLeave(player);
	}

	public static void onServerTick(MinecraftServer server) {
		int seconds = StatsHttpServer.getInstance().config().saveIntervalSeconds();
		if (seconds > 0 && server.getTickCount() % (seconds * 20) == 0) {
			// Only for crash safety: the web UI reads live stats from memory and needs no saved files.
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.getStats().save();
				player.getAdvancements().save();
			}
		}
	}

	/** Returns {@code config/<mod id><suffix>}; tools like VoxelDash match config files by mod ID. */
	public static Path configFile(String suffix) {
		return platform().configDir().resolve(MOD_ID + suffix);
	}

	/** Returns {@code <world>/<mod id>/}, the mod's data in the save. */
	public static Path worldDataDir(Path worldRoot) {
		return worldRoot.resolve(MOD_ID);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
