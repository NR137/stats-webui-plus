package de.nr137.statswebuiplus.api;

import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.assets.GameAssets;
import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.web.CustomPages;
import de.nr137.statswebuiplus.web.HeaderImageHandler;
import de.nr137.statswebuiplus.web.HeadingFontHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The answers that need no stats: {@code /api}, {@code /api/health} and {@code /api/status}. */
public final class Status {
	/** Raised when an answer changes in a way an existing client could trip over. */
	public static final int API_VERSION = 2;
	public static final String OPENAPI = "/api/openapi.yaml";

	private static final Map<String, String> ENDPOINTS = endpoints();

	private Status() {
	}

	private static Map<String, String> endpoints() {
		Map<String, String> endpoints = new LinkedHashMap<>();
		endpoints.put("index", "/api");
		endpoints.put("health", "/api/health");
		endpoints.put("openapi", OPENAPI);
		endpoints.put("status", "/api/status");
		endpoints.put("page", "/api/pages/{id}");
		endpoints.put("players", "/api/players");
		endpoints.put("player", "/api/players/{player}");
		endpoints.put("playerAdvancement", "/api/players/{player}/advancements/{advancement}");
		endpoints.put("rankings", "/api/rankings");
		endpoints.put("leaderboards", "/api/leaderboards");
		endpoints.put("leaderboard", "/api/leaderboards/{id}");
		endpoints.put("stats", "/api/stats");
		endpoints.put("statistic", "/api/stats/{group}/{statistic}");
		endpoints.put("advancements", "/api/advancements");
		endpoints.put("mostAdvancements", "/api/advancements/most");
		endpoints.put("advancement", "/api/advancements/{advancement}");
		return Collections.unmodifiableMap(endpoints);
	}

	/** Every endpoint by name, for anyone exploring the API. */
	public static Map<String, Object> index() {
		Map<String, Object> body = identity();
		body.put("apiVersion", API_VERSION);
		body.put("documentation", OPENAPI);
		body.put("endpoints", ENDPOINTS);
		return body;
	}

	public static Map<String, Object> health() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ok", true);
		body.putAll(identity());
		return body;
	}

	/**
	 * What every page needs besides the stats: the config's branding, links and pages, and the server's state. Not
	 * the bind address: the web UI doesn't use it, and it tells visitors about the server's network.
	 */
	public static Map<String, Object> status(WebConfig config, List<PlayerRecord> players, int onlinePlayers, int maxPlayers) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("trackedPlayers", players.size());
		body.put("nameClashes", Players.nameClashes(players));
		body.put("onlinePlayers", onlinePlayers);
		body.put("maxPlayers", maxPlayers);
		body.put("branding", config.brandingPayload());
		body.put("headerImage", HeaderImageHandler.url(config));
		body.put("customFont", HeadingFontHandler.payload(config));
		body.put("languages", config.languages());
		body.put("links", config.linksPayload());
		List<Map<String, Object>> pages = CustomPages.payload(config);
		body.put("pages", pages);
		// The start page only once it has a text; until then the ranking stays the start page.
		body.put("startPage", pages.stream().anyMatch(page -> config.startPage().equals(page.get("id"))) ? config.startPage() : null);
		body.put("defaultLanguage", config.defaultLanguage());
		body.put("gameAssets", GameAssets.get().state().name().toLowerCase(Locale.ROOT));
		return body;
	}

	private static Map<String, Object> identity() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("mod", StatsWebuiPlus.MOD_ID);
		body.put("version", StatsWebuiPlus.platform().modVersion());
		body.put("minecraft", StatsWebuiPlus.platform().minecraftVersion());
		return body;
	}
}
