package app.niixfliix.download;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamCache;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.addon.StreamSearch;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.app.Constant;
import app.niixfliix.data.NewEpisodes;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.util.FileNames;

public final class SeriesDownload {

	public static final class Result {
		public final int added;
		public final int alreadyThere;
		public final int notDownloadable;

		Result(int added, int alreadyThere, int notDownloadable) {
			this.added = added;
			this.alreadyThere = alreadyThere;
			this.notDownloadable = notDownloadable;
		}
	}

	public interface Callback {
		@MainThread
		void onFinished(@NonNull Result result);
	}

	private static final int PARALLEL = 3;

	private final StreamCache streams;
	private final Downloads downloads;
	private final String type;
	private final String seriesName;
	private final List<Video> episodes;
	private final Function<Video, String> titleOf;
	private final Quality preferred;
	private final Callback callback;
	private final StreamInfo[] picks;
	private final boolean[] looked;
	private int started;
	private int nextToQueue;
	private int added;
	private int alreadyThere;
	private int notDownloadable;

	private SeriesDownload(StreamCache streams, Downloads downloads, String type, String seriesName,
			List<Video> episodes, Function<Video, String> titleOf, Quality preferred, Callback callback) {
		this.streams = streams;
		this.downloads = downloads;
		this.type = type;
		this.seriesName = seriesName;
		this.episodes = episodes;
		this.titleOf = titleOf;
		this.preferred = preferred;
		this.callback = callback;
		picks = new StreamInfo[episodes.size()];
		looked = new boolean[episodes.size()];
	}

	@NonNull
	public static List<Video> episodes(@NonNull Meta series, @Nullable Integer season, long nowMs) {
		List<Video> out = new ArrayList<>();
		for (Map.Entry<Integer, List<Video>> e : series.seasons().entrySet()) {
			int number = e.getKey();
			if (season != null ? number != season : number == 0) {
				continue;
			}
			for (Video v : e.getValue()) {
				long released = NewEpisodes.releasedMs(v.released);
				if (v.id != null && (released <= 0 || released <= nowMs)) {
					out.add(v);
				}
			}
		}
		return out;
	}

	@MainThread
	public static void start(@NonNull StreamCache streams, @NonNull Downloads downloads, @NonNull Meta series,
			@NonNull List<Video> episodes, @NonNull Quality preferred, @NonNull Function<Video, String> titleOf,
			@NonNull Callback callback) {
		String type = series.type != null ? series.type : Meta.TYPE_SERIES;
		SeriesDownload run = new SeriesDownload(streams, downloads, type, series.nameOrEmpty(),
				new ArrayList<>(episodes), titleOf, preferred, callback);
		if (episodes.isEmpty()) {
			callback.onFinished(new Result(0, 0, 0));
			return;
		}
		for (int i = 0; i < PARALLEL; i++) {
			run.lookUpNext();
		}
	}

	@NonNull
	public static String fileName(@NonNull String seriesName, int season, int episode, @NonNull String extension) {
		String name = seriesName.trim().isEmpty() ? "Episode" : seriesName.trim();
		return String.format(Locale.ROOT, "%s.s%de%d%s", name, season, episode, extension);
	}

	@NonNull
	public static String fileNameFor(@NonNull Meta meta, @NonNull String videoId, @NonNull StreamInfo stream,
			@NonNull String title) {
		Video v = meta.isSeries() ? meta.video(videoId) : null;
		if (v != null && v.season != null && v.episode != null && !meta.nameOrEmpty().trim().isEmpty()) {
			return fileName(meta.nameOrEmpty(), v.season, v.episode, stream.downloadExtension());
		}
		return stream.downloadFileName(title);
	}

	@VisibleForTesting
	@Nullable
	static StreamInfo pick(@NonNull List<StreamInfo> all, @NonNull Quality preferred) {
		List<StreamInfo> savable = new ArrayList<>();
		for (StreamInfo s : all) {
			if (s.isDownloadable()) {
				savable.add(s);
			}
		}
		List<StreamInfo> best = StreamRanker.bestPerQuality(StreamRanker.sort(savable), false);
		return StreamRanker.pick(best, preferred, null, false);
	}

	private void lookUpNext() {
		if (started >= episodes.size()) {
			return;
		}
		int index = started++;
		Video episode = episodes.get(index);
		List<StreamInfo> found = new ArrayList<>();
		String videoId = episode.id;
		if (videoId == null) {
			done(index, null);
			return;
		}
		streams.open(type, videoId, new StreamSearch.Listener() {
			@Override
			public void onResults(@NonNull InstalledAddon addon, @NonNull List<StreamInfo> result) {
				found.addAll(result);
			}

			@Override
			public void onAddonFailed(@NonNull InstalledAddon addon) {
			}

			@Override
			public void onFinished() {
				done(index, pick(found, preferred));
			}
		});
	}

	private void done(int index, @Nullable StreamInfo pick) {
		picks[index] = pick;
		looked[index] = true;
		while (nextToQueue < episodes.size() && looked[nextToQueue]) {
			queue(episodes.get(nextToQueue), picks[nextToQueue]);
			picks[nextToQueue] = null;
			nextToQueue++;
		}
		if (nextToQueue == episodes.size()) {
			callback.onFinished(new Result(added, alreadyThere, notDownloadable));
			return;
		}
		lookUpNext();
	}

	private void queue(Video episode, @Nullable StreamInfo pick) {
		if (pick == null || pick.stream.url == null) {
			notDownloadable++;
			return;
		}
		String name = fileName(seriesName, episode.seasonOrZero(), episode.episodeOrZero(), pick.downloadExtension());
		if (isAlreadyThere(name)) {
			alreadyThere++;
			return;
		}
		if (downloads.enqueue(pick.stream.url, pick.stream.requestHeaders(), titleOf.apply(episode), name)) {
			added++;
		} else {
			notDownloadable++;
		}
	}

	private boolean isAlreadyThere(String fileName) {
		String saved = FileNames.sanitize(fileName, Constant.FILE_NAME_MAX_BYTES);
		for (DownloadItem item : downloads.all()) {
			if (item.state != DownloadItem.State.FAILED && item.fileName.equalsIgnoreCase(saved)) {
				return true;
			}
		}
		return false;
	}
}
