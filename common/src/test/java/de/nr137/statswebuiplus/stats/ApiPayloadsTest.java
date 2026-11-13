package de.nr137.statswebuiplus.stats;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.nr137.statswebuiplus.MinecraftBootstrap;
import de.nr137.statswebuiplus.TestPlatform;
import de.nr137.statswebuiplus.api.AdvancementView;
import de.nr137.statswebuiplus.api.Boards;
import de.nr137.statswebuiplus.api.Players;
import de.nr137.statswebuiplus.api.Statistics;
import de.nr137.statswebuiplus.web.HttpJson;
import de.nr137.statswebuiplus.web.LeaderboardCatalog;
import de.nr137.statswebuiplus.web.LeaderboardInfo;

/**
 * A world folder on disk, read like on server start, and the API's answers built from it (the shapes of
 * openapi.yaml). Formerly StatsServiceTest: every case it checked, in the API's new shape.
 */
class ApiPayloadsTest {
	private static final AdvancementView NO_ADVANCEMENTS = AdvancementView.of(List.of(), List.of(), Map.of());

	@TempDir
	Path server;

	private Path world;
	private final Map<UUID, String> names = new HashMap<>();

	@BeforeAll
	static void minecraft() {
		MinecraftBootstrap.run();
	}

	@BeforeEach
	void setUp() throws IOException {
		TestPlatform.install(server);
		world = server.resolve("world");
		Files.createDirectories(world);
		SessionLog.get().boot(world);
	}

	private UUID write(String name, Stats stats) throws IOException {
		UUID uuid = UUID.nameUUIDFromBytes(name.getBytes());
		Path file = PlayerFiles.statsDirectory(world).resolve(uuid + ".json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, stats.json().toString());
		names.put(uuid, name);
		return uuid;
	}

	private List<PlayerRecord> sampleWorld() throws IOException {
		write("Alex", Stats.player().allRounder(1));
		write("Steve", Stats.player().allRounder(3));
		write(".BedrockSteve", Stats.player().hours(30).mined("stone", 500));
		write("Newcomer", Stats.player().hours(0.5));
		return PlayerFiles.load(world, names);
	}

	/** As the API sends it: nulls included. */
	private static JsonObject json(Object payload) {
		return JsonParser.parseString(HttpJson.toJson(payload)).getAsJsonObject();
	}

	private static JsonObject page(PlayerRecord player, List<PlayerRecord> players) {
		return json(Players.detail(player, players, new VanillaRanks(players), NO_ADVANCEMENTS));
	}

	private static JsonObject statisticBoard(String group, String id, List<PlayerRecord> players) {
		return json(Statistics.board(group, id, players, new VanillaRanks(players)));
	}

	// --- Loading ---

	@Test
	void loadsEveryPlayerWithName() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		Set<String> loaded = new HashSet<>();
		players.forEach(player -> loaded.add(player.name()));
		assertEquals(Set.of("Alex", "Steve", ".BedrockSteve", "Newcomer"), loaded);
		assertEquals(10, Players.find(players, "Alex").playHours(), 0.001);
	}

	@Test
	void playerWithoutKnownNameStillShowsUp() throws IOException {
		UUID uuid = UUID.randomUUID();
		Path file = PlayerFiles.statsDirectory(world).resolve(uuid + ".json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, Stats.player().hours(1).json().toString());

		List<PlayerRecord> players = PlayerFiles.load(world, Map.of());
		assertEquals(1, players.size());
		assertNotNull(players.getFirst().name());
		assertFalse(players.getFirst().name().isBlank());
	}

	@Test
	void brokenAndForeignFilesAreSkipped() throws IOException {
		write("Alex", Stats.player().hours(1));
		Path stats = PlayerFiles.statsDirectory(world);
		Files.writeString(stats.resolve(UUID.randomUUID() + ".json"), "{ broken");
		Files.writeString(stats.resolve("notes.txt"), "hello");
		Files.writeString(stats.resolve("not-a-uuid.json"), "{}");

		List<PlayerRecord> players = assertDoesNotThrow(() -> PlayerFiles.load(world, names));
		assertTrue(players.stream().anyMatch(player -> player.name().equals("Alex")));
	}

	@Test
	void emptyWorldHasNoPlayers() {
		List<PlayerRecord> players = PlayerFiles.load(world, names);
		assertTrue(players.isEmpty());
		JsonObject rankings = json(Boards.rankings(players));
		for (String ranking : List.of("legends", "veterans", "grinders")) {
			assertTrue(rankings.has(ranking) && rankings.get(ranking).isJsonNull(), rankings.toString());
		}
	}

	@Test
	void findsPlayersByName() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		assertEquals("Steve", Players.find(players, "Steve").name());
		assertEquals(".BedrockSteve", Players.find(players, ".BedrockSteve").name());
		assertNull(Players.find(players, "Nobody"));
		assertNull(Players.find(players, "Steve.png"));
	}

