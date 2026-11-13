package de.nr137.statswebuiplus.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Biome and structure icons (Adventuring Time, the structure advancements): a small 32 x 32 scene seen from the
 * side, drawn from the game's textures and each biome's colours (sky, grass, leaves, water). The sky gets lighter
 * towards the ground, the ground is a row of its blocks' sides, and in front stand the biome's plants, trees or a
 * building, each at the texture's own pixel size.
 */
final class Scenes {
	/** The picture's size. */
	static final int SIZE = 32;

	/** Something drawn behind the plants and before the ground: a tree, a building, a column of ice. */
	@FunctionalInterface
	interface Part {
		void draw(Scenes scenes, Pixels canvas, int groundY, Colors colors) throws IOException;
	}

	/** A biome's colours, from its data (or the game's colour maps where it has none of its own). */
	record Colors(int grass, int foliage, int water, Integer sky, Integer fog) {
	}

	/** What fills the picture above the ground. */
	enum Background {
		/** The biome's sky colour, lighter further down. */
		SKY,
		/** The biome's fog colour (the Nether), lighter further down. */
		FOG,
		/** Under water: the water colour, darker. */
		WATER,
		/** The End's dark sky. */
		END,
		/** Nothing but the cave texture. */
		NONE
	}

	/** The End has no sky colour of its own; its sky is a dark grey-purple texture. */
	static final int END_SKY = 0x17121F;

	/**
	 * One scene: the biome whose colours it uses, the background (darkened by a factor under water), a cave texture
	 * behind it (or null), the ground and where it starts, what stands behind the plants, the plants (texture and x)
	 * and what's drawn last, over the ground (water, ice).
	 */
	static final class Scene {
		final String biome;
		Background background = Background.SKY;
		double darken;
		String cave;
		Ground ground = Ground.GRASS;
		int groundY = 22;
		final List<Part> back = new ArrayList<>();
		final List<Deco> deco = new ArrayList<>();
		final List<Part> front = new ArrayList<>();

		Scene(String biome) {
			this.biome = biome;
		}

		Scene background(Background value) {
			background = value;
			return this;
		}

		Scene water(double factor) {
			background = Background.WATER;
			darken = factor;
			return this;
		}

		Scene cave(String texture) {
			background = Background.NONE;
			cave = texture;
			return this;
		}

		Scene ground(Ground value) {
			ground = value;
			return this;
		}

		Scene groundY(int y) {
			groundY = y;
			return this;
		}

		Scene back(Part... parts) {
			back.addAll(List.of(parts));
			return this;
		}

		Scene deco(Object... textureAndX) {
			for (int i = 0; i < textureAndX.length; i += 2) {
				deco.add(new Deco((String) textureAndX[i], (Integer) textureAndX[i + 1]));
			}
			return this;
		}

		Scene front(Part... parts) {
			front.addAll(List.of(parts));
			return this;
		}
	}

	/** A plant standing on the ground, its texture at full size, x from the left. */
	record Deco(String texture, int x) {
	}

	/** The ground's look: the block side the row of ground shows. */
	enum Ground {
		GRASS, SAND, SNOW, MYCELIUM, SCULK, WARPED, BLACKSTONE, PALE_MOSS, GRAVEL, STONE, RED_SAND, PODZOL, MUD, MOSS,
		DRIPSTONE, NETHERRACK, CRIMSON, SOUL_SAND, BASALT, END_STONE, SULFUR, WATER, NONE
	}

	private final ZipFile jar;
	private final Map<String, Pixels> textures = new HashMap<>();

	private Scenes(ZipFile jar) {
		this.jar = jar;
	}

	/** Draws every scene into out/scene/; returns icon key -> URL. */
	static Map<String, String> render(Path clientJar, Path out) throws IOException {
		Map<String, String> icons = new LinkedHashMap<>();
		try (ZipFile jar = new ZipFile(clientJar.toFile())) {
			Scenes scenes = new Scenes(jar);
			for (Map.Entry<String, Scene> biome : BIOMES.entrySet()) {
				if (jar.getEntry("data/minecraft/worldgen/biome/" + biome.getKey() + ".json") == null) {
					continue;
				}
				String url = "/mc/scene/" + biome.getKey() + ".png";
				scenes.draw(biome.getValue()).write(out.resolve("scene/" + biome.getKey() + ".png"));
				icons.put("biome:minecraft:" + biome.getKey(), url);
			}
			for (Map.Entry<String, Scene> structure : STRUCTURES.entrySet()) {
				if (jar.getEntry("data/minecraft/worldgen/biome/" + structure.getValue().biome + ".json") == null) {
					continue;
				}
				String file = structure.getKey().replace('-', '_');
				String url = "/mc/scene/" + file + ".png";
				scenes.draw(structure.getValue()).write(out.resolve("scene/" + file + ".png"));
				icons.put("structure:" + structure.getKey(), url);
			}
		}
		return icons;
	}

	// --- Drawing -------------------------------------------------------------------------------------------------

	Pixels draw(Scene scene) throws IOException {
		Colors colors = colors(scene.biome);
		Pixels canvas = new Pixels(SIZE, SIZE);
		if (scene.cave != null) {
			Pixels cave = block(scene.cave);
			for (int y = 0; y < SIZE; y++) {
				double factor = 0.35 + 0.25 * ((double) y / SIZE);
				for (int x = 0; x < SIZE; x++) {
					canvas.set(x, y, 0xFF000000 | Pixels.shade(cave.get(x % 16, y % 16), factor) & 0xFFFFFF);
				}
			}
		}
		Integer top = switch (scene.background) {
			case SKY -> colors.sky();
			case FOG -> colors.fog();
			case WATER -> darker(colors.water(), scene.darken);
			case END -> END_SKY;
			case NONE -> null;
		};
		if (top != null) {
			boolean lighter = scene.background == Background.SKY;
			for (int y = 0; y < SIZE; y++) {
				double k = (double) y / SIZE * 0.35;
				int pixel = 0xFF000000;
				for (int shift = 16; shift >= 0; shift -= 8) {
					int v = top >> shift & 255;
					pixel |= Math.min(255, lighter ? (int) (v + (255 - v) * k) : (int) (v * (1 + k))) << shift;
				}
				for (int x = 0; x < SIZE; x++) {
					canvas.set(x, y, pixel);
				}
			}
		}
		int groundY = scene.groundY;
		for (Part part : scene.back) {
			part.draw(this, canvas, groundY, colors);
		}
		if (scene.ground != Ground.NONE) {
			Pixels side = groundSide(scene.ground, colors);
			for (int x = 0; x < SIZE; x += 16) {
				composite(canvas, side.crop(0, 0, 16, SIZE - groundY), x, groundY);
			}
		}
		for (Deco deco : scene.deco) {
			composite(canvas, sprite(deco.texture(), colors), deco.x(), groundY - 16);
		}
		for (Part part : scene.front) {
			part.draw(this, canvas, groundY, colors);
		}
		return canvas;
	}

