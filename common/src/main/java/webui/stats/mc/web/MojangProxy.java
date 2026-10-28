package webui.stats.mc.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import webui.stats.mc.McStatsWebui;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class MojangProxy {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(6))
		.build();
	private static final Pattern UUID_PATTERN = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[0-9a-f]{32}");
	private static final Pattern TEXTURE = Pattern.compile("(?i)[0-9a-f]+");
	private static final String PROFILE_PATH = "/mojang/session/minecraft/profile/";
	private static final String TEXTURE_PATH = "/mojang/textures/texture/";
	// After this, a profile is still served but looked up again in the background, so a skin change shows up on the
	// site a visit later without anyone waiting for Mojang.
	private static final long PROFILE_TTL_MS = 5 * 60_000L;
	// Skins are a few KB and profiles about one, so this caps the cache at roughly a megabyte.
	private static final int CACHE_ENTRIES = 512;
	// Players whose skins are fetched at startup; Mojang allows about 600 profile lookups per 10 minutes.
	private static final int PREWARM_PLAYERS = 100;
	// Minecraft's default skins in DefaultPlayerSkin's order: these nine slim, then the same nine wide. The web UI
	// serves them from the client JAR as /mc/skins/<slim|wide>/<name>.png.
	private static final List<String> DEFAULT_SKINS = List.of("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri");

	// Mojang requests get threads of their own, so a slow Mojang never holds up the stats API.
	private final ExecutorService fetcher = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, McStatsWebui.MOD_ID + "-mojang");
		thread.setDaemon(true);
		return thread;
	});
	// Least recently used entries go first once the cache is full.
	private final Map<URI, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<URI, Cached> eldest) {
			return size() > CACHE_ENTRIES;
		}
	};
	// Requests to Mojang under way, so concurrent asks for the same URL (a head and the 3D skin) share one.
	private final Map<URI, CompletableFuture<Fetched>> inFlight = new ConcurrentHashMap<>();
	private final String profileBase;
	private final String textureBase;

	public MojangProxy() {
		this("https://sessionserver.mojang.com/session/minecraft/profile/", "https://textures.minecraft.net/texture/");
	}

	/** With other servers in place of Mojang's, for tests; both bases end with "/". */
	MojangProxy(String profileBase, String textureBase) {
		this.profileBase = profileBase;
		this.textureBase = textureBase;
	}

	public void handle(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			HttpJson.error(exchange, 405, "method_not_allowed", "Use GET");
			return;
		}
		URI target = resolve(exchange.getRequestURI().getPath());
		if (target == null) {
			HttpJson.error(exchange, 404, "not_found", "Unknown Mojang proxy path");
			return;
		}
		boolean texture = isTexture(target);
		Cached cached = cached(target);
		if (cached != null) {
			if (!texture && cached.ageMs() >= PROFILE_TTL_MS) {
				// This answer is the older copy; the next one has whatever Mojang says now.
				fetch(target);
			}
			send(exchange, 200, cached.contentType(), cached.body(), cacheControl(texture, cached.ageMs()));
			return;
		}
		// Finished on a fetch thread, so this web server thread is free again at once; respond() closes the exchange.
		fetch(target).whenComplete((fetched, error) -> respond(exchange, target, texture, fetched, error));
	}

	/** Fetches the profile, skin and cape of these players in the background, so their pages never wait for Mojang. */
	public void prewarm(List<UUID> players) {
		for (UUID player : players.subList(0, Math.min(players.size(), PREWARM_PLAYERS))) {
			URI profile = resolve(PROFILE_PATH + player);
			if (profile == null || cached(profile) != null) {
				continue;
			}
			fetch(profile).thenAccept(fetched -> {
				if (fetched.status() != 200) {
					return;
				}
				for (URI texture : textureUrls(fetched.body())) {
					if (cached(texture) == null) {
						fetch(texture);
					}
				}
			});
		}
	}

	private CompletableFuture<Fetched> fetch(URI target) {
		CompletableFuture<Fetched> created = new CompletableFuture<>();
		CompletableFuture<Fetched> running = inFlight.putIfAbsent(target, created);
		if (running != null) {
			return running;
		}
		fetcher.execute(() -> {
			try {
				created.complete(download(target));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				created.completeExceptionally(e);
			} catch (Exception e) {
				created.completeExceptionally(e);
			} finally {
				inFlight.remove(target, created);
			}
		});
		return created;
	}

	private Fetched download(URI target) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(target)
			.timeout(Duration.ofSeconds(8))
			.header("User-Agent", McStatsWebui.MOD_ID)
			.GET()
			.build();
		HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
		String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
		byte[] body = response.body() == null ? new byte[0] : response.body();
		Fetched fetched = new Fetched(response.statusCode(), contentType, body);
		if (!isTexture(target)) {
			fetched = withDefaultSkin(target, fetched);
		}
		// "No profile" answers become profiles above, so they are cached too and Mojang isn't asked on every visit.
		if (fetched.status() == 200) {
			synchronized (cache) {
				cache.put(target, new Cached(fetched.contentType(), fetched.body(), System.nanoTime()));
			}
		}
		return fetched;
	}

	/**
	 * A player without a skin of their own gets the default skin the game shows them, picked from the UUID like
	 * DefaultPlayerSkin.get: when Mojang has no profile (offline mode, 204) or the profile has no skin. The answer is
	 * a profile whose skin is one of the web UI's files, so the page treats it like any other.
	 */
	private static Fetched withDefaultSkin(URI target, Fetched fetched) {
		JsonObject profile;
		JsonObject textures = new JsonObject();
		if (fetched.status() == 204 || fetched.status() == 404) {
			profile = new JsonObject();
			profile.addProperty("id", uuidOf(target).toString().replace("-", ""));
			profile.addProperty("name", "");
		} else if (fetched.status() == 200) {
			try {
				profile = JsonParser.parseString(new String(fetched.body(), StandardCharsets.UTF_8)).getAsJsonObject();
				JsonObject payload = texturesPayload(profile);
				if (payload != null && payload.has("textures")) {
					textures = payload.getAsJsonObject("textures");
				}
			} catch (RuntimeException e) {
				return fetched;
			}
			if (textures.has("SKIN")) {
				return fetched;
			}
		} else {
			return fetched;
		}
		int index = Math.floorMod(uuidOf(target).hashCode(), DEFAULT_SKINS.size() * 2);
		boolean slim = index < DEFAULT_SKINS.size();
		JsonObject skin = new JsonObject();
		skin.addProperty("url", "/mc/skins/" + (slim ? "slim/" : "wide/") + DEFAULT_SKINS.get(index % DEFAULT_SKINS.size()) + ".png");
		if (slim) {
			JsonObject metadata = new JsonObject();
			metadata.addProperty("model", "slim");
			skin.add("metadata", metadata);
		}
		textures.add("SKIN", skin);
		JsonObject payload = new JsonObject();
		payload.add("textures", textures);
		JsonObject property = new JsonObject();
		property.addProperty("name", "textures");
		property.addProperty("value", Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8)));
		JsonArray properties = new JsonArray();
		properties.add(property);
		profile.add("properties", properties);
		return new Fetched(200, "application/json", profile.toString().getBytes(StandardCharsets.UTF_8));
	}

	/** The decoded "textures" property of a profile, or null when it has none. */
	private static JsonObject texturesPayload(JsonObject profile) {
		if (!profile.has("properties")) {
			return null;
		}
		for (JsonElement property : profile.getAsJsonArray("properties")) {
			JsonObject entry = property.getAsJsonObject();
			if ("textures".equals(entry.get("name").getAsString())) {
				String decoded = new String(Base64.getDecoder().decode(entry.get("value").getAsString()), StandardCharsets.UTF_8);
				return JsonParser.parseString(decoded).getAsJsonObject();
			}
		}
		return null;
	}

	private static UUID uuidOf(URI profile) {
		String path = profile.getPath();
		return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
	}

	private void respond(HttpExchange exchange, URI target, boolean texture, Fetched fetched, Throwable error) {
		try {
			if (error != null) {
				Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
				McStatsWebui.LOGGER.warn("Mojang proxy failed for {}", target, cause);
				HttpJson.error(exchange, 502, "bad_gateway", "Could not reach Mojang");
			} else if (fetched.status() != 200) {
				// Errors and rate limits must not stick in browsers or proxies.
				send(exchange, fetched.status(), fetched.contentType(), fetched.body(), "no-store");
			} else {
				send(exchange, 200, fetched.contentType(), fetched.body(), cacheControl(texture, 0));
			}
		} catch (IOException e) {
			// The browser went away meanwhile.
			exchange.close();
		}
	}

	private Cached cached(URI target) {
		synchronized (cache) {
			return cache.get(target);
		}
	}

	private static String cacheControl(boolean texture, long ageMs) {
		// A texture URL names one image forever (a new skin gets a new URL); a profile points at the current skin.
		return texture
			? "public, max-age=86400, immutable"
			: "public, max-age=" + Math.max(0, (PROFILE_TTL_MS - ageMs) / 1000);
	}

	/** The skin and cape a profile response points to, as proxy targets. */
	private List<URI> textureUrls(byte[] profile) {
		List<URI> urls = new ArrayList<>();
		try {
			JsonObject payload = texturesPayload(JsonParser.parseString(new String(profile, StandardCharsets.UTF_8)).getAsJsonObject());
			JsonObject textures = payload == null ? null : payload.getAsJsonObject("textures");
			for (String kind : new String[] {"SKIN", "CAPE"}) {
				if (textures != null && textures.has(kind)) {
					String url = textures.getAsJsonObject(kind).get("url").getAsString();
					// A default skin is one of the web UI's own files; there is nothing to fetch from Mojang.
					if (!url.startsWith("http")) {
						continue;
					}
					URI target = resolve(TEXTURE_PATH + url.substring(url.lastIndexOf('/') + 1));
					if (target != null) {
						urls.add(target);
					}
				}
			}
		} catch (RuntimeException e) {
			// Not a profile with textures; nothing to prefetch.
		}
		return urls;
	}

	private static void send(HttpExchange exchange, int status, String contentType, byte[] body, String cacheControl)
		throws IOException {
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.getResponseHeaders().set("Cache-Control", cacheControl);
		boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
		exchange.sendResponseHeaders(status, head || body.length == 0 ? -1 : body.length);
		if (!head && body.length > 0) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		} else {
			exchange.close();
		}
	}

	private record Cached(String contentType, byte[] body, long fetchedAt) {
		long ageMs() {
			return (System.nanoTime() - fetchedAt) / 1_000_000L;
		}
	}

	private record Fetched(int status, String contentType, byte[] body) {
	}

	private boolean isTexture(URI target) {
		return target.toString().startsWith(textureBase);
	}

	private URI resolve(String path) {
		if (path.startsWith(PROFILE_PATH)) {
			String uuid = path.substring(PROFILE_PATH.length());
			if (!UUID_PATTERN.matcher(uuid).matches()) {
				return null;
			}
			// One cache entry per player, whichever way the UUID is written.
			String dashed = uuid.length() == 32
				? uuid.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5")
				: uuid;
			return URI.create(profileBase + dashed.toLowerCase(Locale.ROOT));
		}
		if (path.startsWith(TEXTURE_PATH)) {
			String hash = path.substring(TEXTURE_PATH.length());
			if (!TEXTURE.matcher(hash).matches()) {
				return null;
			}
			return URI.create(textureBase + hash.toLowerCase(Locale.ROOT));
		}
		return null;
	}
}
