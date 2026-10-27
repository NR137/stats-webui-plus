package webui.stats.mc;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** README, Menu links. */
class MenuLinkTest {
	private static MenuLink parse(String json) {
		return MenuLink.parse(JsonParser.parseString(json));
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://map.example.com", "http://example.com/x?y=1", "/map", "/"})
	void allowedUrls(String url) {
		assertTrue(MenuLink.isAllowedUrl(url), url);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,hi", "ftp://example.com", "map.example.com",
		"mailto:a@example.com", " https://example.com", "//evil.example.com", "/\\evil.example.com"
	})
	void forbiddenUrls(String url) {
		assertFalse(MenuLink.isAllowedUrl(url), url);
	}

	@Test
	void emptyUrlIsThePlaceholder() {
		assertTrue(MenuLink.isAllowedUrl(""));
		assertFalse(parse("{\"label\": \"Map\", \"url\": \"\"}").shown());
	}

	@Test
	void shownOnlyWithUrlAndLabel() {
		assertTrue(parse("{\"label\": \"Map\", \"url\": \"/map\"}").shown());
		assertFalse(parse("{\"label\": \"Map\", \"url\": \"\"}").shown());
		assertFalse(parse("{\"label\": \"Map\"}").shown());
	}

	@Test
	void labelPerLanguageOrOneForAll() {
		assertEquals("Map", parse("{\"label\": \"Map\", \"url\": \"/map\"}").label());
		assertEquals(Map.of("en", "Map", "de", "Karte"), parse("{\"label\": {\"en\": \"Map\", \"de\": \"Karte\"}, \"url\": \"/map\"}").label());
	}

	@Test
	void newTabDefaultsToTrue() {
		assertTrue(parse("{\"label\": \"Map\", \"url\": \"/map\"}").newTab());
		assertFalse(parse("{\"label\": \"Map\", \"url\": \"/map\", \"newTab\": false}").newTab());
	}

	@Test
	void jsonHasWhatTheMenuNeeds() {
		Map<String, Object> json = parse("{\"label\": \"Map\", \"url\": \"/map\", \"newTab\": false}").toJson();
		assertEquals("Map", json.get("label"));
		assertEquals("/map", json.get("url"));
		assertEquals(false, json.get("newTab"));
	}
}
