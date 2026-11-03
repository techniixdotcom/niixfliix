package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.player.PlaySession;
import app.niixfliix.torrent.Magnet;
import app.niixfliix.torrentio.TorrentioStreamParser;

public final class Launch {

	private Launch() {
	}

	public static void play(@NonNull PlaySession session) {
		DesktopGraph.get().playback().play(session);
		MainWindow.get().showPlayer();
	}

	public static void playMagnet(@NonNull Magnet magnet) {
		Stream stream = new Stream();
		stream.infoHash = magnet.infoHash;
		stream.title = magnet.name;
		List<String> sources = new ArrayList<>();
		for (String tracker : magnet.trackers) {
			sources.add("tracker:" + tracker);
		}
		stream.sources = sources;
		String title = magnet.name != null ? magnet.name : I18n.t("magnet_untitled");
		StreamInfo info = TorrentioStreamParser.parse(stream, I18n.t("magnet_source"), "");
		play(new PlaySession(null, Meta.TYPE_MOVIE, "magnet:" + magnet.infoHash, title, null, magnet.infoHash, null,
				Collections.singletonList(info), info));
	}

	public static void playDownload(@NonNull DownloadItem item) {
		File file = DesktopGraph.downloadedFile(item);
		if (file == null || !file.isFile()) {
			return;
		}
		Stream stream = new Stream();
		stream.url = file.toURI().toString();
		StreamInfo info = new StreamInfo(stream, I18n.t("download_source"), "");
		info.kind = StreamInfo.Kind.DIRECT;
		info.releaseName = item.fileName;
		play(new PlaySession(null, Meta.TYPE_MOVIE, "download:" + item.id, item.title, null, item.id, null,
				Collections.singletonList(info), info));
	}
}
