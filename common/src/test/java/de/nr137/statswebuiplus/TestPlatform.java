package de.nr137.statswebuiplus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A {@link Platform} with its own server folder, as a loader would pass it to {@link StatsWebuiPlus#init}. */
public record TestPlatform(Path gameDir) implements Platform {
	/** Creates {@code root/config} and makes it the platform of {@link StatsWebuiPlus}. */
	public static TestPlatform install(Path root) {
		TestPlatform platform = new TestPlatform(root);
		try {
			Files.createDirectories(platform.configDir());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		StatsWebuiPlus.init(platform);
		return platform;
	}

	@Override
	public Path configDir() {
		return gameDir.resolve("config");
	}

	@Override
	public String modVersion() {
		return "0.0.0-test";
	}

	@Override
	public String minecraftVersion() {
		return "26.3";
	}
}
