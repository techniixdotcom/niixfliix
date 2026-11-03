package app.niixfliix.addon.model;

import androidx.annotation.Nullable;

public final class Trailer {

	@Nullable public String ytId;
	@Nullable public String source;

	@Nullable
	public String youtubeId() {
		return ytId != null ? ytId : source;
	}
}
