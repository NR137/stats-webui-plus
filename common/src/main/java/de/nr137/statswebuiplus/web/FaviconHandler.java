package de.nr137.statswebuiplus.web;

import com.sun.net.httpserver.HttpExchange;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.WebConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * The web UI's favicon at one fixed address: the file the config names ({@code favicon}, inside the config folder),
 * else the Minecraft server's own icon (server-icon.png in the server folder, the one the game's server list shows)
 * if it has one, else the mod's. The page links it statically, so browsers never ask for a missing file and need no
 * script to swap icons (which Safari doesn't follow).
 */
public final class FaviconHandler {
	public static final String PATH = "/favicon";
	// The mod icon (the podium, scripts/art/icon.py).
	private static final String OWN_ICON = "/assets/" + StatsWebuiPlus.MOD_ID + "/icon.png";
	private static final Map<String, String> TYPES = Map.of(".png", "image/png", ".ico", "image/x-icon", ".svg",
		"image/svg+xml", ".webp", "image/webp", ".gif", "image/gif");

	private FaviconHandler() {
	}

	/** The configured favicon file, or null if none is set or it is missing. */
	public static Path configured(WebConfig config) {
		if (config.favicon().isEmpty() || type(config.favicon()) == null) {
			return null;
		}
		Path dir = StatsWebuiPlus.platform().configDir().toAbsolutePath().normalize();
		Path file = dir.resolve(config.favicon()).normalize();
		return file.startsWith(dir) && Files.isRegularFile(file) ? file : null;
	}

	public static void handle(HttpExchange exchange, WebConfig config) throws IOException {
		if (!HttpJson.isGetOrHead(exchange)) {
			HttpJson.empty(exchange, 405);
			return;
		}
		Path own = configured(config);
		Path serverIcon = StatsWebuiPlus.platform().gameDir().resolve("server-icon.png");
		byte[] bytes;
		String type = "image/png";
		if (own != null) {
			bytes = Files.readAllBytes(own);
			type = type(own.getFileName().toString());
		} else if (Files.isRegularFile(serverIcon)) {
			bytes = Files.readAllBytes(serverIcon);
		} else {
			try (InputStream mod = FaviconHandler.class.getResourceAsStream(OWN_ICON)) {
				if (mod == null) {
					HttpJson.empty(exchange, 404);
					return;
				}
				bytes = mod.readAllBytes();
			}
		}
		// Checked on every visit, so a new or removed icon shows up; unchanged, it is an empty 304.
		String etag = HttpJson.etag(bytes);
		exchange.getResponseHeaders().set("ETag", etag);
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		if (HttpJson.matches(exchange.getRequestHeaders().getFirst("If-None-Match"), etag)) {
			HttpJson.empty(exchange, 304);
			return;
		}
		exchange.getResponseHeaders().set("Content-Type", type);
		HttpJson.respond(exchange, 200, bytes);
	}

	private static String type(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> entry : TYPES.entrySet()) {
			if (lower.endsWith(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}
}
