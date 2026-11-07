package webui.stats.mc.web;

import com.sun.net.httpserver.HttpExchange;
import webui.stats.mc.McStatsWebui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The web UI's favicon at one fixed address: the Minecraft server's own icon (server-icon.png in the server folder,
 * the one the game's server list shows) if it has one, else the mod's. The page links it statically, so browsers
 * never ask for a missing file and need no script to swap icons (which Safari doesn't follow).
 */
public final class FaviconHandler {
	public static final String PATH = "/favicon";
	private static final String OWN_ICON = "/assets/" + McStatsWebui.MOD_ID + "/favicon.svg";

	private FaviconHandler() {
	}

	public static void handle(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			HttpJson.empty(exchange, 405);
			return;
		}
		Path serverIcon = McStatsWebui.platform().gameDir().resolve("server-icon.png");
		byte[] bytes;
		String type;
		if (Files.isRegularFile(serverIcon)) {
			bytes = Files.readAllBytes(serverIcon);
			type = "image/png";
		} else {
			try (InputStream own = FaviconHandler.class.getResourceAsStream(OWN_ICON)) {
				if (own == null) {
					HttpJson.empty(exchange, 404);
					return;
				}
				bytes = own.readAllBytes();
			}
			type = "image/svg+xml";
		}
		// Checked on every visit, so a new or removed server icon shows up; unchanged, it is an empty 304.
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
}
