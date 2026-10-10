package app.niixfliix.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
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

/** Source URLs can contain a debrid key so they live in SecretStore, not in the list. */
public final class Downloads {

	public interface Listener {
		@MainThread
		void onDownloadsChanged();
	}

	private static final String TAG = "Downloads";
	private static final String ITEMS = "download_items";
	private static final String SOURCE_PREFIX = "download_source_";

	private final Context context;
	private final KeyValueStore store;
	private final SecretStore secrets;
	private final Gson gson;
	private final Handler main = new Handler(Looper.getMainLooper());
	private final List<DownloadItem> items = new ArrayList<>();
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	public Downloads(@NonNull Context context, @NonNull KeyValueStore store, @NonNull SecretStore secrets,
			@NonNull Gson gson) {
		this.context = context;
		this.store = store;
		this.secrets = secrets;
		this.gson = gson;
		String json = store.getString(ITEMS, null);
		if (json != null) {
			try {
				List<DownloadItem> saved = gson.fromJson(json, new TypeToken<List<DownloadItem>>() { }.getType());
				if (saved != null) {
					for (DownloadItem item : saved) {
						if (item == null || item.id.isEmpty()) {
							continue;
						}
						// was running or waiting when the process died
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
		startService();
		return true;
	}

	public void pause(@NonNull String id) {
		setState(id, DownloadItem.State.PAUSED);
	}

	public void resume(@NonNull String id) {
		setState(id, DownloadItem.State.QUEUED);
		startService();
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
		deleteFile(removed);
		changed();
	}

	@Nullable
	synchronized DownloadItem get(@NonNull String id) {
		for (DownloadItem item : items) {
			if (item.id.equals(id)) {
				return item;
			}
		}
		return null;
	}

	/** oldest queued item, already marked RUNNING so a pause or delete can't slip in between */
	@Nullable
	DownloadItem claimNextQueued() {
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
	DownloadSource source(@NonNull String id) {
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

	/** any thread; listeners run on the main thread */
	void changed() {
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

	private void deleteFile(DownloadItem item) {
		if (item.contentUri == null) {
			return;
		}
		Uri uri = Uri.parse(item.contentUri);
		try {
			if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
				//noinspection ResultOfMethodCallIgnored
				new File(uri.getPath()).delete();
			} else {
				context.getContentResolver().delete(uri, null, null);
			}
		} catch (SecurityException | IllegalArgumentException e) {
			Logs.w(TAG, "Could not delete a downloaded file", e);
		}
	}

	private void startService() {
		ContextCompat.startForegroundService(context, new Intent(context, DownloadService.class));
	}
}
