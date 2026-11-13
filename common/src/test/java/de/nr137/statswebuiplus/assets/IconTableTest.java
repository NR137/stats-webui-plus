package de.nr137.statswebuiplus.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The icon table: IDs without a picture of their own get a related one. */
class IconTableTest {
	@TempDir
	Path dir;

	@Test
	void blocksAndEntitiesWithoutAnIconBorrowARelatedOne() throws IOException {
		Path out = dir.resolve("mc");
		for (String file : new String[] {"item/potato.png", "item/oak_sign.png", "item/poppy.png"}) {
			Files.createDirectories(out.resolve(file).getParent());
			Files.write(out.resolve(file), new byte[] {1});
		}
		Path jar = jar(Map.of(
			"assets/minecraft/blockstates/potatoes.json", "{}",
			"assets/minecraft/blockstates/oak_wall_sign.json", "{}",
			"assets/minecraft/blockstates/potted_poppy.json", "{}",
			"assets/minecraft/lang/en_us.json", "{\"entity.minecraft.creaking_transient\": \"Creaking\"}"));
		Map<String, String> table = IconTable.write(jar, out,
			Map.of("minecraft:potato", "/mc/item/potato.png", "minecraft:oak_sign", "/mc/item/oak_sign.png",
				"minecraft:poppy", "/mc/item/poppy.png"),
			Map.of("entity:minecraft:creaking", "/mc/face/creaking.png"), Map.of("biome:minecraft:plains", "/mc/scene/plains.png"));
		assertEquals("/mc/item/potato.png", table.get("minecraft:potatoes"), "a crop shows what it grows");
		assertEquals("/mc/item/oak_sign.png", table.get("minecraft:oak_wall_sign"), "a wall sign its sign");
		assertEquals("/mc/item/poppy.png", table.get("minecraft:potted_poppy"), "a potted plant its plant");
		assertEquals("/mc/face/creaking.png", table.get("entity:minecraft:creaking_transient"),
			"the creaking a heart spawns has the creaking's face");
		assertEquals("/mc/scene/plains.png", table.get("biome:minecraft:plains"));
		assertTrue(Files.readString(out.resolve("icons.json")).contains("\"minecraft:potatoes\""), "written for the web UI");
	}

	@Test
	void aliases() {
		assertEquals("cake", IconTable.alias("red_candle_cake"));
		assertEquals("cauldron", IconTable.alias("water_cauldron"));
		assertEquals("creeper_head", IconTable.alias("creeper_wall_head"));
		assertEquals("oak_boat", IconTable.alias("boat"));
		assertNull(IconTable.alias("stone"));
	}

	private Path jar(Map<String, String> files) throws IOException {
		Path jar = dir.resolve("client.jar");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
			for (Map.Entry<String, String> file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return jar;
	}
}
