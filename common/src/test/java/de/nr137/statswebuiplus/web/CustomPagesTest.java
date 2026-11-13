package de.nr137.statswebuiplus.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.nr137.statswebuiplus.CustomPage;
import de.nr137.statswebuiplus.TestPlatform;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.api.Status;

/**
 * README, Custom pages: each page's text is a file per language in config/stats_webui_plus/pages/&lt;id&gt;/ (.md, .html
 * or .txt), page.&lt;ext&gt; for every language; visitors get their language, else the server's default language, else
 * English, else page.&lt;ext&gt;, else any file. Pages show up only once they have a file and are enabled; the samples
 * (imprint, privacy policy, rules) come switched off, with their files written on first start.
 */
class CustomPagesTest {
	// Two pages of one's own, enabled by default.
	private static final String OWN_PAGES = """
		{"pages": [
		  {"id": "imprint", "label": {"en": "Imprint", "de": "Impressum"}},
		  {"id": "rules", "label": "Rules", "place": "menu"}
		]}
		""";

	@TempDir
	Path server;

	private TestPlatform platform;

	@BeforeEach
	void setUp() {
		platform = TestPlatform.install(server);
	}

	private WebConfig config(String json) throws IOException {
		Files.writeString(WebConfig.path(), json);
		return WebConfig.load();
	}

	private Path pages() {
		return platform.configDir().resolve("stats_webui_plus/pages");
	}

	private void file(String relative, String text) throws IOException {
		Path file = pages().resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
	}

	private static List<Object> ids(WebConfig config) {
		return CustomPages.payload(config).stream().map(page -> page.get("id")).toList();
	}

	/** README, Start page: the page must be an enabled entry of pages with a text; until then the ranking stays. */
	@Test
	void theStartPageOnlyOnceItHasAText() throws IOException {
		WebConfig config = config("""
			{"startPage": "home", "pages": [{"id": "home", "label": "Home", "place": "main"}]}
			""");
		assertEquals("home", config.startPage(), "an enabled entry of pages");
		Files.deleteIfExists(pages().resolve("home/en.md"));
		assertNull(Status.status(config, List.of(), 0, 20).get("startPage"), "no text yet: the ranking stays");
		file("home/en.md", "# Welcome");
		assertEquals("home", Status.status(config, List.of(), 0, 20).get("startPage"));
	}

	@Test
	void firstStartWritesTheSamplesSwitchedOff() throws IOException {
		WebConfig config = WebConfig.load();
		assertEquals(List.of("home", "imprint", "privacy", "rules"), config.pages().stream().map(CustomPage::id).toList());
		assertTrue(config.pages().stream().noneMatch(CustomPage::enabled));
		assertEquals(List.of("main", "footer", "footer", "menu"), config.pages().stream().map(CustomPage::place).toList());
		for (String id : List.of("home", "imprint", "privacy", "rules")) {
			for (String language : List.of("de", "en", "es", "fr")) {
				Path sample = pages().resolve(id + "/" + language + ".md");
				assertTrue(Files.isRegularFile(sample), sample.toString());
				assertTrue(Files.readString(sample).contains("\"enabled\": true"), "the note says how to switch it on: " + sample);
			}
		}
		// Switched off: no link, no page, although the files are there.
		assertEquals(List.of(), ids(config));
		assertNull(CustomPages.find(config, "imprint", "en"));
		assertTrue(Files.readString(WebConfig.path()).contains("\"enabled\": false"));
	}

	@Test
	void enablingASampleShowsIt() throws IOException {
		WebConfig.load();
		String json = Files.readString(WebConfig.path()).replaceFirst("\"enabled\": false", "\"enabled\": true");
		WebConfig config = config(json);
		assertEquals(List.of("home"), ids(config));
		CustomPages.Content home = CustomPages.find(config, "home", "de");
		assertEquals("de", home.language());
		assertTrue(home.text().startsWith("# Willkommen"));
	}

