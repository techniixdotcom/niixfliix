package app.niixfliix.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;

/** What the PlaybackController plays: the title, the episode and every stream found for it. */
public final class PlaySession {

	@Nullable public final Meta meta;
	@NonNull public final String type;
	@NonNull public final String metaId;
	@NonNull public final String title;
	@Nullable public final String poster;
	@NonNull public final String videoId;
	@Nullable public final Video video;
	@NonNull public final List<StreamInfo> streams;
	@NonNull public StreamInfo stream;

	public PlaySession(@Nullable Meta meta, @NonNull String type, @NonNull String metaId, @NonNull String title,
			@Nullable String poster, @NonNull String videoId, @Nullable Video video,
			@NonNull List<StreamInfo> streams, @NonNull StreamInfo stream) {
		this.meta = meta;
		this.type = type;
		this.metaId = metaId;
		this.title = title;
		this.poster = poster;
		this.videoId = videoId;
		this.video = video;
		this.streams = new ArrayList<>(streams);
		this.stream = stream;
	}

	@Nullable
	public Video nextVideo() {
		return meta != null ? meta.nextVideo(videoId) : null;
	}
}
