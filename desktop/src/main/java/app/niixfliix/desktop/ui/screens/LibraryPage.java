package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Dialog;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.ListBox;
import app.niixfliix.desktop.gtk.Picture;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.gtk.Scroller;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Opener;
import app.niixfliix.desktop.platform.Paths;
import app.niixfliix.desktop.ui.Dialogs;
import app.niixfliix.desktop.ui.Fmt;
import app.niixfliix.desktop.ui.ImageLoader;
import app.niixfliix.desktop.ui.Launch;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.PosterCard;
import app.niixfliix.desktop.ui.PosterGrid;
import app.niixfliix.desktop.ui.PosterMenu;
import app.niixfliix.desktop.ui.Toast;
import app.niixfliix.download.Downloads;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.player.PlaySession;
import app.niixfliix.player.QueueResolver;
import app.niixfliix.ui.common.PosterItem;

public final class LibraryPage implements Page, PosterCard.Listener, Downloads.Listener {

	private enum Tab {
		SAVED("library_saved"),
		HISTORY("library_history"),
		DOWNLOADS("library_downloads"),
		QUEUE("library_queue");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private enum Filter {
		ALL("library_filter_all", null),
		MOVIES("type_movies", Meta.TYPE_MOVIE),
		SERIES("type_series", Meta.TYPE_SERIES);

		final String label;
		@Nullable final String type;

		Filter(String label, @Nullable String type) {
			this.label = label;
			this.type = type;
		}
	}

	private static final String KEY_WATCHED_HEADER = "watched";
	private static final int QUEUE_THUMB_WIDTH = 48;
	private static final int QUEUE_THUMB_HEIGHT = 72;

	private final DesktopGraph graph = DesktopGraph.get();
	private final Box root = Box.column(0);
	private final Box filters = Box.row(8);
	private final Box queueActions = Box.row(8);
	private final Layers content = Layers.stack(0, false);
	private final PosterGrid grid = new PosterGrid(this);
	private final ListBox downloads = ListBox.create(true);
	private final Widget downloadsView;
	private final ListBox queue = ListBox.create(true);
	private final Widget queueView;
	private final Label empty = Label.wrapping(null, "empty-text");
	private final Map<String, DownloadRow> downloadRows = new HashMap<>();
	private final List<String> downloadOrder = new ArrayList<>();
	@Nullable private QueueResolver resolving;
	@Nullable private Dialog resolvingDialog;
	private Tab tab = Tab.SAVED;
	private Filter filter = Filter.ALL;
	private boolean shown;

	public LibraryPage() {
		Box tabs = Box.row(0);
		tabs.addClass("linked");
		tabs.halign(Widget.ALIGN_START);
		Button firstTab = null;
		for (Tab t : Tab.values()) {
			Button button = Button.toggle(I18n.t(t.label), "tab-button");
			if (firstTab == null) {
				firstTab = button;
			} else {
				button.group(firstTab);
			}
			button.active(t == tab);
			button.onToggled(() -> {
				if (button.isActive() && tab != t) {
					tab = t;
					show();
				}
			});
			tabs.add(button);
		}
		filters.halign(Widget.ALIGN_START);
		Button firstFilter = null;
		for (Filter f : Filter.values()) {
			Button chip = Button.toggle(I18n.t(f.label), "chip");
			if (firstFilter == null) {
				firstFilter = chip;
			} else {
				chip.group(firstFilter);
			}
			chip.active(f == filter);
			chip.onToggled(() -> {
				if (chip.isActive() && filter != f) {
					filter = f;
					show();
				}
			});
			filters.add(chip);
		}
		Button playAll = Button.iconText(Icons.name("play"), I18n.t("queue_play_all"), "suggested-action", "pill");
		playAll.onClick(() -> {
			QueueItem first = graph.queue.peek();
			if (first != null) {
				playQueued(first);
			}
		});
		Button clear = Button.text(I18n.t("queue_clear"), "flat");
		clear.onClick(() -> {
			graph.queue.clear();
			graph.playback().queueChanged();
			show();
		});
		queueActions.add(playAll, clear);
		Box top = Box.column(14, Label.of(I18n.t("nav_library"), "title-1"), tabs, filters, queueActions);
		top.margins(20, 24, 8, 24);
		downloadsView = listPage(downloads);
		queueView = listPage(queue);
		content.add(grid.view()).add(downloadsView).add(queueView);
		empty.center();
		empty.maxWidthChars(60);
		empty.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER).margin(32);
		empty.canTarget(false);
		Layers center = Layers.overlay(content, empty);
		center.vexpand(true);
		root.add(top, center);
	}

