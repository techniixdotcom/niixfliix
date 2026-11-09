package app.niixfliix.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;

/**
 * Watched state of a whole series or one season. Marking as watched only marks episodes that are out, so a
 * new episode still shows up as new when it comes out; marking as unwatched clears every episode.
 */
public final class SeriesWatched {

	private SeriesWatched() {
	}

	/** Ids of the episodes that are out (or have no date): of {@code season}, or of every season but specials. */
	@NonNull
	public static List<String> out(@NonNull Meta series, @Nullable Integer season, long nowMs) {
		List<String> ids = new ArrayList<>();
		for (Video v : all(series, season)) {
			long released = NewEpisodes.releasedMs(v.released);
			if (released <= 0 || released <= nowMs) {
				ids.add(v.id);
			}
		}
		return ids;
	}

	public static boolean isWatched(@NonNull History history, @NonNull Meta series, @Nullable Integer season,
			long nowMs) {
		List<String> ids = out(series, season, nowMs);
		if (ids.isEmpty()) {
			return false;
		}
		for (String id : ids) {
			if (!history.isWatched(id)) {
				return false;
			}
		}
		return true;
	}

	/** Marks as watched, or as unwatched when it all was; returns whether it is now watched. */
	public static boolean toggle(@NonNull History history, @NonNull Meta series, @Nullable Integer season,
			long nowMs) {
		boolean mark = !isWatched(history, series, season, nowMs);
		set(history, series, season, mark, nowMs);
		return mark;
	}

	public static void set(@NonNull History history, @NonNull Meta series, @Nullable Integer season,
			boolean watched, long nowMs) {
		if (watched) {
			history.setWatched(out(series, season, nowMs), true);
			return;
		}
		List<String> ids = new ArrayList<>();
		for (Video v : all(series, season)) {
			ids.add(v.id);
		}
		history.setWatched(ids, false);
	}

	private static List<Video> all(Meta series, @Nullable Integer season) {
		List<Video> out = new ArrayList<>();
		for (Map.Entry<Integer, List<Video>> e : series.seasons().entrySet()) {
			int number = e.getKey();
			if (season != null ? number != season : number == 0) {
				continue;
			}
			for (Video v : e.getValue()) {
				if (v.id != null) {
					out.add(v);
				}
			}
		}
		return out;
	}
}
