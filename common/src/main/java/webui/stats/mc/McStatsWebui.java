package webui.stats.mc;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import webui.stats.mc.stats.AdvancementCatalog;
import webui.stats.mc.stats.SessionLog;
import webui.stats.mc.stats.StatsCache;
import webui.stats.mc.web.StatsHttpServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Loader-independent entry point; the Fabric and NeoForge entry classes forward their events here. */
public final class McStatsWebui {
	public static final String MOD_ID = "stats_webui_plus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Platform platform;

	private McStatsWebui() {
	}

	public static void init(Platform loaderPlatform) {
		platform = loaderPlatform;
	}

	public static Platform platform() {
		if (platform == null) {
			throw new IllegalStateException("McStatsWebui.init was not called by the loader entry point");
		}
		return platform;
	}

	public static void onServerStarted(MinecraftServer server) {
		try {
			StatsHttpServer.getInstance().start(server);
		} catch (IOException e) {
			LOGGER.error("Could not bind the stats web server", e);
		}
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