	@Test
	void samplesAreNotWrittenAgain() throws IOException {
		WebConfig.load();
		Path rules = pages().resolve("rules/en.md");
		Files.delete(rules);
		Path imprint = pages().resolve("imprint/en.md");
		Files.writeString(imprint, "# My imprint");
		WebConfig.load();
		assertFalse(Files.exists(rules), "a deleted sample stays deleted");
		assertEquals("# My imprint", Files.readString(imprint));
	}

	@Test
	void pagesShowUpOnlyWithAFile() throws IOException {
		WebConfig config = config(OWN_PAGES);
		assertEquals(List.of(), ids(config));
		assertNull(CustomPages.find(config, "imprint", "en"));

		file("imprint/de.md", "# Impressum");
		assertEquals(List.of("imprint"), ids(config));
		Map<String, Object> imprint = CustomPages.payload(config).getFirst();
		assertEquals("footer", imprint.get("place"));
		assertEquals(Map.of("en", "Imprint", "de", "Impressum"), imprint.get("label"));
	}

	@Test
	void disabledPagesAreHidden() throws IOException {
		WebConfig config = config("{\"pages\": [{\"id\": \"rules\", \"label\": \"Rules\", \"enabled\": false}]}");
		file("rules/en.md", "1. Be nice");
		assertEquals(List.of(), ids(config));
		assertNull(CustomPages.find(config, "rules", "en"));
	}

	@Test
	void visitorLanguageFirst() throws IOException {
		WebConfig config = config(OWN_PAGES);
		file("imprint/en.md", "Hello");
		file("imprint/de.md", "Hallo");
		CustomPages.Content content = CustomPages.find(config, "imprint", "de");
		assertEquals("de", content.language());
		assertEquals("markdown", content.format());
		assertEquals("Hallo", content.text());
	}

	@Test
	void thenDefaultLanguageThenEnglishThenPageFileThenAny() throws IOException {
		file("rules/it.txt", "Regole");
		file("rules/page.html", "<p>Rules</p>");
		file("rules/en.md", "Rules");
		file("rules/fr.md", "Règles");
		WebConfig config = config("{\"defaultLanguage\": \"fr\", \"pages\": [{\"id\": \"rules\", \"label\": \"Rules\"}]}");

		assertEquals("fr", CustomPages.find(config, "rules", "es").language());
		Files.delete(pages().resolve("rules/fr.md"));
		assertEquals("en", CustomPages.find(config, "rules", "es").language());
		Files.delete(pages().resolve("rules/en.md"));
		CustomPages.Content neutral = CustomPages.find(config, "rules", "es");
		assertNull(neutral.language());
		assertEquals("html", neutral.format());
		Files.delete(pages().resolve("rules/page.html"));
		CustomPages.Content any = CustomPages.find(config, "rules", "es");
		assertEquals("it", any.language());
		assertEquals("text", any.format());
	}

	@Test
	void markdownWinsOverHtmlOverText() throws IOException {
		WebConfig config = config(OWN_PAGES);
		file("imprint/de.txt", "Text");
		file("imprint/de.html", "<p>HTML</p>");
		assertEquals("html", CustomPages.find(config, "imprint", "de").format());
		file("imprint/de.md", "Markdown");
		assertEquals("markdown", CustomPages.find(config, "imprint", "de").format());
	}

	@Test
	void visitorLanguagesInTheirOrder() throws IOException {
		WebConfig config = config(OWN_PAGES);
		file("imprint/en.md", "Hello");
		file("imprint/de.md", "Hallo");
		// French first, but there is no French file: the visitor's next language, not English.
		assertEquals("de", CustomPages.find(config, "imprint", "fr,de,en").language());
		assertEquals("en", CustomPages.find(config, "imprint", "fr,en,de").language());
	}

	@Test
	void languagesTheUiDoesNotOfferCountToo() throws IOException {
		WebConfig config = config(OWN_PAGES);
		file("imprint/en.md", "Hello");
		file("imprint/it.md", "Ciao");
		assertEquals("it", CustomPages.find(config, "imprint", "it,de").language());
	}

