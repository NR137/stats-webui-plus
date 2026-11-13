package de.nr137.statswebuiplus.assets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The web UI's icon table, /mc/icons.json: which picture each ID shows. Items, blocks, mobs ("entity:minecraft:x"),
 * cat/wolf/frog variants, biomes, structures and the UI's own icons ("special:x"). Besides what the other classes
 * drew, it gives IDs without a picture of their own a related one: a crop its fruit, a wall sign its sign, a
 * potted plant its plant, an arrow or boat entity its item.
 */
final class IconTable {
	/** Blocks whose item has another name. */
	static final Map<String, String> BLOCK_ALIASES = Map.of(
		"redstone_wire", "redstone",
		"piston_head", "piston",
		"moving_piston", "piston",
		"bamboo_sapling", "bamboo",
		"bubble_column", "water",
		"end_portal", "end_portal_frame",
		"potted_azalea_bush", "azalea",
		"potted_flowering_azalea_bush", "flowering_azalea",
		"tall_seagrass", "seagrass");

	/** Crops show what they grow, not a sprout nobody recognises. */
	static final Map<String, String> CROP_ITEMS = Map.of(
		"carrots", "carrot",
		"potatoes", "potato",
		"beetroots", "beetroot",
		"cocoa", "cocoa_beans",
		"pitcher_crop", "pitcher_pod",
		"torchflower_crop", "torchflower");

	/** Entities without an icon of their own: the closest item or mob. */
	static final Map<String, String> ENTITY_ALIASES = new LinkedHashMap<>();

	static {
		String[] pairs = {
			"giant", "zombie", "illusioner", "evoker", "killer_bunny", "rabbit", "creaking_transient", "creaking",
			"mannequin", "player_head", "boat", "oak_boat", "chest_boat", "oak_chest_boat", "spawner_minecart", "spawner",
			"cushion", "white_cushion", "breeze_wind_charge", "wind_charge", "fireball", "fire_charge",
			"small_fireball", "fire_charge", "dragon_fireball", "dragon_breath", "wither_skull", "wither_skeleton_skull",
			"shulker_bullet", "shulker_shell", "llama_spit", "llama", "evoker_fangs", "evoker", "eye_of_ender", "ender_eye",
			"experience_orb", "experience_bottle", "fishing_bobber", "fishing_rod", "leash_knot", "lead",
			"lightning_bolt", "lightning_rod", "falling_block", "sand", "area_effect_cloud", "lingering_potion",
			"ominous_item_spawner", "ominous_trial_key",
			// Technical entities that never show in survival stats, so that every stat ID still has an icon.
			"marker", "barrier", "interaction", "barrier", "block_display", "item_frame", "item_display", "item_frame",
			"text_display", "oak_sign", "item", "bundle"};
		for (int i = 0; i < pairs.length; i += 2) {
			ENTITY_ALIASES.put(pairs[i], pairs[i + 1]);
		}
	}

	/** Blocks that are no item and no texture of their own. */
	static final Map<String, String> SPECIALS = new LinkedHashMap<>();

	static {
		String[] pairs = {
			"white_banner", "/mc/block/white_wool.png",
			"water", "/mc/block/water_still.png",
			"lava", "/mc/block/lava_still.png",
			"fire", "/mc/block/fire_0.png",
			"soul_fire", "/mc/block/soul_fire_0.png",
			"air", "/mc/item/barrier.png",
			"cave_air", "/mc/item/barrier.png",
			"void_air", "/mc/item/barrier.png",
			"decorated_pot", "/mc/item/flower_pot.png",
			"potatoes", "/mc/item/potato.png",
			"carrots", "/mc/item/carrot.png",
			"beetroots", "/mc/item/beetroot.png"};
		for (int i = 0; i < pairs.length; i += 2) {
			SPECIALS.put("minecraft:" + pairs[i], pairs[i + 1]);
		}
	}

	/** Blocks without an item: (end of the name, replacement) for the item to show instead. */
	private static final String[][] SWAPS = {
		{"_wall_sign", "_sign"}, {"_wall_hanging_sign", "_hanging_sign"}, {"_wall_fan", "_fan"}, {"_wall_coral", ""}};

	/** Plants and crops: the seed or fruit to show. */
	static final Map<String, String> PLANTED = Map.ofEntries(
		Map.entry("potatoes", "potato"), Map.entry("carrots", "carrot"), Map.entry("beetroots", "beetroot"),
		Map.entry("sweet_berry_bush", "sweet_berries"), Map.entry("melon_stem", "melon_seeds"),
		Map.entry("attached_melon_stem", "melon_seeds"), Map.entry("pumpkin_stem", "pumpkin_seeds"),
		Map.entry("attached_pumpkin_stem", "pumpkin_seeds"), Map.entry("pitcher_crop", "pitcher_pod"),
		Map.entry("torchflower_crop", "torchflower"), Map.entry("cocoa", "cocoa_beans"),
		Map.entry("nether_wart", "nether_wart"), Map.entry("wheat", "wheat"), Map.entry("decorated_pot", "flower_pot"));

