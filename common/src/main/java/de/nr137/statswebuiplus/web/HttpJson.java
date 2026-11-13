package de.nr137.statswebuiplus.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;

/**
 * How every answer of the web server is sent: JSON (and other bodies) with an ETag, gzip for text, empty answers and
 * JSON errors. Each send finishes the exchange's body, so a handler that answers from another thread (MojangProxy)
 * needs nothing else; closing the exchange again afterwards does no harm.
 */
public final class HttpJson {
	public static final String JSON = "application/json; charset=utf-8";
	public static final String NO_STORE = "no-store";
	/** Bodies shorter than this go uncompressed: gzip's own header and the extra work outweigh the bytes saved. */
	static final int GZIP_MIN_BYTES = 1024;

	// No HTML escaping: names and texts go out as they are ("<" stays "<"); the web UI never puts them in as HTML.
	// Nulls are kept, the API documents them (an unknown time, a ranking nobody leads).
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

	// Text that shrinks well; images and fonts are compressed already.
	private static final Set<String> COMPRESSIBLE = Set.of(
		"application/json", "application/javascript", "text/javascript", "text/html", "text/css", "text/plain",
		"text/xml", "application/xml", "image/svg+xml", "application/yaml", "text/yaml"
	);

	/** The visitor went away while the answer was being sent; nothing the mod did wrong, so callers don't log it as an error. */
	public static final class ConnectionLost extends IOException {
		public ConnectionLost(IOException cause) {
			super("The connection was lost while answering: " + cause.getMessage(), cause);
		}
	}

	private HttpJson() {
	}

	/** Whether the request is a GET or a HEAD, the only methods the web server answers; each handler sends its own 405. */
	public static boolean isGetOrHead(HttpExchange exchange) {
		String method = exchange.getRequestMethod();
		return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
	}

	/** The payload as JSON, not to be kept by browsers or proxies. */
	public static void send(HttpExchange exchange, int status, Object payload) throws IOException {
		send(exchange, status, payload, NO_STORE);
	}

	/** The payload as JSON with this Cache-Control, an ETag, and an empty 304 for a browser that has it already. */
	public static void send(HttpExchange exchange, int status, Object payload, String cacheControl) throws IOException {
		sendBytes(exchange, status, toJson(payload).getBytes(StandardCharsets.UTF_8), JSON, cacheControl);
	}

	/**
	 * A body of any type with this Cache-Control, an ETag of it, and an empty 304 when the browser's copy matches; text
	 * is compressed for browsers that take gzip.
	 */
	public static void sendBytes(HttpExchange exchange, int status, byte[] body, String contentType, String cacheControl)
		throws IOException {
		sendBytes(exchange, status, body, etag(body), contentType, cacheControl, null);
	}

	/**
	 * As {@link #sendBytes(HttpExchange, int, byte[], String, String)} with an ETag worked out before, and the gzipped
	 * body from a cache if the caller keeps one (null: compressed now if needed).
	 */
	static void sendBytes(HttpExchange exchange, int status, byte[] body, String etag, String contentType, String cacheControl,
		Supplier<byte[]> gzipped) throws IOException {
		Headers headers = exchange.getResponseHeaders();
		headers.set("Cache-Control", cacheControl);
		headers.set("ETag", etag);
		if (isCompressible(contentType)) {
			// The 304 too: a cache must know that the answer it keeps depends on the header.
			headers.set("Vary", "Accept-Encoding");
		}
		if (status == 200 && matches(exchange.getRequestHeaders().getFirst("If-None-Match"), etag)) {
			empty(exchange, 304);
			return;
		}
		headers.set("Content-Type", contentType);
		respond(exchange, status, body, gzipped);
	}

	/** A JSON error: {@code {"error": code, "message": message}}, never cached. */
	public static void error(HttpExchange exchange, int status, String code, String message) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", code);
		body.put("message", message);
		exchange.getResponseHeaders().set("Cache-Control", NO_STORE);
		// An ETag set before the error (an answer that failed halfway) doesn't belong to it.
		exchange.getResponseHeaders().remove("ETag");
		exchange.getResponseHeaders().set("Content-Type", JSON);
		respond(exchange, status, GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
	}

	/** Only the status and the headers set so far (304, 404 of a file, 405). */
	public static void empty(HttpExchange exchange, int status) throws IOException {
		checkNotSent(exchange, status);
		try {
			exchange.sendResponseHeaders(status, -1);
			exchange.getResponseBody().close();
		} catch (IOException e) {
			throw new ConnectionLost(e);
		}
	}

