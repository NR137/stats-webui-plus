package de.nr137.statswebuiplus.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The ranking: seven skills and the level made of them (the "How we rank" page, about.* in the web UI's messages,
 * explains every number here; keep both in step).
 *
 * <p>Each skill adds up parts of two kinds:
 * <ul>
 *   <li><b>breadth</b>: how many different things a player did (mob types, biomes, ores, block types, ...), against a
 *   fixed number. Full at that number, never more. A farm can't fake it.</li>
 *   <li><b>depth</b>: how much, on a curve that gives 100 at a seasoned player's value and keeps rising slowly past it
 *   (every doubling adds about 18). Farmable kinds are soft-capped: full up to a cap per mob type or ore, then only
 *   the square root of the rest.</li>
 * </ul>
 * A skill's value is the weighted sum of its parts (100 = very good, open-ended), the level the weighted sum of the
 * skills. Nothing is compared to other players: a player's level only ever changes by what they do, and, outside
 * Survival, it never goes down (game stats only grow).
 */
public final class Skills {
	/** The skills in the order the web UI shows them, with their weight in the level (adds up to 1). */
	public static final List<String> ORDER = List.of(
		"milestones", "combat", "exploration", "mining", "building", "farming", "survival"
	);
	private static final Map<String, Double> WEIGHTS = Map.of(
		"milestones", 0.20,
		"combat", 0.18,
		"exploration", 0.15,
		"mining", 0.14,
		"building", 0.13,
		"farming", 0.10,
		"survival", 0.10
	);

	// Depth curve: 100 at the target; the steepness keeps a little play from scoring a lot.
	private static final double STEEPNESS = 50;

	// Targets: what a well-rounded player has after a few hundred hours. Checked against about 600 real players' files
	// from public world backups (2026-11-08): the best survival players (300 to 1,000 hours) come out at levels 85 to 95.
	static final int HOSTILE_TYPES_TARGET = 30;
	static final double KILLS_TARGET = 4_000;
	static final int KILL_CAP = 250;
	static final int BIOMES_TARGET = 50;
	static final double KM_TARGET = 5_000;
	static final double MINECART_SHARE = 0.2;
	static final int ORE_TYPES_TARGET = 10;
	static final double ORE_TARGET = 12_000;
	static final int ORE_CAP = 1_500;
	static final int BLOCK_TYPES_TARGET = 400;
	static final int CRAFTED_TYPES_TARGET = 250;
	static final double PLACED_TARGET = 300_000;
	static final int FOOD_TYPES_TARGET = 25;
	static final int ANIMAL_TYPES_TARGET = 15;
	static final double FARMING_TARGET = 5_000;
	static final int TRADE_CAP = 2_000;
	static final int FISH_CAP = 1_000;
	static final double CROP_SHARE = 0.05;
	static final double HOURS_PER_DEATH_TARGET = 8;
	static final double KILLS_PER_DEATH_TARGET = 100;
	static final double MILESTONE_TARGET = 100;
	static final double REPEATS_TARGET = 400;
	static final int REPEAT_CAP = 10;
	/** Most active: actions per hour, from this much play time on (less says nothing yet). */
	static final double ACTIVE_MIN_HOURS = 2;

	/** One-time deeds: the stat that counts them, its points, and whether doing it again adds (less and less). */
	record Milestone(String id, String group, String stat, int points, boolean repeats) {
	}

	static final List<Milestone> MILESTONES = List.of(
		new Milestone("dragon", "killed", "minecraft:ender_dragon", 12, true),
		new Milestone("wither", "killed", "minecraft:wither", 14, true),
		new Milestone("warden", "killed", "minecraft:warden", 16, true),
		new Milestone("elder_guardian", "killed", "minecraft:elder_guardian", 8, true),
		new Milestone("raid", "custom", "minecraft:raid_win", 8, false),
		new Milestone("elytra", "picked_up", "minecraft:elytra", 10, false),
		new Milestone("beacon", "crafted", "minecraft:beacon", 8, true),
		new Milestone("netherite", "crafted", "minecraft:netherite_ingot", 6, true),
		new Milestone("conduit", "crafted", "minecraft:conduit", 6, true),
		new Milestone("mace", "crafted", "minecraft:mace", 6, true),
		new Milestone("totem", "used", "minecraft:totem_of_undying", 4, false),
		new Milestone("trident", "picked_up", "minecraft:trident", 4, false),
		new Milestone("dragon_egg", "picked_up", "minecraft:dragon_egg", 4, false),
		new Milestone("heart_of_the_sea", "picked_up", "minecraft:heart_of_the_sea", 3, false),
		new Milestone("wither_skull", "picked_up", "minecraft:wither_skeleton_skull", 3, false),
		new Milestone("golden_apple", "used", "minecraft:enchanted_golden_apple", 3, false),
		new Milestone("sniffer_egg", "picked_up", "minecraft:sniffer_egg", 3, false)
	);

