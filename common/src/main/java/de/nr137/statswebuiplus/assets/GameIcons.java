package de.nr137.statswebuiplus.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Icons the web UI draws from the game's textures as the game's inventory shows them, where no single texture is the
 * icon: full blocks as small 3D cubes (iso/), items made of layers (layered/, leather armour, potions), textures the
 * game colours (tinted/, leaves), and a few icons of our own (special/). Read from the item and block models in the
 * client JAR, textures from the folder GameTextures filled.
 *
 * Blocks with animated textures (a .mcmeta with "animation": command blocks, magma, prismarine, ...) become animated
 * PNGs (see Apng) that play like the game's, blending frames where the game does.
 */
public final class GameIcons {
	/** Models whose parents make a full cube; an item using only these is drawn as an iso block. */
	private static final List<String> CUBE_HINTS = List.of(
		"block/cube", "block/cube_all", "block/cube_column", "block/cube_column_horizontal", "block/cube_bottom_top",
		"block/cube_top", "block/cube_mirrored", "block/orientable", "block/leaves"
	);

	/** What one run drew: ID -> URL below /mc/, and which of them are animated. */
	public record Result(Map<String, String> icons, List<String> animated) {
	}

	/** Most frames an animated icon gets per loop; slow animations (prismarine: 5.5 min) are sampled less often. */
	private static final int MAX_FRAMES = Integer.getInteger("stats.maxAnimationFrames", 120);

	private final ZipFile jar;
	private final Path out;
	private final Map<String, JsonObject> models = new HashMap<>();
	private final Map<String, Pixels> grassMap = new HashMap<>();
	private final List<String> animated = new ArrayList<>();

	private GameIcons(ZipFile jar, Path out) {
		this.jar = jar;
		this.out = out;
	}

	/** Draws the icons into out (which already holds GameTextures' files). */
	public static Result render(Path clientJar, Path out) throws IOException {
		try (ZipFile jar = new ZipFile(clientJar.toFile())) {
			GameIcons icons = new GameIcons(jar, out);
			Map<String, String> found = new TreeMap<>();
			List<String> items = new ArrayList<>();
			jar.stream().map(ZipEntry::getName)
				.filter(name -> name.startsWith("assets/minecraft/items/") && name.endsWith(".json"))
				.forEach(items::add);
			for (String entry : items) {
				String id = "minecraft:" + entry.substring("assets/minecraft/items/".length(), entry.length() - 5);
				JsonObject data = icons.json(entry);
				if (data == null) {
					continue;
				}
				String url = icons.resolveItem(id, data);
				if (url != null) {
					found.put(id, url);
				}
			}
			found.putAll(icons.specials());
			icons.blocksWithoutItem().forEach(found::putIfAbsent);
			return new Result(found, List.copyOf(icons.animated));
		}
	}

	// --- Items ---------------------------------------------------------------------------------------------------

	/**
	 * The icon of an item as its definition and models say: its layers put together, its own texture, its layer0
	 * texture, a cube of its block's faces, or its model drawn; null if none fits (the web UI shows a fallback then).
	 */
	String resolveItem(String id, JsonObject data) throws IOException {
		String name = id.substring("minecraft:".length());
		JsonObject primary = primaryModel(data);
		String model = primary == null ? null : primary.get("model").getAsString();
		List<Integer> tints = new ArrayList<>();
		if (primary != null && primary.get("tints") instanceof JsonArray list) {
			for (JsonElement tint : list) {
				tints.add(tintColor(tint));
			}
		}
		Map<String, JsonElement> textures = model == null ? Map.of() : chain(model).textures();
		Map<String, String> resolved = resolveRefs(textures);

		// Layers the game stacks or colours (leather armour, potions) only look right put together: a single texture
		// of them would show the uncoloured base.
		if (resolved.containsKey("layer0")
			&& (resolved.containsKey("layer1") || tints.stream().anyMatch(tint -> tint != null))) {
			String url = layeredIcon(id, name, resolved, tints);
			if (url != null) {
				return url;
			}
		}
		// The game's own picture of the item comes before anything we draw.
		String own = "/mc/item/" + name + ".png";
		if (exists(own)) {
			return own;
		}
		if (model != null) {
			String fromTexture = textureIcon(resolved);
			if (fromTexture != null) {
				return fromTexture;
			}
			String cube = cubeIcon(id, name, data, model, textures, tints);
			if (cube != null) {
				return cube;
			}
			// Drawings only where no texture fits: stairs, fences, beds and the like have no picture of their own.
			String drawn = modelIcon(name, data, model, tints);
			if (drawn != null) {
				return drawn;
			}
		}
		// Last resort: a spawn egg texture of the item's name.
		String egg = "/mc/item/" + name + "_spawn_egg.png";
		return exists(egg) ? egg : null;
	}

	/** The model's layer0 texture as the icon, where it is a texture we have (an item named unlike its texture). */
	private String textureIcon(Map<String, String> resolved) {
		String layer0 = resolved.get("layer0");
		String url = layer0 == null ? null : texUrl(layer0);
		return url != null && exists(url) ? url : null;
	}

