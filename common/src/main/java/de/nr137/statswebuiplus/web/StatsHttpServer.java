package de.nr137.statswebuiplus.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import de.nr137.statswebuiplus.ConfigWatcher;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.api.AdvancementView;
import de.nr137.statswebuiplus.assets.GameAssets;
import de.nr137.statswebuiplus.assets.MojangDownloads;
import de.nr137.statswebuiplus.stats.AdvancementCatalog;
import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.stats.SessionLog;
import de.nr137.statswebuiplus.stats.StatsCache;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The web server: the website, the API and the skin proxy on the configured address, from server start (once the
 * game's files are settled) until the server stops. Config changes apply while it runs, a new address too.
 */
public final class StatsHttpServer {
	private static final StatsHttpServer INSTANCE = new StatsHttpServer();
	private static final String API = "/api";
	private static final String MOJANG = "/mojang";

	private final MojangProxy mojang = new MojangProxy();
	private final AtomicInteger threadNumber = new AtomicInteger();

	// All below under this lock; config and minecraft are read by request threads without it.
	private final Object lock = new Object();
	private volatile WebConfig config = WebConfig.defaults();
	private volatile MinecraftServer minecraft;
	private HttpServer http;
	private ThreadPoolExecutor pool;
	private ConfigWatcher watcher;
	private StaticHandler site;
	private ApiHandler api;
	private Path world;
	// Counts starts and stops, so an open that waited for the game's files doesn't happen after either.
	private long starts;

	private StatsHttpServer() {
	}

	public static StatsHttpServer getInstance() {
		return INSTANCE;
	}

	/** The config in use: the defaults until the first start. */
	public WebConfig config() {
		return config;
	}

	/** Server start: everything the web UI needs; the web server opens once the game's files are settled. */
	public void start(MinecraftServer server) {
		long start;
		synchronized (lock) {
			stop();
			WebConfig loaded = WebConfig.load();
			config = loaded;
			minecraft = server;
			start = ++starts;
			int threads = loaded.webThreadCount();
			pool = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
				Thread thread = new Thread(runnable, StatsWebuiPlus.MOD_ID + "-web-" + threadNumber.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			});
			site = new StaticHandler(this::config);
			site.preload();
			api = new ApiHandler(this::config, ApiHandler.cache(server), AdvancementView::catalog);
			GameAssets.get().start(GameAssets.cacheRoot(), SharedConstants.getCurrentVersion().id(), loaded.downloadGameAssets(),
				new MojangDownloads());
			world = worldFolder(server, loaded);
			AdvancementCatalog.get().boot(server);
			StatsCache.get().boot(world, Map.of());
			mojang.prewarm(byPlayTime(StatsCache.get().current().players()));
			// Not cleared on stop: logouts after the stop event still count.
			SessionLog.get().boot(world);
		}
		if (GameAssets.get().state() == GameAssets.State.PREPARING) {
			StatsWebuiPlus.LOGGER.info("The stats web UI opens once the game's icons are drawn (once per Minecraft version)");
		}
		GameAssets.get().whenSettled(() -> open(start));
	}

	/** The config file changed or /reload ran: applies the new config to the open web server. */
	public void reloadConfig(String reason) {
		synchronized (lock) {
			if (http == null) {
				return;
			}
			WebConfig next = WebConfig.reload();
			if (next == null) {
				return;
			}
			WebConfig current = config;
			if (!next.bind().equals(current.bind()) || next.port() != current.port()) {
				// The old socket first: the new address may overlap it (0.0.0.0 and a single address, the same port).
				http.stop(0);
				http = bind(next, "Could not bind the stats web server to " + next.bind() + ":" + next.port()
					+ ", keeping the current config");
				if (http == null) {
					http = bind(current, "Could not bind the stats web server again; restart the server");
					return;
				}
			}
			if (!next.world().equals(current.world())) {
				StatsWebuiPlus.LOGGER.info("The world folder changes after a restart of the server");
			}
			int threads = next.webThreadCount();
			if (threads != pool.getCorePoolSize()) {
				// Never a core bigger than the maximum, which both setters refuse.
				if (threads > pool.getMaximumPoolSize()) {
					pool.setMaximumPoolSize(threads);
					pool.setCorePoolSize(threads);
				} else {
					pool.setCorePoolSize(threads);
					pool.setMaximumPoolSize(threads);
				}
				StatsWebuiPlus.LOGGER.info("Stats web UI now answers on {} threads", threads);
			}
			if (next.downloadGameAssets() != current.downloadGameAssets()) {
				GameAssets.get().start(GameAssets.cacheRoot(), SharedConstants.getCurrentVersion().id(), next.downloadGameAssets(),
					new MojangDownloads());
			}
			config = next;
			if (!next.headerImage().equals(current.headerImage())) {
				logHeaderImage(next);
			}
			StatsWebuiPlus.LOGGER.info("Reloaded config ({}); open pages show changes after a reload", reason);
		}
	}

	/** Server stopping: closes everything; a start still waiting for the game's files won't open. */
	public void stop() {
		synchronized (lock) {
			starts++;
			if (watcher != null) {
				watcher.stop();
				watcher = null;
			}
			if (http != null) {
				http.stop(0);
				http = null;
			}
			if (pool != null) {
				pool.shutdown();
				pool = null;
			}
			minecraft = null;
			api = null;
			site = null;
			StatsCache.get().clear();
			AdvancementCatalog.get().clear();
		}
	}

	/** Once the game's files are settled; nothing if the server stopped or started again meanwhile. */
	private void open(long start) {
		synchronized (lock) {
			if (start != starts || minecraft == null) {
				return;
			}
			WebConfig current = config;
			http = bind(current, "Could not bind the stats web server");
			if (http == null) {
				return;
			}
			logHeaderImage(current);
			StatsWebuiPlus.LOGGER.info("Stats web UI listening on http://{}:{}/ world={}, {} threads", current.bind(), current.port(),
				current.world().isEmpty() ? "(active save)" : world, pool.getCorePoolSize());
			watcher = new ConfigWatcher(WebConfig.path(), () -> reloadConfig("file changed"));
			watcher.start();
		}
	}

	/** A started web server on the config's address, or null if it can't bind there (logged with this message). */
	private HttpServer bind(WebConfig target, String failure) {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(target.bind(), target.port()), 0);
			server.createContext(API, guarded(this::api, true));
			// The proxy answers from its own threads and closes its exchanges itself.
			server.createContext(MOJANG, guarded(mojang::handle, false));
			server.createContext("/", guarded(this::other, true));
			server.setExecutor(pool);
			server.start();
			return server;
		} catch (IOException | RuntimeException e) {
			StatsWebuiPlus.LOGGER.error(failure, e);
			return null;
		}
	}

	private void api(HttpExchange exchange) throws IOException {
		ApiHandler handler = api;
		if (minecraft == null || handler == null) {
			HttpJson.error(exchange, 503, "unavailable", "Minecraft server is not ready");
			return;
		}
		handler.handle(exchange);
	}

	private void other(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		if (FaviconHandler.PATH.equals(path)) {
			FaviconHandler.handle(exchange, config);
		} else if (HeaderImageHandler.PATH.equals(path)) {
			HeaderImageHandler.handle(exchange, config);
		} else if (path.startsWith(HeadingFontHandler.PATH)) {
			HeadingFontHandler.handle(exchange, config);
		} else if (isUnder(path, API) || isUnder(path, MOJANG)) {
			// Only reached if the server's context matching lets such a path through to "/".
			HttpJson.error(exchange, 404, "not_found", "Unknown endpoint");
		} else {
			StaticHandler handler = site;
			if (handler == null) {
				HttpJson.error(exchange, 503, "unavailable", "Minecraft server is not ready");
				return;
			}
			handler.handle(exchange);
		}
	}

	private static boolean isUnder(String path, String prefix) {
		return path != null && (path.equals(prefix) || path.startsWith(prefix + "/"));
	}

	/**
	 * A visitor who went away is no error (logged at debug level only); anything else is logged and answered with a
	 * 500 if nothing was sent yet. The exchange is closed at the end, unless the handler closes it itself.
	 */
	private static HttpHandler guarded(HttpHandler handler, boolean close) {
		return exchange -> {
			try {
				handler.handle(exchange);
			} catch (HttpJson.ConnectionLost e) {
				StatsWebuiPlus.LOGGER.debug("Connection lost: {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), e);
				exchange.close();
			} catch (Exception e) {
				StatsWebuiPlus.LOGGER.error("Web request failed: {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), e);
				if (exchange.getResponseCode() == -1) {
					try {
						HttpJson.error(exchange, 500, "internal_error", "Internal error");
					} catch (IOException ignored) {
						// Gone as well.
					}
				}
				exchange.close();
			} finally {
				if (close) {
					exchange.close();
				}
			}
		};
	}

	private static void logHeaderImage(WebConfig config) {
		if (config.headerImage().isEmpty()) {
			return;
		}
		Path file = HeaderImageHandler.file(config);
		if (file != null) {
			StatsWebuiPlus.LOGGER.info("Showing header image {}", file);
		} else {
			StatsWebuiPlus.LOGGER.info("No header image: put one at config/{} or set \"headerImage\" to \"\" to stop looking",
				config.headerImage());
		}
	}

	/** The config's world folder (relative to the game folder), or the running save's. */
	private static Path worldFolder(MinecraftServer server, WebConfig config) {
		Path folder = config.world().isEmpty()
			? server.getWorldPath(LevelResource.ROOT)
			: StatsWebuiPlus.platform().gameDir().resolve(config.world());
		return folder.toAbsolutePath().normalize();
	}

	/** Most play time first: their skins are the ones most pages show. */
	private static List<UUID> byPlayTime(List<PlayerRecord> players) {
		List<PlayerRecord> sorted = new ArrayList<>(players);
		sorted.sort(Comparator.comparingDouble((PlayerRecord player) -> player.playHours()).reversed());
		List<UUID> uuids = new ArrayList<>(sorted.size());
		for (PlayerRecord player : sorted) {
			uuids.add(player.uuid());
		}
		return uuids;
	}
}
