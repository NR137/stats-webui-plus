package webui.stats.mc.web;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * README, Stats cache: skins are fetched from Mojang through /mojang/..., skin images are kept in memory and player
 * profiles for 5 minutes; players without a skin of their own get Minecraft's default skin. CHANGELOG: Mojang is
 * asked on threads of its own, so a page full of heads can't make anything else wait.
 */
class MojangProxyTest {
	private static final String SKIN_HASH = "abc123def456";
	private static final byte[] SKIN_PNG = skinPng();

	private static byte[] skinPng() {
		try {
			return TestServer.resource("/assets/stats_webui_plus/web/mc/item/diamond.png");
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private HttpServer mojang;
	private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
	private final Map<String, Integer> statusFor = new ConcurrentHashMap<>();
	private volatile CountDownLatch hold = new CountDownLatch(0);
	private TestServer proxy;

	@BeforeEach
	void start() throws IOException {
		mojang = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		mojang.setExecutor(Executors.newCachedThreadPool());
		mojang.createContext("/", this::answer);
		mojang.start();
		String base = "http://127.0.0.1:" + mojang.getAddress().getPort();
		proxy = new TestServer(new MojangProxy(base + "/profile/", base + "/texture/")::handle);
	}

	@AfterEach
	void stop() {
		hold.countDown();
		proxy.close();
		mojang.stop(0);
	}

	private void answer(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		hits.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
		try {
			hold.await(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		int status = statusFor.getOrDefault(path, 200);
		byte[] body;
		String type;
		if (status == 204) {
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
			return;
		} else if (status != 200) {
			body = "{\"error\":\"TooManyRequests\"}".getBytes(StandardCharsets.UTF_8);
			type = "application/json";
		} else if (path.startsWith("/texture/")) {
			body = SKIN_PNG;
			type = "image/png";
		} else {
			String uuid = path.substring("/profile/".length());
			body = profile(uuid, path.contains("0000") ? null : "http://textures.minecraft.net/texture/" + SKIN_HASH);
			type = "application/json";
		}
		exchange.getResponseHeaders().set("Content-Type", type);
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	/** A session server profile; without a skin URL the textures property has no SKIN. */
	private static byte[] profile(String uuid, String skinUrl) {
		JsonObject textures = new JsonObject();
		if (skinUrl != null) {
			JsonObject skin = new JsonObject();
			skin.addProperty("url", skinUrl);
			textures.add("SKIN", skin);
		}
		JsonObject payload = new JsonObject();
		payload.add("textures", textures);
		String value = Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
		return ("{\"id\":\"" + uuid.replace("-", "") + "\",\"name\":\"Steve\",\"properties\":[{\"name\":\"textures\",\"value\":\""
			+ value + "\"}]}").getBytes(StandardCharsets.UTF_8);
	}

	private int hitsOn(String prefix) {
		return hits.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix)).mapToInt(entry -> entry.getValue().get()).sum();
	}

	private static JsonObject skinOf(TestServer.Response response) {
		JsonObject profile = JsonParser.parseString(response.text()).getAsJsonObject();
		String value = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
		JsonObject payload = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
		return payload.getAsJsonObject("textures").getAsJsonObject("SKIN");
	}

	private static final String PLAYER = "11111111-2222-3333-4444-555555555555";

	@Test
	void profileIsPassedOnAndKeptFiveMinutes() throws IOException {
		TestServer.Response first = proxy.get("/mojang/session/minecraft/profile/" + PLAYER);
		assertEquals(200, first.status());
		assertTrue(first.header("Content-Type").contains("json"));
		assertEquals("http://textures.minecraft.net/texture/" + SKIN_HASH, skinOf(first).get("url").getAsString());
		String cache = first.header("Cache-Control");
		int maxAge = Integer.parseInt(cache.replaceAll(".*max-age=(\\d+).*", "$1"));
		assertTrue(maxAge > 0 && maxAge <= 300, cache);

		assertEquals(200, proxy.get("/mojang/session/minecraft/profile/" + PLAYER).status());
		assertEquals(1, hitsOn("/profile/"), "Mojang asked again within 5 minutes");
	}

	@Test
	void oneEntryPerPlayerHoweverTheUuidIsWritten() throws IOException {
		proxy.get("/mojang/session/minecraft/profile/" + PLAYER);
		proxy.get("/mojang/session/minecraft/profile/" + PLAYER.replace("-", ""));
		proxy.get("/mojang/session/minecraft/profile/" + PLAYER.toUpperCase());
		assertEquals(1, hitsOn("/profile/"));
	}

	@Test
	void skinsAreKeptAndNeverChange() throws IOException {
		TestServer.Response first = proxy.get("/mojang/textures/texture/" + SKIN_HASH);
		assertEquals(200, first.status());
		assertEquals("image/png", first.header("Content-Type"));
		assertArrayEquals(SKIN_PNG, first.body());
		assertTrue(first.header("Cache-Control").contains("immutable"), first.header("Cache-Control"));

		assertArrayEquals(SKIN_PNG, proxy.get("/mojang/textures/texture/" + SKIN_HASH).body());
		assertEquals(1, hitsOn("/texture/"));
	}

	@Test
	void offlinePlayerGetsTheDefaultSkin() throws IOException {
		String offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes()).toString();
		statusFor.put("/profile/" + offline, 204);

		TestServer.Response response = proxy.get("/mojang/session/minecraft/profile/" + offline);
		assertEquals(200, response.status());
		assertTrue(skinOf(response).get("url").getAsString().matches("/mc/skins/(slim|wide)/[a-z]+\\.png"), response.text());
	}

	@Test
	void sameDefaultSkinAsTheGameEveryTime() throws IOException {
		String offline = UUID.nameUUIDFromBytes("OfflinePlayer:Alex".getBytes()).toString();
		statusFor.put("/profile/" + offline, 204);
		String first = skinOf(proxy.get("/mojang/session/minecraft/profile/" + offline)).get("url").getAsString();
		statusFor.put("/profile/" + offline, 204);
		MojangProxy other = new MojangProxy("http://127.0.0.1:" + mojang.getAddress().getPort() + "/profile/", "http://127.0.0.1:1/texture/");
		try (TestServer second = new TestServer(other::handle)) {
			assertEquals(first, skinOf(second.get("/mojang/session/minecraft/profile/" + offline)).get("url").getAsString());
		}
	}

	@Test
	void profileWithoutSkinGetsTheDefaultSkin() throws IOException {
		String noSkin = "00000000-0000-4000-8000-000000000001";
		TestServer.Response response = proxy.get("/mojang/session/minecraft/profile/" + noSkin);
		assertEquals(200, response.status());
		assertTrue(skinOf(response).get("url").getAsString().startsWith("/mc/skins/"), response.text());
	}

	@Test
	void rateLimitIsPassedOnAndNotKept() throws IOException {
		statusFor.put("/profile/" + PLAYER, 429);
		TestServer.Response limited = proxy.get("/mojang/session/minecraft/profile/" + PLAYER);
		assertEquals(429, limited.status());
		assertEquals("no-store", limited.header("Cache-Control"));

		statusFor.remove("/profile/" + PLAYER);
		assertEquals(200, proxy.get("/mojang/session/minecraft/profile/" + PLAYER).status());
		assertEquals(2, hitsOn("/profile/"));
	}

	@Test
	void mojangDownIsABadGateway() throws IOException {
		MojangProxy unreachable = new MojangProxy("http://127.0.0.1:1/profile/", "http://127.0.0.1:1/texture/");
		try (TestServer server = new TestServer(unreachable::handle)) {
			TestServer.Response response = server.get("/mojang/session/minecraft/profile/" + PLAYER);
			assertEquals(502, response.status());
			assertTrue(response.text().contains("bad_gateway"));
		}
	}

	/** Only player profiles and skin images can be fetched; nothing else is ever sent on to another server. */
	@ParameterizedTest
	@ValueSource(strings = {
		"/mojang/session/minecraft/profile/not-a-uuid",
		"/mojang/session/minecraft/profile/" + PLAYER + "/../../admin",
		"/mojang/textures/texture/../../profile/x",
		"/mojang/textures/texture/abc.png",
		"/mojang/textures/texture/",
		"/mojang/other/thing",
		"/mojang/",
	})
	void otherPathsAreRefusedWithoutAskingMojang(String path) throws IOException {
		assertEquals(404, proxy.get(path).status(), path);
		assertEquals(0, hitsOn("/"), path);
	}

	@Test
	void onlyGetAndHead() throws IOException {
		assertEquals(405, proxy.request("POST", "/mojang/session/minecraft/profile/" + PLAYER).status());
		assertEquals(200, proxy.request("HEAD", "/mojang/textures/texture/" + SKIN_HASH).status());
	}

	@Test
	void slowMojangDoesNotHoldUpOtherRequests() throws Exception {
		proxy.get("/mojang/textures/texture/" + SKIN_HASH);
		hold = new CountDownLatch(1);
		CompletableFuture<TestServer.Response> slow = CompletableFuture.supplyAsync(() -> {
			try {
				return proxy.get("/mojang/session/minecraft/profile/" + PLAYER);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		Thread.sleep(200);

		long started = System.nanoTime();
		TestServer.Response cached = proxy.get("/mojang/textures/texture/" + SKIN_HASH);
		long ms = (System.nanoTime() - started) / 1_000_000;
		assertEquals(200, cached.status());
		assertTrue(ms < 1000, "a kept skin waited " + ms + " ms for Mojang");
		assertFalse(slow.isDone());

		hold.countDown();
		assertEquals(200, slow.get(10, TimeUnit.SECONDS).status());
	}

	@Test
	void requestsForTheSameThingShareOneMojangRequest() throws Exception {
		hold = new CountDownLatch(1);
		List<CompletableFuture<TestServer.Response>> requests = java.util.stream.IntStream.range(0, 4)
			.mapToObj(i -> CompletableFuture.supplyAsync(() -> {
				try {
					return proxy.get("/mojang/textures/texture/" + SKIN_HASH);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}))
			.toList();
		Thread.sleep(300);
		hold.countDown();
		for (CompletableFuture<TestServer.Response> request : requests) {
			assertEquals(200, request.get(10, TimeUnit.SECONDS).status());
		}
		assertEquals(1, hitsOn("/texture/"));
	}

	@Test
	void prewarmFetchesProfilesAndSkinsAhead() throws Exception {
		MojangProxy warm = new MojangProxy(
			"http://127.0.0.1:" + mojang.getAddress().getPort() + "/profile/",
			"http://127.0.0.1:" + mojang.getAddress().getPort() + "/texture/"
		);
		warm.prewarm(List.of(UUID.fromString(PLAYER)));
		long deadline = System.currentTimeMillis() + 5000;
		while (hitsOn("/texture/") == 0 && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		assertEquals(1, hitsOn("/profile/"));
		assertEquals(1, hitsOn("/texture/"));

		try (TestServer server = new TestServer(warm::handle)) {
			assertEquals(200, server.get("/mojang/session/minecraft/profile/" + PLAYER).status());
			assertEquals(200, server.get("/mojang/textures/texture/" + SKIN_HASH).status());
		}
		assertEquals(1, hitsOn("/profile/"), "the page asked Mojang although prewarm had fetched it");
		assertEquals(1, hitsOn("/texture/"));
	}
}
