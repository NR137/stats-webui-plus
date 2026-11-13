package de.nr137.statswebuiplus.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.item.crafting.FireworkStarFadeRecipe;
import net.minecraft.world.item.crafting.FireworkStarRecipe;
import net.minecraft.world.item.crafting.MapExtendingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import de.nr137.statswebuiplus.StatsWebuiPlus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which statistics each of the game's nine statistic groups lists, and the item sets the boards need (food, block
 * items). The lists only leave out what no player can get; any statistic a player has is shown on top of them
 * elsewhere, so a list may shorten what is shown but never be the reason something can't appear.
 */
public final class VanillaCatalog {
	/** The groups in the order the web UI shows them; each is stored in the stats file as {@code minecraft:<group>}. */
	static final String[] GROUPS = {
		"custom", "killed", "killed_by", "mined", "crafted", "used", "broken", "picked_up", "dropped"
	};

	/**
	 * The vanilla custom statistics (the game's {@code stat.minecraft.*} language keys), for when the registry is empty:
	 * in tests that don't start the game.
	 */
	static final List<String> CUSTOM = List.of(
		"minecraft:animals_bred", "minecraft:aviate_one_cm", "minecraft:bell_ring", "minecraft:boat_one_cm",
		"minecraft:clean_armor", "minecraft:clean_banner", "minecraft:clean_shulker_box", "minecraft:climb_one_cm",
		"minecraft:crouch_one_cm", "minecraft:damage_absorbed", "minecraft:damage_blocked_by_shield", "minecraft:damage_dealt",
		"minecraft:damage_dealt_absorbed", "minecraft:damage_dealt_resisted", "minecraft:damage_resisted", "minecraft:damage_taken",
		"minecraft:deaths", "minecraft:drop", "minecraft:eat_cake_slice", "minecraft:enchant_item",
		"minecraft:fall_one_cm", "minecraft:fill_cauldron", "minecraft:fish_caught", "minecraft:fly_one_cm",
		"minecraft:happy_ghast_one_cm", "minecraft:horse_one_cm", "minecraft:inspect_dispenser", "minecraft:inspect_dropper",
		"minecraft:inspect_hopper", "minecraft:interact_with_anvil", "minecraft:interact_with_beacon",
		"minecraft:interact_with_blast_furnace", "minecraft:interact_with_brewingstand", "minecraft:interact_with_campfire",
		"minecraft:interact_with_cartography_table", "minecraft:interact_with_crafting_table", "minecraft:interact_with_furnace",
		"minecraft:interact_with_grindstone", "minecraft:interact_with_lectern", "minecraft:interact_with_loom",
		"minecraft:interact_with_smithing_table", "minecraft:interact_with_smoker", "minecraft:interact_with_stonecutter",
		"minecraft:jump", "minecraft:leave_game", "minecraft:minecart_one_cm", "minecraft:mob_kills", "minecraft:nautilus_one_cm",
		"minecraft:open_barrel", "minecraft:open_chest", "minecraft:open_enderchest", "minecraft:open_shulker_box",
		"minecraft:pig_one_cm", "minecraft:player_kills", "minecraft:play_noteblock", "minecraft:play_record",
		"minecraft:play_time", "minecraft:pot_flower", "minecraft:raid_trigger", "minecraft:raid_win",
		"minecraft:sleep_in_bed", "minecraft:sleep_in_straw_bed", "minecraft:sneak_time", "minecraft:sprint_one_cm",
		"minecraft:strider_one_cm", "minecraft:swim_one_cm", "minecraft:talked_to_villager", "minecraft:target_hit",
		"minecraft:time_since_death", "minecraft:time_since_rest", "minecraft:total_world_time", "minecraft:traded_with_villager",
		"minecraft:trigger_trapped_chest", "minecraft:tune_noteblock", "minecraft:use_cauldron", "minecraft:walk_one_cm",
		"minecraft:walk_on_water_one_cm", "minecraft:walk_under_water_one_cm"
	);

	// The armor stand is an entity with health, but it is removed, never killed.
	private static final Set<String> NEVER_KILLED = Set.of("minecraft:armor_stand");

	/*
	 * Vanilla mobs that are never credited with a player's death. The game credits whoever last damaged the player within
	 * 5 seconds, so a mob that never damages a player is never a killer, however the player died next to it. Goats
	 * (ramming), foxes, snow golems (knockback), llamas (spit), dolphins, pandas, bees, wolves, polar bears and rabbits
	 * (the killer bunny) can damage players, and stay.
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

	// Results of recipes the game works out in code: their displays show no result.
	private static final Map<Class<?>, String> CODE_RECIPES = Map.of(
		BookCloningRecipe.class, "minecraft:written_book",
		FireworkRocketRecipe.class, "minecraft:firework_rocket",
		FireworkStarRecipe.class, "minecraft:firework_star",
		FireworkStarFadeRecipe.class, "minecraft:firework_star",
		MapExtendingRecipe.class, "minecraft:filled_map"
	);

	private static final Map<String, List<String>> KEYS = new ConcurrentHashMap<>();
	// null until the server started: then every item.
	private static volatile List<String> crafted;
	private static volatile List<String> broken;
	private static volatile Set<String> foodItems = Set.of();
	private static volatile Set<String> blockItems = Set.of();

	private VanillaCatalog() {
	}

	static boolean isGroup(String group) {
		return group != null && Arrays.asList(GROUPS).contains(group);
	}

	/** The statistic IDs a group lists, sorted; empty for an unknown group. */
	public static List<String> keys(String group) {
		if (!isGroup(group)) {
			return List.of();
		}
		switch (group) {
			case "crafted": {
				List<String> keys = crafted;
				return keys != null ? keys : KEYS.computeIfAbsent("items", key -> items());
			}
			case "broken": {
				List<String> keys = broken;
				return keys != null ? keys : KEYS.computeIfAbsent("items", key -> items());
			}
			case "used":
			case "picked_up":
			case "dropped":
				return KEYS.computeIfAbsent("items", key -> items());
			default:
				return KEYS.computeIfAbsent(group, VanillaCatalog::compute);
		}
	}