	private static Widget listPage(ListBox list) {
		list.margins(8, 24, 24, 24);
		Scroller scroll = Scroller.vertical(list);
		scroll.vexpand(true);
		return scroll;
	}

	@NonNull
	@Override
	public Widget view() {
		return root;
	}

	@Override
	public void onShow() {
		if (!shown) {
			shown = true;
			graph.downloads().addListener(this);
		}
		show();
	}

	@Override
	public void onHide() {
		stopResolving();
	}

	@Override
	public void onDownloadsChanged() {
		if (tab == Tab.DOWNLOADS) {
			syncDownloads();
		}
	}

	private void show() {
		boolean posters = tab == Tab.SAVED || tab == Tab.HISTORY;
		filters.visible(posters);
		queueActions.visible(false);
		switch (tab) {
			case QUEUE: {
				List<QueueItem> items = graph.queue.all();
				fillQueue(items);
				queueActions.visible(!items.isEmpty());
				content.show(queueView);
				setEmpty(items.isEmpty(), I18n.t("library_queue_empty_desktop"));
				return;
			}
			case DOWNLOADS: {
				syncDownloads();
				content.show(downloadsView);
				return;
			}
			default:
				break;
		}
		List<PosterItem> items;
		boolean anyAtAll;
		if (tab == Tab.SAVED) {
			List<LibraryItem> saved = graph.library.all();
			anyAtAll = !saved.isEmpty();
			items = savedItems(saved);
			refreshNewEpisodes(saved);
			graph.fillLibraryDates(this::show);
		} else {
			List<HistoryEntry> recent = graph.history.recent();
			anyAtAll = !recent.isEmpty();
			items = new ArrayList<>();
			for (HistoryEntry e : recent) {
				if (passesFilter(e.type)) {
					items.add(withNewMark(PosterItem.of(e, true)));
				}
			}
		}
		grid.show(items);
		grid.refresh();
		content.show(grid.view());
		grid.onShown();
		String emptyText = anyAtAll ? I18n.t("library_filter_empty")
				: I18n.t(tab == Tab.SAVED ? "library_saved_empty" : "library_history_empty");
		setEmpty(items.isEmpty(), emptyText);
	}

	private List<PosterItem> savedItems(List<LibraryItem> saved) {
		List<PosterItem> toWatch = new ArrayList<>();
		List<PosterItem> watched = new ArrayList<>();
		for (LibraryItem item : saved) {
			if (!passesFilter(item.type)) {
				continue;
			}
			PosterItem poster = withNewMark(PosterItem.of(item));
			boolean done = !Meta.TYPE_SERIES.equals(item.type) && graph.history.isWatched(item.id);
			(done ? watched : toWatch).add(poster);
		}
		Comparator<PosterItem> order = ReleaseOrder.<PosterItem>newestFirst(this::releaseOf).thenComparing(byTitle());
		toWatch.sort(order);
		watched.sort(order);
		List<PosterItem> items = new ArrayList<>(toWatch.size() + watched.size() + 1);
		items.addAll(toWatch);
		if (!watched.isEmpty()) {
			items.add(PosterItem.header(KEY_WATCHED_HEADER, I18n.t("library_watched")));
			items.addAll(watched);
		}
		return items;
	}

	private Comparator<PosterItem> byTitle() {
		Collator collator = Collator.getInstance(I18n.locale());
		collator.setStrength(Collator.PRIMARY);
		return (a, b) -> {
			String x = title(a);
			String y = title(b);
			int c = collator.compare(x, y);
			if (c == 0) {
				c = x.compareTo(y);
			}
			return c != 0 ? c : a.id.compareTo(b.id);
		};
	}

	private long releaseOf(PosterItem item) {
		return Meta.TYPE_SERIES.equals(item.type) ? Math.max(item.released, graph.newEpisodes.latestOutMs(item.id))
				: item.released;
	}

