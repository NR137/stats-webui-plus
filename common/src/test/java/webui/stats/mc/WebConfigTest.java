package webui.stats.mc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The settings table and rules in the README's Configuration section. */
class WebConfigTest {
	@TempDir
	Path server;

	private TestPlatform platform;

	@BeforeEach
	void setUp() {
		platform = TestPlatform.install(server);
	}

	private WebConfig loadWith(String json) throws IOException {
		Files.writeString(WebConfig.path(), json);
		return WebConfig.load();
	}

	@Test
	void livesInConfigFolder() {
		assertEquals(platform.configDir().resolve("stats_webui_plus.json"), WebConfig.path());
	}

	@Test
	void firstStartCreatesFileWithDefaults() {
		WebConfig config = WebConfig.load();

		assertTrue(Files.isRegularFile(WebConfig.path()));
		assertEquals(25580, config.port());
		assertEquals("0.0.0.0", config.bind());
		assertEquals("", config.world());
		assertEquals(15000, config.refreshCooldownMs());
		assertEquals(0, config.saveIntervalSeconds());
		assertEquals(0, config.webThreads());
		assertEquals("en", config.defaultLanguage());
		assertEquals(List.of("de", "en", "es", "fr"), config.languages());
		assertEquals("stats_webui_plus/header.png", config.headerImage());
		assertEquals("top", config.headerImagePosition());
		assertTrue(config.siteHeadingTaglinePulse());
		assertTrue(config.statusBar());
		assertEquals("", config.serverAddress());
		assertEquals("walk", config.avatarPose());
		assertEquals(0, config.avatarAngle());
		assertTrue(config.avatarRotate());
		assertEquals(3, config.links().size());
	}

	@Test
	void createdFileLoadsBackTheSame() throws IOException {
		WebConfig first = WebConfig.load();
		WebConfig second = WebConfig.load();

		assertEquals(first.brandingPayload(), second.brandingPayload());
		assertEquals(first.port(), second.port());
		assertEquals(first.refreshCooldownMs(), second.refreshCooldownMs());
	}

	@Test
	void missingSettingsGetDefaults() throws IOException {
		WebConfig config = loadWith("{\"port\": 25999}");

		assertEquals(25999, config.port());
		assertEquals("0.0.0.0", config.bind());
		assertEquals(15000, config.refreshCooldownMs());
		assertEquals("walk", config.avatarPose());
		assertEquals(3, config.links().size());
	}

	@Test
	void readsEverySetting() throws IOException {
		WebConfig config = loadWith("""
			{
			  "port": 25600,
			  "bind": "127.0.0.1",
			  "world": "other_world",
			  "refreshCooldownMs": 0,
			  "saveIntervalSeconds": 300,
			  "defaultLanguage": "de",
			  "languages": ["de", "en"],
			  "headerImage": "",
			  "headerImagePosition": "bottom",
			  "siteHeading": {"en": "My Place", "de": "Mein Ort"},
			  "siteHeadingTagline": "",
			  "siteHeadingTaglinePulse": false,
			  "siteHeadingSlogan": "Hi",
			  "statusBar": false,
			  "serverAddress": "mc.example.com",
			  "avatarPose": "fly",
			  "avatarAngle": 90,
			  "avatarRotate": false,
			  "links": []
			}
			""");

		assertEquals(25600, config.port());
		assertEquals("127.0.0.1", config.bind());
		assertEquals("other_world", config.world());
		assertEquals(0, config.refreshCooldownMs());
		assertEquals(300, config.saveIntervalSeconds());
		assertEquals("de", config.defaultLanguage());
		assertEquals(List.of("de", "en"), config.languages());
		assertEquals("", config.headerImage());
		assertEquals("bottom", config.headerImagePosition());
		assertEquals(Map.of("en", "My Place", "de", "Mein Ort"), config.siteHeading());
		assertTrue(LocalizedText.isEmpty(config.siteHeadingTagline()));
		assertFalse(config.siteHeadingTaglinePulse());
		assertEquals("Hi", config.siteHeadingSlogan());
		assertFalse(config.statusBar());
		assertEquals("mc.example.com", config.serverAddress());
		assertEquals("fly", config.avatarPose());
		assertEquals(90, config.avatarAngle());
		assertFalse(config.avatarRotate());
	}

