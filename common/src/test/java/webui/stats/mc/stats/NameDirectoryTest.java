package webui.stats.mc.stats;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import webui.stats.mc.TestPlatform;

/** Player names: known names, and the server's own usercache.json. No Mojang lookups here. */
class NameDirectoryTest {
	@TempDir
	Path server;

	@BeforeEach
	void setUp() {
		TestPlatform.install(server);
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
}