	/**
	 * A full block as an iso cube of its top, left and right face (animated where a face is), or null if the item isn't
	 * a plain cube or a face is missing. See-through cubes (glass, ice) go to the model renderer first: it shows the
	 * back faces through the front ones as the game does, the flat iso cube can't.
	 */
	private String cubeIcon(String id, String name, JsonObject data, String model, Map<String, JsonElement> textures,
		List<Integer> tints) throws IOException {
		if (!itemModelsAreCube(data)) {
			return null;
		}
		List<String> faces = cubeFaces(textures);
		if (faces == null) {
			return null;
		}
		List<String> paths = new ArrayList<>();
		for (String face : faces) {
			String url = texUrl(face);
			if (url == null || !exists(url)) {
				return null;
			}
			paths.add(url);
		}
		String url = "/mc/iso/" + name + ".png";
		if (paths.stream().anyMatch(this::isAnimated)) {
			writeAnimatedIso(model, paths, tints, file(url));
			animated.add(id);
			return url;
		}
		if (paths.stream().anyMatch(this::seeThrough)) {
			String drawn = modelIcon(name, data, model, tints);
			if (drawn != null) {
				return drawn;
			}
		}
		List<Pixels> tinted = tintCubeFaces(model, paths, tints);
		if (tinted == null) {
			return null;
		}
		Pixels.iso(tinted.get(0), tinted.get(1), tinted.get(2), 16).write(file(url));
		return url;
	}

	/** Blocks without an item that stats still name (mined, used): drawn from their block state's model. */
	static final List<String> BLOCK_MODELS = List.of("frosted_ice", "powder_snow");

	/**
	 * Blocks without an item that the game draws as thin crosses or flat planes: their texture flat, as their pictures
	 * show them (animated where the texture is). Block -> texture.
	 */
	static final Map<String, String> FLAT_BLOCKS = new LinkedHashMap<>();

	static {
		String[] pairs = {"fire", "fire_0", "soul_fire", "soul_fire_0", "cave_vines", "cave_vines_lit",
			"melon_stem", "melon_stem", "pumpkin_stem", "pumpkin_stem", "tripwire", "tripwire",
			"redstone_wire", "redstone_dust_line0"};
		for (int i = 0; i < pairs.length; i += 2) {
			FLAT_BLOCKS.put(pairs[i], pairs[i + 1]);
		}
	}


	/** Redstone dust without power (the game colours it by power: 0.3 red at 0). */
	static final int UNPOWERED_DUST = 0x4C0000;

	/** A fully grown stem's colour (the game colours stems by age). */
	static final int GROWN_STEM = 0xE0C71C;

	/** The colour water has outside special biomes. */
	static final int WATER = 0x3F76E4;

	/**
	 * Icons of blocks without an item: their block state's last variant (the grown or lit one) as a model, water and
	 * lava as animated cubes, the end gateway as a cube of the end portal's stars.
	 */
	private Map<String, String> blocksWithoutItem() throws IOException {
		Map<String, String> icons = new LinkedHashMap<>();
		for (String block : BLOCK_MODELS) {
			// Frosted ice without cracks (its first variant); the others fully grown or lit (their last).
			String model = blockStateModel(block, block.equals("frosted_ice"));
			if (model == null) {
				continue;
			}
			List<Integer> tints = block.endsWith("_stem") ? List.of(GROWN_STEM) : List.of();
			String url = modelIcon(block, new JsonObject(), model, tints);
			if (url != null) {
				icons.put("minecraft:" + block, url);
			}
		}
		for (Map.Entry<String, String> flat : FLAT_BLOCKS.entrySet()) {
			Integer tint = flat.getKey().endsWith("_stem") ? Integer.valueOf(GROWN_STEM)
				: flat.getKey().equals("redstone_wire") ? Integer.valueOf(UNPOWERED_DUST) : null;
			String url = flatIcon(flat.getKey(), "/mc/block/" + flat.getValue() + ".png", tint);
			if (url != null) {
				icons.put("minecraft:" + flat.getKey(), url);
			}
		}
		if (exists("/mc/block/nether_portal.png")) {
			String url = "/mc/iso/nether_portal.png";
			String portal = "/mc/block/nether_portal.png";
			writeAnimatedIso(List.of(portal, portal, portal), java.util.Arrays.asList(null, null, null), file(url));
			icons.put("minecraft:nether_portal", url);
			animated.add("minecraft:nether_portal");
		}
		String[][] liquids = {{"water", "/mc/block/water_still.png"}, {"lava", "/mc/block/lava_still.png"}};
		for (String[] liquid : liquids) {
			if (!exists(liquid[1])) {
				continue;
			}
			Integer tint = liquid[0].equals("water") ? WATER : null;
			String url = "/mc/iso/" + liquid[0] + ".png";
			// Opaque: see-through water over nothing looks almost black next to the other icons.
			opaqueFaces = true;
			try {
				writeAnimatedIso(List.of(liquid[1], liquid[1], liquid[1]), java.util.Arrays.asList(tint, tint, tint), file(url));
			} finally {
				opaqueFaces = false;
			}
			icons.put("minecraft:" + liquid[0], url);
			animated.add("minecraft:" + liquid[0]);
		}
		Pixels bubble = jarPngOrNull("assets/minecraft/textures/particle/bubble.png");
		if (bubble != null) {
			icons.put("minecraft:bubble_column", bubbles(bubble));
			animated.add("minecraft:bubble_column");
		}
		Pixels sky = jarPngOrNull("assets/minecraft/textures/environment/end_sky.png");
		Pixels stars = jarPngOrNull("assets/minecraft/textures/entity/end_portal/end_portal.png");
		if (sky != null && stars != null) {
			// The game's portal shader, ported (EndGateway).
			List<Apng.Frame> frames = EndGateway.frames(sky, stars);
			Apng.write(file("/mc/iso/end_gateway.png"), frames);
			Sizes.animated(file("/mc/iso/end_gateway.png"), frames);
			icons.put("minecraft:end_gateway", "/mc/iso/end_gateway.png");
			animated.add("minecraft:end_gateway");
		}
		return icons;
	}


