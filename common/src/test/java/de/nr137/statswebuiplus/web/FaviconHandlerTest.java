package de.nr137.statswebuiplus.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.nr137.statswebuiplus.TestPlatform;
import de.nr137.statswebuiplus.WebConfig;

/**
 * README, Configuration: the favicon is at /favicon, the file of the config's favicon setting, else the Minecraft
 * server's icon (server-icon.png) if it has one, else the web UI's own; browsers check it on every visit and get an
 * empty 304 while it is unchanged.
 */
class FaviconHandlerTest {
	@TempDir
	Path server;

	private TestPlatform platform;
	private TestServer http;
	private WebConfig config = WebConfig.defaults();

	@BeforeEach
	void setUp() throws IOException {
		platform = TestPlatform.install(server);
		http = new TestServer(exchange -> {
			try {
				FaviconHandler.handle(exchange, config);
			} finally {
				exchange.close();
			}
		});
	}

	@AfterEach
	void tearDown() {
		http.close();
	}

	@Test
	void ownIconWithoutServerIcon() throws IOException {
		TestServer.Response response = http.get("/favicon");
		assertEquals(200, response.status());
		assertEquals("image/png", response.header("Content-Type"));
		assertArrayEquals(TestServer.resource("/assets/stats_webui_plus/icon.png"), response.body());
	}

	@Test
	void serverIconWhenThereIsOne() throws IOException {
		// Any PNG other than the mod icon, so the test sees which one is served.
		byte[] icon = TestServer.resource("/assets/stats_webui_plus/header.png");
		Files.write(platform.gameDir().resolve("server-icon.png"), icon);
		TestServer.Response response = http.get("/favicon");
		assertEquals(200, response.status());
		assertEquals("image/png", response.header("Content-Type"));
		assertArrayEquals(icon, response.body());
	}

	@Test
	void unchangedIconIsNotSentAgain() throws IOException {
		String etag = http.get("/favicon").header("ETag");
		assertNotNull(etag);
		assertEquals("no-cache", http.get("/favicon").header("Cache-Control"));
		assertEquals(304, http.get("/favicon", "If-None-Match", etag).status());

		// A server icon added later (any PNG but the mod icon) is a new version.
		Files.write(platform.gameDir().resolve("server-icon.png"), TestServer.resource("/assets/stats_webui_plus/header.png"));
		TestServer.Response changed = http.get("/favicon", "If-None-Match", etag);
		assertEquals(200, changed.status());
		assertEquals("image/png", changed.header("Content-Type"));
	}

	@Test
	void theConfiguredFaviconComesFirst() throws IOException {
		Files.write(platform.gameDir().resolve("server-icon.png"), TestServer.resource("/assets/stats_webui_plus/header.png"));
		Path icon = platform.configDir().resolve("stats_webui_plus/icon.svg");
		Files.createDirectories(icon.getParent());
		Files.writeString(icon, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
		config = config("stats_webui_plus/icon.svg");
		TestServer.Response response = http.get("/favicon");
		assertEquals(200, response.status());
		assertEquals("image/svg+xml", response.header("Content-Type"));
		assertEquals("<svg xmlns=\"http://www.w3.org/2000/svg\"/>", response.text());
	}

	@Test
	void aFaviconOutsideTheConfigFolderOrOfAnotherTypeIsIgnored() throws IOException {
		assertEquals("", config("../server-icon.png").favicon());
		assertEquals("", config("stats_webui_plus/icon.bmp").favicon());
		// Set but missing: the next in line.
		config = config("stats_webui_plus/missing.png");
		assertArrayEquals(TestServer.resource("/assets/stats_webui_plus/icon.png"), http.get("/favicon").body());
	}

	private static WebConfig config(String favicon) throws IOException {
		Files.writeString(WebConfig.path(), "{\"favicon\": \"" + favicon + "\"}");
		return WebConfig.load();
	}
}
