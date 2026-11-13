package de.nr137.statswebuiplus.web;

import com.sun.net.httpserver.HttpExchange;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.stats.PlayerRecord;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a link to the website shows where it is shared (Discord, WhatsApp, Telegram, Slack, X, ...): Open Graph and
 * Twitter card tags, put into each page as the server sends it, since those services don't run the website's
 * script. Title and description are the site heading and slogan in the server's default language; on a player's pages
 * the title is the player, as in the browser tab, with their level. The picture is the header image, large, or else
 * the favicon, small.
 */
public final class LinkPreview {
	private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]+(:\\d{1,5})?|\\[[0-9A-Fa-f:.]+\\](:\\d{1,5})?");
	private static final Pattern PAGE_PLACEHOLDER = Pattern.compile("\\{page\\}|<page>");
	// What separated the page from the rest of a window title, at its start or end.
	private static final Pattern SEPARATORS = Pattern.compile("^[\\s\\-–—|·:]+|[\\s\\-–—|·:]+$");
	// "Level" in the website's languages, for a player's preview.
	private static final Map<String, String> LEVEL = Map.of("de", "Level", "en", "Level", "es", "Nivel", "fr", "Niveau");

	private LinkPreview() {
	}

	/**
	 * The website's address as the visitor reached it: the config's siteUrl, else the request's Host header (with
	 * https when a proxy says so in X-Forwarded-Proto); null when there is no usable host.
	 */
	public static String origin(HttpExchange exchange, WebConfig config) {
		if (!config.siteUrl().isEmpty()) {
			return config.siteUrl();
		}
		String host = exchange.getRequestHeaders().getFirst("Host");
		if (host == null || !HOST.matcher(host).matches()) {
			return null;
		}
		String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
		boolean https = proto != null && proto.split(",")[0].trim().equalsIgnoreCase("https");
		return (https ? "https://" : "http://") + host;
	}

	/** The tags for the page at {@code path}; without an origin only those that need no address. */
	public static String tags(WebConfig config, String origin, String path, List<PlayerRecord> players) {
		String language = config.defaultLanguage();
		String heading = text(config.siteHeading(), language);
		String siteName = heading.isEmpty() ? withoutPage(text(config.windowTitle(), language)) : heading;
		String title = siteName;
		String description = text(config.siteHeadingSlogan(), language);
		PlayerRecord player = player(path, players);
		if (player != null) {
			title = PAGE_PLACEHOLDER.matcher(text(config.windowTitle(), language)).replaceAll(player.name());
			String level = LEVEL.getOrDefault(language, "Level") + " " + (int) Math.floor(player.level());
			description = description.isEmpty() ? level : level + " · " + description;
		}
		StringBuilder tags = new StringBuilder();
		meta(tags, "property", "og:type", "website");
		meta(tags, "property", "og:site_name", siteName);
		meta(tags, "property", "og:title", title);
		meta(tags, "property", "og:description", description);
		if (origin != null) {
			meta(tags, "property", "og:url", origin + path);
			String headerImage = HeaderImageHandler.url(config);
			meta(tags, "property", "og:image", origin + (headerImage != null ? headerImage : FaviconHandler.PATH));
			meta(tags, "name", "twitter:card", headerImage != null ? "summary_large_image" : "summary");
		}
		return tags.toString();
	}

	/** The player of /players/<name or UUID>/..., if there is one by that name or UUID. */
	private static PlayerRecord player(String path, List<PlayerRecord> players) {
		if (!path.startsWith("/players/")) {
			return null;
		}
		String key = path.substring("/players/".length()).split("/", 2)[0];
		if (key.isEmpty()) {
			return null;
		}
		for (PlayerRecord player : players) {
			if (player.name().equalsIgnoreCase(key) || player.uuid().toString().equalsIgnoreCase(key)) {
				return player;
			}
		}
		return null;
	}

	/** A config text in the language, else English, else the first one it has. */
	static String text(Object text, String language) {
		if (text instanceof String plain) {
			return plain.trim();
		}
		if (text instanceof Map<?, ?> texts) {
			for (Object key : List.of(language, "en")) {
				if (texts.get(key) instanceof String found && !found.isBlank()) {
					return found.trim();
				}
			}
			for (Object value : texts.values()) {
				if (value instanceof String found && !found.isBlank()) {
					return found.trim();
				}
			}
		}
		return "";
	}

	/** "{page} - My Server" without the page: "My Server". */
	private static String withoutPage(String windowTitle) {
		return SEPARATORS.matcher(PAGE_PLACEHOLDER.matcher(windowTitle).replaceAll("")).replaceAll("");
	}

	private static void meta(StringBuilder tags, String attribute, String name, String content) {
		if (content == null || content.isEmpty()) {
			return;
		}
		tags.append("<meta ").append(attribute).append("=\"").append(name).append("\" content=\"").append(escape(content))
			.append("\" />");
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
