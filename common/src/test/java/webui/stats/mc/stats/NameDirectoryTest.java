package webui.stats.mc.stats;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import webui.stats.mc.TestPlatform;

/**
 * Player names: known names, the server's own files, and Mojang lookups against a fake Mojang. CHANGELOG: lookups
 * run in the background and the short UUID is shown until the name arrives; offline-mode UUIDs are never sent to
 * Mojang; failed lookups are retried after an hour; the name cache file is written atomically.
 */
class NameDirectoryTest {
	@TempDir
	Path server;

	private HttpServer mojang;
	private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
	private final Map<String, Integer> statusFor = new ConcurrentHashMap<>();
	private volatile CountDownLatch hold = new CountDownLatch(0);
	private final AtomicLong now = new AtomicLong(System.currentTimeMillis());

	@BeforeEach
	void setUp() throws Exception {
		TestPlatform.install(server);
		mojang = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		mojang.setExecutor(Executors.newCachedThreadPool());
		mojang.createContext("/", exchange -> {
			String compact = exchange.getRequestURI().getPath().substring("/profile/".length());
			hits.computeIfAbsent(compact, key -> new AtomicInteger()).incrementAndGet();
			try {
				hold.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			int status = statusFor.getOrDefault(compact, 200);
			byte[] body = status == 200
				? ("{\"id\":\"" + compact + "\",\"name\":\"Name_" + compact.substring(0, 6) + "\"}").getBytes(StandardCharsets.UTF_8)
				: new byte[0];
			exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
			if (body.length > 0) {
				exchange.getResponseBody().write(body);
			}
			exchange.close();
		});
		mojang.start();
		NameDirectory.profileBase = "http://127.0.0.1:" + mojang.getAddress().getPort() + "/profile/";
		NameDirectory.clock = now::get;
	}

	@AfterEach
	void tearDown() {
		hold.countDown();
		mojang.stop(0);
		NameDirectory.profileBase = "https://sessionserver.mojang.com/session/minecraft/profile/";
		NameDirectory.clock = System::currentTimeMillis;
	}

	private int hitsFor(UUID uuid) {
		AtomicInteger count = hits.get(uuid.toString().replace("-", ""));
		return count == null ? 0 : count.get();
	}

	private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (!condition.getAsBoolean()) {
			assertTrue(System.currentTimeMillis() < deadline, "timed out waiting for " + what);
			Thread.sleep(20);
		}
	}

	/** What a later start of the server knows from the name cache file. */
	private NameDirectory afterRestart() {
		NameDirectory names = new NameDirectory();
		names.loadLocal(server);
		return names;
	}

	private static String expectedName(UUID uuid) {
		return "Name_" + uuid.toString().replace("-", "").substring(0, 6);
	}

	@Test
	void putAndGet() {
		NameDirectory names = new NameDirectory();
		UUID steve = UUID.randomUUID();
		assertFalse(names.knows(steve));
		names.put(steve, "Steve");
		assertTrue(names.knows(steve));
		assertEquals("Steve", names.get(steve));
	}

	@Test
	void unknownPlayerStillHasSomethingToShow() {
		String shown = new NameDirectory().get(UUID.randomUUID());
		assertNotNull(shown);
		assertFalse(shown.isBlank());
	}

	@Test
	void readsUsercache() throws Exception {
		UUID alex = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
		Files.writeString(server.resolve("usercache.json"), """
			[{"name": "Alex", "uuid": "%s", "expiresOn": "2099-01-01 00:00:00 +0000"}]
			""".formatted(alex));
		NameDirectory names = new NameDirectory();
		names.loadLocal(server);
		assertEquals("Alex", names.get(alex));
	}

	@Test
	void brokenUsercacheIsIgnored() throws Exception {
		Files.writeString(server.resolve("usercache.json"), "[{ broken");
		assertDoesNotThrow(() -> new NameDirectory().loadLocal(server));
	}

	// --- Mojang lookups ---

	@Test
	void unknownNameIsLookedUpInTheBackgroundAndKept() throws Exception {
		UUID uuid = UUID.randomUUID();
		hold = new CountDownLatch(1);
		NameDirectory names = new NameDirectory();

		long started = System.nanoTime();
		names.resolveMissingAsync(Set.of(uuid));
		long ms = (System.nanoTime() - started) / 1_000_000;
		assertTrue(ms < 500, "resolveMissingAsync waited " + ms + " ms for Mojang");
		assertEquals(uuid.toString().substring(0, 8), names.get(uuid), "the short UUID until the name arrives");

		hold.countDown();
		waitFor(() -> afterRestart().knows(uuid), "the name in the cache file");
		assertEquals(expectedName(uuid), afterRestart().get(uuid));
		assertEquals(1, hitsFor(uuid));
	}

