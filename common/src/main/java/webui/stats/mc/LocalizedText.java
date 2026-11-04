package webui.stats.mc;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Config text that is either one String or a Map of language code to text ({"en": "Map", "de": "Karte"}).
 * Sent to the web UI as-is; the UI picks the visitor's language. An empty text in the Map means "not set" for that
 * language (it falls back to English, then to the first text given); empty entries are kept so a config can list
 * every language as a template.
 */
public final class LocalizedText {
	private LocalizedText() {
	}

	/** A String, a Map of language code to text, or "" when the value is missing or unusable. */
	public static Object parse(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			return element.getAsString().trim();
		}
		if (element != null && element.isJsonObject()) {
			Map<String, String> texts = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					texts.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString().trim());
				}
			}
			return texts;
		}
		return "";
	}

	public static boolean isEmpty(Object text) {
		if (text instanceof String value) {
			return value.isEmpty();
		}
		return !(text instanceof Map<?, ?> texts) || texts.values().stream().allMatch(value -> value.toString().isBlank());
	}

	/** Builds a per-language text from alternating language codes and texts. */
	public static Map<String, String> of(String... languageAndText) {
		Map<String, String> texts = new LinkedHashMap<>();
		for (int i = 0; i + 1 < languageAndText.length; i += 2) {
			texts.put(languageAndText[i], languageAndText[i + 1]);
		}
		return texts;
	}
}
