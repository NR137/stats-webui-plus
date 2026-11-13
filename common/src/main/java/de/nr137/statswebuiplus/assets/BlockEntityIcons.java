package de.nr137.statswebuiplus.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Inventory icons of the items the game draws with an entity model instead of a block model ("special" item models):
 * chests, shulker boxes, mob heads, banners, the decorated pot, the conduit, the shield and copper golem statues. Their
 * shapes come from the client JAR's model code (ClientModels.boxes), their textures from its entity textures, and they
 * are placed as the item definition says and drawn by ModelRenderer like the block models.
 */
final class BlockEntityIcons {
	/** A head kind's model layer and texture. */
	private static final Map<String, String[]> HEADS = Map.of(
		"skeleton", new String[] {"skeleton_skull#main", "entity/skeleton/skeleton"},
		"wither_skeleton", new String[] {"wither_skeleton_skull#main", "entity/skeleton/wither_skeleton"},
		"zombie", new String[] {"zombie_head#main", "entity/zombie/zombie"},
		"creeper", new String[] {"creeper_head#main", "entity/creeper/creeper"},
		"dragon", new String[] {"dragon_skull#main", "entity/enderdragon/dragon"},
		"piglin", new String[] {"piglin_head#main", "entity/piglin/piglin"});

	/** A copper golem statue's pose and its model layer. */
	private static final Map<String, String> POSES = Map.of(
		"standing", "copper_golem#main",
		"running", "copper_golem_running#main",
		"sitting", "copper_golem_sitting#main",
		"star", "copper_golem_star#main");

