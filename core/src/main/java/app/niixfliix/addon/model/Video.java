package app.niixfliix.addon.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Video {

	@Nullable public String id;
	@Nullable public String title;
	@Nullable public String name;
	@Nullable public Integer season;
	@Nullable public Integer episode;
	@Nullable public String released;
	@Nullable public String thumbnail;
	@Nullable public String overview;
	@Nullable public String description;

	@NonNull
	public String displayTitle() {
		if (title != null && !title.isEmpty()) {
			return title;
		}
		return name != null ? name : "";
	}

	@Nullable
	public String summary() {
		if (overview != null && !overview.trim().isEmpty()) {
			return overview.trim();
		}
		if (description != null && !description.trim().isEmpty()) {
			return description.trim();
		}
		return null;
	}

	public int seasonOrZero() {
		return season != null ? season : 0;
	}

	public int episodeOrZero() {
		return episode != null ? episode : 0;
	}
}
