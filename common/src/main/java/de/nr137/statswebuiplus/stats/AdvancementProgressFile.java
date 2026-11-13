package de.nr137.statswebuiplus.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import de.nr137.statswebuiplus.StatsWebuiPlus;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a player has done, read from {@code <world>/advancements/<uuid>.json} or from the game's live progress: the
 * finished advancements, when each was completed, and when each criterion of the advancements with several was met.
 * Times are epoch milliseconds.
 */
public final class AdvancementProgressFile {
	// As Minecraft writes them: "2026-08-16 14:47:03 +0200".
	private static final DateTimeFormatter GAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.ROOT);
	private static final AdvancementProgressFile EMPTY = new AdvancementProgressFile(Set.of(), Map.of(), 0);

	/** IDs of the finished advancements. */
	public final Set<String> done;
	/** Finished advancement -> when it was completed. */
	public final Map<String, Long> obtainedAt;
	/**
	 * The oldest criterion time in the file, recipes and unfinished advancements included; 0 if none. Players unlock
	 * recipes in their first minutes, so this is close to their first join.
	 */
	public final long firstActivityAt;
	/** Advancement with several criteria (finished or not, no recipes) -> criterion -> when it was met. */
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
		this.done = Collections.unmodifiableSet(new LinkedHashSet<>(done));
		this.obtainedAt = Collections.unmodifiableMap(new LinkedHashMap<>(obtainedAt));
		this.firstActivityAt = firstActivityAt;
		Map<String, Map<String, Long>> copy = new LinkedHashMap<>();
		criteria.forEach((id, times) -> copy.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(times))));
		this.criteria = Collections.unmodifiableMap(copy);
	}

	public static AdvancementProgressFile empty() {
		return EMPTY;
	}

	/**
	 * The live progress of an online player. Server thread only: the game changes it there. Criterion times are cut to
	 * whole seconds, as the file has them, so a player reads the same before and after the game saved.
	 */
	public static AdvancementProgressFile of(Map<AdvancementHolder, AdvancementProgress> progress) {
		Summary summary = new Summary();
		for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progress.entrySet()) {
			AdvancementHolder holder = entry.getKey();
			AdvancementProgress advancement = entry.getValue();
			Map<String, Long> times = new LinkedHashMap<>();
			for (String criterion : advancement.getCompletedCriteria()) {
				CriterionProgress met = advancement.getCriterion(criterion);
				Instant obtained = met == null ? null : met.getObtained();
				if (obtained != null) {
					times.put(criterion, obtained.getEpochSecond() * 1000);
				}
			}
			if (times.isEmpty()) {
				continue;
			}
			// Recipes are advancements without a display.
			boolean recipe = holder.value().display().isEmpty();
			summary.add(holder.id().toString(), advancement.isDone(), times, holder.value().requirements().requirements(), recipe);
		}
		return summary.build();
	}

	/** A player's advancements file; a missing or unreadable file gives the empty summary. */
	public static AdvancementProgressFile read(Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return EMPTY;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				StatsWebuiPlus.LOGGER.warn("Could not read advancements file {}: not a JSON object", file.getFileName());
				return EMPTY;
			}
			return parse(root.getAsJsonObject());
		} catch (Exception e) {
			StatsWebuiPlus.LOGGER.warn("Could not read advancements file {}: {}", file.getFileName(), e.toString());
			return EMPTY;
		}
	}

	static AdvancementProgressFile parse(JsonObject root) {
		Summary summary = new Summary();
		AdvancementCatalog catalog = AdvancementCatalog.get();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			// DataVersion, and anything else that isn't an advancement's entry, is no object.
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject advancement = entry.getValue().getAsJsonObject();
			Map<String, Long> times = new LinkedHashMap<>();
			JsonElement criteria = advancement.get("criteria");
			if (criteria != null && criteria.isJsonObject()) {
				for (Map.Entry<String, JsonElement> criterion : criteria.getAsJsonObject().entrySet()) {
					long time = time(criterion.getValue());
					if (time != Long.MIN_VALUE) {
						times.put(criterion.getKey(), time);
					}
				}
			}
			JsonElement done = advancement.get("done");
			boolean finished = done instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
			String id = entry.getKey();
			summary.add(id, finished, times, catalog.requirements(id), id.contains(":recipes/"));
		}
		return summary.build();
	}

	/**
	 * When an advancement was completed: each requirement list is met by its earliest criterion, the advancement when
	 * the last list is met. Adventuring Time (one list per biome) is completed with the last biome, an advancement
	 * with one list of alternatives with the first of them. Unknown requirements, or none matching the times: the
	 * latest time.
	 */
	static long completedAt(Map<String, Long> times, List<List<String>> requirements) {
		if (times.isEmpty()) {
			return 0;
		}
		long completed = Long.MIN_VALUE;
		if (requirements != null) {
			for (List<String> list : requirements) {
				long earliest = Long.MAX_VALUE;
				for (String criterion : list) {
					Long time = times.get(criterion);
					if (time != null && time < earliest) {
						earliest = time;
					}
				}
				if (earliest != Long.MAX_VALUE && earliest > completed) {
					completed = earliest;
				}
			}
		}
		if (completed == Long.MIN_VALUE) {
			for (long time : times.values()) {
				completed = Math.max(completed, time);
			}
		}
		return completed;
	}

	/**
	 * Whether an advancement has several criteria, so its parts are worth showing: by its requirements if the server
	 * knows it, else by the times in the file.
	 */
	static boolean keepsCriteria(List<List<String>> requirements, Map<String, Long> times) {
		if (requirements == null) {
			return times.size() > 1;
		}
		Set<String> criteria = new HashSet<>();
		for (List<String> list : requirements) {
			criteria.addAll(list);
		}
		return criteria.size() > 1;
	}

	/** A criterion time as epoch milliseconds, or Long.MIN_VALUE if it isn't one. */
	private static long time(JsonElement value) {
		if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
			return Long.MIN_VALUE;
		}
		String text = primitive.getAsString().trim();
		try {
			return OffsetDateTime.parse(text, GAME_TIME).toInstant().toEpochMilli();
		} catch (DateTimeParseException ignored) {
			// not the game's format; maybe ISO-8601
		}
		try {
			return Instant.parse(text).toEpochMilli();
		} catch (DateTimeParseException ignored) {
			return Long.MIN_VALUE;
		}
	}

	/** Collects advancements one by one, from the file or the live progress alike. */
	private static final class Summary {
		private final Set<String> done = new LinkedHashSet<>();
		private final Map<String, Long> obtainedAt = new LinkedHashMap<>();
		private final Map<String, Map<String, Long>> criteria = new LinkedHashMap<>();
		private long firstActivityAt = Long.MAX_VALUE;

		void add(String id, boolean finished, Map<String, Long> times, List<List<String>> requirements, boolean recipe) {
			for (long time : times.values()) {
				firstActivityAt = Math.min(firstActivityAt, time);
			}
			if (finished) {
				done.add(id);
				if (!times.isEmpty()) {
					obtainedAt.put(id, completedAt(times, requirements));
				}
			}
			if (!recipe && !times.isEmpty() && keepsCriteria(requirements, times)) {
				criteria.put(id, times);
			}
		}

		AdvancementProgressFile build() {
			return new AdvancementProgressFile(done, obtainedAt, firstActivityAt == Long.MAX_VALUE ? 0 : firstActivityAt, criteria);
		}
	}
}