	/**
	 * The body with the headers set so far (Content-Type, Cache-Control); gzipped if its Content-Type is text, it is
	 * big enough and the browser takes gzip. HEAD gets the headers only.
	 */
	public static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
		respond(exchange, status, body, null);
	}

	private static void respond(HttpExchange exchange, int status, byte[] body, Supplier<byte[]> gzipped) throws IOException {
		checkNotSent(exchange, status);
		Headers headers = exchange.getResponseHeaders();
		byte[] sent = body;
		if (isCompressible(headers.getFirst("Content-Type"))) {
			headers.set("Vary", "Accept-Encoding");
			if (body.length >= GZIP_MIN_BYTES && acceptsGzip(exchange.getRequestHeaders().getFirst("Accept-Encoding"))) {
				sent = gzipped != null ? gzipped.get() : gzip(body);
				headers.set("Content-Encoding", "gzip");
			}
		}
		boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
		try {
			if (head) {
				// The length the GET would have; the server itself sends no body for HEAD.
				headers.set("Content-Length", Integer.toString(sent.length));
				exchange.sendResponseHeaders(status, -1);
				exchange.getResponseBody().close();
				return;
			}
			exchange.sendResponseHeaders(status, sent.length == 0 ? -1 : sent.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(sent);
			}
		} catch (IOException e) {
			throw new ConnectionLost(e);
		}
	}

	public static String toJson(Object payload) {
		return GSON.toJson(payload);
	}

	/** {@code "<crc32>-<length>"} in hex, from the uncompressed body: the same for the gzipped and the plain answer. */
	public static String etag(byte[] body) {
		CRC32 crc = new CRC32();
		crc.update(body);
		return "\"" + Long.toHexString(crc.getValue()) + "-" + Integer.toHexString(body.length) + "\"";
	}

	/** Whether an If-None-Match header names this ETag: any of its tags, weak ones too, or {@code *}. */
	public static boolean matches(String ifNoneMatch, String etag) {
		if (ifNoneMatch == null || etag == null) {
			return false;
		}
		for (String tag : ifNoneMatch.split(",")) {
			String trimmed = tag.trim();
			if (trimmed.startsWith("W/")) {
				trimmed = trimmed.substring(2);
			}
			if (trimmed.equals("*") || trimmed.equals(etag)) {
				return true;
			}
		}
		return false;
	}

	/** Whether an Accept-Encoding header allows gzip: named (or {@code *}) without {@code q=0}. */
	static boolean acceptsGzip(String acceptEncoding) {
		if (acceptEncoding == null) {
			return false;
		}
		Boolean any = null;
		for (String part : acceptEncoding.split(",")) {
			String[] pieces = part.split(";");
			String coding = pieces[0].trim().toLowerCase(Locale.ROOT);
			boolean allowed = quality(pieces) > 0;
			if (coding.equals("gzip") || coding.equals("x-gzip")) {
				// Named itself, it decides, whatever "*" says.
				return allowed;
			}
			if (coding.equals("*")) {
				any = allowed;
			}
		}
		return any != null && any;
	}

	private static double quality(String[] pieces) {
		for (int i = 1; i < pieces.length; i++) {
			String parameter = pieces[i].trim().toLowerCase(Locale.ROOT);
			if (parameter.startsWith("q=")) {
				try {
					return Double.parseDouble(parameter.substring(2).trim());
				} catch (NumberFormatException e) {
					return 0;
				}
			}
		}
		return 1;
	}

	/** JSON and text; never images or fonts. */
	static boolean isCompressible(String contentType) {
		if (contentType == null) {
			return false;
		}
		int semicolon = contentType.indexOf(';');
		String type = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
		return COMPRESSIBLE.contains(type);
	}

	static byte[] gzip(byte[] body) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, body.length / 4));
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			gzip.write(body);
		} catch (IOException e) {
			// A stream in memory doesn't fail.
			throw new IllegalStateException(e);
		}
		return out.toByteArray();
	}

	/** A second answer to one request is a bug in the mod, not a lost connection. */
	private static void checkNotSent(HttpExchange exchange, int status) {
		int sent = exchange.getResponseCode();
		if (sent != -1) {
			throw new IllegalStateException("Answered " + sent + " already, now asked to answer " + status);
		}
	}
}
