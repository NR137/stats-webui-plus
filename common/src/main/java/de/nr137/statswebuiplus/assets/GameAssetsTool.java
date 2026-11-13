package de.nr137.statswebuiplus.assets;

import java.nio.file.Path;

/**
 * Runs the extraction outside a server, for development: ./gradlew :common:extractGameAssets
 * Arguments: Minecraft version, output folder (what /mc/ serves), cache folder (downloads), optionally the web code's
 * copy of the icon table. With "--download-only" as the output folder it only downloads the client JAR (for the sample
 * data and the smoke test: ./gradlew :common:downloadClientJar).
 */
public final class GameAssetsTool {
	private GameAssetsTool() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.err.println("Usage: GameAssetsTool <minecraft version> <output folder | --download-only> <cache folder>");
			System.exit(2);
		}
		String version = args[0];
		Path out = Path.of(args[1].equals("--download-only") ? "." : args[1]);
		Path cache = Path.of(args[2]).resolve(version);
		MojangDownloads mojang = new MojangDownloads();
		MojangDownloads.Version meta = mojang.version(version);
		Path clientJar = cache.resolve("client.jar");
		System.out.println("Client JAR " + meta.client().url() + " -> " + clientJar);
		mojang.fetch(meta.client(), clientJar);
		if (args[1].equals("--download-only")) {
			return;
		}
		int copied = GameTextures.copy(clientJar, out);
		System.out.println("Copied " + copied + " textures to " + out);
		GameLanguages.write(clientJar, mojang, meta, out);
		System.out.println("Wrote the game's texts for " + GameLanguages.FILES.keySet() + " to " + out.resolve("lang"));
		GameIcons.Result icons = GameIcons.render(clientJar, out);
		System.out.println("Resolved " + icons.icons().size() + " icons, animated: " + icons.animated());
		if (Boolean.getBoolean("stats.dumpLayers")) {
			java.nio.file.Files.writeString(out.resolveSibling("layers.json"), ClientClasses.layers(clientJar));
			java.nio.file.Files.writeString(out.resolveSibling("boxes.json"), ClientClasses.boxes(clientJar,
				java.util.Set.of(System.getProperty("stats.dumpBoxes", "").split(","))));
		}
		java.util.List<String> failed = new java.util.ArrayList<>();
		java.util.Map<String, String> faces = MobFaces.render(clientJar, out, failed);
		System.out.println("Mob faces: " + faces.size() + ", without a face: " + failed);
		java.util.Map<String, String> scenes = Scenes.render(clientJar, out);
		System.out.println("Scenes: " + scenes.size());
		java.util.Map<String, String> drawn = new java.util.TreeMap<>(icons.icons());
		java.util.Map<String, String> blockEntities = BlockEntityIcons.render(clientJar, out);
		System.out.println("Block entities: " + blockEntities.size());
		blockEntities.forEach(drawn::putIfAbsent);
		java.util.Map<String, String> table = IconTable.write(clientJar, out, drawn, faces, scenes);
		System.out.println("Icon table: " + table.size() + " entries in " + out.resolve("icons.json"));
		// What the item models alone resolved, next to the output, to compare with the build's icons.json.
		java.nio.file.Files.writeString(out.resolveSibling("resolved-icons.json"),
			new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(icons.icons()));
	}
}
