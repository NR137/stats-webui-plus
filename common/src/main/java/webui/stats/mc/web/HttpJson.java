package webui.stats.mc.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;

public final class HttpJson {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private HttpJson() {
	}

	public static void send(HttpExchange exchange, int status, Object body) throws IOException {
		send(exchange, status, body, "no-store");
	}

	public static void send(HttpExchange exchange, int status, Object body, String cacheControl) throws IOException {
		byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", cacheControl);
		respond(exchange, status, bytes);
	}

	/**
	 * Sends the status and, except for HEAD requests, the body; every handler answers through here. A connection that
	 * breaks meanwhile becomes {@link ConnectionLost}.
	 */
	public static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
		// A second response to one request is a bug of the mod, not a visitor who went away.
		if (exchange.getResponseCode() != -1) {
			throw new IllegalStateException("Response " + exchange.getResponseCode() + " was already sent, not sending " + status);
		}
		boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
		try {
			exchange.sendResponseHeaders(status, head || body.length == 0 ? -1 : body.length);
			if (!head && body.length > 0) {
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			} else {
				exchange.close();
			}
		} catch (IOException e) {
			throw new ConnectionLost(e);
		}
	}

	/**
	 * Like {@link #send}, with an ETag of the body: a browser that has this answer already gets an empty 304 instead.
	 * For answers that are cheap to make but may be big, so revalidating saves the transfer (custom pages).
	 */
	public static void sendRevalidated(HttpExchange exchange, Object body, String cacheControl) throws IOException {
		byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		String etag = etag(bytes);
		exchange.getResponseHeaders().set("ETag", etag);
		exchange.getResponseHeaders().set("Cache-Control", cacheControl);
		if (matches(exchange.getRequestHeaders().getFirst("If-None-Match"), etag)) {
			empty(exchange, 304);
			return;
		}
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		respond(exchange, 200, bytes);
	}

	static String etag(byte[] bytes) {
		CRC32 crc = new CRC32();
		crc.update(bytes);
		return "\"" + Long.toHexString(crc.getValue()) + "-" + Integer.toHexString(bytes.length) + "\"";
	}

	/** If-None-Match may list several tags, and proxies that compress the file mark them weak (W/"..."). */
	static boolean matches(String ifNoneMatch, String etag) {
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

	/** A response without a body (304, 404, 405, ...). */
	public static void empty(HttpExchange exchange, int status) throws IOException {
		respond(exchange, status, new byte[0]);
	}

	/**
	 * The visitor's connection broke while the response was being sent: the tab was closed, or the web server stopped
	 * (a singleplayer world closing, a new port). Nothing the mod did wrong, so it isn't logged as an error.
	 */
	public static final class ConnectionLost extends IOException {
		ConnectionLost(IOException cause) {
			super(cause.getMessage(), cause);
		}
	}

	public static void error(HttpExchange exchange, int status, String code, String message) throws IOException {
		send(exchange, status, Map.of("error", code, "message", message));
	}
}
