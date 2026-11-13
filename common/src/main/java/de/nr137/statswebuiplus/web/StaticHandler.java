package de.nr137.statswebuiplus.web;

import com.sun.net.httpserver.HttpExchange;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.assets.GameAssets;
import de.nr137.statswebuiplus.assets.GameLanguages;
import de.nr137.statswebuiplus.stats.StatsCache;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The website (the SvelteKit build packed into the JAR under {@code /assets/stats_webui_plus/web}, kept in memory)
 * and the game's files under {@code /mc/} (see {@link GameAssets}). Hashed app files and game files of the current
 * stamp are kept by browsers for good; everything else is checked again on each visit and gets an empty 304 when
 * unchanged.
 */
public final class StaticHandler {
	static final String ROOT = "/assets/" + StatsWebuiPlus.MOD_ID + "/web";
	private static final String INDEX = "index.html";
	private static final String FALLBACK = "200.html";
	private static final String GAME = "mc/";
	private static final String IMMUTABLE = "public, max-age=31536000, immutable";
	private static final String NO_CACHE = "no-cache";
	private static final String NOT_PACKAGED = "Frontend is not packaged. Rebuild the mod JAR, stop Minecraft, then "
		+ "replace the Stats WebUI Plus jar in mods/.";
	// Paths with these extensions are files: a missing one is a 404, not a page.
	private static final Set<String> FILE_TYPES = Set.of(
		"js", "mjs", "css", "map", "json", "txt", "xml", "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "woff",
		"woff2", "ttf", "yaml"
	);
	private static final Map<String, String> TYPES = Map.ofEntries(
		Map.entry("html", "text/html; charset=utf-8"),
		Map.entry("js", "text/javascript; charset=utf-8"),
		Map.entry("mjs", "text/javascript; charset=utf-8"),
		Map.entry("css", "text/css; charset=utf-8"),
		Map.entry("json", "application/json; charset=utf-8"),
		Map.entry("map", "application/json; charset=utf-8"),
		Map.entry("svg", "image/svg+xml; charset=utf-8"),
		Map.entry("png", "image/png"),
		Map.entry("jpg", "image/jpeg"),
		Map.entry("jpeg", "image/jpeg"),
		Map.entry("gif", "image/gif"),
		Map.entry("webp", "image/webp"),
		Map.entry("ico", "image/x-icon"),
		Map.entry("woff", "font/woff"),
		Map.entry("woff2", "font/woff2"),
		Map.entry("ttf", "font/ttf"),
		Map.entry("txt", "text/plain; charset=utf-8"),
		Map.entry("xml", "application/xml; charset=utf-8"),
		Map.entry("yaml", "application/yaml; charset=utf-8")
	);
	private static final Pattern SERVER_ENGLISH = Pattern.compile("lang/(en|de|es|fr)\\.json");
	private static final Pattern SEGMENT_SEPARATOR = Pattern.compile("[/\\\\]");
	private static final String HEAD_END = "</head>";
	// The attribute app.html starts with (src/app.html).
	private static final String DEFAULT_TITLE_FONT = "data-title-font=\"tiny\"";

	/** A file as it is sent: its bytes, type and ETag, and the gzipped bytes once someone asked for them. */
	private static final class Cached {
		final byte[] bytes;
		final String contentType;
		final String etag;
		private volatile byte[] gzipped;

		Cached(byte[] bytes, String contentType) {
			this.bytes = bytes;
			this.contentType = contentType;
			this.etag = HttpJson.etag(bytes);
		}

		byte[] gzipped() {
			byte[] done = gzipped;
			if (done == null) {
				done = HttpJson.gzip(bytes);
				gzipped = done;
			}
			return done;
		}
	}

