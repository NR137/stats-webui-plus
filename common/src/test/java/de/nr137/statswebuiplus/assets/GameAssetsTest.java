package de.nr137.statswebuiplus.assets;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The game's files come from Mojang at server start, not from the mod JAR: the download and the plain copies. */
class GameAssetsTest {
	@TempDir
	Path dir;

	private HttpServer mojang;
	private String base;
	private final Map<String, byte[]> files = new ConcurrentHashMap<>();
	private final AtomicInteger clientDownloads = new AtomicInteger();

	@BeforeEach
	void start() throws IOException {
		mojang = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		mojang.createContext("/", this::answer);
		mojang.start();
		base = "http://127.0.0.1:" + mojang.getAddress().getPort();
	}

	@AfterEach
	void stop() {
		mojang.stop(0);
	}

	private void answer(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		if (path.equals("/client.jar")) {
			clientDownloads.incrementAndGet();
		}
		byte[] body = files.get(path);
		exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : body.length);
		if (body != null) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		}
		exchange.close();
	}

	@ParameterizedTest
	@CsvSource({
		"assets/minecraft/textures/item/apple.png,                                  item/apple.png",
		"assets/minecraft/textures/block/stone.png,                                 block/stone.png",
		"assets/minecraft/textures/gui/sprites/advancements/goal_frame_obtained.png, advancement/goal_frame_obtained.png",
		"assets/minecraft/textures/gui/advancements/backgrounds/stone.png,          advancement/backgrounds/stone.png",
		"assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png,     gui/experience_bar_progress.png",
		"assets/minecraft/textures/entity/chest/ender.png,                          entity/ender_chest.png",
		"assets/minecraft/textures/entity/player/slim/alex.png,                     skins/slim/alex.png"
	})
	void texturesGoWhereTheWebUiLooks(String entry, String destination) {
		assertEquals(Optional.of(destination), GameTextures.destination(entry));
	}

	@Test
	void otherFilesAreNotCopied() {
		assertEquals(Optional.empty(), GameTextures.destination("assets/minecraft/textures/entity/creeper/creeper.png"));
		assertEquals(Optional.empty(), GameTextures.destination("assets/minecraft/models/item/apple.json"));
		assertEquals(Optional.empty(), GameTextures.destination("assets/minecraft/textures/item/apple.png.mcmeta"));
		assertEquals(Optional.empty(), GameTextures.destination("assets/minecraft/textures/gui/sprites/advancements/tab_above_left.png"));
	}

	@Test
	void downloadsTheClientJarOnceAndCopiesItsTextures() throws Exception {
		byte[] jar = zip(Map.of(
			"assets/minecraft/textures/item/apple.png", "apple",
			"assets/minecraft/textures/entity/chest/normal.png", "chest",
			"assets/minecraft/textures/entity/creeper/creeper.png", "creeper",
			"net/minecraft/client/Main.class", "code"
		));
		serveVersion("26.3", jar, sha1(jar));
		MojangDownloads downloads = new MojangDownloads(URI.create(base + "/manifest.json"), URI.create(base + "/assets/"));
		MojangDownloads.Version version = downloads.version("26.3");
		Path clientJar = dir.resolve("cache/26.3/client.jar");

		downloads.fetch(version.client(), clientJar);
		downloads.fetch(version.client(), clientJar);
		assertEquals(1, clientDownloads.get(), "a client JAR with the right SHA-1 is not downloaded again");

		Path out = dir.resolve("mc");
		assertEquals(2, GameTextures.copy(clientJar, out));
		assertEquals("apple", Files.readString(out.resolve("item/apple.png")));
		assertEquals("chest", Files.readString(out.resolve("entity/chest.png")));
		assertFalse(Files.exists(out.resolve("entity/creeper.png")));
	}

	@Test
	void aDamagedDownloadIsNotKept() throws Exception {
		byte[] jar = zip(Map.of("assets/minecraft/textures/item/apple.png", "apple"));
		serveVersion("26.3", jar, "0000000000000000000000000000000000000000");
		MojangDownloads downloads = new MojangDownloads(URI.create(base + "/manifest.json"), URI.create(base + "/assets/"));
		Path clientJar = dir.resolve("cache/26.3/client.jar");

		IOException error = assertThrows(IOException.class,
			() -> downloads.fetch(downloads.version("26.3").client(), clientJar));
		assertTrue(error.getMessage().contains("damaged"), error.getMessage());
		assertFalse(Files.exists(clientJar));
		assertFalse(Files.exists(clientJar.resolveSibling("client.jar.part")));
	}

	@Test
	void anUnknownVersionSaysSo() {
		files.put("/manifest.json", "{\"versions\": []}".getBytes(StandardCharsets.UTF_8));
		MojangDownloads downloads = new MojangDownloads(URI.create(base + "/manifest.json"), URI.create(base + "/assets/"));
		IOException error = assertThrows(IOException.class, () -> downloads.version("99.1"));
		assertTrue(error.getMessage().contains("99.1"), error.getMessage());
	}

	@Test
	void theServerDownloadsOnceAndKeepsOnlyItsVersion() throws Exception {
		byte[] jar = zip(Map.of(
			"assets/minecraft/textures/item/apple.png", "apple",
			"assets/minecraft/items/apple.json", "{}",
			"assets/minecraft/lang/en_us.json", "{\"item.minecraft.apple\": \"Apple\", \"advancements.story.root.title\": \"Minecraft\"}"
		));
		serveVersion("26.3", jar, sha1(jar));
		MojangDownloads downloads = new MojangDownloads(URI.create(base + "/manifest.json"), URI.create(base + "/assets/"));
		Path root = dir.resolve("cache");
		Files.createDirectories(root.resolve("26.2/mc"));
		GameAssets assets = GameAssets.get();

		assets.start(root, "26.3", true, downloads);
		assets.await();
		assertEquals(GameAssets.State.READY, assets.state());
		assertEquals("apple", Files.readString(assets.file("item/apple.png").orElseThrow()));
		assertTrue(assets.file("item/missing.png").isEmpty());
		assertTrue(assets.file("../../26.3/complete").isEmpty(), "nothing outside the textures is served");
		String english = Files.readString(assets.file("lang/en.json").orElseThrow());
		assertTrue(english.contains("\"minecraft:apple\":\"Apple\"") && english.contains("\"advancements.story.root.title\":\"Minecraft\""), english);
		assertTrue(Files.readString(assets.file("lang/de.json").orElseThrow()).contains("apple de_de"),
			"the other languages come from the asset CDN");
		assertFalse(Files.exists(root.resolve("26.3/client.jar")), "the client JAR isn't kept once extracted");
		assertFalse(Files.exists(root.resolve("26.2")), "an older version's textures are removed");

		assets.start(root, "26.3", true, downloads);
		assets.await();
		assertEquals(GameAssets.State.READY, assets.state());
		assertEquals(1, clientDownloads.get(), "the next start uses the finished cache");
	}

	@Test
	void theUiGetsTheNamesAndAdvancementTextsOfALanguage() {
		com.google.gson.JsonObject lang = com.google.gson.JsonParser.parseString("""
			{"item.minecraft.apple": "Apple", "block.minecraft.stone": "Stone", "block.minecraft.oak_wall_sign": "Oak Sign",
			 "entity.minecraft.zombie": "Zombie", "entity.minecraft.villager.farmer": "Farmer",
			 "biome.minecraft.plains": "Plains", "trim_pattern.minecraft.silence": "Silence Armor Trim",
			 "stat.minecraft.jump": "Jumps", "stat.minecraft.custom": "Custom",
			 "advancements.story.root.title": "Minecraft", "gui.done": "Done"}
			""").getAsJsonObject();
		Map<String, Map<String, String>> texts = GameLanguages.texts(lang, java.util.List.of("apple", "stone"),
			java.util.List.of("stone", "oak_wall_sign"));
		assertEquals(Map.of("minecraft:apple", "Apple", "minecraft:stone", "Stone"), texts.get("item"),
			"block items have only a block name");
		assertEquals(Map.of("minecraft:stone", "Stone", "minecraft:oak_wall_sign", "Oak Sign"), texts.get("block"));
		assertEquals(Map.of("minecraft:zombie", "Zombie"), texts.get("entity"), "no villager professions");
		assertEquals(Map.of("minecraft:plains", "Plains"), texts.get("biome"));
		assertEquals(Map.of("minecraft:silence", "Silence Armor Trim"), texts.get("trim"));
		assertEquals(Map.of("advancements.story.root.title", "Minecraft"), texts.get("advancement"));
		assertEquals(Map.of("minecraft:jump", "Jumps", "minecraft:custom", "Custom"), texts.get("stat"));
	}

	@Test
	void switchedOffNothingIsDownloaded() throws Exception {
		GameAssets assets = GameAssets.get();
		assets.start(dir.resolve("cache"), "26.3", false, new MojangDownloads(URI.create(base + "/manifest.json"), URI.create(base + "/assets/")));
		assets.await();
		assertEquals(GameAssets.State.DISABLED, assets.state());
		assertTrue(assets.file("item/apple.png").isEmpty());
		assertEquals(0, clientDownloads.get());
	}

	@Test
	void withoutMojangTheUiKeepsItsFallbacks() throws Exception {
		GameAssets assets = GameAssets.get();
		assets.start(dir.resolve("cache"), "26.3", true, new MojangDownloads(URI.create(base + "/manifest.json"), URI.create(base + "/assets/")));
		assets.await();
		assertEquals(GameAssets.State.FAILED, assets.state());
		assertTrue(assets.file("item/apple.png").isEmpty());
		assertFalse(Files.exists(dir.resolve("cache/26.3/complete")), "tried again at the next start");
	}

	private void serveVersion(String id, byte[] jar, String sha1) throws Exception {
		files.put("/client.jar", jar);
		// The other languages, from the asset CDN at /assets/<first two of the hash>/<hash>.
		StringBuilder objects = new StringBuilder();
		for (String lang : new String[] {"de_de", "es_es", "fr_fr"}) {
			byte[] file = ("{\"item.minecraft.apple\": \"apple " + lang + "\"}").getBytes(StandardCharsets.UTF_8);
			String hash = sha1(file);
			files.put("/assets/" + hash.substring(0, 2) + "/" + hash, file);
			objects.append(objects.isEmpty() ? "" : ",").append("\"minecraft/lang/").append(lang).append(".json\": {\"hash\": \"")
				.append(hash).append("\", \"size\": ").append(file.length).append("}");
		}
		byte[] index = ("{\"objects\": {" + objects + "}}").getBytes(StandardCharsets.UTF_8);
		files.put("/index.json", index);
		String indexSha1 = sha1(index);
		files.put("/manifest.json", ("{\"versions\": [{\"id\": \"" + id + "\", \"url\": \"" + base + "/" + id + ".json\"}]}")
			.getBytes(StandardCharsets.UTF_8));
		files.put("/" + id + ".json", ("{\"downloads\": {\"client\": {\"url\": \"" + base + "/client.jar\", \"sha1\": \"" + sha1
			+ "\", \"size\": " + jar.length + "}}, \"assetIndex\": {\"url\": \"" + base + "/index.json\", \"sha1\": \"" + indexSha1
			+ "\", \"size\": " + index.length + "}}")
			.getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] zip(Map<String, String> entries) throws IOException {
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	private static String sha1(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
	}
}
