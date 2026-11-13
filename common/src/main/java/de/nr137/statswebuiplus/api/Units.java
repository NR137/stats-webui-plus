package de.nr137.statswebuiplus.api;

import de.nr137.statswebuiplus.web.LeaderboardInfo;

/**
 * Numbers, units and IDs as the API sends them. Values are plain numbers in a unit (the web UI formats them in the
 * visitor's language); IDs are full in JSON ({@code minecraft:jump}) and short in paths ({@code jump}).
 */
public final class Units {
	public static final String LEVEL = "level";
	public static final String SKILL = "skill";
	public static final String HOURS = "hours";
	public static final String KM = "km";
	public static final String HEARTS = "hearts";
	public static final String COUNT = "count";

	private static final String MINECRAFT = "minecraft:";
	private static final double TICKS_PER_HOUR = 20.0 * 3600;
	private static final double CM_PER_KM = 100_000.0;
	// The game counts damage in tenths of a health point, and a heart is two points.
	private static final double DAMAGE_PER_HEART = 20.0;

	private Units() {
	}

	/**
	 * Rounded to 2 decimals; a whole number as a whole number ({@code 31}, not {@code 31.0}). Not a number or infinite
	 * (which JSON can't hold) is 0.
	 */
	public static Number round(double value) {
		if (!Double.isFinite(value)) {
			return 0L;
		}
		double rounded = Math.round(value * 100) / 100.0;
		if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15) {
			return (long) rounded;
		}
		return rounded;
	}

	/** A board's unit for the API. */
	public static String of(LeaderboardInfo board) {
		return board.unit();
	}

	/**
	 * The unit of one of the game's statistics: in {@code custom}, distances ({@code *_one_cm}) in km, damage in hearts
	 * and times in hours; everything else counts.
	 */
	public static String ofStatistic(String group, String id) {
		if (!"custom".equals(group)) {
			return COUNT;
		}
		if (id.endsWith("_one_cm")) {
			return KM;
		}
		if (id.contains("damage_")) {
			return HEARTS;
		}
		if (id.endsWith("_time") || id.startsWith("minecraft:time_since_") || id.equals("minecraft:sneak_time")) {
			return HOURS;
		}
		return COUNT;
	}

	/** A statistic's raw value (cm, damage points, ticks, a count) in its unit, rounded. */
	public static Number statistic(String group, String id, long raw) {
		return switch (ofStatistic(group, id)) {
			case KM -> round(raw / CM_PER_KM);
			case HEARTS -> round(raw / DAMAGE_PER_HEART);
			case HOURS -> round(raw / TICKS_PER_HOUR);
			default -> raw;
		};
	}

	/** A time in epoch milliseconds, or null for unknown (0). */
	public static Long time(long millis) {
		return millis > 0 ? millis : null;
	}

	/** An ID from a path, full: {@code jump} -> {@code minecraft:jump}; {@code somemod:thing} stays. */
	public static String fullId(String path) {
		return path.indexOf(':') >= 0 ? path : MINECRAFT + path;
	}

	/** An ID as the website's paths write it: {@code minecraft:jump} -> {@code jump}; other namespaces stay. */
	public static String pathId(String id) {
		return id.startsWith(MINECRAFT) ? id.substring(MINECRAFT.length()) : id;
	}
}
