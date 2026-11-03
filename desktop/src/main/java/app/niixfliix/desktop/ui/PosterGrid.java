package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.ListView;
import app.niixfliix.desktop.gtk.MainLoop;
import app.niixfliix.desktop.gtk.Scroller;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.ui.common.PosterItem;

public final class PosterGrid {

	private static final class Line {
		@NonNull final List<PosterItem> posters;
		@Nullable final String heading;

		Line(@NonNull List<PosterItem> posters, @Nullable String heading) {
			this.posters = posters;
			this.heading = heading;
		}

		boolean sameAs(Line other) {
			return posters.equals(other.posters)
					&& (heading == null ? other.heading == null : heading.equals(other.heading));
		}
	}

	private static final int SIDE_PADDING = 24;
	private static final int CARD_GAP = 12;
	private static final int END_MARGIN_ROWS = 2;

	private final ListView<LineRow> list;
	private final Scroller scroll;
	private final PosterCard.Listener listener;
	private List<PosterItem> items = Collections.emptyList();
	private List<Line> lines = new ArrayList<>();
	private int columns = 1;
	@Nullable private Runnable onEnd;
	private boolean endCheckPosted;

	public PosterGrid(@NonNull PosterCard.Listener listener) {
		this.listener = listener;
		list = ListView.create(false, LineRow::new);
		list.addClass("poster-grid");
		scroll = Scroller.vertical(list);
		scroll.vexpand(true).hexpand(true);
		scroll.vadjustment().onChange(this::postEndCheck);
		scroll.hadjustment().onNotify("page-size", () -> MainLoop.post(this::fitColumns));
	}

	private final class LineRow implements ListView.Row {
		final Box root = Box.column(0);
		final Label heading = Label.of(null, "title-4", "grid-heading");
		final Box cards = Box.row(0);
		final List<PosterCard> made = new ArrayList<>();

		LineRow() {
			root.add(heading, cards);
			root.margins(0, SIDE_PADDING, 0, SIDE_PADDING - CARD_GAP / 2);
		}

		@NonNull
		@Override
		public Widget widget() {
			return root;
		}

		@Override
		public void bind(int position) {
			if (position < 0 || position >= lines.size()) {
				return;
			}
			Line line = lines.get(position);
			heading.visible(line.heading != null);
			heading.text(line.heading);
			cards.visible(line.heading == null);
			while (made.size() < line.posters.size()) {
				PosterCard card = new PosterCard(listener);
				made.add(card);
				cards.add(card.widget());
			}
			for (int i = 0; i < made.size(); i++) {
				boolean used = i < line.posters.size();
				made.get(i).widget().visible(used);
				if (used) {
					made.get(i).bind(line.posters.get(i));
				}
			}
		}
	}

	@NonNull
	public Widget view() {
		return scroll;
	}

	public void show(@NonNull List<PosterItem> posters) {
		items = new ArrayList<>(posters);
		layoutLines();
	}

	public void refresh() {
		list.rebindAll();
	}

	public void setOnEnd(@Nullable Runnable action) {
		onEnd = action;
		postEndCheck();
	}

	private void fitColumns() {
		int width = scroll.width();
		if (width <= 0) {
			return;
		}
		int fits = Math.max(1, (width - SIDE_PADDING * 2) / (PosterCard.WIDTH + CARD_GAP));
		if (fits != columns) {
			columns = fits;
			layoutLines();
		}
	}

	private void postEndCheck() {
		if (endCheckPosted || onEnd == null) {
			return;
		}
		endCheckPosted = true;
		MainLoop.post(() -> {
			endCheckPosted = false;
			checkEnd();
		});
	}

	private void checkEnd() {
		Runnable end = onEnd;
		if (end == null || lines.isEmpty() || !scroll.isMapped()) {
			return;
		}
		Scroller.Adjustment v = scroll.vadjustment();
		double margin = END_MARGIN_ROWS * (double) (PosterCard.CARD_HEIGHT + CARD_GAP);
		if (v.value() + v.pageSize() >= v.upper() - margin) {
			end.run();
		}
	}

	public void onShown() {
		fitColumns();
		postEndCheck();
	}

	private void layoutLines() {
		List<Line> next = new ArrayList<>();
		List<PosterItem> current = new ArrayList<>();
		for (PosterItem item : items) {
			if (item.header) {
				if (!current.isEmpty()) {
					next.add(new Line(current, null));
					current = new ArrayList<>();
				}
				next.add(new Line(Collections.emptyList(), item.name));
				continue;
			}
			current.add(item);
			if (current.size() == columns) {
				next.add(new Line(current, null));
				current = new ArrayList<>();
			}
		}
		if (!current.isEmpty()) {
			next.add(new Line(current, null));
		}
		int same = 0;
		while (same < next.size() && same < lines.size() && next.get(same).sameAs(lines.get(same))) {
			same++;
		}
		int removed = lines.size() - same;
		int added = next.size() - same;
		lines = next;
		if (removed > 0 || added > 0) {
			list.changed(same, removed, added);
		}
		postEndCheck();
	}
}
