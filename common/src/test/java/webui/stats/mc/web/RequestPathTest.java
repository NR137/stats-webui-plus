package webui.stats.mc.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The API reads names and IDs straight from {@code getRequestURI().getPath()}: the web UI encodes each part once with
 * encodeURIComponent, and the path comes back decoded once, also for Floodgate prefixes like "+" or "%".
 */
class RequestPathTest {
	private static TestServer server;

	@BeforeAll
	static void start() throws IOException {
		server = new TestServer(exchange -> {
			byte[] path = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, path.length);
			exchange.getResponseBody().write(path);
			exchange.close();
		});
	}

	@AfterAll
	static void stop() {
		server.close();
	}

	// Left: what encodeURIComponent sends. Right: the name or ID the API must look up.
	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource(delimiter = '|', value = {
		"Steve               | Steve",
		".Steve              | .Steve",
		"%2BSteve            | +Steve",
		"%25Steve            | %Steve",
		"*Steve              | *Steve",
		"Cool_Guy            | Cool_Guy",
		"Cool%20Guy          | Cool Guy",
		"mined/minecraft%3Astone | mined/minecraft:stone",
		"minecraft/story/mine_stone | minecraft/story/mine_stone",
	})
	void decodedOnce(String sent, String expected) throws IOException {
		assertEquals("/api/players/" + expected, server.get("/api/players/" + sent).text());
	}
}
