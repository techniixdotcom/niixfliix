package app.niixfliix.download;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import app.niixfliix.app.Constant;
import app.niixfliix.data.KeyValueStore;
import app.niixfliix.data.SecretStore;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.download.model.DownloadSource;
import app.niixfliix.util.FileNames;
import app.niixfliix.util.Logs;
import app.niixfliix.util.MainQueue;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class Downloads {

	public interface Listener {
		@MainThread
		void onDownloadsChanged();
	}

	public interface Platform {
		void start();

		void deleteFile(@NonNull DownloadItem item);
	}

	public interface Target {
		@NonNull
		OutputStream open(@NonNull DownloadItem item, boolean append) throws IOException;

		void completed(@NonNull DownloadItem item) throws IOException;

		void progress(@NonNull DownloadItem item);
	}

	private static final String TAG = "Downloads";
	private static final String ITEMS = "download_items";
	private static final String SOURCE_PREFIX = "download_source_";
	private static final long PROGRESS_INTERVAL_MS = 750;

	private final KeyValueStore store;
	private final SecretStore secrets;
	private final Gson gson;
	private final MainQueue main;
	private final Platform platform;
	private final List<DownloadItem> items = new ArrayList<>();
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	public Downloads(@NonNull KeyValueStore store, @NonNull SecretStore secrets, @NonNull Gson gson,
			@NonNull MainQueue main, @NonNull Platform platform) {
		this.store = store;
		this.secrets = secrets;
		this.gson = gson;
		this.main = main;
		this.platform = platform;
		String json = store.getString(ITEMS, null);
		if (json != null) {
			try {
				List<DownloadItem> saved = gson.fromJson(json, new TypeToken<List<DownloadItem>>() { }.getType());
				if (saved != null) {
					for (DownloadItem item : saved) {
						if (item == null || item.id.isEmpty()) {
							continue;
						}
						if (item.state == DownloadItem.State.RUNNING || item.state == DownloadItem.State.QUEUED) {
							item.state = DownloadItem.State.PAUSED;
						}
						items.add(item);
					}
				}
			} catch (JsonParseException e) {
				Logs.w(TAG, "Download list was unreadable", e);
			}
		}
	}

	public void addListener(@NonNull Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(@NonNull Listener listener) {
		listeners.remove(listener);
	}

	@NonNull
	public synchronized List<DownloadItem> all() {
		return new ArrayList<>(items);
	}

	public boolean enqueue(@NonNull String url, @NonNull Map<String, String> headers, @NonNull String title,
			@NonNull String fileName) {
		DownloadItem item = new DownloadItem();
		item.id = UUID.randomUUID().toString();
		item.title = title;
		item.fileName = FileNames.sanitize(fileName, Constant.FILE_NAME_MAX_BYTES);
		DownloadSource source = new DownloadSource();
		source.url = url;
		source.headers = headers;
		if (!secrets.put(SOURCE_PREFIX + item.id, gson.toJson(source))) {
			return false;
		}
		synchronized (this) {
			items.add(0, item);
		}
		changed();
		platform.start();
		return true;
	}

	public void pause(@NonNull String id) {
		setState(id, DownloadItem.State.PAUSED);
	}

	public void resume(@NonNull String id) {
		setState(id, DownloadItem.State.QUEUED);
		platform.start();
	}

	public void delete(@NonNull String id) {
		DownloadItem removed = null;
		synchronized (this) {
			for (DownloadItem item : items) {
				if (item.id.equals(id)) {
					removed = item;
					break;
				}
			}
			if (removed != null) {
				items.remove(removed);
			}
		}
		if (removed == null) {
			return;
		}
		secrets.put(SOURCE_PREFIX + id, null);
		if (removed.contentUri != null) {
			platform.deleteFile(removed);
		}
		changed();
	}

	@Nullable
	public synchronized DownloadItem get(@NonNull String id) {
		for (DownloadItem item : items) {
			if (item.id.equals(id)) {
				return item;
			}
		}
		return null;
	}

	@WorkerThread
	public void drain(@NonNull OkHttpClient http, @NonNull Target target) {
		DownloadItem item;
		while ((item = claimNextQueued()) != null) {
			download(http, item, target);
		}
	}

	public void pauseAll() {
		for (DownloadItem item : all()) {
			if (item.isActive()) {
				pause(item.id);
			}
		}
	}

	@Nullable
	private DownloadItem claimNextQueued() {
		DownloadItem next = null;
		synchronized (this) {
			for (int i = items.size() - 1; i >= 0; i--) {
				if (items.get(i).state == DownloadItem.State.QUEUED) {
					next = items.get(i);
					next.state = DownloadItem.State.RUNNING;
					break;
				}
			}
		}
		if (next != null) {
			changed();
		}
		return next;
	}

	@Nullable
	private DownloadSource source(@NonNull String id) {
		String json = secrets.get(SOURCE_PREFIX + id);
		if (json == null) {
			return null;
		}
		try {
			DownloadSource source = gson.fromJson(json, DownloadSource.class);
			if (source != null && source.headers == null) {
				source.headers = Collections.emptyMap();
			}
			return source;
		} catch (JsonParseException e) {
			return null;
		}
	}

	private void changed() {
		synchronized (this) {
			store.putString(ITEMS, gson.toJson(items));
		}
		main.post(() -> {
			for (Listener l : listeners) {
				l.onDownloadsChanged();
			}
		});
	}

	private void setState(String id, DownloadItem.State state) {
		DownloadItem item = get(id);
		if (item != null && item.state != DownloadItem.State.DONE) {
			item.state = state;
			changed();
		}
	}

	private void download(OkHttpClient http, DownloadItem item, Target target) {
		DownloadSource source = source(item.id);
		if (source == null || source.url == null) {
			finish(item, DownloadItem.State.FAILED);
			return;
		}
		Request.Builder request = new Request.Builder().url(source.url);
		for (Map.Entry<String, String> h : source.headers.entrySet()) {
			request.header(h.getKey(), h.getValue());
		}
		if (item.bytesDone > 0) {
			request.header("Range", "bytes=" + item.bytesDone + "-");
		}
		try (Response response = http.newCall(request.build()).execute()) {
			if (response.code() == 416 && item.bytesTotal > 0 && item.bytesDone >= item.bytesTotal) {
				target.completed(item);
				finish(item, DownloadItem.State.DONE);
				return;
			}
			if (!response.isSuccessful()) {
				throw new IOException("HTTP " + response.code());
			}
			boolean resumed = response.code() == 206 && item.bytesDone > 0;
			if (!resumed) {
				item.bytesDone = 0;
			}
			ResponseBody body = response.body();
			long length = body.contentLength();
			item.bytesTotal = length >= 0 ? item.bytesDone + length : -1;
			try (OutputStream out = target.open(item, resumed); InputStream in = body.byteStream()) {
				if (!copy(item, in, out, target)) {
					return;
				}
			}
			target.completed(item);
			finish(item, DownloadItem.State.DONE);
		} catch (IOException | RuntimeException e) {
			Logs.w(TAG, "Download failed", e);
			finish(item, DownloadItem.State.FAILED);
		}
	}

	private boolean copy(DownloadItem item, InputStream in, OutputStream out, Target target) throws IOException {
		byte[] buffer = new byte[64 * 1024];
		long lastUpdate = 0;
		int n;
		while ((n = in.read(buffer)) != -1) {
			out.write(buffer, 0, n);
			item.bytesDone += n;
			long now = System.currentTimeMillis();
			if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
				lastUpdate = now;
				DownloadItem current = get(item.id);
				changed();
				if (current == null || current.state != DownloadItem.State.RUNNING) {
					return false;
				}
				target.progress(item);
			}
		}
		return true;
	}

	private void finish(DownloadItem item, DownloadItem.State state) {
		item.state = state;
		changed();
	}
}
