package de.nr137.statswebuiplus.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * The game's files from Mojang's own servers, as the launcher gets them: the version manifest, a version's client JAR
 * (checked against the SHA-1 the manifest gives) and its asset index (languages). The mod ships none of them.
 */
public final class MojangDownloads {
	public static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
	public static final URI ASSETS = URI.create("https://resources.download.minecraft.net/");
	private static final String USER_AGENT = "stats-webui-plus (Minecraft server mod)";

	/** A file to download and what it must be: Mojang lists the SHA-1 and size of each. */
	public record Download(URI url, String sha1, long size) {
	}

	/** What a Minecraft version needs from Mojang: the client JAR and the asset index. */
	public record Version(String id, Download client, Download assetIndex) {
	}

	private final HttpClient http;
	private final URI manifest;
	private final URI assets;

	public MojangDownloads() {
		this(MANIFEST, ASSETS);
	}

	/** With other addresses, for tests against a fake Mojang. */
	public MojangDownloads(URI manifest, URI assets) {
		this.manifest = manifest;
		this.assets = assets;
		this.http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(20))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	}

	/** The version's downloads, from the manifest and the version's own file. */
	public Version version(String id) throws IOException {
		JsonObject list = json(manifest).getAsJsonObject();
		for (JsonElement entry : list.getAsJsonArray("versions")) {
			JsonObject version = entry.getAsJsonObject();
			if (!id.equals(version.get("id").getAsString())) {
				continue;
			}
			JsonObject meta = json(manifest.resolve(version.get("url").getAsString())).getAsJsonObject();
			JsonObject client = meta.getAsJsonObject("downloads").getAsJsonObject("client");
			JsonObject index = meta.getAsJsonObject("assetIndex");
			return new Version(id, download(client), download(index));
		}
		throw new IOException("Minecraft " + id + " is not in Mojang's version manifest");
	}

	private static Download download(JsonObject entry) {
		return new Download(URI.create(entry.get("url").getAsString()), entry.get("sha1").getAsString(),
			entry.get("size").getAsLong());
	}

	public JsonElement json(URI url) throws IOException {
		HttpResponse<String> response = send(url, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return JsonParser.parseString(response.body());
	}

	/**
	 * Downloads to target unless a file with the right SHA-1 is already there. Written next to it first and moved
	 * into place once complete and checked, so a cut connection never leaves a broken file behind.
	 */
	public void fetch(Download download, Path target) throws IOException {
		if (Files.isRegularFile(target) && download.sha1().equalsIgnoreCase(sha1(target))) {
			return;
		}
		Files.createDirectories(target.getParent());
		Path part = target.resolveSibling(target.getFileName() + ".part");
		HttpResponse<InputStream> response = send(download.url(), HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
			in.transferTo(out);
		}
		String actual = sha1(part);
		if (!download.sha1().equalsIgnoreCase(actual) || Files.size(part) != download.size()) {
			Files.deleteIfExists(part);
			throw new IOException("Download of " + download.url() + " is damaged (SHA-1 " + actual + ", expected "
				+ download.sha1() + ")");
		}
		Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/** A small file in memory, checked like fetch() checks a big one. */
	public byte[] bytes(Download download) throws IOException {
		byte[] body = send(download.url(), HttpResponse.BodyHandlers.ofByteArray()).body();
		String actual = sha1(body);
		if (!download.sha1().equalsIgnoreCase(actual) || body.length != download.size()) {
			throw new IOException("Download of " + download.url() + " is damaged (SHA-1 " + actual + ", expected "
				+ download.sha1() + ")");
		}
		return body;
	}

	/** A file of the version's asset index ("minecraft/lang/de_de.json"), from Mojang's asset CDN. */
	public Download asset(JsonObject index, String name) throws IOException {
		JsonObject objects = index.getAsJsonObject("objects");
		JsonElement entry = objects == null ? null : objects.get(name);
		if (entry == null || !entry.isJsonObject()) {
			throw new IOException(name + " is not in the asset index");
		}
		String hash = entry.getAsJsonObject().get("hash").getAsString();
		return new Download(assets.resolve(hash.substring(0, 2) + "/" + hash), hash,
			entry.getAsJsonObject().get("size").getAsLong());
	}

	public static String sha1(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private <T> HttpResponse<T> send(URI url, HttpResponse.BodyHandler<T> body) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(url)
			.timeout(Duration.ofMinutes(5))
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();
		try {
			HttpResponse<T> response = http.send(request, body);
			if (response.statusCode() != 200) {
				throw new IOException("HTTP " + response.statusCode() + " for " + url);
			}
			return response;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while downloading " + url, e);
		}
	}

	public static String sha1(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] buffer = new byte[1 << 16];
			for (int read; (read = in.read(buffer)) > 0; ) {
				digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