	@Test
	void emptyLinksRemovePlaceholdersForGood() throws IOException {
		assertTrue(loadWith("{\"links\": []}").links().isEmpty());
		// Loading again must not bring them back.
		assertTrue(WebConfig.load().links().isEmpty());
	}

	@Test
	void defaultLinkPlaceholdersAreHidden() {
		// "a button only appears once its url is filled in"
		WebConfig config = WebConfig.load();
		assertTrue(config.links().stream().noneMatch(MenuLink::shown));
		assertTrue(config.linksPayload().isEmpty());
	}

	@Test
	void linksWithForbiddenUrlsAreIgnored() throws IOException {
		WebConfig config = loadWith("""
			{"links": [
			  {"label": "Map", "url": "https://map.example.com"},
			  {"label": "Local", "url": "/map"},
			  {"label": "Plain", "url": "http://example.com"},
			  {"label": "Bad", "url": "javascript:alert(1)"},
			  {"label": "Ftp", "url": "ftp://example.com"},
			  {"label": "Data", "url": "data:text/html,hi"}
			]}
			""");

		List<String> shown = config.links().stream().filter(MenuLink::shown).map(MenuLink::url).toList();
		assertEquals(List.of("https://map.example.com", "/map", "http://example.com"), shown);
		assertEquals(3, config.linksPayload().size());
	}

	@Test
	void linksOpenInNewTabByDefault() throws IOException {
		WebConfig config = loadWith("{\"links\": [{\"label\": \"Map\", \"url\": \"/map\"}]}");
		assertTrue(config.links().getFirst().newTab());
	}

	@Test
	void refreshCooldownStaysInRange() throws IOException {
		int high = loadWith("{\"refreshCooldownMs\": 999999}").refreshCooldownMs();
		int low = loadWith("{\"refreshCooldownMs\": -5}").refreshCooldownMs();

		assertTrue(high >= 0 && high <= 60000, "refreshCooldownMs " + high);
		assertTrue(low >= 0 && low <= 60000, "refreshCooldownMs " + low);
	}

	/** README: webThreads 0 = half the CPUs, at least 1 and at most 4; 1–16 is taken as it is. */
	@Test
	void webThreadsAreAutomaticOrAsSet() throws IOException {
		assertEquals(1, WebConfig.automaticWebThreads(1));
		assertEquals(1, WebConfig.automaticWebThreads(2));
		assertEquals(1, WebConfig.automaticWebThreads(3));
		assertEquals(2, WebConfig.automaticWebThreads(4));
		assertEquals(3, WebConfig.automaticWebThreads(6));
		assertEquals(4, WebConfig.automaticWebThreads(8));
		assertEquals(4, WebConfig.automaticWebThreads(64));

		WebConfig automatic = loadWith("{}");
		assertEquals(WebConfig.automaticWebThreads(Runtime.getRuntime().availableProcessors()), automatic.webThreadCount());
		WebConfig set = loadWith("{\"webThreads\": 6}");
		assertEquals(6, set.webThreads());
		assertEquals(6, set.webThreadCount());
		assertEquals(16, loadWith("{\"webThreads\": 16}").webThreadCount());
	}

	@Test
	void webThreadsStayInRange() throws IOException {
		assertEquals(0, loadWith("{\"webThreads\": 17}").webThreads());
		assertEquals(0, loadWith("{\"webThreads\": -1}").webThreads());
	}

	@Test
	void saveIntervalStaysInRange() throws IOException {
		int high = loadWith("{\"saveIntervalSeconds\": 99999}").saveIntervalSeconds();
		int low = loadWith("{\"saveIntervalSeconds\": -1}").saveIntervalSeconds();

		assertTrue(high >= 0 && high <= 3600, "saveIntervalSeconds " + high);
		assertTrue(low >= 0 && low <= 3600, "saveIntervalSeconds " + low);
	}