	/** The colours dyes give banners (DyeColor's texture colour). */
	static final Map<String, Integer> DYES = Map.ofEntries(
		Map.entry("white", 0xF9FFFE), Map.entry("orange", 0xF9801D), Map.entry("magenta", 0xC74EBD),
		Map.entry("light_blue", 0x3AB3DA), Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F),
		Map.entry("pink", 0xF38BAA), Map.entry("gray", 0x474F52), Map.entry("light_gray", 0x9D9D97),
		Map.entry("cyan", 0x169C9C), Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA),
		Map.entry("brown", 0x835432), Map.entry("green", 0x5E7C16), Map.entry("red", 0xB02E26),
		Map.entry("black", 0x1D1D21));

	/** The raid's ominous banner (Voluntary Exile, Hero of the Village): white with these patterns, in order. */
	static final String[][] OMINOUS = {{"rhombus", "cyan"}, {"stripe_bottom", "light_gray"}, {"stripe_center", "gray"},
		{"border", "light_gray"}, {"stripe_middle", "black"}, {"half_horizontal", "light_gray"}, {"circle", "light_gray"},
		{"border", "black"}};

	/** One special item: its ID, the special model, the transformation around it and the base model (its view). */
	private record Special(String name, JsonObject model, JsonObject transformation, String base) {
	}

	private final ZipFile jar;
	private final Path out;
	private final Map<String, Pixels> textures = new HashMap<>();
	private JsonObject boxes;

	private BlockEntityIcons(ZipFile jar, Path out) {
		this.jar = jar;
		this.out = out;
	}

	/** Draws the icons into out/iso/; returns ID -> URL (also the ominous banner's advancements). */
	static Map<String, String> render(Path clientJar, Path out) throws IOException {
		try (ZipFile jar = new ZipFile(clientJar.toFile())) {
			BlockEntityIcons icons = new BlockEntityIcons(jar, out);
			List<Special> specials = icons.specials();
			Set<String> layers = new TreeSet<>(List.of("standing_banner#main", "standing_banner#flag"));
			for (Special special : specials) {
				layers.addAll(icons.layers(special.model()));
			}
			icons.boxes = JsonParser.parseString(ClientClasses.boxes(clientJar, layers)).getAsJsonObject();
			Map<String, String> found = new LinkedHashMap<>();
			for (Special special : specials) {
				String url = icons.draw(special);
				if (url != null) {
					found.put("minecraft:" + special.name(), url);
				}
			}
			String ominous = icons.ominousBanner();
			if (ominous != null) {
				found.put("advancement:minecraft:adventure/voluntary_exile", ominous);
				found.put("advancement:minecraft:adventure/hero_of_the_village", ominous);
			}
			return found;
		}
	}

	/** Every item drawn by one special model (its plain case: fallbacks and "false" branches first). */
	private List<Special> specials() throws IOException {
		List<Special> found = new ArrayList<>();
		List<String> entries = jar.stream().map(ZipEntry::getName)
			.filter(name -> name.startsWith("assets/minecraft/items/") && name.endsWith(".json")).sorted().toList();
		for (String entry : entries) {
			JsonObject data = json(entry);
			if (data == null || !(data.get("model") instanceof JsonObject model)) {
				continue;
			}
			Special special = find(entry.substring("assets/minecraft/items/".length(), entry.length() - 5), model, null);
			if (special != null) {
				found.add(special);
			}
		}
		return found;
	}

	private static Special find(String name, JsonObject node, JsonObject transformation) {
		JsonObject placed = node.get("transformation") instanceof JsonObject own ? own : transformation;
		if (type(node).equals("special") && node.get("model") instanceof JsonObject model) {
			String base = node.has("base") ? node.get("base").getAsString() : null;
			return new Special(name, model, placed, base);
		}
		for (String key : new String[] {"fallback", "on_false"}) {
			if (node.get(key) instanceof JsonObject next) {
				return find(name, next, placed);
			}
		}
		return null;
	}

	private static String type(JsonObject node) {
		return node.has("type") ? node.get("type").getAsString().replace("minecraft:", "") : "";
	}

	/** The model layers a special model needs. */
	private List<String> layers(JsonObject model) {
		return switch (type(model)) {
			case "chest" -> List.of("chest#main");
			case "shulker_box" -> List.of("shulker_box#main");
			case "head" -> HEADS.containsKey(kind(model)) ? List.of(HEADS.get(kind(model))[0]) : List.of();
			case "decorated_pot" -> List.of("decorated_pot_base#main", "decorated_pot_sides#main");
			case "conduit" -> List.of("conduit#shell");
			case "shield" -> List.of("shield#main");
			case "copper_golem_statue" -> List.of(POSES.getOrDefault(string(model, "pose", "standing"), "copper_golem#main"));
			default -> List.of();
		};
	}

	private static String kind(JsonObject model) {
		return string(model, "kind", "");
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.get(key) instanceof JsonPrimitive value && value.isString() ? value.getAsString() : fallback;
	}

	/** Without the namespace: "minecraft:shulker_red" -> shulker_red. */
	private static String path(String id) {
		return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
	}

	private String draw(Special special) throws IOException {
		JsonObject model = special.model();
		List<ModelRenderer.Part> parts = new ArrayList<>();
		switch (type(model)) {
			case "chest" -> parts.add(part("chest#main", texture("entity/chest/" + path(string(model, "texture", "normal"))), special));
			case "shulker_box" -> parts.add(part("shulker_box#main",
				texture("entity/shulker/" + path(string(model, "texture", "shulker"))), special));
			case "head" -> {
				String[] head = HEADS.get(kind(model));
				if (head == null) {
					return null;
				}
				parts.add(part(head[0], texture(head[1]), special));
			}
			case "banner" -> {
				Integer color = DYES.get(string(model, "color", "white"));
				if (color == null) {
					return null;
				}
				parts.addAll(banner(color, List.of(), special));
			}
			case "decorated_pot" -> {
				parts.add(part("decorated_pot_base#main", texture("entity/decorated_pot/decorated_pot_base"), special));
				parts.add(part("decorated_pot_sides#main", texture("entity/decorated_pot/decorated_pot_side"), special));
			}
			case "conduit" -> parts.add(part("conduit#shell", texture("entity/conduit/base"), special));
			case "shield" -> parts.add(part("shield#main", texture("entity/shield/shield_base_nopattern"), special));
			case "copper_golem_statue" -> {
				String file = path(string(model, "texture", "textures/entity/copper_golem/copper_golem.png"));
				Pixels texture = file.startsWith("textures/") && file.endsWith(".png")
					? texture(file.substring("textures/".length(), file.length() - 4)) : null;
				parts.add(part(POSES.getOrDefault(string(model, "pose", "standing"), "copper_golem#main"), texture, special));
			}
			default -> {
				return null;
			}
		}
		if (parts.contains(null)) {
			return null;
		}
		Pixels icon = ModelRenderer.render(parts, ModelRenderer.View.of(gui(special.base(), new java.util.HashSet<>())));
		if (icon == null) {
			return null;
		}
		String url = "/mc/iso/" + special.name() + ".png";
		icon.write(out.resolve("iso/" + special.name() + ".png"));
		return url;
	}

	/** A standing banner of this colour with these patterns ({pattern, dye}), as the game layers them on the flag. */
	private List<ModelRenderer.Part> banner(int color, List<String[]> patterns, Special special) throws IOException {
		Pixels pole = texture("entity/banner/banner_base");
		Pixels base = texture("entity/banner/base");
		if (pole == null || base == null) {
			return java.util.Arrays.asList((ModelRenderer.Part) null);
		}
		Pixels flag = Scenes.copy(pole);
		flag.over(base.tint(color));
		for (String[] pattern : patterns) {
			Pixels layer = texture("entity/banner/" + pattern[0]);
			if (layer != null) {
				flag.over(layer.tint(DYES.get(pattern[1])));
			}
		}
		return java.util.Arrays.asList(part("standing_banner#main", pole, special), part("standing_banner#flag", flag, special));
	}

	/** The ominous banner, placed like a white banner's item; null if something is missing. */
	private String ominousBanner() throws IOException {
		JsonObject white = json("assets/minecraft/items/white_banner.json");
		Special special = white == null || !(white.get("model") instanceof JsonObject model) ? null : find("white_banner", model, null);
		if (special == null) {
			return null;
		}
		List<String[]> patterns = java.util.Arrays.asList(OMINOUS);
		List<ModelRenderer.Part> parts = banner(DYES.get("white"), patterns, special);
		if (parts.contains(null)) {
			return null;
		}
		Pixels icon = ModelRenderer.render(parts, ModelRenderer.View.of(gui(special.base(), new java.util.HashSet<>())));
		if (icon == null) {
			return null;
		}
		icon.write(out.resolve("iso/ominous_banner.png"));
		return "/mc/iso/ominous_banner.png";
	}

	/** A model layer's faces with this texture, placed as the special item's transformation says. */
	private ModelRenderer.Part part(String layer, Pixels texture, Special special) {
		if (texture == null || !(boxes.get(layer) instanceof JsonArray quads) || quads.isEmpty()) {
			return null;
		}
		List<ModelRenderer.Face> faces = new ArrayList<>();
		for (JsonElement entry : quads) {
			JsonArray quad = entry.getAsJsonArray();
			if (quad.size() != 24) {
				continue;
			}
			double[][] points = new double[4][];
			double[][] uv = new double[4][];
			for (int c = 0; c < 4; c++) {
				int at = 1 + c * 5;
				points[c] = new double[] {quad.get(at).getAsDouble(), quad.get(at + 1).getAsDouble(), quad.get(at + 2).getAsDouble()};
				uv[c] = new double[] {quad.get(at + 3).getAsDouble() * 16, quad.get(at + 4).getAsDouble() * 16};
			}
			double[] normal = {quad.get(21).getAsDouble(), quad.get(22).getAsDouble(), quad.get(23).getAsDouble()};
			faces.add(new ModelRenderer.Face(points, uv, normal, texture));
		}
		JsonObject t = special.transformation();
		return ModelRenderer.Part.faces(faces, numbers(t, "translation", 0, 3, 0), numbers(t, "scale", 1, 3, 1),
			numbers(t, "left_rotation", 0, 4, 1), numbers(t, "right_rotation", 0, 4, 1));
	}

	private static double[] numbers(JsonObject object, String key, double fallback, int count, double last) {
		double[] result = new double[count];
		java.util.Arrays.fill(result, fallback);
		result[count - 1] = last;
		if (object != null && object.get(key) instanceof JsonArray array && array.size() == count) {
			for (int i = 0; i < count; i++) {
				result[i] = array.get(i).getAsDouble();
			}
		}
		return result;
	}

	/** The inventory transform of the special item's base model, or of the nearest parent that has one. */
	private JsonObject gui(String model, Set<String> seen) throws IOException {
		if (model == null || !seen.add(model)) {
			return null;
		}
		JsonObject data = json(GameIcons.modelPath(model));
		if (data == null) {
			return null;
		}
		if (data.get("display") instanceof JsonObject display && display.get("gui") instanceof JsonObject gui) {
			return gui;
		}
		return data.get("parent") instanceof JsonPrimitive parent && parent.isString() ? gui(parent.getAsString(), seen) : null;
	}

	/** A texture below assets/minecraft/textures/ (without .png); null if the JAR has none. */
	private Pixels texture(String path) throws IOException {
		if (textures.containsKey(path)) {
			return textures.get(path);
		}
		ZipEntry entry = jar.getEntry("assets/minecraft/textures/" + path + ".png");
		Pixels texture = null;
		if (entry != null) {
			try (InputStream in = jar.getInputStream(entry)) {
				texture = Pixels.decode(in.readAllBytes());
			}
		}
		textures.put(path, texture);
		return texture;
	}

	private JsonObject json(String path) throws IOException {
		ZipEntry entry = jar.getEntry(path);
		if (entry == null) {
			return null;
		}
		try (InputStream in = jar.getInputStream(entry)) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