	private static int darker(int rgb, double factor) {
		return (int) ((rgb >> 16 & 255) * factor) << 16 | (int) ((rgb >> 8 & 255) * factor) << 8
			| (int) ((rgb & 255) * factor);
	}

	private Pixels groundSide(Ground ground, Colors colors) throws IOException {
		return switch (ground) {
			case GRASS -> grassSide(colors.grass());
			case SAND -> block("sand");
			case SNOW -> block("grass_block_snow");
			case MYCELIUM -> block("mycelium_side");
			case SCULK -> block("sculk");
			case WARPED -> block("warped_nylium_side");
			case BLACKSTONE -> block("blackstone");
			case PALE_MOSS -> block("pale_moss_block");
			case GRAVEL -> block("gravel");
			case STONE -> block("stone");
			case RED_SAND -> block("red_sand");
			case PODZOL -> block("podzol_side");
			case MUD -> block("mud");
			case MOSS -> block("moss_block");
			case DRIPSTONE -> block("dripstone_block");
			case NETHERRACK -> block("netherrack");
			case CRIMSON -> block("crimson_nylium_side");
			case SOUL_SAND -> block("soul_sand");
			case BASALT -> block("basalt_side");
			case END_STONE -> block("end_stone");
			case SULFUR -> block("sulfur");
			case WATER -> water(colors);
			case NONE -> new Pixels(16, 16);
		};
	}

	/** Water as the game tints it: its grey texture in the biome's water colour, over a darker shade of it. */
	Pixels water(Colors colors) throws IOException {
		Pixels water = new Pixels(16, 16);
		java.util.Arrays.fill(water.argb, 0xFF000000 | darker(colors.water(), 0.75));
		composite(water, block("water_still").tint(colors.water()), 0, 0);
		return water;
	}

	/** A texture by name, or "@grass" for the top of a grass block, "@water" for water, in the biome's colours. */
	Pixels texture(String name, Colors colors) throws IOException {
		return switch (name) {
			case "@grass" -> block("grass_block_top").tint(colors.grass());
			case "@water" -> water(colors);
			default -> sprite(name, colors);
		};
	}

	private Pixels grassSide(int grass) throws IOException {
		Pixels side = copy(block("grass_block_side"));
		composite(side, block("grass_block_side_overlay").tint(grass), 0, 0);
		return side;
	}

	/** A plant's texture; grass and ferns in the biome's grass colour, leaves in its foliage colour. */
	Pixels sprite(String name, Colors colors) throws IOException {
		Pixels texture = block(name);
		return switch (name) {
			case "short_grass", "fern", "tall_grass_bottom", "tall_grass_top", "sugar_cane" -> texture.tint(colors.grass());
			case "jungle_leaves", "oak_leaves", "acacia_leaves", "dark_oak_leaves", "vine" -> texture.tint(colors.foliage());
			// Leaves the game colours alike everywhere.
			case "birch_leaves" -> texture.tint(0x80A755);
			case "spruce_leaves" -> texture.tint(0x619961);
			case "mangrove_leaves" -> texture.tint(0x92C648);
			default -> texture;
		};
	}

	/** Any texture below textures/ ("entity/end_portal/end_portal"). */
	Pixels texture(String path) throws IOException {
		return block("../" + path);
	}

	/** A block texture (an animated one's first frame). */
	Pixels block(String name) throws IOException {
		Pixels cached = textures.get(name);
		if (cached != null) {
			return cached;
		}
		String path = name.startsWith("../") ? "assets/minecraft/textures/" + name.substring(3) + ".png"
			: "assets/minecraft/textures/block/" + name + ".png";
		ZipEntry entry = jar.getEntry(path);
		if (entry == null) {
			throw new IOException("No texture " + path);
		}
		Pixels texture;
		try (InputStream in = jar.getInputStream(entry)) {
			texture = Pixels.decode(in.readAllBytes());
		}
		if (texture.height > texture.width) {
			texture = texture.crop(0, 0, texture.width, texture.width);
		}
		textures.put(name, texture);
		return texture;
	}

	// --- Biome colours -------------------------------------------------------------------------------------------