	private String title(PosterItem item) {
		String translated = graph.translations.name(item.id);
		return (translated != null ? translated : item.name).trim();
	}

	private boolean passesFilter(String type) {
		return filter.type == null || filter.type.equals(type);
	}

	private PosterItem withNewMark(PosterItem item) {
		return Meta.TYPE_SERIES.equals(item.type) ? item.withNew(graph.newEpisodes.hasNew(item.id)) : item;
	}

	private void refreshNewEpisodes(List<LibraryItem> saved) {
		List<String> series = new ArrayList<>();
		for (LibraryItem item : saved) {
			if (Meta.TYPE_SERIES.equals(item.type)) {
				series.add(item.id);
			}
		}
		graph.newEpisodes.refresh(series,
				id -> MetaFinder.find(graph.addons, graph.addonClient, Meta.TYPE_SERIES, id),
				this::show);
	}

	private void setEmpty(boolean isEmpty, String text) {
		empty.text(text);
		empty.visible(isEmpty);
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		MainWindow.get().openDetails(item);
	}

	@Override
	public void onPosterMenu(@NonNull PosterItem item, @NonNull Widget card, double x, double y) {
		PosterMenu.show(card, x, y, item, tab != Tab.SAVED, I18n.t("action_remove"), () -> confirmRemove(item),
				this::show);
	}

	private void confirmRemove(PosterItem item) {
		boolean saved = tab == Tab.SAVED;
		Dialogs.confirm(item.name, I18n.t(saved ? "library_remove_saved" : "library_remove_history"),
				I18n.t("action_remove"), () -> {
					if (saved) {
						graph.library.remove(item.id);
					} else {
						graph.history.removeMeta(item.id);
					}
					show();
				});
	}

	private void fillQueue(List<QueueItem> items) {
		queue.clear();
		for (QueueItem item : items) {
			Picture poster = Picture.create(Picture.FIT_COVER);
			Widget thumb = poster.fixed(QUEUE_THUMB_WIDTH, QUEUE_THUMB_HEIGHT);
			thumb.addClass("queue-poster");
			thumb.clipChildren();
			ImageLoader.into(poster, item.poster, QUEUE_THUMB_WIDTH, QUEUE_THUMB_HEIGHT, ImageLoader.Kind.POSTER);
			Label title = Label.wrapping(item.label());
			title.hexpand(true).valign(Widget.ALIGN_CENTER);
			Button open = Button.wrapping(Box.row(14, thumb, title), "flat", "selectable-row");
			open.hexpand(true);
			open.onClick(() -> playQueued(item));
			Button remove = Button.icon(Icons.name("close"), I18n.t("action_remove"));
			remove.valign(Widget.ALIGN_CENTER);
			remove.onClick(() -> {
				graph.queue.remove(item.key());
				graph.playback().queueChanged();
				show();
			});
			Box row = Box.row(6, open, remove);
			row.margins(4, 8, 4, 4);
			queue.add(row);
		}
	}

	private void playQueued(QueueItem item) {
		stopResolving();
		Button cancel = Dialogs.button(I18n.t("action_cancel"), false);
		Dialog dialog = Dialogs.custom(item.label(), Label.wrapping(I18n.t("queue_finding")), 420, cancel);
		resolvingDialog = dialog;
		dialog.onClosed(() -> {
			if (resolvingDialog == dialog) {
				resolvingDialog = null;
				cancelResolving();
			}
		});
		cancel.onClick(this::stopResolving);
		resolving = QueueResolver.start(true, item, null, null, new QueueResolver.Callback() {
			@Override
			public void onReady(@NonNull PlaySession session) {
				stopResolving();
				graph.queue.remove(item.key());
				Launch.play(session);
			}

			@Override
			public void onFailed() {
				stopResolving();
				Toast.show(I18n.t("queue_no_stream"));
			}
		});
	}

	private void cancelResolving() {
		if (resolving != null) {
			resolving.cancel();
			resolving = null;
		}
	}

	private void stopResolving() {
		cancelResolving();
		if (resolvingDialog != null) {
			Dialog d = resolvingDialog;
			resolvingDialog = null;
			d.close();
		}
	}

