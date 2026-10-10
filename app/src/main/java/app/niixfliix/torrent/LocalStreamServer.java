package app.niixfliix.torrent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.niixfliix.util.FileNames;

/** Serves one file on 127.0.0.1, random port + random token in the path so other apps can't read it. */
public final class LocalStreamServer {

	/** read() may block until the data is there */
	public interface Source {
		long length();

		int read(long position, @NonNull byte[] buffer, int offset, int length) throws IOException;
	}

	private static final int MAX_HEADER_BYTES = 8 * 1024;
	private static final int MAX_CLIENTS = 16;
	private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");

	private final Source source;
	private final String mimeType;
	private final String path;
	private final ServerSocket server;
	private final ExecutorService clients = Executors.newFixedThreadPool(MAX_CLIENTS);
	private volatile boolean running = true;

	public LocalStreamServer(@NonNull Source source, @NonNull String fileName) throws IOException {
		this.source = source;
		this.mimeType = FileNames.mimeType(fileName, "application/octet-stream");
		this.path = "/" + newToken() + "/video";
		server = new ServerSocket(0, MAX_CLIENTS, InetAddress.getByName("127.0.0.1"));
		Thread accept = new Thread(this::acceptLoop, "stream-server");
		accept.setDaemon(true);
		accept.start();
	}

	@NonNull
	public String url() {
		return "http://127.0.0.1:" + server.getLocalPort() + path;
	}

	public void stop() {
		running = false;
		try {
			server.close();
		} catch (IOException ignored) {
		}
		clients.shutdownNow();
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket socket = server.accept();
				clients.execute(() -> serve(socket));
			} catch (IOException | RejectedExecutionException e) {
				return;
			}
		}
	}

	private void serve(Socket socket) {
		try (Socket s = socket) {
			s.setSoTimeout(30_000);
			Request request = readRequest(s.getInputStream());
			OutputStream out = new BufferedOutputStream(s.getOutputStream(), 64 * 1024);
			if (request == null || !isAllowed(request.path)) {
				writeStatus(out, "404 Not Found", 0, null);
				return;
			}
			if (!"GET".equals(request.method) && !"HEAD".equals(request.method)) {
				writeStatus(out, "405 Method Not Allowed", 0, null);
				return;
			}
			long length = source.length();
			long[] range = parseRange(request.range, length);
			if (range == null) {
				writeStatus(out, "416 Range Not Satisfiable", 0, "Content-Range: bytes */" + length + "\r\n");
				return;
			}
			long start = range[0];
			long end = range[1];
			boolean partial = request.range != null;
			String extra = partial ? "Content-Range: bytes " + start + "-" + end + "/" + length + "\r\n" : "";
			writeStatus(out, partial ? "206 Partial Content" : "200 OK", end - start + 1, extra);
			if ("GET".equals(request.method)) {
				copy(start, end, out);
			}
			out.flush();
		} catch (IOException ignored) {
			// client went away, or the player closed the socket to seek
		}
	}

	private void copy(long start, long end, OutputStream out) throws IOException {
		byte[] buffer = new byte[64 * 1024];
		long position = start;
		while (running && position <= end) {
			int want = (int) Math.min(buffer.length, end - position + 1);
			int n = source.read(position, buffer, 0, want);
			out.write(buffer, 0, n);
			position += n;
		}
	}

	private void writeStatus(OutputStream out, String status, long contentLength, @Nullable String extraHeaders)
			throws IOException {
		StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n")
				.append("Content-Type: ").append(mimeType).append("\r\n")
				.append("Accept-Ranges: bytes\r\n")
				.append("Content-Length: ").append(contentLength).append("\r\n")
				.append("Connection: close\r\n");
		if (extraHeaders != null) {
			head.append(extraHeaders);
		}
		head.append("\r\n");
		out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
		out.flush();
	}

	private boolean isAllowed(String requestPath) {
		// constant time compare
		return MessageDigest.isEqual(requestPath.getBytes(StandardCharsets.US_ASCII),
				path.getBytes(StandardCharsets.US_ASCII));
	}

	static final class Request {
		final String method;
		final String path;
		@Nullable final String range;

		Request(String method, String path, @Nullable String range) {
			this.method = method;
			this.path = path;
			this.range = range;
		}
	}

	@Nullable
	static Request readRequest(@NonNull InputStream in) throws IOException {
		StringBuilder header = new StringBuilder();
		int previous = -1;
		int c;
		while ((c = in.read()) != -1) {
			if (header.length() >= MAX_HEADER_BYTES) {
				return null;
			}
			header.append((char) c);
			if (c == '\n' && previous == '\r' && header.length() >= 4
					&& header.substring(header.length() - 4).equals("\r\n\r\n")) {
				break;
			}
			previous = c;
		}
		if (header.indexOf("\r\n") < 0) {
			return null;
		}
		String[] lines = header.toString().split("\r\n");
		String[] first = lines[0].split(" ");
		if (first.length != 3 || !first[2].startsWith("HTTP/1.")) {
			return null;
		}
		String range = null;
		for (int i = 1; i < lines.length; i++) {
			int colon = lines[i].indexOf(':');
			if (colon > 0 && lines[i].substring(0, colon).trim().equalsIgnoreCase("Range")) {
				range = lines[i].substring(colon + 1).trim();
			}
		}
		return new Request(first[0], first[1], range);
	}

	/** inclusive [start, end], null if unsatisfiable. No header = whole file. */
	@Nullable
	static long[] parseRange(@Nullable String header, long length) {
		if (length <= 0) {
			return null;
		}
		if (header == null) {
			return new long[] {0, length - 1};
		}
		Matcher m = RANGE.matcher(header.trim().toLowerCase(Locale.ROOT));
		if (!m.matches()) {
			return null;
		}
		String from = m.group(1);
		String to = m.group(2);
		try {
			if (from.isEmpty()) {
				if (to.isEmpty()) {
					return null;
				}
				long suffix = Long.parseLong(to);
				if (suffix <= 0) {
					return null;
				}
				return new long[] {Math.max(0, length - suffix), length - 1};
			}
			long start = Long.parseLong(from);
			long end = to.isEmpty() ? length - 1 : Math.min(Long.parseLong(to), length - 1);
			if (start >= length || end < start) {
				return null;
			}
			return new long[] {start, end};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String newToken() {
		byte[] bytes = new byte[24];
		new SecureRandom().nextBytes(bytes);
		StringBuilder hex = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
		}
		return hex.toString();
	}
}
