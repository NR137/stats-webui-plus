package de.nr137.statswebuiplus.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import de.nr137.statswebuiplus.WebConfig;
import de.nr137.statswebuiplus.loadtest.TestPlayers;
import de.nr137.statswebuiplus.stats.PlayerRecord;
import de.nr137.statswebuiplus.stats.SessionLog;
import de.nr137.statswebuiplus.stats.StatsCache;
import de.nr137.statswebuiplus.stats.PlayerFiles;
import de.nr137.statswebuiplus.web.StatsHttpServer;

/**
 * What only a running server with a player shows: joins and leaves through the loader's events, stats and
 * advancements through the mixins, and both reaching the stats cache the API serves (README, "Stats cache"). Each
 * loader registers these as game tests (fabric/src/gametest, neoforge/src/gametest); a test player logs in through the server's
 * own login path (see joinPlayer).
 */
public final class LiveTests {
	private LiveTests() {
	}

	/**
	 * Tests that block the server thread or change the shared config. Each loader puts them in a test environment of
	 * their own: game tests of one environment run at the same time, environments one after another.
	 */
	public static final java.util.Set<String> ISOLATED = java.util.Set.of("save_interval_and_busy_server");

	/** Test name -> test, in the order they run. */
	public static Map<String, Consumer<GameTestHelper>> all() {
		Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
		tests.put("web_server_started", LiveTests::webServerStarted);
		tests.put("craftable_items_from_recipes_and_trades", LiveTests::craftableItemsFromRecipesAndTrades);
		tests.put("join_and_leave", LiveTests::joinAndLeave);
		tests.put("stats_reach_the_cache", LiveTests::statsReachTheCache);
		tests.put("advancements_reach_the_cache", LiveTests::advancementsReachTheCache);
		tests.put("revoked_advancement_leaves_the_cache", LiveTests::revokedAdvancementLeavesTheCache);
		tests.put("save_interval_and_busy_server", LiveTests::saveIntervalAndBusyServer);
		return tests;
	}

	static void webServerStarted(GameTestHelper helper) {
		helper.assertTrue(StatsCache.get().isReady(), "the stats cache was not loaded on server start");
		CompletableFuture<HttpResponse<String>> health = get("/api/health");
		// Waited for in real time, not in ticks: the test server ticks as fast as it can, so its 200 ticks may be over
		// in a second, before a freshly started JVM has sent its first request. /api/health doesn't need the server
		// thread, so blocking it here can't keep the answer back.
		// The game tests run without downloading the game's files (downloadGameAssets: false): the texts are then the
		// server's own English, the same as the downloaded ones, in every language.
		CompletableFuture<HttpResponse<String>> english = get("/mc/lang/de.json");
		awaitAtMost(health, Duration.ofSeconds(20));
		awaitAtMost(english, Duration.ofSeconds(20));
		helper.succeedWhen(() -> {
			helper.assertTrue(health.isDone(), "waiting for /api/health");
			helper.assertFalse(health.isCompletedExceptionally(), "the mod's web server does not answer");
			helper.assertValueEqual(health.join().statusCode(), 200, "/api/health status");
			helper.assertValueEqual(english.join().statusCode(), 200, "/mc/lang/de.json status without the download");
			JsonObject texts = JsonParser.parseString(english.join().body()).getAsJsonObject();
			helper.assertValueEqual(texts.getAsJsonObject("block").get("minecraft:lapis_ore").getAsString(), "Lapis Lazuli Ore",
				"the game's own name of minecraft:lapis_ore");
			helper.assertValueEqual(texts.getAsJsonObject("advancement").get("advancements.story.root.title").getAsString(),
				"Minecraft", "the advancement texts");
		});
	}