	@Test
	void namesAreCaseInsensitiveLikeInMinecraft() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		assertEquals("Steve", Players.find(players, "steve").name());
	}

	// --- /api/players ---

	/** Most play time first, then by name. */
	@Test
	void playersListHasEveryoneOnceByPlayTime() throws IOException {
		JsonArray list = json(Players.list(sampleWorld(), 0)).getAsJsonArray("players");
		assertEquals(4, list.size());
		Set<String> seen = new HashSet<>();
		double previous = Double.MAX_VALUE;
		for (JsonElement element : list) {
			JsonObject player = element.getAsJsonObject();
			for (String key : List.of("name", "uuid", "playHours", "level", "actionsPerHour", "advancements")) {
				assertTrue(player.has(key), "missing " + key + " in " + player);
			}
			assertTrue(player.getAsJsonObject("advancements").has("done"), player.toString());
			assertTrue(player.getAsJsonObject("advancements").has("total"), player.toString());
			assertTrue(seen.add(player.get("name").getAsString()), list.toString());
			double hours = player.get("playHours").getAsDouble();
			assertTrue(hours <= previous, list.toString());
			previous = hours;
		}
		// Steve and .BedrockSteve both have 30 hours: then by name, ignoring case and the dot.
		assertEquals(".BedrockSteve", list.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("Steve", list.get(1).getAsJsonObject().get("name").getAsString());
	}

	// --- /api/players/<name> ---

	@Test
	void playerPageHasWhatTheWebUiReads() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonObject page = page(Players.find(players, "Steve"), players);

		for (String key : List.of(
			"name", "uuid", "online", "firstJoin", "firstJoinEstimated", "lastLogout", "lastLogoutEstimated", "playHours",
			"level", "levelRank", "actionsPerHour", "trackedPlayers", "sameName", "skills", "boards", "statistics",
			"advancements"
		)) {
			assertTrue(page.has(key), "missing " + key);
		}
		assertEquals(1, page.get("levelRank").getAsInt());
		assertEquals(4, page.get("trackedPlayers").getAsInt());
		assertEquals(LeaderboardCatalog.all().size(), page.getAsJsonObject("boards").size());
		for (Map.Entry<String, JsonElement> board : page.getAsJsonObject("boards").entrySet()) {
			JsonObject value = board.getValue().getAsJsonObject();
			for (String key : List.of("value", "unit", "category", "rank")) {
				assertTrue(value.has(key), board.getKey() + " missing " + key);
			}
			assertTrue(value.get("value").getAsJsonPrimitive().isNumber(), board.getKey() + " " + value);
		}
		assertEquals(List.copyOf(Skills.ORDER), List.copyOf(page.getAsJsonObject("skills").keySet()));
		for (Map.Entry<String, JsonElement> skill : page.getAsJsonObject("skills").entrySet()) {
			JsonObject value = skill.getValue().getAsJsonObject();
			assertTrue(value.has("value") && value.has("parts"), skill.getKey());
			for (JsonElement part : value.getAsJsonArray("parts")) {
				for (String key : List.of("id", "value", "target", "weight", "score")) {
					assertTrue(part.getAsJsonObject().has(key), skill.getKey() + " part missing " + key);
				}
			}
		}
		assertEquals("skill", page.getAsJsonObject("boards").getAsJsonObject("skill-mining").get("unit").getAsString());
	}

	@Test
	void levelRankFollowsTheLevel() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		List<PlayerRecord> byScore = new ArrayList<>(players);
		// As the boards list them: by level, then less play time, then name (tied players get 1 and 2, not both 1).
		byScore.sort(java.util.Comparator.comparingDouble((PlayerRecord player) -> player.level()).reversed()
			.thenComparingDouble(player -> player.playHours())
			.thenComparing(player -> player.name().toLowerCase(java.util.Locale.ROOT)));
		for (int i = 0; i < byScore.size(); i++) {
			PlayerRecord player = byScore.get(i);
			assertEquals(i + 1, page(player, players).get("levelRank").getAsInt(), player.name());
		}
	}

	/**
	 * The page sends only the statistics the player raised (the web UI fills in the zeros from /api/stats): each
	 * once, every one of them, in /api/stats's order, and nothing at 0.
	 */
	@Test
	void statisticsHaveEveryRaisedStatOnceAndNoZeros() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		PlayerRecord steve = Players.find(players, "Steve");
		JsonObject statistics = page(steve, players).getAsJsonObject("statistics");
		assertEquals(VanillaStats.groups(), List.copyOf(statistics.keySet()));

		for (String group : statistics.keySet()) {
			List<String> ids = new ArrayList<>(statistics.getAsJsonObject(group).keySet());
			List<String> order = VanillaStats.keys(group, players);
			List<String> expected = new ArrayList<>();
			for (String id : order) {
				if (steve.vanillaRaw(group, id) != 0) {
					expected.add(id);
				}
			}
			assertEquals(expected, ids, group);
			for (String id : ids) {
				JsonObject stat = statistics.getAsJsonObject(group).getAsJsonObject(id);
				for (String key : List.of("value", "unit", "rank")) {
					assertTrue(stat.has(key), group + " " + id + " missing " + key);
				}
				assertNotEquals(0, stat.get("value").getAsDouble(), group + " " + id);
			}
		}
		assertTrue(statistics.getAsJsonObject("mined").size() > 0, statistics.toString());
		// Values in their units: 3 × 2 000 000 cm walked is 60 km, 30 hours of play time.
		JsonObject custom = statistics.getAsJsonObject("custom");
		assertEquals("km", custom.getAsJsonObject("minecraft:walk_one_cm").get("unit").getAsString());
		assertEquals(60, custom.getAsJsonObject("minecraft:walk_one_cm").get("value").getAsDouble(), 1e-9);
		assertEquals("hours", custom.getAsJsonObject("minecraft:play_time").get("unit").getAsString());
		assertEquals(30, custom.getAsJsonObject("minecraft:play_time").get("value").getAsDouble(), 1e-9);
		assertEquals("count", custom.getAsJsonObject("minecraft:deaths").get("unit").getAsString());
	}

	/** A statistic's rank is the player's place: one more than the number of players with a higher value. */
	@Test
	void vanillaRanksArePlaces() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		for (PlayerRecord player : players) {
			JsonObject statistics = page(player, players).getAsJsonObject("statistics");
			for (String group : statistics.keySet()) {
				for (Map.Entry<String, JsonElement> stat : statistics.getAsJsonObject(group).entrySet()) {
					assertEquals(place(players, group, stat.getKey(), player), stat.getValue().getAsJsonObject().get("rank").getAsInt(),
						player.name() + " " + group + " " + stat.getKey());
				}
			}
		}
	}

	private static int place(List<PlayerRecord> players, String group, String id, PlayerRecord target) {
		long value = target.vanillaRaw(group, id);
		return 1 + (int) players.stream().filter(other -> other.vanillaRaw(group, id) > value).count();
	}

	// --- /api/leaderboards/<id> and /api/stats/<group>/<statistic> ---

	@Test
	void leaderboardIsBestFirstWithoutZeros() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonObject board = statisticBoard("mined", "minecraft:stone", players);
		assertEquals("mined", board.get("group").getAsString());
		assertEquals("minecraft:stone", board.get("id").getAsString());
		assertEquals("count", board.get("unit").getAsString());
		JsonArray entries = board.getAsJsonArray("entries");

		assertEquals(".BedrockSteve", entries.get(0).getAsJsonObject().get("name").getAsString());
		for (int i = 0; i < entries.size(); i++) {
			JsonObject entry = entries.get(i).getAsJsonObject();
			assertEquals(i + 1, entry.get("rank").getAsInt(), entries.toString());
			assertTrue(entry.get("value").getAsDouble() > 0, entries.toString());
			if (i > 0) {
				assertTrue(entry.get("value").getAsDouble() <= entries.get(i - 1).getAsJsonObject().get("value").getAsDouble());
			}
		}
		assertFalse(entries.toString().contains("Newcomer"));
	}

	@Test
	void legendsBoardListsEveryone() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonArray entries = json(Boards.board(LeaderboardCatalog.find("legends"), players)).getAsJsonArray("entries");
		assertEquals(players.size(), entries.size());
	}

	@Test
	void lowerWinsBoardsStillListTheMostFirst() throws IOException {
		// Deaths: "lower wins" only on the compare page (fewer deaths wins the duel); the board itself, like every
		// board, starts with the highest value (decided 2026-11-07: the players with 0 deaths aren't on it at all, so
		// the fewest first would put a player with one death ahead of everyone who never died).
		List<PlayerRecord> players = sampleWorld();
		boolean checked = false;
		for (LeaderboardInfo info : LeaderboardCatalog.all()) {
			if (!info.lowerWins()) {
				continue;
			}
			JsonObject board = json(Boards.board(info, players));
			assertTrue(board.get("lowerWins").getAsBoolean(), info.id());
			JsonArray entries = board.getAsJsonArray("entries");
			for (int i = 1; i < entries.size(); i++) {
				checked = true;
				assertTrue(
					entries.get(i).getAsJsonObject().get("value").getAsDouble()
						<= entries.get(i - 1).getAsJsonObject().get("value").getAsDouble(),
					info.id() + " " + entries
				);
			}
		}
		assertTrue(checked, "some lower-wins board has more than one player");
	}

	@Test
	void everyCatalogBoardWorks() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		for (LeaderboardInfo info : LeaderboardCatalog.all()) {
			JsonObject board = assertDoesNotThrow(() -> json(Boards.board(info, players)), info.id());
			assertEquals(info.id(), board.get("id").getAsString());
			assertTrue(board.has("entries"), info.id());
			assertEquals(!PlayerMetrics.parts(info.id()).isEmpty(), board.has("parts"), info.id());
		}
	}

	@Test
	void resolvesBoards() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		VanillaRanks ranks = new VanillaRanks(players);
		assertNotNull(LeaderboardCatalog.find("legends"));
		// Every statistic has a board, also one nobody has raised yet.
		assertNotNull(Statistics.board("mined", "minecraft:diamond_ore", players, ranks));
		assertNotNull(Statistics.board("mined", "minecraft:deepslate_emerald_ore", players, ranks));
		assertNull(Statistics.board("mined", "minecraft:no_such_block", players, ranks));
		assertNull(Statistics.board("nonsense", "minecraft:stone", players, ranks));
		assertNull(LeaderboardCatalog.find("no_such_board"));
	}

	// --- /api/rankings and /api/leaderboards ---

	@Test
	void rankingsGoToTheRightPlayers() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonObject rankings = json(Boards.rankings(players));

		// The highest value; on a tie less play time, then the name (Steve and .BedrockSteve both have 30 hours).
		java.util.function.Function<java.util.function.ToDoubleFunction<PlayerRecord>, PlayerRecord> best = value -> players.stream()
			.min(java.util.Comparator.comparingDouble(value).reversed()
				.thenComparingDouble((PlayerRecord player) -> player.playHours())
				.thenComparing(player -> player.name(), String.CASE_INSENSITIVE_ORDER))
			.orElseThrow();
		PlayerRecord legend = best.apply(player -> player.level());
		PlayerRecord veteran = best.apply(player -> player.playHours());
		PlayerRecord active = best.apply(player -> player.actionsPerHour());
		assertEquals(".BedrockSteve", veteran.name(), "the tie is decided by the name");
		assertEquals(legend.name(), rankings.getAsJsonObject("legends").get("name").getAsString());
		assertEquals(veteran.name(), rankings.getAsJsonObject("veterans").get("name").getAsString());
		assertEquals(active.name(), rankings.getAsJsonObject("grinders").get("name").getAsString());
		Map<String, String> units = Map.of("legends", "level", "veterans", "hours", "grinders", "actions_per_hour");
		for (String ranking : List.of("legends", "veterans", "grinders")) {
			JsonObject value = rankings.getAsJsonObject(ranking);
			for (String key : List.of("name", "uuid", "value", "unit")) {
				assertTrue(value.has(key), ranking + " missing " + key);
			}
			assertEquals(units.get(ranking), value.get("unit").getAsString());
		}
		assertEquals(30, rankings.getAsJsonObject("veterans").get("value").getAsDouble(), 1e-9);
	}

	@Test
	void leaderboardsOverviewIsConsistent() {
		JsonObject overview = json(Boards.overview());
		Set<String> boards = new HashSet<>();
		for (JsonElement board : overview.getAsJsonArray("boards")) {
			JsonObject value = board.getAsJsonObject();
			boards.add(value.get("id").getAsString());
			for (String key : List.of("id", "category", "unit", "icon", "listed", "compare", "lowerWins")) {
				assertTrue(value.has(key), value + " missing " + key);
			}
		}
		assertEquals(LeaderboardCatalog.all().size(), boards.size());
		List<String> categories = new ArrayList<>();
		for (JsonElement category : overview.getAsJsonArray("categories")) {
			JsonObject value = category.getAsJsonObject();
			assertTrue(value.has("id") && value.has("icon"), value.toString());
			categories.add(value.get("id").getAsString());
			for (JsonElement board : value.getAsJsonArray("boards")) {
				assertTrue(boards.contains(board.getAsString()), board.toString());
			}
		}
		assertEquals(LeaderboardCatalog.listedCategories(), categories);
	}

	/**
	 * Statistics: each statistic someone has shows its leader and their value, the same player and
	 * value as first place on its board; the ones nobody has have none.
	 */
	@Test
	void overviewNamesEachStatisticsLeader() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonObject groups = json(Statistics.overview(new VanillaRanks(players))).getAsJsonObject("groups");
		assertEquals(VanillaStats.groups(), List.copyOf(groups.keySet()));
		Map<String, JsonObject> mined = byId(groups.getAsJsonArray("mined"));
		JsonObject first = statisticBoard("mined", "minecraft:stone", players).getAsJsonArray("entries").get(0).getAsJsonObject();
		JsonObject stone = mined.get("minecraft:stone").getAsJsonObject("leader");
		assertEquals(first.get("name").getAsString(), stone.get("name").getAsString());
		assertEquals(first.get("uuid").getAsString(), stone.get("uuid").getAsString());
		assertEquals(first.get("value").getAsDouble(), stone.get("value").getAsDouble());
		assertTrue(mined.containsKey("minecraft:sponge"), "the page still lists it");
		assertFalse(mined.get("minecraft:sponge").has("leader"), "nobody mined a sponge: no leader");
		assertFalse(mined.get("minecraft:stone").has("unit"), "counts leave their unit out");
		assertEquals(VanillaStats.keys("mined", players), List.copyOf(mined.keySet()));
		// Distances in km, like on their board.
		for (Map.Entry<String, JsonObject> stat : byId(groups.getAsJsonArray("custom")).entrySet()) {
			if (stat.getKey().endsWith("_one_cm")) {
				assertEquals("km", stat.getValue().get("unit").getAsString(), stat.getKey());
				if (stat.getValue().has("leader")) {
					JsonObject top = statisticBoard("custom", stat.getKey(), players).getAsJsonArray("entries").get(0).getAsJsonObject();
					assertEquals(top.get("value").getAsDouble(), stat.getValue().getAsJsonObject("leader").get("value").getAsDouble(),
						stat.getKey());
				}
			}
		}
	}

	private static Map<String, JsonObject> byId(JsonArray statistics) {
		Map<String, JsonObject> byId = new java.util.LinkedHashMap<>();
		for (JsonElement stat : statistics) {
			byId.put(stat.getAsJsonObject().get("id").getAsString(), stat.getAsJsonObject());
		}
		return byId;
	}

	// --- README, Floodgate support: players sharing a name ---

	private static PlayerRecord bedrockPlayer(long xuid, String name) {
		return PlayerMetrics.compute(new UUID(0, xuid), name, new StatFile(Stats.player().hours(1).json()));
	}

	@Test
	void playersSharingANameAreToldApart() {
		// Floodgate cuts long gamertags to 15 letters after its "." and turns spaces into "_": two Bedrock players can
		// end up as ".LongGamertagNam", and "John Doe" and "John_Doe" both as ".John_Doe".
		PlayerRecord first = bedrockPlayer(2535400000000011L, ".LongGamertagNam");
		PlayerRecord second = bedrockPlayer(2535400000000012L, ".LongGamertagNam");
		PlayerRecord lower = bedrockPlayer(2535400000000013L, ".john_doe");
		PlayerRecord upper = bedrockPlayer(2535400000000014L, ".John_Doe");
		List<PlayerRecord> players = List.of(first, second, lower, upper, Stats.player().hours(2).compute("Alex"));

		assertEquals(List.of(".john_doe", ".longgamertagnam"), Players.nameClashes(players));
		// By UUID each one; by name the exact spelling first.
		assertSame(second, Players.find(players, second.uuid().toString()));
		assertSame(upper, Players.find(players, ".John_Doe"));
		assertSame(lower, Players.find(players, ".john_doe"));

		JsonArray others = page(first, players).getAsJsonArray("sameName");
		assertEquals(1, others.size());
		assertEquals(second.uuid().toString(), others.get(0).getAsJsonObject().get("uuid").getAsString());
		assertEquals(0, page(players.get(4), players).getAsJsonArray("sameName").size());
	}
}
