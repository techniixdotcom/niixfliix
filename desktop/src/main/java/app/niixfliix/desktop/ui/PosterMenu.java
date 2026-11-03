package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.data.History;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Menu;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.ui.common.LibraryToggle;
import app.niixfliix.ui.common.PosterItem;

public final class PosterMenu {

	private PosterMenu() {
	}

	public static void show(@NonNull Widget owner, double x, double y, @NonNull PosterItem poster,
			@Nullable String extraLabel, @Nullable Runnable extraAction, @Nullable Runnable onWatchedChanged) {
		show(owner, x, y, poster, true, extraLabel, extraAction, onWatchedChanged);
	}

	public static void show(@NonNull Widget owner, double x, double y, @NonNull PosterItem poster,
			boolean libraryToggle, @Nullable String extraLabel, @Nullable Runnable extraAction,
			@Nullable Runnable onWatchedChanged) {
		Menu menu = base(QueueItem.of(poster.id, poster.type, poster.name, poster.poster));
		if (libraryToggle) {
			DesktopGraph graph = DesktopGraph.get();
			boolean saved = graph.library.contains(poster.id);
			menu.item(I18n.t(saved ? "details_unsave" : "details_save"), () -> {
				boolean nowSaved = LibraryToggle.toggle(graph.library, poster);
				Toast.show(I18n.t(nowSaved ? "library_added" : "library_removed"));
				if (onWatchedChanged != null) {
					onWatchedChanged.run();
				}
			});
		}
		if (Meta.TYPE_SERIES.equals(poster.type)) {
			menu.item(I18n.t("details_mark_watched"), () -> markSeriesWatched(poster, onWatchedChanged));
		} else {
			History history = DesktopGraph.get().history;
			boolean watched = history.isWatched(poster.id);
			menu.item(I18n.t(watched ? "details_mark_unwatched" : "details_mark_watched"), () -> {
				history.setWatched(poster.id, !watched);
				if (onWatchedChanged != null) {
					onWatchedChanged.run();
				}
			});
		}
		if (extraLabel != null && extraAction != null) {
			menu.item(extraLabel, extraAction);
		}
		menu.showAt(owner, x, y);
	}

	public static void showEpisode(@NonNull Widget owner, double x, double y, @NonNull QueueItem episode,
			boolean watched, @NonNull Runnable toggleWatched) {
		Menu menu = base(episode);
		menu.item(I18n.t(watched ? "details_mark_unwatched" : "details_mark_watched"), toggleWatched);
		menu.showAt(owner, x, y);
	}

	private static Menu base(QueueItem item) {
		return new Menu()
				.item(I18n.t("queue_add"), () -> enqueue(item, false))
				.item(I18n.t("queue_play_next"), () -> enqueue(item, true));
	}

	private static void enqueue(QueueItem item, boolean next) {
		DesktopGraph graph = DesktopGraph.get();
		boolean added = graph.queue.add(item, next);
		graph.playback().queueChanged();
		Toast.show(I18n.t(next ? "queue_added_next" : added ? "queue_added" : "queue_already"));
	}

	private static void markSeriesWatched(PosterItem poster, @Nullable Runnable onDone) {
		DesktopGraph graph = DesktopGraph.get();
		graph.io.execute(() -> {
			Meta meta = MetaFinder.find(graph.addons, graph.addonClient, poster.type, poster.id);
			List<String> ids = meta != null ? meta.episodeIds() : Collections.emptyList();
			graph.main.post(() -> {
				if (ids.isEmpty()) {
					Toast.show(I18n.t("queue_mark_failed"));
					return;
				}
				graph.history.setWatched(ids, true);
				Toast.show(I18n.t("queue_marked_watched"));
				if (onDone != null) {
					onDone.run();
				}
			});
		});
	}
}
