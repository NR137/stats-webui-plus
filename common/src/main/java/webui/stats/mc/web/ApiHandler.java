package webui.stats.mc.web;

import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import webui.stats.mc.McStatsWebui;
import webui.stats.mc.WebConfig;
import webui.stats.mc.stats.AdvancementCatalog;
import webui.stats.mc.stats.AdvancementInfo;
import webui.stats.mc.stats.AdvancementService;
import webui.stats.mc.stats.PlayerRecord;
import webui.stats.mc.stats.StatsCache;
import webui.stats.mc.stats.StatsService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public final class ApiHandler {
	private final WebConfig config;

	public ApiHandler(WebConfig config) {
		this.config = config;
	}

	public void handle(HttpExchange exchange, MinecraftServer server) throws IOException {
		String method = exchange.getRequestMethod();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			HttpJson.error(exchange, 405, "method_not_allowed", "Use GET");
			return;
		}

		// Already percent-decoded once, as the web UI encodes each part once (encodeURIComponent). Decoding again
		// would turn a "+" into a space and break names with "+" or "%" (Floodgate prefixes can be any text).
		String path = exchange.getRequestURI().getPath();
		if (path.endsWith("/") && path.length() > 1) {
			path = path.substring(0, path.length() - 1);
		}

		try {
			// Answered without refreshing the stats, so health checks and status polls never wake the server thread.
			if (path.equals("/api")) {
				HttpJson.send(exchange, 200, apiIndex());
				return;
			}
			if (path.equals("/api/health")) {
				HttpJson.send(exchange, 200, health());
				return;
			}
			if (path.equals("/api/status")) {
				// Every open tab asks every 30 s, but only for the tracked player count; the cache's is close enough.
				// Never cached: it costs nothing now, and the online count must be the server's current one.
				HttpJson.send(exchange, 200, status(StatsCache.get().current().players(), server));
				return;
			}
			StatsCache.Snapshot snapshot = StatsCache.get().refresh(server, config.refreshCooldownMs());
			List<PlayerRecord> players = snapshot.players();
			String cache = cacheControl(snapshot.refreshedAt());
			switch (path) {
				case "/api/players" -> HttpJson.send(exchange, 200, StatsService.playersPayload(players), cache);
				case "/api/leaderboards" -> HttpJson.send(exchange, 200, StatsService.catalogPayload(players), cache);
				case "/api/crowns" -> HttpJson.send(exchange, 200, StatsService.crownsPayload(players), cache);
				case "/api/advancements" -> HttpJson.send(exchange, 200, AdvancementService.catalogPayload(players), cache);
				case "/api/advancements/most" -> {
					LeaderboardInfo info = LeaderboardCatalog.find("advancements");
					HttpJson.send(exchange, 200, StatsService.leaderboardPayload(info, players), cache);
				}
				default -> handleDynamic(exchange, path, players, cache);
			}
		} catch (TimeoutException e) {
			// Not an error of the mod: the game was too busy (lag spike, big command) to hand over the stats in time.
			McStatsWebui.LOGGER.warn("Server thread busy for 5 s, answered a stats request with 503");
			exchange.getResponseHeaders().set("Retry-After", "5");
			HttpJson.error(exchange, 503, "busy", "The Minecraft server is busy, try again in a moment");
		} catch (Exception e) {
			McStatsWebui.LOGGER.error("API request failed", e);
			HttpJson.error(exchange, 500, "internal_error", "Internal server error");
		}
	}

	private void handleDynamic(HttpExchange exchange, String path, List<PlayerRecord> players, String cache) throws IOException {
		if (path.startsWith("/api/players/")) {
			String name = path.substring("/api/players/".length());
			PlayerRecord player = StatsService.find(players, name);
			if (player == null) {
				HttpJson.error(exchange, 404, "not_found", "Player not found");
				return;
			}
			HttpJson.send(exchange, 200, StatsService.playerPayload(player, players), cache);
			return;
		}
		if (path.startsWith("/api/leaderboards/")) {
			String id = path.substring("/api/leaderboards/".length());
			LeaderboardInfo info = StatsService.resolve(id, players);
			if (info == null) {
				HttpJson.error(exchange, 404, "not_found", "Unknown leaderboard");
				return;
			}
			HttpJson.send(exchange, 200, StatsService.leaderboardPayload(info, players), cache);
			return;
		}
		if (path.startsWith("/api/advancements/")) {
			String raw = path.substring("/api/advancements/".length());
			String id = toAdvancementId(raw);
			AdvancementInfo info = AdvancementCatalog.get().find(id);
			if (info == null) {
				HttpJson.error(exchange, 404, "not_found", "Unknown advancement");
				return;
			}
			HttpJson.send(exchange, 200, AdvancementService.detailPayload(info, players), cache);
			return;
		}
		HttpJson.error(exchange, 404, "not_found", "Unknown endpoint");
	}

	/** Lets browsers and proxies keep a response until the stats behind it would be refreshed anyway. */
	private String cacheControl(long refreshedAt) {
		long cooldownMs = config.refreshCooldownMs();
		if (cooldownMs <= 0) {
			return "no-store";
		}
		long ageMs = (System.nanoTime() - refreshedAt) / 1_000_000L;
		// Rounded down, so a cached copy never outlives the stats it was built from.
		long maxAge = Math.max(0, (cooldownMs - ageMs) / 1000);
		return "public, max-age=" + maxAge;
	}

	private Map<String, Object> apiIndex() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("health", "/api/health");
		body.put("status", "/api/status");
		body.put("players", "/api/players");
		body.put("leaderboards", "/api/leaderboards");
		body.put("crowns", "/api/crowns");
		body.put("advancements", "/api/advancements");
		return body;
	}

	private Map<String, Object> health() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ok", true);
		body.put("mod", McStatsWebui.MOD_ID);
		body.put("version", modVersion());
		body.put("minecraft", minecraftVersion());
		return body;
	}

	private Map<String, Object> status(List<PlayerRecord> players, MinecraftServer server) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("bind", config.bind());
		body.put("trackedPlayers", players.size());
		// For the status bar; the web UI asks again every 30 s.
		body.put("onlinePlayers", server.getPlayerCount());
		body.put("maxPlayers", server.getMaxPlayers());
		body.put("branding", config.brandingPayload());
		body.put("headerImage", HeaderImageHandler.url(config));
		body.put("languages", config.languages());
		body.put("links", config.linksPayload());
		body.put("defaultLanguage", config.defaultLanguage());
		return body;
	}

	public Path resolveWorld(MinecraftServer server) {
		String folder = config.world();
		if (folder.isEmpty()) {
			return server.getWorldPath(LevelResource.ROOT);
		}
		return McStatsWebui.platform().gameDir().resolve(folder);
	}

	private static String toAdvancementId(String path) {
		int slash = path.indexOf('/');
		if (slash <= 0) {
			return path;
		}
		return path.substring(0, slash) + ":" + path.substring(slash + 1);
	}

	private static String modVersion() {
		return McStatsWebui.platform().modVersion();
	}

	private static String minecraftVersion() {
		return McStatsWebui.platform().minecraftVersion();
	}
}
