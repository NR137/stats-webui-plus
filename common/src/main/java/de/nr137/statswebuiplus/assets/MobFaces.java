package de.nr137.statswebuiplus.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mob faces for the web UI: each mob's head seen from the front with everything attached to it (snout, horns, ears,
 * beak), drawn from the game's model geometry (read by the client JAR's own code, see ClientModels) and the mob's
 * texture in the client JAR. Parts nearer the viewer cover what's behind them; overlay layers (eyes, wool, outer layers) go on top.
 */
public final class MobFaces {
	/** Pixels of an icon: faces are scaled by whole numbers to fit, sharp like the game's textures. */
	static final int SIZE = 32;

	/** One layer of a face: the model layer's geometry, the texture painted on it, and a colour the game tints it with. */
	record Layer(String model, String texture, Integer tint, boolean solid) {
		Layer(String model, String texture) {
			this(model, texture, null, false);
		}

		Layer(String model, String texture, Integer tint) {
			this(model, texture, tint, false);
		}
	}

	/**
	 * How a mob is shown: from the front (its face) or from the side (long heads: horses, fish, ...), and which model
	 * parts: those below a part named in include (default: "head"; all parts if the model has none), without those
	 * below a part named in exclude (a ghast's tentacles).
	 */
	record View(boolean side, Set<String> include, Set<String> exclude, float minX, float maxX, float minY, float maxY) {
		private static final float ALL = Float.MAX_VALUE;
		static final View FRONT = new View(false, Set.of("head"), Set.of());

		View(boolean side, Set<String> include, Set<String> exclude) {
			this(side, include, exclude, -ALL, ALL, -ALL, ALL);
		}

		/** Only what lies inside this frame (model pixels): a llama's head without its neck. */
		View crop(float left, float right, float top, float bottom) {
			return new View(side, include, exclude, left, right, top, bottom);
		}
	}

	static final Map<String, View> VIEWS = new HashMap<>();
	/** Mobs whose outer layer is bigger than the first: the size takes it in (a sulfur cube's shell). */
	static final Set<String> SHELLS = Set.of("sulfur_cube");
	/** Mobs drawn with fewer, bigger pixels (a 16 x 16 grid): a goat's head and body would be busy at icon size. */
	static final Set<String> COARSE = Set.of("goat");
	/**
	 * Where the model's front lets you see inside (model pixels: left, top, right, bottom): its see-through pixels there
	 * show the dark inside (the texture's average colour in shadow), with these parts drawn in it (a shulker's head in
	 * the cut-out of its lid).
	 */
	static final Map<String, float[]> WINDOWS = Map.of("shulker", new float[] {-8, 5, 8, 16});
	static final Map<String, Set<String>> IN_WINDOW = Map.of("shulker", Set.of("head"));
	/**
	 * Notches in a model's front through which its inside shows (model pixels: left, top, right, bottom): a shulker's
	 * base, where the closed lid fits in. Drawn in the model's colour a little in shadow.
	 */
	static final Map<String, float[][]> NOTCHES = Map.of("shulker", new float[][] {{-8, 16, -4, 20}, {4, 16, 8, 20}});
	/**
	 * Where windows and notches take their texture from (texture pixels: x, y, width, height): an opaque part of the
	 * model's texture (a shulker's lid side), repeated, so they show its pixel pattern instead of a flat colour.
	 */
	static final Map<String, int[]> INSIDE_PATTERN = Map.of();
	/** Mobs drawn mirrored (facing right). */
	static final Set<String> MIRRORED = new java.util.HashSet<>();

	private static void side(String... ids) {
		for (String id : ids) {
			VIEWS.put(id, new View(true, Set.of("head"), Set.of()));
		}
	}

	private static void parts(String id, Set<String> include, Set<String> exclude) {
		VIEWS.put(id, new View(false, include, exclude));
	}

	/**
	 * What to draw for each mob, where the plain rule (model "id#main", texture "entity/id/id.png") doesn't hold. The
	 * game names these in code (its renderers), so they are listed here.
	 */
	static final Map<String, List<Layer>> MOBS = new LinkedHashMap<>();

	private static void mob(String id, String... modelAndTexture) {
		List<Layer> layers = new ArrayList<>();
		for (int i = 0; i < modelAndTexture.length; i += 2) {
			layers.add(new Layer(modelAndTexture[i], modelAndTexture[i + 1]));
		}
		MOBS.put(id, layers);
	}

