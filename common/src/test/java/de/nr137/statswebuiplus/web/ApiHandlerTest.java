package de.nr137.statswebuiplus.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import de.nr137.statswebuiplus.MinecraftBootstrap;
import de.nr137.statswebuiplus.TestPlatform;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.api.AdvancementView;
import de.nr137.statswebuiplus.stats.AdvancementInfo;
import de.nr137.statswebuiplus.stats.AdvancementTab;
import de.nr137.statswebuiplus.stats.Skills;
import de.nr137.statswebuiplus.stats.PlayerMetrics;
import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.stats.SessionLog;
import de.nr137.statswebuiplus.stats.StatFile;
import de.nr137.statswebuiplus.stats.StatsCache;
import de.nr137.statswebuiplus.stats.VanillaRanks;

/**
 * The API over HTTP (openapi.yaml): every endpoint, the ID forms in paths, errors, caching and compression, with three
 * players and a small advancement catalog instead of a running server.
 */
class ApiHandlerTest {
	private static final String ROOT = "minecraft:story/root";
	private static final String QUEST = "somemod:quests/first";

	@TempDir
	Path folder;

	private TestServer server;
	private WebConfig config;
	private Source source;
	private PlayerRecord steve;

	@BeforeAll
	static void minecraft() {
		MinecraftBootstrap.run();
	}

	/** The players of the test; refresh can be made to fail like a busy server. */
	private static final class Source implements ApiHandler.Source {
		final StatsCache.Snapshot snapshot;
		Exception failure;
		int refreshes;

		Source(List<PlayerRecord> players) {
			snapshot = new StatsCache.Snapshot(List.copyOf(players), System.nanoTime(), new VanillaRanks(players));
		}

		@Override
		public StatsCache.Snapshot current() {
			return snapshot;
		}

		@Override
		public StatsCache.Snapshot refresh(long maxAgeMs) throws Exception {
			refreshes++;
			if (failure != null) {
				throw failure;
			}
			return snapshot;
		}

		@Override
		public int onlinePlayers() {
			return 1;
		}

		@Override
		public int maxPlayers() {
			return 20;
		}
	}

	private static PlayerRecord player(String name, long hours, Map<String, Map<String, Long>> stats) {
		JsonObject groups = new JsonObject();
		stats.forEach((group, values) -> {
			JsonObject json = new JsonObject();
			values.forEach(json::addProperty);
			groups.add("minecraft:" + group, json);
		});
		JsonObject custom = groups.has("minecraft:custom") ? groups.getAsJsonObject("minecraft:custom") : new JsonObject();
		custom.addProperty("minecraft:play_time", hours * 72_000);
		groups.add("minecraft:custom", custom);
		JsonObject root = new JsonObject();
		root.add("stats", groups);
		return PlayerMetrics.compute(UUID.nameUUIDFromBytes(name.getBytes()), name, new StatFile(root));
	}

	@BeforeEach
	void setUp() throws IOException {
		TestPlatform.install(folder);
		Path world = folder.resolve("world");
		Files.createDirectories(world);
		SessionLog.get().boot(world);
		Files.writeString(WebConfig.path(), "{\"refreshCooldownMs\": 15000, \"headerImage\": \"\","
			+ " \"pages\": [{\"id\": \"rules\", \"label\": \"Rules\"}]}");
		config = WebConfig.load();
		Path rules = folder.resolve("config/stats_webui_plus/pages/rules");
		Files.createDirectories(rules);
		Files.writeString(rules.resolve("en.md"), "# Rules");
		Files.writeString(rules.resolve("de.md"), "# Regeln");

		List<PlayerRecord> players = new ArrayList<>();
		players.add(player("Steve", 30, Map.of(
			"mined", Map.of("minecraft:stone", 500L),
			"custom", Map.of("minecraft:walk_one_cm", 250_000L, "minecraft:jump", 40L)
		)).withAdvancements(Set.of(ROOT), Map.of(ROOT, 1784418248000L), Map.of()));
		players.add(player("+Plus", 2, Map.of("mined", Map.of("minecraft:stone", 20L))));
		players.add(player("Alex", 10, Map.of("custom", Map.of("minecraft:jump", 100L)))
			.withAdvancements(Set.of(ROOT, QUEST), Map.of(), Map.of(QUEST, Map.of("first_step", 5L))));
		players = Skills.apply(players);
		steve = players.stream().filter(player -> player.name().equals("Steve")).findFirst().orElseThrow();
		source = new Source(players);
		AdvancementView catalog = AdvancementView.of(
			List.of(
				new AdvancementInfo(ROOT, "story", "Minecraft", "The heart of the game", "minecraft:grass_block", "task", false, 0,
					null, 0, 0),
				new AdvancementInfo(QUEST, "quests", "First quest", "", "minecraft:book", "goal", false, 10, null, 0, 0)
			),
			List.of(new AdvancementTab("story", "Minecraft", "minecraft:grass_block", "stone")),
			Map.of(ROOT, List.of(List.of("crafting_table")))
		);
		server = new TestServer(new ApiHandler(() -> config, source, () -> catalog)::handle);
	}

