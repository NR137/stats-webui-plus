package webui.stats.mc;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Calls back when a file's content changes. Editors may save in several steps (truncate, write, rename), so it waits
 * until the file has been quiet for a moment and then compares the content with what it saw last; saving without a
 * change, or the mod writing the same content, calls nothing.
 */
public final class ConfigWatcher {
	private static final long QUIET_MS = 500;

	private final Path file;
	private final Runnable onChange;
	private WatchService service;
	private byte[] seen;

	public ConfigWatcher(Path file, Runnable onChange) {
		this.file = file;
		this.onChange = onChange;
	}

	public synchronized void start() {
		try {
			service = file.getFileSystem().newWatchService();
			file.getParent().register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
		} catch (IOException | UnsupportedOperationException e) {
			McStatsWebui.LOGGER.warn("Can't watch {} for changes ({}); run /reload after editing it", file, e.toString());
			stop();
			return;
		}
		seen = read();
		Thread thread = new Thread(() -> run(service), McStatsWebui.MOD_ID + "-config-watcher");
		thread.setDaemon(true);
		thread.start();
	}

	/** Closing the watch service also ends the thread waiting on it. */
	public synchronized void stop() {
		if (service != null) {
			try {
				service.close();
			} catch (IOException ignored) {
			}
			service = null;
		}
	}

	private void run(WatchService watch) {
		try {
			while (true) {
				if (!concernsFile(watch.take())) {
					continue;
				}
				// Wait until nothing has happened in the folder for QUIET_MS.
				WatchKey next;
				while ((next = watch.poll(QUIET_MS, TimeUnit.MILLISECONDS)) != null) {
					concernsFile(next);
				}
				byte[] content = read();
				if (content != null && !Arrays.equals(content, seen)) {
					seen = content;
					onChange.run();
				}
			}
		} catch (InterruptedException | ClosedWatchServiceException e) {
			// Stopped.
		} catch (RuntimeException e) {
			McStatsWebui.LOGGER.error("Config watcher stopped; run /reload after editing {}", file, e);
		}
	}

	private boolean concernsFile(WatchKey key) {
		boolean concerns = false;
		for (WatchEvent<?> event : key.pollEvents()) {
			if (event.kind() == StandardWatchEventKinds.OVERFLOW || file.getFileName().equals(event.context())) {
				concerns = true;
			}
		}
		key.reset();
		return concerns;
	}

	private byte[] read() {
		try {
			return Files.readAllBytes(file);
		} catch (IOException e) {
			return null;
		}
	}
}
