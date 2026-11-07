package webui.stats.mc.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import webui.stats.mc.TestPlatform;

/**
 * CHANGELOG 1.4.0: the favicon is at /favicon, the Minecraft server's icon (server-icon.png) if it has one, else the
 * web UI's own; browsers check it on every visit and get an empty 304 while it is unchanged.
 */
class FaviconHandlerTest {
	@TempDir
	Path server;

	private TestPlatform platform;
	private TestServer http;

	@BeforeEach
	void setUp() throws IOException {
		platform = TestPlatform.install(server);
		http = new TestServer(exchange -> {
			try {
				FaviconHandler.handle(exchange);
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
		assertEquals("image/svg+xml", response.header("Content-Type"));
		assertTrue(response.text().contains("<svg"));
	}

	@Test
	void serverIconWhenThereIsOne() throws IOException {
		byte[] icon = TestServer.resource("/assets/stats_webui_plus/icon.png");
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

		// A server icon added later is a new version.
		Files.write(platform.gameDir().resolve("server-icon.png"), TestServer.resource("/assets/stats_webui_plus/icon.png"));
		TestServer.Response changed = http.get("/favicon", "If-None-Match", etag);
		assertEquals(200, changed.status());
		assertEquals("image/png", changed.header("Content-Type"));
	}
}
