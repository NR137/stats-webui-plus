package webui.stats.mc.gametest;

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
import webui.stats.mc.WebConfig;
import webui.stats.mc.stats.PlayerRecord;
import webui.stats.mc.stats.SessionLog;
import webui.stats.mc.stats.StatsCache;
import webui.stats.mc.stats.StatsService;
import webui.stats.mc.web.StatsHttpServer;

/**
 * What only a running server with a player shows: joins and leaves through the loader's events, stats and
 * advancements through the mixins, and both reaching the stats cache the API serves (README, "Stats cache"). Each
 * loader registers these as game tests (fabric/src/gametest, neoforge/src/gametest); a mock player stands in for a
 * real one.
 */
public final class LiveTests {
	private LiveTests() {
	}

	/** Test name -> test, in the order they run. */
	public static Map<String, Consumer<GameTestHelper>> all() {
		Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
		tests.put("web_server_started", LiveTests::webServerStarted);
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
		helper.succeedWhen(() -> {
			helper.assertTrue(health.isDone(), "waiting for /api/health");
			helper.assertFalse(health.isCompletedExceptionally(), "the mod's web server does not answer");
			helper.assertValueEqual(health.join().statusCode(), 200, "/api/health status");
		});
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
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
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
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.awardStat(Stats.CUSTOM.get(Stats.JUMP), 7);
		player.awardStat(Stats.BLOCK_MINED.get(Blocks.DIAMOND_ORE), 3);
		AtomicReference<CompletableFuture<StatsCache.Snapshot>> refresh = new AtomicReference<>();
		helper.startSequence()
			.thenExecute(() -> refresh.set(refresh(helper.getLevel().getServer())))
			.thenWaitUntil(() -> {
				PlayerRecord record = record(helper, refresh.get(), player);
				helper.assertValueEqual(record.vanillaRaw("custom", "minecraft:jump"), 7L, "jumps in the cache");
				helper.assertValueEqual(record.vanillaRaw("mined", "minecraft:diamond_ore"), 3L, "diamond ore in the cache");
				helper.assertValueEqual(record.name, player.getName().getString(), "name in the cache");
				helper.assertTrue(record.categories.getOrDefault("mining", 0.0) > 0, "mining counts the diamonds: " + record.categories);
			})
			// A second change after the first refresh is picked up by the next one.
			.thenExecute(() -> {
				player.awardStat(Stats.CUSTOM.get(Stats.JUMP), 5);
				refresh.set(refresh(helper.getLevel().getServer()));
			})
			.thenWaitUntil(() -> helper.assertValueEqual(
				record(helper, refresh.get(), player).vanillaRaw("custom", "minecraft:jump"), 12L, "jumps after more jumping"))
			.thenExecute(() -> leave(player))
			.thenSucceed();
	}

	static void advancementsReachTheCache(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AdvancementHolder root = advancement(helper, "story/root");
		player.getAdvancements().award(root, "crafting_table");
		AtomicReference<CompletableFuture<StatsCache.Snapshot>> refresh = new AtomicReference<>();
		helper.startSequence()
			.thenExecute(() -> refresh.set(refresh(helper.getLevel().getServer())))
			.thenWaitUntil(() -> {
				PlayerRecord record = record(helper, refresh.get(), player);
				helper.assertTrue(record.hasAdvancement("minecraft:story/root"), "awarded advancement missing: " + record.advancements);
				helper.assertTrue(record.advancementTime("minecraft:story/root") > 0, "awarded advancement has no time");
			})
			.thenExecute(() -> leave(player))
			.thenSucceed();
	}

	static void revokedAdvancementLeavesTheCache(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AdvancementHolder root = advancement(helper, "story/root");
		player.getAdvancements().award(root, "crafting_table");
		AtomicReference<CompletableFuture<StatsCache.Snapshot>> refresh = new AtomicReference<>();
		helper.startSequence()
			.thenExecute(() -> refresh.set(refresh(helper.getLevel().getServer())))
			.thenWaitUntil(() -> helper.assertTrue(record(helper, refresh.get(), player).hasAdvancement("minecraft:story/root"),
				"awarded advancement missing"))
			.thenExecute(() -> {
				player.getAdvancements().revoke(root, "crafting_table");
				refresh.set(refresh(helper.getLevel().getServer()));
			})
			.thenWaitUntil(() -> helper.assertFalse(record(helper, refresh.get(), player).hasAdvancement("minecraft:story/root"),
				"revoked advancement still in the cache"))
			.thenExecute(() -> leave(player))
			.thenSucceed();
	}

	/**
	 * Both need a changed config, which all tests share, so they run one after the other in one test.
	 * README: saveIntervalSeconds also saves online players' stats that often. CHANGELOG: when the game can't hand
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

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.awardStat(Stats.CUSTOM.get(Stats.JUMP), 3);
		Path statsFile = StatsService.statsDirectory(server.getWorldPath(LevelResource.ROOT)).resolve(player.getUUID() + ".json");
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
				// An API request while the server thread is stuck, like in a lag spike.
				request.set(get("/api/players"));
				try {
					Thread.sleep(8_000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
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
	 * Refreshes the cache like an API request: on another thread, since the refresh waits for the server thread,
	 * which keeps ticking the test meanwhile.
	 */
	private static CompletableFuture<StatsCache.Snapshot> refresh(MinecraftServer server) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return StatsCache.get().refresh(server, 0);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static PlayerRecord record(GameTestHelper helper, CompletableFuture<StatsCache.Snapshot> refresh, ServerPlayer player) {
		helper.assertTrue(refresh != null && refresh.isDone(), "waiting for the refresh");
		if (refresh.isCompletedExceptionally()) {
			helper.fail("the refresh failed: " + refresh.exceptionNow());
		}
		for (PlayerRecord record : refresh.join().players()) {
			if (record.uuid.equals(player.getUUID())) {
				return record;
			}
		}
		helper.fail("the online player " + player.getName().getString() + " is not in the cache");
		throw new IllegalStateException();
	}

	private static AdvancementHolder advancement(GameTestHelper helper, String path) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Identifier.withDefaultNamespace(path));
		helper.assertTrue(holder != null, "no advancement minecraft:" + path);
		return holder;
	}

	/** Disconnects the player the way a kick or quit does, through the connection. */
	private static void leave(ServerPlayer player) {
		player.connection.disconnect(Component.literal("Game test finished"));
	}
}
