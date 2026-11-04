package webui.stats.mc.stats;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import webui.stats.mc.TestPlatform;

/** CHANGELOG 1.3.0: first joined and last online, kept in <world>/stats_webui_plus/sessions.json. */
class SessionLogTest {
	@TempDir
	Path server;

	private Path world;
	private final UUID player = UUID.randomUUID();

	@BeforeEach
	void setUp() throws Exception {
		TestPlatform.install(server);
		world = server.resolve("world");
		Files.createDirectories(world);
		SessionLog.get().boot(world);
	}

	@Test
	void joinMeansOnlineWithRealFirstJoin() {
		long before = System.currentTimeMillis();
		SessionLog.get().onJoin(player);
		SessionLog.Times times = SessionLog.get().times(player);

		assertTrue(times.online());
		assertFalse(times.firstJoinEstimated());
		assertTrue(times.firstJoin() >= before && times.firstJoin() <= System.currentTimeMillis(), "" + times);
	}

	@Test
	void leaveRecordsLastOnline() throws Exception {
		SessionLog.get().onJoin(player);
		Thread.sleep(5);
		long beforeLeave = System.currentTimeMillis();
		SessionLog.get().onLeave(player);
		SessionLog.Times times = SessionLog.get().times(player);

		assertFalse(times.online());
		assertFalse(times.lastLogoutEstimated());
		assertTrue(times.lastLogout() >= beforeLeave, "" + times);
		assertTrue(times.lastLogout() >= times.firstJoin(), "" + times);
	}

	@Test
	void rejoiningKeepsFirstJoin() throws Exception {
		SessionLog.get().onJoin(player);
		long firstJoin = SessionLog.get().times(player).firstJoin();
		SessionLog.get().onLeave(player);
		Thread.sleep(5);
		SessionLog.get().onJoin(player);

		assertEquals(firstJoin, SessionLog.get().times(player).firstJoin());
		assertTrue(SessionLog.get().times(player).online());
	}

	@Test
	void survivesRestart() throws Exception {
		SessionLog.get().onJoin(player);
		SessionLog.get().onLeave(player);
		SessionLog.Times before = SessionLog.get().times(player);

		assertTrue(Files.isRegularFile(world.resolve("stats_webui_plus/sessions.json")));
		SessionLog.get().boot(world);
		SessionLog.Times after = SessionLog.get().times(player);

		assertEquals(before.firstJoin(), after.firstJoin());
		assertEquals(before.lastLogout(), after.lastLogout());
		assertFalse(after.online());
	}

	@Test
	void anotherWorldHasItsOwnLog() throws Exception {
		SessionLog.get().onJoin(player);
		SessionLog.get().onLeave(player);

		Path other = server.resolve("other");
		Files.createDirectories(other);
		SessionLog.get().boot(other);
		assertFalse(SessionLog.get().times(player).online());
		assertTrue(SessionLog.get().times(player).firstJoin() == 0 || SessionLog.get().times(player).firstJoinEstimated());
	}

	// --- Players from before the mod: "estimated (oldest advancement or recipe, last save of the player's files)" ---

	private static final Instant OLDEST = Instant.parse("2025-03-01T10:00:00Z");
	private static final Instant LAST_SAVE = Instant.parse("2026-02-01T18:30:00Z");

	/** Files as Minecraft leaves them for a player who played before the mod was installed. */
	private void oldPlayerFiles(UUID uuid) throws Exception {
		Path advancements = StatsService.advancementsDirectory(world).resolve(uuid + ".json");
		Files.createDirectories(advancements.getParent());
		Files.writeString(advancements, """
			{
			  "minecraft:story/mine_stone": {"criteria": {"get_stone": "2025-05-10 12:00:00 +0000"}, "done": true},
			  "minecraft:recipes/misc/crafting_table": {"criteria": {"has_planks": "2025-03-01 10:00:00 +0000"}, "done": true},
			  "minecraft:story/smelt_iron": {"criteria": {"iron": "2025-06-01 08:00:00 +0000"}, "done": true},
			  "DataVersion": 4000
			}
			""");
		Path stats = StatsService.statsDirectory(world).resolve(uuid + ".json");
		Files.createDirectories(stats.getParent());
		Files.writeString(stats, Stats.player().hours(5).json().toString());
		Files.setLastModifiedTime(advancements, FileTime.from(LAST_SAVE.minusSeconds(3600)));
		Files.setLastModifiedTime(stats, FileTime.from(LAST_SAVE));
	}

	@Test
	void oldPlayerFirstJoinIsTheOldestAdvancementOrRecipe() throws Exception {
		oldPlayerFiles(player);
		SessionLog.Times times = SessionLog.get().times(player);

		assertTrue(times.firstJoinEstimated(), "" + times);
		assertEquals(OLDEST.toEpochMilli(), times.firstJoin(), "" + times);
	}

	@Test
	void oldPlayerLastOnlineIsTheLastSave() throws Exception {
		oldPlayerFiles(player);
		SessionLog.Times times = SessionLog.get().times(player);

		assertTrue(times.lastLogoutEstimated(), "" + times);
		assertFalse(times.online());
		assertEquals(LAST_SAVE.toEpochMilli(), times.lastLogout(), 1000, "" + times);
	}

	@Test
	void oldPlayerWhoJoinsAgainKeepsTheEarlierFirstJoin() throws Exception {
		oldPlayerFiles(player);
		SessionLog.get().onJoin(player);
		SessionLog.get().onLeave(player);
		SessionLog.Times times = SessionLog.get().times(player);

		// The real first join was long before the mod saw them.
		assertEquals(OLDEST.toEpochMilli(), times.firstJoin(), "" + times);
		assertFalse(times.lastLogoutEstimated(), "" + times);
		assertTrue(times.lastLogout() > LAST_SAVE.toEpochMilli(), "" + times);
	}

	@Test
	void newerAdvancementsFileUpdatesTheEstimate() throws Exception {
		oldPlayerFiles(player);
		assertEquals(OLDEST.toEpochMilli(), SessionLog.get().times(player).firstJoin());

		// CHANGELOG: estimates are kept "until their advancements file changes".
		Path advancements = StatsService.advancementsDirectory(world).resolve(player + ".json");
		Files.writeString(advancements, """
			{"minecraft:story/root": {"criteria": {"crafting_table": "2024-12-24 18:00:00 +0000"}, "done": true}, "DataVersion": 4000}
			""");
		Files.setLastModifiedTime(advancements, FileTime.from(LAST_SAVE.plusSeconds(60)));

		assertEquals(Instant.parse("2024-12-24T18:00:00Z").toEpochMilli(), SessionLog.get().times(player).firstJoin());
	}

	@Test
	void unknownPlayerIsNotOnline() {
		assertFalse(SessionLog.get().times(UUID.randomUUID()).online());
	}

	@Test
	void brokenFileDoesNotStopTheServer() throws Exception {
		Path file = world.resolve("stats_webui_plus/sessions.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{ not json");
		assertDoesNotThrow(() -> SessionLog.get().boot(world));
		assertDoesNotThrow(() -> SessionLog.get().onJoin(player));
		assertTrue(SessionLog.get().times(player).online());
	}
}
