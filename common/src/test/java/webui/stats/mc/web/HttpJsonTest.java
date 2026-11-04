package webui.stats.mc.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Every handler answers through HttpJson.respond. A visitor who goes away mid-response (a closed tab, the web server
 * stopping with a singleplayer world) is no error of the mod; a second response to one request is.
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
}
