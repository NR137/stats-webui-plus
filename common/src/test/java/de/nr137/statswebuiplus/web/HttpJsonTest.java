package de.nr137.statswebuiplus.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every handler answers through HttpJson.respond. A visitor who goes away mid-response (a closed tab, the web server
 * stopping with a singleplayer world) is no error of the mod; a second response to one request is. JSON answers
 * carry an ETag (an empty 304 when the browser has them) and come gzipped when they are big enough.
 */
class HttpJsonTest {
	@Test
	void aVisitorWhoLeavesMidResponseIsAConnectionLostNotAnError() throws Exception {
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		// Far more than the socket buffers hold, so the write is still going when the client is gone.
		byte[] big = new byte[32 * 1024 * 1024];
		CountDownLatch started = new CountDownLatch(1);
		try (TestServer server = new TestServer(exchange -> {
			started.countDown();
			try {
				HttpJson.respond(exchange, 200, big);
			} catch (Throwable e) {
				thrown.set(e);
			} finally {
				exchange.close();
				done.countDown();
			}
		})) {
			try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
				OutputStream out = socket.getOutputStream();
				out.write("GET / HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
				out.flush();
				assertTrue(started.await(10, TimeUnit.SECONDS));
				// Closing with unread data resets the connection, as a browser closing the tab does.
				socket.setSoLinger(true, 0);
			}
			assertTrue(done.await(20, TimeUnit.SECONDS), "the response finished");
		}
		assertInstanceOf(HttpJson.ConnectionLost.class, thrown.get(), String.valueOf(thrown.get()));
	}

	@Test
	void aSecondResponseToOneRequestIsAnErrorNotAConnectionLost() throws Exception {
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		try (TestServer server = new TestServer(exchange -> {
			HttpJson.send(exchange, 200, "first");
			try {
				HttpJson.error(exchange, 500, "internal_error", "second");
			} catch (Throwable e) {
				thrown.set(e);
			}
			exchange.close();
		})) {
			TestServer.Response response = server.get("/");
			assertEquals(200, response.status());
			assertEquals("\"first\"", response.text());
		}
		assertInstanceOf(IllegalStateException.class, thrown.get(), String.valueOf(thrown.get()));
	}

	@Test
	void headGetsTheStatusWithoutABody() throws Exception {
		try (TestServer server = new TestServer(exchange -> {
			HttpJson.send(exchange, 200, "body");
			exchange.close();
		})) {
			TestServer.Response response = server.request("HEAD", "/");
			assertEquals(200, response.status());
			assertEquals(0, response.body().length);
		}
	}

	/** JSON of about this many bytes: a string of letters. */
	private static String text(int length) {
		return "x".repeat(length);
	}

	private static byte[] gunzip(byte[] body) throws java.io.IOException {
		try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
			return in.readAllBytes();
		}
	}

	@Test
	void bigJsonIsGzippedForBrowsersThatTakeIt() throws Exception {
		String payload = text(5000);
		try (TestServer server = new TestServer(exchange -> {
			HttpJson.send(exchange, 200, payload);
			exchange.close();
		})) {
			TestServer.Response gzipped = server.get("/", "Accept-Encoding", "gzip, deflate, br");
			assertEquals("gzip", gzipped.header("Content-Encoding"));
			assertEquals("Accept-Encoding", gzipped.header("Vary"));
			assertTrue(gzipped.body().length < 1000, "compressed: " + gzipped.body().length);
			assertEquals(HttpJson.toJson(payload), new String(gunzip(gzipped.body()), StandardCharsets.UTF_8));

			TestServer.Response plain = server.get("/");
			assertNull(plain.header("Content-Encoding"));
			assertEquals("Accept-Encoding", plain.header("Vary"));
			assertEquals(HttpJson.toJson(payload), plain.text());
			// One ETag for both: it is the uncompressed body's.
			assertEquals(plain.header("ETag"), gzipped.header("ETag"));
		}
	}

	@Test
	void smallJsonIsSentAsItIs() throws Exception {
		try (TestServer server = new TestServer(exchange -> {
			HttpJson.send(exchange, 200, text(100));
			exchange.close();
		})) {
			TestServer.Response response = server.get("/", "Accept-Encoding", "gzip");
			assertNull(response.header("Content-Encoding"));
			assertEquals("Accept-Encoding", response.header("Vary"));
			assertEquals("application/json; charset=utf-8", response.header("Content-Type"));
		}
	}

	@Test
	void imagesAreNeverCompressed() throws Exception {
		byte[] image = new byte[10_000];
		try (TestServer server = new TestServer(exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "image/png");
			HttpJson.respond(exchange, 200, image);
			exchange.close();
		})) {
			TestServer.Response response = server.get("/", "Accept-Encoding", "gzip");
			assertNull(response.header("Content-Encoding"));
			assertNull(response.header("Vary"));
			assertEquals(image.length, response.body().length);
		}
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource(delimiter = '|', value = {
		"gzip                    | true",
		"deflate, gzip;q=0.5     | true",
		"GZIP                    | true",
		"*                       | true",
		"gzip;q=0                | false",
		"gzip;q=0, *             | false",
		"*;q=0                   | false",
		"deflate, br             | false",
		"identity                | false",
	})
	void acceptEncoding(String header, boolean gzip) {
		assertEquals(gzip, HttpJson.acceptsGzip(header));
	}

	@Test
	void everyJsonAnswerHasAnEtagAndAnEmpty304() throws Exception {
		try (TestServer server = new TestServer(exchange -> {
			HttpJson.send(exchange, 200, Map.of("players", 3), "public, max-age=10");
			exchange.close();
		})) {
			TestServer.Response first = server.get("/");
			String etag = first.header("ETag");
			assertNotNull(etag);
			assertTrue(etag.matches("\"[0-9a-f]+-[0-9a-f]+\""), etag);
			assertEquals("public, max-age=10", first.header("Cache-Control"));

			TestServer.Response again = server.get("/", "If-None-Match", "\"other\", W/" + etag);
			assertEquals(304, again.status());
			assertEquals(0, again.body().length);
			assertEquals(etag, again.header("ETag"));
			assertEquals("public, max-age=10", again.header("Cache-Control"));

			assertEquals(304, server.get("/", "If-None-Match", "*").status());
			assertEquals(200, server.get("/", "If-None-Match", "\"other\"").status());
		}
	}

	@Test
	void etagIsChecksumAndLength() {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(body);
		assertEquals("\"" + Long.toHexString(crc.getValue()) + "-2\"", HttpJson.etag(body));
		assertNotEquals(HttpJson.etag(body), HttpJson.etag("[]".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void errorsAreJsonAndNeverCached() throws Exception {
		try (TestServer server = new TestServer(exchange -> {
			HttpJson.error(exchange, 404, "not_found", "Player not found");
			exchange.close();
		})) {
			TestServer.Response response = server.get("/");
			assertEquals(404, response.status());
			assertEquals("no-store", response.header("Cache-Control"));
			assertNull(response.header("ETag"));
			assertEquals("{\"error\":\"not_found\",\"message\":\"Player not found\"}", response.text());
		}
	}

	@Test
	void namesAreNotHtmlEscapedAndNullsAreKept() {
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("name", "<Steve & Alex>");
		body.put("lastLogout", null);
		assertEquals("{\"name\":\"<Steve & Alex>\",\"lastLogout\":null}", HttpJson.toJson(body));
	}
}
