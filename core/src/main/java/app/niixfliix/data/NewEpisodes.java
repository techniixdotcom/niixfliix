package app.niixfliix.data;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.data.model.EpisodeList;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.util.MainQueue;

public final class NewEpisodes {

	private static final long REFRESH_MS = 6L * 60 * 60 * 1000;
	private static final long RETRY_MS = 15L * 60 * 1000;
	@VisibleForTesting
	static final long RECENT_MS = 14L * 24 * 60 * 60 * 1000;
	private static final String KEY = "series_episodes";
	private static final long SAVE_DELAY_MS = 1000;

	private final KeyValueStore store;
	private final Gson gson;
	private final ExecutorService io;
	private final MainQueue main;
	private final History history;
	private final Runnable save = this::save;
	private final Map<String, EpisodeList> series;
	private final Map<String, Long> failedAt = new HashMap<>();
	private boolean refreshing;

	public NewEpisodes(@NonNull KeyValueStore store, @NonNull Gson gson, @NonNull ExecutorService io,
			@NonNull MainQueue main, @NonNull History history) {
		this.store = store;
		this.gson = gson;
		this.io = io;
		this.main = main;
		this.history = history;
		series = read();
	}

	@MainThread
	public long latestOutMs(@NonNull String id) {
		EpisodeList s = series.get(id);
		if (s == null) {
			return 0;
		}
		long now = System.currentTimeMillis();
		long latest = 0;
		for (EpisodeList.Episode e : s.episodes) {
			if (e.releasedMs > latest && e.releasedMs <= now) {
				latest = e.releasedMs;
			}
		}
		return latest;
	}

	@MainThread
	public boolean hasNew(@NonNull String id) {
		EpisodeList s = series.get(id);
		if (s == null) {
			return false;
		}
		return hasNew(s.episodes, history::isWatched, videoId -> {
			HistoryEntry e = history.get(videoId);
			return e != null ? e.updatedAt : 0;
		}, System.currentTimeMillis());
	}

	@MainThread
	public void refresh(@NonNull Collection<String> ids, @NonNull Function<String, Meta> fetch,
			@NonNull Runnable onChanged) {
		if (series.keySet().retainAll(ids)) {
			scheduleSave();
		}
		if (refreshing) {
			return;
		}
		long now = System.currentTimeMillis();
		List<String> stale = new ArrayList<>();
		for (String id : ids) {
			EpisodeList s = series.get(id);
			Long failed = failedAt.get(id);
			if ((s == null || now - s.fetchedAt > REFRESH_MS) && (failed == null || now - failed > RETRY_MS)) {
				stale.add(id);
			}
		}
		if (stale.isEmpty()) {
			return;
		}
		refreshing = true;
		io.execute(() -> {
			Map<String, EpisodeList> fetched = new HashMap<>();
			for (String id : stale) {
				EpisodeList s = fetchOne(id, fetch);
				if (s != null) {
					fetched.put(id, s);
				}
			}
			long done = System.currentTimeMillis();
			main.post(() -> {
				refreshing = false;
				for (String id : stale) {
					if (fetched.containsKey(id)) {
						failedAt.remove(id);
					} else {
						failedAt.put(id, done);
					}
				}
				if (!fetched.isEmpty()) {
					series.putAll(fetched);
					scheduleSave();
					onChanged.run();
				}
			});
		});
	}

	@WorkerThread
	@Nullable
	private static EpisodeList fetchOne(String id, Function<String, Meta> fetch) {
		Meta meta;
		try {
			meta = fetch.apply(id);
		} catch (RuntimeException e) {
			return null;
		}
		if (meta == null || meta.videos == null) {
			return null;
		}
		EpisodeList s = new EpisodeList();
		s.fetchedAt = System.currentTimeMillis();
		s.episodes = episodes(meta.videos);
		return s;
	}

	@VisibleForTesting
	@NonNull
	static List<EpisodeList.Episode> episodes(@NonNull List<Video> videos) {
		List<EpisodeList.Episode> out = new ArrayList<>();
		for (Video v : videos) {
			if (v.id == null || v.seasonOrZero() <= 0) {
				continue;
			}
			out.add(new EpisodeList.Episode(v.id, v.seasonOrZero(), v.episodeOrZero(), releasedMs(v.released)));
		}
		out.sort((a, b) -> a.season != b.season ? Integer.compare(a.season, b.season)
				: Integer.compare(a.episode, b.episode));
		return out;
	}

	public static long releasedMs(@Nullable String released) {
		if (released == null || released.isEmpty()) {
			return 0;
		}
		try {
			return Instant.parse(released).toEpochMilli();
		} catch (DateTimeParseException e) {
			return 0;
		}
	}

	@VisibleForTesting
	static boolean hasNew(@NonNull List<EpisodeList.Episode> episodes, @NonNull Predicate<String> watched,
			@NonNull ToLongFunction<String> watchedAt, long now) {
		int last = -1;
		for (int i = episodes.size() - 1; i >= 0; i--) {
			if (watched.test(episodes.get(i).videoId)) {
				last = i;
				break;
			}
		}
		if (last < 0) {
			return false;
		}
		for (int i = 0; i < last; i++) {
			EpisodeList.Episode e = episodes.get(i);
			if (isOut(e, now) && !watched.test(e.videoId)) {
				return false;
			}
		}
		EpisodeList.Episode next = null;
		for (int i = last + 1; i < episodes.size(); i++) {
			if (isOut(episodes.get(i), now)) {
				next = episodes.get(i);
				break;
			}
		}
		if (next == null) {
			return false;
		}
		long seen = watchedAt.applyAsLong(episodes.get(last).videoId);
		return seen > 0 ? next.releasedMs > seen : now - next.releasedMs <= RECENT_MS;
	}

	private static boolean isOut(EpisodeList.Episode e, long now) {
		return e.releasedMs > 0 && e.releasedMs <= now;
	}

	private void scheduleSave() {
		main.removeCallbacks(save);
		main.postDelayed(save, SAVE_DELAY_MS);
	}

	private void save() {
		store.putString(KEY, gson.toJson(series));
	}

	private Map<String, EpisodeList> read() {
		String json = store.getString(KEY, null);
		if (json != null) {
			try {
				Map<String, EpisodeList> saved = gson.fromJson(json, new TypeToken<HashMap<String, EpisodeList>>() { }.getType());
				if (saved != null) {
					saved.values().removeIf(s -> s == null || s.episodes == null);
					return saved;
				}
			} catch (JsonParseException e) {
			}
		}
		return new HashMap<>();
	}
}
