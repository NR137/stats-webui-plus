package de.nr137.statswebuiplus.assets;

import de.nr137.statswebuiplus.StatsWebuiPlus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The game's textures and texts for the web UI (/mc/...), fetched from Mojang by each server instead of shipped in the
 * mod: at server start, on a thread of its own, the client JAR of the running version and its language files are
 * downloaded and what the UI uses is extracted into config/stats_webui_plus/cache/&lt;version&gt;/mc. Once complete they stay there; a new
 * Minecraft version (or a mod update that extracts more, see FORMAT) does it again and removes the old folder.
 */
public final class GameAssets {
	public enum State {
		/** Turned off in the config (downloadGameAssets): the UI shows names and fallbacks instead of game images. */
		DISABLED,
		/** Downloading or drawing; the web UI opens once it's done (see whenSettled). */
		PREPARING,
		READY,
		/** Couldn't download or extract (offline, Mojang down, disk full); tried again at the next start. */
		FAILED
	}

	/** What the extraction produces; raise it when it changes, so existing caches are made again. */
	static final int FORMAT = 19;
	private static final String COMPLETE = "complete";
	private static final GameAssets INSTANCE = new GameAssets();

	private volatile State state = State.DISABLED;
	private volatile Path files;
	private volatile String version = "";
	private Thread worker;
	/** What waits for the download and drawing to end (the web server, which opens only then). */
	private final List<Runnable> waiting = new ArrayList<>();

	private GameAssets() {
	}

	public static GameAssets get() {
		return INSTANCE;
	}

	public State state() {
		return state;
	}

	/**
	 * What the files under /mc/ are made from (the Minecraft version and FORMAT, "26.3-18"), or null while they
	 * aren't ready. Pages put it in the files' addresses, so browsers may keep each file for good: a new version or
	 * format changes every address.
	 */
	public String stamp() {
		return state == State.READY ? version + "-" + FORMAT : null;
	}

	/**
	 * Runs action once the game's assets are ready or couldn't be made (at once if nothing is being made): on the
	 * worker's thread, or on this one.
	 */
	public void whenSettled(Runnable action) {
		synchronized (this) {
			if (state == State.PREPARING) {
				waiting.add(action);
				return;
			}
		}
		action.run();
	}

	private void settle() {
		List<Runnable> actions;
		synchronized (this) {
			actions = new ArrayList<>(waiting);
			waiting.clear();
		}
		for (Runnable action : actions) {
			try {
				action.run();
			} catch (RuntimeException e) {
				StatsWebuiPlus.LOGGER.error("Could not finish starting after the web UI's textures", e);
			}
		}
	}

	/** config/stats_webui_plus/cache: one folder per Minecraft version. */
	public static Path cacheRoot() {
		return StatsWebuiPlus.platform().configDir().resolve(StatsWebuiPlus.MOD_ID).resolve("cache");
	}

	/** Uses the finished cache of this version, or starts making it in the background. */
	public synchronized void start(Path root, String gameVersion, boolean enabled, MojangDownloads mojang) {
		if (!enabled) {
			state = State.DISABLED;
			files = null;
			StatsWebuiPlus.LOGGER.info("Game textures are off (downloadGameAssets: false); the web UI shows fallbacks");
			return;
		}
		if (worker != null && worker.isAlive() && gameVersion.equals(version)) {
			return;
		}
		version = gameVersion;
		Path dir = root.resolve(gameVersion);
		if (isComplete(dir)) {
			files = dir.resolve("mc");
			state = State.READY;
			removeOtherVersions(root, gameVersion);
			return;
		}
		state = State.PREPARING;
		files = null;
		worker = new Thread(() -> prepare(root, dir, gameVersion, mojang), StatsWebuiPlus.MOD_ID + "-game-assets");
		worker.setDaemon(true);
		worker.start();
	}

	/** Waits until the background work is done (tests and the dev tool). */
	public void await() throws InterruptedException {
		Thread running;
		synchronized (this) {
			running = worker;
		}
		if (running != null) {
			running.join();
		}
	}

