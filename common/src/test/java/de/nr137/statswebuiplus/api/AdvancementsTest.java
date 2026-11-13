package de.nr137.statswebuiplus.api;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import de.nr137.statswebuiplus.stats.AdvancementInfo;
import de.nr137.statswebuiplus.stats.AdvancementTab;
import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.web.HttpJson;

/** A player's advancements, one advancement for a player, and who has an advancement, from a catalog of five. */
class AdvancementsTest {
	private static final String ROOT = "minecraft:story/root";
	private static final String STONE = "minecraft:story/mine_stone";
	// Two requirements: eat both.
	private static final String DIET = "minecraft:husbandry/diet";
	// One requirement met by either criterion.
	private static final String EITHER = "minecraft:adventure/either";
	// The catalog knows no requirements for it.
	private static final String MODDED = "somemod:quests/first";

	private static final AdvancementView CATALOG = AdvancementView.of(
		List.of(info(ROOT, null), info(STONE, ROOT), info(DIET, null), info(EITHER, null), info(MODDED, null)),
		List.of(new AdvancementTab("story", "Minecraft", "minecraft:grass_block", "stone")),
		Map.of(
			ROOT, List.of(List.of("crafting_table")),
			STONE, List.of(List.of("get_stone")),
			DIET, List.of(List.of("apple"), List.of("bread")),
			EITHER, List.of(List.of("left", "right"))
		)
	);

	private static AdvancementInfo info(String id, String parent) {
		return new AdvancementInfo(id, id.substring(id.indexOf(':') + 1, id.indexOf('/')), id, "", "minecraft:stone", "task",
			false, 0, parent, 1.5, 2.333);
	}

	private static PlayerRecord player(String name, double hours, Map<String, Long> done, Map<String, Map<String, Long>> criteria) {
		return new PlayerRecord(UUID.nameUUIDFromBytes(name.getBytes()), name, hours, Map.of(), Map.of(), Set.of(), Map.of())
			.withAdvancements(done.keySet(), done, criteria);
	}

	private static JsonObject json(Object payload) {
		return JsonParser.parseString(HttpJson.toJson(payload)).getAsJsonObject();
	}

