package app.niixfliix.data;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.niixfliix.app.Constant;

public final class SearchHistory {

	private static final String QUERIES = "search_history";

	private final KeyValueStore store;
	private final Gson gson;
	private final List<String> queries = new ArrayList<>();

	public SearchHistory(@NonNull KeyValueStore store, @NonNull Gson gson) {
		this.store = store;
		this.gson = gson;
		String json = store.getString(QUERIES, null);
		if (json != null) {
			try {
				List<String> saved = gson.fromJson(json, new TypeToken<List<String>>() { }.getType());
				if (saved != null) {
					queries.addAll(saved);
				}
			} catch (JsonParseException ignored) {
				// corrupt list, start empty
			}
		}
	}

	public synchronized void add(@NonNull String query) {
		String clean = query.trim();
		if (clean.isEmpty()) {
			return;
		}
		String lower = clean.toLowerCase(Locale.ROOT);
		queries.removeIf(q -> q.toLowerCase(Locale.ROOT).equals(lower));
		queries.add(0, clean);
		while (queries.size() > Constant.RECENT_SEARCHES) {
			queries.remove(queries.size() - 1);
		}
		persist();
	}

	@NonNull
	public synchronized List<String> all() {
		return new ArrayList<>(queries);
	}

	public synchronized void clear() {
		queries.clear();
		persist();
	}

	private void persist() {
		store.putString(QUERIES, gson.toJson(queries));
	}
}
