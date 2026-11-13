package de.nr137.statswebuiplus.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Icons drawn from the game's models and textures, as the inventory shows them (see GameIcons). */
class GameIconsTest {
	private static final int RED = 0xFFC83232;
	private static final int GREY = 0xFF808080;

	@TempDir
	Path dir;

	@Test
	void aFullBlockBecomesAnIsoCube() throws IOException {
		Path out = render(Map.of(
			"assets/minecraft/items/stone.json", item("minecraft:block/stone"),
			"assets/minecraft/models/block/stone.json", "{\"parent\": \"minecraft:block/cube_all\", \"textures\": {\"all\": \"minecraft:block/stone\"}}"
		), Map.of("block/stone.png", solid(RED)));
		Pixels icon = Pixels.read(out.resolve("iso/stone.png"));
		assertEquals(32, icon.width);
		assertEquals(RED, icon.get(15, 1), "the top at full brightness");
		assertEquals(Pixels.shade(RED, 0.8), icon.get(2, 16), "the left side a little darker");
		assertEquals(Pixels.shade(RED, 0.55), icon.get(29, 16), "the right side darker still");
		assertEquals(0, Pixels.alpha(icon.get(0, 0)), "transparent around it");
	}

	@Test
	void aSlabIsDrawnByTheModelRendererHalfAsHigh() throws IOException {
		Path out = render(Map.of(
			"assets/minecraft/items/stone_slab.json", item("minecraft:block/stone_slab"),
			"assets/minecraft/models/block/stone_slab.json", "{\"textures\": {\"all\": \"minecraft:block/stone\"}, \"elements\": [{"
				+ "\"from\": [0, 0, 0], \"to\": [16, 8, 16], \"faces\": {\"down\": {\"texture\": \"#all\"}, "
				+ "\"up\": {\"texture\": \"#all\"}, \"north\": {\"texture\": \"#all\"}, \"south\": {\"texture\": \"#all\"}, "
				+ "\"west\": {\"texture\": \"#all\"}, \"east\": {\"texture\": \"#all\"}}}]}"
		), Map.of("block/stone.png", solid(RED)));
		Pixels icon = Pixels.read(out.resolve("iso/stone_slab.png"));
		assertEquals(0, Pixels.alpha(icon.get(15, 3)), "nothing where a full block's top would be");
		assertEquals(RED, icon.get(15, 14), "its top, lower, at full brightness");
		assertEquals(Pixels.shade(RED, 0.8), icon.get(3, 21), "the left side as a cube's");
		assertEquals(Pixels.shade(RED, 0.55), icon.get(28, 21), "the right side as a cube's");
	}

	@Test
	void seeThroughBlocksShowTheirBackFaces() throws IOException {
		Path out = render(Map.of(
			"assets/minecraft/items/glass.json", item("minecraft:block/glass"),
			"assets/minecraft/models/block/glass.json", "{\"parent\": \"minecraft:block/cube_all\", \"textures\": {\"all\": "
				+ "{\"sprite\": \"minecraft:block/glass\", \"force_translucent\": true}}}",
			"assets/minecraft/models/block/cube_all.json", "{\"elements\": [{\"from\": [0, 0, 0], \"to\": [16, 16, 16], \"faces\": {"
				+ "\"down\": {\"texture\": \"#all\"}, \"up\": {\"texture\": \"#all\"}, \"north\": {\"texture\": \"#all\"}, "
				+ "\"south\": {\"texture\": \"#all\"}, \"west\": {\"texture\": \"#all\"}, \"east\": {\"texture\": \"#all\"}}}]}"
		), Map.of("block/glass.png", solid(0x80FFFFFF)));
		Pixels icon = Pixels.read(out.resolve("iso/glass.png"));
		assertTrue(Pixels.alpha(icon.get(16, 8)) > 0x80, "the top over the bottom seen through it: two layers of glass");
	}

