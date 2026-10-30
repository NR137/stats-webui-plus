package webui.stats.mc.web;

import com.sun.net.httpserver.HttpExchange;
import webui.stats.mc.McStatsWebui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import java.util.zip.ZipException;
import java.util.stream.Stream;

public final class StaticHandler {
	private static final String ROOT = "/assets/" + McStatsWebui.MOD_ID + "/web";
	// Not html: a missing /something.html still gets the app, as before.
	private static final Set<String> FILE_TYPES = Set.of(
		"js", "mjs", "css", "map", "json", "txt", "xml", "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "woff", "woff2", "ttf"
	);

	private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
	// Per resource, so browsers can ask "still the same?" and get an empty 304 instead of the whole file again.
	private final Map<String, String> etags = new ConcurrentHashMap<>();

	public void preload() {
		cache.clear();
		etags.clear();
		URL index = McStatsWebui.class.getResource(ROOT + "/index.html");
		if (index == null) {
			McStatsWebui.LOGGER.error("Frontend is not packaged in the mod JAR");
			return;
		}
		try {
			URLConnection connection = index.openConnection();
			if (connection instanceof JarURLConnection jarConnection) {
				cacheJar(jarConnection.getJarFile());
			} else {
				cacheDirectory(Path.of(index.toURI()).getParent());
			}
		} catch (Exception e) {
			McStatsWebui.LOGGER.error("Could not cache frontend from {}", index, e);
			cacheResource(ROOT + "/index.html");
			cacheResource(ROOT + "/200.html");
		}
		McStatsWebui.LOGGER.info("Cached {} frontend files", cache.size());
	}

	public void handle(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			exchange.sendResponseHeaders(405, -1);
			exchange.close();
			return;
		}

		String path = exchange.getRequestURI().getPath();
		path = URLDecoder.decode(path, StandardCharsets.UTF_8);
		if (climbsUp(path)) {
			exchange.sendResponseHeaders(400, -1);
			exchange.close();
			return;
		}
		if (path.endsWith("/")) {
			path = path + "index.html";
		}
		if (path.equals("")) {
			path = "/index.html";
		}

		boolean head = "HEAD".equalsIgnoreCase(method);
		String resource = ROOT + path;
		byte[] bytes = load(resource);
		if (bytes == null && isAsset(path)) {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
			return;
		}
		if (bytes == null && !path.endsWith(".html")) {
			// A page SvelteKit prerendered (/about -> about.html): the same shell as 200.html, but it already names
			// that page's scripts and styles, so the browser loads them right away.
			resource = ROOT + path + ".html";
			bytes = load(resource);
		}
		if (bytes == null) {
			// Pages that can't be prerendered, e.g. /players/<name>: the browser works out the page.
			resource = ROOT + "/200.html";
			bytes = load(resource);
		}
		if (bytes == null) {
			resource = ROOT + "/index.html";
			bytes = load(resource);
		}
		if (bytes == null) {
			send(
				exchange,
				503,
				"text/plain; charset=utf-8",
				"Frontend is not packaged. Rebuild the mod JAR, stop Minecraft, then replace the Stats WebUI Plus jar in mods/."
					.getBytes(StandardCharsets.UTF_8),
				head,
				resource
			);
			return;
		}

