package webui.stats.mc.stats;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VanillaCatalog {
	public static final String[] GROUPS = {
		"custom",
		"killed",
		"killed_by",
		"mined",
		"crafted",
		"used",
		"broken",
		"picked_up",
		"dropped"
	};

	public static final List<String> CUSTOM = List.of(
		"minecraft:animals_bred",
		"minecraft:aviate_one_cm",
		"minecraft:bell_ring",
		"minecraft:boat_one_cm",
		"minecraft:clean_armor",
		"minecraft:clean_banner",
		"minecraft:clean_shulker_box",
		"minecraft:climb_one_cm",
		"minecraft:crouch_one_cm",
		"minecraft:damage_absorbed",
		"minecraft:damage_blocked_by_shield",
		"minecraft:damage_dealt",
		"minecraft:damage_dealt_absorbed",
		"minecraft:damage_dealt_resisted",
		"minecraft:damage_resisted",
		"minecraft:damage_taken",
		"minecraft:deaths",
		"minecraft:drop",
		"minecraft:eat_cake_slice",
		"minecraft:enchant_item",
		"minecraft:fall_one_cm",
		"minecraft:fill_cauldron",
		"minecraft:fish_caught",
		"minecraft:fly_one_cm",
		"minecraft:happy_ghast_one_cm",
		"minecraft:horse_one_cm",
		"minecraft:inspect_dispenser",
		"minecraft:inspect_dropper",
		"minecraft:inspect_hopper",
		"minecraft:interact_with_anvil",
		"minecraft:interact_with_beacon",
		"minecraft:interact_with_blast_furnace",
		"minecraft:interact_with_brewingstand",
		"minecraft:interact_with_campfire",
		"minecraft:interact_with_cartography_table",
		"minecraft:interact_with_crafting_table",
		"minecraft:interact_with_furnace",
		"minecraft:interact_with_grindstone",
		"minecraft:interact_with_lectern",
		"minecraft:interact_with_loom",
		"minecraft:interact_with_smithing_table",
		"minecraft:interact_with_smoker",
		"minecraft:interact_with_stonecutter",
		"minecraft:jump",
		"minecraft:leave_game",
		"minecraft:minecart_one_cm",
		"minecraft:mob_kills",
		"minecraft:nautilus_one_cm",
		"minecraft:open_barrel",
		"minecraft:open_chest",
		"minecraft:open_enderchest",
		"minecraft:open_shulker_box",
		"minecraft:pig_one_cm",
		"minecraft:play_noteblock",
		"minecraft:play_record",
		"minecraft:play_time",
		"minecraft:player_kills",
		"minecraft:pot_flower",
		"minecraft:raid_trigger",
		"minecraft:raid_win",
		"minecraft:sleep_in_bed",
		"minecraft:sneak_time",
		"minecraft:sprint_one_cm",
		"minecraft:strider_one_cm",
		"minecraft:swim_one_cm",
		"minecraft:talked_to_villager",
		"minecraft:target_hit",
		"minecraft:time_since_death",
		"minecraft:time_since_rest",
		"minecraft:total_world_time",
		"minecraft:traded_with_villager",
		"minecraft:trigger_trapped_chest",
		"minecraft:tune_noteblock",
		"minecraft:use_cauldron",
		"minecraft:walk_on_water_one_cm",
		"minecraft:walk_one_cm",
		"minecraft:walk_under_water_one_cm"
	);

	private static final Map<String, List<String>> CACHE = new ConcurrentHashMap<>();

	private VanillaCatalog() {
	}

	public static boolean isGroup(String group) {
		for (String known : GROUPS) {
			if (known.equals(group)) {
				return true;
			}
		}
		return false;
	}

	public static String jsonType(String group) {
		return "minecraft:" + group;
	}

	public static List<String> keys(String group) {
		return CACHE.computeIfAbsent(group, VanillaCatalog::computeKeys);
	}

	private static List<String> computeKeys(String group) {
		return switch (group) {
			case "custom" -> customIds();
			case "killed" -> killableEntityIds().stream().filter(id -> !NEVER_KILLED.contains(id)).toList();
			case "killed_by" -> killableEntityIds().stream().filter(id -> !NEVER_KILLERS.contains(id)).toList();
			case "mined" -> ids(BuiltInRegistries.BLOCK);
			default -> ids(BuiltInRegistries.ITEM);
		};
	}

	/**
	 * Vanilla living entities that can never be credited with a player's death. killed_by goes to the entity
	 * responsible for the last damage the player took within 5 seconds (LivingEntity.getKillCredit), so only
	 * entities that never cause damage to a player are left out: no attack at all, or attacks only against
	 * animals/slimes (cat, ocelot, frog, axolotl). Checked against the 26.3 code. Kept on purpose: goat (rams), fox
	 * (defends players it trusts against other players), snow golem (a 0-damage snowball still counts as the last
	 * hit), llamas (spit), dolphin, panda, bee, wolf, polar bear, rabbit (killer bunny). Any non-zero value is
	 * shown anyway, so a mod or command that makes one of these attack still shows up.
	 */
	private static final Set<String> NEVER_KILLERS = Set.of(
		"minecraft:allay", "minecraft:armadillo", "minecraft:armor_stand", "minecraft:axolotl", "minecraft:bat",
		"minecraft:camel", "minecraft:camel_husk", "minecraft:cat", "minecraft:chicken", "minecraft:cod",
		"minecraft:copper_golem", "minecraft:cow", "minecraft:donkey", "minecraft:frog", "minecraft:giant",
		"minecraft:glow_squid", "minecraft:happy_ghast", "minecraft:horse", "minecraft:mannequin", "minecraft:mooshroom",
		"minecraft:mule", "minecraft:ocelot", "minecraft:parrot", "minecraft:pig", "minecraft:salmon", "minecraft:sheep",
		"minecraft:skeleton_horse", "minecraft:sniffer", "minecraft:squid", "minecraft:strider", "minecraft:tadpole",
		"minecraft:tropical_fish", "minecraft:turtle", "minecraft:villager", "minecraft:wandering_trader",
		"minecraft:zombie_horse"
	);

	/**
	 * Vanilla living entities a player can never be credited with killing: a broken armor stand is just removed
	 * (ArmorStand.kill), it never dies, and only a death awards the killed stat.
	 */
	private static final Set<String> NEVER_KILLED = Set.of("minecraft:armor_stand");

	/**
	 * Entity types that can appear in kill/death stats. Minecraft only awards them for living entities
	 * (Player.killedEntity, and ServerPlayer.die via getKillCredit), and every living entity must have default
	 * attributes, so vanilla types without them (boats, arrows, item frames, ...) can never have a value.
	 * Types added by other mods are all kept, since we can't know how those mods award stats.
	 */
	private static List<String> killableEntityIds() {
		List<String> keys = new ArrayList<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (id == null) {
				continue;
			}
			if (Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) && !DefaultAttributes.hasSupplier(type)) {
				continue;
			}
			keys.add(id.toString());
		}
		Collections.sort(keys);
		return keys;
	}

	private static List<String> customIds() {
		List<String> keys = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.CUSTOM_STAT) {
			keys.add(id.toString());
		}
		if (keys.isEmpty()) {
			return CUSTOM;
		}
		Collections.sort(keys);
		return keys;
	}

	private static <T> List<String> ids(Registry<T> registry) {
		List<String> keys = new ArrayList<>();
		for (T value : registry) {
			Identifier id = registry.getKey(value);
			if (id != null) {
				keys.add(id.toString());
			}
		}
		Collections.sort(keys);
		return keys;
	}
}
