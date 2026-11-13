package de.nr137.statswebuiplus.assets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The game's textures the web UI shows as they are, copied out of the client JAR under the paths the UI asks for
 * (/mc/item/apple.png, ...). No drawing: the icons made from several textures come from elsewhere.
 */
public final class GameTextures {
	private static final String TEXTURES = "assets/minecraft/textures/";

	/** Single files under a name of their own: the textures the UI draws chests, the shield, ... from. */
	private static final Map<String, String> SINGLE = Map.of(
		"gui/sprites/hud/experience_bar_background.png", "gui/experience_bar_background.png",
		"gui/sprites/hud/experience_bar_progress.png", "gui/experience_bar_progress.png",
		"entity/chest/normal.png", "entity/chest.png",
		"entity/chest/ender.png", "entity/ender_chest.png",
		"entity/chest/trapped.png", "entity/trapped_chest.png",
		"entity/chest/copper.png", "entity/copper_chest.png",
		"entity/shield/shield_base_nopattern.png", "entity/shield.png",
		"entity/bed/red.png", "entity/bed.png",
		"entity/shulker/shulker.png", "entity/shulker.png"
	);

	private GameTextures() {
	}

	/** Where a file of the client JAR goes below /mc/, or empty if the UI doesn't use it as it is. */
	public static Optional<String> destination(String entry) {
		if (!entry.startsWith(TEXTURES) || !entry.endsWith(".png")) {
			return Optional.empty();
		}
		String path = entry.substring(TEXTURES.length());
		String file = path.substring(path.lastIndexOf('/') + 1);
		if (path.startsWith("item/")) {
			return Optional.of("item/" + file);
		}
		if (path.startsWith("block/")) {
			return Optional.of("block/" + file);
		}
		// The background shapes of advancements: task, goal and challenge, each obtained and unobtained.
		if (path.startsWith("gui/sprites/advancements/") && file.contains("_frame_")) {
			return Optional.of("advancement/" + file);
		}
		// Each advancement tab's background (stone, nether, end, ...), tiled behind its tree.
		if (path.startsWith("gui/advancements/backgrounds/")) {
			return Optional.of("advancement/backgrounds/" + file);
		}
		// The default skins (Steve, Alex, ...) for players without one of their own: skins/<slim|wide>/<name>.png.
		if (path.startsWith("entity/player/slim/") || path.startsWith("entity/player/wide/")) {
			return Optional.of("skins/" + path.substring("entity/player/".length()));
		}
		return Optional.ofNullable(SINGLE.get(path));
	}

	/** Copies every file with a destination from the client JAR into out; returns how many. */
	public static int copy(Path clientJar, Path out) throws IOException {
		int copied = 0;
		try (ZipFile jar = new ZipFile(clientJar.toFile())) {
			Enumeration<? extends ZipEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				Optional<String> destination = entry.isDirectory() ? Optional.empty() : destination(entry.getName());
				if (destination.isEmpty()) {
					continue;
				}
				Path target = out.resolve(destination.get());
				Files.createDirectories(target.getParent());
				try (InputStream in = jar.getInputStream(entry)) {
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
				copied++;
			}
		}
		return copied;
	}
}
