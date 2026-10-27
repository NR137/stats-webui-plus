package webui.stats.mc.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** One handler on a local port, and a raw HTTP/1.0 client that sends paths exactly as given (no normalizing). */
final class TestServer implements AutoCloseable {
	private final HttpServer server;

	TestServer(HttpHandler handler) throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", handler);
		server.start();
	}

	Response get(String rawPath, String... headers) throws IOException {
		return request("GET", rawPath, headers);
	}

	Response request(String method, String rawPath, String... headers) throws IOException {
		try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getAddress().getPort())) {
			socket.setSoTimeout(10_000);
			StringBuilder request = new StringBuilder(method + " " + rawPath + " HTTP/1.0\r\nHost: localhost\r\n");
			for (int i = 0; i + 1 < headers.length; i += 2) {
				request.append(headers[i]).append(": ").append(headers[i + 1]).append("\r\n");
			}
			request.append("\r\n");
			OutputStream out = socket.getOutputStream();
			out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
			out.flush();
			return Response.parse(socket.getInputStream().readAllBytes());
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}

	record Response(int status, Map<String, String> headers, byte[] body) {
		String header(String name) {
			return headers.get(name.toLowerCase(Locale.ROOT));
		}

		String text() {
			return new String(body, StandardCharsets.UTF_8);
		}

		static Response parse(byte[] raw) {
			int split = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
			String head = new String(raw, 0, split < 0 ? raw.length : split, StandardCharsets.ISO_8859_1);
			String[] lines = head.split("\r\n");
			int status = Integer.parseInt(lines[0].split(" ")[1]);
			Map<String, String> headers = new LinkedHashMap<>();
			for (int i = 1; i < lines.length; i++) {
				int colon = lines[i].indexOf(':');
				if (colon > 0) {
					headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
				}
			}
			byte[] body = new byte[0];
			if (split >= 0) {
				ByteArrayOutputStream rest = new ByteArrayOutputStream();
				rest.write(raw, split + 4, raw.length - split - 4);
				body = rest.toByteArray();
			}
			return new Response(status, headers, body);
		}

		private static int indexOf(byte[] data, byte[] pattern) {
			outer:
			for (int i = 0; i + pattern.length <= data.length; i++) {
				for (int j = 0; j < pattern.length; j++) {
					if (data[i + j] != pattern[j]) {
						continue outer;
					}
				}
				return i;
			}
			return -1;
		}
	}

	static byte[] resource(String path) throws IOException {
		try (InputStream in = TestServer.class.getResourceAsStream(path)) {
			return in.readAllBytes();
		}
	}
}
