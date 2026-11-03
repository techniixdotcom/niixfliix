package app.niixfliix.addon;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.live.LiveCheck;
import app.niixfliix.torrentio.TorrentioStreamParser;
import app.niixfliix.util.Logs;
import app.niixfliix.util.MainQueue;

public final class StreamSearch {

	public interface Listener {
		@MainThread
		void onResults(@NonNull InstalledAddon addon, @NonNull List<StreamInfo> streams);

		@MainThread
		void onAddonFailed(@NonNull InstalledAddon addon);

		@MainThread
		void onFinished();
	}

	private static final String TAG = "StreamSearch";

	private final AtomicBoolean cancelled = new AtomicBoolean();
	private final int addonCount;

	private StreamSearch(int addonCount) {
		this.addonCount = addonCount;
	}

	@NonNull
	public static StreamSearch start(@NonNull AddonRepository addons, @NonNull AddonClient client,
			@NonNull ExecutorService io, @NonNull MainQueue main, @NonNull String type, @NonNull String videoId,
			@NonNull Listener listener) {
		List<InstalledAddon> sources = addons.supporting(Manifest.RESOURCE_STREAM, type, videoId);
		StreamSearch search = new StreamSearch(sources.size());
		if (sources.isEmpty()) {
			main.post(() -> {
				if (!search.cancelled.get()) {
					listener.onFinished();
				}
			});
			return search;
		}
		AtomicInteger left = new AtomicInteger(sources.size());
		for (InstalledAddon addon : sources) {
			io.execute(() -> {
				List<StreamInfo> parsed = null;
				try {
					List<Stream> streams = client.streams(addon, type, videoId);
					parsed = new ArrayList<>(streams.size());
					String name = addon.displayName();
					for (Stream s : streams) {
						StreamInfo info = TorrentioStreamParser.parse(s, name, addon.transportUrl);
						if (!StreamFilter.isPlaceholder(info, type) && !StreamFilter.isLowQuality(info, type)
								&& !LiveCheck.isKnownDead(s)) {
							parsed.add(info);
						}
					}
				} catch (IOException | RuntimeException e) {
					Logs.w(TAG, "Streams failed for " + addon.transportUrl, e);
				}
				List<StreamInfo> result = parsed;
				main.post(() -> {
					boolean last = left.decrementAndGet() == 0;
					if (search.cancelled.get()) {
						return;
					}
					if (result != null) {
						listener.onResults(addon, result);
					} else {
						listener.onAddonFailed(addon);
					}
					if (last) {
						listener.onFinished();
					}
				});
			});
		}
		return search;
	}

	public int addonCount() {
		return addonCount;
	}

	public void cancel() {
		cancelled.set(true);
	}
}
