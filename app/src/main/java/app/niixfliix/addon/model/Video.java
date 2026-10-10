package app.niixfliix.addon.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Video {

	@Nullable public String id;
	@Nullable public String title;
	// some add-ons send "name" instead of "title"
	@Nullable public String name;
	@Nullable public Integer season;
	@Nullable public Integer episode;
	@Nullable public String released;
	@Nullable public String thumbnail;

	@NonNull
	public String displayTitle() {
		if (title != null && !title.isEmpty()) {
			return title;
		}
		return name != null ? name : "";
	}

	public int seasonOrZero() {
		return season != null ? season : 0;
	}

	public int episodeOrZero() {
		return episode != null ? episode : 0;
	}
}
