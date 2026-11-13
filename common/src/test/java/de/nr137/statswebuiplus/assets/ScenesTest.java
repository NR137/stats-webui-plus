package de.nr137.statswebuiplus.assets;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The scenes' pixel helpers. They give exactly Pillow's results (the approved scenes were drawn with Pillow first), so
 * the Java scenes stay pixel-identical to them.
 */
class ScenesTest {
	@Test
	void layersMixLikePillow() {
		// Expected values computed with Pillow's Image.alpha_composite.
		assertEquals(0xFF826E3C, Scenes.over(argb(255, 10, 200, 30), argb(128, 250, 20, 90)));
		assertEquals(0xD5EB1F56, Scenes.over(argb(60, 10, 200, 30), argb(200, 250, 20, 90)));
		assertEquals(0x4D783C1E, Scenes.over(0, argb(77, 120, 60, 30)));
		assertEquals(argb(255, 1, 2, 3), Scenes.over(argb(255, 1, 2, 3), 0), "a transparent layer changes nothing");
	}

	@Test
	void nearestNeighbourResizingPicksPillowsPixels() {
		Pixels row = new Pixels(16, 1);
		for (int x = 0; x < 16; x++) {
			row.set(x, 0, x);
		}
		// Pillow adds up the source position step by step; 16 -> 10 picks 11, not 12, for the eighth pixel.
		assertArrayEquals(new int[] {0, 2, 4, 5, 7, 8, 10, 11, 13, 15}, Scenes.resize(row, 10, 1).argb);
		assertArrayEquals(new int[] {1, 4, 8, 11, 14}, Scenes.resize(row, 5, 1).argb);
	}

	@Test
	void everyBiomeTheAdvancementsNameHasAScene() {
		for (String biome : new String[] {"plains", "desert", "deep_dark", "warped_forest", "cherry_grove", "the_end"}) {
			assertTrue(Scenes.BIOMES.containsKey(biome), biome);
		}
		assertEquals(67, Scenes.BIOMES.size(), "one scene per biome of the game");
		assertEquals(7, Scenes.STRUCTURES.size(), "one scene per structure the advancements name");
	}

	private static int argb(int a, int r, int g, int b) {
		return a << 24 | r << 16 | g << 8 | b;
	}
}
