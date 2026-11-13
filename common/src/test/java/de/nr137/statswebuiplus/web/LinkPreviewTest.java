package de.nr137.statswebuiplus.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.nr137.statswebuiplus.TestPlatform;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.stats.PlayerRecord;

/**
 * README, Link previews: a shared link shows the site heading and slogan in the server's default language, a
 * player's name and level on their pages, and the header image large, else the favicon small.
 */
class LinkPreviewTest {
	private static final String ORIGIN = "https://stats.example.com";
	private static final PlayerRecord STEVE = new PlayerRecord(UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7"),
		"Steve", 12, Map.of(), Map.of(), Set.of(), Map.of());

	@TempDir
	Path server;

	@BeforeEach
	void setUp() {
		TestPlatform.install(server);
	}

	private WebConfig config(String json) throws IOException {
		Files.writeString(WebConfig.path(), json);
		return WebConfig.load();
	}

	private static String content(String tags, String name) {
		int at = tags.indexOf("=\"" + name + "\" content=\"");
		if (at < 0) {
			return null;
		}
		int start = at + name.length() + 13;
		return tags.substring(start, tags.indexOf('"', start));
	}

	@Test
	void headingAndSloganInTheDefaultLanguage() throws IOException {
		WebConfig config = config("""
			{"defaultLanguage": "de", "headerImage": "",
			 "siteHeading": {"en": "My Server", "de": "Mein Server"}, "siteHeadingSlogan": {"en": "The best."}}
			""");
		String tags = LinkPreview.tags(config, ORIGIN, "/leaderboards", List.of());
		assertEquals("website", content(tags, "og:type"));
		assertEquals("Mein Server", content(tags, "og:site_name"));
		assertEquals("Mein Server", content(tags, "og:title"));
		assertEquals("The best.", content(tags, "og:description"), "English when the default language has none");
		assertEquals(ORIGIN + "/leaderboards", content(tags, "og:url"));
	}

	@Test
	void theHeaderImageLargeElseTheFaviconSmall() throws IOException {
		Path image = server.resolve("config/stats_webui_plus/banner.png");
		Files.createDirectories(image.getParent());
		Files.write(image, TestServer.resource("/assets/stats_webui_plus/header.png"));
		String withImage = LinkPreview.tags(config("{\"headerImage\": \"stats_webui_plus/banner.png\"}"), ORIGIN, "/",
			List.of());
		assertTrue(content(withImage, "og:image").startsWith(ORIGIN + "/header-image?v="), withImage);
		assertEquals("summary_large_image", content(withImage, "twitter:card"));

		String without = LinkPreview.tags(config("{\"headerImage\": \"\"}"), ORIGIN, "/", List.of());
		assertEquals(ORIGIN + "/favicon", content(without, "og:image"));
		assertEquals("summary", content(without, "twitter:card"));
	}

	@Test
	void aPlayersPagesNameThePlayerAndTheirLevel() throws IOException {
		WebConfig config = config("{\"headerImage\": \"\"}");
		for (String path : List.of("/players/steve", "/players/Steve/advancements", "/players/" + STEVE.uuid())) {
			String tags = LinkPreview.tags(config, ORIGIN, path, List.of(STEVE));
			assertEquals("Steve - My Server", content(tags, "og:title"), path);
			assertEquals("Level 0 · The best server in the world.", content(tags, "og:description"), path);
		}
		// Nobody of that name: the site's own preview, no made-up name.
		assertEquals("My Server", content(LinkPreview.tags(config, ORIGIN, "/players/Herobrine", List.of(STEVE)),
			"og:title"));
	}

	@Test
	void withoutAHeadingTheWindowTitleNamesTheSite() throws IOException {
		WebConfig config = config("{\"siteHeading\": \"\", \"windowTitle\": \"{page} | Block Party!\"}");
		assertEquals("Block Party!", content(LinkPreview.tags(config, ORIGIN, "/", List.of()), "og:title"));
	}

	@Test
	void textsAreEscaped() throws IOException {
		WebConfig config = config("{\"siteHeading\": \"<b>\\\"Tom & Jerry\\\"</b>\"}");
		String tags = LinkPreview.tags(config, ORIGIN, "/", List.of());
		assertEquals("&lt;b&gt;&quot;Tom &amp; Jerry&quot;&lt;/b&gt;", content(tags, "og:title"));
		assertFalse(tags.contains("<b>"), tags);
	}

	@Test
	void withoutAnAddressNoTagsThatNeedOne() throws IOException {
		String tags = LinkPreview.tags(config("{}"), null, "/", List.of());
		assertNotNull(content(tags, "og:title"));
		assertNull(content(tags, "og:url"));
		assertNull(content(tags, "og:image"));
	}

	@Test
	void siteUrlMustBeJustAnAddress() throws IOException {
		assertEquals("https://stats.example.com", config("{\"siteUrl\": \"https://stats.example.com/\"}").siteUrl());
		assertEquals("", config("{\"siteUrl\": \"stats.example.com\"}").siteUrl());
		assertEquals("", config("{\"siteUrl\": \"https://stats.example.com/stats\"}").siteUrl());
		assertEquals("", config("{\"siteUrl\": \"javascript:alert(1)\"}").siteUrl());
	}
}