	private static Map<String, Long> ordered(Object... pairs) {
		Map<String, Long> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
		}
		return map;
	}

	@Test
	void completedInCatalogOrderUnknownOnesLastByIdWithTimes() {
		// Done in another order, with one the catalog doesn't show and one without a time.
		PlayerRecord alex = player("Alex", 1, ordered("zzz:hidden", 5, STONE, 0, "aaa:gone", 7, ROOT, 1000), Map.of());
		JsonObject advancements = json(Advancements.ofPlayer(alex, List.of(alex), CATALOG));
		assertEquals(4, advancements.get("done").getAsInt());
		assertEquals(5, advancements.get("total").getAsInt());
		assertEquals(1, advancements.get("rank").getAsInt());
		JsonObject completed = advancements.getAsJsonObject("completed");
		assertEquals(List.of(ROOT, STONE, "aaa:gone", "zzz:hidden"), List.copyOf(completed.keySet()));
		assertEquals(1000, completed.get(ROOT).getAsLong());
		assertTrue(completed.get(STONE).isJsonNull(), "unknown time");
	}

	@Test
	void progressOnUnfinishedOnesWithSeveralRequirements() {
		PlayerRecord alex = player("Alex", 1, ordered(ROOT, 1000), Map.of(DIET, Map.of("bread", 2000L)));
		JsonObject progress = json(Advancements.ofPlayer(alex, List.of(alex), CATALOG)).getAsJsonObject("progress");
		// Root is done, the others have one requirement (EITHER has two criteria, but one requirement) or none known.
		assertEquals(Set.of(DIET), progress.keySet());
		assertEquals(1, progress.getAsJsonObject(DIET).get("done").getAsInt());
		assertEquals(2, progress.getAsJsonObject(DIET).get("total").getAsInt());
	}

	@Test
	void rankIsThePlaceOnTheBoardOfTheMost() {
		PlayerRecord one = player("One", 5, ordered(ROOT, 1), Map.of());
		PlayerRecord two = player("Two", 5, ordered(ROOT, 1, STONE, 2), Map.of());
		PlayerRecord tie = player("Tie", 1, ordered(ROOT, 1), Map.of());
		List<PlayerRecord> players = List.of(one, two, tie);
		assertEquals(1, json(Advancements.ofPlayer(two, players, CATALOG)).get("rank").getAsInt());
		// Equal counts: less play time first.
		assertEquals(2, json(Advancements.ofPlayer(tie, players, CATALOG)).get("rank").getAsInt());
		assertEquals(3, json(Advancements.ofPlayer(one, players, CATALOG)).get("rank").getAsInt());
	}

	@Test
	void requirementsAsTheGameKeepsThemWithTimes() {
		PlayerRecord alex = player("Alex", 1, ordered(), Map.of(DIET, Map.of("bread", 2000L)));
		JsonObject diet = json(Advancements.ofPlayer(alex, CATALOG.find().apply(DIET), List.of(alex), CATALOG));
		assertEquals("Alex", diet.getAsJsonObject("player").get("name").getAsString());
		assertFalse(diet.get("done").getAsBoolean());
		assertTrue(diet.get("completedAt").isJsonNull());
		assertEquals(0, diet.get("holders").getAsInt());
		JsonArray requirements = diet.getAsJsonArray("requirements");
		assertEquals(2, requirements.size());
		JsonObject apple = requirements.get(0).getAsJsonArray().get(0).getAsJsonObject();
		JsonObject bread = requirements.get(1).getAsJsonArray().get(0).getAsJsonObject();
		assertEquals("apple", apple.get("criterion").getAsString());
		assertTrue(apple.get("at").isJsonNull());
		assertEquals(2000, bread.get("at").getAsLong());
	}

	@Test
	void aDoneSingleCriterionGetsTheAdvancementsTime() {
		// The game keeps no criteria times for advancements with one criterion.
		PlayerRecord alex = player("Alex", 1, ordered(STONE, 3000), Map.of());
		JsonObject stone = json(Advancements.ofPlayer(alex, CATALOG.find().apply(STONE), List.of(alex), CATALOG));
		assertTrue(stone.get("done").getAsBoolean());
		assertEquals(3000, stone.get("completedAt").getAsLong());
		assertEquals(1, stone.get("holders").getAsInt());
		JsonObject criterion = stone.getAsJsonArray("requirements").get(0).getAsJsonArray().get(0).getAsJsonObject();
		assertEquals("get_stone", criterion.get("criterion").getAsString());
		assertEquals(3000, criterion.get("at").getAsLong());

		// A requirement of two criteria isn't one criterion: no time is made up for it.
		PlayerRecord bob = player("Bob", 1, ordered(EITHER, 4000), Map.of());
		JsonArray either = json(Advancements.ofPlayer(bob, CATALOG.find().apply(EITHER), List.of(bob), CATALOG))
			.getAsJsonArray("requirements").get(0).getAsJsonArray();
		assertEquals(2, either.size());
		assertTrue(either.get(0).getAsJsonObject().get("at").isJsonNull());
	}

	@Test
	void withoutKnownRequirementsEachMetCriterionIsOne() {
		Map<String, Long> times = new LinkedHashMap<>();
		times.put("first_step", 10L);
		times.put("second_step", 20L);
		PlayerRecord alex = player("Alex", 1, ordered(), Map.of(MODDED, times));
		JsonArray requirements = json(Advancements.ofPlayer(alex, CATALOG.find().apply(MODDED), List.of(alex), CATALOG))
			.getAsJsonArray("requirements");
		assertEquals(2, requirements.size());
		assertEquals("first_step", requirements.get(0).getAsJsonArray().get(0).getAsJsonObject().get("criterion").getAsString());
		assertEquals(20, requirements.get(1).getAsJsonArray().get(0).getAsJsonObject().get("at").getAsLong());
	}

	@Test
	void holdersEarliestFirstUnknownTimesLast() {
		PlayerRecord late = player("Late", 1, ordered(ROOT, 5000), Map.of());
		PlayerRecord early = player("early", 1, ordered(ROOT, 1000), Map.of());
		PlayerRecord unknownB = player("bee", 1, ordered(ROOT, 0), Map.of());
		PlayerRecord unknownA = player("Ant", 1, ordered(ROOT, 0), Map.of());
		PlayerRecord none = player("None", 1, ordered(), Map.of());
		JsonObject root = json(Advancements.detail(CATALOG.find().apply(ROOT), List.of(late, early, unknownB, unknownA, none)));
		assertEquals(4, root.get("holders").getAsInt());
		JsonArray entries = root.getAsJsonArray("entries");
		assertEquals(4, entries.size());
		List<String> names = entries.asList().stream().map(entry -> entry.getAsJsonObject().get("name").getAsString()).toList();
		assertEquals(List.of("early", "Late", "Ant", "bee"), names);
		assertEquals(1, entries.get(0).getAsJsonObject().get("rank").getAsInt());
		assertEquals(1000, entries.get(0).getAsJsonObject().get("completedAt").getAsLong());
		assertTrue(entries.get(3).getAsJsonObject().get("completedAt").isJsonNull());
	}

	@Test
	void listHasTheCatalogWithHolders() {
		PlayerRecord alex = player("Alex", 1, ordered(ROOT, 1), Map.of());
		PlayerRecord bob = player("Bob", 1, ordered(ROOT, 1, STONE, 2), Map.of());
		JsonObject list = json(Advancements.list(CATALOG, List.of(alex, bob)));
		assertEquals(5, list.get("total").getAsInt());
		JsonObject tab = list.getAsJsonArray("tabs").get(0).getAsJsonObject();
		assertEquals(Set.of("id", "title", "icon", "background"), tab.keySet());
		JsonArray advancements = list.getAsJsonArray("advancements");
		assertEquals(5, advancements.size());
		JsonObject root = advancements.get(0).getAsJsonObject();
		assertEquals(Set.of("id", "tab", "title", "description", "icon", "frame", "hidden", "experience", "parent", "x", "y",
			"holders"), root.keySet());
		assertEquals(ROOT, root.get("id").getAsString());
		assertTrue(root.get("parent").isJsonNull());
		assertEquals(2, root.get("holders").getAsInt());
		assertEquals(2.33, root.get("y").getAsDouble(), 1e-9);
		assertEquals(1, advancements.get(1).getAsJsonObject().get("holders").getAsInt());
		assertEquals(ROOT, advancements.get(1).getAsJsonObject().get("parent").getAsString());
	}
}