	/** Water faces drawn opaque (the liquid icons). */
	private boolean opaqueFaces;

	/**
	 * A bubble column: the game's bubble particle three times, rising through the picture and starting over at the
	 * bottom, a pixel per frame.
	 */
	private String bubbles(Pixels bubble) throws IOException {
		int[][] starts = {{5, 4}, {14, 22}, {22, 13}};
		List<Apng.Frame> frames = new ArrayList<>();
		int loop = 32 + bubble.height;
		for (int frame = 0; frame < loop; frame++) {
			Pixels canvas = new Pixels(32, 32);
			for (int[] start : starts) {
				int y = Math.floorMod(start[1] - frame + bubble.height, loop) - bubble.height;
				Scenes.composite(canvas, bubble, start[0], y);
			}
			frames.add(new Apng.Frame(canvas, AnimatedTexture.TICK * 2));
		}
		String url = "/mc/tinted/bubble_column.png";
		Apng.write(file(url), frames);
		Sizes.animated(file(url), frames);
		return url;
	}

	/** A block texture shown flat as an icon (tinted if given), animated like the game where the texture is. */
	private String flatIcon(String name, String texture, Integer tint) throws IOException {
		if (!exists(texture)) {
			return null;
		}
		String url = "/mc/tinted/" + name + ".png";
		AnimatedTexture animation = animation(texture);
		if (animation == null) {
			Pixels still = Pixels.read(file(texture));
			if (name.equals("redstone_wire")) {
				// The texture's line runs top to bottom; its pictures show it lying across.
				Pixels turned = new Pixels(still.height, still.width);
				for (int y = 0; y < still.height; y++) {
					for (int x = 0; x < still.width; x++) {
						turned.set(y, x, still.get(x, y));
					}
				}
				still = turned;
			}
			(tint == null ? still : still.tint(tint)).write(file(url));
			return url;
		}
		int loop = animation.length();
		int step = animation.interpolates() ? Math.max(1, -Math.floorDiv(-loop, MAX_FRAMES)) : 1;
		List<Apng.Frame> frames = new ArrayList<>();
		for (int tick = 0; tick < loop; tick += step) {
			Pixels frame = animation.at(tick);
			frames.add(new Apng.Frame(tint == null ? frame : frame.tint(tint), Math.min(step, loop - tick) * AnimatedTexture.TICK));
		}
		Apng.write(file(url), frames);
		Sizes.animated(file(url), frames);
		animated.add("minecraft:" + name);
		return url;
	}

