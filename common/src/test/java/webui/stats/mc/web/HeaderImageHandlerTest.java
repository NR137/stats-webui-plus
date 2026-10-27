package webui.stats.mc.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import webui.stats.mc.TestPlatform;
import webui.stats.mc.WebConfig;

/** README, Header image: a file inside config/, "" turns it off, a missing file shows no header. */
class HeaderImageHandlerTest {
	@TempDir
	Path server;

	private TestPlatform platform;

	@BeforeEach
	void setUp() {
		platform = TestPlatform.install(server);
	}

	private WebConfig config(String headerImage) throws IOException {
		Files.writeString(WebConfig.path(), "{\"headerImage\": \"" + headerImage + "\"}");
		return WebConfig.load();
	}

	private Path image(String relative) throws IOException {
		Path file = platform.configDir().resolve(relative);
		Files.createDirectories(file.getParent());
		Files.write(file, TestServer.resource("/assets/stats_webui_plus/web/mc/item/diamond.png"));
		return file;
	}

	@Test
	void imageInsideConfigIsUsed() throws IOException {
		Path file = image("my_banner/banner.png");
		WebConfig config = config("my_banner/banner.png");

		assertEquals(file.toAbsolutePath().normalize(), HeaderImageHandler.file(config).toAbsolutePath().normalize());
		assertNotNull(HeaderImageHandler.url(config));
		assertTrue(HeaderImageHandler.url(config).startsWith(HeaderImageHandler.PATH), HeaderImageHandler.url(config));
	}

	@Test
	void emptyTurnsItOff() throws IOException {
		image("stats_webui_plus/header.png");
		assertTrue(isOff(config("")));
	}

	@Test
	void missingFileShowsNoHeader() throws IOException {
		assertTrue(isOff(config("nothing/here.png")));
	}

	@Test
	void pathsOutsideConfigAreRefused() throws IOException {
		Files.write(server.resolve("outside.png"), TestServer.resource("/assets/stats_webui_plus/web/mc/item/diamond.png"));
		assertTrue(isOff(config("../outside.png")));
		assertTrue(isOff(config(server.resolve("outside.png").toAbsolutePath().toString().replace("\\", "/"))));
	}

	@Test
	void unsupportedFormatIsRefused() throws IOException {
		Files.writeString(platform.configDir().resolve("notes.txt"), "not an image");
		assertTrue(isOff(config("notes.txt")));
	}

	@Test
	void servesTheImage() throws IOException {
		image("stats_webui_plus/header.png");
		WebConfig config = config("stats_webui_plus/header.png");
		try (TestServer http = new TestServer(exchange -> HeaderImageHandler.handle(exchange, config))) {
			TestServer.Response response = http.get(HeaderImageHandler.url(config));
			assertEquals(200, response.status());
			assertEquals("image/png", response.header("Content-Type"));
			assertArrayEquals(Files.readAllBytes(HeaderImageHandler.file(config)), response.body());
		}
	}

	@Test
	void noImageIsNotFound() throws IOException {
		WebConfig config = config("");
		try (TestServer http = new TestServer(exchange -> HeaderImageHandler.handle(exchange, config))) {
			assertEquals(404, http.get(HeaderImageHandler.PATH).status());
		}
	}

	private static boolean isOff(WebConfig config) {
		String url = HeaderImageHandler.url(config);
		Path file = HeaderImageHandler.file(config);
		return (url == null || url.isEmpty()) && (file == null || !Files.isRegularFile(file));
	}
}