		byte[] body = bytes;
		String etag = etags.computeIfAbsent(resource, key -> etag(body));
		exchange.getResponseHeaders().set("ETag", etag);
		if (matches(exchange.getRequestHeaders().getFirst("If-None-Match"), etag)) {
			exchange.getResponseHeaders().set("Cache-Control", cacheControl(resource));
			exchange.sendResponseHeaders(304, -1);
			exchange.close();
			return;
		}
		send(exchange, 200, mime(resource), bytes, head, resource);
	}

	private static String etag(byte[] bytes) {
		CRC32 crc = new CRC32();
		crc.update(bytes);
		return "\"" + Long.toHexString(crc.getValue()) + "-" + Integer.toHexString(bytes.length) + "\"";
	}

	/** If-None-Match may list several tags, and proxies that compress the file mark them weak (W/"..."). */
	private static boolean matches(String ifNoneMatch, String etag) {
		if (ifNoneMatch == null) {
			return false;
		}
		for (String candidate : ifNoneMatch.split(",")) {
			String tag = candidate.trim();
			if (tag.startsWith("W/")) {
				tag = tag.substring(2);
			}
			if (tag.equals(etag) || tag.equals("*")) {
				return true;
			}
		}
		return false;
	}

	private static String cacheControl(String resource) {
		if (resource.contains("/_app/")) {
			// File names contain a hash of their content, so they never change.
			return "public, max-age=31536000, immutable";
		}
		if (resource.contains("/mc/")) {
			// Icons and names only change with a mod update; a day of staleness after one is fine.
			return "public, max-age=86400";
		}
		return "no-cache";
	}

	private byte[] load(String resource) {
		byte[] cached = cache.get(resource);
		if (cached != null) {
			return cached;
		}
		return cacheResource(resource);
	}

	private byte[] cacheResource(String resource) {
		try (InputStream stream = McStatsWebui.class.getResourceAsStream(resource)) {
			if (stream == null) {
				return null;
			}
			byte[] bytes = stream.readAllBytes();
			cache.put(resource, bytes);
			return bytes;
		} catch (ZipException e) {
			McStatsWebui.LOGGER.error(
				"Mod JAR was replaced while Minecraft is running. Stop the server, copy the new JAR into mods, then start again."
			);
			return null;
		} catch (IOException e) {
			McStatsWebui.LOGGER.error("Could not read {}", resource, e);
			return null;
		}
	}

	private void cacheJar(JarFile jar) throws IOException {
		String prefix = ROOT.substring(1) + "/";
		jar.stream()
			.filter(entry -> !entry.isDirectory() && entry.getName().startsWith(prefix))
			.forEach(entry -> {
				try (InputStream stream = jar.getInputStream(entry)) {
					cache.put("/" + entry.getName(), stream.readAllBytes());
				} catch (IOException e) {
					McStatsWebui.LOGGER.error("Could not cache {}", entry.getName(), e);
				}
			});
	}

	private void cacheDirectory(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.filter(Files::isRegularFile).forEach(file -> {
				String relative = dir.relativize(file).toString().replace('\\', '/');
				try {
					cache.put(ROOT + "/" + relative, Files.readAllBytes(file));
				} catch (IOException e) {
					McStatsWebui.LOGGER.error("Could not cache {}", file, e);
				}
			});
		}
	}

	private static void send(
		HttpExchange exchange,
		int status,
		String contentType,
		byte[] bytes,
		boolean head,
		String resource
	) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", contentType);
		if (status == 200) {
			exchange.getResponseHeaders().set("Cache-Control", cacheControl(resource));
		}
		exchange.sendResponseHeaders(status, head ? -1 : bytes.length);
		if (!head) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		} else {
			exchange.close();
		}
	}

	/** A ".." segment, also with "\\" as separator. Dots inside a name ("/players/..Steve") are fine. */
	private static boolean climbsUp(String path) {
		for (String segment : path.split("[/\\\\]", -1)) {
			if (segment.equals("..")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Paths that can only be files, so a missing one is a 404 and not a page: everything under /_app/ and /mc/, and
	 * files at the top such as /favicon.ico. Deeper paths are pages, whatever their name: the web UI asks the API
	 * whether /players/.Steve, /players/Steve.png or /advancements/mymod/data.json exists.
	 */
	private static boolean isAsset(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		if (lower.startsWith("/_app/") || lower.startsWith("/mc/")) {
			return true;
		}
		if (lower.lastIndexOf('/') != 0) {
			return false;
		}
		int dot = lower.lastIndexOf('.');
		return dot > 1 && FILE_TYPES.contains(lower.substring(dot + 1));
	}

	private static String mime(String resource) {
		String lower = resource.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".html")) {
			return "text/html; charset=utf-8";
		}
		if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
			return "text/javascript; charset=utf-8";
		}
		if (lower.endsWith(".css")) {
			return "text/css; charset=utf-8";
		}
		if (lower.endsWith(".svg")) {
			return "image/svg+xml";
		}
		if (lower.endsWith(".png")) {
			return "image/png";
		}
		if (lower.endsWith(".gif")) {
			return "image/gif";
		}
		if (lower.endsWith(".webp")) {
			return "image/webp";
		}
		if (lower.endsWith(".ico")) {
			return "image/x-icon";
		}
		if (lower.endsWith(".woff2")) {
			return "font/woff2";
		}
		if (lower.endsWith(".ttf")) {
			return "font/ttf";
		}
		if (lower.endsWith(".json") || lower.endsWith(".map")) {
			return "application/json";
		}
		return "application/octet-stream";
	}
}
