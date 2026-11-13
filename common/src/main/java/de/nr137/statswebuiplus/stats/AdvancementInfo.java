package de.nr137.statswebuiplus.stats;

/**
 * An advancement the server has, as the web UI shows it: texts as plain text, the icon's item ID, the frame
 * ({@code task}, {@code goal}, {@code challenge}), its tab, the nearest shown ancestor ({@code parent}, null for a
 * root) and its place in the game's advancement tree (column x, row y).
 */
public record AdvancementInfo(
	String id,
	String tab,
	String title,
	String description,
	String icon,
	String frame,
	boolean hidden,
	int experience,
	String parent,
	double x,
	double y
) {
}
