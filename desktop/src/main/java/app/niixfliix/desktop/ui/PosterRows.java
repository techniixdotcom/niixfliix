package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.MainLoop;
import app.niixfliix.desktop.gtk.Scroller;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.ui.common.PosterItem;

public final class PosterRows {

	public enum State {
		LOADING,
		READY,
		EMPTY,
		FAILED
	}

	public interface Loader {
		void load(@NonNull Row row);
	}

	public static final class Row {
		@NonNull public final String key;
		@NonNull public final String title;
		@Nullable public final String subtitle;
		@NonNull public State state = State.LOADING;
		@NonNull public List<PosterItem> items = new ArrayList<>();
		public boolean requested;
		@Nullable public String emptyText;
		@Nullable RowNode node;

		public Row(@NonNull String key, @NonNull String title, @Nullable String subtitle) {
			this.key = key;
			this.title = title;
			this.subtitle = subtitle;
		}
	}

	private static final int PRELOAD_PX = 400;
	private static final int PLACEHOLDERS = 8;

	private final PosterCard.Listener posters;
	private final Loader loader;
	private final Box content = Box.column(0);
	private final Scroller scroll = Scroller.vertical(content);
	@Nullable private Widget top;
	private List<Row> rows = Collections.emptyList();
	private boolean checkPosted;

	public PosterRows(@NonNull PosterCard.Listener posters, @NonNull Loader loader) {
		this.posters = posters;
		this.loader = loader;
		content.margins(0, 0, 32, 0);
		scroll.vexpand(true);
		scroll.vadjustment().onChange(this::loadVisible);
	}

	@NonNull
	public Widget view() {
		return scroll;
	}

	public void setRows(@Nullable Widget newTop, @NonNull List<Row> newRows) {
		for (Row old : rows) {
			if (old.node != null && !newRows.contains(old)) {
				content.remove(old.node.root);
				old.node = null;
			}
		}
		if (top != null && top != newTop) {
			content.remove(top);
		}
		List<Widget> kept = new ArrayList<>();
		for (Row row : newRows) {
			if (row.node != null) {
				Widget root = row.node.root;
				root.ref();
				content.remove(root);
				kept.add(root);
			}
		}
		if (newTop != null && newTop == top) {
			newTop.ref();
			content.remove(newTop);
			kept.add(newTop);
		}
		rows = new ArrayList<>(newRows);
		top = newTop;
		if (top != null) {
			content.add(top);
		}
		for (Row row : rows) {
			if (row.node == null) {
				row.node = new RowNode(row);
			}
			content.add(row.node.root);
			row.node.draw();
		}
		for (Widget w : kept) {
			w.unref();
		}
		loadVisible();
	}

	@Nullable
	public Row row(@NonNull String key) {
		for (Row row : rows) {
			if (row.key.equals(key)) {
				return row;
			}
		}
		return null;
	}

	public boolean isEmpty() {
		return rows.isEmpty();
	}

	public void update(@NonNull Row row) {
		if (row.node != null) {
			row.node.draw();
		}
	}

	public void refreshWatched() {
		for (Row row : rows) {
			if (row.node != null) {
				row.node.list.refresh();
			}
		}
	}

	public void onScrolled(@NonNull Runnable action) {
		scroll.vadjustment().onChange(action);
	}

	public double scrollOffset() {
		return scroll.vadjustment().value();
	}

	public void scrollToTop() {
		scroll.vadjustment().value(0);
	}

	private void loadVisible() {
		if (checkPosted) {
			return;
		}
		checkPosted = true;
		MainLoop.post(() -> {
			checkPosted = false;
			if (!scroll.isMapped()) {
				return;
			}
			Scroller.Adjustment v = scroll.vadjustment();
			double from = v.value();
			double to = from + v.pageSize() + PRELOAD_PX;
			for (Row row : rows) {
				if (row.requested || row.node == null) {
					continue;
				}
				float[] bounds = row.node.root.boundsIn(content);
				if (bounds != null && bounds[1] < to && bounds[1] + bounds[3] >= from) {
					row.requested = true;
					loader.load(row);
				}
			}
		});
	}

	public void onShown() {
		loadVisible();
	}

	private final class RowNode {
		final Row row;
		final Box root = Box.column(0);
		final PosterRow list;
		final Box placeholders = Box.row(12);
		final Label empty = Label.wrapping(I18n.t("row_empty"), "dim-label");
		final Box failed;
		final Layers body = Layers.stack(0, false);

		RowNode(Row row) {
			this.row = row;
			list = new PosterRow(posters);
			list.passWheelTo(scroll);
			Box header = Box.column(2, Label.of(row.title, "title-4"));
			if (row.subtitle != null && !row.subtitle.isEmpty()) {
				header.add(Label.of(row.subtitle, "caption", "dim-label"));
			}
			header.addClass("row-header");
			for (int i = 0; i < PLACEHOLDERS; i++) {
				placeholders.add(Box.column(0).size(PosterCard.WIDTH, PosterCard.HEIGHT).addClass("skeleton"));
			}
			placeholders.margins(6, 0, PosterRow.HEIGHT - PosterCard.HEIGHT - 6, 24);
			Button retry = Button.text(I18n.t("action_retry"), "flat");
			retry.onClick(() -> {
				row.state = State.LOADING;
				draw();
				loader.load(row);
			});
			failed = Box.row(8, Label.wrapping(I18n.t("row_failed_desktop"), "dim-label"), retry);
			failed.margins(0, 0, 0, 24);
			empty.margins(0, 0, 0, 24);
			body.add(placeholders).add(list.widget()).add(empty).add(failed);
			root.add(header, body);
		}

		void draw() {
			switch (row.state) {
				case READY:
					list.show(row.items);
					body.show(list.widget());
					break;
				case EMPTY:
					empty.text(row.emptyText != null ? row.emptyText : I18n.t("row_empty"));
					body.show(empty);
					break;
				case FAILED:
					body.show(failed);
					break;
				case LOADING:
				default:
					body.show(placeholders);
					break;
			}
		}
	}
}