	// The website's files by path below ROOT ("index.html", "_app/version.json").
	private final Map<String, Cached> site = new ConcurrentHashMap<>();
	// The game's files by path below /mc/, for the stamp they were read under (gameStamp; "" while not ready).
	private final Map<String, Cached> game = new ConcurrentHashMap<>();
	// Pages with the stamp and the server's settings put in, by path, for pagesKey.
	private final Map<String, Cached> stampedPages = new ConcurrentHashMap<>();
	private volatile String gameStamp = "";
	private volatile String pagesKey = "";
	// Pages with their link preview tags, the most recently sent first: one per page, address and player at most, and
	// only so many, since any player's address is a page of its own.
	private static final int PREVIEWS_KEPT = 256;
	private final Map<Preview, Cached> previews = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Preview, Cached> eldest) {
			return size() > PREVIEWS_KEPT;
		}
	};

	private record Preview(Cached page, String tags) {
	}
	// The current config: the pages name its page title font, so the browser fetches only the font that is used.
	private final Supplier<WebConfig> config;

	/** With the default settings (tests). */
	public StaticHandler() {
		this(WebConfig::defaults);
	}

	public StaticHandler(Supplier<WebConfig> config) {
		this.config = config;
	}

	/** Reads every file of the website into memory, so no request waits for the JAR. */
	public void preload() {
		site.clear();
		int count = 0;
		try {
			for (String path : list()) {
				if (load(path) != null) {
					count++;
				}
			}
		} catch (IOException | RuntimeException e) {
			StatsWebuiPlus.LOGGER.warn("Could not list the frontend files ({}); reading them when asked", e.toString());
		}
		// The pages the website needs, also when the listing came up empty: where the mod is made of several folders
		// (NeoForge's dev runs), its web folder isn't found as a whole; the other files are read when asked.
		for (String path : List.of(INDEX, FALLBACK)) {
			if (!site.containsKey(path) && load(path) != null) {
				count++;
			}
		}
		if (site.containsKey(INDEX) || site.containsKey(FALLBACK)) {
			StatsWebuiPlus.LOGGER.info("Cached {} frontend files", count);
		} else {
			StatsWebuiPlus.LOGGER.warn("Frontend is not packaged in the mod JAR");
		}
	}

	public void handle(HttpExchange exchange) throws IOException {
		if (!HttpJson.isGetOrHead(exchange)) {
			exchange.getResponseHeaders().set("Allow", "GET, HEAD");
			HttpJson.empty(exchange, 405);
			return;
		}
		// Decoded once by the URI; "%2e%2e" is ".." here and refused like it.
		String path = exchange.getRequestURI().getPath();
		if (path == null) {
			HttpJson.empty(exchange, 400);
			return;
		}
		for (String segment : SEGMENT_SEPARATOR.split(path)) {
			if (segment.equals("..")) {
				HttpJson.empty(exchange, 400);
				return;
			}
		}
		String relative = path.startsWith("/") ? path.substring(1) : path;
		if (relative.isEmpty() || relative.endsWith("/")) {
			relative += INDEX;
		}
		if (relative.startsWith(GAME)) {
			serveGameFile(exchange, relative.substring(GAME.length()));
			return;
		}
		Cached file = site(relative);
		if (file != null) {
			send(exchange, relative, file, relative.startsWith("_app/immutable/") ? IMMUTABLE : NO_CACHE);
			return;
		}
		if (looksLikeFile(relative)) {
			HttpJson.empty(exchange, 404);
			return;
		}
		// A page: prerendered, else the app's fallback, which routes on the client.
		for (String page : List.of(relative + ".html", FALLBACK, INDEX)) {
			Cached found = site(page);
			if (found != null) {
				send(exchange, page, found, NO_CACHE);
				return;
			}
		}
		exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", HttpJson.NO_STORE);
		HttpJson.respond(exchange, 503, NOT_PACKAGED.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * {@code /mc/<rest>} or, stamped, {@code /mc/<stamp>/<rest>}: the stamp is the first folder and starts with a digit.
	 * Kept for good under the current stamp; under another, or without one, checked on each visit.
	 */
	private void serveGameFile(HttpExchange exchange, String rest) throws IOException {
		String cacheControl = NO_CACHE;
		int slash = rest.indexOf('/');
		if (slash > 0 && Character.isDigit(rest.charAt(0))) {
			String stamp = rest.substring(0, slash);
			rest = rest.substring(slash + 1);
			if (stamp.equals(GameAssets.get().stamp())) {
				cacheControl = IMMUTABLE;
			}
		}
		Cached file = game(rest);
		if (file == null) {
			HttpJson.empty(exchange, 404);
			return;
		}
		send(exchange, GAME + rest, file, cacheControl);
	}

	/**
	 * A game file: the extracted one, or a size variant made now, or (while the files aren't ready) the server's own
	 * English for the language files, or one the website ships under mc/. Kept once read, until the stamp changes.
	 */
	private Cached game(String relative) {
		String stamp = GameAssets.get().stamp();
		String key = stamp == null ? "" : stamp;
		if (!key.equals(gameStamp)) {
			// Other files now (ready, another version, turned off): what was kept belongs to the old ones.
			game.clear();
			gameStamp = key;
		}
		Cached kept = game.get(relative);
		if (kept != null) {
			return kept;
		}
		byte[] bytes = gameBytes(relative, stamp != null);
		if (bytes == null) {
			Cached own = site(GAME + relative);
			return own;
		}
		Cached file = new Cached(bytes, contentType(relative));
		game.put(relative, file);
		return file;
	}

	private static byte[] gameBytes(String relative, boolean ready) {
		try {
			Optional<Path> file = GameAssets.get().file(relative);
			if (file.isPresent()) {
				return Files.readAllBytes(file.get());
			}
			byte[] sized = GameAssets.get().sized(relative);
			if (sized != null) {
				return sized;
			}
			if (!ready && SERVER_ENGLISH.matcher(relative).matches()) {
				return GameLanguages.serverEnglish();
			}
		} catch (IOException | RuntimeException e) {
			StatsWebuiPlus.LOGGER.debug("Could not read the game file {}", relative, e);
		}
		return null;
	}

	/** The file with its ETag and caching; pages with the stamp of the game's files when there is one. */
	private void send(HttpExchange exchange, String path, Cached file, String cacheControl) throws IOException {
		Cached sent = file.contentType.startsWith("text/html") ? previewed(exchange, stamped(path, file)) : file;
		HttpJson.sendBytes(exchange, 200, sent.bytes, sent.etag, sent.contentType, cacheControl, sent::gzipped);
	}

	/**
	 * The page with {@code <meta name="mc-files" content="<stamp>" />} before {@code </head>}, which the website reads,
	 * and the configured page title font on {@code <html>} (app.html starts with the default): set before the page is
	 * drawn, the browser fetches only the font that is used.
	 */
	private Cached stamped(String path, Cached page) {
		String stamp = GameAssets.get().stamp();
		String titleFont = config.get().pageTitleFont();
		String key = stamp + "|" + titleFont;
		if (!key.equals(pagesKey)) {
			stampedPages.clear();
			pagesKey = key;
		}
		return stampedPages.computeIfAbsent(path, unused -> {
			String html = new String(page.bytes, StandardCharsets.UTF_8)
				.replace(DEFAULT_TITLE_FONT, "data-title-font=\"" + titleFont + "\"");
			int head = html.indexOf(HEAD_END);
			if (stamp != null && head >= 0) {
				html = html.substring(0, head) + "<meta name=\"mc-files\" content=\"" + stamp + "\" />" + html.substring(head);
			}
			return new Cached(html.getBytes(StandardCharsets.UTF_8), page.contentType);
		});
	}

	/** The page with the link preview tags for the address asked for (see LinkPreview) before {@code </head>}. */
	private Cached previewed(HttpExchange exchange, Cached page) {
		WebConfig current = config.get();
		String tags;
		try {
			tags = LinkPreview.tags(current, LinkPreview.origin(exchange, current), exchange.getRequestURI().getPath(),
				StatsCache.get().current().players());
		} catch (RuntimeException e) {
			// The page works without its preview.
			StatsWebuiPlus.LOGGER.warn("Could not make the link preview of {}", exchange.getRequestURI().getPath(), e);
			return page;
		}
		Preview key = new Preview(page, tags);
		synchronized (previews) {
			Cached kept = previews.get(key);
			if (kept != null) {
				return kept;
			}
		}
		String html = new String(page.bytes, StandardCharsets.UTF_8);
		int head = html.indexOf(HEAD_END);
		Cached result = head < 0 ? page
			: new Cached((html.substring(0, head) + tags + html.substring(head)).getBytes(StandardCharsets.UTF_8),
				page.contentType);
		synchronized (previews) {
			previews.put(key, result);
		}
		return result;
	}

	/** A website file from memory, else from the classpath (then kept); null if there is none. */
	private Cached site(String relative) {
		Cached file = site.get(relative);
		return file != null ? file : load(relative);
	}

	private Cached load(String relative) {
		URL url = StaticHandler.class.getResource(ROOT + "/" + relative);
		if (url == null) {
			return null;
		}
		try {
			// A folder reads as its listing (or as nothing in a JAR); it is no file to send.
			if ("jar".equals(url.getProtocol()) && url.openConnection() instanceof JarURLConnection jar
				&& jar.getJarEntry() != null && jar.getJarEntry().isDirectory()
				|| "file".equals(url.getProtocol()) && Files.isDirectory(Path.of(url.toURI()))) {
				return null;
			}
			// Read through the class loader, not the URL: NeoForge's dev runs serve the mod from a file system of their
			// own (union:), whose URLs don't open as connections.
			try (InputStream in = StaticHandler.class.getResourceAsStream(ROOT + "/" + relative)) {
				if (in == null) {
					return null;
				}
				Cached file = new Cached(in.readAllBytes(), contentType(relative));
				site.put(relative, file);
				return file;
			}
		} catch (IOException | URISyntaxException | RuntimeException e) {
			return null;
		}
	}

	/** Every file below ROOT, as paths relative to it: from the JAR, or from a folder in development. */
	private static List<String> list() throws IOException {
		URL url = StaticHandler.class.getResource(ROOT);
		if (url == null) {
			return List.of();
		}
		URI uri;
		try {
			uri = url.toURI();
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		Path root;
		try {
			root = Path.of(uri);
		} catch (FileSystemNotFoundException e) {
			// A JAR nobody opened as a file system yet; it stays open, the files are read later too.
			root = FileSystems.newFileSystem(uri, Map.of()).provider().getPath(uri);
		}
		Path base = root;
		try (Stream<Path> walk = Files.walk(base)) {
			return walk.filter(Files::isRegularFile)
				.map(file -> base.relativize(file).toString().replace('\\', '/'))
				.toList();
		}
	}

	/**
	 * Whether a missing path is a file rather than a page: at the top with the extension of a known file type
	 * ({@code /favicon.ico}), or anywhere below {@code /_app/} or {@code /mc/}. Below the top, a path with a dot is a
	 * page (a Bedrock player's ".Steve", an advancement "mymod/some.thing").
	 */
	private static boolean looksLikeFile(String relative) {
		if (relative.startsWith("_app/") || relative.startsWith(GAME)) {
			return true;
		}
		return relative.indexOf('/') < 0 && FILE_TYPES.contains(extension(relative));
	}

	private static String extension(String path) {
		int slash = path.lastIndexOf('/');
		int dot = path.lastIndexOf('.');
		return dot <= slash + 1 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	static String contentType(String path) {
		return TYPES.getOrDefault(extension(path), "application/octet-stream");
	}
}
