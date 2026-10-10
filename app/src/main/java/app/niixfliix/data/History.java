package app.niixfliix.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.app.Constant;
import app.niixfliix.data.model.HistoryEntry;

public final class History {

	private static final String ENTRIES = "history_entries";
	private static final String WATCHED = "history_watched";
	private static final long MIN_RESUME_MS = 30_000;

	private final KeyValueStore store;
	private final Gson gson;
	private final Map<String, HistoryEntry> byVideo = new LinkedHashMap<>();
	private final Set<String> watched = new HashSet<>();

	public History(@NonNull KeyValueStore store, @NonNull Gson gson) {
		this.store = store;
		this.gson = gson;
		Type listType = new TypeToken<List<HistoryEntry>>() { }.getType();
		List<HistoryEntry> entries = read(ENTRIES, listType);
		if (entries != null) {
			for (HistoryEntry e : entries) {
				if (e != null && !e.videoId.isEmpty()) {
					byVideo.put(e.videoId, e);
				}
			}
		}
		List<String> ids = read(WATCHED, new TypeToken<List<String>>() { }.getType());
		if (ids != null) {
			watched.addAll(ids);
		}
	}

	@Nullable
	public synchronized HistoryEntry get(@NonNull String videoId) {
		return byVideo.get(videoId);
	}

	/** 0 if there's nothing worth resuming */
	public synchronized long resumePosition(@NonNull String videoId) {
		HistoryEntry e = byVideo.get(videoId);
		if (e == null || watched.contains(videoId) || e.positionMs < MIN_RESUME_MS
				|| e.progress() >= Constant.WATCHED_FRACTION) {
			return 0;
		}
		return e.positionMs;
	}

	public synchronized void save(@NonNull HistoryEntry entry) {
		entry.updatedAt = System.currentTimeMillis();
		byVideo.remove(entry.videoId);
		byVideo.put(entry.videoId, entry);
		if (entry.progress() >= Constant.WATCHED_FRACTION) {
			watched.add(entry.videoId);
		}
		trim();
		persist();
	}

	@NonNull
	public synchronized List<HistoryEntry> recent() {
		Map<String, HistoryEntry> latest = new LinkedHashMap<>();
		for (HistoryEntry e : byVideo.values()) {
			HistoryEntry known = latest.get(e.metaId);
			if (known == null || e.updatedAt > known.updatedAt) {
				latest.put(e.metaId, e);
			}
		}
		List<HistoryEntry> out = new ArrayList<>(latest.values());
		out.sort(Comparator.comparingLong((HistoryEntry e) -> e.updatedAt).reversed());
		return out;
	}

	@NonNull
	public synchronized List<HistoryEntry> continueWatching() {
		List<HistoryEntry> out = new ArrayList<>();
		for (HistoryEntry e : recent()) {
			if (e.positionMs >= MIN_RESUME_MS && !watched.contains(e.videoId)
					&& e.progress() < Constant.WATCHED_FRACTION) {
				out.add(e);
			}
		}
		return out;
	}

	/** Which episode to play: in progress > after last watched > first unwatched > first. */
	@Nullable
	public synchronized Video nextUp(@NonNull Meta m) {
		HistoryEntry latest = null;
		for (Video v : m.videosOrEmpty()) {
			HistoryEntry e = v.id != null ? get(v.id) : null;
			if (e != null && (latest == null || e.updatedAt > latest.updatedAt)) {
				latest = e;
			}
		}
		if (latest != null) {
			if (resumePosition(latest.videoId) > 0) {
				return m.video(latest.videoId);
			}
			Video after = m.nextVideo(latest.videoId);
			if (after != null) {
				return after;
			}
		}
		Video first = null;
		for (List<Video> list : m.seasons().values()) {
			for (Video v : list) {
				if (v.id == null) {
					continue;
				}
				if (first == null) {
					first = v;
				}
				if (!isWatched(v.id) && v.seasonOrZero() > 0) {
					return v;
				}
			}
		}
		// all watched, start over
		return first;
	}

	public synchronized boolean isWatched(@NonNull String videoId) {
		return watched.contains(videoId);
	}

	public synchronized void setWatched(@NonNull String videoId, boolean isWatched) {
		setWatched(Collections.singletonList(videoId), isWatched);
	}

	public synchronized void setWatched(@NonNull Collection<String> videoIds, boolean isWatched) {
		for (String videoId : videoIds) {
			if (isWatched) {
				watched.add(videoId);
			} else {
				watched.remove(videoId);
				HistoryEntry e = byVideo.get(videoId);
				if (e != null && e.progress() >= Constant.WATCHED_FRACTION) {
					e.positionMs = 0;
				}
			}
		}
		persist();
	}

	public synchronized void removeMeta(@NonNull String metaId) {
		byVideo.values().removeIf(e -> e.metaId.equals(metaId));
		persist();
	}

	public synchronized void clear() {
		byVideo.clear();
		watched.clear();
		persist();
	}

	private void trim() {
		while (byVideo.size() > Constant.HISTORY_LIMIT) {
			String oldest = byVideo.keySet().iterator().next();
			byVideo.remove(oldest);
		}
	}

	private void persist() {
		store.putString(ENTRIES, gson.toJson(new ArrayList<>(byVideo.values())));
		store.putString(WATCHED, gson.toJson(new ArrayList<>(watched)));
	}

	@Nullable
	private <T> T read(String key, Type type) {
		String json = store.getString(key, null);
		if (json == null) {
			return null;
		}
		try {
			return gson.fromJson(json, type);
		} catch (JsonParseException e) {
			return null;
		}
	}
}
