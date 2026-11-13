package de.nr137.statswebuiplus.web;

import de.nr137.statswebuiplus.stats.PlayerRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every board the mod ranks players on, in the order the web UI shows them. Only some are listed on the leaderboards
 * page (the boards that combine statistics); the others are reached from the rankings, a player's page or the compare
 * page. Each game statistic has a board of its own as well ({@link #vanilla}).
 */
public final class LeaderboardCatalog {
	private static final List<String> LISTED_CATEGORIES = List.of("skills", "life", "exploration", "mining", "combat", "totals");

	private static final Map<String, String> CATEGORY_ICONS = ordered(
		"rankings", "minecraft:nether_star",
		"skills", "minecraft:beacon",
		"advancements", "minecraft:experience_bottle",
		"life", "minecraft:clock",
		"exploration", "minecraft:compass",
		"mining", "minecraft:diamond_pickaxe",
		"deaths", "minecraft:bone",
		"combat", "minecraft:iron_sword",
		"totals", "minecraft:chest"
	);

	private static final List<LeaderboardInfo> ALL = List.of(
		board("legends", "rankings", "level", "minecraft:nether_star", false, false),
		board("veterans", "rankings", "hours", "minecraft:clock", false, false),
		board("grinders", "rankings", "actions_per_hour", "minecraft:sugar", false, false),
		skill("milestones", "minecraft:beacon"),
		skill("combat", "minecraft:iron_sword"),
		skill("exploration", "minecraft:filled_map"),
		skill("mining", "minecraft:diamond_pickaxe"),
		skill("building", "minecraft:bricks"),
		skill("farming", "minecraft:wheat"),
		skill("survival", "minecraft:golden_apple"),
		board("advancements", "advancements", "count", "minecraft:experience_bottle", false, false),
		board("play-time", "life", "hours", "minecraft:clock", true, false),
		board("current-life", "life", "hours", "minecraft:totem_of_undying", true, true),
		lowerWins("deaths", "life", "count", "special:player-killed"),
		lowerWins("deaths-per-hour", "life", "deaths_per_hour", "special:deaths-per-hour"),
		board("food-eaten", "life", "count", "minecraft:cooked_beef", true, true),
		board("distance-total", "exploration", "km", "minecraft:compass", true, true),
		board("distance-walk-sprint", "exploration", "km", "minecraft:leather_boots", true, true),
		board("distance-elytra", "exploration", "km", "minecraft:elytra", true, true),
		board("distance-per-hour", "exploration", "km_per_hour", "minecraft:recovery_compass", true, true),
		board("ores", "mining", "blocks", "minecraft:iron_ore", true, true),
		board("coal-mined", "mining", "blocks", "minecraft:coal", true, true),
		board("copper-mined", "mining", "blocks", "minecraft:copper_ingot", true, true),
		board("iron-mined", "mining", "blocks", "minecraft:iron_ingot", true, true),
		board("gold-mined", "mining", "blocks", "minecraft:gold_ingot", true, true),
		board("redstone-mined", "mining", "blocks", "minecraft:redstone", true, true),
		board("lapis-mined", "mining", "blocks", "minecraft:lapis_lazuli", true, true),
		board("diamonds-mined", "mining", "blocks", "minecraft:diamond", true, true),
		board("emerald-mined", "mining", "blocks", "minecraft:emerald", true, true),
		board("quartz-mined", "mining", "blocks", "minecraft:quartz", true, true),
		board("ancient-debris", "mining", "blocks", "minecraft:ancient_debris", true, true),
		board("logs-mined", "mining", "blocks", "minecraft:oak_log", true, true),
		board("hostile-kills", "combat", "kills", "minecraft:iron_sword", true, true),
		board("hostile-no-silverfish", "combat", "kills", "minecraft:stone_sword", true, true),
		board("other-mobs", "combat", "kills", "minecraft:cooked_chicken", true, true),
		board("bosses", "combat", "kills", "minecraft:dragon_head", true, true),
		board("kills-per-hour", "combat", "kills_per_hour", "special:kills-per-hour", true, true),
		board("player-kills", "combat", "kills", "minecraft:player_head", true, true),
		board("kills-per-death", "combat", "ratio", "special:kills-per-death", true, true),
		board("mob-types", "combat", "mob_types", "minecraft:skeleton_skull", true, true),
		board("blocks-mined", "totals", "blocks", "minecraft:diamond_pickaxe", true, true),
		board("blocks-placed", "totals", "blocks", "minecraft:bricks", true, true),
		board("items-crafted", "totals", "count", "minecraft:crafting_table", true, true),
		board("items-used", "totals", "count", "minecraft:flint_and_steel", true, true),
		board("tools-broken", "totals", "count", "special:broken-elytra", true, true),
		board("items-picked-up", "totals", "count", "minecraft:bundle", true, true),
		board("items-dropped", "totals", "count", "minecraft:dropper", true, true)
	);

	private static final Map<String, LeaderboardInfo> BY_ID = byId();
	private static final Map<String, List<LeaderboardInfo>> LISTED = listed();

	private LeaderboardCatalog() {
	}

	public static List<LeaderboardInfo> all() {
		return ALL;
	}

	public static LeaderboardInfo find(String id) {
		return id == null ? null : BY_ID.get(id);
	}

	/** The categories of the leaderboards page, in the order it shows them. */
	public static List<String> listedCategories() {
		return LISTED_CATEGORIES;
	}

	/** The listed boards of a category, in catalog order; empty for a category the page doesn't show. */
	public static List<LeaderboardInfo> listedIn(String category) {
		return LISTED.getOrDefault(category, List.of());
	}

	/** Every listed category, in page order, with its boards. */
	public static Map<String, List<LeaderboardInfo>> listedByCategory() {
		return LISTED;
	}

	public static String categoryIcon(String category) {
		return category == null ? "" : CATEGORY_ICONS.getOrDefault(category, "");
	}

	/**
	 * The board of one game statistic, e.g. {@code mined/minecraft:stone}: its group is the category; it has no icon of
	 * its own (the web UI shows the statistic's), isn't listed or compared, and more is better.
	 */
	public static LeaderboardInfo vanilla(String path, String group, String unit) {
		return new LeaderboardInfo(path, group, unit, "", false, false, false);
	}

	private static LeaderboardInfo board(String id, String category, String unit, String icon, boolean listed, boolean compare) {
		return new LeaderboardInfo(id, category, unit, icon, listed, compare, false);
	}

	private static LeaderboardInfo lowerWins(String id, String category, String unit, String icon) {
		return new LeaderboardInfo(id, category, unit, icon, true, true, true);
	}

	private static LeaderboardInfo skill(String skill, String icon) {
		return new LeaderboardInfo(PlayerRecord.SKILL_BOARD + skill, "skills", "skill", icon, true, false, false);
	}

	private static Map<String, LeaderboardInfo> byId() {
		Map<String, LeaderboardInfo> boards = new LinkedHashMap<>();
		for (LeaderboardInfo board : ALL) {
			boards.put(board.id(), board);
		}
		return Collections.unmodifiableMap(boards);
	}

	private static Map<String, List<LeaderboardInfo>> listed() {
		Map<String, List<LeaderboardInfo>> categories = new LinkedHashMap<>();
		for (String category : LISTED_CATEGORIES) {
			List<LeaderboardInfo> boards = new ArrayList<>();
			for (LeaderboardInfo board : ALL) {
				if (board.listed() && board.category().equals(category)) {
					boards.add(board);
				}
			}
			categories.put(category, List.copyOf(boards));
		}
		return Collections.unmodifiableMap(categories);
	}

	private static Map<String, String> ordered(String... pairs) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return Collections.unmodifiableMap(map);
	}
}
