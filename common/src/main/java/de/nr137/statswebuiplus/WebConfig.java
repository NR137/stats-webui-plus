package de.nr137.statswebuiplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The mod's settings from {@code config/stats_webui_plus.json}; see the README's Configuration section. Built by
 * {@link #load()}, {@link #reload()} and {@link #defaults()}, which check every value.
 *
 * @param world folder to read stats from, relative to the server folder; "" for the running world
 * @param windowTitle the browser tab title template with {page} (or &lt;page&gt;): a String or text per language
 * @param refreshCooldownMs API requests reuse stats refreshed less than this many ms ago; 0 refreshes on every
 *     request. Stats on the page are at most this old; in exchange the server thread is touched at most once per
 *     window, and browsers and proxies may keep responses for the rest of it.
 * @param saveIntervalSeconds also saves online players' stats and advancements this often, so a crash loses less; 0
 *     leaves it to Minecraft (it saves online players every 5 minutes and on logout)
 * @param webThreads threads answering web requests as configured; 0 = automatic (see {@link #webThreadCount()})
 * @param defaultLanguage web UI language for visitors who haven't picked one and whose browser languages aren't
 *     supported
 * @param languages languages offered in the web UI: all supported ones unless the config narrows them down
 * @param headerImage path relative to the config folder; empty when disabled
 * @param headerImagePosition top, between (heading and slogan) or bottom
 * @param favicon the browser tab's icon, a path relative to the config folder; "" for the Minecraft server's own icon
 *     (server-icon.png), or the mod's without one
 * @param siteHeading heading under the header image on every page: a String or text per language; "" for none
 * @param siteHeadingSlogan line under the heading: a String or text per language; "" for none
 * @param siteHeadingFont the site heading's font, one of {@link #SITE_HEADING_FONTS}
 * @param siteHeadingCustomFont the server owner's own font for the site heading (used with siteHeadingFont "custom")
 * @param siteHeadingSloganStyle how the slogan is shown, one of {@link #SLOGAN_STYLES}
 * @param pageTitleFont the page titles' font, one of {@link #PAGE_TITLE_FONTS}
 * @param avatarPose starting pose of the player page avatar
 * @param avatarAngle camera angle around the avatar in degrees: 0 = front, 90 = side, 180 = back
 * @param avatarRotate whether the avatar slowly turns on its own when the page opens (visitors can toggle it)
 * @param statusBar whether the status bar (online, address, player count) is shown under the site heading
 * @param serverAddress the address players connect to, shown in the status bar; empty hides it
 * @param siteUrl the website's own address as visitors reach it (e.g. https://stats.example.com, without a trailing
 *     slash), for link previews; "" takes it from each request's Host header
 * @param geyserApi whether Bedrock players' skins and unknown names are asked from GeyserMC's Global API
 *     (api.geysermc.org). Only players with a Floodgate UUID are ever looked up there, so servers without Geyser never
 *     contact it.
 * @param downloadGameAssets whether the server downloads the Minecraft client of its version from Mojang (once per
 *     version, into config/stats_webui_plus/cache) to show the game's textures in the web UI. The mod ships none of
 *     them. Off, the web UI shows names and simple fallbacks instead.
 * @param links all configured menu links, including placeholders without a URL (see {@link MenuLink#shown()})
 * @param pages all configured pages, including ones without a text file yet (see
 *     {@link de.nr137.statswebuiplus.web.CustomPages})
 * @param startPage the id of the custom page that is the website's start page ("/", with the ranking at /ranking),
 *     or "" for the ranking. Only an enabled entry of pages; whether it has a text is checked when the website asks.
 */
public record WebConfig(int port, String bind, String world, Object windowTitle, int refreshCooldownMs,
	int saveIntervalSeconds, int webThreads, String defaultLanguage, List<String> languages, String headerImage,
	String headerImagePosition, String favicon, Object siteHeading, Object siteHeadingSlogan, String siteHeadingFont,
	CustomFont siteHeadingCustomFont, String siteHeadingSloganStyle, String pageTitleFont, String avatarPose,
	double avatarAngle, boolean avatarRotate, boolean statusBar, String serverAddress, String siteUrl,
	boolean geyserApi, boolean downloadGameAssets, List<MenuLink> links, List<CustomPage> pages, String startPage) {
	/** Must match the locales in web/src/lib/i18n/locales.ts. */
	public static final List<String> LANGUAGES = List.of("de", "en", "es", "fr");
	public static final List<String> AVATAR_POSES = List.of("idle", "walk", "run", "fly");
	/** Where the header image goes: above the site heading, between heading and slogan, or below the slogan. */
	public static final List<String> HEADER_IMAGE_POSITIONS = List.of("top", "between", "bottom");
	/**
	 * The site heading's font: blocky pixel capitals (Press Start 2P), pixel blackletter as written (Jacquard 12), or the
	 * server owner's own (siteHeadingCustomFont).
	 */
	public static final List<String> SITE_HEADING_FONTS = List.of("blocky", "gothic", "custom");
	/**
	 * The page titles' font: small pixel capitals (Tiny5), blocky pixel capitals (Press Start 2P) or the Minecraft font
	 * of the rest of the page.
	 */
	public static final List<String> PAGE_TITLE_FONTS = List.of("tiny", "blocky", "minecraft");
	/**
	 * How the slogan is shown: in spaced capitals between two lines, on a plate, small above the heading, on a ribbon,
	 * or in quotes.
	 */
	public static final List<String> SLOGAN_STYLES = List.of("line", "plate", "above", "ribbon", "quote");

	private static final int DEFAULT_PORT = 25580;
	private static final String DEFAULT_BIND = "0.0.0.0";
	private static final int DEFAULT_REFRESH_COOLDOWN_MS = 15000;
	private static final int MAX_REFRESH_COOLDOWN_MS = 60000;
	private static final int MAX_SAVE_INTERVAL_SECONDS = 3600;
	private static final int MAX_WEB_THREADS = 16;
	private static final String DEFAULT_LANGUAGE = "en";
	private static final String DEFAULT_HEADER_IMAGE = StatsWebuiPlus.MOD_ID + "/header.png";
	private static final String DEFAULT_HEADER_IMAGE_POSITION = "top";
	private static final String DEFAULT_AVATAR_POSE = "walk";
	private static final String DEFAULT_SITE_HEADING_FONT = "blocky";
	private static final String DEFAULT_SLOGAN_STYLE = "line";
	private static final String DEFAULT_PAGE_TITLE_FONT = "tiny";
	private static final List<String> HEADER_IMAGE_TYPES = List.of(".png", ".jpg", ".jpeg", ".webp", ".gif");
	/** The formats browsers show as a tab icon. */
	public static final List<String> FAVICON_TYPES = List.of(".png", ".ico", ".svg", ".webp", ".gif");
	private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:.*");
	private static final Pattern SITE_URL = Pattern.compile("https?://[A-Za-z0-9.-]+(:\\d{1,5})?");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public WebConfig {
		windowTitle = frozen(windowTitle);
		languages = List.copyOf(languages);
		siteHeading = frozen(siteHeading);
		siteHeadingSlogan = frozen(siteHeadingSlogan);
		links = List.copyOf(links);
		pages = List.copyOf(pages);
	}

	public static Path path() {
		return StatsWebuiPlus.configFile(".json");
	}

	public static WebConfig defaults() {
		return new WebConfig(DEFAULT_PORT, DEFAULT_BIND, "", defaultWindowTitle(), DEFAULT_REFRESH_COOLDOWN_MS, 0, 0,
			DEFAULT_LANGUAGE, LANGUAGES, DEFAULT_HEADER_IMAGE, DEFAULT_HEADER_IMAGE_POSITION, "", defaultSiteHeading(),
			defaultSiteHeadingSlogan(), DEFAULT_SITE_HEADING_FONT, CustomFont.empty(), DEFAULT_SLOGAN_STYLE,
			DEFAULT_PAGE_TITLE_FONT, DEFAULT_AVATAR_POSE, 0, true, true, "", "", true, true,
			defaultLinks(), defaultPages(), "");
	}

	/** Server start: reads the file and writes it back with the checked values, or writes the defaults if there is none. */
	public static WebConfig load() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			if (!Files.exists(path)) {
				WebConfig defaults = defaults();
				defaults.write(path);
				StatsWebuiPlus.LOGGER.info("Wrote default config {}", path);
				writeDefaultHeaderImage();
				writeSamplePages();
				return defaults;
			}
			WebConfig config = read(path);
			// So the file gains new keys and drops unknown ones.
			config.write(path);
			return config;
		} catch (Exception e) {
			// The owner's file stays as it is.
			StatsWebuiPlus.LOGGER.warn("Could not read {}, using defaults", path, e);
			return defaults();
		}
	}

	/**
	 * The file changed while the server runs. Never writes: the file may be half edited, and writing it would report a
	 * change to the file watcher again. Null when the file can't be read, so the caller keeps the current config.
	 */
	public static WebConfig reload() {
		Path path = path();
		try {
			return read(path);
		} catch (Exception e) {
			StatsWebuiPlus.LOGGER.warn("Could not read {}, keeping the current config: {}", path, e.toString());
			return null;
		}
	}

	/**
	 * Half the CPUs, at least 1 and at most 4: the game keeps the other half. Half the CPUs Java may use (a container's
	 * limit, not the machine's); more threads than that took CPU time from the game in the load test (scripts/load_test)
	 * and were barely faster.
	 */
	public static int automaticWebThreads(int cpus) {
		return Math.max(1, Math.min(4, cpus / 2));
	}

	private static WebConfig read(Path path) throws IOException {
		JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();

		int port = intValue(json, "port", DEFAULT_PORT);
		if (port < 1 || port > 65535) {
			StatsWebuiPlus.LOGGER.warn("Invalid port {}, using {}", port, DEFAULT_PORT);
			port = DEFAULT_PORT;
		}
		String bind = string(json, "bind", DEFAULT_BIND);
		if (bind.isEmpty()) {
			bind = DEFAULT_BIND;
		}
		String world = string(json, "world", "");
		if (world.contains("..") || world.startsWith("/") || world.startsWith("\\")) {
			StatsWebuiPlus.LOGGER.warn("Ignoring unsafe world folder {}", world);
			world = "";
		}
		Object windowTitle = windowTitle(json);
		int refreshCooldownMs = intValue(json, "refreshCooldownMs", DEFAULT_REFRESH_COOLDOWN_MS);
		if (refreshCooldownMs < 0 || refreshCooldownMs > MAX_REFRESH_COOLDOWN_MS) {
			StatsWebuiPlus.LOGGER.warn("refreshCooldownMs must be between 0 and {}, using {}", MAX_REFRESH_COOLDOWN_MS,
				DEFAULT_REFRESH_COOLDOWN_MS);
			refreshCooldownMs = DEFAULT_REFRESH_COOLDOWN_MS;
		}
		int saveIntervalSeconds = intValue(json, "saveIntervalSeconds", 0);
		if (saveIntervalSeconds < 0 || saveIntervalSeconds > MAX_SAVE_INTERVAL_SECONDS) {
			StatsWebuiPlus.LOGGER.warn("saveIntervalSeconds must be between 0 and {}, using {}", MAX_SAVE_INTERVAL_SECONDS, 0);
			saveIntervalSeconds = 0;
		}
		int webThreads = intValue(json, "webThreads", 0);
		if (webThreads < 0 || webThreads > MAX_WEB_THREADS) {
			StatsWebuiPlus.LOGGER.warn("webThreads must be between 0 and {}, using {} (automatic)", MAX_WEB_THREADS, 0);
			webThreads = 0;
		}
		String defaultLanguage = string(json, "defaultLanguage", DEFAULT_LANGUAGE).toLowerCase(Locale.ROOT);
		if (!LANGUAGES.contains(defaultLanguage)) {
			StatsWebuiPlus.LOGGER.warn("defaultLanguage must be one of {}, using {}", LANGUAGES, DEFAULT_LANGUAGE);
			defaultLanguage = DEFAULT_LANGUAGE;
		}
		List<String> languages = languages(json);
		if (!languages.contains(defaultLanguage)) {
			String fallback = languages.contains(DEFAULT_LANGUAGE) ? DEFAULT_LANGUAGE : languages.getFirst();
			StatsWebuiPlus.LOGGER.warn("defaultLanguage {} is not in languages {}, using {}", defaultLanguage, languages, fallback);
			defaultLanguage = fallback;
		}
		String headerImage = headerImage(json);
		String headerImagePosition = choice(json, "headerImagePosition", HEADER_IMAGE_POSITIONS, DEFAULT_HEADER_IMAGE_POSITION);
		String favicon = favicon(json);
		Object siteHeading = text(json, "siteHeading", defaultSiteHeading());
		Object siteHeadingSlogan = text(json, "siteHeadingSlogan", defaultSiteHeadingSlogan());
		String siteHeadingFont = choice(json, "siteHeadingFont", SITE_HEADING_FONTS, DEFAULT_SITE_HEADING_FONT);
		CustomFont siteHeadingCustomFont = CustomFont.parse(json.get("siteHeadingCustomFont"));
		if (siteHeadingFont.equals("custom") && siteHeadingCustomFont.files().isEmpty()) {
			StatsWebuiPlus.LOGGER.warn("siteHeadingFont is custom, but siteHeadingCustomFont names no font file; using {}",
				DEFAULT_SITE_HEADING_FONT);
			siteHeadingFont = DEFAULT_SITE_HEADING_FONT;
		}
		String siteHeadingSloganStyle = choice(json, "siteHeadingSloganStyle", SLOGAN_STYLES, DEFAULT_SLOGAN_STYLE);
		String pageTitleFont = choice(json, "pageTitleFont", PAGE_TITLE_FONTS, DEFAULT_PAGE_TITLE_FONT);
		String avatarPose = choice(json, "avatarPose", AVATAR_POSES, DEFAULT_AVATAR_POSE);
		double avatarAngle = json.has("avatarAngle") && json.get("avatarAngle").isJsonPrimitive()
			? json.get("avatarAngle").getAsDouble()
			: 0;
		// Written this way round so NaN fails it too.
		if (!(avatarAngle >= -180 && avatarAngle <= 180)) {
			StatsWebuiPlus.LOGGER.warn("avatarAngle must be between -180 and 180 degrees, using 0");
			avatarAngle = 0;
		}
		boolean avatarRotate = flag(json, "avatarRotate");
		boolean statusBar = flag(json, "statusBar");
		String serverAddress = string(json, "serverAddress", "");
		String siteUrl = siteUrl(json);
		boolean geyserApi = flag(json, "geyserApi");
		boolean downloadGameAssets = flag(json, "downloadGameAssets");
		List<MenuLink> links = links(json);
		List<CustomPage> pages = pages(json);
		String configuredStart = string(json, "startPage", "");
		String startPage = configuredStart;
		if (!startPage.isEmpty() && pages.stream().noneMatch(page -> page.id().equals(configuredStart) && page.enabled())) {
			StatsWebuiPlus.LOGGER.warn("startPage {} is not an enabled page of pages; the ranking stays the start page", startPage);
			startPage = "";
		}

		return new WebConfig(port, bind, world, windowTitle, refreshCooldownMs, saveIntervalSeconds, webThreads, defaultLanguage,
			languages, headerImage, headerImagePosition, favicon, siteHeading,
			siteHeadingSlogan, siteHeadingFont, siteHeadingCustomFont, siteHeadingSloganStyle, pageTitleFont, avatarPose,
			avatarAngle, avatarRotate, statusBar, serverAddress, siteUrl, geyserApi, downloadGameAssets,
			links, pages, startPage);
	}

	private static int intValue(JsonObject json, String key, int fallback) {
		return json.has(key) ? json.get(key).getAsInt() : fallback;
	}

	private static String string(JsonObject json, String key, String fallback) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString().trim() : fallback;
	}

	private static boolean flag(JsonObject json, String key) {
		return !json.has(key) || !json.get(key).isJsonPrimitive() || json.get(key).getAsBoolean();
	}

	private static String choice(JsonObject json, String key, List<String> allowed, String fallback) {
		String value = string(json, key, fallback).toLowerCase(Locale.ROOT);
		if (!allowed.contains(value)) {
			StatsWebuiPlus.LOGGER.warn(key + " must be one of {}, using {}", allowed, fallback);
			return fallback;
		}
		return value;
	}

	/** Missing (or null) gives the default; an empty value hides the text. */
	private static Object text(JsonObject json, String key, Object fallback) {
		return json.has(key) && !json.get(key).isJsonNull() ? LocalizedText.parse(json.get(key)) : fallback;
	}

	private static List<String> languages(JsonObject json) {
		if (!json.has("languages") || !json.get("languages").isJsonArray()) {
			return LANGUAGES;
		}
		List<String> enabled = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("languages")) {
			String code = element.isJsonPrimitive() ? element.getAsString().trim().toLowerCase(Locale.ROOT) : "";
			if (!LANGUAGES.contains(code)) {
				StatsWebuiPlus.LOGGER.warn("Ignoring unknown language {} in languages; supported: {}", element, LANGUAGES);
			} else if (!enabled.contains(code)) {
				enabled.add(code);
			}
		}
		if (enabled.isEmpty()) {
			StatsWebuiPlus.LOGGER.warn("languages enables no supported language, enabling all of {}", LANGUAGES);
			return LANGUAGES;
		}
		return enabled;
	}

	/** Relative to the server's config folder; "" disables it. */
	private static String headerImage(JsonObject json) {
		String image = string(json, "headerImage", DEFAULT_HEADER_IMAGE).replace('\\', '/');
		if (image.contains("..") || image.startsWith("/") || DRIVE_LETTER.matcher(image).matches()) {
			StatsWebuiPlus.LOGGER.warn("headerImage must be a path inside the config folder, disabling it: {}", image);
			return "";
		}
		String lower = image.toLowerCase(Locale.ROOT);
		if (!image.isEmpty() && HEADER_IMAGE_TYPES.stream().noneMatch(lower::endsWith)) {
			StatsWebuiPlus.LOGGER.warn("headerImage must be a .png, .jpg, .webp or .gif file, disabling it: {}", image);
			return "";
		}
		return image;
	}

	private static String favicon(JsonObject json) {
		String icon = string(json, "favicon", "").replace('\\', '/');
		if (icon.contains("..") || icon.startsWith("/") || DRIVE_LETTER.matcher(icon).matches()) {
			StatsWebuiPlus.LOGGER.warn("favicon must be a path inside the config folder, ignoring it: {}", icon);
			return "";
		}
		String lower = icon.toLowerCase(Locale.ROOT);
		if (!icon.isEmpty() && FAVICON_TYPES.stream().noneMatch(lower::endsWith)) {
			StatsWebuiPlus.LOGGER.warn("favicon must be a .png, .ico, .svg, .webp or .gif file, ignoring it: {}", icon);
			return "";
		}
		return icon;
	}

	/** An http(s) address: scheme, host and maybe a port, nothing after it (a trailing slash is dropped). */
	private static String siteUrl(JsonObject json) {
		String url = string(json, "siteUrl", "");
		if (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		if (!url.isEmpty() && !SITE_URL.matcher(url).matches()) {
			StatsWebuiPlus.LOGGER.warn("siteUrl must be like https://stats.example.com, ignoring it: {}", url);
			return "";
		}
		return url;
	}

	/** Every text must contain the page's name ({page} or <page>). */
	private static Object windowTitle(JsonObject json) {
		if (!json.has("windowTitle")) {
			return defaultWindowTitle();
		}
		Object title = LocalizedText.parse(json.get("windowTitle"));
		if (title instanceof Map<?, ?> texts) {
			Map<String, String> checked = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : texts.entrySet()) {
				String language = entry.getKey().toString();
				String text = entry.getValue().toString();
				if (!text.isEmpty() && !hasPagePlaceholder(text)) {
					// Blank, so that language uses the English title.
					StatsWebuiPlus.LOGGER.warn("windowTitle for {} must include {page} or <page>, ignoring it", language);
					text = "";
				}
				checked.put(language, text);
			}
			title = checked;
		} else if (!hasPagePlaceholder(title.toString())) {
			StatsWebuiPlus.LOGGER.warn("windowTitle must include {page} or <page>, using default");
			title = defaultWindowTitle();
		}
		if (LocalizedText.isEmpty(title)) {
			StatsWebuiPlus.LOGGER.warn("windowTitle is empty, using default");
			title = defaultWindowTitle();
		}
		return title;
	}

	private static boolean hasPagePlaceholder(String text) {
		return text.contains("{page}") || text.contains("<page>");
	}

	private static List<MenuLink> links(JsonObject json) {
		if (!json.has("links")) {
			return defaultLinks();
		}
		if (!json.get("links").isJsonArray()) {
			StatsWebuiPlus.LOGGER.warn("links must be a list of {\"label\": ..., \"url\": ...} entries, ignoring it");
			return List.of();
		}
		List<MenuLink> links = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("links")) {
			MenuLink link = MenuLink.parse(element);
			if (link != null) {
				links.add(link);
			}
		}
		return links;
	}

	private static List<CustomPage> pages(JsonObject json) {
		if (!json.has("pages")) {
			return defaultPages();
		}
		if (!json.get("pages").isJsonArray()) {
			StatsWebuiPlus.LOGGER.warn("pages must be a list of {\"id\": ..., \"label\": ..., \"place\": ...} entries, ignoring it");
			return List.of();
		}
		List<CustomPage> pages = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (JsonElement element : json.getAsJsonArray("pages")) {
			CustomPage page = CustomPage.parse(element);
			if (page == null) {
				continue;
			}
			if (!ids.add(page.id())) {
				StatsWebuiPlus.LOGGER.warn("Ignoring the second page with id {}", page.id());
				continue;
			}
			pages.add(page);
		}
		return pages;
	}

	// The defaults are per language, so the default config shows how to translate them.

	private static Object defaultWindowTitle() {
		return LocalizedText.of("en", "{page} - My Server", "de", "{page} - Mein Server", "es", "{page} - Mi servidor",
			"fr", "{page} - Mon serveur");
	}

	private static Object defaultSiteHeading() {
		return LocalizedText.of("en", "My Server", "de", "Mein Server", "es", "Mi servidor", "fr", "Mon serveur");
	}

	private static Object defaultSiteHeadingSlogan() {
		return LocalizedText.of("en", "The best server in the world.", "de", "Der beste Server der Welt.",
			"es", "El mejor servidor del mundo.", "fr", "Le meilleur serveur du monde.");
	}

	/** Placeholders: they only show up in the menu once a URL is filled in. */
	private static List<MenuLink> defaultLinks() {
		return List.of(
			new MenuLink(LocalizedText.of("en", "Map", "de", "Karte", "es", "Mapa", "fr", "Carte"), "", true, "menu",
				"minecraft:filled_map"),
			new MenuLink(LocalizedText.of("en", "Discord", "de", "Discord", "es", "Discord", "fr", "Discord"), "", true, "menu",
				"minecraft:bell"),
			new MenuLink(LocalizedText.of("en", "Dashboard", "de", "Dashboard", "es", "Panel", "fr", "Tableau de bord"), "", true,
				"menu", "minecraft:comparator")
		);
	}

	/**
	 * Samples, off until the server owner fills in their files and sets "enabled": true. "home" is a start page (set
	 * "startPage": "home" too).
	 */
	private static List<CustomPage> defaultPages() {
		return List.of(
			new CustomPage("home", LocalizedText.of("en", "Home", "de", "Start", "es", "Inicio", "fr", "Accueil"), "main", false,
				"minecraft:oak_door"),
			new CustomPage("imprint",
				LocalizedText.of("en", "Imprint", "de", "Impressum", "es", "Aviso legal", "fr", "Mentions légales"), "footer", false,
				CustomPage.DEFAULT_ICON),
			new CustomPage("privacy", LocalizedText.of("en", "Privacy Policy", "de", "Datenschutzerklärung",
				"es", "Política de privacidad", "fr", "Politique de confidentialité"), "footer", false, CustomPage.DEFAULT_ICON),
			new CustomPage("rules", LocalizedText.of("en", "Rules", "de", "Regeln", "es", "Normas", "fr", "Règles"), "menu", false,
				"minecraft:lectern")
		);
	}

	/** A text map that can't be changed through a getter; a LinkedHashMap underneath, so the languages keep their order. */
	private static Object frozen(Object text) {
		if (text instanceof Map<?, ?> texts) {
			return Collections.unmodifiableMap(new LinkedHashMap<>(texts));
		}
		return text;
	}

	private void write(Path path) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("port", port);
		body.put("bind", bind);
		body.put("world", world);
		body.put("windowTitle", windowTitle);
		body.put("refreshCooldownMs", refreshCooldownMs);
		body.put("saveIntervalSeconds", saveIntervalSeconds);
		body.put("webThreads", webThreads);
		body.put("defaultLanguage", defaultLanguage);
		body.put("languages", languages);
		body.put("headerImage", headerImage);
		body.put("headerImagePosition", headerImagePosition);
		body.put("favicon", favicon);
		body.put("siteHeading", siteHeading);
		body.put("siteHeadingSlogan", siteHeadingSlogan);
		body.put("siteHeadingFont", siteHeadingFont);
		body.put("siteHeadingCustomFont", siteHeadingCustomFont.toJson());
		body.put("siteHeadingSloganStyle", siteHeadingSloganStyle);
		body.put("pageTitleFont", pageTitleFont);
		body.put("avatarPose", avatarPose);
		body.put("avatarAngle", avatarAngle);
		body.put("avatarRotate", avatarRotate);
		body.put("statusBar", statusBar);
		body.put("serverAddress", serverAddress);
		body.put("siteUrl", siteUrl);
		body.put("geyserApi", geyserApi);
		body.put("downloadGameAssets", downloadGameAssets);
		body.put("startPage", startPage);
		body.put("links", links.stream().map(MenuLink::toJson).toList());
		body.put("pages", pages.stream().map(CustomPage::toJson).toList());
		Files.writeString(path, GSON.toJson(body) + "\n", StandardCharsets.UTF_8);
	}

	/**
	 * Only next to a new config, so a server owner who deletes the image doesn't get it back; "headerImage": "" hides it
	 * without deleting.
	 */
	private static void writeDefaultHeaderImage() {
		Path target = StatsWebuiPlus.platform().configDir().resolve(DEFAULT_HEADER_IMAGE);
		if (Files.exists(target)) {
			return;
		}
		try (InputStream image = WebConfig.class.getResourceAsStream("/assets/" + StatsWebuiPlus.MOD_ID + "/header.png")) {
			if (image == null) {
				StatsWebuiPlus.LOGGER.warn("The default header image is missing from the mod JAR");
				return;
			}
			Files.createDirectories(target.getParent());
			Files.copy(image, target);
			StatsWebuiPlus.LOGGER.info("Wrote default header image {}", target);
		} catch (IOException e) {
			StatsWebuiPlus.LOGGER.warn("Could not write the default header image {}", target, e);
		}
	}

	/** Likewise only next to a new config, so deleted samples don't come back, and never over an existing file. */
	private static void writeSamplePages() {
		Path folder = StatsWebuiPlus.platform().configDir().resolve(StatsWebuiPlus.MOD_ID).resolve("pages");
		for (CustomPage page : defaultPages()) {
			for (String language : LANGUAGES) {
				String name = page.id() + "/" + language + ".md";
				Path target = folder.resolve(name);
				if (Files.exists(target)) {
					continue;
				}
				try (InputStream sample = WebConfig.class.getResourceAsStream("/assets/" + StatsWebuiPlus.MOD_ID + "/pages/" + name)) {
					if (sample == null) {
						StatsWebuiPlus.LOGGER.warn("The sample page {} is missing from the mod JAR", name);
						continue;
					}
					Files.createDirectories(target.getParent());
					Files.copy(sample, target);
				} catch (IOException e) {
					StatsWebuiPlus.LOGGER.warn("Could not write the sample page {}", target, e);
				}
			}
		}
		StatsWebuiPlus.LOGGER.info("Wrote sample pages to {}; fill them in and set \"enabled\": true in the config", folder);
	}

	/** The threads answering web requests: {@link #webThreads()}, or {@link #automaticWebThreads(int)} for 0. */
	public int webThreadCount() {
		return webThreads > 0 ? webThreads : automaticWebThreads(Runtime.getRuntime().availableProcessors());
	}

	/** The links that are shown, in order. */
	public List<Map<String, Object>> linksPayload() {
		return links.stream().filter(MenuLink::shown).map(MenuLink::toJson).toList();
	}

	/** How the web UI looks, for the API. */
	public Map<String, Object> brandingPayload() {
		Map<String, Object> branding = new LinkedHashMap<>();
		branding.put("windowTitle", windowTitle);
		branding.put("headerImagePosition", headerImagePosition);
		branding.put("siteHeading", siteHeading);
		branding.put("siteHeadingSlogan", siteHeadingSlogan);
		branding.put("siteHeadingFont", siteHeadingFont);
		branding.put("siteHeadingSloganStyle", siteHeadingSloganStyle);
		branding.put("pageTitleFont", pageTitleFont);
		branding.put("avatarPose", avatarPose);
		branding.put("avatarAngle", avatarAngle);
		branding.put("avatarRotate", avatarRotate);
		branding.put("statusBar", statusBar);
		branding.put("serverAddress", serverAddress);
		return Collections.unmodifiableMap(branding);
	}
}
