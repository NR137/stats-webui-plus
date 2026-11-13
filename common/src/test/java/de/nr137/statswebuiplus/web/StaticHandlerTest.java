package de.nr137.statswebuiplus.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import de.nr137.statswebuiplus.TestPlatform;
import de.nr137.statswebuiplus.assets.GameAssets;
import de.nr137.statswebuiplus.assets.GameAssetsFixture;

/**
 * Serving the SvelteKit build (adapter-static with fallback 200.html, see web/vite.config.ts) and the caching rules in
 * the README. The pages are test fixtures in src/test/resources.
 */
class StaticHandlerTest {
	private static TestServer server;
	// A server folder, as the link previews look for the header image in its config folder.
	@TempDir
	static Path serverFolder;

	@BeforeAll
	static void start() throws IOException {
		TestPlatform.install(serverFolder);
		StaticHandler handler = new StaticHandler();
		handler.preload();
		server = new TestServer(handler::handle);
	}

	@AfterAll
	static void stop() {
		server.close();
	}

	@Test
	void rootServesIndex() throws IOException {
		TestServer.Response response = server.get("/");
		assertEquals(200, response.status());
		assertTrue(response.header("Content-Type").startsWith("text/html"), response.header("Content-Type"));
		assertTrue(response.text().contains("index page"));
	}

	@Test
	void prerenderedPageWithoutExtension() throws IOException {
		TestServer.Response response = server.get("/about");
		assertEquals(200, response.status());
		assertTrue(response.text().contains("about page"));
	}

	@Test
	void clientRoutesGetFallbackPage() throws IOException {
		for (String path : new String[] {
			"/players/Steve", "/players/Steve/advancements", "/leaderboards/mined/minecraft%3Astone",
			"/advancements/minecraft/story/mine_stone", "/players/Steve?tab=1"
		}) {
			TestServer.Response response = server.get(path);
			assertEquals(200, response.status(), path);
			assertTrue(response.text().contains("fallback page"), path);
		}
	}

	/**
	 * What every kind of path gets. Below the top, every path that isn't a file is a page; the web UI then asks the API
	 * whether that player or ID exists. "changed" rows differ from before (prerendered pages, dots in names and IDs).
	 */
	@ParameterizedTest(name = "{0} -> {1} {2}")
	@CsvSource({
		"/,                                      200, index page",
		"/about,                                 200, about page",    // changed: was the fallback
		"/about.html,                            200, about page",
		"/missing.html,                          200, fallback page",
		"/players/Steve,                         200, fallback page",
		"/players/Steve/advancements,            200, fallback page",
		"/players/Steve?tab=1,                   200, fallback page",
		"/players/.Steve,                        200, fallback page", // changed: was 404
		"/players/.Steve/advancements,           200, fallback page", // changed: was 404
		"/players/%2BSteve,                      200, fallback page",
		"/players/..Steve,                       200, fallback page", // changed: was 400
		"/players/Steve..,                       200, fallback page", // changed: was 400
		"/players/..,                            400, ''",
		"/players/%2E%2E,                        400, ''",
		"/advancements/mymod/some.thing,         200, fallback page", // changed: was 404
		"/leaderboards/mined/minecraft%3Astone,  200, fallback page",
		"/robots.txt,                            200, User-agent",
		"/mc/item/sample.png,                   200, PNG",
		"/players/Steve.png,                     200, fallback page", // changed: was 404
		"/players/Steve.backup,                  200, fallback page", // changed: was 404
		"/advancements/mymod/data.json,          200, fallback page", // changed: was 404
		"/favicon.ico,                           404, ''",
		"/apple-touch-icon.png,                  404, ''",
		"/.Steve,                                200, fallback page", // changed: was 404
		"/mc/item/missing.png,                   404, ''",
		"/_app/immutable/missing.js,             404, ''",
		"/_app/missing,                          404, ''",            // changed: was the fallback
	})
	void whatEachPathGets(String path, int status, String marker) throws IOException {
		TestServer.Response response = server.get(path);
		assertEquals(status, response.status(), path);
		if (!marker.isEmpty()) {
			assertTrue(response.text().contains(marker), path + " -> " + response.text());
		}
	}

