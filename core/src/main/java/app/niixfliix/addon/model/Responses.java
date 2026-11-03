package app.niixfliix.addon.model;

import androidx.annotation.Nullable;

import java.util.List;

public final class Responses {

	public static final class Catalog {
		@Nullable public List<Meta> metas;
	}

	public static final class MetaResponse {
		@Nullable public Meta meta;
	}

	public static final class Streams {
		@Nullable public List<Stream> streams;
	}

	public static final class Subtitles {
		@Nullable public List<Subtitle> subtitles;
	}

	private Responses() {
	}
}
