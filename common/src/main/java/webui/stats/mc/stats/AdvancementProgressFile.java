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

	public AdvancementProgressFile(Set<String> done, Map<String, Long> obtainedAt, long firstActivityAt) {
		this.done = Set.copyOf(done);
		this.obtainedAt = Map.copyOf(obtainedAt);
		this.firstActivityAt = firstActivityAt;
	}

	public static AdvancementProgressFile empty() {
		return new AdvancementProgressFile(Set.of(), Map.of(), 0);
	}

	/** The same summary from the progress Minecraft holds in memory. Server thread only, where the progress changes. */
	public static AdvancementProgressFile of(Map<AdvancementHolder, AdvancementProgress> progress) {
		Set<String> done = new LinkedHashSet<>();
		Map<String, Long> obtainedAt = new LinkedHashMap<>();
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
			if (entry.getValue().isDone()) {
				String id = entry.getKey().id().toString();
				done.add(id);
				obtainedAt.put(id, completedAt(times, entry.getKey().value().requirements().requirements()));
			}
		}
		return new AdvancementProgressFile(done, obtainedAt, firstActivityAt);
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

	private static AdvancementProgressFile parse(JsonObject root) {
		Set<String> done = new LinkedHashSet<>();
		Map<String, Long> obtainedAt = new LinkedHashMap<>();
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
			if (!body.has("done") || !body.get("done").getAsBoolean()) {
				continue;
			}
			done.add(id);
			if (!times.isEmpty()) {
				obtainedAt.put(id, completedAt(times, AdvancementCatalog.get().requirements(id)));
			}
		}
		return new AdvancementProgressFile(done, obtainedAt, firstActivityAt);
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
