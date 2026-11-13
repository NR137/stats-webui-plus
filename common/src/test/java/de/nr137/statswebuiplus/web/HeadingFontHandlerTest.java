package de.nr137.statswebuiplus.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.nr137.statswebuiplus.TestPlatform;
import de.nr137.statswebuiplus.WebConfig;

/** README, Custom font for the site heading: files inside config/, several formats, served in the listed order. */
class HeadingFontHandlerTest {
	@TempDir
	Path server;

	private TestPlatform platform;

	@BeforeEach
	void setUp() {
		platform = TestPlatform.install(server);
	}

	private WebConfig config(String font, String files) throws IOException {
		Files.writeString(WebConfig.path(), """
			{"siteHeadingFont": "%s", "siteHeadingCustomFont": {"name": "Title", "files": %s, "license": {"en": "By me"}}}
			""".formatted(font, files));
		return WebConfig.load();
	}

	private void fontFile(String relative, String content) throws IOException {
		Path file = platform.configDir().resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, String>> sources(WebConfig config) {
		return (List<Map<String, String>>) HeadingFontHandler.payload(config).get("sources");
	}

	@Test
	void listsTheFilesThatExistInOrderWithTheirFormat() throws IOException {
		fontFile("fonts/title.woff2", "w2");
		fontFile("fonts/title.ttf", "tt");
		WebConfig config = config("custom", "[\"fonts/title.woff2\", \"fonts/missing.woff\", \"fonts/title.ttf\"]");

		Map<String, Object> payload = HeadingFontHandler.payload(config);
		assertEquals("Title", payload.get("name"));
		assertEquals(Map.of("en", "By me"), payload.get("license"));
		List<Map<String, String>> sources = sources(config);
		assertEquals(List.of("woff2", "truetype"), sources.stream().map(source -> source.get("format")).toList());
		assertTrue(sources.get(0).get("url").startsWith(HeadingFontHandler.PATH + "0?v="), sources.toString());
		assertTrue(sources.get(1).get("url").startsWith(HeadingFontHandler.PATH + "2?v="), sources.toString());
	}

	@Test
	void notUsedWithAnotherFont() throws IOException {
		fontFile("fonts/title.woff2", "w2");
		WebConfig config = config("gothic", "[\"fonts/title.woff2\"]");

		assertNull(HeadingFontHandler.payload(config));
		assertNull(HeadingFontHandler.file(config, 0));
	}

	@Test
	void customWithoutUsableFilesFallsBackToBlocky() throws IOException {
		Files.writeString(server.resolve("outside.ttf"), "x");
		WebConfig config = config("custom", "[\"../outside.ttf\", \"fonts/notes.txt\"]");

		assertEquals("blocky", config.siteHeadingFont());
		assertTrue(config.siteHeadingCustomFont().files().isEmpty());
	}

	@Test
	void servesTheLicenseTextFileWhenSet() throws IOException {
		fontFile("fonts/title.woff2", "w2");
		fontFile("fonts/OFL.txt", "SIL OPEN FONT LICENSE");
		Files.writeString(WebConfig.path(), """
			{"siteHeadingFont": "custom", "siteHeadingCustomFont": {"files": ["fonts/title.woff2"], "licenseFile": "fonts/OFL.txt"}}
			""");
		WebConfig config = WebConfig.load();

		String url = (String) HeadingFontHandler.payload(config).get("licenseText");
		assertTrue(url.startsWith(HeadingFontHandler.PATH + "license?v="), url);
		try (TestServer http = new TestServer(exchange -> HeadingFontHandler.handle(exchange, config))) {
			TestServer.Response response = http.get(url);
			assertEquals(200, response.status());
			assertEquals("SIL OPEN FONT LICENSE", response.text());
		}
		assertNull(HeadingFontHandler.payload(config("custom", "[\"fonts/title.woff2\"]")).get("licenseText"));
	}

	@Test
	void aLicenseFileOutsideTheConfigFolderIsIgnored() throws IOException {
		fontFile("fonts/title.woff2", "w2");
		Files.writeString(server.resolve("OFL.txt"), "outside");
		Files.writeString(WebConfig.path(), """
			{"siteHeadingFont": "custom", "siteHeadingCustomFont": {"files": ["fonts/title.woff2"], "licenseFile": "../OFL.txt"}}
			""");
		WebConfig config = WebConfig.load();

		assertEquals("", config.siteHeadingCustomFont().licenseFile());
		assertNull(HeadingFontHandler.payload(config).get("licenseText"));
	}

	@Test
	void servesAFileAndNothingElse() throws IOException {
		fontFile("fonts/title.woff2", "woff2 bytes");
		WebConfig config = config("custom", "[\"fonts/title.woff2\"]");
		try (TestServer http = new TestServer(exchange -> HeadingFontHandler.handle(exchange, config))) {
			TestServer.Response response = http.get(sources(config).get(0).get("url"));
			assertEquals(200, response.status());
			assertEquals("font/woff2", response.header("Content-Type"));
			assertEquals("woff2 bytes", new String(response.body()));
			assertEquals(404, http.get(HeadingFontHandler.PATH + "1").status());
			assertEquals(404, http.get(HeadingFontHandler.PATH + "x").status());
		}
	}
}
