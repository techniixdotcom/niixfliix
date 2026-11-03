package app.niixfliix.app;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamCache;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.addon.StreamSearch;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.torrent.TorrentEngine;
import app.niixfliix.util.MainQueue;

final class StartAhead implements StreamSearch.Listener {

	private final MainQueue main;
	private final TorrentEngine engine;
	private final Quality preferred;
	private final List<StreamInfo> all = new ArrayList<>();
	private final Runnable graceOver = this::decide;
	@Nullable private StreamCache.Handle search;
	private boolean done;
	private boolean waitingForGrace;

	private StartAhead(@NonNull MainQueue main, @NonNull TorrentEngine engine, @NonNull Quality preferred) {
		this.main = main;
		this.engine = engine;
		this.preferred = preferred;
	}

	@MainThread
	@NonNull
	static StartAhead start(@NonNull StreamCache streams, @NonNull MainQueue main, @NonNull TorrentEngine engine,
			@NonNull Quality preferred, @NonNull String type, @NonNull String videoId) {
		StartAhead ahead = new StartAhead(main, engine, preferred);
		ahead.search = streams.open(type, videoId, ahead);
		return ahead;
	}

	@MainThread
	void cancel() {
		done = true;
		main.removeCallbacks(graceOver);
		if (search != null) {
			search.cancel();
			search = null;
		}
	}

	@Override
	public void onResults(@NonNull InstalledAddon addon, @NonNull List<StreamInfo> streams) {
		if (done) {
			return;
		}
		all.addAll(streams);
		if (!waitingForGrace) {
			waitingForGrace = true;
			main.postDelayed(graceOver, StreamRanker.AUTOPLAY_GRACE_MS);
		}
		if (StreamRanker.canAutoplayEarly(best(), preferred)) {
			decide();
		}
	}

	@Override
	public void onAddonFailed(@NonNull InstalledAddon addon) {
	}

	@Override
	public void onFinished() {
		decide();
	}

	private List<StreamInfo> best() {
		return StreamRanker.bestPerQuality(StreamRanker.sort(all), false);
	}

	private void decide() {
		if (done) {
			return;
		}
		List<StreamInfo> best = best();
		if (best.isEmpty()) {
			return;
		}
		cancel();
		StreamInfo pick = StreamRanker.pick(best, preferred, null, false);
		if (pick != null && pick.kind == StreamInfo.Kind.TORRENT && pick.stream.infoHash != null
				&& StreamRanker.isPlayable(pick)) {
			engine.preload(Core.magnetFor(pick), pick.stream.fileIdx);
		}
	}
}
