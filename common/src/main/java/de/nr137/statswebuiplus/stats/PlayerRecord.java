package de.nr137.statswebuiplus.stats;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player, ready to rank: their board values and raw statistics, their advancements and, once {@link Skills#apply}
 * has ranked them, their skills and level. Immutable; the {@code with...} methods return a changed copy.
 *
 * @param values board ID -> value, see {@link PlayerMetrics}
 * @param skills skill -> its value, in {@link Skills#ORDER}; empty until ranked
 * @param vanilla group (custom, mined, ...) -> statistic ID -> raw value, each group in the stats file's order
 * @param advancements IDs of the finished advancements
 * @param advancementTimes advancement -> when it was completed (epoch milliseconds)
 * @param advancementCriteria advancement with several criteria -> criterion -> when it was met (epoch milliseconds)
 */
public record PlayerRecord(UUID uuid, String name, double playHours, double level, double actionsPerHour,
	Map<String, Double> values, Map<String, Double> skills, Map<String, List<Skills.Part>> skillParts,
	Map<String, Map<String, Long>> vanilla, Set<String> advancements, Map<String, Long> advancementTimes,
	Map<String, Map<String, Long>> advancementCriteria) {
	/** Prefix of the skill boards' IDs: {@code skill-combat} is the combat skill's value. */
	public static final String SKILL_BOARD = "skill-";

	public PlayerRecord {
		values = copy(values);
		skills = copy(skills);
		Map<String, List<Skills.Part>> parts = new LinkedHashMap<>();
		if (skillParts != null) {
			skillParts.forEach((skill, list) -> parts.put(skill, List.copyOf(list)));
		}
		skillParts = Collections.unmodifiableMap(parts);
		vanilla = nestedCopy(vanilla);
		advancements = advancements == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(advancements));
		advancementTimes = copy(advancementTimes);
		advancementCriteria = nestedCopy(advancementCriteria);
	}

	/** A player as read from the files, not ranked yet. */
	public PlayerRecord(UUID uuid, String name, double playHours, Map<String, Double> values,
		Map<String, Map<String, Long>> vanilla, Set<String> advancements, Map<String, Long> advancementTimes) {
		this(uuid, name, playHours, 0, 0, values, Map.of(), Map.of(), vanilla, advancements, advancementTimes, Map.of());
	}

	/** The same player with their skills and level. */
	public PlayerRecord withSkills(Skills.Result skills) {
		return new PlayerRecord(uuid, name, playHours, skills.level(), skills.actionsPerHour(), values, skills.skills(),
			skills.parts(), vanilla, advancements, advancementTimes, advancementCriteria);
	}

	public PlayerRecord withName(String newName) {
		return new PlayerRecord(uuid, newName, playHours, level, actionsPerHour, values, skills, skillParts, vanilla,
			advancements, advancementTimes, advancementCriteria);
	}

	/** The same player with other advancements; the skills are dropped, since they depend on the advancements. */
	public PlayerRecord withAdvancements(Set<String> done, Map<String, Long> times, Map<String, Map<String, Long>> criteria) {
		return new PlayerRecord(uuid, name, playHours, 0, 0, values, Map.of(), Map.of(), vanilla, done, times, criteria);
	}

	/** A statistic's raw value, e.g. {@code ("mined", "minecraft:stone")}; 0 if the player has none. */
	public long vanillaRaw(String group, String id) {
		Map<String, Long> values = vanilla.get(group);
		if (values == null) {
			return 0;
		}
		Long value = values.get(id);
		return value == null ? 0 : value;
	}

	public boolean hasAdvancement(String id) {
		return advancements.contains(id);
	}

	/** When the advancement was completed (epoch milliseconds), 0 if not or unknown. */
	public long advancementTime(String id) {
		Long time = advancementTimes.get(id);
		return time == null ? 0 : time;
	}

	/** When each criterion of an advancement with several was met; empty if none. */
	public Map<String, Long> advancementCriteria(String id) {
		return advancementCriteria.getOrDefault(id, Map.of());
	}

	/** The player's value on a board (see {@link de.nr137.statswebuiplus.web.LeaderboardCatalog}); 0 if they have none. */
	public double value(String board) {
		switch (board) {
			case "legends":
				return level;
			case "veterans":
			case "play-time":
				return playHours;
			case "grinders":
				return actionsPerHour;
			case "advancements":
				return advancements.size();
			default:
				break;
		}
		if (board.startsWith(SKILL_BOARD)) {
			return skills.getOrDefault(board.substring(SKILL_BOARD.length()), 0.0);
		}
		return values.getOrDefault(board, 0.0);
	}

	@Override
	public String toString() {
		return "PlayerRecord{" + name + " " + uuid + "}";
	}

	// Ordered copies: groups keep the stats file's order and skills their display order, which Map.copyOf would lose
	// (it also can't be asked for a null key, which a lookup from a web request may pass).
	private static <K, V> Map<K, V> copy(Map<K, V> map) {
		if (map == null || map.isEmpty()) {
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(map));
	}

	private static <K, I, V> Map<K, Map<I, V>> nestedCopy(Map<K, ? extends Map<I, V>> map) {
		if (map == null || map.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<K, Map<I, V>> copy = new LinkedHashMap<>();
		map.forEach((key, inner) -> copy.put(key, copy(inner)));
		return Collections.unmodifiableMap(copy);
	}
}
