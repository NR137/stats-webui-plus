package webui.stats.mc.stats;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import webui.stats.mc.MinecraftBootstrap;
import webui.stats.mc.TestPlatform;
import webui.stats.mc.web.LeaderboardCatalog;
import webui.stats.mc.web.LeaderboardInfo;

/**
 * A world folder on disk, read like on server start, and the API data built from it. The expected shapes are the
 * types the web UI reads (web/src/lib/types.ts).
 */
class StatsServiceTest {
	private static final Gson GSON = new Gson();

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
		Path file = StatsService.statsDirectory(world).resolve(uuid + ".json");
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
		return StatsService.load(world, names);
	}

	private static JsonObject json(Object payload) {
		return GSON.toJsonTree(payload).getAsJsonObject();
	}

	// --- Loading ---

	@Test
	void loadsEveryPlayerWithName() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		Set<String> loaded = new HashSet<>();
		players.forEach(player -> loaded.add(player.name));
		assertEquals(Set.of("Alex", "Steve", ".BedrockSteve", "Newcomer"), loaded);
		assertEquals(10, StatsService.find(players, "Alex").playHours, 0.001);
	}

	@Test
	void playerWithoutKnownNameStillShowsUp() throws IOException {
		UUID uuid = UUID.randomUUID();
		Path file = StatsService.statsDirectory(world).resolve(uuid + ".json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, Stats.player().hours(1).json().toString());

		List<PlayerRecord> players = StatsService.load(world, Map.of());
		assertEquals(1, players.size());
		assertNotNull(players.getFirst().name);
		assertFalse(players.getFirst().name.isBlank());
	}

	@Test
	void brokenAndForeignFilesAreSkipped() throws IOException {
		write("Alex", Stats.player().hours(1));
		Path stats = StatsService.statsDirectory(world);
		Files.writeString(stats.resolve(UUID.randomUUID() + ".json"), "{ broken");
		Files.writeString(stats.resolve("notes.txt"), "hello");
		Files.writeString(stats.resolve("not-a-uuid.json"), "{}");

		List<PlayerRecord> players = assertDoesNotThrow(() -> StatsService.load(world, names));
		assertTrue(players.stream().anyMatch(player -> player.name.equals("Alex")));
	}

	@Test
	void emptyWorldHasNoPlayers() {
		List<PlayerRecord> players = StatsService.load(world, names);
		assertTrue(players.isEmpty());
		JsonObject crowns = new Gson().newBuilder().serializeNulls().create().toJsonTree(StatsService.crownsPayload(players)).getAsJsonObject();
		for (String crown : List.of("champion", "dedicated", "efficient")) {
			assertTrue(crowns.has(crown) && crowns.get(crown).isJsonNull(), crowns.toString());
		}
	}

	@Test
	void findsPlayersByName() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		assertEquals("Steve", StatsService.find(players, "Steve").name);
		assertEquals(".BedrockSteve", StatsService.find(players, ".BedrockSteve").name);
		assertNull(StatsService.find(players, "Nobody"));
		assertNull(StatsService.find(players, "Steve.png"));
	}

	@Test
	void namesAreCaseInsensitiveLikeInMinecraft() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		assertEquals("Steve", StatsService.find(players, "steve").name);
	}

	// --- /api/players ---

	/** Sorted by play time; the web UI sorts it again for each page (sortRoster in api.ts). */
	@Test
	void playersListHasEveryoneOnceByPlayTime() throws IOException {
		JsonArray list = json(StatsService.playersPayload(sampleWorld())).getAsJsonArray("players");
		assertEquals(4, list.size());
		Set<String> seen = new HashSet<>();
		double previous = Double.MAX_VALUE;
		for (JsonElement element : list) {
			JsonObject player = element.getAsJsonObject();
			for (String key : List.of("name", "uuid", "playHours", "championScore", "scorePerHour")) {
				assertTrue(player.has(key), "missing " + key + " in " + player);
			}
			assertTrue(seen.add(player.get("name").getAsString()), list.toString());
			double hours = player.get("playHours").getAsDouble();
			assertTrue(hours <= previous, list.toString());
			previous = hours;
		}
	}

	// --- /api/players/<name> ---

	@Test
	void playerPageHasWhatTheWebUiReads() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonObject page = json(StatsService.playerPayload(StatsService.find(players, "Steve"), players));

		for (String key : List.of(
			"name", "uuid", "championScore", "championDisplay", "championRank", "playHours", "playHoursDisplay",
			"scorePerHour", "scorePerHourDisplay", "trackedPlayers", "firstJoinEstimated", "lastLogoutEstimated",
			"online", "grades", "stats"
		)) {
			assertTrue(page.has(key), "missing " + key);
		}
		assertEquals(1, page.get("championRank").getAsInt());
		assertEquals(4, page.get("trackedPlayers").getAsInt());
		for (Map.Entry<String, JsonElement> stat : page.getAsJsonObject("stats").entrySet()) {
			JsonObject value = stat.getValue().getAsJsonObject();
			for (String key : List.of("value", "display", "unit", "category", "rank")) {
				assertTrue(value.has(key), stat.getKey() + " missing " + key);
			}
		}
	}

	@Test
	void championRankFollowsTheScore() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		List<PlayerRecord> byScore = new ArrayList<>(players);
		byScore.sort((a, b) -> Double.compare(b.championScore, a.championScore));
		for (int i = 0; i < byScore.size(); i++) {
			PlayerRecord player = byScore.get(i);
			JsonObject page = json(StatsService.playerPayload(player, players));
			assertEquals(i + 1, page.get("championRank").getAsInt(), player.name);
		}
	}

	/**
	 * CHANGELOG: "vanilla stats the player never raised are sent as compact ID lists instead of one full row each, and
	 * the web UI rebuilds the identical rows". Rows plus zero IDs must give every stat exactly once.
	 */
	@Test
	void vanillaRowsAndZeroIdsCoverEveryStatOnce() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		PlayerRecord steve = StatsService.find(players, "Steve");
		JsonObject page = json(StatsService.playerPayload(steve, players));
		JsonObject vanilla = page.getAsJsonObject("vanilla");
		JsonObject zeros = page.has("vanillaZero") ? page.getAsJsonObject("vanillaZero") : new JsonObject();
		assertNotNull(vanilla, page.keySet().toString());

		for (String group : vanilla.keySet()) {
			List<String> ids = new ArrayList<>();
			for (JsonElement row : vanilla.getAsJsonArray(group)) {
				JsonObject stat = row.getAsJsonObject();
				for (String key : List.of("id", "value", "display", "unit", "rank")) {
					assertTrue(stat.has(key), group + " row missing " + key);
				}
				ids.add(stat.get("id").getAsString());
				// Values are in human units (hours, km). The web UI rebuilds zero rows as counts, so a zero sent as a
				// full row must be one with another unit.
				boolean zero = steve.vanillaRaw(group, stat.get("id").getAsString()) == 0;
				assertEquals(zero, stat.get("value").getAsDouble() == 0, group + " " + stat);
				if (zero) {
					assertNotEquals("count", stat.get("unit").getAsString(), group + " " + stat);
				}
			}
			if (zeros.has(group)) {
				for (Map.Entry<String, JsonElement> rank : zeros.getAsJsonObject(group).entrySet()) {
					assertTrue(Integer.parseInt(rank.getKey()) >= 1, group + " rank " + rank.getKey());
					for (Map.Entry<String, JsonElement> namespace : rank.getValue().getAsJsonObject().entrySet()) {
						for (JsonElement path : namespace.getValue().getAsJsonArray()) {
							String id = namespace.getKey().isEmpty() ? path.getAsString() : namespace.getKey() + ":" + path.getAsString();
							assertEquals(0, steve.vanillaRaw(group, id), group + " " + id);
							ids.add(id);
						}
					}
				}
			}
			assertEquals(ids.size(), new HashSet<>(ids).size(), group + " has an ID twice");
			assertEquals(Set.copyOf(StatsService.vanillaKeys(group, players)), Set.copyOf(ids), group);
		}
		assertTrue(vanilla.has("mined"), vanilla.keySet().toString());
	}

	/** A vanilla stat's rank is the player's place: one more than the number of players with a higher value. */
	@Test
	void vanillaRanksArePlaces() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		for (PlayerRecord player : players) {
			JsonObject page = json(StatsService.playerPayload(player, players));
			JsonObject vanilla = page.getAsJsonObject("vanilla");
			JsonObject zeros = page.has("vanillaZero") ? page.getAsJsonObject("vanillaZero") : new JsonObject();
			for (String group : vanilla.keySet()) {
				for (JsonElement row : vanilla.getAsJsonArray(group)) {
					String id = row.getAsJsonObject().get("id").getAsString();
					assertEquals(place(players, group, id, player), row.getAsJsonObject().get("rank").getAsInt(),
						player.name + " " + group + " " + id);
				}
				if (!zeros.has(group)) {
					continue;
				}
				for (Map.Entry<String, JsonElement> rank : zeros.getAsJsonObject(group).entrySet()) {
					for (Map.Entry<String, JsonElement> namespace : rank.getValue().getAsJsonObject().entrySet()) {
						for (JsonElement path : namespace.getValue().getAsJsonArray()) {
							String id = namespace.getKey().isEmpty() ? path.getAsString() : namespace.getKey() + ":" + path.getAsString();
							assertEquals(place(players, group, id, player), Integer.parseInt(rank.getKey()),
								player.name + " " + group + " " + id);
						}
					}
				}
			}
		}
	}

	private static int place(List<PlayerRecord> players, String group, String id, PlayerRecord target) {
		long value = target.vanillaRaw(group, id);
		return 1 + (int) players.stream().filter(other -> other.vanillaRaw(group, id) > value).count();
	}

	// --- /api/leaderboards/<id> ---

	@Test
	void leaderboardIsBestFirstWithoutZeros() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonObject board = json(StatsService.leaderboardPayload(StatsService.resolve("mined/minecraft:stone", players), players));
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
	void championBoardListsEveryone() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonArray entries = json(StatsService.leaderboardPayload(LeaderboardCatalog.find("champion"), players)).getAsJsonArray("entries");
		assertEquals(players.size(), entries.size());
	}

	@Test
	void lowerWinsBoardsPutTheLowestFirst() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		for (LeaderboardInfo info : LeaderboardCatalog.all()) {
			if (!info.lowerWins()) {
				continue;
			}
			JsonArray entries = json(StatsService.leaderboardPayload(info, players)).getAsJsonArray("entries");
			for (int i = 1; i < entries.size(); i++) {
				assertTrue(
					entries.get(i).getAsJsonObject().get("value").getAsDouble()
						>= entries.get(i - 1).getAsJsonObject().get("value").getAsDouble(),
					info.id() + " " + entries
				);
			}
		}
	}

	@Test
	void everyCatalogBoardWorks() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		for (LeaderboardInfo info : LeaderboardCatalog.all()) {
			JsonObject board = assertDoesNotThrow(() -> json(StatsService.leaderboardPayload(info, players)), info.id());
			assertEquals(info.id(), board.get("id").getAsString());
			assertTrue(board.has("entries"), info.id());
		}
	}

	@Test
	void resolvesBoards() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		assertNotNull(StatsService.resolve("champion", players));
		// Every vanilla stat has a board, also one nobody has raised yet.
		assertNotNull(StatsService.resolve("mined/minecraft:diamond_ore", players));
		assertNotNull(StatsService.resolve("mined/minecraft:deepslate_emerald_ore", players));
		assertNull(StatsService.resolve("mined/minecraft:no_such_block", players));
		assertNull(StatsService.resolve("nonsense/minecraft:stone", players));
		assertNull(StatsService.resolve("no_such_board", players));
	}

	// --- /api/crowns and /api/leaderboards ---

	@Test
	void crownsGoToTheRightPlayers() throws IOException {
		List<PlayerRecord> players = sampleWorld();
		JsonObject crowns = json(StatsService.crownsPayload(players));

		PlayerRecord champion = players.stream().max((a, b) -> Double.compare(a.championScore, b.championScore)).orElseThrow();
		PlayerRecord dedicated = players.stream().max((a, b) -> Double.compare(a.playHours, b.playHours)).orElseThrow();
		PlayerRecord efficient = players.stream().max((a, b) -> Double.compare(a.scorePerHour, b.scorePerHour)).orElseThrow();
		assertEquals(champion.name, crowns.getAsJsonObject("champion").get("name").getAsString());
		assertEquals(dedicated.name, crowns.getAsJsonObject("dedicated").get("name").getAsString());
		assertEquals(efficient.name, crowns.getAsJsonObject("efficient").get("name").getAsString());
		for (String crown : List.of("champion", "dedicated", "efficient")) {
			JsonObject value = crowns.getAsJsonObject(crown);
			for (String key : List.of("name", "uuid", "score", "display")) {
				assertTrue(value.has(key), crown + " missing " + key);
			}
		}
	}

	@Test
	void leaderboardsOverviewIsConsistent() throws IOException {
		JsonObject overview = json(StatsService.catalogPayload(sampleWorld()));
		Set<String> boards = new HashSet<>();
		for (JsonElement board : overview.getAsJsonArray("boards")) {
			boards.add(board.getAsJsonObject().get("id").getAsString());
		}
		for (JsonElement category : overview.getAsJsonArray("categories")) {
			JsonObject value = category.getAsJsonObject();
			assertTrue(value.has("id") && value.has("icon"), value.toString());
			for (JsonElement board : value.getAsJsonArray("boards")) {
				assertTrue(boards.contains(board.getAsJsonObject().get("id").getAsString()), board.toString());
			}
		}
	}
}