	@Test
	void missingFileAtTheTopIsNotFound() throws IOException {
		assertEquals(404, server.get("/favicon.ico").status());
	}

	@Test
	void playerNamesWithDotsGetFallbackPage() throws IOException {
		// Geyser/Floodgate puts a "." in front of Bedrock players' names.
		TestServer.Response response = server.get("/players/.BedrockSteve");
		assertEquals(200, response.status());
		assertTrue(response.text().contains("fallback page"));
	}

	@Test
	void gameFilesWithoutStampAreCheckedAgain() throws IOException {
		TestServer.Response response = server.get("/mc/item/sample.png");
		assertEquals(200, response.status());
		assertEquals("image/png", response.header("Content-Type"));
		assertArrayEquals(TestServer.resource("/assets/stats_webui_plus/web/mc/item/sample.png"), response.body());
		assertEquals("no-cache", response.header("Cache-Control"));
	}

	@Test
	void gameFilesWithAnotherStampAreServedButNotKept() throws IOException {
		// No game files are ready in this test, so every stamp is an old one: today's file, not kept for good.
		TestServer.Response response = server.get("/mc/26.1-3/item/sample.png");
		assertEquals(200, response.status());
		assertArrayEquals(TestServer.resource("/assets/stats_webui_plus/web/mc/item/sample.png"), response.body());
		assertEquals("no-cache", response.header("Cache-Control"));
	}

	@Test
	void appVersionIsCheckedAgain() throws IOException {
		assertEquals("no-cache", server.get("/_app/version.json").header("Cache-Control"));
	}

	@Test
	void hashedAppFilesAreImmutable() throws IOException {
		TestServer.Response response = server.get("/_app/immutable/app.abc123.js");
		assertEquals(200, response.status());
		assertTrue(response.header("Content-Type").contains("javascript"), response.header("Content-Type"));
		assertTrue(response.header("Cache-Control").contains("immutable"), response.header("Cache-Control"));
	}

	@Test
	void pagesAreNotCachedForever() throws IOException {
		String cacheControl = server.get("/").header("Cache-Control");
		assertTrue(cacheControl == null || !cacheControl.contains("immutable"), cacheControl);
	}

	@ParameterizedTest
	@ValueSource(strings = {"/", "/about", "/mc/item/sample.png", "/_app/immutable/app.abc123.js", "/players/Steve"})
	void revalidationGetsEmpty304(String path) throws IOException {
		TestServer.Response first = server.get(path);
		String etag = first.header("ETag");
		assertNotNull(etag, path);

		TestServer.Response second = server.get(path, "If-None-Match", etag);
		assertEquals(304, second.status(), path);
		assertEquals(0, second.body().length, path);

		TestServer.Response changed = server.get(path, "If-None-Match", "\"something-else\"");
		assertEquals(200, changed.status(), path);
	}

	@Test
	void sameFileKeepsSameEtag() throws IOException {
		assertEquals(server.get("/about").header("ETag"), server.get("/about").header("ETag"));
		assertNotEquals(server.get("/about").header("ETag"), server.get("/").header("ETag"));
	}

