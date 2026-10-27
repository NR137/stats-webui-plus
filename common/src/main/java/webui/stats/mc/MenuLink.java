package webui.stats.mc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A custom menu button from the config ("links").
 *
 * @param label  either a String or a Map of language code to text
 * @param url    an http(s) address or a path on this site; empty hides the button
 * @param newTab whether the link opens in a new browser tab
 */
public record MenuLink(Object label, String url, boolean newTab) {
	/** Buttons appear only with a label and a safe URL (no javascript: or similar). */
	public boolean shown() {
		return !url.isEmpty() && isAllowedUrl(url) && hasLabel();
	}

	public boolean hasLabel() {
		return !LocalizedText.isEmpty(label);
	}

	public static boolean isAllowedUrl(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		return url.isEmpty() || lower.startsWith("http://") || lower.startsWith("https://") || isSitePath(url);
	}

	/** A page on this site. Browsers read //host and /\host as another site, so those don't count. */
	private static boolean isSitePath(String url) {
		return url.startsWith("/") && !url.startsWith("//") && !url.startsWith("/\\");
	}

	public Map<String, Object> toJson() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("label", label);
		body.put("url", url);
		body.put("newTab", newTab);
		return body;
	}

	/** Reads one entry of the "links" array; returns null (with a warning) if it isn't usable. */
	public static MenuLink parse(JsonElement element) {
		if (!element.isJsonObject()) {
			McStatsWebui.LOGGER.warn("Ignoring links entry {}: expected an object with label and url", element);
			return null;
		}
		JsonObject json = element.getAsJsonObject();
		Object label = LocalizedText.parse(json.get("label"));
		String url = json.has("url") && json.get("url").isJsonPrimitive() ? json.get("url").getAsString().trim() : "";
		boolean newTab = !json.has("newTab") || !json.get("newTab").isJsonPrimitive() || json.get("newTab").getAsBoolean();
		MenuLink link = new MenuLink(label, url, newTab);
		if (!isAllowedUrl(url)) {
			McStatsWebui.LOGGER.warn("Hiding link {}: url must start with http://, https:// or a single / (a page on this site)", url);
		} else if (!url.isEmpty() && !link.hasLabel()) {
			McStatsWebui.LOGGER.warn("Hiding link {}: it has no label", url);
		}
		return link;
	}
}
