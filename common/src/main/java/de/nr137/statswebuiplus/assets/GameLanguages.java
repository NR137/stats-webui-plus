package de.nr137.statswebuiplus.assets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The game's own texts for the web UI, one file per language: /mc/lang/&lt;locale&gt;.json with the official
 * block, item, entity, biome and trim names (stat labels, advancement criteria) and the advancement titles and
 * descriptions. English is in the client JAR; the other languages come from Mojang's asset CDN, like the launcher
 * gets them.
 */
public final class GameLanguages {
	/** The web UI's languages and the game's language file of each. */
	static final Map<String, String> FILES = Map.of("en", "en_us", "de", "de_de", "es", "es_es", "fr", "fr_fr");
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private GameLanguages() {
	}

	/** Writes lang/&lt;locale&gt;.json into out for every language; returns how many. */
	public static int write(Path clientJar, MojangDownloads mojang, MojangDownloads.Version version, Path out)
		throws IOException {
		TreeSet<String> items = new TreeSet<>();
		TreeSet<String> blocks = new TreeSet<>();
		JsonObject english;
		try (ZipFile jar = new ZipFile(clientJar.toFile())) {
			Enumeration<? extends ZipEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				if (name.endsWith(".json") && name.startsWith("assets/minecraft/items/")) {
					items.add(stem(name));
				} else if (name.endsWith(".json") && name.startsWith("assets/minecraft/blockstates/")) {
					blocks.add(stem(name));
				}
			}
			ZipEntry lang = jar.getEntry("assets/minecraft/lang/en_us.json");
			if (lang == null) {
				throw new IOException("The client JAR has no en_us.json");
			}
			try (InputStream in = jar.getInputStream(lang)) {
				english = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			}
		}
		JsonObject index = null;
		Path folder = out.resolve("lang");
		Files.createDirectories(folder);
		for (Map.Entry<String, String> language : new TreeMap<>(FILES).entrySet()) {
			JsonObject lang;
			if (language.getValue().equals("en_us")) {
				lang = english;
			} else {
				if (index == null) {
					index = JsonParser.parseString(new String(mojang.bytes(version.assetIndex()), StandardCharsets.UTF_8))
						.getAsJsonObject();
				}
				byte[] file = mojang.bytes(mojang.asset(index, "minecraft/lang/" + language.getValue() + ".json"));
				lang = JsonParser.parseString(new String(file, StandardCharsets.UTF_8)).getAsJsonObject();
			}
			Files.writeString(folder.resolve(language.getKey() + ".json"), GSON.toJson(texts(lang, items, blocks)),
				StandardCharsets.UTF_8);
		}
		return FILES.size();
	}

	/**
	 * English from the running server itself, for when the game's files aren't downloaded (an offline server, or
	 * downloadGameAssets turned off): its own en_us.json, with the item and block IDs from its registries instead of
	 * the client JAR's item and block state files. The same file as the downloaded lang/en.json.
	 */
	public static byte[] serverEnglish() throws IOException {
		JsonObject lang;
		try (InputStream in = net.minecraft.locale.Language.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
			if (in == null) {
				throw new IOException("The server has no en_us.json");
			}
			lang = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		return GSON.toJson(texts(lang, vanilla(net.minecraft.core.registries.BuiltInRegistries.ITEM),
			vanilla(net.minecraft.core.registries.BuiltInRegistries.BLOCK))).getBytes(StandardCharsets.UTF_8);
	}

	/** The game's own IDs in a registry (without a mod's namespace), sorted. */
	private static <T> TreeSet<String> vanilla(net.minecraft.core.Registry<T> registry) {
		TreeSet<String> ids = new TreeSet<>();
		for (net.minecraft.resources.Identifier id : registry.keySet()) {
			if (id.getNamespace().equals("minecraft")) {
				ids.add(id.getPath());
			}
		}
		return ids;
	}

	/** The texts of one language file the web UI uses, by kind, sorted like the files always were. */
	static Map<String, Map<String, String>> texts(JsonObject lang, Iterable<String> items, Iterable<String> blocks) {
		Map<String, Map<String, String>> names = new LinkedHashMap<>();
		for (String kind : new String[] {"advancement", "biome", "block", "entity", "item", "stat", "trim"}) {
			names.put(kind, new TreeMap<>());
		}
		for (String item : items) {
			// Block items (stone, oak_planks, ...) only have a block.* name.
			String value = string(lang, "item.minecraft." + item);
			if (value == null) {
				value = string(lang, "block.minecraft." + item);
			}
			if (value != null) {
				names.get("item").put("minecraft:" + item, value);
			}
		}
		for (String block : blocks) {
			String value = string(lang, "block.minecraft." + block);
			if (value != null) {
				names.get("block").put("minecraft:" + block, value);
			}
		}
		for (Map.Entry<String, JsonElement> entry : lang.entrySet()) {
			String key = entry.getKey();
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			String value = entry.getValue().getAsString();
			if (key.startsWith("advancements.")) {
				names.get("advancement").put(key, value);
			} else if (plain(key, "entity.minecraft.")) {
				names.get("entity").put("minecraft:" + key.substring("entity.minecraft.".length()), value);
			} else if (plain(key, "biome.minecraft.")) {
				names.get("biome").put("minecraft:" + key.substring("biome.minecraft.".length()), value);
			} else if (plain(key, "stat.minecraft.")) {
				// The general statistics (minecraft:custom): "Distance Walked", "Times Slept in a Bed", ...
				names.get("stat").put("minecraft:" + key.substring("stat.minecraft.".length()), value);
			} else if (plain(key, "trim_pattern.minecraft.")) {
				// Every template item is just "Smithing Template"; the pattern has the name ("Silence Armor Trim").
				names.get("trim").put("minecraft:" + key.substring("trim_pattern.minecraft.".length()), value);
			}
		}
		return names;
	}

	/** "entity.minecraft.zombie", not "entity.minecraft.zombie.baby" or similar. */
	private static boolean plain(String key, String prefix) {
		return key.startsWith(prefix) && key.indexOf('.', prefix.length()) < 0;
	}

	private static String string(JsonObject lang, String key) {
		JsonElement value = lang.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static String stem(String entry) {
		String file = entry.substring(entry.lastIndexOf('/') + 1);
		return file.substring(0, file.length() - ".json".length());
	}
}