	private void syncDownloads() {
		List<DownloadItem> items = graph.downloads().all();
		List<String> ids = new ArrayList<>();
		for (DownloadItem item : items) {
			ids.add(item.id);
		}
		if (!ids.equals(downloadOrder)) {
			downloads.clear();
			Set<String> keep = new HashSet<>(ids);
			downloadRows.keySet().retainAll(keep);
			for (DownloadItem item : items) {
				DownloadRow row = new DownloadRow();
				downloadRows.put(item.id, row);
				downloads.add(row.root);
			}
			downloadOrder.clear();
			downloadOrder.addAll(ids);
		}
		for (DownloadItem item : items) {
			DownloadRow row = downloadRows.get(item.id);
			if (row != null) {
				row.bind(item);
			}
		}
		if (tab == Tab.DOWNLOADS) {
			setEmpty(items.isEmpty(), I18n.t("library_downloads_empty_desktop"));
		}
	}

	private final class DownloadRow {
		final Label title = Label.wrapping(null);
		final Label status = Label.of(null, "caption", "dim-label");
		final Progress progress = Progress.bar();
		final Button toggle = Button.icon(Icons.name("pause"), I18n.t("download_pause"));
		final Button folder = Button.icon(Icons.name("folder"), I18n.t("download_show_in_folder"));
		final Button delete = Button.icon(Icons.name("delete"), I18n.t("action_delete"));
		final Box root;
		@Nullable DownloadItem item;

		DownloadRow() {
			Box text = Box.column(4, title, status, progress);
			text.hexpand(true);
			Button open = Button.wrapping(text, "flat", "selectable-row");
			open.hexpand(true);
			open.onClick(() -> {
				if (item != null && item.state == DownloadItem.State.DONE) {
					Launch.playDownload(item);
				}
			});
			toggle.onClick(() -> {
				DownloadItem it = item;
				if (it == null) {
					return;
				}
				if (it.isActive()) {
					graph.downloads().pause(it.id);
				} else if (it.state != DownloadItem.State.DONE) {
					graph.downloads().resume(it.id);
				}
			});
			folder.onClick(() -> {
				File file = item != null ? DesktopGraph.downloadedFile(item) : null;
				File dir = file != null ? file.getParentFile() : null;
				Opener.openFile(dir != null && dir.isDirectory() ? dir : Paths.downloads());
			});
			delete.onClick(() -> {
				DownloadItem it = item;
				if (it != null) {
					Dialogs.confirm(it.title, I18n.t("download_delete_message"), I18n.t("action_delete"), () -> {
						graph.downloads().delete(it.id);
						show();
					});
				}
			});
			for (Widget w : new Widget[] {toggle, folder, delete}) {
				w.valign(Widget.ALIGN_CENTER);
			}
			root = Box.row(6, open, toggle, folder, delete);
			root.margins(4, 8, 4, 4);
		}

		void bind(DownloadItem it) {
			item = it;
			title.text(it.title);
			status.text(statusText(it));
			boolean active = it.isActive();
			boolean done = it.state == DownloadItem.State.DONE;
			if (active && it.bytesTotal <= 0) {
				progress.pulse();
			} else {
				progress.fraction(it.percent() / 100.0);
			}
			progress.visible(!done);
			toggle.visible(!done);
			String toggleText = I18n.t(active ? "download_pause" : "download_resume");
			toggle.iconName(Icons.name(active ? "pause" : "download")).tooltip(toggleText);
			toggle.accessibleLabel(toggleText);
			File file = DesktopGraph.downloadedFile(it);
			folder.visible(file != null && file.exists());
		}
	}

	private static String statusText(DownloadItem item) {
		String done = Fmt.size(item.bytesDone);
		switch (item.state) {
			case DONE:
				return I18n.t("download_done", done);
			case PAUSED:
				return I18n.t("download_paused", done);
			case FAILED:
				return I18n.t("download_failed_desktop");
			case QUEUED:
				return I18n.t("download_queued");
			case RUNNING:
			default:
				if (item.bytesTotal > 0) {
					return I18n.t("download_progress", item.percent(), done, Fmt.size(item.bytesTotal));
				}
				return done;
		}
	}
}
