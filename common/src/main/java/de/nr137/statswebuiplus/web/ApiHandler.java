package de.nr137.statswebuiplus.web;

import com.sun.net.httpserver.HttpExchange;
import net.minecraft.server.MinecraftServer;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.api.AdvancementView;
import de.nr137.statswebuiplus.api.Advancements;
import de.nr137.statswebuiplus.api.Boards;
import de.nr137.statswebuiplus.api.Players;
import de.nr137.statswebuiplus.api.Statistics;
import de.nr137.statswebuiplus.api.Status;
import de.nr137.statswebuiplus.api.Units;
import de.nr137.statswebuiplus.stats.AdvancementInfo;
import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.stats.StatsCache;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The JSON API under {@code /api} (documented in openapi.yaml next to the mod's assets, served at
 * {@code /api/openapi.yaml}). Answers from the stats bring them up to date first and may be cached until the next
 * refresh would happen anyway; the others never touch the server thread.
 */
public final class ApiHandler {
	private static final String PREFIX = "/api/";
	private static final String OPENAPI_RESOURCE = "/assets/" + StatsWebuiPlus.MOD_ID + "/openapi.yaml";
	private static final String YAML = "application/yaml; charset=utf-8";
	private static final int RETRY_AFTER_SECONDS = 5;

	/** Where the players come from: the stats cache and the running server, or players of a test. */
	public interface Source {
		/** The players as they are, without a refresh. */
		StatsCache.Snapshot current();

		/** The players, refreshed unless they matched the game less than {@code maxAgeMs} ago. */
		StatsCache.Snapshot refresh(long maxAgeMs) throws Exception;

		int onlinePlayers();

		int maxPlayers();
	}

	/** One answer from the stats, once they are up to date. */
	private interface StatsAnswer {
		void send(HttpExchange exchange, StatsCache.Snapshot snapshot, String cacheControl) throws IOException;
	}

	private final Supplier<WebConfig> config;
	private final Source source;
	private final Supplier<AdvancementView> advancements;
	private volatile byte[] openApi;

	public ApiHandler(Supplier<WebConfig> config, Source source, Supplier<AdvancementView> advancements) {
		this.config = config;
		this.source = source;
		this.advancements = advancements;
	}

	/** The stats cache, refreshed from this server. */
	public static Source cache(MinecraftServer server) {
		return new Source() {
			@Override
			public StatsCache.Snapshot current() {
				return StatsCache.get().current();
			}

			@Override
			public StatsCache.Snapshot refresh(long maxAgeMs) throws Exception {
				return StatsCache.get().refresh(server, maxAgeMs);
			}

			@Override
			public int onlinePlayers() {
				return server.getPlayerCount();
			}

			@Override
			public int maxPlayers() {
				return server.getMaxPlayers();
			}
		};
	}