	@Test
	void anythingButLanguageCodesIsIgnored() throws IOException {
		WebConfig config = config(OWN_PAGES);
		file("imprint/en.md", "Hello");
		file("imprint/page.md", "Every language");
		assertEquals("en", CustomPages.find(config, "imprint", "../page, PAGE,page,x").language());
		assertEquals("en", CustomPages.find(config, "imprint", null).language());
	}

	@Test
	void onlyMarkdownHtmlAndTextFiles() throws IOException {
		WebConfig config = config(OWN_PAGES);
		file("imprint/en.pdf", "%PDF");
		file("imprint/notes.docx", "x");
		assertEquals(List.of(), ids(config));
		file("imprint/en.markdown", "# Imprint");
		assertEquals("markdown", CustomPages.find(config, "imprint", "en").format());
		file("rules/en.htm", "<p>Rules</p>");
		assertEquals("html", CustomPages.find(config, "rules", "en").format());
	}

	@Test
	void pagesNotInTheConfigAreNotServed() throws IOException {
		WebConfig config = config(OWN_PAGES);
		file("secret/en.md", "Not listed");
		assertNull(CustomPages.find(config, "secret", "en"));
		assertNull(CustomPages.find(config, "../secret", "en"));
		assertNull(CustomPages.find(config, "", "en"));
	}

	@Test
	void ownPagesInTheMenuAndFooter() throws IOException {
		WebConfig config = config("""
			{"pages": [
			  {"id": "rules", "label": {"en": "Rules", "de": "Regeln"}, "place": "menu"},
			  {"id": "team", "label": "Team"},
			  {"id": "Bad Id", "label": "Bad"},
			  {"id": "nolabel"},
			  {"id": "rules", "label": "Twice"},
			  {"id": "odd", "label": "Odd", "place": "sidebar"},
			  "not an object"
			]}
			""");
		assertEquals(List.of("rules", "team", "odd"), config.pages().stream().map(CustomPage::id).toList());
		assertEquals("menu", config.pages().get(0).place());
		assertEquals("footer", config.pages().get(1).place());
		assertEquals("footer", config.pages().get(2).place());
		assertTrue(config.pages().stream().allMatch(CustomPage::enabled), "pages of one's own are enabled by default");

		file("rules/en.md", "1. Be nice");
		file("team/page.txt", "Alex and Sam");
		assertEquals(List.of("rules", "team"), ids(config));
	}

	@Test
	void emptyListRemovesTheSamplesForGood() throws IOException {
		assertTrue(config("{\"pages\": []}").pages().isEmpty());
		assertTrue(WebConfig.load().pages().isEmpty());
	}

	@Test
	void missingKeyGetsTheSamples() throws IOException {
		WebConfig config = config("{}");
		assertEquals(List.of("home", "imprint", "privacy", "rules"), config.pages().stream().map(CustomPage::id).toList());
		assertTrue(Files.readString(WebConfig.path()).contains("\"pages\""));
	}

	@Test
	void textIsReadAsUtf8WithoutByteOrderMark() throws IOException {
		WebConfig config = config(OWN_PAGES);
		file("imprint/de.txt", "﻿Straße 1");
		assertEquals("Straße 1", CustomPages.find(config, "imprint", "de").text());
	}

	@Test
	void unreadableOrHugeFilesAreLeftOut() throws IOException {
		WebConfig config = config(OWN_PAGES);
		Path latin1 = pages().resolve("imprint/de.txt");
		Files.createDirectories(latin1.getParent());
		Files.write(latin1, "Straße".getBytes(StandardCharsets.ISO_8859_1));
		file("imprint/en.txt", "x".repeat((int) CustomPages.MAX_BYTES + 1));
		file("imprint/page.md", "Fallback");
		assertEquals("Fallback", CustomPages.find(config, "imprint", "de").text());
	}
}
