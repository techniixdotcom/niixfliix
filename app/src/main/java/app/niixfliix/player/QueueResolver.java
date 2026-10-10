package app.niixfliix.player;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.addon.StreamSearch;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.net.Network;

/**
 * Turns a queued title (or the next episode) into a ready PlaySession: loads the details if needed,
 * works out the episode, asks the add-ons and picks the remembered quality the same way the streams sheet does.
 */
public final class QueueResolver {

	public interface Callback {
		void onReady(@NonNull PlaySession session);

		void onFailed();
	}

	private final AppGraph graph = AppGraph.get();
	private final boolean unmetered;
	private final QueueItem item;
	@Nullable private final String bingeGroup;
	private final Callback callback;
	@Nullable private StreamSearch search;
	private boolean cancelled;

	private QueueResolver(Context context, QueueItem item, @Nullable String bingeGroup, Callback callback) {
		this.unmetered = Network.isUnmetered(context);
		this.item = item;
		this.bingeGroup = bingeGroup;
		this.callback = callback;
	}

	/**
	 * @param meta       already loaded details, skips the lookup (next episode of what's playing)
	 * @param bingeGroup keeps the same release for the next episode
	 */
	@NonNull
	@MainThread
	public static QueueResolver start(@NonNull Context context, @NonNull QueueItem item, @Nullable Meta meta,
			@Nullable String bingeGroup, @NonNull Callback callback) {
		QueueResolver resolver = new QueueResolver(context, item, bingeGroup, callback);
		if (meta != null) {
			resolver.withMeta(meta);
		} else {
			resolver.graph.io.execute(() -> {
				AppGraph graph = resolver.graph;
				Meta found = MetaFinder.find(graph.addons, graph.addonClient, item.type, item.metaId);
				Meta loaded = found != null ? graph.translations.translate(found) : resolver.stub();
				graph.main.post(() -> resolver.withMeta(loaded));
			});
		}
		return resolver;
	}

	@MainThread
	public void cancel() {
		cancelled = true;
		if (search != null) {
			search.cancel();
		}
	}

	/** streams only need the id, a missing meta add-on shouldn't stop a movie */
	private Meta stub() {
		Meta stub = new Meta();
		stub.id = item.metaId;
		stub.type = item.type;
		stub.name = item.name;
		stub.poster = item.poster;
		return stub;
	}

	private void withMeta(Meta meta) {
		if (cancelled) {
			return;
		}
		String videoId = item.videoId;
		if (videoId == null) {
			if (meta.isSeries()) {
				Video next = graph.history.nextUp(meta);
				videoId = next != null ? next.id : null;
			} else {
				videoId = meta.playableVideoId();
			}
		}
		if (videoId == null) {
			callback.onFailed();
			return;
		}
		String target = videoId;
		List<StreamInfo> collected = new ArrayList<>();
		search = StreamSearch.start(graph.addons, graph.addonClient, graph.io, graph.main, item.type, target,
				new StreamSearch.Listener() {
					@Override
					public void onResults(@NonNull InstalledAddon addon, @NonNull List<StreamInfo> streams) {
						for (StreamInfo s : streams) {
							if (s.kind != StreamInfo.Kind.UNSUPPORTED) {
								collected.add(s);
							}
						}
					}

					@Override
					public void onAddonFailed(@NonNull InstalledAddon addon) {
						// the others may still have it
					}

					@Override
					public void onFinished() {
						if (!cancelled) {
							finish(meta, target, StreamRanker.sort(collected));
						}
					}
				});
	}

	private void finish(Meta meta, String videoId, List<StreamInfo> sorted) {
		StreamInfo pick = StreamRanker.pick(sorted, graph.playerPrefs.quality(unmetered), bingeGroup);
		if (pick == null) {
			callback.onFailed();
			return;
		}
		String title = meta.name != null ? meta.name : item.name;
		String poster = meta.poster != null ? meta.poster : item.poster;
		callback.onReady(new PlaySession(meta, item.type, item.metaId, title, poster, videoId, meta.video(videoId),
				sorted, pick));
	}
}
