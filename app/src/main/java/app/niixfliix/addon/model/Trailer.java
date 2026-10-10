package app.niixfliix.addon.model;

import androidx.annotation.Nullable;

/** trailerStreams {title, ytId} or the older trailers {source, type} */
public final class Trailer {

	@Nullable public String ytId;
	@Nullable public String source;

	@Nullable
	public String youtubeId() {
		return ytId != null ? ytId : source;
	}
}
