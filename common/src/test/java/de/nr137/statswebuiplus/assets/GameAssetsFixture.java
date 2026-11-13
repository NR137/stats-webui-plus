package de.nr137.statswebuiplus.assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The game's files as a finished cache, for tests of what serves them; no download. */
public final class GameAssetsFixture {
	private GameAssetsFixture() {
	}

	/** Makes {@code root/<version>/mc} a complete cache and starts GameAssets on it: READY at once. */
	public static Path ready(Path root, String version) throws IOException {
		Path dir = root.resolve(version);
		Files.createDirectories(dir.resolve("mc"));
		Files.writeString(dir.resolve("complete"), Integer.toString(GameAssets.FORMAT), StandardCharsets.UTF_8);
		GameAssets.get().start(root, version, true, null);
		return dir.resolve("mc");
	}

	/** Back to no game files, as in the tests that don't start them. */
	public static void off(Path root) {
		GameAssets.get().start(root, "", false, null);
	}
}
