package de.nr137.statswebuiplus.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The numbers behind the boards: turns a player's statistics into one value per board of
 * {@link de.nr137.statswebuiplus.web.LeaderboardCatalog}, and says which statistics each board adds up.
 */
public final class PlayerMetrics {
	private static final double TICKS_PER_HOUR = 20.0 * 3600;
	private static final double CM_PER_KM = 100_000.0;
	// Below this much play time a per-hour value says nothing yet (a minute's two kills would be 120 an hour).
	private static final double MIN_HOURS_PER_HOUR = 0.05;

	private static final Set<String> HOSTILE = ids(
		"zombie", "skeleton", "creeper", "spider", "cave_spider", "enderman", "witch", "drowned", "husk", "stray",
		"phantom", "blaze", "ghast", "magma_cube", "slime", "wither_skeleton", "piglin", "piglin_brute",
		"zombified_piglin", "hoglin", "zoglin", "guardian", "elder_guardian", "shulker", "vindicator", "evoker",
		"pillager", "ravager", "vex", "warden", "wither", "ender_dragon", "endermite", "breeze", "bogged", "creaking",
		"zombie_villager", "zombie_nautilus"
	);
	// Silverfish come out of infested stone by the hundred; they have a board of hostile mobs without them.
	private static final Set<String> HOSTILE_ALL = union(HOSTILE, ids("silverfish"));
	private static final Set<String> BOSSES = ids("ender_dragon", "wither", "warden", "elder_guardian");
	private static final String PLAYER = "minecraft:player";

	/** The ore boards and their blocks, each ore with its deepslate variant. */
	private static final Map<String, Set<String>> ORE_BOARDS = oreBoards();
	private static final Set<String> ORES = allOres();
	private static final String ANCIENT_DEBRIS = "minecraft:ancient_debris";

	private static final Set<String> CROPS = ids(
		"wheat", "potatoes", "carrots", "beetroots", "sugar_cane", "nether_wart", "melon", "pumpkin", "cocoa", "cactus",
		"bamboo", "sweet_berry_bush", "kelp", "chorus_flower", "torchflower", "pitcher_crop"
	);

	// Useful distance: getting somewhere. Creative flight and falling don't count.
	private static final Set<String> TRAVEL = ids(
		"walk_one_cm", "sprint_one_cm", "swim_one_cm", "boat_one_cm", "aviate_one_cm", "horse_one_cm",
		"happy_ghast_one_cm", "nautilus_one_cm", "minecart_one_cm", "strider_one_cm", "climb_one_cm", "crouch_one_cm",
		"walk_on_water_one_cm", "walk_under_water_one_cm"
	);
	private static final Set<String> WALK_SPRINT = ids("walk_one_cm", "sprint_one_cm");
	private static final String AVIATE = "minecraft:aviate_one_cm";

	private static final Predicate<String> LOGS = id -> id.endsWith("_log") || id.endsWith("_stem");
	private static final Predicate<String> OTHER_MOBS = id -> !HOSTILE_ALL.contains(id) && !id.equals(PLAYER);

	private PlayerMetrics() {
	}

	/** A player's board values and raw statistics; no skills yet (see {@link Skills#apply}) and no advancements. */
	public static PlayerRecord compute(UUID uuid, String name, StatFile stats) {
		double hours = stats.custom("play_time") / TICKS_PER_HOUR;
		long deaths = stats.custom("deaths");
		long hostile = stats.sum("killed", HOSTILE);
		double travel = km(stats.sum("custom", TRAVEL));

		Map<String, Double> values = new LinkedHashMap<>();
		values.put("hostile-kills", (double) stats.sum("killed", HOSTILE_ALL));
		values.put("hostile-no-silverfish", (double) hostile);
		values.put("other-mobs", (double) stats.sum("killed", OTHER_MOBS));
		values.put("bosses", (double) stats.sum("killed", BOSSES));
		values.put("player-kills", (double) stats.custom("player_kills"));
		values.put("deaths", (double) deaths);
		values.put("kills-per-hour", perHour(stats.custom("mob_kills"), hours));
		values.put("deaths-per-hour", perHour(deaths, hours));
		values.put("kills-per-death", hostile / (double) Math.max(deaths, 1));
		values.put("mob-types", mobTypes(stats));
		values.put("distance-per-hour", perHour(travel, hours));
		values.put("current-life", stats.custom("time_since_death") / TICKS_PER_HOUR);
		values.put("food-eaten", (double) (stats.sum("used", VanillaCatalog.foodItems()) + stats.custom("eat_cake_slice")));
		values.put("blocks-placed", (double) stats.sum("used", VanillaCatalog.blockItems()));
		values.put("items-crafted", (double) stats.sum("crafted"));
		values.put("items-used", (double) stats.sum("used"));
		values.put("items-picked-up", (double) stats.sum("picked_up"));
		values.put("items-dropped", (double) stats.sum("dropped"));
		values.put("blocks-mined", (double) stats.sum("mined"));
		values.put("tools-broken", (double) stats.sum("broken"));
		values.put("ores", (double) stats.sum("mined", ORES));
		ORE_BOARDS.forEach((board, blocks) -> values.put(board, (double) stats.sum("mined", blocks)));
		values.put("ancient-debris", (double) stats.mined(ANCIENT_DEBRIS));
		values.put("logs-mined", (double) stats.sum("mined", LOGS));
		values.put("distance-total", travel);
		values.put("distance-walk-sprint", km(stats.sum("custom", WALK_SPRINT)));
		values.put("distance-elytra", km(stats.custom(AVIATE)));

		Map<String, Map<String, Long>> vanilla = new LinkedHashMap<>();
		for (String group : VanillaCatalog.GROUPS) {
			vanilla.put(group, stats.groupValues(group));
		}
		return new PlayerRecord(uuid, name, hours, values, vanilla, Set.of(), Map.of());
	}

