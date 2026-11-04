package webui.stats.mc;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** "text per language or one text for all" (README, Configuration). */
class LocalizedTextTest {
	@Test
	void oneTextForAll() {
		assertEquals("My Server", LocalizedText.parse(JsonParser.parseString("\"My Server\"")));
	}

	@Test
	void textPerLanguage() {
		assertEquals(
			Map.of("en", "My Server", "de", "Mein Server"),
			LocalizedText.parse(JsonParser.parseString("{\"en\": \"My Server\", \"de\": \"Mein Server\"}"))
		);
	}

	@Test
	void emptyMeansOff() {
		assertTrue(LocalizedText.isEmpty(""));
		assertTrue(LocalizedText.isEmpty(Map.of()));
		assertTrue(LocalizedText.isEmpty(Map.of("en", "", "de", "")));
		assertFalse(LocalizedText.isEmpty("x"));
		assertFalse(LocalizedText.isEmpty(Map.of("en", "", "de", "x")));
	}

	@Test
	void ofPairs() {
		assertEquals(Map.of("en", "Hi", "de", "Hallo"), LocalizedText.of("en", "Hi", "de", "Hallo"));
	}
}
