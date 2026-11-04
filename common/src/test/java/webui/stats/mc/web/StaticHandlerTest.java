package webui.stats.mc.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Serving the SvelteKit build (adapter-static with fallback 200.html, see web/vite.config.ts) and the caching rules in
 * the CHANGELOG. The pages are test fixtures in src/test/resources.
 */
class StaticHandlerTest {
	private static TestServer server;

	@BeforeAll
	static void start() throws IOException {
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
		"/mc/item/diamond.png,                   200, PNG",
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
	void imagesHaveTheirTypeAndMayBeCachedADay() throws IOException {
		TestServer.Response response = server.get("/mc/item/diamond.png");
		assertEquals(200, response.status());
		assertEquals("image/png", response.header("Content-Type"));
		assertArrayEquals(TestServer.resource("/assets/stats_webui_plus/web/mc/item/diamond.png"), response.body());
		assertTrue(response.header("Cache-Control").contains("max-age=86400"), response.header("Cache-Control"));
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
	@ValueSource(strings = {"/", "/about", "/mc/item/diamond.png", "/_app/immutable/app.abc123.js", "/players/Steve"})
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
}