	/** Server start and /reload: what needs the server (item components, recipes, trades). */
	public static void boot(MinecraftServer server) {
		Set<String> food = new TreeSet<>();
		Set<String> blocks = new TreeSet<>();
		Set<String> durable = new TreeSet<>();
		for (Item item : BuiltInRegistries.ITEM) {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			if (item.components().has(DataComponents.FOOD)) {
				food.add(id);
			}
			if (item instanceof BlockItem) {
				blocks.add(id);
			}
			if (item.components().has(DataComponents.MAX_DAMAGE)) {
				durable.add(id);
			}
		}
		foodItems = Set.copyOf(food);
		blockItems = Set.copyOf(blocks);
		broken = List.copyOf(durable);
		crafted = craftable(server);
	}

	/** Items with the food component; empty before the server started. */
	public static Set<String> foodItems() {
		return foodItems;
	}

	/** Items that place a block; empty before the server started. */
	public static Set<String> blockItems() {
		return blockItems;
	}

	static void useItemsForTests(Set<String> food, Set<String> blockItems) {
		VanillaCatalog.foodItems = Set.copyOf(food);
		VanillaCatalog.blockItems = Set.copyOf(blockItems);
	}

	private static List<String> compute(String group) {
		Set<String> keys = new TreeSet<>();
		switch (group) {
			case "custom" -> {
				for (Identifier id : BuiltInRegistries.CUSTOM_STAT.keySet()) {
					keys.add(id.toString());
				}
				if (keys.isEmpty()) {
					keys.addAll(CUSTOM);
				}
			}
			case "killed", "killed_by" -> {
				Set<String> left = group.equals("killed") ? NEVER_KILLED : NEVER_KILLERS;
				for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
					Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
					// Mods' entities all count: we can't tell which of them can kill or be killed. Vanilla ones only if
					// they are living entities, which are the ones with default attributes.
					if (!isVanilla(id)) {
						keys.add(id.toString());
					} else if (DefaultAttributes.hasSupplier(type) && !left.contains(id.toString())) {
						keys.add(id.toString());
					}
				}
			}
			case "mined" -> {
				for (Block block : BuiltInRegistries.BLOCK) {
					Identifier id = BuiltInRegistries.BLOCK.getKey(block);
					if (!isVanilla(id) || breakable(block)) {
						keys.add(id.toString());
					}
				}
			}
			default -> {
			}
		}
		return List.copyOf(keys);
	}

	/** Whether a survival player can break the block: not air, no liquid, and a destroy time that isn't negative. */
	private static boolean breakable(Block block) {
		return !block.defaultBlockState().isAir()
			&& !(block instanceof LiquidBlock)
			&& block.defaultDestroyTime() >= 0;
	}

	private static List<String> items() {
		Set<String> keys = new TreeSet<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			keys.add(id.toString());
		}
		return List.copyOf(keys);
	}

	/**
	 * What a player can be credited with crafting on this server: every recipe's result (data packs included), what
	 * the game's own code recipes make, the cartography table's empty map, and what villagers trade.
	 */
	private static List<String> craftable(MinecraftServer server) {
		try {
			Set<String> keys = new TreeSet<>();
			ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
			for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
				Recipe<?> recipe = holder.value();
				String code = CODE_RECIPES.get(recipe.getClass());
				if (code != null) {
					keys.add(code);
				}
				try {
					for (RecipeDisplay display : recipe.display()) {
						for (ItemStack stack : display.result().resolveForStacks(context)) {
							if (!stack.isEmpty()) {
								keys.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
							}
						}
					}
				} catch (RuntimeException e) {
					StatsWebuiPlus.LOGGER.debug("Skipping recipe {} for the crafted list", holder.id(), e);
				}
			}
			keys.add("minecraft:map");
			keys.addAll(tradeResults(server));
			return List.copyOf(keys);
		} catch (RuntimeException e) {
			StatsWebuiPlus.LOGGER.warn("Could not list the craftable items; listing every item as crafted", e);
			return null;
		}
	}

	/** What villager trades give, from each trade's encoded form (it has no getter for it). */
	private static Set<String> tradeResults(MinecraftServer server) {
		Set<String> keys = new TreeSet<>();
		Registry<VillagerTrade> trades = server.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
		RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (VillagerTrade trade : trades) {
			JsonElement encoded = VillagerTrade.CODEC.encodeStart(ops, trade).result().orElse(null);
			if (encoded == null || !encoded.isJsonObject()) {
				continue;
			}
			JsonElement gives = encoded.getAsJsonObject().get("gives");
			if (gives != null && gives.isJsonObject()) {
				JsonObject item = gives.getAsJsonObject();
				gives = item.get("id");
			}
			if (gives != null && gives.isJsonPrimitive() && gives.getAsJsonPrimitive().isString()) {
				Identifier id = Identifier.tryParse(gives.getAsString());
				if (id != null) {
					keys.add(id.toString());
				}
			}
		}
		return keys;
	}

	private static boolean isVanilla(Identifier id) {
		return id.getNamespace().equals("minecraft");
	}
}
