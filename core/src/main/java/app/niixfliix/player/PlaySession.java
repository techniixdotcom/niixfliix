package app.niixfliix.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;

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

	/**
	 * The torrent with this info hash won the start race, so it's the stream playing now. Returns whether it
	 * was found among this session's streams.
	 */
	public boolean adoptTorrent(@NonNull String infoHash) {
		for (StreamInfo s : streams) {
			if (s.kind == StreamInfo.Kind.TORRENT && infoHash.equalsIgnoreCase(s.stream.infoHash)) {
				stream = s;
				return true;
			}
		}
		return false;
	}

	@Nullable
	public Video nextVideo() {
		return meta != null ? meta.nextVideo(videoId) : null;
	}
}
