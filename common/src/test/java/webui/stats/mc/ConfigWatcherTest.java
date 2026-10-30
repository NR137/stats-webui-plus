package webui.stats.mc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** "Changes apply as soon as the file is saved, no restart needed" (README, Configuration). */
class ConfigWatcherTest {
	@TempDir
	Path dir;

	@Test
	void savingTheFileIsNoticed() throws Exception {
		Path file = dir.resolve("stats_webui_plus.json");
		Files.writeString(file, "{}");
		CountDownLatch changed = new CountDownLatch(1);
		ConfigWatcher watcher = new ConfigWatcher(file, changed::countDown);
		watcher.start();
		try {
			Thread.sleep(200);
			Files.writeString(file, "{\"port\": 25600}");
			assertTrue(changed.await(15, TimeUnit.SECONDS), "no change reported");
		} finally {
			watcher.stop();
		}
	}

	@Test
	void otherFilesInTheFolderAreIgnored() throws Exception {
		Path file = dir.resolve("stats_webui_plus.json");
		Files.writeString(file, "{}");
		CountDownLatch changed = new CountDownLatch(1);
		ConfigWatcher watcher = new ConfigWatcher(file, changed::countDown);
		watcher.start();
		try {
			Thread.sleep(200);
			Files.writeString(dir.resolve("other-mod.json"), "{}");
			assertFalse(changed.await(3, TimeUnit.SECONDS), "change reported for another file");
		} finally {
			watcher.stop();
		}
	}

	@Test
	void stopIsSafeTwice() {
		ConfigWatcher watcher = new ConfigWatcher(dir.resolve("x.json"), () -> {});
		watcher.start();
		watcher.stop();
		assertDoesNotThrow(watcher::stop);
	}
}
