package webui.stats.mc.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import webui.stats.mc.McStatsWebui;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AdvancementProgressFile {
	private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder()
		.parseCaseInsensitive()
		.appendPattern("yyyy-MM-dd HH:mm:ss Z")
		.toFormatter(Locale.ROOT);

	public final Set<String> done;
	// When each finished advancement was completed: the moment its last requirement was met.
	public final Map<String, Long> obtainedAt;
	// Oldest timestamp of any criterion, finished or not, recipes included; 0 if there is none.
	// Players unlock recipes within their first minutes, so this approximates when they first joined.
	public final long firstActivityAt;
	// When each met criterion was met, for advancements with more than one criterion only (Adventuring Time's biomes,
	// Monsters Hunted's mobs, ...), finished or not: what a player's advancement page lists as done and missing. Single
	// criterion advancements are covered by obtainedAt, and recipes are left out, so this stays small.
	public final Map<String, Map<String, Long>> criteria;

	public AdvancementProgressFile(Set<String> done, Map<String, Long> obtainedAt, long firstActivityAt) {
		this(done, obtainedAt, firstActivityAt, Map.of());
	}

	public AdvancementProgressFile(
		Set<String> done,
		Map<String, Long> obtainedAt,
		long firstActivityAt,
		Map<String, Map<String, Long>> criteria
	) {
		this.done = Set.copyOf(done);
		this.obtainedAt = Map.copyOf(obtainedAt);
		this.firstActivityAt = firstActivityAt;
		Map<String, Map<String, Long>> copy = new LinkedHashMap<>();
		criteria.forEach((id, times) -> copy.put(id, Map.copyOf(times)));
		this.criteria = Map.copyOf(copy);
	}

	public static AdvancementProgressFile empty() {
		return new AdvancementProgressFile(Set.of(), Map.of(), 0);
	}

	/** Whether an advancement's criteria are kept: it has several (by its requirements, or by the file if unknown). */
	static boolean keepsCriteria(List<List<String>> requirements, Map<String, Long> times) {
		if (requirements == null) {
			return times.size() > 1;
		}
		int count = 0;
		for (List<String> requirement : requirements) {
			count += requirement.size();
		}
		return count > 1;
	}

	/** The same summary from the progress Minecraft holds in memory. Server thread only, where the progress changes. */
	public static AdvancementProgressFile of(Map<AdvancementHolder, AdvancementProgress> progress) {
		Set<String> done = new LinkedHashSet<>();
		Map<String, Long> obtainedAt = new LinkedHashMap<>();
		Map<String, Map<String, Long>> criteria = new LinkedHashMap<>();
		long firstActivityAt = 0;
		for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progress.entrySet()) {
			Map<String, Long> times = new LinkedHashMap<>();
			for (String criterion : entry.getValue().getCompletedCriteria()) {
				CriterionProgress criterionProgress = entry.getValue().getCriterion(criterion);
				Instant obtained = criterionProgress != null ? criterionProgress.getObtained() : null;
				if (obtained != null) {
					// Whole seconds, like the times in the file.
					times.put(criterion, obtained.getEpochSecond() * 1000);
				}
			}
			// Without any finished criterion; the file leaves such advancements out.
			if (times.isEmpty()) {
				continue;
			}
			long started = Collections.min(times.values());
			if (firstActivityAt == 0 || started < firstActivityAt) {
				firstActivityAt = started;
			}
			String id = entry.getKey().id().toString();
			List<List<String>> requirements = entry.getKey().value().requirements().requirements();
			// Recipes have no display; nobody looks at their criteria.
			if (entry.getKey().value().display().isPresent() && keepsCriteria(requirements, times)) {
				criteria.put(id, times);
			}
			if (entry.getValue().isDone()) {
				done.add(id);
				obtainedAt.put(id, completedAt(times, requirements));
			}
		}
		return new AdvancementProgressFile(done, obtainedAt, firstActivityAt, criteria);
	}

	public static AdvancementProgressFile read(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return empty();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				return empty();
			}
			return parse(parsed.getAsJsonObject());
		} catch (Exception e) {
			McStatsWebui.LOGGER.warn("Could not read advancements {}", path.getFileName(), e);
			return empty();
		}
	}

	static AdvancementProgressFile parse(JsonObject root) {
		Set<String> done = new LinkedHashSet<>();
		Map<String, Long> obtainedAt = new LinkedHashMap<>();
		Map<String, Map<String, Long>> criteria = new LinkedHashMap<>();
		long firstActivityAt = 0;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			String id = entry.getKey();
			if ("DataVersion".equals(id) || !entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject body = entry.getValue().getAsJsonObject();
			Map<String, Long> times = criteriaTimes(body.get("criteria"));
			long started = times.isEmpty() ? 0 : Collections.min(times.values());
			if (started > 0 && (firstActivityAt == 0 || started < firstActivityAt)) {
				firstActivityAt = started;
			}
			if (!times.isEmpty() && !id.contains(":recipes/")
				&& keepsCriteria(AdvancementCatalog.get().requirements(id), times)) {
				criteria.put(id, times);
			}
			if (!body.has("done") || !body.get("done").getAsBoolean()) {
				continue;
			}
			done.add(id);
			if (!times.isEmpty()) {
				obtainedAt.put(id, completedAt(times, AdvancementCatalog.get().requirements(id)));
			}
		}
		return new AdvancementProgressFile(done, obtainedAt, firstActivityAt, criteria);
	}

	private static Map<String, Long> criteriaTimes(JsonElement criteria) {
		Map<String, Long> times = new LinkedHashMap<>();
		if (criteria == null || !criteria.isJsonObject()) {
			return times;
		}
		for (Map.Entry<String, JsonElement> entry : criteria.getAsJsonObject().entrySet()) {
			if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
				continue;
			}
			long at = parseTime(entry.getValue().getAsString());
			if (at > 0) {
				times.put(entry.getKey(), at);
			}
		}
		return times;
	}

	/**
	 * When the advancement was completed: each requirement is met by its earliest criterion, and the advancement once
	 * all requirements are met. "Adventuring Time" is completed with the last biome, not the first.
	 */
	static long completedAt(Map<String, Long> times, List<List<String>> requirements) {
		long completed = 0;
		boolean matched = false;
		if (requirements != null) {
			for (List<String> requirement : requirements) {
				long met = 0;
				for (String criterion : requirement) {
					Long at = times.get(criterion);
					if (at != null && (met == 0 || at < met)) {
						met = at;
					}
				}
				if (met > 0) {
					matched = true;
					completed = Math.max(completed, met);
				}
			}
		}
		// Unknown advancement, or its criteria were renamed: the last criterion is the best guess.
		return matched ? completed : Collections.max(times.values());
	}

	private static long parseTime(String raw) {
		try {
			return OffsetDateTime.parse(raw, TIME).toInstant().toEpochMilli();
		} catch (Exception ignored) {
			try {
				return Instant.parse(raw).toEpochMilli();
			} catch (Exception e) {
				return 0;
			}
		}
	}
}