	@AfterEach
	void tearDown() {
		server.close();
	}

	private JsonObject json(String path) throws IOException {
		TestServer.Response response = server.get(path);
		assertEquals(200, response.status(), path + " -> " + response.text());
		assertEquals("application/json; charset=utf-8", response.header("Content-Type"), path);
		return JsonParser.parseString(response.text()).getAsJsonObject();
	}

	private void assertError(String path, int status, String code, String message) throws IOException {
		TestServer.Response response = server.get(path);
		assertEquals(status, response.status(), path + " -> " + response.text());
		JsonObject error = JsonParser.parseString(response.text()).getAsJsonObject();
		assertEquals(code, error.get("error").getAsString(), path);
		assertEquals(message, error.get("message").getAsString(), path);
		assertEquals("no-store", response.header("Cache-Control"), "errors are never cached: " + path);
	}

	// --- Without the stats ---

	@Test
	void indexListsEveryEndpoint() throws IOException {
		TestServer.Response response = server.get("/api");
		assertEquals("no-store", response.header("Cache-Control"));
		JsonObject index = JsonParser.parseString(response.text()).getAsJsonObject();
		assertEquals("stats_webui_plus", index.get("mod").getAsString());
		assertEquals("0.0.0-test", index.get("version").getAsString());
		assertEquals("26.3", index.get("minecraft").getAsString());
		assertEquals(2, index.get("apiVersion").getAsInt());
		assertEquals("/api/openapi.yaml", index.get("documentation").getAsString());
		assertEquals(16, index.getAsJsonObject("endpoints").size());
		assertEquals(0, source.refreshes, "no stats refresh");
	}

	@Test
	void health() throws IOException {
		JsonObject health = json("/api/health");
		assertTrue(health.get("ok").getAsBoolean());
		assertEquals("stats_webui_plus", health.get("mod").getAsString());
		assertEquals(Set.of("ok", "mod", "version", "minecraft"), health.keySet());
		assertEquals(0, source.refreshes);
	}

	/** The description is served, and it has exactly the endpoints the index lists. */
	@Test
	void openApiDescribesEveryEndpoint() throws IOException {
		TestServer.Response response = server.get("/api/openapi.yaml");
		assertEquals(200, response.status());
		assertEquals("application/yaml; charset=utf-8", response.header("Content-Type"));
		assertEquals("no-cache", response.header("Cache-Control"));
		assertEquals(304, server.get("/api/openapi.yaml", "If-None-Match", response.header("ETag")).status());
		String yaml = response.text();
		assertTrue(yaml.startsWith("openapi: 3.1"), yaml.substring(0, 40));
		Set<String> described = new java.util.TreeSet<>();
		Matcher path = Pattern.compile("(?m)^  (/api[^:]*):$").matcher(yaml);
		while (path.find()) {
			described.add(path.group(1));
		}
		Set<String> listed = new java.util.TreeSet<>();
		for (Map.Entry<String, JsonElement> endpoint : json("/api").getAsJsonObject("endpoints").entrySet()) {
			listed.add(endpoint.getValue().getAsString());
		}
		assertEquals(listed, described);
	}

	@Test
	void statusWithoutRefreshOrBind() throws IOException {
		TestServer.Response response = server.get("/api/status");
		assertEquals("no-store", response.header("Cache-Control"));
		JsonObject status = JsonParser.parseString(response.text()).getAsJsonObject();
		assertEquals(Set.of("trackedPlayers", "nameClashes", "onlinePlayers", "maxPlayers", "branding", "headerImage",
			"customFont", "languages", "links", "pages", "startPage", "defaultLanguage", "gameAssets"), status.keySet());
		assertEquals(3, status.get("trackedPlayers").getAsInt());
		assertEquals(1, status.get("onlinePlayers").getAsInt());
		assertEquals(20, status.get("maxPlayers").getAsInt());
		assertTrue(status.get("headerImage").isJsonNull());
		assertTrue(status.get("customFont").isJsonNull());
		assertTrue(Set.of("ready", "preparing", "failed", "disabled").contains(status.get("gameAssets").getAsString()));
		assertEquals("rules", status.getAsJsonArray("pages").get(0).getAsJsonObject().get("id").getAsString());
		assertEquals(0, source.refreshes);
	}

