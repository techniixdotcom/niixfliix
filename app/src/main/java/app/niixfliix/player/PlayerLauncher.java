package app.niixfliix.player;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.app.AppGraph;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.torrent.Magnet;
import app.niixfliix.torrentio.TorrentioStreamParser;

/** Every way into the player. */
public final class PlayerLauncher {

	private PlayerLauncher() {
	}

	public static void play(@NonNull Activity activity, @NonNull PlaySession session) {
		AppGraph.get().playback().play(session);
		activity.startActivity(new Intent(activity, PlayerActivity.class));
	}

	/** back to the player that's already running (mini bar tap) */
	public static void resume(@NonNull Activity activity) {
		activity.startActivity(new Intent(activity, PlayerActivity.class));
	}

	public static void playMagnet(@NonNull Activity activity, @NonNull Magnet magnet) {
		Stream stream = new Stream();
		stream.infoHash = magnet.infoHash;
		stream.title = magnet.name;
		List<String> sources = new ArrayList<>();
		for (String tracker : magnet.trackers) {
			sources.add("tracker:" + tracker);
		}
		stream.sources = sources;
		String title = magnet.name != null ? magnet.name : activity.getString(R.string.magnet_untitled);
		StreamInfo info = TorrentioStreamParser.parse(stream, activity.getString(R.string.magnet_source), "");
		PlaySession session = new PlaySession(null, Meta.TYPE_MOVIE, "magnet:" + magnet.infoHash, title, null,
				magnet.infoHash, null, Collections.singletonList(info), info);
		play(activity, session);
	}

	public static void playLocal(@NonNull Activity activity, @NonNull DownloadItem item) {
		if (item.contentUri == null) {
			return;
		}
		Stream stream = new Stream();
		stream.url = item.contentUri;
		StreamInfo info = new StreamInfo(stream, activity.getString(R.string.download_source), "");
		info.kind = StreamInfo.Kind.DIRECT;
		info.releaseName = item.fileName;
		PlaySession session = new PlaySession(null, Meta.TYPE_MOVIE, "download:" + item.id, item.title, null,
				item.id, null, Collections.singletonList(info), info);
		play(activity, session);
	}
}