	public void handle(HttpExchange exchange) throws IOException {
		if (!HttpJson.isGetOrHead(exchange)) {
			exchange.getResponseHeaders().set("Allow", "GET, HEAD");
			HttpJson.error(exchange, 405, "method_not_allowed", "Use GET");
			return;
		}
		// Decoded once already; decoding again would turn a "+" or "%" in a name into something else.
		String path = exchange.getRequestURI().getPath();
		if (path.length() > 1 && path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		try {
			answer(exchange, path);
		} catch (HttpJson.ConnectionLost e) {
			throw e;
		} catch (TimeoutException e) {
			StatsWebuiPlus.LOGGER.warn("Server thread busy for {} s, answered a stats request with 503", RETRY_AFTER_SECONDS);
			if (exchange.getResponseCode() == -1) {
				exchange.getResponseHeaders().set("Retry-After", Integer.toString(RETRY_AFTER_SECONDS));
				HttpJson.error(exchange, 503, "busy", "The Minecraft server is busy, try again in a moment");
			}
		} catch (Exception e) {
			StatsWebuiPlus.LOGGER.error("API request failed: {}", path, e);
			if (exchange.getResponseCode() == -1) {
				HttpJson.error(exchange, 500, "internal_error", "Internal error");
			}
		}
	}

	private void answer(HttpExchange exchange, String path) throws Exception {
		switch (path) {
			case "/api" -> {
				HttpJson.send(exchange, 200, Status.index());
				return;
			}
			case "/api/health" -> {
				HttpJson.send(exchange, 200, Status.health());
				return;
			}
			case Status.OPENAPI -> {
				sendOpenApi(exchange);
				return;
			}
			case "/api/status" -> {
				HttpJson.send(exchange, 200, Status.status(config.get(), source.current().players(), source.onlinePlayers(),
					source.maxPlayers()));
				return;
			}
			default -> {
			}
		}
		if (path.startsWith(PREFIX + "pages/")) {
			sendPage(exchange, path.substring((PREFIX + "pages/").length()));
			return;
		}
		StatsAnswer answer = path.startsWith(PREFIX) ? route(path.substring(PREFIX.length())) : null;
		if (answer == null) {
			HttpJson.error(exchange, 404, "not_found", "Unknown endpoint");
			return;
		}
		long cooldown = config.get().refreshCooldownMs();
		StatsCache.Snapshot snapshot = source.refresh(cooldown);
		answer.send(exchange, snapshot, cacheControl(cooldown, snapshot));
	}

	/** The answer for a path below /api/ that needs the stats; null for none. */
	private StatsAnswer route(String rest) {
		String[] parts = rest.split("/", -1);
		String resource = parts[0];
		switch (resource) {
			case "players" -> {
				if (parts.length == 1) {
					return (exchange, snapshot, cache) -> HttpJson.send(exchange, 200,
						Players.list(snapshot.players(), advancements.get().size()), cache);
				}
				String name = parts[1];
				if (parts.length == 2) {
					return (exchange, snapshot, cache) -> {
						PlayerRecord player = Players.find(snapshot.players(), name);
						if (player == null) {
							HttpJson.error(exchange, 404, "not_found", "Player not found");
							return;
						}
						HttpJson.send(exchange, 200, Players.detail(player, snapshot.players(), snapshot.vanillaRanks(),
							advancements.get()), cache);
					};
				}
				String prefix = name + "/advancements/";
				if (parts.length >= 4 && parts[2].equals("advancements") && rest.length() > "players/".length() + prefix.length()) {
					String advancement = Units.fullId(rest.substring("players/".length() + prefix.length()));
					return (exchange, snapshot, cache) -> {
						PlayerRecord player = Players.find(snapshot.players(), name);
						if (player == null) {
							HttpJson.error(exchange, 404, "not_found", "Player not found");
							return;
						}
						AdvancementView catalog = advancements.get();
						AdvancementInfo info = catalog.find().apply(advancement);
						if (info == null) {
							HttpJson.error(exchange, 404, "not_found", "Unknown advancement");
							return;
						}
						HttpJson.send(exchange, 200, Advancements.ofPlayer(player, info, snapshot.players(), catalog), cache);
					};
				}
				return null;
			}
			case "rankings" -> {
				return parts.length == 1
					? (exchange, snapshot, cache) -> HttpJson.send(exchange, 200, Boards.rankings(snapshot.players()), cache)
					: null;
			}
			case "leaderboards" -> {
				if (parts.length == 1) {
					return (exchange, snapshot, cache) -> HttpJson.send(exchange, 200, Boards.overview(), cache);
				}
				String id = rest.substring("leaderboards/".length());
				return (exchange, snapshot, cache) -> sendBoard(exchange, LeaderboardCatalog.find(id), snapshot, cache);
			}
			case "stats" -> {
				if (parts.length == 1) {
					return (exchange, snapshot, cache) -> HttpJson.send(exchange, 200, Statistics.overview(snapshot.vanillaRanks()), cache);
				}
				if (parts.length < 3 || parts[2].isEmpty()) {
					return null;
				}
				String group = parts[1];
				String id = Units.fullId(rest.substring("stats/".length() + group.length() + 1));
				return (exchange, snapshot, cache) -> {
					Map<String, Object> board = Statistics.board(group, id, snapshot.players(), snapshot.vanillaRanks());
					if (board == null) {
						HttpJson.error(exchange, 404, "not_found", "Unknown statistic");
						return;
					}
					HttpJson.send(exchange, 200, board, cache);
				};
			}
			case "advancements" -> {
				if (parts.length == 1) {
					return (exchange, snapshot, cache) -> HttpJson.send(exchange, 200,
						Advancements.list(advancements.get(), snapshot.players()), cache);
				}
				String id = rest.substring("advancements/".length());
				if (id.equals("most")) {
					return (exchange, snapshot, cache) -> sendBoard(exchange, LeaderboardCatalog.find(Advancements.MOST), snapshot, cache);
				}
				String advancement = Units.fullId(id);
				return (exchange, snapshot, cache) -> {
					AdvancementInfo info = advancements.get().find().apply(advancement);
					if (info == null) {
						HttpJson.error(exchange, 404, "not_found", "Unknown advancement");
						return;
					}
					HttpJson.send(exchange, 200, Advancements.detail(info, snapshot.players()), cache);
				};
			}
			default -> {
				return null;
			}
		}
	}

	private static void sendBoard(HttpExchange exchange, LeaderboardInfo board, StatsCache.Snapshot snapshot, String cache)
		throws IOException {
		if (board == null) {
			HttpJson.error(exchange, 404, "not_found", "Unknown leaderboard");
			return;
		}
		HttpJson.send(exchange, 200, Boards.board(board, snapshot.players()), cache);
	}

	/** A configured page in the visitor's language; read on every request, so an edit shows on the next visit. */
	private void sendPage(HttpExchange exchange, String id) throws IOException {
		CustomPages.Content content = CustomPages.find(config.get(), id, query(exchange, "lang"));
		if (content == null) {
			HttpJson.error(exchange, 404, "not_found", "Unknown page");
			return;
		}
		HttpJson.send(exchange, 200, content.toJson(), "no-cache");
	}

	private void sendOpenApi(HttpExchange exchange) throws IOException {
		byte[] yaml = openApi;
		if (yaml == null) {
			try (InputStream in = ApiHandler.class.getResourceAsStream(OPENAPI_RESOURCE)) {
				if (in == null) {
					HttpJson.error(exchange, 404, "not_found", "The API description is not packaged");
					return;
				}
				yaml = in.readAllBytes();
			}
			openApi = yaml;
		}
		HttpJson.sendBytes(exchange, 200, yaml, YAML, "no-cache");
	}

	/**
	 * Until the next refresh would happen anyway: the cooldown minus the snapshot's age, in whole seconds; never kept
	 * when every request refreshes.
	 */
	static String cacheControl(long cooldownMs, StatsCache.Snapshot snapshot) {
		if (cooldownMs <= 0) {
			return HttpJson.NO_STORE;
		}
		long ageMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - snapshot.refreshedAt());
		return "public, max-age=" + Math.max(0, (cooldownMs - ageMs) / 1000);
	}

	/** A query parameter, decoded; null if missing. */
	private static String query(HttpExchange exchange, String name) {
		String raw = exchange.getRequestURI().getRawQuery();
		if (raw == null) {
			return null;
		}
		for (String pair : raw.split("&")) {
			int equals = pair.indexOf('=');
			String key = equals < 0 ? pair : pair.substring(0, equals);
			if (key.equals(name)) {
				return equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
			}
		}
		return null;
	}
}