	/** Ores by kind, with their worth per block (rarer is worth more). */
	record Ore(String id, List<String> blocks, double worth) {
	}

	static final List<Ore> ORES = List.of(
		new Ore("coal", List.of("minecraft:coal_ore", "minecraft:deepslate_coal_ore"), 0.25),
		new Ore("copper", List.of("minecraft:copper_ore", "minecraft:deepslate_copper_ore"), 1),
		new Ore("iron", List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore"), 2),
		new Ore("gold", List.of("minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore"), 3),
		new Ore("redstone", List.of("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"), 0.8),
		new Ore("lapis", List.of("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore"), 1.5),
		new Ore("diamond", List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"), 8),
		new Ore("emerald", List.of("minecraft:emerald_ore", "minecraft:deepslate_emerald_ore"), 6),
		new Ore("quartz", List.of("minecraft:nether_quartz_ore"), 1.2),
		new Ore("debris", List.of("minecraft:ancient_debris"), 15)
	);

	/** Advancements whose criteria are the biomes visited (overworld, nether) and the animals bred. */
	static final List<String> BIOME_ADVANCEMENTS = List.of(
		"minecraft:adventure/adventuring_time", "minecraft:nether/explore_nether"
	);
	static final String ANIMALS_ADVANCEMENT = "minecraft:husbandry/bred_all_animals";

	/**
	 * One part of a skill: its raw value (a count, km, points, ...), the target that gives 100, its share of the skill
	 * and its grade (0..100 for breadth and capped parts, open-ended for depth).
	 */
	public record Part(String id, double value, double target, double weight, double grade) {
	}

	/** A player's skills: each skill's value, its parts, the level and the actions per hour. */
	public record Result(Map<String, Double> skills, Map<String, List<Part>> parts, double level, double actionsPerHour) {
	}

	private Skills() {
	}

	/** Every player with their skills and level, best first (then less play time, then name, as the boards sort). */
	public static List<PlayerRecord> apply(List<PlayerRecord> players) {
		List<PlayerRecord> scored = new ArrayList<>(players.size());
		for (PlayerRecord player : players) {
			scored.add(player.withSkills(score(player)));
		}
		scored.sort(
			Comparator.comparingDouble((PlayerRecord player) -> player.level())
				.reversed()
				.thenComparingDouble(player -> player.playHours())
				.thenComparing(player -> player.name().toLowerCase(Locale.ROOT))
		);
		return scored;
	}

	public static double weight(String skill) {
		return WEIGHTS.getOrDefault(skill, 0.0);
	}

	public static Result score(PlayerRecord player) {
		Map<String, List<Part>> parts = new LinkedHashMap<>();
		double softKills = softKills(player);
		parts.put("milestones", milestones(player));
		parts.put("combat", List.of(
			breadth("mob_types", count(player, "killed", PlayerMetrics.hostileAll()), HOSTILE_TYPES_TARGET, 0.5),
			depth("kills", softKills, KILLS_TARGET, 0.5)
		));
		double km = PlayerMetrics.travelKm(player) - (1 - MINECART_SHARE) * PlayerMetrics.km(player.vanillaRaw("custom", "minecraft:minecart_one_cm"));
		parts.put("exploration", List.of(
			breadth("biomes", criteria(player, BIOME_ADVANCEMENTS), BIOMES_TARGET, 0.5),
			depth("distance", km, KM_TARGET, 0.5)
		));
		double oreWorth = 0;
		int oreTypes = 0;
		for (Ore ore : ORES) {
			long mined = 0;
			for (String block : ore.blocks()) {
				mined += player.vanillaRaw("mined", block);
			}
			if (mined > 0) {
				oreTypes += 1;
			}
			oreWorth += soft(mined, ORE_CAP) * ore.worth();
		}
		parts.put("mining", List.of(
			breadth("ore_types", oreTypes, ORE_TYPES_TARGET, 0.3),
			depth("ores", oreWorth, ORE_TARGET, 0.7)
		));
		parts.put("building", List.of(
			breadth("block_types", count(player, "used", VanillaCatalog.blockItems()), BLOCK_TYPES_TARGET, 0.35),
			breadth("crafted_types", count(player, "crafted", null), CRAFTED_TYPES_TARGET, 0.15),
			depth("placed", placed(player), PLACED_TARGET, 0.5)
		));
		int foods = count(player, "used", VanillaCatalog.foodItems())
			+ (player.vanillaRaw("custom", "minecraft:eat_cake_slice") > 0 ? 1 : 0);
		double farming = player.vanillaRaw("custom", "minecraft:animals_bred")
			+ soft(player.vanillaRaw("custom", "minecraft:traded_with_villager"), TRADE_CAP)
			+ soft(player.vanillaRaw("custom", "minecraft:fish_caught"), FISH_CAP)
			+ PlayerMetrics.crops(player) * CROP_SHARE;
		parts.put("farming", List.of(
			breadth("food_types", foods, FOOD_TYPES_TARGET, 0.25),
			breadth("animal_types", criteria(player, List.of(ANIMALS_ADVANCEMENT)), ANIMAL_TYPES_TARGET, 0.25),
			depth("farming", farming, FARMING_TARGET, 0.5)
		));
		long deaths = player.vanillaRaw("custom", "minecraft:deaths");
		parts.put("survival", List.of(
			capped("hours_per_death", player.playHours() / (deaths + 1), HOURS_PER_DEATH_TARGET, 0.5),
			capped("kills_per_death", softKills / (deaths + 1), KILLS_PER_DEATH_TARGET, 0.5)
		));

		Map<String, Double> skills = new LinkedHashMap<>();
		double level = 0;
		for (String skill : ORDER) {
			double value = 0;
			for (Part part : parts.get(skill)) {
				value += part.grade() * part.weight();
			}
			skills.put(skill, value);
			level += value * weight(skill);
		}
		return new Result(skills, parts, level, actionsPerHour(player, softKills));
	}

	private static List<Part> milestones(PlayerRecord player) {
		double firsts = 0;
		double repeats = 0;
		int done = 0;
		for (Milestone milestone : MILESTONES) {
			long times = player.vanillaRaw(milestone.group(), milestone.stat());
			if (times <= 0) {
				continue;
			}
			done += 1;
			firsts += milestone.points();
			if (milestone.repeats()) {
				repeats += milestone.points() * soft(times - 1, REPEAT_CAP);
			}
		}
		return List.of(
			new Part("milestones", done, MILESTONES.size(), 0.75, share(firsts, MILESTONE_TARGET)),
			depth("repeats", repeats, REPEATS_TARGET, 0.25)
		);
	}

	/** Kills of hostile mobs, each type full up to KILL_CAP, then the square root of the rest. */
	static double softKills(PlayerRecord player) {
		double kills = 0;
		for (String mob : PlayerMetrics.hostileAll()) {
			kills += soft(player.vanillaRaw("killed", mob), KILL_CAP);
		}
		return kills;
	}

	static double placed(PlayerRecord player) {
		long placed = 0;
		Map<String, Long> used = player.vanilla().getOrDefault("used", Map.of());
		for (String item : VanillaCatalog.blockItems()) {
			placed += used.getOrDefault(item, 0L);
		}
		return placed;
	}

	/**
	 * Most active: what a player gets done per hour online. Blocks mined and placed, metres travelled (a minecart
	 * not), and kills (soft-capped); AFK time adds hours but nothing else, so it lowers this.
	 */
	static double actionsPerHour(PlayerRecord player, double softKills) {
		if (player.playHours() < ACTIVE_MIN_HOURS) {
			return 0;
		}
		long mined = 0;
		for (long value : player.vanilla().getOrDefault("mined", Map.of()).values()) {
			mined += value;
		}
		double km = PlayerMetrics.travelKm(player) - PlayerMetrics.km(player.vanillaRaw("custom", "minecraft:minecart_one_cm"));
		double actions = mined + placed(player) + km * 200 + softKills * 10;
		return actions / player.playHours();
	}

	/** Different keys of a group with a value above 0, among {@code only} (or all, if null). */
	private static int count(PlayerRecord player, String group, Set<String> only) {
		int count = 0;
		for (Map.Entry<String, Long> entry : player.vanilla().getOrDefault(group, Map.of()).entrySet()) {
			if (entry.getValue() != null && entry.getValue() > 0 && (only == null || only.contains(entry.getKey()))) {
				count += 1;
			}
		}
		return count;
	}

	/** Different criteria met over some advancements (a biome in both lists counts once). */
	private static int criteria(PlayerRecord player, List<String> advancements) {
		Set<String> met = new HashSet<>();
		for (String advancement : advancements) {
			met.addAll(player.advancementCriteria(advancement).keySet());
		}
		return met.size();
	}

	private static Part breadth(String id, double value, double target, double weight) {
		return new Part(id, value, target, weight, share(value, target));
	}

	private static Part depth(String id, double value, double target, double weight) {
		return new Part(id, value, target, weight, curve(value, target));
	}

	private static Part capped(String id, double value, double target, double weight) {
		return new Part(id, value, target, weight, Math.min(100, curve(value, target)));
	}

	/** 0..100: the share of the target reached. */
	static double share(double value, double target) {
		return value <= 0 ? 0 : 100 * Math.min(1, value / target);
	}

	/** 100 at the target, open-ended above (each doubling adds about 18), 0 at 0. */
	static double curve(double value, double target) {
		if (value <= 0) {
			return 0;
		}
		return 100 * Math.log1p(value * STEEPNESS / target) / Math.log1p(STEEPNESS);
	}

	/** Full up to the cap, then the square root of the rest: a farm still adds, but little. */
	static double soft(double value, double cap) {
		return value <= cap ? Math.max(0, value) : cap + Math.sqrt(value - cap);
	}
}
