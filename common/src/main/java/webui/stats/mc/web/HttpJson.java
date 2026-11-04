package webui.stats.mc.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
