package webui.stats.mc.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * CHANGELOG 1.4.0: a player's page shows, per advancement with several parts (Adventuring Time's biomes, Monsters
 * Hunted's mobs, ...), which parts are done and when, also while the advancement is unfinished. Read from
 * {@code <world>/advancements/<uuid>.json} as Minecraft writes it.
 */
class AdvancementProgressFileTest {
	private static JsonObject file(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	void keepsWhenEachPartWasMetAlsoWhileUnfinished() {
		AdvancementProgressFile progress = AdvancementProgressFile.parse(file("""
			{
			  "minecraft:adventure/adventuring_time": {
			    "criteria": {
			      "minecraft:plains": "2026-07-01 10:00:00 +0000",
			      "minecraft:desert": "2026-07-02 12:30:00 +0200"
			    },
			    "done": false
			  },
			  "DataVersion": 4700
			}
			"""));
		var parts = progress.criteria.get("minecraft:adventure/adventuring_time");
		assertEquals(2, parts.size());
		assertEquals(java.time.Instant.parse("2026-07-01T10:00:00Z").toEpochMilli(), parts.get("minecraft:plains"));
		assertEquals(java.time.Instant.parse("2026-07-02T10:30:00Z").toEpochMilli(), parts.get("minecraft:desert"));
		assertFalse(progress.done.contains("minecraft:adventure/adventuring_time"));
	}

	@Test
	void singlePartAdvancementsAndRecipesStayOut() {
		AdvancementProgressFile progress = AdvancementProgressFile.parse(file("""
			{
			  "minecraft:story/mine_stone": {
			    "criteria": {"get_stone": "2026-07-01 10:00:00 +0000"},
			    "done": true
			  },
			  "minecraft:recipes/misc/stick": {
			    "criteria": {"has_planks": "2026-07-01 10:00:00 +0000", "has_the_recipe": "2026-07-01 10:00:01 +0000"},
			    "done": true
			  }
			}
			"""));
		assertTrue(progress.criteria.isEmpty(), "only advancements with several parts keep them: " + progress.criteria);
		assertTrue(progress.done.contains("minecraft:story/mine_stone"));
		assertTrue(progress.obtainedAt.containsKey("minecraft:story/mine_stone"));
	}

	@Test
	void finishedAdvancementKeepsItsPartsToo() {
		AdvancementProgressFile progress = AdvancementProgressFile.parse(file("""
			{
			  "minecraft:husbandry/leash_all_frog_variants": {
			    "criteria": {
			      "minecraft:cold": "2026-07-01 10:00:00 +0000",
			      "minecraft:temperate": "2026-07-03 10:00:00 +0000",
			      "minecraft:warm": "2026-07-02 10:00:00 +0000"
			    },
			    "done": true
			  }
			}
			"""));
		assertEquals(3, progress.criteria.get("minecraft:husbandry/leash_all_frog_variants").size());
		assertTrue(progress.done.contains("minecraft:husbandry/leash_all_frog_variants"));
	}
}