	@Test
	void missingAssetIsNotFoundInsteadOfHtml() throws IOException {
		TestServer.Response response = server.get("/_app/immutable/missing.js");
		assertEquals(404, response.status());
		assertFalse(response.text().contains("fallback page"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/../secret.txt",
		"/mc/../../secret.txt",
		"/%2e%2e/secret.txt",
		"/..%2fsecret.txt",
		"/%2e%2e%2fsecret.txt",
		"/mc/..%2f..%2fsecret.txt",
		"/..\\secret.txt",
		"/mc/%2e%2e/%2e%2e/secret.txt",
	})
	void cannotEscapeWebRoot(String path) throws IOException {
		TestServer.Response response = server.get(path);
		assertFalse(response.text().contains("SECRET-OUTSIDE-WEB-ROOT"), path + " -> " + response.status());
	}

	@Test
	void onlyGetAndHead() throws IOException {
		assertEquals(405, server.request("POST", "/").status());
		TestServer.Response head = server.request("HEAD", "/about");
		assertEquals(200, head.status());
		assertEquals(0, head.body().length);
		assertNotNull(head.header("ETag"));
	}

	@Test
	void textIsGzippedImagesNot() throws IOException {
		TestServer.Response page = server.get("/big", "Accept-Encoding", "gzip");
		assertEquals(200, page.status());
		assertEquals("gzip", page.header("Content-Encoding"));
		assertEquals("Accept-Encoding", page.header("Vary"));
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(page.body()))) {
			assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("big page"));
		}
		assertEquals(server.get("/big").header("ETag"), page.header("ETag"), "the ETag of the uncompressed file");

		TestServer.Response image = server.get("/mc/item/sample.png", "Accept-Encoding", "gzip");
		assertNull(image.header("Content-Encoding"));
		assertNull(image.header("Vary"));
		// Small text stays as it is, but still says it could be compressed.
		TestServer.Response small = server.get("/robots.txt", "Accept-Encoding", "gzip");
		assertNull(small.header("Content-Encoding"));
		assertEquals("Accept-Encoding", small.header("Vary"));
		assertEquals("text/plain; charset=utf-8", small.header("Content-Type"));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
		"/about,                         text/html; charset=utf-8",
		"/_app/immutable/app.abc123.js,  text/javascript; charset=utf-8",
		"/_app/version.json,             application/json; charset=utf-8",
		"/mc/item/sample.png,            image/png",
	})
	void contentTypes(String path, String type) throws IOException {
		assertEquals(type, server.get(path).header("Content-Type"));
	}

	/** README, Link previews: every page carries them before its head ends, with the address it was asked for. */
	@Test
	void pagesHaveLinkPreviews() throws IOException {
		String html = server.get("/stamped").text();
		assertTrue(html.contains("<meta property=\"og:title\" content=\"My Server\" />"), html);
		assertTrue(html.contains("<meta property=\"og:url\" content=\"http://localhost/stamped\" />"), html);
		assertTrue(html.indexOf("og:title") < html.indexOf("</head>"), html);
		// A page without a head (the other test pages) is sent as it is.
		assertEquals("<!doctype html><title>index page</title>", server.get("/").text().trim());
	}

	/** With the game's files ready: their stamp in every page, and stamped addresses kept for good. */
	@Test
	void stampOfTheGameFiles(@TempDir Path cache) throws IOException {
		Path mc = GameAssetsFixture.ready(cache, "26.3");
		try {
			Files.createDirectories(mc.resolve("item"));
			Files.writeString(mc.resolve("item/apple.png"), "apple");
			String stamp = GameAssets.get().stamp();
			assertNotNull(stamp);

			TestServer.Response page = server.get("/stamped");
			assertTrue(page.text().contains("<meta name=\"mc-files\" content=\"" + stamp + "\" />"), page.text());
			assertEquals(HttpJson.etag(page.body()), page.header("ETag"), "the ETag of the changed page");
			assertEquals(304, server.get("/stamped", "If-None-Match", page.header("ETag")).status());

			TestServer.Response current = server.get("/mc/" + stamp + "/item/apple.png");
			assertEquals(200, current.status());
			assertEquals("apple", current.text());
			assertEquals("public, max-age=31536000, immutable", current.header("Cache-Control"));
			TestServer.Response old = server.get("/mc/26.1-3/item/apple.png");
			assertEquals("apple", old.text());
			assertEquals("no-cache", old.header("Cache-Control"));
			assertEquals("no-cache", server.get("/mc/item/apple.png").header("Cache-Control"));
		} finally {
			GameAssetsFixture.off(cache);
		}
		assertFalse(server.get("/stamped").text().contains("mc-files"), "no stamp without the game's files");
		assertEquals(404, server.get("/mc/item/apple.png").status(), "nothing kept from the files that are gone");
	}
}