	Colors colors(String biome) throws IOException {
		JsonObject data;
		try (InputStream in = jar.getInputStream(jar.getEntry("data/minecraft/worldgen/biome/" + biome + ".json"))) {
			data = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		JsonObject effects = data.has("effects") ? data.getAsJsonObject("effects") : new JsonObject();
		JsonObject attributes = data.has("attributes") ? data.getAsJsonObject("attributes") : new JsonObject();
		double temperature = data.has("temperature") ? data.get("temperature").getAsDouble() : 0.5;
		double downfall = data.has("downfall") ? data.get("downfall").getAsDouble() : 0.5;
		int grass = effects.has("grass_color") ? hex(effects.get("grass_color").getAsString())
			: colormap("grass", temperature, downfall);
		String modifier = effects.has("grass_color_modifier") ? effects.get("grass_color_modifier").getAsString() : "";
		if (modifier.equals("dark_forest")) {
			grass = ((grass & 0xFEFEFE) + 0x28340A) >> 1;
		} else if (modifier.equals("swamp")) {
			// The game picks one of two by noise; the more common one.
			grass = 0x6A7039;
		}
		int foliage = effects.has("foliage_color") ? hex(effects.get("foliage_color").getAsString())
			: colormap("foliage", temperature, downfall);
		int water = effects.has("water_color") ? hex(effects.get("water_color").getAsString()) : 0x3F76E4;
		return new Colors(grass, foliage, water, color(attributes, "minecraft:visual/sky_color"),
			color(attributes, "minecraft:visual/fog_color"));
	}

	private static Integer color(JsonObject attributes, String key) {
		return attributes.get(key) instanceof com.google.gson.JsonPrimitive value && value.isString()
			? hex(value.getAsString()) : null;
	}

	private static int hex(String color) {
		return Integer.parseInt(color.substring(color.startsWith("#") ? 1 : 0), 16);
	}

	/** The game's colour map for grass or leaves at this temperature and downfall. */
	private int colormap(String name, double temperature, double downfall) throws IOException {
		Pixels map;
		try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/colormap/" + name + ".png"))) {
			map = Pixels.decode(in.readAllBytes());
		}
		double t = Math.min(Math.max(temperature, 0), 1);
		double d = Math.min(Math.max(downfall, 0), 1) * t;
		return map.get((int) ((1 - t) * 255), (int) ((1 - d) * 255)) & 0xFFFFFF;
	}

	// --- Pixel helpers (as the Python prototype drew them with Pillow, so both give the same pixels) ---------------

	static Pixels copy(Pixels pixels) {
		return pixels.crop(0, 0, pixels.width, pixels.height);
	}

