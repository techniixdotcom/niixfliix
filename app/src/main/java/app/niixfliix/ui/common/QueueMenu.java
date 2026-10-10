package app.niixfliix.ui.common;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.History;
import app.niixfliix.data.model.QueueItem;

/** The long-press menu on posters and episodes: queue it, play it next, mark it watched. */
public final class QueueMenu {

	private QueueMenu() {
	}

	public static void show(@NonNull Context context, @NonNull PosterItem poster) {
		show(context, poster, null, null);
	}

	/** with one more action at the end, like "Remove" in the library */
	public static void show(@NonNull Context context, @NonNull PosterItem poster, @Nullable String extraLabel,
			@Nullable Runnable extraAction) {
		Menu menu = new Menu(context, QueueItem.of(poster.id, poster.type, poster.name, poster.poster));
		if (Meta.TYPE_SERIES.equals(poster.type)) {
			menu.add(R.string.details_mark_watched, () -> markSeriesWatched(context, poster));
		} else {
			History history = AppGraph.get().history;
			boolean watched = history.isWatched(poster.id);
			menu.add(watched ? R.string.details_mark_unwatched : R.string.details_mark_watched,
					() -> history.setWatched(poster.id, !watched));
		}
		if (extraLabel != null && extraAction != null) {
			menu.add(extraLabel, extraAction);
		}
		menu.show();
	}

	public static void showEpisode(@NonNull Context context, @NonNull QueueItem episode, boolean watched,
			@NonNull Runnable toggleWatched) {
		Menu menu = new Menu(context, episode);
		menu.add(watched ? R.string.details_mark_unwatched : R.string.details_mark_watched, toggleWatched);
		menu.show();
	}

	/** every episode, which needs the episode list first */
	private static void markSeriesWatched(Context context, PosterItem poster) {
		AppGraph graph = AppGraph.get();
		Context app = context.getApplicationContext();
		graph.io.execute(() -> {
			Meta meta = MetaFinder.find(graph.addons, graph.addonClient, poster.type, poster.id);
			List<String> ids = meta != null ? meta.episodeIds() : Collections.emptyList();
			graph.main.post(() -> {
				if (ids.isEmpty()) {
					Toast.makeText(app, R.string.queue_mark_failed, Toast.LENGTH_SHORT).show();
					return;
				}
				graph.history.setWatched(ids, true);
				Toast.makeText(app, R.string.queue_marked_watched, Toast.LENGTH_SHORT).show();
			});
		});
	}

	private static final class Menu {
		private final Context context;
		private final QueueItem item;
		private final List<String> labels = new ArrayList<>();
		private final List<Runnable> actions = new ArrayList<>();

		Menu(Context context, QueueItem item) {
			this.context = context;
			this.item = item;
			add(R.string.queue_add, () -> enqueue(false));
			add(R.string.queue_play_next, () -> enqueue(true));
		}

		void add(int label, Runnable action) {
			add(context.getString(label), action);
		}

		void add(String label, Runnable action) {
			labels.add(label);
			actions.add(action);
		}

		void show() {
			new MaterialAlertDialogBuilder(context)
					.setTitle(item.label())
					.setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
					.show();
		}

		private void enqueue(boolean next) {
			AppGraph graph = AppGraph.get();
			boolean added = graph.queue.add(item, next);
			graph.playback().queueChanged();
			int message = next ? R.string.queue_added_next : added ? R.string.queue_added : R.string.queue_already;
			Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
		}
	}
}
