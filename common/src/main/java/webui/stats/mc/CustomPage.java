package webui.stats.mc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A page of the server owner's own from the config ("pages"), e.g. an imprint or the server rules. Its text lives in
 * {@code config/stats_webui_plus/pages/<id>/}, one file per language; see {@link webui.stats.mc.web.CustomPages}.
 *
 * @param id      the folder name and the address (/pages/&lt;id&gt;): lowercase letters, digits, - and _
 * @param label   either a String or a Map of language code to text
 * @param place   "menu" (a button next to the custom links) or "footer" (a link next to How we rank)
 * @param enabled false hides the page (and its link) without removing it, e.g. a sample not filled in yet
 */
public record CustomPage(String id, Object label, String place, boolean enabled) {
	public static final List<String> PLACES = List.of("menu", "footer");
	public static final String DEFAULT_PLACE = "footer";
	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

	public static boolean isValidId(String id) {
		return id != null && ID.matcher(id).matches();
	}

	public Map<String, Object> toJson() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", id);
		body.put("label", label);
		body.put("place", place);
		body.put("enabled", enabled);
		return body;
	}

	/** Reads one entry of the "pages" array; returns null (with a warning) if it isn't usable. */
	public static CustomPage parse(JsonElement element) {
		if (!element.isJsonObject()) {
			McStatsWebui.LOGGER.warn("Ignoring pages entry {}: expected an object with id, label and place", element);
			return null;
		}
		JsonObject json = element.getAsJsonObject();
		String id = json.has("id") && json.get("id").isJsonPrimitive() ? json.get("id").getAsString().trim() : "";
		if (!isValidId(id)) {
			McStatsWebui.LOGGER.warn(
				"Ignoring page {}: id must be 1 to 64 lowercase letters, digits, - or _, starting with a letter or digit",
				id
			);
			return null;
		}
		Object label = LocalizedText.parse(json.get("label"));
		if (LocalizedText.isEmpty(label)) {
			McStatsWebui.LOGGER.warn("Ignoring page {}: it has no label", id);
			return null;
		}
		String place = json.has("place") && json.get("place").isJsonPrimitive()
			? json.get("place").getAsString().trim().toLowerCase(Locale.ROOT)
			: DEFAULT_PLACE;
		if (!PLACES.contains(place)) {
			McStatsWebui.LOGGER.warn("Page {}: place must be one of {}, using {}", id, PLACES, DEFAULT_PLACE);
			place = DEFAULT_PLACE;
		}
		boolean enabled = !json.has("enabled") || !json.get("enabled").isJsonPrimitive() || json.get("enabled").getAsBoolean();
		return new CustomPage(id, label, place, enabled);
	}
}
