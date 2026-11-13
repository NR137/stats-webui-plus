package de.nr137.statswebuiplus.web;

import com.sun.net.httpserver.HttpExchange;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.WebConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Serves the optional header image configured by {@code headerImage} (a path inside the config folder). */
public final class HeaderImageHandler {
	public static final String PATH = "/header-image";

	private HeaderImageHandler() {
	}

	/** The image file, or null if the header is disabled, the file is missing or the format is unsupported. */
	public static Path file(WebConfig config) {
		if (config.headerImage().isEmpty() || contentType(config.headerImage()) == null) {
			return null;
		}
		Path dir = StatsWebuiPlus.platform().configDir().toAbsolutePath().normalize();
		Path file = dir.resolve(config.headerImage()).normalize();
		if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
			return null;
		}
		return file;
	}

	/** URL for the web UI, or null when there is no header. Changes whenever the file is replaced. */
	public static String url(WebConfig config) {
		Path file = file(config);
		if (file == null) {
			return null;
		}
		try {
			return PATH + "?v=" + Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return null;
		}
	}

	public static void handle(HttpExchange exchange, WebConfig config) throws IOException {
		if (!HttpJson.isGetOrHead(exchange)) {
			HttpJson.empty(exchange, 405);
			return;
		}
		Path file = file(config);
		if (file == null) {
			HttpJson.empty(exchange, 404);
			return;
		}

		byte[] bytes = Files.readAllBytes(file);
		exchange.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
		// The URL from url() carries the file's modification time, so it can be cached.
		exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
		HttpJson.respond(exchange, 200, bytes);
	}

	private static String contentType(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".png")) {
			return "image/png";
		}
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		if (lower.endsWith(".webp")) {
			return "image/webp";
		}
		if (lower.endsWith(".gif")) {
			return "image/gif";
		}
		return null;
	}
}