	static {
		mob("cow", "cow#main", "cow/cow_temperate");
		mob("pig", "pig#main", "pig/pig_temperate");
		mob("chicken", "chicken#main", "chicken/chicken_temperate");
		mob("sheep", "sheep#main", "sheep/sheep", "sheep#wool", "sheep/sheep_wool");
		mob("enderman", "enderman#main", "enderman/enderman", "enderman#main", "enderman/enderman_eyes");
		mob("spider", "spider#main", "spider/spider", "spider#main", "spider/spider_eyes");
		mob("cave_spider", "cave_spider#main", "spider/cave_spider", "cave_spider#main", "spider/spider_eyes");
		mob("phantom", "phantom#main", "phantom/phantom", "phantom#main", "phantom/phantom_eyes");
		mob("drowned", "drowned#main", "zombie/drowned", "drowned#outer", "zombie/drowned_outer_layer");
		mob("husk", "husk#main", "zombie/husk");
		mob("zombie", "zombie#main", "zombie/zombie");
		mob("giant", "giant#main", "zombie/zombie");
		mob("zombie_villager", "zombie_villager#main", "zombie_villager/zombie_villager");
		mob("skeleton", "skeleton#main", "skeleton/skeleton");
		mob("stray", "stray#main", "skeleton/stray", "stray#outer", "skeleton/stray_overlay");
		mob("bogged", "bogged#main", "skeleton/bogged", "bogged#outer", "skeleton/bogged_overlay");
		mob("wither_skeleton", "wither_skeleton#main", "skeleton/wither_skeleton");
		mob("creeper", "creeper#main", "creeper/creeper");
		// The outer cube over the inner one as the game blends it, but opaque where nothing is behind it (in the game,
		// the world shows through there; on a page that would look like a hole).
		MOBS.put("slime", List.of(new Layer("slime#main", "slime/slime"), new Layer("slime#outer", "slime/slime", null, true)));
		mob("magma_cube", "magma_cube#main", "slime/magmacube");
		mob("breeze", "breeze#main", "breeze/breeze", "breeze#eyes", "breeze/breeze_eyes");
		mob("creaking", "creaking#main", "creaking/creaking", "creaking#eyes", "creaking/creaking_eyes");
		mob("creaking_transient", "creaking#main", "creaking/creaking", "creaking#eyes", "creaking/creaking_eyes");
		mob("rabbit", "rabbit#main", "rabbit/rabbit_brown");
		mob("killer_bunny", "rabbit#main", "rabbit/rabbit_caerbannog");
		mob("pufferfish", "pufferfish_big#main", "fish/pufferfish");
		// A clownfish: the white base and pattern textures in the colours the game gives this variant (orange, white).
		MOBS.put("tropical_fish", List.of(new Layer("tropical_fish_small#main", "fish/tropical_a", 0xF9801D),
			new Layer("tropical_fish_small#main", "fish/tropical_a_pattern_1", 0xF9FFFE)));
		mob("cod", "cod#main", "fish/cod");
		mob("salmon", "salmon#main", "fish/salmon");
		mob("camel_husk", "camel#main", "camel/camel_husk");
		mob("mannequin", "player_slim#main", "player/slim/alex");
		mob("player", "player#main", "player/wide/steve");
		mob("villager", "villager#main", "villager/villager");
		mob("wandering_trader", "wandering_trader#main", "wandering_trader/wandering_trader");
		mob("illusioner", "illusioner#main", "illager/illusioner");
		mob("evoker", "evoker#main", "illager/evoker");
		mob("pillager", "pillager#main", "illager/pillager");
		mob("vindicator", "vindicator#main", "illager/vindicator");
		mob("witch", "witch#main", "witch/witch");
		mob("horse", "horse#main", "horse/horse_brown");
		mob("donkey", "donkey#main", "horse/donkey");
		mob("mule", "mule#main", "horse/mule");
		mob("skeleton_horse", "skeleton_horse#main", "horse/horse_skeleton");
		mob("zombie_horse", "zombie_horse#main", "horse/horse_zombie");
		mob("llama", "llama#main", "llama/llama_creamy");
		mob("trader_llama", "trader_llama#main", "llama/llama_creamy");
		mob("wolf", "wolf#main", "wolf/wolf");
		mob("cat", "cat#main", "cat/cat_black");
		mob("ocelot", "ocelot#main", "cat/ocelot");
		mob("fox", "fox#main", "fox/fox");
		mob("panda", "panda#main", "panda/panda");
		mob("parrot", "parrot#main", "parrot/parrot_red_blue");
		mob("axolotl", "axolotl#main", "axolotl/axolotl_lucy");
		mob("frog", "frog#main", "frog/frog_temperate");
		mob("bee", "bee#main", "bee/bee");
		mob("goat", "goat#main", "goat/goat");
		mob("mooshroom", "mooshroom#main", "cow/mooshroom_red");
		mob("polar_bear", "polar_bear#main", "bear/polarbear");
		mob("iron_golem", "iron_golem#main", "iron_golem/iron_golem");
		mob("snow_golem", "snow_golem#main", "snow_golem/snow_golem");
		mob("ender_dragon", "ender_dragon#main", "enderdragon/dragon", "ender_dragon#main", "enderdragon/dragon_eyes");
		mob("wither", "wither#main", "wither/wither");
		mob("guardian", "guardian#main", "guardian/guardian");
		mob("elder_guardian", "elder_guardian#main", "guardian/guardian_elder");
		mob("shulker", "shulker#main", "shulker/shulker");
		mob("hoglin", "hoglin#main", "hoglin/hoglin");
		mob("zoglin", "zoglin#main", "hoglin/zoglin");
		mob("piglin", "piglin#main", "piglin/piglin");
		mob("piglin_brute", "piglin_brute#main", "piglin/piglin_brute");
		mob("zombified_piglin", "zombified_piglin#main", "piglin/zombified_piglin");
		mob("strider", "strider#main", "strider/strider");
		mob("ghast", "ghast#main", "ghast/ghast");
		mob("happy_ghast", "happy_ghast#main", "ghast/happy_ghast");
		mob("blaze", "blaze#main", "blaze/blaze");
		mob("vex", "vex#main", "illager/vex");
		mob("allay", "allay#main", "allay/allay");
		mob("warden", "warden#main", "warden/warden");
		mob("sniffer", "sniffer#main", "sniffer/sniffer");
		mob("armadillo", "armadillo#main", "armadillo/armadillo");
		mob("bat", "bat#main", "bat/bat");
		mob("squid", "squid#main", "squid/squid");
		mob("glow_squid", "glow_squid#main", "squid/glow_squid");
		mob("dolphin", "dolphin#main", "dolphin/dolphin");
		mob("turtle", "turtle#main", "turtle/turtle");
		mob("tadpole", "tadpole#main", "tadpole/tadpole");
		mob("endermite", "endermite#main", "endermite/endermite");
		mob("silverfish", "silverfish#main", "silverfish/silverfish");
		mob("ravager", "ravager#main", "illager/ravager");
		mob("zombie_nautilus", "zombie_nautilus#main", "nautilus/zombie_nautilus");
		mob("nautilus", "nautilus#main", "nautilus/nautilus");
		mob("copper_golem", "copper_golem#main", "copper_golem/copper_golem", "copper_golem#main", "copper_golem/copper_golem_eyes");
		// Facing right, as their usual pictures show them: the goat with its head and the front of its body.
		VIEWS.put("goat", new View(true, Set.of(), Set.of()).crop(-17, 0, -3, 14));
		MIRRORED.add("goat");
		MIRRORED.add("skeleton_horse");
		side("sniffer", "turtle", "nautilus", "zombie_nautilus");
		// Horses: the head with its neck and mane (their model's "head_parts"), from the side.
		for (String horse : new String[] {"horse", "donkey", "mule", "skeleton_horse", "zombie_horse"}) {
			VIEWS.put(horse, new View(true, Set.of("head_parts"), Set.of()));
		}
		// Small animals whose head alone is a few pixels: the whole animal from the side.
		for (String whole : new String[] {"parrot", "cod", "salmon", "tropical_fish", "armadillo", "dolphin"}) {
			VIEWS.put(whole, new View(true, Set.of(), Set.of()));
		}
		// A llama's head is one tall box with its neck: only the top (ears, face, snout).
		// Ten rows: exactly three times as big (eleven would need a fraction, which makes pixels uneven).
		VIEWS.put("llama", View.FRONT.crop(-View.ALL, View.ALL, -View.ALL, -2));
		VIEWS.put("trader_llama", View.FRONT.crop(-View.ALL, View.ALL, -View.ALL, -2));
		// A frog's mouth is its body: head and body, without arms, legs and tongue.
		parts("frog", Set.of("body"), Set.of("left_arm", "right_arm", "croaking_body", "tongue"));
		// A shulker's head sits in its shell.
		// A shulker as its pictures show it: the shell open, the head on its base, the dark inside beside it.
		VIEWS.put("shulker", new View(false, Set.of(), Set.of("head")).crop(-8, 8, 5, 21));
		// Villagers without the brim of their profession hat (16 pixels wide), the witch without her tall hat.
		// Cropped to 10.6 texture pixels tall (the nose's tip): exactly three times as big; 11.5 would need a fraction.
		for (String villager : new String[] {"villager", "wandering_trader"}) {
			VIEWS.put(villager, new View(false, Set.of("head"), Set.of("hat_rim")).crop(-View.ALL, View.ALL, -View.ALL, 1.5f));
		}
		VIEWS.put("zombie_villager", new View(false, Set.of("head"), Set.of("hat_rim")).crop(-View.ALL, View.ALL, -View.ALL, 0.1f));
		VIEWS.put("witch", new View(false, Set.of("head"), Set.of("hat")).crop(-View.ALL, View.ALL, -View.ALL, 2));
		VIEWS.put("iron_golem", View.FRONT.crop(-View.ALL, View.ALL, -View.ALL, -8.4f));
		// The copper golem's face, below the antenna on its head.
		VIEWS.put("copper_golem", View.FRONT.crop(-View.ALL, View.ALL, 6, View.ALL));
		// A camel's head part reaches down its neck to the body: the head and the upper neck.
		for (String camel : new String[] {"camel", "camel_husk"}) {
			VIEWS.put(camel, new View(true, Set.of("head"), Set.of()).crop(-View.ALL, View.ALL, -View.ALL, -6));
		}
		// The creaking's head cubes include its branches: the face.
		VIEWS.put("creaking", View.FRONT.crop(-14, 6, -20, -5));
		VIEWS.put("creaking_transient", View.FRONT.crop(-14, 6, -20, -5));
		// The ender dragon's head (16 pixels wide) with the base of its horns and its jaw: 16 x 16, exactly twice.
		VIEWS.put("ender_dragon", View.FRONT.crop(-8, 8, 10, 26));
		// Half its shell's rim (17 instead of 18 pixels): eyes and mouth a little bigger, the rim still showing.
		VIEWS.put("sulfur_cube", View.FRONT.crop(-8.5f, 8.5f, -8.5f, 8.5f));
		parts("ghast", Set.of("body"), Set.of("tentacle"));
		parts("happy_ghast", Set.of("body"), Set.of("tentacle"));
		parts("squid", Set.of("body"), Set.of("tentacle"));
		parts("glow_squid", Set.of("body"), Set.of("tentacle"));
		parts("wither", Set.of("center_head"), Set.of());
		parts("bee", Set.of("body"), Set.of("left_wing", "right_wing", "front_legs", "middle_legs", "back_legs", "stinger"));
		mob("parched", "parched#main", "skeleton/parched");
		// Like the slime: the outer shell opaque where nothing is behind it.
		MOBS.put("sulfur_cube", List.of(new Layer("sulfur_cube#inner", "sulfur_cube/sulfur_cube_inner"),
			new Layer("sulfur_cube#main", "sulfur_cube/sulfur_cube_outer", null, true)));
	}

