package de.nr137.statswebuiplus.stats;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import de.nr137.statswebuiplus.MinecraftBootstrap;

/**
 * Statistics: like the mob groups, blocks mined lists only what a player can realistically get; any
 * statistic someone has is shown anyway (VanillaStats.keys). Broken and crafted need a running server (item
 * components, recipes, trades): see the game test craftable_items_from_recipes_and_trades.
 */
class VanillaCatalogTest {
	@BeforeAll
	static void bootstrap() {
		MinecraftBootstrap.run();
	}

	@Test
	void minedListsBlocksSurvivalCanBreak() {
		List<String> mined = VanillaCatalog.keys("mined");
		assertTrue(mined.containsAll(List.of("minecraft:stone", "minecraft:oak_log", "minecraft:short_grass", "minecraft:obsidian")));
		for (String never : List.of("minecraft:air", "minecraft:water", "minecraft:lava", "minecraft:bedrock", "minecraft:barrier",
				"minecraft:command_block", "minecraft:end_portal", "minecraft:end_portal_frame")) {
			assertFalse(mined.contains(never), never);
		}
	}

	@Test
	void usedPickedUpAndDroppedListEveryItem() {
		// Any item can be put into a decorated pot (used) or lie on the ground (picked up, dropped).
		for (String group : List.of("used", "picked_up", "dropped")) {
			assertTrue(VanillaCatalog.keys(group).containsAll(List.of("minecraft:stick", "minecraft:stone", "minecraft:diamond")), group);
		}
	}

	@Test
	void statisticsSomeoneHasAreShownAnyway() {
		PlayerRecord player = new PlayerRecord(java.util.UUID.randomUUID(), "Op", 1, java.util.Map.of(),
			java.util.Map.of("mined", java.util.Map.of("minecraft:bedrock", 1L)), java.util.Set.of(), java.util.Map.of());
		assertTrue(VanillaStats.keys("mined", List.of(player)).contains("minecraft:bedrock"));
	}
}