	private void prepare(Path root, Path dir, String gameVersion, MojangDownloads mojang) {
		long started = System.nanoTime();
		try {
			StatsWebuiPlus.LOGGER.info("Downloading the Minecraft {} client from Mojang for the web UI's textures (once)",
				gameVersion);
			MojangDownloads.Version meta = mojang.version(gameVersion);
			Path clientJar = dir.resolve("client.jar");
			mojang.fetch(meta.client(), clientJar);
			Path temporary = dir.resolve("mc.tmp");
			deleteTree(temporary);
			int copied = GameTextures.copy(clientJar, temporary);
			GameLanguages.write(clientJar, mojang, meta, temporary);
			GameIcons.Result icons = GameIcons.render(clientJar, temporary);
			java.util.Map<String, String> faces = java.util.Map.of();
			try {
				List<String> failed = new ArrayList<>();
				faces = MobFaces.render(clientJar, temporary, failed);
				if (!failed.isEmpty()) {
					StatsWebuiPlus.LOGGER.warn("No face for these mobs in the web UI: {}", failed);
				}
			} catch (IOException | RuntimeException e) {
				// The rest is usable without them: mobs show their names' first letter until the next version.
				Throwable cause = e;
				while (cause.getCause() != null) {
					cause = cause.getCause();
				}
				StatsWebuiPlus.LOGGER.warn("Could not draw the mob faces for the web UI ({}; cause: {})", e.getMessage(), cause.toString());
			}
			java.util.Map<String, String> scenes = java.util.Map.of();
			try {
				scenes = Scenes.render(clientJar, temporary);
			} catch (IOException | RuntimeException e) {
				// Biomes and structures show their names' first letter instead.
				StatsWebuiPlus.LOGGER.warn("Could not draw the biome and structure scenes for the web UI", e);
			}
			java.util.Map<String, String> drawn = new java.util.TreeMap<>(icons.icons());
			try {
				BlockEntityIcons.render(clientJar, temporary).forEach(drawn::putIfAbsent);
			} catch (IOException | RuntimeException e) {
				// Chests, banners, heads and the like show a related icon or a letter instead.
				StatsWebuiPlus.LOGGER.warn("Could not draw the chests, banners and heads for the web UI", e);
			}
			java.util.Map<String, String> table = IconTable.write(clientJar, temporary, drawn, faces, scenes);
			Path target = dir.resolve("mc");
			deleteTree(target);
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
			Files.writeString(dir.resolve(COMPLETE), Integer.toString(FORMAT), StandardCharsets.UTF_8);
			// Only needed to extract again, which a new version or mod update does with a fresh download anyway.
			Files.deleteIfExists(clientJar);
			files = target;
			state = State.READY;
			removeOtherVersions(root, gameVersion);
			StatsWebuiPlus.LOGGER.info("Web UI textures ready in {}: {} textures, {} icons ({} drawn, {} mob faces, {} scenes), texts in {} languages ({} s)",
				target, copied, table.size(), drawn.size(), faces.size(), scenes.size(), GameLanguages.FILES.size(), (System.nanoTime() - started) / 1_000_000_000L);
		} catch (Exception e) {
			state = State.FAILED;
			StatsWebuiPlus.LOGGER.warn("Could not get the game's textures for the web UI ({}); it shows fallbacks and tries "
				+ "again at the next start", e.toString());
		} finally {
			settle();
		}
	}

	/** A still icon's size variant for the web UI, made now (see Sizes); null if there is none. */
	public byte[] sized(String relative) {
		if (state != State.READY) {
			return null;
		}
		try {
			return Sizes.onRequest(relative, icon -> file(icon).orElse(null));
		} catch (IOException | RuntimeException e) {
			StatsWebuiPlus.LOGGER.debug("Could not size {}", relative, e);
			return null;
		}
	}

	private static boolean isComplete(Path dir) {
		try {
			Path marker = dir.resolve(COMPLETE);
			return Files.isRegularFile(marker) && Files.isDirectory(dir.resolve("mc"))
				&& Integer.toString(FORMAT).equals(Files.readString(marker, StandardCharsets.UTF_8).trim());
		} catch (IOException e) {
			return false;
		}
	}

	/** A finished file below /mc/, or empty while there is none (not ready, off, or no such file). */
	public Optional<Path> file(String relative) {
		Path root = files;
		if (root == null || state != State.READY) {
			return Optional.empty();
		}
		// Both normalized: the config folder may come as run/./config, which a normalized file never starts with.
		root = root.toAbsolutePath().normalize();
		Path file = root.resolve(relative).normalize();
		return file.startsWith(root) && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
	}

	private static void removeOtherVersions(Path root, String keep) {
		try (Stream<Path> versions = Files.list(root)) {
			for (Path other : versions.filter(Files::isDirectory).toList()) {
				if (!other.getFileName().toString().equals(keep)) {
					deleteTree(other);
					StatsWebuiPlus.LOGGER.info("Removed the web UI textures of an older Minecraft version: {}", other);
				}
			}
		} catch (IOException e) {
			StatsWebuiPlus.LOGGER.debug("Could not clean up {}", root, e);
		}
	}

	static void deleteTree(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(path)) {
			for (Path each : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(each);
			}
		}
	}
}