	/** The model of a block state's first or last variant, or of the first part of a multipart block. */
	private String blockStateModel(String block, boolean first) throws IOException {
		JsonObject state = json("assets/minecraft/blockstates/" + block + ".json");
		if (state == null) {
			return null;
		}
		JsonElement chosen = null;
		if (state.get("variants") instanceof JsonObject variants) {
			for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
				chosen = variant.getValue();
				if (first) {
					break;
				}
			}
		} else if (state.get("multipart") instanceof JsonArray parts && !parts.isEmpty()) {
			chosen = parts.get(0).getAsJsonObject().get("apply");
		}
		if (chosen instanceof JsonArray options && !options.isEmpty()) {
			chosen = options.get(0);
		}
		return chosen instanceof JsonObject variant && variant.has("model") ? variant.get("model").getAsString() : null;
	}

	/** A block model that isn't a plain cube (stairs, fences, beds, ...) drawn by ModelRenderer into iso/. */
	String modelIcon(String name, JsonObject data, String model, List<Integer> tints) throws IOException {
		List<ModelRenderer.Part> parts = new ArrayList<>();
		// An item made of several models side by side (a bed's head and foot): all of them, each where it's put.
		if (data.get("model") instanceof JsonObject root && "composite".equals(type(root))
			&& root.get("models") instanceof JsonArray models) {
			for (JsonElement entry : models) {
				if (!(entry instanceof JsonObject child) || !"model".equals(type(child)) || !child.has("model")) {
					return null;
				}
				List<Integer> childTints = new ArrayList<>();
				if (child.get("tints") instanceof JsonArray list) {
					for (JsonElement tint : list) {
						childTints.add(tintColor(tint));
					}
				}
				ModelRenderer.Part part = modelPart(child.get("model").getAsString(), childTints, child.getAsJsonObject("transformation"));
				if (part == null) {
					return null;
				}
				parts.add(part);
			}
		} else {
			ModelRenderer.Part part = modelPart(model, tints, null);
			if (part == null) {
				return null;
			}
			parts.add(part);
		}
		ModelRenderer.View view = ModelRenderer.View.of(chain(model).gui());
		usedTextures.clear();
		Pixels icon = ModelRenderer.render(parts, view);
		if (icon == null) {
			return null;
		}
		String url = "/mc/iso/" + name + ".png";
		// Textures the game animates (a sculk sensor's tendrils, a stonecutter's saw): a picture per game tick until
		// all of them start over, as for the animated cubes.
		List<AnimatedTexture> animations = new ArrayList<>();
		for (String used : usedTextures) {
			AnimatedTexture animation = animation(used);
			if (animation != null) {
				animations.add(animation);
			}
		}
		if (animations.isEmpty()) {
			icon.write(file(url));
			return url;
		}
		int loop = 1;
		boolean smooth = false;
		for (AnimatedTexture animation : animations) {
			loop = lcm(loop, animation.length());
			smooth |= animation.interpolates();
		}
		int step = smooth ? Math.max(1, -Math.floorDiv(-loop, MAX_FRAMES)) : 1;
		List<Apng.Frame> frames = new ArrayList<>();
		try {
			for (int tick = 0; tick < loop; tick += step) {
				renderTick = tick;
				Pixels frame = ModelRenderer.render(parts, view);
				frames.add(new Apng.Frame(frame == null ? icon : frame, Math.min(step, loop - tick) * AnimatedTexture.TICK));
			}
		} finally {
			renderTick = -1;
		}
		Apng.write(file(url), frames);
		Sizes.animated(file(url), frames);
		animated.add("minecraft:" + name);
		return url;
	}

	/** Whether a texture has half transparent pixels (glass, ice), not only fully clear or solid ones. */
	private boolean seeThrough(String url) {
		try {
			for (int pixel : Pixels.read(file(url)).argb) {
				int alpha = Pixels.alpha(pixel);
				if (alpha > 0 && alpha < 255) {
					return true;
				}
			}
		} catch (IOException e) {
			return false;
		}
		return false;
	}

	/** The game tick a model is drawn at (its animated textures' picture then); -1: their first frame. */
	private int renderTick = -1;
	/** The texture files the model just drawn used. */
	private final Set<String> usedTextures = new java.util.TreeSet<>();
	private final Map<String, AnimatedTexture> animations = new HashMap<>();

	/** A model's elements and textures for ModelRenderer, placed as the item definition's transformation says. */
	private ModelRenderer.Part modelPart(String model, List<Integer> tints, JsonObject transformation) throws IOException {
		JsonArray elements = chain(model).elements();
		if (elements == null || elements.isEmpty()) {
			return null;
		}
		Map<String, String> resolved = resolveRefs(chain(model).textures());
		Map<String, Pixels> loaded = new HashMap<>();
		java.util.function.Function<String, Pixels> textures = variable -> {
			String texture = resolved.getOrDefault(variable, variable.contains("/") ? variable : null);
			String url = texture == null ? null : texUrl(texture);
			if (url == null || !exists(url)) {
				return null;
			}
			usedTextures.add(url);
			if (renderTick >= 0 && isAnimated(url)) {
				AnimatedTexture animation = animations.computeIfAbsent(url, key -> {
					try {
						return animation(key);
					} catch (IOException e) {
						return null;
					}
				});
				if (animation != null) {
					return animation.at(renderTick);
				}
			}
			return loaded.computeIfAbsent(url, key -> {
				try {
					return Pixels.read(file(key));
				} catch (IOException e) {
					return null;
				}
			});
		};
		if (transformation == null) {
			return new ModelRenderer.Part(elements, textures, tints);
		}
		return new ModelRenderer.Part(elements, textures, tints, numbers(transformation.get("translation"), 0, 3),
			numbers(transformation.get("scale"), 1, 3), numbers(transformation.get("left_rotation"), 0, 4, 1),
			numbers(transformation.get("right_rotation"), 0, 4, 1));
	}

	/** A JSON number array, or fallback values (the last one: last) where it's missing. */
	private static double[] numbers(JsonElement value, double fallback, int count) {
		return numbers(value, fallback, count, fallback);
	}

	private static double[] numbers(JsonElement value, double fallback, int count, double last) {
		double[] result = new double[count];
		java.util.Arrays.fill(result, fallback);
		result[count - 1] = last;
		if (value instanceof JsonArray array && array.size() == count) {
			for (int i = 0; i < count; i++) {
				result[i] = array.get(i).getAsDouble();
			}
		}
		return result;
	}


	/**
	 * An iso cube whose faces play their animations together: one loop until all of them start over, a frame per game
	 * tick (fewer for slow ones), tinted like the still cubes.
	 */
	private void writeAnimatedIso(String model, List<String> urls, List<Integer> tints, Path target) throws IOException {
		writeAnimatedIso(urls, faceTints(model, tints), target);
	}

	/** The same with the tint of the top, left and right face given. */
	private void writeAnimatedIso(List<String> urls, List<Integer> faceTints, Path target) throws IOException {
		List<AnimatedTexture> animations = new ArrayList<>();
		List<Pixels> still = new ArrayList<>();
		int loop = 1;
		boolean smooth = false;
		for (String url : urls) {
			AnimatedTexture animation = animation(url);
			animations.add(animation);
			still.add(animation == null ? Pixels.read(file(url)) : null);
			if (animation != null) {
				loop = lcm(loop, animation.length());
				smooth |= animation.interpolates();
			}
		}
		int step = smooth ? Math.max(1, -Math.floorDiv(-loop, MAX_FRAMES)) : 1;
		List<Apng.Frame> frames = new ArrayList<>();
		for (int tick = 0; tick < loop; tick += step) {
			Pixels[] faces = new Pixels[3];
			for (int i = 0; i < 3; i++) {
				Pixels face = animations.get(i) == null ? still.get(i) : animations.get(i).at(tick);
				faces[i] = faceTints.get(i) == null ? face : face.tint(faceTints.get(i));
				if (opaqueFaces) {
					faces[i] = faces[i].opaque();
				}
			}
			frames.add(new Apng.Frame(Pixels.iso(faces[0], faces[1], faces[2], 16),
				Math.min(step, loop - tick) * AnimatedTexture.TICK));
		}
		Apng.write(target, frames);
		Sizes.animated(target, frames);
	}

	/** The tint of the top, left and right face of a single-element cube model (null: none). */
	private List<Integer> faceTints(String model, List<Integer> tints) throws IOException {
		List<Integer> result = new ArrayList<>(java.util.Arrays.asList(null, null, null));
		JsonArray elements = chain(model).elements();
		if (tints.stream().noneMatch(tint -> tint != null) || elements == null || elements.size() != 1) {
			return result;
		}
		JsonObject sides = elements.get(0).getAsJsonObject().getAsJsonObject("faces");
		String[] names = {"up", "south", "east"};
		for (int i = 0; i < 3; i++) {
			JsonObject face = sides == null ? null : sides.getAsJsonObject(names[i]);
			JsonElement index = face == null ? null : face.get("tintindex");
			if (index != null && index.isJsonPrimitive() && index.getAsJsonPrimitive().isNumber()) {
				int at = index.getAsInt();
				result.set(i, at >= 0 && at < tints.size() ? tints.get(at) : null);
			}
		}
		return result;
	}

	private static int lcm(int a, int b) {
		long value = (long) a / gcd(a, b) * b;
		// Textures that never line up again: one loop of the longest is enough to show them.
		return value > 20 * 60 * 10 ? Math.max(a, b) : (int) value;
	}

	private static int gcd(int a, int b) {
		return b == 0 ? a : gcd(b, a % b);
	}

	/** Stacks layer0, layer1, ... like the game's generated item models, tinting layer i with tint i. */
	private String layeredIcon(String id, String name, Map<String, String> resolved, List<Integer> tints) throws IOException {
		Pixels canvas = null;
		for (int index = 0; resolved.containsKey("layer" + index); index++) {
			String url = texUrl(resolved.get("layer" + index));
			if (url == null || !exists(url)) {
				return null;
			}
			if (isAnimated(url)) {
				animated.add(id);
				return null;
			}
			Pixels layer = Pixels.read(file(url));
			Integer color = index < tints.size() ? tints.get(index) : null;
			if (color != null) {
				layer = layer.tint(color);
			}
			if (canvas == null) {
				canvas = layer;
			} else {
				canvas.over(layer);
			}
		}
		if (canvas == null) {
			return null;
		}
		String url = "/mc/layered/" + name + ".png";
		canvas.write(file(url));
		return url;
	}

	/** The top, left and right face textures of a single-element cube, tinted where the model says so. */
	private List<Pixels> tintCubeFaces(String model, List<String> urls, List<Integer> tints) throws IOException {
		List<Pixels> faces = new ArrayList<>();
		for (String url : urls) {
			faces.add(Pixels.read(file(url)));
		}
		JsonArray elements = chain(model).elements();
		if (tints.stream().noneMatch(tint -> tint != null) || elements == null || elements.size() != 1) {
			return faces;
		}
		JsonObject sides = elements.get(0).getAsJsonObject().getAsJsonObject("faces");
		String[] names = {"up", "south", "east"};
		for (int i = 0; i < 3; i++) {
			JsonObject face = sides == null ? null : sides.getAsJsonObject(names[i]);
			JsonElement index = face == null ? null : face.get("tintindex");
			if (index != null && index.isJsonPrimitive() && index.getAsJsonPrimitive().isNumber()) {
				int at = index.getAsInt();
				Integer color = at >= 0 && at < tints.size() ? tints.get(at) : null;
				if (color != null) {
					Pixels tinted = faces.get(i).tint(color);
					String stem = urls.get(i).substring(urls.get(i).lastIndexOf('/') + 1, urls.get(i).length() - 4);
					tinted.write(file(String.format(Locale.ROOT, "/mc/tinted/%s_%06x.png", stem, color)));
					faces.set(i, tinted);
				}
			}
		}
		return faces;
	}

	// --- Icons of our own ----------------------------------------------------------------------------------------

	/**
	 * Adventure's "killed by something": Steve's face with crossed-out eyes, and the per-hour and per-death boards'
	 * icons (a face or a sword with a clock or the face in the corner).
	 */
	private Map<String, String> specials() throws IOException {
		Map<String, String> icons = new LinkedHashMap<>();
		if (experienceOrb()) {
			icons.put("special:experience-orb", "/mc/special/experience_orb.png");
		}
		Pixels skin = jarPng("assets/minecraft/textures/entity/player/wide/steve.png");
		if (skin == null) {
			return icons;
		}
		Pixels face = new Pixels(16, 16);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int hat = skin.get(40 + x / 2, 8 + y / 2);
				face.set(x, y, Pixels.alpha(hat) != 0 ? hat : skin.get(8 + x / 2, 8 + y / 2));
			}
		}
		int cross = 0xFF181818;
		// Steve's eyes are face row 4, columns 1-2 and 5-6: rows 8-9, columns 2-5 and 10-13 at 16x16.
		for (int left : new int[] {2, 10}) {
			for (int y = 8; y <= 9; y++) {
				for (int x = left; x < left + 4; x++) {
					face.set(x, y, face.get(x, 11));
				}
			}
			for (int i = 0; i < 4; i++) {
				face.set(left + i, 7 + i, cross);
				face.set(left + 3 - i, 7 + i, cross);
			}
		}
		face.write(file("/mc/special/player_killed.png"));
		icons.put("special:player-killed", "/mc/special/player_killed.png");
		Pixels clock = jarPng("assets/minecraft/textures/item/clock_00.png");
		Pixels sword = jarPng("assets/minecraft/textures/item/iron_sword.png");
		Object[][] badges = {{"deaths-per-hour", face, clock}, {"kills-per-hour", sword, clock}, {"kills-per-death", sword, face}};
		for (Object[] badge : badges) {
			Pixels base = (Pixels) badge[1];
			Pixels corner = (Pixels) badge[2];
			if (base == null || corner == null) {
				continue;
			}
			Pixels icon = new Pixels(32, 32);
			for (int y = 0; y < 32; y++) {
				for (int x = 0; x < 32; x++) {
					icon.set(x, y, base.get(x / 2, y / 2));
				}
			}
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					if (Pixels.alpha(corner.get(x, y)) != 0) {
						icon.set(16 + x, 16 + y, corner.get(x, y));
					}
				}
			}
			String url = "/mc/special/" + ((String) badge[0]).replace('-', '_') + ".png";
			icon.write(file(url));
			icons.put("special:" + badge[0], url);
		}
		return icons;
	}

	/** Which of the 11 orb sizes (by the orb's value) the icon shows: a middle one, about as tall as the text next to it. */
	private static final int ORB_SIZE = 6;

	/**
	 * The experience orb next to the level, as the game draws it: one orb of its grey sheet, its colour pulsing between
	 * green and yellow (ExperienceOrbRenderer: red (sin(age / 2) + 1) / 2, green full, a little blue), about 0.6 s a
	 * loop. Opaque, where the game draws it half transparent: a page isn't a dark world behind it.
	 */
	// How much slower than in the game the experience orb changes its colour.
	private static final int ORB_SLOWDOWN = 6;

	private boolean experienceOrb() throws IOException {
		Pixels sheet = jarPng("assets/minecraft/textures/entity/experience/experience_orb.png");
		if (sheet == null || sheet.width < 64 || sheet.height < 64) {
			return false;
		}
		Pixels orb = sheet.crop(ORB_SIZE % 4 * 16, ORB_SIZE / 4 * 16, 16, 16);
		// The game's colour cycle (sin(age / 2), 4π ticks, 0.63 s) slowed down ORB_SLOWDOWN times, as the orb sits still
		// next to numbers here; with as many times the frames, so each still shows as briefly and the colour flows as
		// smoothly as in the game.
		int count = 16 * ORB_SLOWDOWN;
		double loop = 4 * Math.PI;
		List<Apng.Frame> frames = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double age = loop * i / count;
			int red = (int) ((Math.sin(age / 2) + 1) * 0.5 * 255);
			int blue = (int) ((Math.sin(age / 2 + 4.1887903) + 1) * 0.1 * 255);
			frames.add(new Apng.Frame(orb.tint(red << 16 | 255 << 8 | blue),
				(int) Math.round(ORB_SLOWDOWN * loop * AnimatedTexture.TICK / count)));
		}
		Apng.write(file("/mc/special/experience_orb.png"), frames);
		Sizes.animated(file("/mc/special/experience_orb.png"), frames);
		return true;
	}

	// --- Models --------------------------------------------------------------------------------------------------

	/** The plain model of an item definition; "fallback"/"on_false" first (leather armour without a trim). */
	static JsonObject primaryModel(JsonElement node) {
		if (node instanceof JsonObject object) {
			if ("model".equals(type(object)) && object.get("model") instanceof JsonPrimitive model && model.isString()) {
				return object;
			}
			for (String key : new String[] {"fallback", "on_false"}) {
				if (object.has(key)) {
					JsonObject found = primaryModel(object.get(key));
					if (found != null) {
						return found;
					}
				}
			}
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				JsonObject found = primaryModel(entry.getValue());
				if (found != null) {
					return found;
				}
			}
		} else if (node instanceof JsonArray array) {
			for (JsonElement value : array) {
				JsonObject found = primaryModel(value);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static String type(JsonObject node) {
		JsonElement type = node.get("type");
		return type != null && type.isJsonPrimitive() ? type.getAsString().replace("minecraft:", "") : "";
	}

	private static void collectModels(JsonElement node, List<String> found) {
		if (node instanceof JsonObject object) {
			if ("model".equals(type(object)) && object.get("model") instanceof JsonPrimitive model && model.isString()) {
				found.add(model.getAsString());
			}
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				collectModels(entry.getValue(), found);
			}
		} else if (node instanceof JsonArray array) {
			for (JsonElement value : array) {
				collectModels(value, found);
			}
		}
	}

	private static boolean hasType(JsonElement node, Set<String> types) {
		if (node instanceof JsonObject object) {
			if (types.contains(type(object))) {
				return true;
			}
			return object.entrySet().stream().anyMatch(entry -> hasType(entry.getValue(), types));
		}
		if (node instanceof JsonArray array) {
			for (JsonElement value : array) {
				if (hasType(value, types)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean itemModelsAreCube(JsonObject data) throws IOException {
		if (hasType(data, Set.of("composite", "special"))) {
			return false;
		}
		List<String> found = new ArrayList<>();
		collectModels(data, found);
		if (found.isEmpty()) {
			return false;
		}
		for (String model : found) {
			if (!isCube(chain(model))) {
				return false;
			}
		}
		return true;
	}


	/**
	 * A model and the models it inherits from ("parent"), nearest first, as Minecraft reads block and item models:
	 * textures add up from the farthest parent to the model, a nearer definition replacing a farther one; elements and
	 * the inventory view come from the nearest model that has them. A parent that is missing, or already in the chain,
	 * ends it.
	 */
	record ModelChain(List<String> ids, List<JsonObject> models) {
		Map<String, JsonElement> textures() {
			Map<String, JsonElement> merged = new LinkedHashMap<>();
			for (JsonObject model : models.reversed()) {
				if (model.get("textures") instanceof JsonObject textures) {
					textures.entrySet().forEach(entry -> merged.put(entry.getKey(), entry.getValue()));
				}
			}
			return merged;
		}

		JsonArray elements() {
			return models.stream().map(model -> model.get("elements")).filter(JsonArray.class::isInstance)
				.map(JsonArray.class::cast).findFirst().orElse(null);
		}

		JsonObject gui() {
			return models.stream().map(model -> model.get("display")).filter(JsonObject.class::isInstance)
				.map(display -> ((JsonObject) display).get("gui")).filter(JsonObject.class::isInstance)
				.map(JsonObject.class::cast).findFirst().orElse(null);
		}
	}

	private final Map<String, ModelChain> chains = new HashMap<>();

	ModelChain chain(String modelId) throws IOException {
		ModelChain known = chains.get(modelId);
		if (known != null) {
			return known;
		}
		List<String> ids = new ArrayList<>();
		List<JsonObject> models = new ArrayList<>();
		for (String id = modelId; id != null && !ids.contains(id); ) {
			ids.add(id);
			JsonObject model = json(modelPath(id));
			if (model == null) {
				break;
			}
			models.add(model);
			id = model.get("parent") instanceof JsonPrimitive parent && parent.isString() ? parent.getAsString() : null;
		}
		ModelChain chain = new ModelChain(List.copyOf(ids), List.copyOf(models));
		chains.put(modelId, chain);
		return chain;
	}

	/** Whether a model is one of the game's full-block templates or built on one (stone, logs, furnaces, leaves). */
	private static boolean isCube(ModelChain chain) {
		return chain.ids().stream().map(id -> id.replace("minecraft:", ""))
			.anyMatch(id -> CUBE_HINTS.stream().anyMatch(id::contains));
	}

	/**
	 * Texture variables followed to the texture they stand for: "#side" names another variable of the same model,
	 * possibly through several; a texture may also be given with options ({"sprite": ...}). Variables that end in
	 * nothing (a missing or circular reference) are left out.
	 */
	static Map<String, String> resolveRefs(Map<String, JsonElement> textures) {
		Map<String, String> named = new LinkedHashMap<>();
		textures.forEach((key, value) -> {
			String texture = textureName(value);
			if (texture != null) {
				named.put(key, texture);
			}
		});
		Map<String, String> resolved = new LinkedHashMap<>();
		for (String key : named.keySet()) {
			String value = named.get(key);
			for (int steps = 0; value != null && value.startsWith("#") && steps < named.size(); steps++) {
				value = named.get(value.substring(1));
			}
			if (value != null && !value.startsWith("#")) {
				resolved.put(key, value);
			}
		}
		return resolved;
	}

	/** A texture entry's name: a plain string, or the sprite of {"sprite": ..., "force_translucent": ...}. */
	private static String textureName(JsonElement value) {
		if (value instanceof JsonObject options) {
			value = options.get("sprite");
		}
		return value instanceof JsonPrimitive name && name.isString() ? name.getAsString() : null;
	}

	/** The variable names the game's cube templates use for each face of the iso cube, most telling first. */
	private static final String[] TOP_NAMES = {"up", "top", "end"};
	private static final String[] LEFT_NAMES = {"south", "front", "north", "side"};
	private static final String[] RIGHT_NAMES = {"east", "west", "side"};
	private static final String[] ALL_NAMES = {"all", "particle", "texture"};

	/**
	 * The textures of the iso cube's top, left and right side, by the variable names of the game's cube templates (a
	 * texture for every side where a side has none of its own); null for flat item models and plants, or if a side
	 * has no texture.
	 */
	static List<String> cubeFaces(Map<String, JsonElement> textures) {
		Map<String, String> resolved = resolveRefs(textures);
		if (resolved.containsKey("layer0") || resolved.containsKey("cross") || resolved.containsKey("plant")) {
			return null;
		}
		String every = named(resolved, ALL_NAMES);
		List<String> faces = new ArrayList<>();
		for (String[] names : new String[][] {TOP_NAMES, LEFT_NAMES, RIGHT_NAMES}) {
			String face = named(resolved, names);
			faces.add(face != null ? face : every);
		}
		return faces.contains(null) ? null : List.copyOf(faces);
	}

	/** The first of these variables with a texture. */
	private static String named(Map<String, String> textures, String... names) {
		for (String name : names) {
			String texture = textures.get(name);
			if (texture != null && !texture.isEmpty()) {
				return texture;
			}
		}
		return null;
	}

	static String modelPath(String modelId) {
		String value = modelId.replace("minecraft:", "");
		if (!value.startsWith("block/") && !value.startsWith("item/")) {
			value = "item/" + value;
		}
		return "assets/minecraft/models/" + value + ".json";
	}







	/** The URL below /mc/ of a model's texture reference ("minecraft:block/stone" -> /mc/block/stone.png). */
	static String texUrl(String texture) {
		String value = texture.replace("minecraft:", "");
		if (value.startsWith("#")) {
			return null;
		}
		if (value.startsWith("item/")) {
			return "/mc/item/" + value.substring(5) + ".png";
		}
		if (value.startsWith("block/")) {
			return "/mc/block/" + value.substring(6) + ".png";
		}
		if (!value.contains("/")) {
			return "/mc/item/" + value + ".png";
		}
		return "/mc/" + value + ".png";
	}

	// --- Tints ---------------------------------------------------------------------------------------------------

	/** RGB of an item tint; tints that depend on the item stack (dye, potion, ...) use their default colour. */
	private Integer tintColor(JsonElement tint) throws IOException {
		if (!(tint instanceof JsonObject object)) {
			return null;
		}
		String kind = type(object);
		if (kind.equals("constant") && isInt(object.get("value"))) {
			return object.get("value").getAsInt() & 0xFFFFFF;
		}
		if (kind.equals("grass")) {
			return grassColor(number(object.get("temperature"), 0.5), number(object.get("downfall"), 1.0));
		}
		return isInt(object.get("default")) ? object.get("default").getAsInt() & 0xFFFFFF : null;
	}

	/** The game's GrassColor.get: a pixel of the grass colour map by temperature and downfall. */
	private Integer grassColor(double temperature, double downfall) throws IOException {
		Pixels map = grassMap.computeIfAbsent("grass", key -> jarPngOrNull("assets/minecraft/textures/colormap/grass.png"));
		if (map == null) {
			return null;
		}
		double t = Math.min(Math.max(temperature, 0.0), 1.0);
		double d = Math.min(Math.max(downfall, 0.0), 1.0) * t;
		int x = (int) ((1.0 - t) * 255);
		int y = (int) ((1.0 - d) * 255);
		if (y >= map.height || x >= map.width) {
			return null;
		}
		return map.get(x, y) & 0xFFFFFF;
	}

	private static boolean isInt(JsonElement value) {
		if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			return false;
		}
		double number = primitive.getAsDouble();
		return number == Math.rint(number) && !primitive.getAsString().contains(".");
	}

	private static double number(JsonElement value, double fallback) {
		return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsDouble() : fallback;
	}

	// --- Files ---------------------------------------------------------------------------------------------------

	private Path file(String url) {
		return out.resolve(url.substring("/mc/".length()));
	}

	private boolean exists(String url) {
		return Files.isRegularFile(file(url));
	}

	/** Whether the texture behind a /mc/item or /mc/block URL is animated in the game (its .mcmeta says so). */
	private boolean isAnimated(String url) {
		JsonObject meta = mcmeta(url);
		return meta != null && meta.has("animation");
	}

	/** The texture's animation, or null if the game shows it still. */
	private AnimatedTexture animation(String url) throws IOException {
		JsonObject meta = mcmeta(url);
		return meta == null ? null : AnimatedTexture.of(Pixels.read(file(url)), meta);
	}

	private JsonObject mcmeta(String url) {
		try {
			return json("assets/minecraft/textures/" + url.substring("/mc/".length()) + ".mcmeta");
		} catch (IOException e) {
			return null;
		}
	}

	private JsonObject json(String path) throws IOException {
		if (models.containsKey(path)) {
			return models.get(path);
		}
		ZipEntry entry = jar.getEntry(path);
		JsonObject data = null;
		if (entry != null) {
			try (InputStream in = jar.getInputStream(entry)) {
				JsonElement parsed = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				data = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
			} catch (RuntimeException e) {
				data = null;
			}
		}
		models.put(path, data);
		return data;
	}

	private Pixels jarPng(String path) throws IOException {
		ZipEntry entry = jar.getEntry(path);
		if (entry == null) {
			return null;
		}
		try (InputStream in = jar.getInputStream(entry)) {
			return Pixels.decode(in.readAllBytes());
		}
	}

	private Pixels jarPngOrNull(String path) {
		try {
			return jarPng(path);
		} catch (IOException e) {
			return null;
		}
	}
}
