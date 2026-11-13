package de.nr137.statswebuiplus.web;

import com.sun.net.httpserver.HttpExchange;
import de.nr137.statswebuiplus.CustomFont;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.WebConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the server owner's font for the site heading (config siteHeadingCustomFont, files inside the config folder):
 * the file at index n of its list as {@code /heading-font/n}, and its license text file as {@code /heading-font/license}.
 */
public final class HeadingFontHandler {
	public static final String PATH = "/heading-font/";
	private static final String LICENSE = "license";
	// A license text, not a book: anything bigger is not served.
	private static final long MAX_LICENSE_BYTES = 256 * 1024;
	private static final Map<String, String> CONTENT_TYPES = Map.of("woff2", "font/woff2", "woff", "font/woff",
		"truetype", "font/ttf", "opentype", "font/otf");

	private HeadingFontHandler() {
	}

	/** The font file at this index of the list, or null when the custom font isn't used or the file is missing. */
	public static Path file(WebConfig config, int index) {
		List<String> files = config.siteHeadingCustomFont().files();
		if (!config.siteHeadingFont().equals("custom") || index < 0 || index >= files.size()) {
			return null;
		}
		Path dir = StatsWebuiPlus.platform().configDir().toAbsolutePath().normalize();
		Path file = dir.resolve(files.get(index)).normalize();
		return file.startsWith(dir) && Files.isRegularFile(file) ? file : null;
	}

	/** The license text file, or null when the custom font isn't used, none is set or it is missing or too big. */
	public static Path licenseFile(WebConfig config) {
		String relative = config.siteHeadingCustomFont().licenseFile();
		if (!config.siteHeadingFont().equals("custom") || relative.isEmpty()) {
			return null;
		}
		Path dir = StatsWebuiPlus.platform().configDir().toAbsolutePath().normalize();
		Path file = dir.resolve(relative).normalize();
		try {
			return file.startsWith(dir) && Files.isRegularFile(file) && Files.size(file) <= MAX_LICENSE_BYTES ? file : null;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * What the website needs for the custom font: its name, the Licenses page's text and the files that exist, each
	 * with its address (changing whenever the file is replaced) and format; null when the custom font isn't used.
	 */
	public static Map<String, Object> payload(WebConfig config) {
		if (!config.siteHeadingFont().equals("custom")) {
			return null;
		}
		CustomFont font = config.siteHeadingCustomFont();
		List<Map<String, String>> sources = new ArrayList<>();
		for (int i = 0; i < font.files().size(); i++) {
			Path file = file(config, i);
			if (file == null) {
				continue;
			}
			try {
				Map<String, String> source = new LinkedHashMap<>();
				source.put("url", PATH + i + "?v=" + Files.getLastModifiedTime(file).toMillis());
				source.put("format", CustomFont.format(file.getFileName().toString()));
				sources.add(source);
			} catch (IOException e) {
				// Gone meanwhile: left out like a missing file.
			}
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", font.name());
		body.put("license", font.license());
		// The license text's address (changing with the file), or null.
		Path license = licenseFile(config);
		String licenseText = null;
		if (license != null) {
			try {
				licenseText = PATH + LICENSE + "?v=" + Files.getLastModifiedTime(license).toMillis();
			} catch (IOException e) {
				// Gone meanwhile: no license text to show.
			}
		}
		body.put("licenseText", licenseText);
		body.put("sources", sources);
		return body;
	}

	public static void handle(HttpExchange exchange, WebConfig config) throws IOException {
		if (!HttpJson.isGetOrHead(exchange)) {
			HttpJson.empty(exchange, 405);
			return;
		}
		String rest = exchange.getRequestURI().getPath().substring(PATH.length());
		if (rest.equals(LICENSE)) {
			Path license = licenseFile(config);
			if (license == null) {
				HttpJson.empty(exchange, 404);
				return;
			}
			exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
			exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
			HttpJson.respond(exchange, 200, Files.readAllBytes(license));
			return;
		}
		Path file = rest.matches("\\d{1,3}") ? file(config, Integer.parseInt(rest)) : null;
		if (file == null) {
			HttpJson.empty(exchange, 404);
			return;
		}
		byte[] bytes = Files.readAllBytes(file);
		exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPES.get(CustomFont.format(file.getFileName().toString())));
		// The address from payload() carries the file's modification time, so it can be cached.
		exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
		HttpJson.respond(exchange, 200, bytes);
	}
}
