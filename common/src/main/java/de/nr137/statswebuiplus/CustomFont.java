package de.nr137.statswebuiplus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The server owner's own font for the site heading (config {@code siteHeadingCustomFont}, used with
 * {@code "siteHeadingFont": "custom"}): its name, its files in the config folder (several formats of the same font,
 * best first, e.g. a .woff2 and a .ttf), what the Licenses page says about it (text per language) and, optionally, a
 * text file with its license, which the Licenses page shows behind "Show license text".
 */
public record CustomFont(String name, List<String> files, Object license, String licenseFile) {
	/** The formats browsers read, by file ending, as CSS names them in {@code format()}. */
	public static final Map<String, String> FORMATS = Map.of(".woff2", "woff2", ".woff", "woff", ".ttf", "truetype",
		".otf", "opentype");

	public CustomFont {
		files = List.copyOf(files);
	}

	/** Nothing set: what a new config shows, so the owner sees the keys to fill in. */
	public static CustomFont empty() {
		return new CustomFont("", List.of(), "", "");
	}

	/** The format of a font file by its ending, or null when browsers can't read it. */
	public static String format(String file) {
		String lower = file.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> entry : FORMATS.entrySet()) {
			if (lower.endsWith(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/** From the config; files outside the config folder or of other types are left out with a warning. */
	public static CustomFont parse(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			if (element != null && !element.isJsonNull()) {
				StatsWebuiPlus.LOGGER.warn(
					"siteHeadingCustomFont must be {\"name\": ..., \"files\": [...], \"license\": ...}, ignoring it");
			}
			return empty();
		}
		JsonObject json = element.getAsJsonObject();
		String name = json.has("name") && json.get("name").isJsonPrimitive() ? json.get("name").getAsString().trim() : "";
		List<String> files = new ArrayList<>();
		if (json.has("files") && json.get("files").isJsonArray()) {
			for (JsonElement file : json.getAsJsonArray("files")) {
				String path = file.isJsonPrimitive() ? file.getAsString().trim().replace('\\', '/') : "";
				if (path.isEmpty()) {
					continue;
				}
				if (outside(path)) {
					StatsWebuiPlus.LOGGER.warn("siteHeadingCustomFont files must be inside the config folder, ignoring {}", path);
				} else if (format(path) == null) {
					StatsWebuiPlus.LOGGER.warn("siteHeadingCustomFont files must be .woff2, .woff, .ttf or .otf, ignoring {}", path);
				} else {
					files.add(path);
				}
			}
		}
		Object license = json.has("license") && !json.get("license").isJsonNull()
			? LocalizedText.parse(json.get("license"))
			: "";
		String licenseFile = json.has("licenseFile") && json.get("licenseFile").isJsonPrimitive()
			? json.get("licenseFile").getAsString().trim().replace('\\', '/')
			: "";
		if (!licenseFile.isEmpty() && outside(licenseFile)) {
			StatsWebuiPlus.LOGGER.warn("siteHeadingCustomFont licenseFile must be inside the config folder, ignoring {}",
				licenseFile);
			licenseFile = "";
		}
		return new CustomFont(name, files, license, licenseFile);
	}

	/** A path that would leave the config folder. */
	private static boolean outside(String path) {
		return path.contains("..") || path.startsWith("/") || path.matches("^[A-Za-z]:.*");
	}

	public Map<String, Object> toJson() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", name);
		body.put("files", files);
		body.put("license", license);
		body.put("licenseFile", licenseFile);
		return body;
	}
}