	/**
	 * Statistics: "crafted" lists what this server's recipes and villager trades can give (crafting
	 * grid, furnaces, smithing table, stonecutter, cartography table, trades), "broken" only items with durability;
	 * any value is shown anyway.
	 */
	static void craftableItemsFromRecipesAndTrades(GameTestHelper helper) {
		java.util.List<String> crafted = de.nr137.statswebuiplus.stats.VanillaCatalog.keys("crafted");
		for (String id : java.util.List.of(
			"minecraft:oak_planks", "minecraft:crafting_table", // crafting grid
			"minecraft:iron_ingot", "minecraft:glass", // furnace
			"minecraft:netherite_sword", // smithing table
			"minecraft:stone_brick_stairs", // stonecutter
			"minecraft:written_book", "minecraft:firework_rocket", // special recipes
			"minecraft:filled_map", // cartography table
			"minecraft:enchanted_book", "minecraft:bell" // villager trades (librarian, armorer): no recipe gives them
		)) {
			helper.assertTrue(crafted.contains(id), "crafted lacks " + id);
		}
		for (String id : java.util.List.of("minecraft:bedrock", "minecraft:dragon_egg", "minecraft:elytra", "minecraft:totem_of_undying")) {
			helper.assertFalse(crafted.contains(id), "crafted lists " + id + ", which nothing can skill");
		}
		// The totals "blocks placed" and "food eaten" add up "used" over block items and food.
		helper.assertTrue(de.nr137.statswebuiplus.stats.VanillaCatalog.blockItems().containsAll(java.util.List.of("minecraft:stone", "minecraft:oak_planks", "minecraft:wheat_seeds")),
			"block items lack stone, planks or seeds");
		helper.assertFalse(de.nr137.statswebuiplus.stats.VanillaCatalog.blockItems().contains("minecraft:diamond"), "a diamond places no block");
		helper.assertTrue(de.nr137.statswebuiplus.stats.VanillaCatalog.foodItems().containsAll(java.util.List.of("minecraft:bread", "minecraft:golden_apple", "minecraft:cooked_beef")),
			"food lacks bread, golden apples or steak");
		helper.assertFalse(de.nr137.statswebuiplus.stats.VanillaCatalog.foodItems().contains("minecraft:stick"), "a stick is no food");
		// Broken: only items with durability.
		java.util.List<String> broken = de.nr137.statswebuiplus.stats.VanillaCatalog.keys("broken");
		for (String id : java.util.List.of("minecraft:diamond_pickaxe", "minecraft:elytra", "minecraft:shield", "minecraft:fishing_rod")) {
			helper.assertTrue(broken.contains(id), "broken lacks " + id);
		}
		for (String id : java.util.List.of("minecraft:stone", "minecraft:stick", "minecraft:diamond", "minecraft:bread")) {
			helper.assertFalse(broken.contains(id), "broken lists " + id + ", which has no durability");
		}
		helper.succeed();
	}

