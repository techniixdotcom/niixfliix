package app.niixfliix.data.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class EpisodeList {

	public long fetchedAt;
	@NonNull public List<Episode> episodes = new ArrayList<>();

	public static final class Episode {
		@NonNull public String videoId = "";
		public int season;
		public int episode;
		public long releasedMs;

		public Episode() {
		}

		public Episode(@NonNull String videoId, int season, int episode, long releasedMs) {
			this.videoId = videoId;
			this.season = season;
			this.episode = episode;
			this.releasedMs = releasedMs;
		}
	}
}