	private final ZipFile jar;
	private final Path out;
	private final Map<String, String> icons = new TreeMap<>();

	private IconTable(ZipFile jar, Path out) {
		this.jar = jar;
		this.out = out;
	}

	/**
	 * Puts the table together from what was drawn (items and specials, mob faces with variants, scenes) and writes it
	 * to out/icons.json; returns it.
	 */
	static Map<String, String> write(Path clientJar, Path out, Map<String, String> items, Map<String, String> faces,
		Map<String, String> scenes) throws IOException {
		try (ZipFile jar = new ZipFile(clientJar.toFile())) {
			IconTable table = new IconTable(jar, out);
			table.build(items, faces, scenes);
			Files.writeString(out.resolve("icons.json"), new Gson().toJson(table.icons), StandardCharsets.UTF_8);
			return table.icons;
		}
	}

	private void build(Map<String, String> items, Map<String, String> faces, Map<String, String> scenes)
		throws IOException {
		String head = playerHead();
		if (head != null) {
			icons.put("minecraft:player_head", head);
		}
		items.forEach(icons::putIfAbsent);
		for (Map.Entry<String, String> crop : CROP_ITEMS.entrySet()) {
			String url = icons.get("minecraft:" + crop.getValue());
			if (url != null) {
				icons.put("minecraft:" + crop.getKey(), url);
			}
		}
		for (Map.Entry<String, String> special : SPECIALS.entrySet()) {
			if (!icons.containsKey(special.getKey()) && exists(special.getValue())) {
				icons.put(special.getKey(), special.getValue());
			}
		}
		if (head != null) {
			icons.putIfAbsent("minecraft:player", head);
		}
		List<String> mobs = MobFaces.mobs(jar);
		for (String mob : mobs) {
			if (exists("/mc/item/" + mob + "_spawn_egg.png")) {
				icons.putIfAbsent("minecraft:" + mob, "/mc/item/" + mob + "_spawn_egg.png");
			}
		}
		// Every block texture under its own name, for IDs named like one (the UI also guesses such names).
		Path blockFolder = out.resolve("block");
		try (Stream<Path> files = Files.isDirectory(blockFolder) ? Files.list(blockFolder) : Stream.empty()) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				String stem = file.getFileName().toString().replaceFirst("\\.png$", "");
				if (!stem.endsWith("_top") && !stem.endsWith("_side") && !stem.endsWith("_front") && !stem.endsWith("_bottom")) {
					icons.putIfAbsent("minecraft:" + stem, "/mc/block/" + file.getFileName());
				}
			}
		}
		Set<String> blocks = names("assets/minecraft/blockstates/");
		Set<String> entities = langNames("entity.minecraft.");
		Set<String> aliased = new TreeSet<>(blocks);
		aliased.addAll(entities);
		for (String name : aliased) {
			String target = alias(name);
			if (target != null && !icons.containsKey("minecraft:" + name) && icons.containsKey("minecraft:" + target)) {
				icons.put("minecraft:" + name, icons.get("minecraft:" + target));
			}
		}
		// Mobs: their face, else their spawn egg, else a related mob's or item's icon. Other entities (arrows,
		// boats) use the item with their ID, which the UI looks up by itself.
		icons.putAll(faces);
		Set<String> allEntities = new TreeSet<>(entities);
		allEntities.addAll(mobs);
		for (String entity : allEntities) {
			String key = "entity:minecraft:" + entity;
			if (icons.containsKey(key)) {
				continue;
			}
			if (!mobs.contains(entity)) {
				// Not a mob of its own, but a form of one (the creaking a heart spawns): that mob's face.
				String target = alias(entity);
				if (target != null && faces.containsKey("entity:minecraft:" + target)) {
					icons.put(key, faces.get("entity:minecraft:" + target));
				}
				continue;
			}
			String icon = exists("/mc/item/" + entity + "_spawn_egg.png") ? "/mc/item/" + entity + "_spawn_egg.png" : null;
			if (icon == null) {
				String target = alias(entity);
				icon = target == null ? null
					: icons.getOrDefault("entity:minecraft:" + target, icons.get("minecraft:" + target));
			}
			if (icon != null) {
				icons.put(key, icon);
			}
		}
		icons.putAll(scenes);
		if (exists("/mc/item/elytra_broken.png")) {
			icons.put("special:broken-elytra", "/mc/item/elytra_broken.png");
		}
		if (head != null) {
			icons.putIfAbsent("entity:minecraft:player", head);
		}
		Set<String> named = new TreeSet<>(names("assets/minecraft/items/"));
		named.addAll(names("assets/minecraft/models/block/"));
		named.addAll(blocks);
		for (String name : named) {
			fillMissing(name);
		}
	}

	/** Which other ID's icon a block or entity without one of its own shows. */
	static String alias(String name) {
		if (BLOCK_ALIASES.containsKey(name)) {
			return BLOCK_ALIASES.get(name);
		}
		if (ENTITY_ALIASES.containsKey(name)) {
			return ENTITY_ALIASES.get(name);
		}
		if (name.startsWith("potted_")) {
			return name.substring("potted_".length());
		}
		if (name.equals("candle_cake") || name.endsWith("_candle_cake")) {
			return "cake";
		}
		if (name.endsWith("_cauldron")) {
			return "cauldron";
		}
		if (name.contains("_wall_")) {
			return name.replaceFirst("_wall_", "_");
		}
		return null;
	}

	/**
	 * The last resort for an ID without an icon: the item or texture of a related name (the sign of a wall sign, the
	 * seeds of a stem, a wall-mounted block's own), else a picture under its own name (also a growth stage's or an
	 * animation's first texture).
	 */
	private void fillMissing(String name) {
		String id = "minecraft:" + name;
		if (icons.containsKey(id)) {
			return;
		}
		List<String> related = new java.util.ArrayList<>();
		for (String[] swap : SWAPS) {
			if (name.endsWith(swap[0])) {
				related.add(name.substring(0, name.length() - swap[0].length()) + swap[1]);
			}
		}
		if (name.startsWith("wall_")) {
			related.add(name.substring("wall_".length()));
		}
		if (PLANTED.containsKey(name)) {
			related.add(PLANTED.get(name));
		}
		List<String> candidates = new java.util.ArrayList<>();
		related.forEach(other -> candidates.addAll(List.of("/mc/item/" + other + ".png", "/mc/block/" + other + ".png")));
		for (String folder : new String[] {"item", "iso", "block"}) {
			candidates.add("/mc/" + folder + "/" + name + ".png");
		}
		for (String stage : new String[] {"_stage3", "_stage7", "_0"}) {
			candidates.add("/mc/block/" + name + stage + ".png");
		}
		candidates.stream().filter(this::exists).findFirst().ifPresent(url -> icons.put(id, url));
	}

	private boolean exists(String url) {
		return Files.isRegularFile(out.resolve(url.substring("/mc/".length())));
	}

	/** File names (without .json) in a folder of the JAR. */
	private Set<String> names(String folder) {
		Set<String> found = new TreeSet<>();
		jar.stream().map(ZipEntry::getName)
			.filter(name -> name.startsWith(folder) && name.endsWith(".json") && name.indexOf('/', folder.length()) < 0)
			.forEach(name -> found.add(name.substring(folder.length(), name.length() - 5)));
		return found;
	}

	/** IDs named in the game's English texts under this prefix ("entity.minecraft.zombie" -> zombie). */
	private Set<String> langNames(String prefix) throws IOException {
		ZipEntry entry = jar.getEntry("assets/minecraft/lang/en_us.json");
		Set<String> found = new TreeSet<>();
		if (entry == null) {
			return found;
		}
		JsonObject lang;
		try (InputStream in = jar.getInputStream(entry)) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		for (String key : lang.keySet()) {
			if (key.startsWith(prefix) && key.indexOf('.', prefix.length()) < 0) {
				found.add(key.substring(prefix.length()));
			}
		}
		return found;
	}

	/**
	 * The Player Head as the inventory shows it: Steve's head as a small cube from his default skin, each side with
	 * its hat layer on top (there is no texture of it; the game draws it from the skin too).
	 */
	private String playerHead() throws IOException {
		ZipEntry entry = jar.getEntry("assets/minecraft/textures/entity/player/wide/steve.png");
		if (entry == null) {
			return null;
		}
		Pixels skin;
		try (InputStream in = jar.getInputStream(entry)) {
			skin = Pixels.decode(in.readAllBytes());
		}
		String url = "/mc/iso/player_head.png";
		Pixels.iso(headSide(skin, 8, 0), headSide(skin, 0, 8), headSide(skin, 8, 8), 12)
			.write(out.resolve("iso/player_head.png"));
		return url;
	}

	/** One side of the head at (u, v) in the skin, the hat layer's pixel (32 to the right) where it has one. */
	private static Pixels headSide(Pixels skin, int u, int v) {
		Pixels side = new Pixels(8, 8);
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				int hat = skin.get(u + 32 + x, v + y);
				side.set(x, y, Pixels.alpha(hat) != 0 ? hat : skin.get(u + x, v + y));
			}
		}
		return side;
	}
}