	@Test
	void offlineModeUuidsAreNeverSentToMojang() throws Exception {
		UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
		assertEquals(3, offline.version());
		UUID real = UUID.randomUUID();

		new NameDirectory().resolveMissingAsync(Set.of(offline, real));
		waitFor(() -> hitsFor(real) == 1, "the real account's lookup");
		Thread.sleep(200);
		assertEquals(0, hitsFor(offline));
	}

	@Test
	void knownNamesAreNotLookedUp() throws Exception {
		UUID known = UUID.randomUUID();
		UUID unknown = UUID.randomUUID();
		NameDirectory names = new NameDirectory();
		names.put(known, "Alex");

		names.resolveMissingAsync(Set.of(known, unknown));
		waitFor(() -> hitsFor(unknown) == 1, "the unknown name's lookup");
		Thread.sleep(200);
		assertEquals(0, hitsFor(known));
	}

	@Test
	void failedLookupIsRetriedOnlyAfterAnHour() throws Exception {
		UUID uuid = UUID.randomUUID();
		statusFor.put(uuid.toString().replace("-", ""), 500);
		NameDirectory names = new NameDirectory();

		names.resolveMissingAsync(Set.of(uuid));
		waitFor(() -> hitsFor(uuid) == 1, "the first lookup");
		Thread.sleep(200);

		// Reloads within the hour don't ask again.
		now.addAndGet(Duration.ofMinutes(59).toMillis());
		names.resolveMissingAsync(Set.of(uuid));
		Thread.sleep(300);
		assertEquals(1, hitsFor(uuid));

		statusFor.remove(uuid.toString().replace("-", ""));
		now.addAndGet(Duration.ofMinutes(2).toMillis());
		names.resolveMissingAsync(Set.of(uuid));
		waitFor(() -> afterRestart().knows(uuid), "the name after the retry");
		assertEquals(2, hitsFor(uuid));
	}

	@Test
	void unknownAccountCountsAsFailed() throws Exception {
		UUID uuid = UUID.randomUUID();
		statusFor.put(uuid.toString().replace("-", ""), 204);
		NameDirectory names = new NameDirectory();
		names.resolveMissingAsync(Set.of(uuid));
		waitFor(() -> hitsFor(uuid) == 1, "the lookup");
		Thread.sleep(200);
		names.resolveMissingAsync(Set.of(uuid));
		Thread.sleep(300);
		assertEquals(1, hitsFor(uuid));
		assertFalse(afterRestart().knows(uuid));
	}

	@Test
	void sameNameIsLookedUpOnceAtATime() throws Exception {
		UUID uuid = UUID.randomUUID();
		hold = new CountDownLatch(1);
		NameDirectory names = new NameDirectory();
		names.resolveMissingAsync(Set.of(uuid));
		names.resolveMissingAsync(Set.of(uuid));
		new NameDirectory().resolveMissingAsync(Set.of(uuid));
		hold.countDown();
		waitFor(() -> afterRestart().knows(uuid), "the name");
		assertEquals(1, hitsFor(uuid));
	}

	@Test
	void mojangDownBreaksNothing() throws Exception {
		NameDirectory.profileBase = "http://127.0.0.1:1/profile/";
		UUID uuid = UUID.randomUUID();
		NameDirectory names = new NameDirectory();
		assertDoesNotThrow(() -> names.resolveMissingAsync(Set.of(uuid)));
		Thread.sleep(500);
		assertEquals(uuid.toString().substring(0, 8), names.get(uuid));
	}

	@Test
	void cacheFileHoldsEveryResolvedName() throws Exception {
		Set<UUID> uuids = new java.util.HashSet<>();
		for (int i = 0; i < 25; i++) {
			uuids.add(UUID.randomUUID());
		}
		new NameDirectory().resolveMissingAsync(uuids);
		waitFor(() -> uuids.stream().allMatch(afterRestart()::knows), "all 25 names");

		Path file = server.resolve("config").resolve("stats_webui_plus-names.json");
		assertTrue(Files.isRegularFile(file));
		assertEquals(25, JsonParser.parseString(Files.readString(file)).getAsJsonObject().size());
		try (var listing = Files.list(file.getParent())) {
			assertTrue(listing.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")), "a temp file was left");
		}
	}
}
