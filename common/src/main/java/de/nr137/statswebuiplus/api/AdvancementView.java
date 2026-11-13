package de.nr137.statswebuiplus.api;

import de.nr137.statswebuiplus.stats.AdvancementCatalog;
import de.nr137.statswebuiplus.stats.AdvancementInfo;
import de.nr137.statswebuiplus.stats.AdvancementTab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What the answers read from the advancement catalog: the shown advancements in order, the tabs, and any
 * advancement's requirements. The catalog needs a running server to boot; tests make one of their own with {@link #of}.
 */
public record AdvancementView(
	List<AdvancementInfo> all,
	List<AdvancementTab> tabs,
	Function<String, AdvancementInfo> find,
	Function<String, List<List<String>>> requirements
) {
	/** The server's catalog, read on every call (a /reload swaps it). */
	public static AdvancementView catalog() {
		AdvancementCatalog catalog = AdvancementCatalog.get();
		return new AdvancementView(catalog.all(), catalog.tabs(), catalog::find, catalog::requirements);
	}

	/** A catalog of these advancements (in this order), tabs and requirements. */
	public static AdvancementView of(List<AdvancementInfo> all, List<AdvancementTab> tabs, Map<String, List<List<String>>> requirements) {
		Map<String, AdvancementInfo> byId = new LinkedHashMap<>();
		for (AdvancementInfo info : all) {
			byId.put(info.id(), info);
		}
		Map<String, List<List<String>>> copy = Map.copyOf(requirements);
		return new AdvancementView(List.copyOf(all), List.copyOf(tabs), byId::get, copy::get);
	}

	public int size() {
		return all.size();
	}
}
