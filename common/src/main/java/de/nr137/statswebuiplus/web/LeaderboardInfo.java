package de.nr137.statswebuiplus.web;

/**
 * One board: its category and unit, an icon for the web UI, whether the leaderboards page lists it, whether it is a
 * row on the compare page, and whether fewer is better there (deaths).
 */
public record LeaderboardInfo(
	String id,
	String category,
	String unit,
	String icon,
	boolean listed,
	boolean compare,
	boolean lowerWins
) {
}