	/** What a board adds up, group -> statistic IDs, for its page; empty for a board of no combined statistics. */
	public static Map<String, List<String>> parts(String board) {
		Map<String, List<String>> parts = new LinkedHashMap<>();
		switch (board) {
			case "hostile-kills" -> parts.put("killed", sorted(HOSTILE_ALL));
			case "hostile-no-silverfish" -> parts.put("killed", sorted(HOSTILE));
			case "other-mobs" -> parts.put("killed", filtered(VanillaCatalog.keys("killed"), OTHER_MOBS));
			case "bosses" -> parts.put("killed", sorted(BOSSES));
			case "kills-per-death" -> {
				parts.put("killed", sorted(HOSTILE));
				parts.put("custom", List.of("minecraft:deaths"));
			}
			case "kills-per-hour" -> parts.put("custom", List.of("minecraft:mob_kills", "minecraft:play_time"));
			case "deaths-per-hour" -> parts.put("custom", List.of("minecraft:deaths", "minecraft:play_time"));
			case "ores" -> parts.put("mined", sorted(ORES));
			case "logs-mined" -> parts.put("mined", filtered(VanillaCatalog.keys("mined"), LOGS));
			case "distance-total" -> parts.put("custom", List.copyOf(TRAVEL));
			case "distance-walk-sprint" -> parts.put("custom", List.copyOf(WALK_SPRINT));
			case "distance-per-hour" -> parts.put("custom", List.copyOf(union(TRAVEL, Set.of("minecraft:play_time"))));
			case "current-life" -> parts.put("custom", List.of("minecraft:time_since_death"));
			case "food-eaten" -> {
				parts.put("used", sorted(VanillaCatalog.foodItems()));
				parts.put("custom", List.of("minecraft:eat_cake_slice"));
			}
			default -> {
				Set<String> ores = ORE_BOARDS.get(board);
				if (ores != null) {
					parts.put("mined", List.copyOf(ores));
				}
			}
		}
		return Collections.unmodifiableMap(parts);
	}

	/** Hostile mobs, silverfish included (for {@link Skills}). */
	static Set<String> hostileAll() {
		return HOSTILE_ALL;
	}

	/** Useful distance in km, from the record's raw custom statistics. */
	static double travelKm(PlayerRecord player) {
		long cm = 0;
		for (String stat : TRAVEL) {
			cm += player.vanillaRaw("custom", stat);
		}
		return km(cm);
	}

	/** Crops harvested (mined). */
	static long crops(PlayerRecord player) {
		long crops = 0;
		for (String crop : CROPS) {
			crops += player.vanillaRaw("mined", crop);
		}
		return crops;
	}

	static double km(long cm) {
		return cm / CM_PER_KM;
	}

	private static double mobTypes(StatFile stats) {
		int types = 0;
		for (long kills : stats.groupValues("killed").values()) {
			if (kills > 0) {
				types++;
			}
		}
		return types;
	}

	private static double perHour(double value, double hours) {
		return hours < MIN_HOURS_PER_HOUR ? 0 : value / hours;
	}

	private static Map<String, Set<String>> oreBoards() {
		Map<String, Set<String>> boards = new LinkedHashMap<>();
		boards.put("coal-mined", ids("coal_ore", "deepslate_coal_ore"));
		boards.put("copper-mined", ids("copper_ore", "deepslate_copper_ore"));
		boards.put("iron-mined", ids("iron_ore", "deepslate_iron_ore"));
		boards.put("gold-mined", ids("gold_ore", "deepslate_gold_ore", "nether_gold_ore"));
		boards.put("redstone-mined", ids("redstone_ore", "deepslate_redstone_ore"));
		boards.put("lapis-mined", ids("lapis_ore", "deepslate_lapis_ore"));
		boards.put("diamonds-mined", ids("diamond_ore", "deepslate_diamond_ore"));
		boards.put("emerald-mined", ids("emerald_ore", "deepslate_emerald_ore"));
		boards.put("quartz-mined", ids("nether_quartz_ore"));
		return Collections.unmodifiableMap(boards);
	}

	private static Set<String> allOres() {
		Set<String> ores = new HashSet<>();
		ORE_BOARDS.values().forEach(ores::addAll);
		ores.add(ANCIENT_DEBRIS);
		return Set.copyOf(ores);
	}

	/** In the order given: a board's page lists the travel statistics and each ore's blocks that way. */
	private static Set<String> ids(String... paths) {
		Set<String> ids = new LinkedHashSet<>();
		for (String path : paths) {
			ids.add("minecraft:" + path);
		}
		return Collections.unmodifiableSet(ids);
	}

	private static Set<String> union(Set<String> a, Set<String> b) {
		Set<String> union = new LinkedHashSet<>(a);
		union.addAll(b);
		return Collections.unmodifiableSet(union);
	}

	private static List<String> sorted(Set<String> ids) {
		return List.copyOf(new TreeSet<>(ids));
	}

	private static List<String> filtered(List<String> ids, Predicate<String> keep) {
		List<String> kept = new ArrayList<>();
		for (String id : ids) {
			if (keep.test(id)) {
				kept.add(id);
			}
		}
		return List.copyOf(kept);
	}
}