	@Test
	void pagesInTheVisitorsLanguage() throws IOException {
		TestServer.Response response = server.get("/api/pages/rules?lang=it,de");
		assertEquals(200, response.status());
		assertEquals("no-cache", response.header("Cache-Control"));
		JsonObject page = JsonParser.parseString(response.text()).getAsJsonObject();
		assertEquals("de", page.get("language").getAsString());
		assertEquals("# Regeln", page.get("content").getAsString());
		assertEquals("en", json("/api/pages/rules").get("language").getAsString());
		assertEquals(304, server.get("/api/pages/rules", "If-None-Match", server.get("/api/pages/rules").header("ETag")).status());
		assertError("/api/pages/imprint", 404, "not_found", "Unknown page");
		assertEquals(0, source.refreshes);
	}

	// --- Players ---

	@Test
	void playersAreCachedUntilTheNextRefresh() throws IOException {
		TestServer.Response response = server.get("/api/players");
		assertEquals(200, response.status());
		Matcher maxAge = Pattern.compile("public, max-age=(\\d+)").matcher(response.header("Cache-Control"));
		assertTrue(maxAge.matches(), response.header("Cache-Control"));
		assertTrue(Integer.parseInt(maxAge.group(1)) <= 15);
		assertEquals(1, source.refreshes);
		JsonArray players = JsonParser.parseString(response.text()).getAsJsonObject().getAsJsonArray("players");
		assertEquals("Steve", players.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals(2, players.get(0).getAsJsonObject().getAsJsonObject("advancements").get("total").getAsInt());
		assertEquals(304, server.get("/api/players", "If-None-Match", response.header("ETag")).status());
	}

	@Test
	void withoutCooldownNothingIsKept() throws IOException {
		Files.writeString(WebConfig.path(), "{\"refreshCooldownMs\": 0}");
		config = WebConfig.load();
		assertEquals("no-store", server.get("/api/players").header("Cache-Control"));
	}

	/** A player in a path: the name as sent (decoded once), any case, or the UUID. */
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"Steve", "steve", "STEVE", "Steve/"})
	void playerByName(String name) throws IOException {
		assertEquals("Steve", json("/api/players/" + name).get("name").getAsString());
	}

	@Test
	void playerByUuidAndNamesWithPlus() throws IOException {
		assertEquals("Steve", json("/api/players/" + steve.uuid()).get("name").getAsString());
		// "+" stays a "+": the path is decoded once.
		assertEquals("+Plus", json("/api/players/%2BPlus").get("name").getAsString());
		assertError("/api/players/Plus", 404, "not_found", "Player not found");
		assertError("/api/players/Nobody", 404, "not_found", "Player not found");
	}

	@Test
	void playerPage() throws IOException {
		JsonObject page = json("/api/players/Steve");
		assertEquals(steve.uuid().toString(), page.get("uuid").getAsString());
		assertEquals(30, page.get("playHours").getAsInt());
		assertEquals(3, page.get("trackedPlayers").getAsInt());
		JsonObject custom = page.getAsJsonObject("statistics").getAsJsonObject("custom");
		assertEquals(2.5, custom.getAsJsonObject("minecraft:walk_one_cm").get("value").getAsDouble(), 1e-9);
		assertEquals("km", custom.getAsJsonObject("minecraft:walk_one_cm").get("unit").getAsString());
		// Alex jumped more.
		assertEquals(2, custom.getAsJsonObject("minecraft:jump").get("rank").getAsInt());
		assertFalse(page.getAsJsonObject("statistics").getAsJsonObject("killed").has("minecraft:zombie"));
		JsonObject advancements = page.getAsJsonObject("advancements");
		assertEquals(1784418248000L, advancements.getAsJsonObject("completed").get(ROOT).getAsLong());
		assertEquals(2, advancements.get("total").getAsInt());
		// Most play time: first on veterans.
		assertEquals(1, page.getAsJsonObject("boards").getAsJsonObject("veterans").get("rank").getAsInt());
		assertEquals("hours", page.getAsJsonObject("boards").getAsJsonObject("veterans").get("unit").getAsString());
	}

	/** The advancement in the website's form or the full one, also with slashes and other namespaces. */
	@ParameterizedTest(name = "{0}")
	@CsvSource({
		"/api/players/Steve/advancements/story/root,           minecraft:story/root, true",
		"/api/players/Steve/advancements/minecraft:story/root, minecraft:story/root, true",
		"/api/players/Steve/advancements/minecraft%3Astory/root, minecraft:story/root, true",
		"/api/players/Alex/advancements/somemod:quests/first,  somemod:quests/first, true",
		"/api/players/Steve/advancements/somemod:quests/first, somemod:quests/first, false",
	})
	void playerAdvancement(String path, String id, boolean done) throws IOException {
		JsonObject advancement = json(path);
		assertEquals(id, advancement.get("id").getAsString());
		assertEquals(done, advancement.get("done").getAsBoolean());
		assertTrue(advancement.has("requirements") && advancement.has("player") && advancement.has("holders"));
	}

	@Test
	void playerAdvancementErrors() throws IOException {
		assertError("/api/players/Nobody/advancements/story/root", 404, "not_found", "Player not found");
		assertError("/api/players/Steve/advancements/story/nothing", 404, "not_found", "Unknown advancement");
		assertError("/api/players/Steve/advancements", 404, "not_found", "Unknown endpoint");
		assertError("/api/players/Steve/other", 404, "not_found", "Unknown endpoint");
	}

	@Test
	void requirementsWithoutTheCatalogsAreTheMetCriteria() throws IOException {
		JsonArray requirements = json("/api/players/Alex/advancements/somemod:quests/first").getAsJsonArray("requirements");
		assertEquals(1, requirements.size());
		JsonObject criterion = requirements.get(0).getAsJsonArray().get(0).getAsJsonObject();
		assertEquals("first_step", criterion.get("criterion").getAsString());
		assertEquals(5, criterion.get("at").getAsLong());
	}

	// --- Boards ---

	@Test
	void rankingsAndLeaderboards() throws IOException {
		JsonObject rankings = json("/api/rankings");
		assertEquals("Steve", rankings.getAsJsonObject("veterans").get("name").getAsString());
		assertEquals("hours", rankings.getAsJsonObject("veterans").get("unit").getAsString());
		JsonObject overview = json("/api/leaderboards");
		assertTrue(overview.has("categories") && overview.has("boards"));
		JsonObject legends = json("/api/leaderboards/legends");
		assertEquals("level", legends.get("unit").getAsString());
		assertEquals(3, legends.getAsJsonArray("entries").size(), "legends lists everyone");
		JsonObject ores = json("/api/leaderboards/ores");
		assertTrue(ores.has("parts"));
		assertFalse(json("/api/leaderboards/play-time").has("parts"));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"/api/leaderboards/nothing", "/api/leaderboards/mined/minecraft:stone", "/api/leaderboards/mined"})
	void unknownLeaderboards(String path) throws IOException {
		// The game's statistics are under /api/stats now.
		assertError(path, 404, "not_found", "Unknown leaderboard");
	}

	@Test
	void mostAdvancements() throws IOException {
		JsonObject most = json("/api/advancements/most");
		assertEquals("advancements", most.get("id").getAsString());
		JsonArray entries = most.getAsJsonArray("entries");
		assertEquals("Alex", entries.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals(2, entries.size(), "+Plus has none");
	}

	// --- The game's statistics ---

	@Test
	void statisticsOverview() throws IOException {
		JsonObject groups = json("/api/stats").getAsJsonObject("groups");
		assertEquals(List.of("custom", "killed", "killed_by", "mined", "crafted", "used", "broken", "picked_up", "dropped"),
			List.copyOf(groups.keySet()));
		JsonObject stone = null;
		for (JsonElement stat : groups.getAsJsonArray("mined")) {
			if (stat.getAsJsonObject().get("id").getAsString().equals("minecraft:stone")) {
				stone = stat.getAsJsonObject();
			}
		}
		assertNotNull(stone);
		assertEquals("Steve", stone.getAsJsonObject("leader").get("name").getAsString());
		assertEquals(500, stone.getAsJsonObject("leader").get("value").getAsInt());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"/api/stats/mined/stone", "/api/stats/mined/minecraft:stone", "/api/stats/mined/minecraft%3Astone"})
	void statisticInEitherIdForm(String path) throws IOException {
		JsonObject board = json(path);
		assertEquals("mined", board.get("group").getAsString());
		assertEquals("minecraft:stone", board.get("id").getAsString());
		JsonArray entries = board.getAsJsonArray("entries");
		assertEquals(2, entries.size());
		assertEquals("Steve", entries.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals(1, entries.get(0).getAsJsonObject().get("rank").getAsInt());
		assertEquals(20, entries.get(1).getAsJsonObject().get("value").getAsInt());
	}

	@Test
	void statisticValuesInTheirUnit() throws IOException {
		JsonObject walk = json("/api/stats/custom/walk_one_cm");
		assertEquals("km", walk.get("unit").getAsString());
		assertEquals(2.5, walk.getAsJsonArray("entries").get(0).getAsJsonObject().get("value").getAsDouble(), 1e-9);
	}

	@Test
	void unknownStatistics() throws IOException {
		assertError("/api/stats/nonsense/stone", 404, "not_found", "Unknown statistic");
		assertError("/api/stats/mined/no_such_block", 404, "not_found", "Unknown statistic");
		assertError("/api/stats/mined", 404, "not_found", "Unknown endpoint");
	}

	// --- Advancements ---

	@Test
	void advancements() throws IOException {
		JsonObject list = json("/api/advancements");
		assertEquals(2, list.get("total").getAsInt());
		assertEquals(2, list.getAsJsonArray("advancements").get(0).getAsJsonObject().get("holders").getAsInt());
		JsonObject root = json("/api/advancements/story/root");
		assertEquals(ROOT, root.get("id").getAsString());
		assertEquals(List.of("Steve", "Alex"), root.getAsJsonArray("entries").asList().stream()
			.map(entry -> entry.getAsJsonObject().get("name").getAsString()).toList(), "unknown times last");
		assertEquals(QUEST, json("/api/advancements/somemod:quests/first").get("id").getAsString());
		assertEquals(ROOT, json("/api/advancements/minecraft:story/root").get("id").getAsString());
		assertError("/api/advancements/story/nothing", 404, "not_found", "Unknown advancement");
	}

	// --- Common rules ---

	@Test
	void onlyGetAndHead() throws IOException {
		TestServer.Response post = server.request("POST", "/api/players");
		assertEquals(405, post.status());
		assertEquals("GET, HEAD", post.header("Allow"));
		JsonObject error = JsonParser.parseString(post.text()).getAsJsonObject();
		assertEquals("method_not_allowed", error.get("error").getAsString());
		assertEquals("Use GET", error.get("message").getAsString());

		TestServer.Response head = server.request("HEAD", "/api/players");
		assertEquals(200, head.status());
		assertEquals(0, head.body().length);
		assertNotNull(head.header("ETag"));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"/api/nothing", "/api/players/Steve/advancements/", "/api/rankings/x", "/api/healthz", "/apix"})
	void unknownEndpoints(String path) throws IOException {
		TestServer.Response response = server.get(path);
		assertEquals(404, response.status(), path);
		JsonObject error = JsonParser.parseString(response.text()).getAsJsonObject();
		assertEquals("not_found", error.get("error").getAsString());
	}

	@Test
	void trailingSlashIsIgnored() throws IOException {
		assertEquals(json("/api/players").toString(), json("/api/players/").toString());
		assertTrue(json("/api/").has("endpoints"));
	}

	@Test
	void bigAnswersAreGzipped() throws IOException {
		TestServer.Response response = server.get("/api/stats", "Accept-Encoding", "gzip");
		assertEquals("gzip", response.header("Content-Encoding"));
		assertEquals("Accept-Encoding", response.header("Vary"));
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
			JsonObject stats = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			assertTrue(stats.has("groups"));
		}
	}

	@Test
	void busyServerIs503WithRetryAfter() throws IOException {
		source.failure = new TimeoutException("lag spike");
		TestServer.Response response = server.get("/api/players");
		assertEquals(503, response.status());
		assertEquals("5", response.header("Retry-After"));
		JsonObject error = JsonParser.parseString(response.text()).getAsJsonObject();
		assertEquals("busy", error.get("error").getAsString());
		assertEquals("The Minecraft server is busy, try again in a moment", error.get("message").getAsString());
		// What needs no stats still answers.
		assertEquals(200, server.get("/api/status").status());
		assertEquals(200, server.get("/api/health").status());
	}

	@Test
	void failureIs500() throws IOException {
		source.failure = new IllegalStateException("broken");
		TestServer.Response response = server.get("/api/rankings");
		assertEquals(500, response.status());
		assertEquals("internal_error", JsonParser.parseString(response.text()).getAsJsonObject().get("error").getAsString());
	}

	@Test
	void unknownPathsDontRefresh() throws IOException {
		server.get("/api/nothing");
		assertEquals(0, source.refreshes);
	}
}
