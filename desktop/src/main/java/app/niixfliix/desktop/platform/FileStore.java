package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.niixfliix.data.KeyValueStore;
import app.niixfliix.util.Logs;

public final class FileStore implements KeyValueStore {

	private static final String TAG = "FileStore";
	private static final long SAVE_DELAY_MS = 300;
	private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "store-writer");
		t.setDaemon(true);
		return t;
	});
	private static final List<FileStore> OPEN = new CopyOnWriteArrayList<>();

	private final File file;
	private final Object writing = new Object();
	private final Map<String, JsonPrimitive> values = new HashMap<>();
	private boolean dirty;
	private boolean scheduled;

	public FileStore(@NonNull File dir, @NonNull String name) {
		file = new File(dir, name + ".json");
		load();
		OPEN.add(this);
	}

	public static void flushAll() {
		for (FileStore store : OPEN) {
			store.flush();
		}
	}

	@Nullable
	@Override
	public synchronized String getString(@NonNull String key, @Nullable String def) {
		JsonPrimitive value = values.get(key);
		return value != null && value.isString() ? value.getAsString() : def;
	}

	@Override
	public void putString(@NonNull String key, @Nullable String value) {
		set(key, value != null ? new JsonPrimitive(value) : null);
	}

	@Override
	public synchronized boolean getBoolean(@NonNull String key, boolean def) {
		JsonPrimitive value = values.get(key);
		return value != null && value.isBoolean() ? value.getAsBoolean() : def;
	}

	@Override
	public void putBoolean(@NonNull String key, boolean value) {
		set(key, new JsonPrimitive(value));
	}

	@Override
	public synchronized int getInt(@NonNull String key, int def) {
		JsonPrimitive value = values.get(key);
		if (value == null || !value.isNumber()) {
			return def;
		}
		try {
			return value.getAsInt();
		} catch (NumberFormatException e) {
			return def;
		}
	}

	@Override
	public void putInt(@NonNull String key, int value) {
		set(key, new JsonPrimitive(value));
	}

	@Override
	public void remove(@NonNull String key) {
		set(key, null);
	}

	private synchronized void set(String key, @Nullable JsonPrimitive value) {
		if (value == null) {
			if (values.remove(key) == null) {
				return;
			}
		} else if (value.equals(values.put(key, value))) {
			return;
		}
		dirty = true;
		if (!scheduled) {
			scheduled = true;
			WRITER.schedule(this::flush, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void flush() {
		synchronized (writing) {
			String json;
			synchronized (this) {
				scheduled = false;
				if (!dirty) {
					return;
				}
				dirty = false;
				JsonObject root = new JsonObject();
				values.forEach(root::add);
				json = new Gson().toJson(root);
			}
			try {
				write(json);
			} catch (IOException e) {
				Logs.w(TAG, "Could not save " + file.getName(), e);
				synchronized (this) {
					dirty = true;
				}
			}
		}
	}

	private void write(String json) throws IOException {
		File dir = file.getParentFile();
		Files.createDirectories(dir.toPath());
		File tmp = new File(dir, file.getName() + ".tmp");
		Files.deleteIfExists(tmp.toPath());
		try (FileChannel channel = FileChannel.open(tmp.toPath(),
				Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
			ByteBuffer bytes = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
			while (bytes.hasRemaining()) {
				channel.write(bytes);
			}
			channel.force(true);
		}
		Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}

	private void load() {
		if (!file.isFile()) {
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				throw new JsonParseException("Not an object");
			}
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					values.put(e.getKey(), e.getValue().getAsJsonPrimitive());
				}
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			Logs.w(TAG, "Unreadable " + file.getName() + ", starting over", e);
			File broken = new File(file.getParentFile(), file.getName() + ".broken");
			if (!file.renameTo(broken)) {
				Logs.w(TAG, "Could not move " + file.getName() + " aside", null);
			}
		}
	}
}