	/** A request to the mod's web server, on another thread: the API may need the server thread meanwhile. */
	private static CompletableFuture<HttpResponse<String>> get(String path) {
		int port = StatsHttpServer.getInstance().config().port();
		return HttpClient.newHttpClient().sendAsync(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30)).build(),
			HttpResponse.BodyHandlers.ofString());
	}

	static void joinAndLeave(GameTestHelper helper) {
		long before = System.currentTimeMillis();
		ServerPlayer player = joinPlayer(helper, "join_leave");
		UUID uuid = player.getUUID();
		helper.startSequence()
			.thenWaitUntil(() -> {
				SessionLog.Times times = SessionLog.get().times(uuid);
				helper.assertTrue(times.online(), "joined player is not online: " + times);
				helper.assertFalse(times.firstJoinEstimated(), "first join is an estimate: " + times);
				helper.assertTrue(times.firstJoin() >= before, "first join is before the join: " + times);
			})
			.thenExecute(() -> leave(player))
			.thenWaitUntil(() -> {
				SessionLog.Times times = SessionLog.get().times(uuid);
				helper.assertFalse(times.online(), "player who left is still online: " + times);
				helper.assertFalse(times.lastLogoutEstimated(), "last online is an estimate: " + times);
				helper.assertTrue(times.lastLogout() >= times.firstJoin(), "last online is before the join: " + times);
			})
			.thenSucceed();
	}

	static void statsReachTheCache(GameTestHelper helper) {
		ServerPlayer player = joinPlayer(helper, "stats");
		player.awardStat(Stats.CUSTOM.get(Stats.JUMP), 7);
		player.awardStat(Stats.BLOCK_MINED.get(Blocks.DIAMOND_ORE), 3);
		Refresh refresh = new Refresh(helper.getLevel().getServer());
		helper.startSequence()
			.thenExecute(refresh::start)
			.thenWaitUntil(() -> {
				PlayerRecord record = refresh.record(helper, player);
				helper.assertValueEqual(record.vanillaRaw("custom", "minecraft:jump"), 7L, "jumps in the cache");
				helper.assertValueEqual(record.vanillaRaw("mined", "minecraft:diamond_ore"), 3L, "diamond ore in the cache");
				helper.assertValueEqual(record.name(), player.getName().getString(), "name in the cache");
				helper.assertTrue(record.skills().getOrDefault("mining", 0.0) > 0, "mining counts the diamonds: " + record.skills());
			})
			// A second change after the first refresh is picked up by the next one.
			.thenExecute(() -> {
				player.awardStat(Stats.CUSTOM.get(Stats.JUMP), 5);
				refresh.start();
			})
			.thenWaitUntil(() -> helper.assertValueEqual(
				refresh.record(helper, player).vanillaRaw("custom", "minecraft:jump"), 12L, "jumps after more jumping"))
			.thenExecute(() -> leave(player))
			.thenSucceed();
	}

	static void advancementsReachTheCache(GameTestHelper helper) {
		ServerPlayer player = joinPlayer(helper, "advancements");
		AdvancementHolder root = advancement(helper, "story/root");
		player.getAdvancements().award(root, "crafting_table");
		Refresh refresh = new Refresh(helper.getLevel().getServer());
		helper.startSequence()
			.thenExecute(refresh::start)
			.thenWaitUntil(() -> {
				PlayerRecord record = refresh.record(helper, player);
				helper.assertTrue(record.hasAdvancement("minecraft:story/root"), "awarded advancement missing: " + record.advancements());
				helper.assertTrue(record.advancementTime("minecraft:story/root") > 0, "awarded advancement has no time");
			})
			.thenExecute(() -> leave(player))
			.thenSucceed();
	}

	static void revokedAdvancementLeavesTheCache(GameTestHelper helper) {
		ServerPlayer player = joinPlayer(helper, "revoke");
		AdvancementHolder root = advancement(helper, "story/root");
		player.getAdvancements().award(root, "crafting_table");
		Refresh refresh = new Refresh(helper.getLevel().getServer());
		helper.startSequence()
			.thenExecute(refresh::start)
			.thenWaitUntil(() -> helper.assertTrue(refresh.record(helper, player).hasAdvancement("minecraft:story/root"),
				"awarded advancement missing"))
			.thenExecute(() -> {
				player.getAdvancements().revoke(root, "crafting_table");
				refresh.start();
			})
			.thenWaitUntil(() -> helper.assertFalse(refresh.record(helper, player).hasAdvancement("minecraft:story/root"),
				"revoked advancement still in the cache"))
			.thenExecute(() -> leave(player))
			.thenSucceed();
	}

	/**
	 * Both need a changed config, which all tests share, so they run one after the other in one test.
	 * README: saveIntervalSeconds also saves online players' stats that often. And when the game can't hand
	 * over the stats within 5 seconds (a lag spike), the API answers 503 with Retry-After.
	 */
	static void saveIntervalAndBusyServer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Path config = WebConfig.path();
		String original = read(config);
		JsonObject changed = JsonParser.parseString(original).getAsJsonObject();
		changed.addProperty("saveIntervalSeconds", 1);
		changed.addProperty("refreshCooldownMs", 0);
		write(config, changed.toString());
		StatsHttpServer.getInstance().reloadConfig("game test");

		ServerPlayer player = joinPlayer(helper, "save_busy");
		player.awardStat(Stats.CUSTOM.get(Stats.JUMP), 3);
		Path statsFile = PlayerFiles.statsDirectory(server.getWorldPath(LevelResource.ROOT)).resolve(player.getUUID() + ".json");
		AtomicReference<CompletableFuture<HttpResponse<String>>> request = new AtomicReference<>();
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(Files.isRegularFile(statsFile), "online player's stats not saved yet: " + statsFile);
				JsonObject stats = JsonParser.parseString(read(statsFile)).getAsJsonObject().getAsJsonObject("stats");
				JsonObject custom = stats == null ? null : stats.getAsJsonObject("minecraft:custom");
				helper.assertTrue(custom != null && custom.has("minecraft:jump") && custom.get("minecraft:jump").getAsInt() == 3,
					"saved stats lack the jumps: " + stats);
			})
			.thenExecute(() -> {
				// An API request while the server thread is stuck, like in a lag spike: blocked until the API has answered
				// (it gives up after 5 s), at most 20 s on a slow machine.
				request.set(get("/api/players"));
				awaitAtMost(request.get(), Duration.ofSeconds(20));
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(request.get().isDone(), "waiting for the API's answer");
				HttpResponse<String> response = request.get().join();
				helper.assertValueEqual(response.statusCode(), 503, "status while the server thread is busy");
				helper.assertTrue(response.headers().firstValue("Retry-After").isPresent(), "503 without Retry-After");
			})
			.thenExecute(() -> {
				write(config, original);
				StatsHttpServer.getInstance().reloadConfig("game test finished");
				leave(player);
			})
			.thenSucceed();
	}

	/** Blocks the calling (server) thread until the request is done, at most for the given time. */
	private static void awaitAtMost(CompletableFuture<?> request, Duration limit) {
		long deadline = System.nanoTime() + limit.toNanos();
		while (!request.isDone() && System.nanoTime() < deadline) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(Path file, String text) {
		try {
			Files.writeString(file, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * A cache refresh like an API request's: on another thread, since it waits for the server thread, which keeps
	 * ticking the test meanwhile. A refresh that timed out (the server thread didn't hand over the stats within 5 s, as
	 * on a busy CI runner; the API then answers 503 and the browser asks again) is started again, a few times.
	 * Used from the server thread only.
	 */
	private static final class Refresh {
		private static final int ATTEMPTS = 3;
		private final MinecraftServer server;
		private CompletableFuture<StatsCache.Snapshot> running;
		private int attempts;

		Refresh(MinecraftServer server) {
			this.server = server;
		}

		void start() {
			attempts = 0;
			run();
		}

		private void run() {
			attempts++;
			running = CompletableFuture.supplyAsync(() -> {
				try {
					return StatsCache.get().refresh(server, 0);
				} catch (Exception e) {
					throw new CompletionException(e);
				}
			});
		}

		/** The player's record once the refresh is done; fails (so a thenWaitUntil waits) until then. */
		PlayerRecord record(GameTestHelper helper, ServerPlayer player) {
			helper.assertTrue(running != null && running.isDone(), "waiting for the refresh");
			if (running.isCompletedExceptionally()) {
				Throwable cause = running.exceptionNow();
				while (cause instanceof CompletionException && cause.getCause() != null) {
					cause = cause.getCause();
				}
				if (cause instanceof TimeoutException && attempts < ATTEMPTS) {
					run();
					helper.fail("the refresh timed out, trying again (attempt " + attempts + " of " + ATTEMPTS + ")");
				}
				helper.fail("the refresh failed (attempt " + attempts + " of " + ATTEMPTS + "): " + cause);
			}
			for (PlayerRecord record : running.join().players()) {
				if (record.uuid().equals(player.getUUID())) {
					return record;
				}
			}
			helper.fail("the online player " + player.getName().getString() + " is not in the cache");
			throw new IllegalStateException();
		}
	}

	private static AdvancementHolder advancement(GameTestHelper helper, String path) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Identifier.withDefaultNamespace(path));
		helper.assertTrue(holder != null, "no advancement minecraft:" + path);
		return holder;
	}

	/** A player who logs in like a real client, with an in-memory connection (see TestPlayers). */
	private static ServerPlayer joinPlayer(GameTestHelper helper, String name) {
		return TestPlayers.join(helper.getLevel().getServer(), helper.getLevel(), UUID.randomUUID(), "test_" + name).player();
	}

	/** Disconnects the player the way a kick or quit does, through the connection. */
	private static void leave(ServerPlayer player) {
		player.connection.disconnect(Component.literal("Game test finished"));
	}
}
