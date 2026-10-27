package webui.stats.mc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WebConfig {
	public static final int DEFAULT_PORT = 25580;
	public static final String DEFAULT_BIND = "0.0.0.0";
	public static final String DEFAULT_WORLD = "";
	// Per language, so the default config shows how to translate them. An empty entry means "not set": that
	// language falls back to English, then to the first text given.
	public static final Map<String, String> DEFAULT_SITE_HEADING =
		LocalizedText.of("en", "My Server", "de", "Mein Server", "es", "Mi servidor", "fr", "Mon serveur");
	public static final Map<String, String> DEFAULT_TAGLINE =
		LocalizedText.of("en", "For all friends", "de", "Für alle Freunde", "es", "Para todos los amigos", "fr", "Pour tous les amis");
	public static final Map<String, String> DEFAULT_SLOGAN = LocalizedText.of(
		"en", "The best server in the world.",
		"de", "Der beste Server der Welt.",
		"es", "El mejor servidor del mundo.",
		"fr", "Le meilleur serveur du monde."
	);
	public static final Map<String, String> DEFAULT_WINDOW_TITLE = LocalizedText.of(
		"en", "{page} - My Server", "de", "{page} - Mein Server", "es", "{page} - Mi servidor", "fr", "{page} - Mon serveur"
	);
	// Stats on the page are at most this old; in exchange the server thread is touched at most once per window, and
	// browsers and proxies may keep responses for the rest of it.
	public static final int DEFAULT_REFRESH_COOLDOWN_MS = 15_000;
	public static final int MAX_REFRESH_COOLDOWN_MS = 60_000;
	// 0: online players are saved only by Minecraft itself, every 5 minutes and on logout.
	public static final int DEFAULT_SAVE_INTERVAL_SECONDS = 0;
	public static final int MAX_SAVE_INTERVAL_SECONDS = 3600;
	public static final String DEFAULT_LANGUAGE = "en";
	public static final List<String> AVATAR_POSES = List.of("idle", "walk", "run", "fly");
	public static final String DEFAULT_AVATAR_POSE = "walk";
	// Where the header image goes: above the site heading, between heading and slogan, or below the slogan.
	public static final List<String> HEADER_IMAGE_POSITIONS = List.of("top", "between", "bottom");
	public static final String DEFAULT_HEADER_IMAGE_POSITION = "top";
	// Relative to the server's config folder; "" disables the header image.
	public static final String DEFAULT_HEADER_IMAGE = McStatsWebui.MOD_ID + "/header.png";
	// Placeholders: they only show up in the menu once a URL is filled in.
	public static final List<MenuLink> DEFAULT_LINKS = List.of(
		new MenuLink(LocalizedText.of("en", "Map", "de", "Karte", "es", "Mapa", "fr", "Carte"), "", true),
		new MenuLink(LocalizedText.of("en", "Discord", "de", "Discord", "es", "Discord", "fr", "Discord"), "", true),
		new MenuLink(LocalizedText.of("en", "Dashboard", "de", "Dashboard", "es", "Panel", "fr", "Tableau de bord"), "", true)
	);
	// Must match the locales in web/src/lib/i18n/locales.ts
	public static final List<String> LANGUAGES = List.of("de", "en", "es", "fr");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final String bind;
	private final int port;
	private final String world;
	private final Object windowTitle;
	private final int refreshCooldownMs;
	private final int saveIntervalSeconds;
	private final String defaultLanguage;
	private final List<String> languages;
	private final String headerImage;
	private final String headerImagePosition;
	private final List<MenuLink> links;
	private final Object siteHeading;
	private final Object siteHeadingTagline;
	private final boolean siteHeadingTaglinePulse;
	private final Object siteHeadingSlogan;
	private final String avatarPose;
	private final double avatarAngle;
	private final boolean avatarRotate;
	private final boolean statusBar;
	private final String serverAddress;

	public WebConfig(
		String bind,
		int port,
		String world,
		Object windowTitle,
		int refreshCooldownMs,
		int saveIntervalSeconds,
		String defaultLanguage,
		List<String> languages,
		String headerImage,
		String headerImagePosition,
		List<MenuLink> links,
		Object siteHeading,
		Object siteHeadingTagline,
		boolean siteHeadingTaglinePulse,
		Object siteHeadingSlogan,
		String avatarPose,
		double avatarAngle,
		boolean avatarRotate,
		boolean statusBar,
		String serverAddress
	) {
		this.bind = bind;
		this.port = port;
		this.world = world;
		this.windowTitle = windowTitle;
		this.refreshCooldownMs = refreshCooldownMs;
		this.saveIntervalSeconds = saveIntervalSeconds;
		this.defaultLanguage = defaultLanguage;
		this.languages = List.copyOf(languages);
		this.headerImage = headerImage;
		this.headerImagePosition = headerImagePosition;
		this.links = List.copyOf(links);
		this.siteHeading = siteHeading;
		this.siteHeadingTagline = siteHeadingTagline;
		this.siteHeadingTaglinePulse = siteHeadingTaglinePulse;
		this.siteHeadingSlogan = siteHeadingSlogan;
		this.avatarPose = avatarPose;
		this.avatarAngle = avatarAngle;
		this.avatarRotate = avatarRotate;
		this.statusBar = statusBar;
		this.serverAddress = serverAddress;
	}

	public String bind() {
		return bind;
	}

	public int port() {
		return port;
	}

	public String world() {
		return world;
	}

	/** Browser tab title template with {page}: a String or text per language. */
	public Object windowTitle() {
		return windowTitle;
	}

	/** API requests reuse stats refreshed less than this many ms ago; 0 refreshes on every request. */
	public int refreshCooldownMs() {
		return refreshCooldownMs;
	}

	/** Also saves online players' stats and advancements this often, so a crash loses less; 0 leaves it to Minecraft. */
	public int saveIntervalSeconds() {
		return saveIntervalSeconds;
	}

	/** Web UI language for visitors who haven't picked one and whose browser languages aren't supported. */
	public String defaultLanguage() {
		return defaultLanguage;
	}

	/** Languages offered in the web UI (all supported ones unless the config narrows them down). */
	public List<String> languages() {
		return languages;
	}

	/** Header image path relative to the config folder; empty when disabled. */
	public String headerImage() {
		return headerImage;
	}

	/** Where the header image goes: top, between (heading and slogan) or bottom. */
	public String headerImagePosition() {
		return headerImagePosition;
	}

	/** All configured menu links, including placeholders without a URL (see {@link MenuLink#shown()}). */
	public List<MenuLink> links() {
		return links;
	}

	/** Heading under the header image on every page: a String or text per language; "" for none. */
	public Object siteHeading() {
		return siteHeading;
	}

	/** Small rotated text next to the site heading: a String or text per language; "" for none. */
	public Object siteHeadingTagline() {
		return siteHeadingTagline;
	}

	/** Whether the tagline pulses in size like Minecraft's splash text. */
	public boolean siteHeadingTaglinePulse() {
		return siteHeadingTaglinePulse;
	}

	/** Line under the site heading: a String or text per language; "" for none. */
	public Object siteHeadingSlogan() {
		return siteHeadingSlogan;
	}

	/** Starting pose of the player page avatar: idle, walk, run or fly. */
	public String avatarPose() {
		return avatarPose;
	}

	/** Camera angle around the player page avatar in degrees: 0 = front, 90 = side, 180 = back. */
	public double avatarAngle() {
		return avatarAngle;
	}

	/** Whether the player page avatar slowly turns on its own when the page opens (visitors can toggle it). */
	public boolean avatarRotate() {
		return avatarRotate;
	}

	/** Whether the status bar (online, address, player count) is shown under the site heading. */
	public boolean statusBar() {
		return statusBar;
	}

	/** Address players connect to, shown in the status bar; empty hides it. */
	public String serverAddress() {
		return serverAddress;
	}

	public List<Map<String, Object>> linksPayload() {
		List<Map<String, Object>> shown = new ArrayList<>();
		for (MenuLink link : links) {
			if (link.shown()) {
				shown.add(link.toJson());
			}
		}
		return shown;
	}

	public Map<String, Object> brandingPayload() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("windowTitle", windowTitle);
		body.put("headerImagePosition", headerImagePosition);
		body.put("siteHeading", siteHeading);
		body.put("siteHeadingTagline", siteHeadingTagline);
		body.put("siteHeadingTaglinePulse", siteHeadingTaglinePulse);
		body.put("siteHeadingSlogan", siteHeadingSlogan);
		body.put("avatarPose", avatarPose);
		body.put("avatarAngle", avatarAngle);
		body.put("avatarRotate", avatarRotate);
		body.put("statusBar", statusBar);
		body.put("serverAddress", serverAddress);
		return body;
	}

	public static WebConfig defaults() {
		return new WebConfig(
			DEFAULT_BIND,
			DEFAULT_PORT,
			DEFAULT_WORLD,
			DEFAULT_WINDOW_TITLE,
			DEFAULT_REFRESH_COOLDOWN_MS,
			DEFAULT_SAVE_INTERVAL_SECONDS,
			DEFAULT_LANGUAGE,
			LANGUAGES,
			DEFAULT_HEADER_IMAGE,
			DEFAULT_HEADER_IMAGE_POSITION,
			DEFAULT_LINKS,
			DEFAULT_SITE_HEADING,
			DEFAULT_TAGLINE,
			true,
			DEFAULT_SLOGAN,
			DEFAULT_AVATAR_POSE,
			0,
			true,
			true,
			""
		);
	}

	public static Path path() {
		return McStatsWebui.configFile(".json");
	}

	public static WebConfig load() {
		Path path = path();
		WebConfig loaded = defaults();
		try {
			Files.createDirectories(path.getParent());
			if (!Files.isRegularFile(path)) {
				write(path, loaded);
				McStatsWebui.LOGGER.info("Wrote default config {}", path);
				writeDefaultHeaderImage();
				return loaded;
			}
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = parse(JsonParser.parseReader(reader).getAsJsonObject());
			}
			write(path, loaded);
		} catch (Exception e) {
			McStatsWebui.LOGGER.warn("Could not read {}, using defaults", path, e);
			return defaults();
		}
		return loaded;
	}

	/**
	 * Puts the banner shipped with the mod where the default config looks for it. Only next to a new config, so a
	 * server owner who deletes the image doesn't get it back; {@code "headerImage": ""} hides it without deleting.
	 */
	private static void writeDefaultHeaderImage() {
		Path target = McStatsWebui.platform().configDir().resolve(DEFAULT_HEADER_IMAGE);
		if (Files.exists(target)) {
			return;
		}
		try (InputStream image = WebConfig.class.getResourceAsStream("/assets/" + McStatsWebui.MOD_ID + "/header.png")) {
			if (image == null) {
				McStatsWebui.LOGGER.warn("The default header image is missing from the mod JAR");
				return;
			}
			Files.createDirectories(target.getParent());
			Files.copy(image, target);
			McStatsWebui.LOGGER.info("Wrote default header image {}", target);
		} catch (IOException e) {
			McStatsWebui.LOGGER.warn("Could not write the default header image {}", target, e);
		}
	}

	/**
	 * Reads the config again while the server runs; null when the file can't be read, so the caller keeps the current
	 * config. Unlike {@link #load()} it never writes the file: it may be half edited, and writing it would report a
	 * change to the file watcher again.
	 */
	public static WebConfig reload() {
		Path path = path();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return parse(JsonParser.parseReader(reader).getAsJsonObject());
		} catch (Exception e) {
			McStatsWebui.LOGGER.warn("Could not read {}, keeping the current config: {}", path, e.toString());
			return null;
		}
	}

	private static WebConfig parse(JsonObject json) {
		int port = json.has("port") ? json.get("port").getAsInt() : DEFAULT_PORT;
		if (port < 1 || port > 65535) {
			McStatsWebui.LOGGER.warn("Invalid port {}, using {}", port, DEFAULT_PORT);
			port = DEFAULT_PORT;
		}
		String bind = text(json, "bind", DEFAULT_BIND);
		if (bind.isEmpty()) {
			bind = DEFAULT_BIND;
		}
		String world = text(json, "world", DEFAULT_WORLD);
		if (world.contains("..") || world.startsWith("/") || world.startsWith("\\")) {
			McStatsWebui.LOGGER.warn("Ignoring unsafe world folder {}", world);
			world = DEFAULT_WORLD;
		}
		Object windowTitle = windowTitle(json);
		int refreshCooldownMs = json.has("refreshCooldownMs")
			? json.get("refreshCooldownMs").getAsInt()
			: DEFAULT_REFRESH_COOLDOWN_MS;
		if (refreshCooldownMs < 0 || refreshCooldownMs > MAX_REFRESH_COOLDOWN_MS) {
			McStatsWebui.LOGGER.warn(
				"refreshCooldownMs must be between 0 and {}, using {}",
				MAX_REFRESH_COOLDOWN_MS,
				DEFAULT_REFRESH_COOLDOWN_MS
			);
			refreshCooldownMs = DEFAULT_REFRESH_COOLDOWN_MS;
		}
		int saveIntervalSeconds = json.has("saveIntervalSeconds")
			? json.get("saveIntervalSeconds").getAsInt()
			: DEFAULT_SAVE_INTERVAL_SECONDS;
		if (saveIntervalSeconds < 0 || saveIntervalSeconds > MAX_SAVE_INTERVAL_SECONDS) {
			McStatsWebui.LOGGER.warn(
				"saveIntervalSeconds must be between 0 and {}, using {}",
				MAX_SAVE_INTERVAL_SECONDS,
				DEFAULT_SAVE_INTERVAL_SECONDS
			);
			saveIntervalSeconds = DEFAULT_SAVE_INTERVAL_SECONDS;
		}
		String defaultLanguage = text(json, "defaultLanguage", DEFAULT_LANGUAGE).toLowerCase(Locale.ROOT);
		if (!LANGUAGES.contains(defaultLanguage)) {
			McStatsWebui.LOGGER.warn("defaultLanguage must be one of {}, using {}", LANGUAGES, DEFAULT_LANGUAGE);
			defaultLanguage = DEFAULT_LANGUAGE;
		}
		List<String> languages = new ArrayList<>();
		if (json.has("languages") && json.get("languages").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("languages")) {
				String code = element.isJsonPrimitive() ? element.getAsString().trim().toLowerCase(Locale.ROOT) : "";
				if (!LANGUAGES.contains(code)) {
					McStatsWebui.LOGGER.warn("Ignoring unknown language {} in languages; supported: {}", element, LANGUAGES);
				} else if (!languages.contains(code)) {
					languages.add(code);
				}
			}
			if (languages.isEmpty()) {
				McStatsWebui.LOGGER.warn("languages enables no supported language, enabling all of {}", LANGUAGES);
			}
		}
		if (languages.isEmpty()) {
			languages = LANGUAGES;
		}
		if (!languages.contains(defaultLanguage)) {
			String fallback = languages.contains(DEFAULT_LANGUAGE) ? DEFAULT_LANGUAGE : languages.getFirst();
			McStatsWebui.LOGGER.warn("defaultLanguage {} is not in languages {}, using {}", defaultLanguage, languages, fallback);
			defaultLanguage = fallback;
		}
		String headerImage = text(json, "headerImage", DEFAULT_HEADER_IMAGE).replace('\\', '/');
		String lowerImage = headerImage.toLowerCase(Locale.ROOT);
		if (headerImage.contains("..") || headerImage.startsWith("/") || headerImage.matches("^[A-Za-z]:.*")) {
			McStatsWebui.LOGGER.warn("headerImage must be a path inside the config folder, disabling it: {}", headerImage);
			headerImage = "";
		} else if (!headerImage.isEmpty() && !lowerImage.matches(".*\\.(png|jpe?g|webp|gif)$")) {
			McStatsWebui.LOGGER.warn("headerImage must be a .png, .jpg, .webp or .gif file, disabling it: {}", headerImage);
			headerImage = "";
		}
		List<MenuLink> links = DEFAULT_LINKS;
		if (json.has("links")) {
			links = new ArrayList<>();
			if (json.get("links").isJsonArray()) {
				for (JsonElement element : json.getAsJsonArray("links")) {
					MenuLink link = MenuLink.parse(element);
					if (link != null) {
						links.add(link);
					}
				}
			} else {
				McStatsWebui.LOGGER.warn("links must be a list of {\"label\": ..., \"url\": ...} entries, ignoring it");
			}
		}
		String headerImagePosition = text(json, "headerImagePosition", DEFAULT_HEADER_IMAGE_POSITION).toLowerCase(Locale.ROOT);
		if (!HEADER_IMAGE_POSITIONS.contains(headerImagePosition)) {
			McStatsWebui.LOGGER.warn(
				"headerImagePosition must be one of {}, using {}", HEADER_IMAGE_POSITIONS, DEFAULT_HEADER_IMAGE_POSITION
			);
			headerImagePosition = DEFAULT_HEADER_IMAGE_POSITION;
		}
		// A missing key gets the default; an empty value hides that text.
		Object siteHeading = localized(json, "siteHeading", DEFAULT_SITE_HEADING);
		Object siteHeadingTagline = localized(json, "siteHeadingTagline", DEFAULT_TAGLINE);
		boolean siteHeadingTaglinePulse = !json.has("siteHeadingTaglinePulse")
			|| !json.get("siteHeadingTaglinePulse").isJsonPrimitive()
			|| json.get("siteHeadingTaglinePulse").getAsBoolean();
		Object siteHeadingSlogan = localized(json, "siteHeadingSlogan", DEFAULT_SLOGAN);
		String avatarPose = text(json, "avatarPose", DEFAULT_AVATAR_POSE).toLowerCase(Locale.ROOT);
		if (!AVATAR_POSES.contains(avatarPose)) {
			McStatsWebui.LOGGER.warn("avatarPose must be one of {}, using {}", AVATAR_POSES, DEFAULT_AVATAR_POSE);
			avatarPose = DEFAULT_AVATAR_POSE;
		}
		double avatarAngle = json.has("avatarAngle") && json.get("avatarAngle").isJsonPrimitive()
			? json.get("avatarAngle").getAsDouble()
			: 0;
		if (!(avatarAngle >= -180 && avatarAngle <= 180)) {
			McStatsWebui.LOGGER.warn("avatarAngle must be between -180 and 180 degrees, using 0");
			avatarAngle = 0;
		}
		boolean avatarRotate = !json.has("avatarRotate") || !json.get("avatarRotate").isJsonPrimitive() || json.get("avatarRotate").getAsBoolean();
		boolean statusBar = !json.has("statusBar") || !json.get("statusBar").isJsonPrimitive() || json.get("statusBar").getAsBoolean();
		String serverAddress = text(json, "serverAddress", "");
		return new WebConfig(
			bind,
			port,
			world,
			windowTitle,
			refreshCooldownMs,
			saveIntervalSeconds,
			defaultLanguage,
			languages,
			headerImage,
			headerImagePosition,
			links,
			siteHeading,
			siteHeadingTagline,
			siteHeadingTaglinePulse,
			siteHeadingSlogan,
			avatarPose,
			avatarAngle,
			avatarRotate,
			statusBar,
			serverAddress
		);
	}

	private static String text(JsonObject json, String key, String fallback) {
		if (!json.has(key) || json.get(key).isJsonNull()) {
			return fallback;
		}
		return json.get(key).getAsString().trim();
	}

	/**
	 * windowTitle as a String or text per language. Every text must contain {page} (or <page>): a String without it
	 * falls back to the default, a language without it is emptied (it then uses the English title).
	 */
	private static Object windowTitle(JsonObject json) {
		Object title = json.has("windowTitle") ? LocalizedText.parse(json.get("windowTitle")) : DEFAULT_WINDOW_TITLE;
		if (title instanceof Map<?, ?> texts) {
			Map<String, String> checked = new LinkedHashMap<>();
			texts.forEach((language, text) -> {
				String value = text.toString();
				if (!value.isEmpty() && !hasPagePlaceholder(value)) {
					McStatsWebui.LOGGER.warn("windowTitle for {} must include {page} or <page>, ignoring it", language);
					value = "";
				}
				checked.put(language.toString(), value);
			});
			title = checked;
		} else if (!hasPagePlaceholder(title.toString())) {
			McStatsWebui.LOGGER.warn("windowTitle must include {page} or <page>, using default");
			return DEFAULT_WINDOW_TITLE;
		}
		if (LocalizedText.isEmpty(title)) {
			McStatsWebui.LOGGER.warn("windowTitle is empty, using default");
			return DEFAULT_WINDOW_TITLE;
		}
		return title;
	}

	private static boolean hasPagePlaceholder(String text) {
		return text.contains("{page}") || text.contains("<page>");
	}

	/** The key's value parsed as {@link LocalizedText}, or the fallback if it is missing. */
	private static Object localized(JsonObject json, String key, Object fallback) {
		return json.has(key) && !json.get(key).isJsonNull() ? LocalizedText.parse(json.get(key)) : fallback;
	}

	private static void write(Path path, WebConfig config) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("port", config.port());
		body.put("bind", config.bind());
		body.put("world", config.world());
		body.put("windowTitle", config.windowTitle());
		body.put("refreshCooldownMs", config.refreshCooldownMs());
		body.put("saveIntervalSeconds", config.saveIntervalSeconds());
		body.put("defaultLanguage", config.defaultLanguage());
		body.put("languages", config.languages());
		body.put("headerImage", config.headerImage());
		body.put("headerImagePosition", config.headerImagePosition());
		body.put("siteHeading", config.siteHeading());
		body.put("siteHeadingTagline", config.siteHeadingTagline());
		body.put("siteHeadingTaglinePulse", config.siteHeadingTaglinePulse());
		body.put("siteHeadingSlogan", config.siteHeadingSlogan());
		body.put("avatarPose", config.avatarPose());
		body.put("avatarAngle", config.avatarAngle());
		body.put("avatarRotate", config.avatarRotate());
		body.put("statusBar", config.statusBar());
		body.put("serverAddress", config.serverAddress());
		body.put("links", config.links().stream().map(MenuLink::toJson).toList());
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(body, writer);
			writer.write('\n');
		}
	}
}