	private MobFaces() {
	}

	/** The layers of a mob: listed above, or the plain rule. */
	static List<Layer> layers(String id) {
		List<Layer> listed = MOBS.get(id);
		return listed != null ? listed : List.of(new Layer(id + "#main", id + "/" + id));
	}

	/**
	 * The face of these layers, or null if a model or texture is missing. The first layer's head decides the frame;
	 * overlay layers are drawn where they're at least as near as what's there.
	 */
	static Pixels face(ZipFile jar, JsonObject models, List<Layer> layers, View view) throws IOException {
		return face(jar, models, layers, view, "");
	}

	static Pixels face(ZipFile jar, JsonObject models, List<Layer> layers, View view, String id) throws IOException {
		return face(jar, models, layers, view, id, SIZE, false);
	}

	/**
	 * The face on a size x size canvas: a whole number of pixels per texture pixel where that fills the canvas enough
	 * (the 32 pixel icon), or with fill, exactly two pixels per texture pixel (what the UI sizes are scaled from).
	 */
	static Pixels face(ZipFile jar, JsonObject models, List<Layer> layers, View view, String id, int size, boolean fill)
		throws IOException {
		List<List<float[]>> quads = new ArrayList<>();
		List<Pixels> textures = new ArrayList<>();
		for (Layer layer : layers) {
			JsonObject model = models.has(layer.model()) ? models.getAsJsonObject(layer.model()) : null;
			Pixels texture = texture(jar, layer.texture());
			if (texture != null && layer.tint() != null) {
				texture = texture.tint(layer.tint());
			}

			if (model == null || texture == null) {
				return null;
			}
			JsonArray all = model.getAsJsonArray(view.side() ? "side" : "front");
			List<float[]> chosen = quads(all, view.include(), view.exclude());
			if (chosen.isEmpty()) {
				chosen = quads(all, Set.of(), view.exclude());
			}
			quads.add(chosen);
			textures.add(texture);
		}
		List<float[]> inWindow = new ArrayList<>();
		if (IN_WINDOW.containsKey(id)) {
			JsonObject first = models.getAsJsonObject(layers.get(0).model());
			inWindow = quads(first.getAsJsonArray(view.side() ? "side" : "front"), IN_WINDOW.get(id), Set.of());
		}
		if (quads.get(0).isEmpty()) {
			return null;
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		float cut = view.maxY();
		float top = view.minY();
		float left = view.minX();
		float right = view.maxX();
		// The first layer decides; an outer shell around it (a sulfur cube's) counts too, so it isn't cut off.
		List<float[]> all = new ArrayList<>(quads.get(0));
		if (SHELLS.contains(id)) {
			quads.forEach(all::addAll);
		}
		for (float[] quad : all) {
			// Only the part of each face inside the view's frame counts (a camel's neck below its head doesn't).
			List<float[]> inside = clip(quad, left, right, top, cut);
			// A face cut down to a line (lying on the frame's edge) shows nothing.
			if (area(inside) < 0.01f) {
				continue;
			}
			for (float[] corner : inside) {
				minX = Math.min(minX, corner[0]);
				maxX = Math.max(maxX, corner[0]);
				minY = Math.min(minY, corner[1]);
				maxY = Math.max(maxY, corner[1]);
			}
		}
		float width = maxX - minX;
		float height = maxY - minY;
		if (width <= 0 || height <= 0) {
			return null;
		}
		// As big as the icon allows. Slightly enlarged cubes (a squid's body is 16.04 pixels tall) count as their size,
		// so an 8 or 16 pixel head is drawn exactly 4 or 2 times as big.
		// Sizes in texture pixels: models the game scales (a cat to 80 %, a giant 6 times) have texture pixels that
		// aren't one model unit wide.
		float texel = texelSize(quads.get(0), textures.get(0));
		float[] anchor = texelAnchor(quads.get(0), textures.get(0), texel);
		float extent = Math.max(width, height) / texel;
		float rounded = Math.round(extent);
		if (Math.abs(extent - rounded) < 0.1f) {
			extent = rounded;
		}
		// A whole number of icon pixels per texture pixel keeps them all equally wide (a cat's face symmetric); only where
		// that would leave the icon less than 80 % filled (big models; a mule's ears) a fraction, where texture pixels are small enough
		// not to show it.
		float whole = (float) Math.floor(size / extent);
		// Faces seen from the front are symmetric, which uneven pixels would spoil: a whole number already from 65 %.
		float enough = view.side() ? 0.8f : 0.65f;
		float scale = (whole >= 1 && whole * extent >= enough * size ? whole : size / extent) / texel;
		if (fill) {
			// Two pixels per texture pixel (the UI sizes are scaled from this, see sized()): even, and parts set half a
			// texture pixel off the grid (a cat's ears, a hat) keep their place.
			scale = 2 / texel;
		}
		if (Boolean.getBoolean("stats.debugFaces")) {
			System.out.printf("face %s: %.3f x %.3f (from %.3f,%.3f) scale %.3f, %.3f pixels per texture pixel%n",
				layers.get(0).texture(), width, height, minX, minY, scale, scale * texel);
		}
		// Centred on whole pixels, so texture pixel edges fall on pixel edges.
		float offsetX = minX - (float) Math.floor((size - width * scale) / 2) / scale;
		float offsetY = minY - (float) Math.floor((size - height * scale) / 2) / scale;
		// The texture pixels of the model itself (not of a layer half a pixel bigger, like a zombie's hat) start on
		// whole icon pixels, so none is split between two.
		offsetX = anchor[0] - Math.round((anchor[0] - offsetX) * scale) / scale;
		offsetY = anchor[1] - Math.round((anchor[1] - offsetY) * scale) / scale;
		Pixels canvas = new Pixels(size, size);
		float[] depth = new float[size * size];
		java.util.Arrays.fill(depth, Float.MAX_VALUE);
		float[] frame = {left, right, top, cut};
		for (int layer = 0; layer < quads.size(); layer++) {
			boolean overlay = layer > 0;
			for (float[] quad : quads.get(layer)) {
				draw(canvas, depth, quad, textures.get(layer), scale, offsetX, offsetY, overlay, layers.get(layer).solid(), frame);
			}
		}
		float[] window = WINDOWS.get(id);
		int[] pattern = INSIDE_PATTERN.get(id);
		if (window != null) {
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float modelX = offsetX + (x + 0.5f) / scale;
					float modelY = offsetY + (y + 0.5f) / scale;
					if (Pixels.alpha(canvas.get(x, y)) == 0 && modelX > window[0] && modelX < window[2] && modelY > window[1]
						&& modelY < window[3]) {
						canvas.set(x, y, inside(textures.get(0), pattern, modelX, modelY, 0.3));
						depth[y * size + x] = Float.MAX_VALUE;
					}
				}
			}
			for (float[] quad : inWindow) {
				draw(canvas, depth, quad, textures.get(0), scale, offsetX, offsetY, false, false, frame);
			}
		}
		float[][] notches = NOTCHES.get(id);
		if (notches != null) {

			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float modelX = offsetX + (x + 0.5f) / scale;
					float modelY = offsetY + (y + 0.5f) / scale;
					for (float[] notch : notches) {
						if (Pixels.alpha(canvas.get(x, y)) == 0 && modelX > notch[0] && modelX < notch[2] && modelY > notch[1]
							&& modelY < notch[3]) {
							canvas.set(x, y, inside(textures.get(0), pattern, modelX, modelY, 0.75));
						}
					}
				}
			}
		}
		if (id.equals("trader_llama")) {
			traderStraps(jar, canvas, scale, offsetX, offsetY);
		}
		if (COARSE.contains(id) && !fill) {
			canvas = coarse(canvas, 2);
		}
		return canvas;
	}

	/** The quads of the parts below one named in include (all, if include is empty) and none named in exclude. */
	private static List<float[]> quads(JsonArray list, Set<String> include, Set<String> exclude) {
		List<float[]> quads = new ArrayList<>();
		for (JsonElement entry : list) {
			JsonArray values = entry.getAsJsonArray();
			List<String> path = List.of(values.get(0).getAsString().split("/"));
			// Excluded by name or name prefix ("tentacle" also leaves out a happy ghast's tentacle0, tentacle1, ...).
			boolean excluded = path.stream().anyMatch(segment -> exclude.stream().anyMatch(segment::startsWith));
			if ((!include.isEmpty() && path.stream().noneMatch(include::contains)) || excluded) {
				continue;
			}
			float[] quad = new float[values.size() - 1];
			for (int i = 0; i < quad.length; i++) {
				quad[i] = values.get(i + 1).getAsFloat();
			}
			// Faces seen edge-on (a goat's beard is a flat plane) cover nothing, but would count towards the size.
			if (extent(quad, 0) > 0.01f && extent(quad, 1) > 0.01f) {
				quads.add(quad);
			}
		}
		return quads;
	}

	/**
	 * Draws a quad (two triangles) pixel by pixel: each pixel's centre is mapped back into the quad, and the texture's
	 * pixel there is taken as it is (no smoothing), where it's nearer than what's drawn.
	 */
	private static void draw(Pixels canvas, float[] depth, float[] quad, Pixels texture, float scale, float offsetX,
		float offsetY, boolean overlay, boolean solid, float[] frame) {
		int corners = quad.length / 5;
		// Pixels on the edge between the face's triangles belong to both; each is drawn once (twice would blend a
		// see-through layer twice: a line across a slime's face).
		java.util.Set<Integer> drawn = new java.util.HashSet<>();
		for (int t = 1; t + 1 < corners; t++) {
			triangle(canvas, depth, quad, 0, t, t + 1, texture, scale, offsetX, offsetY, overlay, solid, frame, drawn);
		}
	}

	private static void triangle(Pixels canvas, float[] depth, float[] q, int a, int b, int c, Pixels texture,
		float scale, float offsetX, float offsetY, boolean overlay, boolean solid, float[] frame, java.util.Set<Integer> drawn) {
		float ax = (q[a * 5] - offsetX) * scale, ay = (q[a * 5 + 1] - offsetY) * scale;
		float bx = (q[b * 5] - offsetX) * scale, by = (q[b * 5 + 1] - offsetY) * scale;
		float cx = (q[c * 5] - offsetX) * scale, cy = (q[c * 5 + 1] - offsetY) * scale;
		float area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
		if (Math.abs(area) < 1e-6f) {
			return;
		}
		int x0 = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
		int x1 = Math.min(canvas.width - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
		int y0 = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
		int y1 = Math.min(canvas.height - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				float px = x + 0.5f, py = y + 0.5f;
				float modelX = offsetX + px / scale;
				float modelY = offsetY + py / scale;
				if (modelX < frame[0] || modelX > frame[1] || modelY < frame[2] || modelY > frame[3]) {
					continue;
				}
				float wa = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / area;
				float wb = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / area;
				float wc = 1 - wa - wb;
				if (wa < -1e-4f || wb < -1e-4f || wc < -1e-4f) {
					continue;
				}
				float z = wa * q[a * 5 + 2] + wb * q[b * 5 + 2] + wc * q[c * 5 + 2];
				int index = y * canvas.width + x;
				if (drawn.contains(index)) {
					continue;
				}
				if (overlay ? z > depth[index] + 0.01f : z >= depth[index]) {
					continue;
				}
				float u = wa * q[a * 5 + 3] + wb * q[b * 5 + 3] + wc * q[c * 5 + 3];
				float v = wa * q[a * 5 + 4] + wb * q[b * 5 + 4] + wc * q[c * 5 + 4];
				// Inside the face's own texture area: at its edge, rounding would reach the neighbouring texture pixel.
				int tx = clamp((int) Math.floor(u * texture.width), uvMin(q, 3) * texture.width, uvMax(q, 3) * texture.width);
				int ty = clamp((int) Math.floor(v * texture.height), uvMin(q, 4) * texture.height, uvMax(q, 4) * texture.height);
				int pixel = texture.get(tx, ty);
				if (Pixels.alpha(pixel) == 0) {
					continue;
				}
				if (solid && Pixels.alpha(canvas.get(x, y)) == 0) {
					pixel |= 0xFF000000;
				}
				canvas.put(x, y, pixel);
				drawn.add(index);
				if (Pixels.alpha(pixel) == 255 || !overlay) {
					depth[index] = Math.min(depth[index], z);
				}
			}
		}
	}

	/** The face's outline (x, y) clipped to the frame (Sutherland-Hodgman, edge by edge); empty if it lies outside. */
	static List<float[]> clip(float[] quad, float left, float right, float top, float bottom) {
		List<float[]> points = new ArrayList<>();
		for (int i = 0; i < quad.length; i += 5) {
			points.add(new float[] {quad[i], quad[i + 1]});
		}
		float[][] edges = {{0, left, 1}, {0, right, -1}, {1, top, 1}, {1, bottom, -1}};
		for (float[] edge : edges) {
			int axis = (int) edge[0];
			float limit = edge[1];
			float sign = edge[2];
			List<float[]> kept = new ArrayList<>();
			for (int i = 0; i < points.size(); i++) {
				float[] a = points.get(i);
				float[] b = points.get((i + 1) % points.size());
				boolean aIn = (a[axis] - limit) * sign >= 0;
				boolean bIn = (b[axis] - limit) * sign >= 0;
				if (aIn) {
					kept.add(a);
				}
				if (aIn != bIn) {
					float t = (limit - a[axis]) / (b[axis] - a[axis]);
					kept.add(new float[] {a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])});
				}
			}
			points = kept;
			if (points.isEmpty()) {
				break;
			}
		}
		return points;
	}

	/**
	 * Model units per texture pixel: 1 for most models, 0.8 for a cat's, 6 for a giant's.
	 */
	private static float texelSize(List<float[]> quads, Pixels texture) {
		// The ratio that shows the most of the texture: each face counts with its visible (opaque) texture pixels. A
		// zombie's hat (bigger, mostly see-through) or a witch's nose hardly count; the head decides.
		Map<Float, Integer> shown = new HashMap<>();
		for (float[] quad : quads) {
			float pixels = extent(quad, 3) * texture.width;
			if (pixels >= 2) {
				shown.merge(Math.round(extent(quad, 0) / pixels * 1000) / 1000f, opaque(quad, texture), Integer::sum);
			}
		}
		return shown.entrySet().stream()
			.max(java.util.Comparator.<Map.Entry<Float, Integer>>comparingInt(Map.Entry::getValue)
				.thenComparing(entry -> -Math.abs(Math.log(entry.getKey()))))
			.map(Map.Entry::getKey)
			.orElse(1f);
	}

	/** How many opaque texture pixels a face shows. */
	private static int opaque(float[] quad, Pixels texture) {
		int left = (int) Math.floor(uvMin(quad, 3) * texture.width + 0.001f);
		int right = (int) Math.ceil(uvMax(quad, 3) * texture.width - 0.001f);
		int top = (int) Math.floor(uvMin(quad, 4) * texture.height + 0.001f);
		int bottom = (int) Math.ceil(uvMax(quad, 4) * texture.height - 0.001f);
		int count = 0;
		for (int y = Math.max(0, top); y < Math.min(texture.height, bottom); y++) {
			for (int x = Math.max(0, left); x < Math.min(texture.width, right); x++) {
				if (Pixels.alpha(texture.get(x, y)) > 0) {
					count++;
				}
			}
		}
		return count;
	}

	/** Where the model's own texture pixels start (x, y): the corner of a face with the smallest pixel size. */
	private static float[] texelAnchor(List<float[]> quads, Pixels texture, float texel) {
		for (float[] quad : quads) {
			float pixels = extent(quad, 3) * texture.width;
			if (pixels >= 2 && Math.abs(extent(quad, 0) / pixels - texel) < 0.05f) {
				return new float[] {uvMin(quad, 0), uvMin(quad, 1)};
			}
		}
		return new float[] {0, 0};
	}

	/**
	 * The trader llama's straps, which lie on the top and sides of its head (out of sight from the front), painted flat
	 * on the face's top row and outer columns, as its pictures show them.
	 */
	private static void traderStraps(ZipFile jar, Pixels canvas, float scale, float offsetX, float offsetY) throws IOException {
		Pixels decor = texture(jar, "equipment/llama_body/trader_llama");
		if (decor == null || decor.width < 128) {
			return;
		}
		// The decor's head box (texture offset 0, 14; 8 x 18 x 6): its top band (row 18), its left and right sides'
		// strap columns (x 3 and 16, rows 20 on); on the face from x -4 to 4 and y -9 down.
		float unit = decor.width / 128f;
		for (int i = 0; i < 8; i++) {
			paint(canvas, decor.get((int) ((6 + i) * unit), (int) (18 * unit)), -4 + i, -9, scale, offsetX, offsetY);
			// Down to the face's last row (y -3; the picture ends at y -2).
			if (i > 6) {
				continue;
			}
			paint(canvas, decor.get((int) (3 * unit), (int) ((20 + i) * unit)), -4, -9 + i, scale, offsetX, offsetY);
			// The same strap on the right, as on the left (the texture's two sides differ slightly; the picture is symmetric).
			paint(canvas, decor.get((int) (3 * unit), (int) ((20 + i) * unit)), 3, -9 + i, scale, offsetX, offsetY);
		}
	}

	/** One model pixel (x, y) in this colour, where the colour isn't transparent. */
	private static void paint(Pixels canvas, int pixel, int x, int y, float scale, float offsetX, float offsetY) {
		if (Pixels.alpha(pixel) == 0) {
			return;
		}
		// Rounded, not floor and ceiling: 2.9999 is pixel 3, else a model pixel would spill into its neighbour.
		for (int py = Math.round((y - offsetY) * scale); py < Math.round((y + 1 - offsetY) * scale); py++) {
			for (int px = Math.round((x - offsetX) * scale); px < Math.round((x + 1 - offsetX) * scale); px++) {
				if (px >= 0 && py >= 0 && px < canvas.width && py < canvas.height) {
					canvas.set(px, py, pixel);
				}
			}
		}
	}

	/**
	 * The colour of the model's inside at a model pixel: its texture pattern (repeated) or, without one, its average
	 * colour, in shadow by this factor.
	 */
	private static int inside(Pixels texture, int[] pattern, float modelX, float modelY, double light) {
		if (pattern == null) {
			return shade(average(texture), light);
		}
		float unit = texture.width / 64f;
		int x = pattern[0] + Math.floorMod((int) Math.floor(modelX), pattern[2]);
		int y = pattern[1] + Math.floorMod((int) Math.floor(modelY), pattern[3]);
		return shade(texture.get((int) (x * unit), (int) (y * unit)) | 0xFF000000, light);
	}

	/** The average colour of the texture's opaque pixels. */
	private static int average(Pixels texture) {
		long r = 0, g = 0, b = 0, n = 0;
		for (int pixel : texture.argb) {
			if (Pixels.alpha(pixel) > 0) {
				r += pixel >> 16 & 255;
				g += pixel >> 8 & 255;
				b += pixel & 255;
				n++;
			}
		}
		return n == 0 ? 0xFF000000 : 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
	}

	private static int shade(int pixel, double factor) {
		return Pixels.shade(pixel, factor);
	}

	/** Fewer, bigger pixels: each block x block square becomes its most common colour (transparent if most are). */
	static Pixels coarse(Pixels canvas, int block) {
		Pixels out = new Pixels(canvas.width, canvas.height);
		for (int by = 0; by < canvas.height; by += block) {
			for (int bx = 0; bx < canvas.width; bx += block) {
				Map<Integer, Integer> counts = new HashMap<>();
				for (int y = by; y < Math.min(canvas.height, by + block); y++) {
					for (int x = bx; x < Math.min(canvas.width, bx + block); x++) {
						int pixel = canvas.get(x, y);
						counts.merge(Pixels.alpha(pixel) == 0 ? 0 : pixel, 1, Integer::sum);
					}
				}
				int common = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
				for (int y = by; y < Math.min(canvas.height, by + block); y++) {
					for (int x = bx; x < Math.min(canvas.width, bx + block); x++) {
						out.set(x, y, common);
					}
				}
			}
		}
		return out;
	}

	/** The area of a polygon (shoelace formula). */
	private static float area(List<float[]> points) {
		float sum = 0;
		for (int i = 0; i < points.size(); i++) {
			float[] a = points.get(i);
			float[] b = points.get((i + 1) % points.size());
			sum += a[0] * b[1] - b[0] * a[1];
		}
		return Math.abs(sum) / 2;
	}

	private static float extent(float[] quad, int offset) {
		return uvMax(quad, offset) - uvMin(quad, offset);
	}

	private static float uvMin(float[] quad, int offset) {
		float min = Float.MAX_VALUE;
		for (int i = offset; i < quad.length; i += 5) {
			min = Math.min(min, quad[i]);
		}
		return min;
	}

	private static float uvMax(float[] quad, int offset) {
		float max = -Float.MAX_VALUE;
		for (int i = offset; i < quad.length; i += 5) {
			max = Math.max(max, quad[i]);
		}
		return max;
	}

	/** value within [floor(min + 0.5), ceil(max - 0.5) - 1]: the texture pixels the area covers. */
	private static int clamp(int value, float min, float max) {
		int low = (int) Math.floor(min + 0.001f);
		int high = Math.max(low, (int) Math.ceil(max - 0.001f) - 1);
		return Math.max(low, Math.min(high, value));
	}

	private static Pixels texture(ZipFile jar, String name) throws IOException {
		ZipEntry entry = jar.getEntry("assets/minecraft/textures/entity/" + name + ".png");
		if (entry == null) {
			return null;
		}
		try (InputStream in = jar.getInputStream(entry)) {
			return Pixels.decode(in.readAllBytes());
		}
	}

	/**
	 * The snow golem's carved pumpkin, which the game draws as a block on its head (not part of the model): the block's
	 * face over the head, a little bigger than it, as the game shows it.
	 */
	private static void pumpkin(ZipFile jar, Pixels face) throws IOException {
		ZipEntry entry = jar.getEntry("assets/minecraft/textures/block/carved_pumpkin.png");
		if (entry == null) {
			return;
		}
		Pixels block;
		try (InputStream in = jar.getInputStream(entry)) {
			block = Pixels.decode(in.readAllBytes());
		}
		Pixels scaled = block.scale(face.width, face.height);
		for (int y = 0; y < face.height; y++) {
			for (int x = 0; x < face.width; x++) {
				face.put(x, y, scaled.get(x, y));
			}
		}
	}

	/**
	 * The face without its transparent margin, so every icon fills its box in the web UI (which fits it in without
	 * stretching): a face drawn 24 pixels wide in a 32 pixel canvas would otherwise look smaller than its neighbours.
	 */
	static Pixels trim(Pixels face) {
		int left = face.width, top = face.height, right = -1, bottom = -1;
		for (int y = 0; y < face.height; y++) {
			for (int x = 0; x < face.width; x++) {
				if (Pixels.alpha(face.get(x, y)) > 0) {
					left = Math.min(left, x);
					right = Math.max(right, x);
					top = Math.min(top, y);
					bottom = Math.max(bottom, y);
				}
			}
		}
		return right < 0 ? face : face.crop(left, top, right - left + 1, bottom - top + 1);
	}

	private static Pixels mirror(Pixels face) {
		Pixels out = new Pixels(face.width, face.height);
		for (int y = 0; y < face.height; y++) {
			for (int x = 0; x < face.width; x++) {
				out.set(face.width - 1 - x, y, face.get(x, y));
			}
		}
		return out;
	}


	/**
	 * The face at one of the UI's sizes: drawn at two pixels per texture pixel (even and, for a symmetric face,
	 * symmetric), then scaled into the box by box(), with the same finishing touches as the 32 pixel icon.
	 */
	private static Pixels sized(ZipFile jar, JsonObject models, List<Layer> layers, View view, String id, int size)
		throws IOException {
		Pixels face = face(jar, models, layers, view, id, 128, true);
		if (face == null) {
			return null;
		}
		if (id.equals("snow_golem")) {
			pumpkin(jar, face);
		}
		if (MIRRORED.contains(id)) {
			face = mirror(face);
		}
		return Sizes.box(trim(face), size);
	}

	/** Mobs without a spawn egg that stats can still name. */
	static final List<String> EXTRA_MOBS = List.of("giant", "illusioner", "player", "killer_bunny", "mannequin");

	/** Every mob of the game: one per spawn egg, and the extras. */
	static List<String> mobs(ZipFile jar) {
		List<String> ids = new ArrayList<>();
		jar.stream().map(ZipEntry::getName)
			.filter(name -> name.startsWith("assets/minecraft/items/") && name.endsWith("_spawn_egg.json"))
			.map(name -> name.substring("assets/minecraft/items/".length(), name.length() - "_spawn_egg.json".length()))
			.sorted()
			.forEach(ids::add);
		for (String extra : EXTRA_MOBS) {
			if (!ids.contains(extra)) {
				ids.add(extra);
			}
		}
		return ids;
	}

	/** Draws the faces of these mobs into out/face/&lt;id&gt;.png; returns ID -> URL and the ones that failed. */
	static Map<String, String> render(Path clientJar, Path out, List<String> failed) throws IOException {
		Map<String, String> icons = new LinkedHashMap<>();
		try (ZipFile jar = new ZipFile(clientJar.toFile())) {
			List<String> ids = mobs(jar);
			Set<String> wanted = new java.util.TreeSet<>();
			for (String id : ids) {
				layers(id).forEach(layer -> wanted.add(layer.model()));
			}
			JsonObject models = JsonParser.parseString(ClientClasses.models(clientJar, wanted)).getAsJsonObject();
			for (String id : ids) {
				Pixels face = face(jar, models, layers(id), VIEWS.getOrDefault(id, View.FRONT), id);
				if (face != null && id.equals("snow_golem")) {
					pumpkin(jar, face);
				}
				if (face != null && MIRRORED.contains(id)) {
					face = mirror(face);
				}
				if (face == null) {
					failed.add(id);
					continue;
				}
				String url = "/mc/face/" + id + ".png";
				trim(face).write(out.resolve("face/" + id + ".png"));
				for (int size : Sizes.UI) {
					Pixels sized = sized(jar, models, layers(id), VIEWS.getOrDefault(id, View.FRONT), id, size);
					if (sized != null) {
						sized.write(out.resolve("face/" + size + "/" + id + ".png"));
					}
				}
				icons.put("entity:minecraft:" + id, url);
			}
			icons.putAll(variants(jar, models, out, failed));
		}
		return icons;
	}

	/**
	 * Cat, wolf and frog variants (A Complete Catalogue, The Whole Pack, ...): the mob's face with each variant's
	 * texture, as the game's data names it (data/minecraft/cat_variant/tabby.json: "asset_id"). Under
	 * "variant:&lt;mob&gt;:minecraft:&lt;variant&gt;".
	 */
	private static Map<String, String> variants(ZipFile jar, JsonObject models, Path out, List<String> failed)
		throws IOException {
		Map<String, String> icons = new LinkedHashMap<>();
		for (String mob : new String[] {"cat", "wolf", "frog"}) {
			String folder = "data/minecraft/" + mob + "_variant/";
			List<String> entries = jar.stream().map(ZipEntry::getName)
				.filter(name -> name.startsWith(folder) && name.endsWith(".json")).sorted().toList();
			for (String entry : entries) {
				String variant = entry.substring(folder.length(), entry.length() - 5);
				JsonObject data;
				try (InputStream in = jar.getInputStream(jar.getEntry(entry))) {
					data = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				}
				String asset = data.has("asset_id") ? data.get("asset_id").getAsString()
					: data.has("assets") ? data.getAsJsonObject("assets").get("wild").getAsString() : null;
				if (asset == null || !asset.startsWith("minecraft:entity/")) {
					failed.add(mob + ":" + variant);
					continue;
				}
				List<Layer> layers = new ArrayList<>(layers(mob));
				layers.set(0, new Layer(layers.get(0).model(), asset.substring("minecraft:entity/".length())));
				Pixels face = face(jar, models, layers, VIEWS.getOrDefault(mob, View.FRONT));
				if (face == null) {
					failed.add(mob + ":" + variant);
					continue;
				}
				String file = "face/" + mob + "_" + variant + ".png";
				trim(face).write(out.resolve(file));
				for (int size : Sizes.UI) {
					Pixels sized = sized(jar, models, layers, VIEWS.getOrDefault(mob, View.FRONT), mob, size);
					if (sized != null) {
						sized.write(out.resolve("face/" + size + "/" + mob + "_" + variant + ".png"));
					}
				}
				icons.put("variant:" + mob + ":minecraft:" + variant, "/mc/" + file);
			}
		}
		return icons;
	}
}
