package webui.stats.mc.web;

import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import webui.stats.mc.ConfigWatcher;
import webui.stats.mc.McStatsWebui;
import webui.stats.mc.WebConfig;
import webui.stats.mc.stats.AdvancementCatalog;
import webui.stats.mc.stats.SessionLog;
import webui.stats.mc.stats.PlayerRecord;
import webui.stats.mc.stats.StatsCache;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class StatsHttpServer {
	private static final StatsHttpServer INSTANCE = new StatsHttpServer();

	private volatile WebConfig config = WebConfig.defaults();
	private volatile ApiHandler apiHandler = new ApiHandler(config);
	private final StaticHandler staticHandler = new StaticHandler();
	private final ServerIconHandler serverIconHandler = new ServerIconHandler();
	private final MojangProxy mojangProxy = new MojangProxy();
	private HttpServer httpServer;
	private ThreadPoolExecutor executor;
	private ConfigWatcher watcher;
	private volatile MinecraftServer minecraft;

	private StatsHttpServer() {
	}

	public static StatsHttpServer getInstance() {
		return INSTANCE;
	}

	public WebConfig config() {
		return config;
	}

	public synchronized void start(MinecraftServer server) throws IOException {
		stop();
		this.config = WebConfig.load();
		this.apiHandler = new ApiHandler(config);
		this.minecraft = server;
		int threads = config.webThreadCount();
		executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
			Thread thread = new Thread(runnable, McStatsWebui.MOD_ID);
			thread.setDaemon(true);
			return thread;
		});
		staticHandler.preload();
		Path world = apiHandler.resolveWorld(server);
		AdvancementCatalog.get().boot(server);
		StatsCache.get().boot(world, Map.of());
		// Most played first: they lead the leaderboards and the home page, so their heads are wanted first.
		mojangProxy.prewarm(
			StatsCache.get().current().players().stream()
				.sorted(Comparator.comparingDouble((PlayerRecord player) -> player.playHours).reversed())
				.map(player -> player.uuid)
				.toList()
		);
		// Not cleared in stop(): players are disconnected after the server-stopping event, and their logout should count.
		SessionLog.get().boot(world);
		httpServer = bind(config);
		httpServer.start();
		logHeaderImage();
		McStatsWebui.LOGGER.info(
			"Stats web UI listening on http://{}:{}/ world={}, {} threads",
			config.bind(),
			config.port(),
			config.world().isEmpty() ? "(active save)" : config.world(),
			threads
		);
		watcher = new ConfigWatcher(WebConfig.path(), () -> reloadConfig("file changed"));
		watcher.start();
	}

	/**
	 * Applies an edited config while the server runs, after the file changed or on /reload. A new port or bind
	 * address rebinds the web server; requests on the way are cut off, and their visitors reload the page. A new
	 * world folder needs a restart: the stats cache and the session log would have to start over.
	 */
	public synchronized void reloadConfig(String reason) {
		if (httpServer == null) {
			return;
		}
		WebConfig next = WebConfig.reload();
		if (next == null) {
			return;
		}
		WebConfig previous = config;
		if (!next.bind().equals(previous.bind()) || next.port() != previous.port()) {
			// The old socket is closed first: the new address may overlap it (0.0.0.0 and 127.0.0.1, same port).
			httpServer.stop(0);
			try {
				httpServer = bind(next);
			} catch (IOException e) {
				McStatsWebui.LOGGER.error(
					"Could not bind the stats web server to {}:{}, keeping the current config", next.bind(), next.port(), e
				);
				try {
					httpServer = bind(previous);
					httpServer.start();
				} catch (IOException again) {
					httpServer = null;
					McStatsWebui.LOGGER.error("Could not bind the stats web server again; restart the server", again);
				}
				return;
			}
			httpServer.start();
			McStatsWebui.LOGGER.info("Stats web UI now listening on http://{}:{}/", next.bind(), next.port());
		}
		if (!next.world().equals(previous.world())) {
			McStatsWebui.LOGGER.warn("The world folder changes after a restart of the server");
		}
		resizeExecutor(next.webThreadCount());
		config = next;
		apiHandler = new ApiHandler(next);
		if (!next.headerImage().equals(previous.headerImage())) {
			logHeaderImage();
		}
		McStatsWebui.LOGGER.info("Reloaded config ({}); open pages show changes after a reload", reason);
	}

	/** A new webThreads applies at once: busy threads finish their request first. */
	private void resizeExecutor(int threads) {
		if (executor == null || executor.getCorePoolSize() == threads) {
			return;
		}
		// The core size may never exceed the maximum, so the order depends on the direction.
		if (threads > executor.getMaximumPoolSize()) {
			executor.setMaximumPoolSize(threads);
			executor.setCorePoolSize(threads);
		} else {
			executor.setCorePoolSize(threads);
			executor.setMaximumPoolSize(threads);
		}
		McStatsWebui.LOGGER.info("Stats web UI now answers on {} threads", threads);
	}

	private HttpServer bind(WebConfig settings) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(settings.bind(), settings.port()), 0);
		server.setExecutor(executor);
		server.createContext("/api", exchange -> {
			try {
				MinecraftServer current = minecraft;
				if (current == null) {
					HttpJson.error(exchange, 503, "unavailable", "Minecraft server is not ready");
					return;
				}
				apiHandler.handle(exchange, current);
			} catch (Exception e) {
				McStatsWebui.LOGGER.error("Unhandled API error", e);
				if (exchange.getResponseCode() == -1) {
					HttpJson.error(exchange, 500, "internal_error", "Internal server error");
				}
			} finally {
				exchange.close();
			}
		});
		server.createContext("/mojang", exchange -> {
			// Not closed here: a response that waits for Mojang is finished, and closed, on a fetch thread.
			try {
				mojangProxy.handle(exchange);
			} catch (Exception e) {
				McStatsWebui.LOGGER.error("Unhandled Mojang proxy error", e);
				exchange.close();
			}
		});
		server.createContext("/", exchange -> {
			try {
				String path = exchange.getRequestURI().getPath();
				if (path.startsWith("/api") || path.startsWith("/mojang")) {
					HttpJson.error(exchange, 404, "not_found", "Unknown endpoint");
					return;
				}
				if ("/server-icon.png".equals(path)) {
					serverIconHandler.handle(exchange);
					return;
				}
				if (HeaderImageHandler.PATH.equals(path)) {
					HeaderImageHandler.handle(exchange, config);
					return;
				}
				staticHandler.handle(exchange);
			} catch (Exception e) {
				McStatsWebui.LOGGER.error("Unhandled static error", e);
			} finally {
				exchange.close();
			}
		});
		return server;
	}

	private void logHeaderImage() {
		if (!config.headerImage().isEmpty()) {
			Path headerImage = HeaderImageHandler.file(config);
			if (headerImage != null) {
				McStatsWebui.LOGGER.info("Showing header image {}", headerImage);
			} else {
				McStatsWebui.LOGGER.info(
					"No header image: put one at config/{} or set \"headerImage\" to \"\" to stop looking",
					config.headerImage()
				);
			}
		}
	}

	public synchronized void stop() {
		if (watcher != null) {
			watcher.stop();
			watcher = null;
		}
		if (httpServer != null) {
			httpServer.stop(0);
			httpServer = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
		minecraft = null;
		StatsCache.get().clear();
		AdvancementCatalog.get().clear();
	}
}