	/** Pillow's nearest-neighbour resize: the source position added up step by step, as Pillow does. */
	static Pixels resize(Pixels source, int width, int height) {
		Pixels out = new Pixels(width, height);
		int[] xs = steps(source.width, width);
		int[] ys = steps(source.height, height);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				out.set(x, y, source.get(xs[x], ys[y]));
			}
		}
		return out;
	}

	private static int[] steps(int from, int to) {
		double scale = (double) from / to;
		double position = scale * 0.5;
		int[] picks = new int[to];
		for (int i = 0; i < to; i++) {
			picks[i] = Math.min(from - 1, (int) Math.floor(position));
			position += scale;
		}
		return picks;
	}

	/** Draws layer over canvas at x, y ("source over" in Pillow's whole-number maths); what falls outside is cut. */
	static void composite(Pixels canvas, Pixels layer, int left, int top) {
		for (int y = 0; y < layer.height; y++) {
			for (int x = 0; x < layer.width; x++) {
				int cx = left + x;
				int cy = top + y;
				if (cx >= 0 && cy >= 0 && cx < canvas.width && cy < canvas.height) {
					canvas.set(cx, cy, over(canvas.get(cx, cy), layer.get(x, y)));
				}
			}
		}
	}

	private static final int PRECISION = 7;

	/** One pixel over another, exactly as Pillow's alpha_composite. */
	static int over(int dst, int src) {
		int sa = src >>> 24;
		if (sa == 0) {
			return dst;
		}
		long blend = (long) (dst >>> 24) * (255 - sa);
		long outA = sa * 255L + blend;
		long coef1 = sa * 255L * 255L * (1 << PRECISION) / outA;
		long coef2 = 255L * (1 << PRECISION) - coef1;
		int pixel = (int) div255(outA + 0x80) << 24;
		for (int shift = 16; shift >= 0; shift -= 8) {
			long mixed = (src >> shift & 255) * coef1 + (dst >> shift & 255) * coef2 + (0x80L << PRECISION);
			pixel |= (int) (div255(mixed) >> PRECISION) << shift;
		}
		return pixel;
	}

	private static long div255(long value) {
		return ((value >> 8) + value) >> 8;
	}

	/** The colour of a texture's opaque pixels on average (whole numbers, cut off). */
	static int average(Pixels texture) {
		long r = 0, g = 0, b = 0;
		int count = 0;
		for (int pixel : texture.argb) {
			if (pixel >>> 24 != 0) {
				r += pixel >> 16 & 255;
				g += pixel >> 8 & 255;
				b += pixel & 255;
				count++;
			}
		}
		return count == 0 ? 0 : (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
	}

	// --- Parts ---------------------------------------------------------------------------------------------------

	/** A plain tree: a 6 pixel wide trunk, the leaves texture as its crown with the corners cut. */
	static Part tree(String log, String leaves, int x, int trunk, int crown) {
		return tree(log, leaves, x, trunk, crown, false);
	}

	/**
	 * The same; dense: the leaves' see-through gaps filled with a darker shade of their colour, for leaves whose
	 * texture is mostly gaps (birch).
	 */
	static Part tree(String log, String leaves, int x, int trunk, int crown, boolean dense) {
		return (scenes, canvas, groundY, colors) -> {
			composite(canvas, scenes.block(log).crop(5, 0, 6, trunk), x + 5, groundY - trunk);
			Pixels crownPixels = new Pixels(16, crown);
			Pixels leafTexture = scenes.sprite(leaves, colors);
			composite(crownPixels, leafTexture.crop(0, 0, 16, crown), 0, 0);
			if (dense) {
				// Gaps with leaves on at least three of their four sides get a darker shade of the leaves; bigger
				// gaps stay open, so some air remains.
				int shade = 0xFF000000 | darker(average(leafTexture), 0.8);
				Pixels before = copy(crownPixels);
				for (int y = 0; y < crown; y++) {
					for (int cx = 0; cx < 16; cx++) {
						if (before.get(cx, y) >>> 24 != 0) {
							continue;
						}
						int around = 0;
						for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							int nx = cx + d[0];
							int ny = y + d[1];
							if (nx >= 0 && ny >= 0 && nx < 16 && ny < crown && before.get(nx, ny) >>> 24 != 0) {
								around++;
							}
						}
						if (around >= 3) {
							crownPixels.set(cx, y, shade);
						}
					}
				}
			}
			int[][] corners = {{0, 0}, {1, 0}, {0, 1}, {15, 0}, {14, 0}, {15, 1}, {0, crown - 1}, {15, crown - 1}};
			for (int[] corner : corners) {
				crownPixels.set(corner[0], corner[1], 0);
			}
			composite(canvas, crownPixels, x, groundY - trunk - crown + 2);
		};
	}

	/** A tree like the game's: a stepped crown of dense leaves (narrow top, wide middle) over a 4 pixel trunk. */
	static Part blockyTree(String log, String leaves, int x, int trunk) {
		return blockyTree(log, leaves, x, trunk, new int[][] {{4, 12}, {4, 20}, {4, 20}, {3, 16}});
	}

	/** The same with its own crown: rows of height and width from the top, centred on the trunk. */
	static Part blockyTree(String log, String leaves, int x, int trunk, int[][] rows) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels leafTexture = scenes.sprite(leaves, colors);
			int base = average(leafTexture);
			int height = 0;
			for (int[] row : rows) {
				height += row[0];
			}
			int top = groundY - trunk - height + 2;
			composite(canvas, scenes.block(log).crop(6, 0, 4, trunk + 2), x + 8, groundY - trunk - 2);
			int y = top;
			for (int[] row : rows) {
				int left = x + 10 - row[1] / 2;
				for (int yy = y; yy < y + row[0]; yy++) {
					for (int xx = left; xx < left + row[1]; xx++) {
						int pixel = leafTexture.get(Math.floorMod(xx, 16), Math.floorMod(yy, 16));
						if (pixel >>> 24 < 128) {
							pixel = darker(base, 0.6);
						}
						boolean edge = xx == left || xx == left + row[1] - 1 || yy == top;
						if (xx >= 0 && yy >= 0 && xx < canvas.width && yy < canvas.height) {
							canvas.set(xx, yy, 0xFF000000 | (edge ? darker(pixel & 0xFFFFFF, 0.8) : pixel & 0xFFFFFF));
						}
					}
				}
				y += row[0];
			}
		};
	}

	/** A column of a texture standing on the ground (a cactus, an ice spike, a log). */
	static Part column(String texture, int x, int height, int width) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels source = scenes.block(texture);
			Pixels column = new Pixels(width, height);
			for (int y = 0; y < height; y += 16) {
				composite(column, source.crop(0, 0, width, Math.min(16, height - y)), 0, y);
			}
			composite(canvas, column, x, groundY - height);
		};
	}

	/** A small building: rows of blocks from the bottom up (null for a gap), each block 8 pixels wide. */
	static Part wall(int x, String[]... rows) {
		return (scenes, canvas, groundY, colors) -> {
			for (int row = 0; row < rows.length; row++) {
				for (int i = 0; i < rows[row].length; i++) {
					if (rows[row][i] != null) {
						composite(canvas, resize(scenes.block(rows[row][i]), 8, 8), x + i * 8, groundY - 8 * (row + 1));
					}
				}
			}
		};
	}

	/** Paints a texture (at its own pixel size, repeated) into these pixels. */
	private static void fill(Pixels canvas, Pixels texture, List<int[]> mask) {
		for (int[] point : mask) {
			if (point[0] >= 0 && point[1] >= 0 && point[0] < canvas.width && point[1] < canvas.height) {
				canvas.set(point[0], point[1], texture.get(point[0] % 16, point[1] % 16));
			}
		}
	}

	private static List<int[]> rect(int left, int top, int right, int bottom) {
		List<int[]> points = new ArrayList<>();
		for (int y = top; y < bottom; y++) {
			for (int x = left; x < right; x++) {
				points.add(new int[] {x, y});
			}
		}
		return points;
	}

	/** The deep dark: a sculk shrieker with its jaws on top, a small sensor with tendrils, glowing specks. */
	static final Part SCULK = (scenes, canvas, groundY, colors) -> {
		Pixels side = scenes.block("sculk_shrieker_side");
		composite(canvas, side.crop(0, 8, 16, 8), 2, groundY - 8);
		Pixels jaws = side.crop(0, 0, 16, 8);
		int bottom = -1;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				if (jaws.get(x, y) >>> 24 != 0) {
					bottom = y;
				}
			}
		}
		if (bottom < 0) {
			bottom = 7;
		}
		composite(canvas, jaws.crop(0, 0, 16, bottom + 1), 2, groundY - 8 - (bottom + 1));
		composite(canvas, resize(scenes.block("sculk_sensor_side").crop(0, 8, 16, 8), 10, 5), 20, groundY - 5);
		composite(canvas, resize(scenes.block("sculk_sensor_tendril_inactive").crop(0, 0, 16, 8), 10, 5), 20, groundY - 10);
		for (int[] speck : new int[][] {{5, 3}, {24, 6}, {14, 9}, {28, 14}, {9, 16}}) {
			canvas.set(speck[0], speck[1], 0xFF00C8DC);
		}
	};

	/**
	 * The End's exit portal as the dragon fight leaves it before it opens: its bedrock bowl in the ground (empty) and
	 * the bedrock pillar in its middle with its torches.
	 */
	static final Part EXIT_PORTAL = (scenes, canvas, groundY, colors) -> {
		Pixels bedrock = scenes.block("bedrock");
		for (int y = groundY - 2; y < groundY + 5; y++) {
			for (int x = 3; x < 29; x++) {
				boolean wall = x < 6 || x >= 26 || y >= groundY + 3;
				if (wall) {
					canvas.set(x, y, 0xFF000000 | bedrock.get(x % 16, Math.floorMod(y, 16)));
				} else if (y >= groundY) {
					canvas.set(x, y, 0xFF000000 | darker(END_SKY, 0.6));
				}
			}
		}
		for (int y = groundY - 12; y < groundY + 3; y++) {
			for (int x = 14; x < 18; x++) {
				canvas.set(x, y, 0xFF000000 | bedrock.get(x % 16, Math.floorMod(y, 16)));
			}
		}
		// The torches on the pillar's sides, smaller than the game's: the torch's flame and stick.
		Pixels torch = resize(scenes.block("torch").crop(7, 6, 2, 10), 2, 6);
		composite(canvas, torch, 12, groundY - 10);
		composite(canvas, torch, 18, groundY - 10);
	};

	/** The desert pyramid's front: the stepped middle, two towers with orange stripes, the dark entrance. */
	static final Part PYRAMID = (scenes, canvas, groundY, colors) -> {
		List<int[]> middle = new ArrayList<>();
		for (int y = groundY - 18; y < groundY; y++) {
			for (int x = 5; x < 27; x++) {
				if (Math.abs(x - 15.5) <= (y - (groundY - 18)) * 0.62 + 2) {
					middle.add(new int[] {x, y});
				}
			}
		}
		fill(canvas, scenes.block("sandstone"), middle);
		for (int towerX : new int[] {1, 24}) {
			fill(canvas, scenes.block("cut_sandstone"), rect(towerX, groundY - 20, towerX + 7, groundY));
			for (int y : new int[] {groundY - 16, groundY - 10}) {
				fill(canvas, scenes.block("orange_terracotta"), rect(towerX + 1, y, towerX + 6, y + 2));
			}
			List<int[]> battlements = new ArrayList<>();
			for (int x = towerX; x < towerX + 7; x += 2) {
				battlements.add(new int[] {x, groundY - 21});
			}
			fill(canvas, scenes.block("sandstone"), battlements);
		}
		fill(canvas, scenes.block("chiseled_sandstone"), rect(13, groundY - 13, 19, groundY - 8));
		for (int[] point : rect(14, groundY - 7, 18, groundY)) {
			canvas.set(point[0], point[1], 0xFF1E160E);
		}
	};

	/** A part drawn higher up (on a hill or a mesa). */
	static Part raised(int by, Part part) {
		return (scenes, canvas, groundY, colors) -> part.draw(scenes, canvas, groundY - by, colors);
	}

	/**
	 * Mountains behind: each peak {centre x, height, half width of its flat top}, sides falling one pixel per pixel;
	 * the top rows (cap, may be 0) in a second texture, like snow on a peak.
	 */
	static Part mountain(String texture, String cap, int capDepth, int[]... peaks) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels body = scenes.texture(texture, colors);
			Pixels top = cap == null ? null : scenes.texture(cap, colors);
			for (int x = 0; x < SIZE; x++) {
				int highest = Integer.MAX_VALUE;
				for (int[] peak : peaks) {
					int away = Math.max(0, Math.abs(x - peak[0]) - peak[2]);
					highest = Math.min(highest, groundY - peak[1] + away);
				}
				for (int y = Math.max(0, highest); y < groundY; y++) {
					Pixels source = top != null && y < highest + capDepth ? top : body;
					canvas.set(x, y, 0xFF000000 | source.get(x % 16, y % 16));
				}
			}
		};
	}

	/** A badlands mesa: bands of terracotta from left to right (exclusive), flat on top. */
	static Part mesa(int left, int right, int height, String... bands) {
		return (scenes, canvas, groundY, colors) -> {
			for (int y = groundY - height; y < groundY; y++) {
				Pixels band = scenes.block(bands[(y - (groundY - height)) / 3 % bands.length]);
				for (int x = left; x < right; x++) {
					canvas.set(x, y, 0xFF000000 | band.get(x % 16, y % 16));
				}
			}
		};
	}

	/** Water (or another texture) in place of the ground from left to right, its surface one pixel lower. */
	static Part pool(String texture, int left, int right) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels water = scenes.texture(texture, colors);
			for (int y = groundY; y < SIZE; y++) {
				for (int x = left; x < right; x++) {
					canvas.set(x, y, y == groundY ? canvas.get(x, y - 1) : 0xFF000000 | water.get(x % 16, y % 16));
				}
			}
		};
	}

	/** Rows of a texture across the picture (ice on a frozen sea, snow on a beach). */
	static Part band(String texture, int top, int bottom) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels source = scenes.texture(texture, colors);
			for (int y = top; y < bottom; y++) {
				for (int x = 0; x < SIZE; x++) {
					canvas.set(x, y, 0xFF000000 | source.get(x % 16, y % 16));
				}
			}
		};
	}

	/** A plant two blocks tall (sunflower, kelp): bottom on the ground, top above, an optional head on the top. */
	static Part tall(String bottom, String top, String head, int x) {
		return (scenes, canvas, groundY, colors) -> {
			composite(canvas, scenes.sprite(bottom, colors), x, groundY - 16);
			composite(canvas, scenes.sprite(top, colors), x, groundY - 32);
			if (head != null) {
				composite(canvas, scenes.sprite(head, colors), x, groundY - 30);
			}
		};
	}

	/** Something hanging from the top (vines, roots): its texture repeated down, the end piece at the bottom. */
	static Part hanging(String plant, String end, int x, int length) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels body = scenes.sprite(plant, colors);
			int y = length - 16;
			if (end != null) {
				composite(canvas, scenes.sprite(end, colors), x, y);
				y -= 16;
			}
			for (; y > -16; y -= 16) {
				composite(canvas, body, x, y);
			}
		};
	}

	/** Pieces at half size stacked from the ground up (up = true) or from the top down: stalagmites, stalactites. */
	static Part spike(int x, boolean up, String... pieces) {
		return (scenes, canvas, groundY, colors) -> {
			for (int i = 0; i < pieces.length; i++) {
				Pixels piece = resize(scenes.block(pieces[i]), 8, 8);
				composite(canvas, piece, x, up ? groundY - 8 * (i + 1) : 8 * i);
			}
		};
	}

	/** A chorus plant: a stem of chorus wood with side branches, a flower on top. */
	static Part chorus(int x, int height) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels plant = scenes.block("chorus_plant");
			Pixels flower = resize(scenes.block("chorus_flower"), 6, 6);
			int top = groundY - height;
			for (int y = top; y < groundY; y++) {
				for (int dx = 0; dx < 4; dx++) {
					canvas.set(x + dx, y, 0xFF000000 | plant.get((x + dx) % 16, y % 16));
				}
			}
			int branch = top + height / 3;
			for (int y = branch; y < branch + 4; y++) {
				for (int dx = -5; dx < 9; dx++) {
					canvas.set(x + dx, y, 0xFF000000 | plant.get(Math.floorMod(x + dx, 16), y % 16));
				}
			}
			for (int y = branch - 6; y < branch; y++) {
				for (int dx : new int[] {-5, -4, 7, 8}) {
					canvas.set(x + dx, y, 0xFF000000 | plant.get(Math.floorMod(x + dx, 16), y % 16));
				}
			}
			composite(canvas, flower, x - 1, top - 6);
			composite(canvas, flower, x - 6, branch - 12);
			composite(canvas, flower, x + 5, branch - 12);
		};
	}

	/** Bamboo: the stalk out of its texture sheet (3 pixels wide), as tall as given. */
	static Part bamboo(int x, int height) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels stalk = scenes.block("bamboo_stalk");
			for (int y = groundY - height; y < groundY; y++) {
				for (int dx = 0; dx < 3; dx++) {
					canvas.set(x + dx, y, 0xFF000000 | stalk.get(dx, Math.floorMod(y, 16)));
				}
			}
			Pixels leaves = scenes.block("bamboo_small_leaves");
			composite(canvas, leaves, x - 6, groundY - height - 4);
		};
	}

	/** A block floating in the air (an End island, the void's platform): rows of widths from the top, centred. */
	static Part island(String texture, String top, int centre, int y, int... widths) {
		return (scenes, canvas, groundY, colors) -> {
			Pixels body = scenes.block(texture);
			Pixels upper = scenes.block(top);
			for (int row = 0; row < widths.length; row++) {
				for (int x = centre - widths[row] / 2; x < centre + (widths[row] + 1) / 2; x++) {
					Pixels source = row == 0 ? upper : body;
					canvas.set(x, y + row, 0xFF000000 | source.get(Math.floorMod(x, 16), (y + row) % 16));
				}
			}
		};
	}

	// --- The scenes ----------------------------------------------------------------------------------------------

	/** Biome ID (without minecraft:) -> its scene. */
	static final Map<String, Scene> BIOMES = new LinkedHashMap<>();
	/** Structure key as the web UI names it -> its scene. */
	static final Map<String, Scene> STRUCTURES = new LinkedHashMap<>();

	private static Scene biome(String id) {
		Scene scene = new Scene(id);
		BIOMES.put(id, scene);
		return scene;
	}

	private static Scene structure(String key, String biome) {
		Scene scene = new Scene(biome);
		STRUCTURES.put(key, scene);
		return scene;
	}

	private static final int[][] SPRUCE = {{2, 4}, {3, 8}, {3, 12}, {3, 8}, {3, 14}, {2, 10}};
	private static final int[][] ACACIA = {{2, 12}, {3, 24}};
	private static final int[][] DARK_OAK = {{3, 16}, {4, 24}, {4, 24}, {2, 18}};
	private static final int[][] FUNGUS = {{3, 10}, {4, 18}, {3, 20}};
	private static final String[] BADLANDS = {"terracotta", "orange_terracotta", "yellow_terracotta", "terracotta",
		"white_terracotta", "red_terracotta", "brown_terracotta", "light_gray_terracotta"};

	static {
		// The first fifteen are the approved prototype's; their pixels must stay as they are.
		biome("plains").deco("short_grass", 0, "poppy", 9, "dandelion", 17);
		biome("desert").ground(Ground.SAND).back(column("cactus_side", 5, 14, 7)).deco("dead_bush", 16);
		biome("jungle").back(tree("jungle_log", "jungle_leaves", 6, 10, 12)).deco("fern", 17);
		biome("ice_spikes").ground(Ground.SNOW).back(column("packed_ice", 6, 20, 6), column("packed_ice", 20, 12, 4));
		biome("mushroom_fields").ground(Ground.MYCELIUM).deco("red_mushroom", 2, "brown_mushroom", 15);
		biome("cherry_grove").back(blockyTree("cherry_log", "cherry_leaves", 6, 8)).deco("pink_petals", 0);
		biome("deep_dark").cave("deepslate").ground(Ground.SCULK).back(SCULK);
		biome("warped_forest").background(Background.FOG).ground(Ground.WARPED)
			.back(blockyTree("warped_stem", "warped_wart_block", 8, 9, FUNGUS)).deco("warped_fungus", 0, "warped_roots", 17);

		structure("bastion-remnant", "nether_wastes").background(Background.FOG).ground(Ground.BLACKSTONE).back(wall(4,
			new String[] {"polished_blackstone_bricks", "gilded_blackstone", "polished_blackstone_bricks"},
			new String[] {"polished_blackstone_bricks", null, "polished_blackstone_bricks"},
			new String[] {"chiseled_polished_blackstone", "polished_blackstone_bricks", "chiseled_polished_blackstone"}));
		structure("creaking-heart-pale-oak", "pale_garden").ground(Ground.PALE_MOSS).back(
			column("pale_oak_log", 13, 12, 6), wall(12, new String[] {null}, new String[] {"creaking_heart"}),
			tree("pale_oak_log", "pale_oak_leaves", 8, 14, 10));
		structure("desert-pyramid", "desert").ground(Ground.SAND).groundY(26).back(PYRAMID);
		structure("desert-well", "desert").ground(Ground.SAND).back(
			wall(4, new String[] {"sandstone", "water_still", "sandstone"}, new String[] {"sandstone_top", null, "sandstone_top"}),
			wall(4, new String[] {null, null, null}, new String[] {null, null, null},
				new String[] {"sandstone_top", "sandstone_top", "sandstone_top"}));
		structure("ocean-ruin", "ocean").water(0.6).ground(Ground.GRAVEL).back(wall(6,
			new String[] {"mossy_stone_bricks", "cracked_stone_bricks"}, new String[] {"stone_bricks", null},
			new String[] {"chiseled_stone_bricks", null}));
		structure("trail-ruins", "plains").back(wall(6,
			new String[] {"white_terracotta", "suspicious_gravel_0", "mud_bricks"}, new String[] {"light_blue_terracotta", null, null}));
		structure("warm-ocean-ruins", "warm_ocean").water(0.7).ground(Ground.SAND).back(wall(6,
			new String[] {"sandstone", "cut_sandstone"}, new String[] {"chiseled_sandstone", null}));

		// Grassland and forests.
		biome("sunflower_plains").back(tall("sunflower_bottom", "sunflower_top", "sunflower_front", 12))
			.deco("short_grass", 0, "short_grass", 20);
		biome("meadow").deco("short_grass", 0, "cornflower", 8, "allium", 16, "oxeye_daisy", 22);
		biome("forest").back(tree("oak_log", "oak_leaves", 0, 9, 12), tree("birch_log", "birch_leaves", 16, 11, 11, true));
		biome("flower_forest").back(tree("oak_log", "oak_leaves", 8, 9, 12)).deco("allium", 0, "red_tulip", 18);
		biome("birch_forest").back(tree("birch_log", "birch_leaves", 0, 10, 11, true),
			tree("birch_log", "birch_leaves", 16, 8, 11, true))
			.deco("short_grass", 9);
		biome("old_growth_birch_forest").back(tree("birch_log", "birch_leaves", 0, 15, 11, true),
			tree("birch_log", "birch_leaves", 16, 13, 11, true));
		biome("dark_forest").back(blockyTree("dark_oak_log", "dark_oak_leaves", 6, 7, DARK_OAK))
			.deco("red_mushroom", 18);
		biome("pale_garden").ground(Ground.PALE_MOSS).back(tree("pale_oak_log", "pale_oak_leaves", 8, 12, 12))
			.deco("closed_eyeblossom", 0).front(hanging("pale_hanging_moss", "pale_hanging_moss_tip", 18, 14));
		biome("dappled_forest").back(blockyTree("oak_log", "oak_leaves", 6, 8)).deco("red_shrub", 16);
		biome("taiga").back(blockyTree("spruce_log", "spruce_leaves", 0, 4, SPRUCE),
			blockyTree("spruce_log", "spruce_leaves", 14, 2, SPRUCE)).deco("fern", 16);
		biome("snowy_taiga").ground(Ground.SNOW).back(blockyTree("spruce_log", "spruce_leaves", 0, 4, SPRUCE),
			blockyTree("spruce_log", "spruce_leaves", 14, 2, SPRUCE));
		biome("old_growth_pine_taiga").ground(Ground.PODZOL).back(
			blockyTree("spruce_log", "spruce_leaves", 6, 12, new int[][] {{2, 4}, {3, 8}, {3, 12}, {2, 8}}))
			.deco("brown_mushroom", 0, "fern", 18);
		biome("old_growth_spruce_taiga").ground(Ground.PODZOL).back(
			blockyTree("spruce_log", "spruce_leaves", 6, 6, new int[][] {{2, 4}, {3, 8}, {3, 12}, {3, 10}, {3, 16}, {3, 12}, {2, 18}}))
			.deco("fern", 0);
		biome("snowy_plains").ground(Ground.SNOW).back(blockyTree("spruce_log", "spruce_leaves", 12, 3,
			new int[][] {{2, 4}, {3, 8}, {2, 6}, {3, 10}}));
		biome("grove").ground(Ground.SNOW).back(blockyTree("spruce_log", "spruce_leaves", -2, 3, SPRUCE),
			blockyTree("spruce_log", "spruce_leaves", 12, 6, SPRUCE));
		biome("savanna").back(blockyTree("acacia_log", "acacia_leaves", 4, 10, ACACIA)).deco("short_grass", 18);
		biome("savanna_plateau").back(mountain("dirt", "@grass", 2, new int[] {16, 6, 12}),
			raised(6, blockyTree("acacia_log", "acacia_leaves", 4, 8, ACACIA)));
		biome("swamp").back(tree("oak_log", "oak_leaves", 2, 9, 12), hanging("vine", null, 2, 14))
			.front(pool("@water", 16, 32));
		biome("mangrove_swamp").ground(Ground.MUD).back(column("mangrove_roots_side", 7, 6, 10),
			raised(5, tree("mangrove_log", "mangrove_leaves", 6, 6, 12))).front(pool("@water", 20, 32));
		biome("sparse_jungle").back(tree("jungle_log", "jungle_leaves", 2, 7, 10)).deco("fern", 16);
		biome("bamboo_jungle").ground(Ground.PODZOL).back(tree("jungle_log", "jungle_leaves", 12, 10, 12),
			bamboo(4, 22), bamboo(10, 16), bamboo(26, 19)).deco("fern", 16);

		// Mountains and hills.
		biome("jagged_peaks").ground(Ground.SNOW).back(mountain("stone", "snow", 4, new int[] {9, 21, 0}, new int[] {24, 15, 0}));
		biome("frozen_peaks").ground(Ground.SNOW).back(mountain("packed_ice", "snow", 3, new int[] {12, 20, 1}, new int[] {27, 12, 0}));
		biome("stony_peaks").ground(Ground.STONE).back(mountain("stone", "calcite", 3, new int[] {10, 20, 1}, new int[] {25, 14, 0}));
		biome("snowy_slopes").ground(Ground.SNOW).back(mountain("snow", "powder_snow", 2, new int[] {20, 16, 2}));
		biome("windswept_hills").back(mountain("stone", "@grass", 2, new int[] {10, 16, 1}, new int[] {26, 10, 0}))
			.deco("short_grass", 16);
		biome("windswept_gravelly_hills").ground(Ground.GRAVEL)
			.back(mountain("gravel", null, 0, new int[] {12, 14, 2}, new int[] {28, 8, 0}));
		biome("windswept_forest").back(mountain("stone", "@grass", 2, new int[] {24, 14, 1}),
			blockyTree("spruce_log", "spruce_leaves", 0, 4, SPRUCE));
		biome("windswept_savanna").back(mountain("stone", "@grass", 2, new int[] {22, 15, 1}),
			blockyTree("acacia_log", "acacia_leaves", 0, 8, ACACIA));
		biome("badlands").ground(Ground.RED_SAND).back(mesa(8, 32, 18, BADLANDS)).deco("dead_bush", 0);
		biome("wooded_badlands").ground(Ground.RED_SAND).back(mesa(0, 32, 9, BADLANDS),
			raised(9, tree("oak_log", "oak_leaves", 8, 6, 11)));
		biome("eroded_badlands").ground(Ground.RED_SAND).back(mesa(3, 9, 20, BADLANDS), mesa(14, 18, 12, BADLANDS),
			mesa(24, 30, 17, BADLANDS));

		// Water.
		biome("river").front(pool("@water", 8, 24));
		biome("frozen_river").ground(Ground.SNOW).front(pool("ice", 8, 24));
		biome("beach").ground(Ground.SAND).front(pool("@water", 18, 32));
		biome("snowy_beach").ground(Ground.SAND).front(band("snow", 22, 24), pool("@water", 18, 32));
		biome("stony_shore").ground(Ground.STONE).back(mountain("stone", null, 0, new int[] {4, 6, 3}))
			.front(pool("@water", 16, 32));
		biome("ocean").water(0.6).ground(Ground.GRAVEL).deco("seagrass", 2).back(tall("kelp_plant", "kelp", null, 16));
		biome("deep_ocean").water(0.45).ground(Ground.GRAVEL).back(tall("kelp_plant", "kelp_plant", null, 2),
			tall("kelp_plant", "kelp", null, 16));
		biome("lukewarm_ocean").water(0.65).ground(Ground.SAND).deco("seagrass", 0).back(tall("kelp_plant", "kelp", null, 16));
		biome("deep_lukewarm_ocean").water(0.5).ground(Ground.SAND).back(tall("kelp_plant", "kelp", null, 4))
			.deco("seagrass", 16);
		biome("warm_ocean").water(0.7).ground(Ground.SAND).deco("tube_coral", 0, "fire_coral", 9, "horn_coral", 17);
		biome("cold_ocean").water(0.6).ground(Ground.GRAVEL).deco("seagrass", 2, "seagrass", 16);
		biome("deep_cold_ocean").water(0.45).ground(Ground.GRAVEL).back(tall("kelp_plant", "kelp", null, 8))
			.deco("seagrass", 18);
		biome("frozen_ocean").ground(Ground.WATER).back(mountain("packed_ice", "snow", 2, new int[] {16, 12, 3}));
		biome("deep_frozen_ocean").ground(Ground.WATER).back(mountain("blue_ice", "packed_ice", 3,
			new int[] {10, 18, 1}, new int[] {24, 10, 2}));

		// Caves.
		biome("dripstone_caves").cave("stone").ground(Ground.DRIPSTONE).back(
			spike(4, true, "pointed_dripstone_up_frustum", "pointed_dripstone_up_tip"),
			spike(20, true, "pointed_dripstone_up_tip"),
			spike(12, false, "pointed_dripstone_down_frustum", "pointed_dripstone_down_tip"),
			spike(24, false, "pointed_dripstone_down_tip"));
		biome("lush_caves").cave("stone").ground(Ground.MOSS).back(hanging("cave_vines_plant_lit", "cave_vines_lit", 2, 14),
			hanging("cave_vines_plant", "cave_vines", 20, 9)).deco("short_grass", 10);
		biome("sulfur_caves").cave("stone").ground(Ground.SULFUR).back(
			spike(3, true, "sulfur_spike_up_frustum", "sulfur_spike_up_tip"),
			spike(21, true, "sulfur_spike_up_tip"),
			spike(12, false, "sulfur_spike_down_frustum", "sulfur_spike_down_tip")).front(pool("@water", 12, 20));

		// The Nether.
		biome("nether_wastes").background(Background.FOG).ground(Ground.NETHERRACK)
			.back(mountain("netherrack", null, 0, new int[] {3, 14, 2}, new int[] {29, 10, 2})).deco("fire_0", 8);
		biome("crimson_forest").background(Background.FOG).ground(Ground.CRIMSON)
			.back(blockyTree("crimson_stem", "nether_wart_block", 2, 9, FUNGUS),
				hanging("weeping_vines_plant", "weeping_vines", 4, 6)).deco("crimson_fungus", 18, "crimson_roots", 10);
		biome("soul_sand_valley").background(Background.FOG).ground(Ground.SOUL_SAND)
			.back(column("bone_block_side", 4, 14, 6)).deco("soul_fire_0", 16);
		biome("basalt_deltas").background(Background.FOG).ground(Ground.BASALT)
			.back(column("basalt_side", 3, 16, 6), column("basalt_side", 13, 9, 6), column("basalt_side", 23, 20, 6));

		// The End.
		biome("the_end").background(Background.END).ground(Ground.END_STONE)
			.back(column("obsidian", 1, 18, 5), column("obsidian", 26, 25, 5)).front(EXIT_PORTAL);
		biome("end_highlands").background(Background.END).ground(Ground.END_STONE).back(chorus(14, 16));
		biome("end_midlands").background(Background.END).ground(Ground.END_STONE).back(chorus(14, 9));
		biome("end_barrens").background(Background.END).ground(Ground.END_STONE)
			.back(mountain("end_stone", null, 0, new int[] {20, 4, 4}));
		biome("small_end_islands").background(Background.END).ground(Ground.NONE).groundY(32)
			.back(island("end_stone", "end_stone", 13, 12, 18, 18, 16, 14, 12, 10, 7, 4),
				island("end_stone", "end_stone", 26, 22, 8, 8, 6, 4));
		biome("the_void").ground(Ground.NONE).groundY(32)
			.back(island("stone", "stone", 16, 17, 24, 24, 24, 24), island("cobblestone", "cobblestone", 16, 17, 4, 4));
	}
}
