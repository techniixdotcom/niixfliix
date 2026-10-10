package app.niixfliix.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.data.model.LibraryItem;

public final class Library {

	/** told about the user's own saves and removals, so a signed-in account can follow */
	public interface Changes {
		void onSaved(@NonNull LibraryItem item);

		void onRemoved(@NonNull LibraryItem item);
	}

	private static final String ITEMS = "library_items";

	private final KeyValueStore store;
	private final Gson gson;
	private final List<LibraryItem> items = new ArrayList<>();
	@Nullable private Changes changes;

	public Library(@NonNull KeyValueStore store, @NonNull Gson gson) {
		this.store = store;
		this.gson = gson;
		String json = store.getString(ITEMS, null);
		if (json != null) {
			try {
				List<LibraryItem> saved = gson.fromJson(json, new TypeToken<List<LibraryItem>>() { }.getType());
				if (saved != null) {
					for (LibraryItem item : saved) {
						if (item != null && !item.id.isEmpty()) {
							items.add(item);
						}
					}
				}
			} catch (JsonParseException ignored) {
				// corrupt list, start empty
			}
		}
	}

	public synchronized boolean contains(@NonNull String id) {
		for (LibraryItem item : items) {
			if (item.id.equals(id)) {
				return true;
			}
		}
		return false;
	}

	public synchronized void setChanges(@Nullable Changes changes) {
		this.changes = changes;
	}

	public synchronized void add(@NonNull LibraryItem item) {
		if (contains(item.id)) {
			return;
		}
		item.addedAt = System.currentTimeMillis();
		items.add(0, item);
		persist();
		if (changes != null) {
			changes.onSaved(item);
		}
	}

	public synchronized void remove(@NonNull String id) {
		LibraryItem removed = null;
		for (LibraryItem item : items) {
			if (item.id.equals(id)) {
				removed = item;
				break;
			}
		}
		if (removed == null) {
			return;
		}
		items.remove(removed);
		persist();
		if (changes != null) {
			changes.onRemoved(removed);
		}
	}

	/** titles saved in the account but not here yet; nothing is echoed back */
	public synchronized void merge(@NonNull List<LibraryItem> fromAccount) {
		boolean changed = false;
		for (LibraryItem item : fromAccount) {
			if (!item.id.isEmpty() && !contains(item.id)) {
				items.add(item);
				changed = true;
			}
		}
		if (changed) {
			items.sort((a, b) -> Long.compare(b.addedAt, a.addedAt));
			persist();
		}
	}

	@NonNull
	public synchronized List<LibraryItem> all() {
		return new ArrayList<>(items);
	}

	private void persist() {
		store.putString(ITEMS, gson.toJson(items));
	}
}
