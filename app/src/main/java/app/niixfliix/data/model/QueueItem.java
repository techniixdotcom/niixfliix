package app.niixfliix.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class QueueItem {

	@NonNull public String metaId = "";
	@NonNull public String type = "";
	@NonNull public String name = "";
	@Nullable public String poster;
	/** a specific episode; null = the movie, or for a series whatever episode is up next when it's played */
	@Nullable public String videoId;
	/** "S1 E2 · Title" when videoId is an episode */
	@Nullable public String episode;

	@NonNull
	public static QueueItem of(@NonNull String metaId, @NonNull String type, @NonNull String name,
			@Nullable String poster) {
		QueueItem item = new QueueItem();
		item.metaId = metaId;
		item.type = type;
		item.name = name;
		item.poster = poster;
		return item;
	}

	@NonNull
	public String key() {
		return videoId != null ? metaId + "|" + videoId : metaId;
	}

	@NonNull
	public String label() {
		return episode != null ? name + " · " + episode : name;
	}
}
