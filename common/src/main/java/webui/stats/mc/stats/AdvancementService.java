package webui.stats.mc.stats;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdvancementService {
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

	private AdvancementService() {
	}

	public static Map<String, Object> catalogPayload(List<PlayerRecord> players) {
		AdvancementCatalog catalog = AdvancementCatalog.get();
		List<Map<String, Object>> tabs = new ArrayList<>();
		for (AdvancementTab tab : catalog.tabs()) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", tab.id());
			item.put("title", tab.title());
			item.put("icon", tab.icon());
			item.put("background", tab.background());
			tabs.add(item);
		}
		List<Map<String, Object>> items = new ArrayList<>();
		for (AdvancementInfo info : catalog.all()) {
			Map<String, Object> item = infoPayload(info);
			item.put("holders", holders(info.id(), players).size());
			items.add(item);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("total", catalog.size());
		body.put("tabs", tabs);
		body.put("advancements", items);
		return body;
	}

	public static Map<String, Object> detailPayload(AdvancementInfo info, List<PlayerRecord> players) {
		List<PlayerRecord> holders = holders(info.id(), players);
		holders.sort(
			Comparator.comparingLong((PlayerRecord player) -> {
					long at = player.advancementTime(info.id());
					return at > 0 ? at : Long.MAX_VALUE;
				})
				.thenComparing(player -> player.name.toLowerCase(Locale.ROOT))
		);
		List<Map<String, Object>> entries = new ArrayList<>();
		int rank = 1;
		for (PlayerRecord player : holders) {
			long at = player.advancementTime(info.id());
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("rank", rank);
			entry.put("name", player.name);
			entry.put("uuid", player.uuid.toString());
			entry.put("value", at);
			entry.put("display", at > 0 ? WHEN.format(Instant.ofEpochMilli(at)) : "✓");
			entries.add(entry);
			rank += 1;
		}
		Map<String, Object> body = infoPayload(info);
		body.put("holders", entries.size());
		body.put("entries", entries);
		return body;
	}

	/**
	 * One player's progress on one advancement: whether and when it was done, and its requirements as Minecraft keeps
	 * them, each a list of criteria with the time it was met (0 if not yet). A requirement is met by any of its
	 * criteria; the advancement once all requirements are met.
	 */
	public static Map<String, Object> playerDetailPayload(AdvancementInfo info, PlayerRecord player, List<PlayerRecord> players) {
		Map<String, Object> body = infoPayload(info);
		body.put("holders", holders(info.id(), players).size());
		body.put("player", player.name);
		body.put("uuid", player.uuid.toString());
		boolean done = player.hasAdvancement(info.id());
		body.put("done", done);
		body.put("obtainedAt", player.advancementTime(info.id()));
		Map<String, Long> met = player.advancementCriteria(info.id());
		List<List<Map<String, Object>>> requirements = new ArrayList<>();
		for (List<String> requirement : requirements(info.id(), met)) {
			List<Map<String, Object>> group = new ArrayList<>();
			for (String criterion : requirement) {
				long at = met.getOrDefault(criterion, 0L);
				// A single criterion isn't kept separately: it was met when the advancement was done.
				if (at == 0 && done && requirement.size() == 1 && met.isEmpty()) {
					at = player.advancementTime(info.id());
				}
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("criterion", criterion);
				item.put("at", at);
				group.add(item);
			}
			requirements.add(group);
		}
		body.put("requirements", requirements);
		return body;
	}

	/** Requirements met / all requirements, for a player's unfinished advancements with more than one requirement. */
	public static Map<String, Object> progressPayload(PlayerRecord player) {
		Map<String, Object> body = new LinkedHashMap<>();
		for (AdvancementInfo info : AdvancementCatalog.get().all()) {
			List<List<String>> requirements = AdvancementCatalog.get().requirements(info.id());
			if (requirements == null || requirements.size() < 2 || player.hasAdvancement(info.id())) {
				continue;
			}
			Map<String, Long> met = player.advancementCriteria(info.id());
			int count = 0;
			for (List<String> requirement : requirements) {
				if (requirement.stream().anyMatch(met::containsKey)) {
					count += 1;
				}
			}
			body.put(info.id(), Map.of("done", count, "total", requirements.size()));
		}
		return body;
	}

	/** The catalog's requirements; for an unknown one, each recorded criterion as a requirement of its own. */
	private static List<List<String>> requirements(String id, Map<String, Long> met) {
		List<List<String>> requirements = AdvancementCatalog.get().requirements(id);
		if (requirements != null && !requirements.isEmpty()) {
			return requirements;
		}
		List<List<String>> own = new ArrayList<>();
		for (String criterion : met.keySet()) {
			own.add(List.of(criterion));
		}
		return own;
	}

	private static Map<String, Object> infoPayload(AdvancementInfo info) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("id", info.id());
		item.put("tab", info.tab());
		item.put("title", info.title());
		item.put("description", info.description());
		item.put("icon", info.icon());
		item.put("frame", info.frame());
		item.put("hidden", info.hidden());
		item.put("experience", info.experience());
		item.put("parent", info.parent());
		item.put("x", info.x());
		item.put("y", info.y());
		return item;
	}

	private static List<PlayerRecord> holders(String id, List<PlayerRecord> players) {
		List<PlayerRecord> holders = new ArrayList<>();
		for (PlayerRecord player : players) {
			if (player.hasAdvancement(id)) {
				holders.add(player);
			}
		}
		return holders;
	}
}
