package de.nr137.statswebuiplus.stats;

/**
 * A tab of the advancements screen: its root's title and icon, and the vanilla background texture's plain name
 * ({@code stone}, {@code nether}, ...; null for any other).
 */
public record AdvancementTab(String id, String title, String icon, String background) {
}
