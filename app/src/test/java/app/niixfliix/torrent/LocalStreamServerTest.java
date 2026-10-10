package app.niixfliix.torrent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class LocalStreamServerTest {

	@Test
	public void ranges() {
		assertArrayEquals(new long[] {0, 99}, LocalStreamServer.parseRange(null, 100));
		assertArrayEquals(new long[] {10, 99}, LocalStreamServer.parseRange("bytes=10-", 100));
		assertArrayEquals(new long[] {10, 20}, LocalStreamServer.parseRange("bytes=10-20", 100));
		assertArrayEquals(new long[] {90, 99}, LocalStreamServer.parseRange("bytes=-10", 100));
		assertArrayEquals(new long[] {50, 99}, LocalStreamServer.parseRange("bytes=50-5000", 100));
		assertNull(LocalStreamServer.parseRange("bytes=100-", 100));
		assertNull(LocalStreamServer.parseRange("bytes=20-10", 100));
		assertNull(LocalStreamServer.parseRange("bytes=0-1,5-6", 100));
		assertNull(LocalStreamServer.parseRange("items=0-1", 100));
	}

	@Test
	public void requestParsing() throws IOException {
		String raw = "GET /abc/video HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=5-\r\n\r\n";
		LocalStreamServer.Request r = LocalStreamServer.readRequest(
				new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
		assertNotNull(r);
		assertEquals("GET", r.method);
		assertEquals("/abc/video", r.path);
		assertEquals("bytes=5-", r.range);
		assertNull(LocalStreamServer.readRequest(new ByteArrayInputStream("nonsense".getBytes(StandardCharsets.US_ASCII))));
	}

	@Test
	public void servesOnlyWithTheTokenAndHonoursRanges() throws Exception {
		byte[] data = new byte[1000];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) i;
		}
		LocalStreamServer server = new LocalStreamServer(new LocalStreamServer.Source() {
			@Override
			public long length() {
				return data.length;
			}

			@Override
			public int read(long position, byte[] buffer, int offset, int length) {
				int n = (int) Math.min(length, data.length - position);
				System.arraycopy(data, (int) position, buffer, offset, n);
				return n;
			}
		}, "movie.mkv");
		try {
			String url = server.url();
			assertTrue(url.startsWith("http://127.0.0.1:"));
			HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection(Proxy.NO_PROXY);
			c.setRequestProperty("Range", "bytes=100-199");
			assertEquals(206, c.getResponseCode());
			assertEquals("video/x-matroska", c.getContentType());
			byte[] body = readAll(c.getInputStream());
			assertEquals(100, body.length);
			assertEquals((byte) 100, body[0]);

			int port = URI.create(url).getPort();
			try (Socket s = new Socket("127.0.0.1", port)) {
				OutputStream out = s.getOutputStream();
				out.write("GET /wrong-token/video HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
				out.flush();
				String reply = new String(readAll(s.getInputStream()), StandardCharsets.US_ASCII);
				assertTrue(reply.startsWith("HTTP/1.1 404"));
			}
		} finally {
			server.stop();
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int n;
		while ((n = in.read(buffer)) != -1) {
			out.write(buffer, 0, n);
		}
		return out.toByteArray();
	}
}
