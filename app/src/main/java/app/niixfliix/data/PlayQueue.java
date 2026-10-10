package app.niixfliix.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.data.model.QueueItem;

/** What plays after the current video, in order. Survives restarts. */
public final class PlayQueue {

	private static final String ITEMS = "play_queue";

	private final KeyValueStore store;
	private final Gson gson;
	private final List<QueueItem> items = new ArrayList<>();

	public PlayQueue(@NonNull KeyValueStore store, @NonNull Gson gson) {
		this.store = store;
		this.gson = gson;
		String json = store.getString(ITEMS, null);
		if (json != null) {
			try {
				List<QueueItem> saved = gson.fromJson(json, new TypeToken<List<QueueItem>>() { }.getType());
				if (saved != null) {
					for (QueueItem item : saved) {
						if (item != null && !item.metaId.isEmpty() && !item.type.isEmpty()) {
							items.add(item);
						}
					}
				}
			} catch (JsonParseException ignored) {
				// corrupt list, start empty
			}
		}
	}

	/** false if it's already queued; "next" moves it to the front either way */
	public synchronized boolean add(@NonNull QueueItem item, boolean next) {
		int existing = indexOf(item.key());
		if (existing >= 0 && !next) {
			return false;
		}
		if (existing >= 0) {
			items.remove(existing);
		}
		if (next) {
			items.add(0, item);
		} else {
			items.add(item);
		}
		persist();
		return existing < 0;
	}

	public synchronized void remove(@NonNull String key) {
		if (items.removeIf(item -> item.key().equals(key))) {
			persist();
		}
	}

	public synchronized void clear() {
		items.clear();
		persist();
	}

	@Nullable
	public synchronized QueueItem peek() {
		return items.isEmpty() ? null : items.get(0);
	}

	@NonNull
	public synchronized List<QueueItem> all() {
		return new ArrayList<>(items);
	}

	private int indexOf(String key) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key().equals(key)) {
				return i;
			}
		}
		return -1;
	}

	private void persist() {
		store.putString(ITEMS, gson.toJson(items));
	}
}
