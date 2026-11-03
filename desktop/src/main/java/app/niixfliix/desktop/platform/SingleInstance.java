package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import app.niixfliix.util.Logs;

public final class SingleInstance {

	private static final String TAG = "SingleInstance";
	private static final int MAX_MESSAGE = 16 * 1024;
	private static final String SEPARATOR = "\n";

	private final File socket;
	private ServerSocketChannel server;

	public SingleInstance(@NonNull File dir) {
		socket = new File(dir, "instance.sock");
	}

	public boolean claim(@NonNull String[] args) {
		if (handOver(args)) {
			return false;
		}
		try {
			Files.deleteIfExists(socket.toPath());
			server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
			server.bind(UnixDomainSocketAddress.of(socket.toPath()));
			return true;
		} catch (IOException e) {
			Logs.w(TAG, "Could not listen for other instances", e);
			return !handOver(args);
		}
	}

	public void listen(@NonNull Consumer<List<String>> onArgs) {
		if (server == null) {
			return;
		}
		Thread t = new Thread(() -> {
			while (server.isOpen()) {
				try (SocketChannel client = server.accept()) {
					onArgs.accept(read(client));
				} catch (IOException e) {
					if (server.isOpen()) {
						Logs.w(TAG, "Bad message from another instance", e);
					}
				}
			}
		}, "single-instance");
		t.setDaemon(true);
		t.start();
	}

	public void close() {
		try {
			if (server != null) {
				server.close();
			}
			Files.deleteIfExists(socket.toPath());
		} catch (IOException e) {
			Logs.w(TAG, "Could not remove the instance socket", e);
		}
	}

	private boolean handOver(String[] args) {
		if (!socket.exists()) {
			return false;
		}
		try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			channel.connect(UnixDomainSocketAddress.of(socket.toPath()));
			ByteBuffer out = ByteBuffer.wrap(String.join(SEPARATOR, args).getBytes(StandardCharsets.UTF_8));
			if (out.remaining() > MAX_MESSAGE) {
				return true;
			}
			while (out.hasRemaining()) {
				channel.write(out);
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static List<String> read(SocketChannel client) throws IOException {
		ByteBuffer in = ByteBuffer.allocate(MAX_MESSAGE);
		int n;
		do {
			n = client.read(in);
		} while (n > 0 && in.hasRemaining());
		String text = new String(in.array(), 0, in.position(), StandardCharsets.UTF_8);
		List<String> args = new ArrayList<>();
		for (String part : text.split(SEPARATOR)) {
			if (!part.isEmpty()) {
				args.add(part);
			}
		}
		return args;
	}
}
