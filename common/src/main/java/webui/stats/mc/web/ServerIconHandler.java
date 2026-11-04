package webui.stats.mc.web;

import com.sun.net.httpserver.HttpExchange;
import webui.stats.mc.McStatsWebui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerIconHandler {
	public void handle(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			HttpJson.empty(exchange, 405);
			return;
		}

		Path icon = McStatsWebui.platform().gameDir().resolve("server-icon.png");
		if (!Files.isRegularFile(icon)) {
			HttpJson.empty(exchange, 404);
			return;
		}

		byte[] bytes = Files.readAllBytes(icon);
		exchange.getResponseHeaders().set("Content-Type", "image/png");
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		HttpJson.respond(exchange, 200, bytes);
	}
}
