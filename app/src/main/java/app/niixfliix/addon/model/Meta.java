package app.niixfliix.addon.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Meta {

	public static final String TYPE_MOVIE = "movie";
	public static final String TYPE_SERIES = "series";

	@Nullable public String id;
	@Nullable public String type;
	@Nullable public String name;
	@Nullable public String poster;
	@Nullable public String background;
	@Nullable public String logo;
	@Nullable public String description;
	@Nullable public String releaseInfo;
	@Nullable public String runtime;
	@Nullable public String imdbRating;
	@Nullable public List<String> genres;
	@Nullable public List<String> cast;
	@Nullable public List<Video> videos;
	@Nullable public List<Trailer> trailerStreams;
	@Nullable public List<Trailer> trailers;
	@Nullable public MetaHints behaviorHints;

	public boolean isValid() {
		return id != null && !id.isEmpty() && type != null && !type.isEmpty() && name != null;
	}

	public boolean isSeries() {
		return TYPE_SERIES.equals(type);
	}

	@NonNull
	public String nameOrEmpty() {
		return name != null ? name : "";
	}

	@NonNull
	public List<Video> videosOrEmpty() {
		return videos != null ? videos : Collections.emptyList();
	}

	/** every real episode, specials left out */
	@NonNull
	public List<String> episodeIds() {
		List<String> ids = new ArrayList<>();
		for (Video v : videosOrEmpty()) {
			if (v.id != null && v.seasonOrZero() > 0) {
				ids.add(v.id);
			}
		}
		return ids;
	}

	/** specials (season 0) last */
	@NonNull
	public Map<Integer, List<Video>> seasons() {
		Map<Integer, List<Video>> map = new TreeMap<>((a, b) -> {
			if (a == 0 && b != 0) {
				return 1;
			}
			if (b == 0 && a != 0) {
				return -1;
			}
			return Integer.compare(a, b);
		});
		for (Video v : videosOrEmpty()) {
			if (v.id == null) {
				continue;
			}
			map.computeIfAbsent(v.seasonOrZero(), k -> new ArrayList<>()).add(v);
		}
		for (List<Video> list : map.values()) {
			list.sort((a, b) -> Integer.compare(a.episodeOrZero(), b.episodeOrZero()));
		}
		return map;
	}

	/** Next in the season, else first of the next one. Specials only lead to specials. */
	@Nullable
	public Video nextVideo(@NonNull String videoId) {
		boolean found = false;
		for (List<Video> episodes : seasons().values()) {
			for (Video v : episodes) {
				if (found) {
					return v.seasonOrZero() == 0 && !isSpecial(videoId) ? null : v;
				}
				if (videoId.equals(v.id)) {
					found = true;
				}
			}
		}
		return null;
	}

	@Nullable
	public Video video(@NonNull String videoId) {
		for (Video v : videosOrEmpty()) {
			if (videoId.equals(v.id)) {
				return v;
			}
		}
		return null;
	}

	private boolean isSpecial(String videoId) {
		Video v = video(videoId);
		return v != null && v.seasonOrZero() == 0;
	}

	@Nullable
	public String playableVideoId() {
		if (behaviorHints != null && behaviorHints.defaultVideoId != null) {
			return behaviorHints.defaultVideoId;
		}
		if (!isSeries()) {
			return id;
		}
		return null;
	}

	@Nullable
	public String trailerYoutubeId() {
		List<Trailer> all = new ArrayList<>();
		if (trailerStreams != null) {
			all.addAll(trailerStreams);
		}
		if (trailers != null) {
			all.addAll(trailers);
		}
		for (Trailer t : all) {
			String yt = t.youtubeId();
			if (yt != null && yt.matches("[A-Za-z0-9_-]{6,20}")) {
				return yt;
			}
		}
		return null;
	}
}