	@Test
	void unknownChoicesFallBackToAllowedValues() throws IOException {
		WebConfig config = loadWith("""
			{"avatarPose": "dance", "headerImagePosition": "left", "defaultLanguage": "xx"}
			""");

		assertTrue(WebConfig.AVATAR_POSES.contains(config.avatarPose()), config.avatarPose());
		assertTrue(WebConfig.HEADER_IMAGE_POSITIONS.contains(config.headerImagePosition()), config.headerImagePosition());
		assertTrue(WebConfig.LANGUAGES.contains(config.defaultLanguage()), config.defaultLanguage());
	}

	@Test
	void onlySupportedLanguagesAreOffered() throws IOException {
		List<String> languages = loadWith("{\"languages\": [\"en\", \"xx\", \"de\"]}").languages();

		assertTrue(WebConfig.LANGUAGES.containsAll(languages), languages.toString());
		assertTrue(languages.containsAll(List.of("en", "de")), languages.toString());
	}

	@Test
	void windowTitleAlwaysHasPagePlaceholder() throws IOException {
		assertEquals("{page} | Test", loadWith("{\"windowTitle\": \"{page} | Test\"}").windowTitle());

		Object withoutPlaceholder = loadWith("{\"windowTitle\": \"No placeholder\"}").windowTitle();
		assertAllContainPage(withoutPlaceholder);

		Object perLanguage = loadWith("{\"windowTitle\": {\"en\": \"{page} - EN\", \"de\": \"Kein Platzhalter\"}}").windowTitle();
		assertAllContainPage(perLanguage);
	}

	/** A text per language without the placeholder is blanked, so the web UI uses its fallback for that language. */
	private static void assertAllContainPage(Object title) {
		if (title instanceof Map<?, ?> map) {
			assertTrue(map.values().stream().anyMatch(value -> value.toString().contains("{page}")), "windowTitle " + title);
			map.values().forEach(value -> assertTrue(
				value.toString().isEmpty() || value.toString().contains("{page}"), "windowTitle " + title));
		} else {
			assertTrue(title.toString().contains("{page}"), "windowTitle " + title);
		}
	}

	@Test
	void reloadReadsChangedFile() throws IOException {
		loadWith("{\"port\": 25601}");
		Files.writeString(WebConfig.path(), "{\"port\": 25602, \"avatarPose\": \"run\"}");

		WebConfig reloaded = WebConfig.reload();
		assertEquals(25602, reloaded.port());
		assertEquals("run", reloaded.avatarPose());
	}

	@Test
	void unreadableFileOnReloadKeepsPreviousSettings() throws IOException {
		loadWith("{\"port\": 25601}");
		String broken = "{ this is not json";
		Files.writeString(WebConfig.path(), broken);

		// null tells the server to keep the settings it has.
		assertNull(WebConfig.reload());
		// A half edited file is left alone.
		assertEquals(broken, Files.readString(WebConfig.path()));
	}

	@Test
	void unreadableFileAtStartIsNotOverwritten() throws IOException {
		String broken = "{ \"port\": 25601, oops";
		WebConfig config = loadWith(broken);

		assertEquals(25580, config.port());
		assertEquals(broken, Files.readString(WebConfig.path()));
	}

	@Test
	void oldMcStatsWebuiConfigIsNotRead() throws IOException {
		Files.writeString(platform.configDir().resolve("mc-stats-webui.json"), "{\"port\": 31337}");

		assertEquals(25580, WebConfig.load().port());
	}

	@Test
	void brandingPayloadReflectsSettings() throws IOException {
		Map<String, Object> payload = loadWith("{\"serverAddress\": \"mc.example.com\", \"statusBar\": false}").brandingPayload();

		assertTrue(payload.containsValue("mc.example.com"), payload.toString());
		assertTrue(payload.containsValue(false), payload.toString());
	}
}