	@Test
	void layersAreStackedAndTinted() throws IOException {
		Path out = render(Map.of(
			"assets/minecraft/items/potion.json",
			"{\"model\": {\"type\": \"minecraft:model\", \"model\": \"minecraft:item/potion\", \"tints\": [{\"type\": \"minecraft:potion\", \"default\": -13083194}]}}",
			"assets/minecraft/models/item/potion.json",
			"{\"parent\": \"minecraft:item/generated\", \"textures\": {\"layer0\": \"minecraft:item/potion_overlay\", \"layer1\": \"minecraft:item/potion\"}}"
		), Map.of("item/potion_overlay.png", solid(0xFFFFFFFF), "item/potion.png", corner(GREY)));
		Pixels icon = Pixels.read(out.resolve("layered/potion.png"));
		int tint = -13083194 & 0xFFFFFF;
		assertEquals(0xFF000000 | tint, icon.get(5, 5), "layer0 (white) in the potion's default colour");
		assertEquals(GREY, icon.get(0, 0), "layer1 over it, untinted");
	}

	@Test
	void animatedBlocksBecomeAnimatedPngs() throws IOException {
		Map<String, String> jar = new LinkedHashMap<>();
		jar.put("assets/minecraft/items/magma_block.json", item("minecraft:block/magma_block"));
		jar.put("assets/minecraft/models/block/magma_block.json",
			"{\"parent\": \"minecraft:block/cube_all\", \"textures\": {\"all\": \"minecraft:block/magma\"}}");
		jar.put("assets/minecraft/textures/block/magma.png.mcmeta", "{\"animation\": {\"frametime\": 8}}");
		Path out = dir.resolve("mc");
		// Two frames, red then grey, 8 ticks (400 ms) each.
		BufferedImage strip = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				strip.setRGB(x, y, y < 16 ? RED : GREY);
			}
		}
		GameIcons.Result result = GameIcons.render(jar(jar, Map.of("block/magma.png", png(strip)), out), out);
		assertEquals(java.util.List.of("minecraft:magma_block"), result.animated());
		assertEquals("/mc/iso/magma_block.png", result.icons().get("minecraft:magma_block"));
		byte[] apng = Files.readAllBytes(out.resolve("iso/magma_block.png"));
		assertEquals(1, Apng.chunks(apng, "acTL").size(), "an animated PNG");
		assertEquals(2, java.nio.ByteBuffer.wrap(Apng.chunks(apng, "acTL").get(0)).getInt(), "two frames");
		assertEquals(RED, Pixels.decode(apng).get(15, 1), "browsers without APNG show the first frame");
	}

	@Test
	void theExperienceOrbPulsesSixTimesSlowerThanInTheGame() throws IOException {
		// The orb sheet: 4 x 4 orbs of 16 px, grey, as in the game's file.
		BufferedImage sheet = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				sheet.setRGB(x, y, GREY);
			}
		}
		Path jar = dir.resolve("client.jar");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
			zip.putNextEntry(new ZipEntry("assets/minecraft/textures/entity/experience/experience_orb.png"));
			zip.write(png(sheet));
			zip.closeEntry();
		}
		Path out = dir.resolve("mc");
		GameIcons.Result result = GameIcons.render(jar, out);
		assertEquals("/mc/special/experience_orb.png", result.icons().get("special:experience-orb"));
		byte[] apng = Files.readAllBytes(out.resolve("special/experience_orb.png"));
		java.util.List<byte[]> frames = Apng.chunks(apng, "fcTL");
		int total = 0;
		int longest = 0;
		for (byte[] frame : frames) {
			int millis = java.nio.ByteBuffer.wrap(frame).getShort(20);
			total += millis;
			longest = Math.max(longest, millis);
		}
		// The game's colour cycle is 4π ticks (0.63 s); here six times that, in steps of about 39 ms.
		assertEquals(6 * 4 * Math.PI * 50, total, 50, "one colour cycle");
		assertTrue(longest <= 2 * 39 + 1, "as smooth as in the game, the longest frame is " + longest + " ms");
	}

	@Test
	void interpolatedAnimationsBlendLikeTheGame() {
		Pixels strip = new Pixels(1, 2);
		strip.set(0, 0, 0xFF000000);
		strip.set(0, 1, 0xFFC8C8C8);
		com.google.gson.JsonObject meta = com.google.gson.JsonParser.parseString(
			"{\"animation\": {\"frametime\": 4, \"interpolate\": true}}").getAsJsonObject();
		AnimatedTexture animation = AnimatedTexture.of(strip, meta);
		assertEquals(8, animation.length());
		assertEquals(0xFF000000, animation.at(0).get(0, 0));
		assertEquals(0xFF646464, animation.at(2).get(0, 0), "halfway between black and grey");
		assertEquals(0xFFC8C8C8, animation.at(4).get(0, 0));
		assertNull(AnimatedTexture.of(strip, com.google.gson.JsonParser.parseString("{}").getAsJsonObject()));
	}

	@Test
	void identicalFramesAreMerged() {
		Pixels a = new Pixels(1, 1);
		Pixels b = new Pixels(1, 1);
		b.set(0, 0, RED);
		var merged = Apng.merge(java.util.List.of(new Apng.Frame(a, 50), new Apng.Frame(a, 50), new Apng.Frame(b, 50),
			new Apng.Frame(a, 50)));
		assertEquals(2, merged.size(), "the last frame equals the first, so the loop starts with it");
		assertEquals(150, merged.get(0).millis());
	}

	@Test
	void anItemsOwnTextureIsItsIcon() throws IOException {
		Map<String, String> jar = Map.of(
			"assets/minecraft/items/apple.json", item("minecraft:item/apple"),
			"assets/minecraft/models/item/apple.json", "{\"parent\": \"minecraft:item/generated\", \"textures\": {\"layer0\": \"minecraft:item/apple\"}}"
		);
		Path out = dir.resolve("mc");
		GameIcons.Result result = GameIcons.render(jar(jar, Map.of("item/apple.png", solid(RED)), out), out);
		assertEquals("/mc/item/apple.png", result.icons().get("minecraft:apple"));
	}

	@Test
	void texturesAreFoundUnderTheirUrls() {
		assertEquals("/mc/block/stone.png", GameIcons.texUrl("minecraft:block/stone"));
		assertEquals("/mc/item/apple.png", GameIcons.texUrl("item/apple"));
		assertEquals("/mc/entity/chest/normal.png", GameIcons.texUrl("minecraft:entity/chest/normal"));
		assertNull(GameIcons.texUrl("#side"));
		assertEquals("assets/minecraft/models/item/apple.json", GameIcons.modelPath("minecraft:apple"));
	}

	private Path render(Map<String, String> jar, Map<String, byte[]> textures) throws IOException {
		Path out = dir.resolve("mc");
		GameIcons.render(jar(jar, textures, out), out);
		return out;
	}

	/** A client JAR with these files; the textures also go to out, where GameTextures would have copied them. */
	private Path jar(Map<String, String> files, Map<String, byte[]> textures, Path out) throws IOException {
		Path jar = dir.resolve("client.jar");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
			for (Map.Entry<String, String> file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		for (Map.Entry<String, byte[]> texture : textures.entrySet()) {
			Path target = out.resolve(texture.getKey());
			Files.createDirectories(target.getParent());
			Files.write(target, texture.getValue());
		}
		return jar;
	}

	private static String item(String model) {
		return "{\"model\": {\"type\": \"minecraft:model\", \"model\": \"" + model + "\"}}";
	}

	private static byte[] solid(int argb) throws IOException {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				image.setRGB(x, y, argb);
			}
		}
		return png(image);
	}

	/** Only the top left pixel set, the rest transparent. */
	private static byte[] corner(int argb) throws IOException {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, argb);
		return png(image);
	}

	private static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);
		return bytes.toByteArray();
	}
}
