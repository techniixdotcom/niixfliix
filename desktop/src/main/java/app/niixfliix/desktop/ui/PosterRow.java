package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.desktop.gtk.Animation;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.ListView;
import app.niixfliix.desktop.gtk.Scroller;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.ui.common.PosterItem;

public final class PosterRow {

	public static final int HEIGHT = PosterCard.CARD_HEIGHT + 20;

	private static final int CARD_STRIDE = PosterCard.WIDTH + 12;
	private static final int PAGE_MS = 450;
	private static final int FADE_MS = 150;
	private static final int ARROW_SIZE = 44;
	private static final double END_SLACK = 1;

	private final ListView<Cell> list;
	private final Scroller scroll;
	private final Layers root;
	private final Layers backArrow;
	private final Layers forwardArrow;
	@Nullable private final Animation glide;
	private double gliding = -1;
	private boolean pointerInside;
	private List<PosterItem> items = new ArrayList<>();

	public PosterRow(@NonNull PosterCard.Listener listener) {
		list = ListView.create(true, () -> new Cell(listener));
		list.addClass("poster-row");
		scroll = Scroller.horizontal(list);
		scroll.size(-1, HEIGHT);
		backArrow = arrow("chevron_left", I18n.t("row_scroll_back"), -1);
		forwardArrow = arrow("chevron_right", I18n.t("row_scroll_forward"), 1);
		backArrow.halign(Widget.ALIGN_START);
		forwardArrow.halign(Widget.ALIGN_END);
		root = Layers.overlay(scroll, backArrow, forwardArrow);
		glide = Animation.of(scroll, scroll.hadjustment(), "value", PAGE_MS);

		Controller pointer = Controller.motion();
		pointer.onEnter((x, y) -> {
			pointerInside = !pointer.isTouch();
			updateArrows();
		});
		pointer.onLeave(() -> {
			pointerInside = false;
			updateArrows();
		});
		root.addController(pointer);
		scroll.hadjustment().onChange(this::updateArrows);
	}

	private Layers arrow(String icon, String label, int direction) {
		Button button = Button.icon(Icons.name(icon), label);
		button.addClass("osd", "circular", "row-arrow");
		button.focusable(false);
		button.onClick(() -> page(direction));
		Layers fade = Layers.revealer(button, FADE_MS);
		fade.valign(Widget.ALIGN_START).margins(6 + (PosterCard.HEIGHT - ARROW_SIZE) / 2, 10, 0, 10);
		return fade;
	}

	private void page(int direction) {
		Scroller.Adjustment h = scroll.hadjustment();
		double cards = Math.floor(h.pageSize() / CARD_STRIDE);
		double step = Math.max(1, cards - 1) * CARD_STRIDE;
		double now = h.value();
		boolean sameWay = gliding >= 0 && Math.signum(gliding - now) == direction;
		double from = sameWay ? gliding : now;
		double max = Math.max(h.lower(), h.upper() - h.pageSize());
		double to = Math.max(h.lower(), Math.min(max, from + direction * step));
		if (glide == null) {
			h.value(to);
			return;
		}
		gliding = to;
		glide.play(now, to);
	}

	private void updateArrows() {
		Scroller.Adjustment h = scroll.hadjustment();
		double value = h.value();
		if (gliding >= 0 && Math.abs(value - gliding) < END_SLACK) {
			gliding = -1;
		}
		double max = h.upper() - h.pageSize();
		boolean back = pointerInside && value > h.lower() + END_SLACK;
		boolean forward = pointerInside && value < max - END_SLACK;
		if (backArrow.revealed() != back) {
			backArrow.reveal(back);
		}
		if (forwardArrow.revealed() != forward) {
			forwardArrow.reveal(forward);
		}
	}

	private final class Cell implements ListView.Row {
		final PosterCard card;

		Cell(PosterCard.Listener listener) {
			card = new PosterCard(listener);
		}

		@NonNull
		@Override
		public Widget widget() {
			return card.widget();
		}

		@Override
		public void bind(int position) {
			if (position >= 0 && position < items.size()) {
				card.bind(items.get(position));
			}
		}
	}

	@NonNull
	public Widget widget() {
		return root;
	}

	public void show(@NonNull List<PosterItem> newItems) {
		boolean same = newItems.equals(items);
		items = new ArrayList<>(newItems);
		if (same) {
			list.rebindAll();
		} else {
			list.replaceAll(items.size());
		}
	}

	public void refresh() {
		list.rebindAll();
	}

	public void passWheelTo(@NonNull Scroller page) {
		Controller wheel = Controller.scroll(Controller.SCROLL_VERTICAL | Controller.SCROLL_HORIZONTAL);
		wheel.phase(Controller.PHASE_CAPTURE);
		wheel.onScroll((dx, dy) -> {
			if ((wheel.modifiers() & Controller.MODIFIER_SHIFT) != 0 || Math.abs(dx) >= Math.abs(dy)) {
				return false;
			}
			Scroller.Adjustment v = page.vadjustment();
			double step = wheel.scrollUnit() == Controller.SCROLL_UNIT_WHEEL
					? Math.pow(Math.max(1, v.pageSize()), 2.0 / 3.0) : 1;
			v.value(v.value() + dy * step);
			return true;
		});
		scroll.addController(wheel);
	}

	/**
	 * A mouse wheel turned over the row scrolls the row sideways, a card per notch; once the row is at its
	 * end that way, the wheel scrolls {@code page} instead, so the page never gets stuck on the row. A
	 * touchpad keeps scrolling the page up and down and the row with a sideways swipe.
	 */
	public void wheelScrollsRow(@NonNull Scroller page) {
		Controller wheel = Controller.scroll(Controller.SCROLL_VERTICAL | Controller.SCROLL_HORIZONTAL);
		wheel.phase(Controller.PHASE_CAPTURE);
		wheel.onScroll((dx, dy) -> {
			if ((wheel.modifiers() & Controller.MODIFIER_SHIFT) != 0 || Math.abs(dx) >= Math.abs(dy)) {
				return false;
			}
			if (wheel.scrollUnit() == Controller.SCROLL_UNIT_WHEEL && slide(dy)) {
				return true;
			}
			Scroller.Adjustment v = page.vadjustment();
			double step = wheel.scrollUnit() == Controller.SCROLL_UNIT_WHEEL
					? Math.pow(Math.max(1, v.pageSize()), 2.0 / 3.0) : 1;
			v.value(v.value() + dy * step);
			return true;
		});
		scroll.addController(wheel);
	}

	/** Moves the row {@code notches} cards along; false when it's already at its end that way. */
	private boolean slide(double notches) {
		Scroller.Adjustment h = scroll.hadjustment();
		double now = h.value();
		double max = Math.max(h.lower(), h.upper() - h.pageSize());
		double from = gliding >= 0 ? gliding : now;
		if (notches > 0 ? from >= max - END_SLACK : from <= h.lower() + END_SLACK) {
			return false;
		}
		double to = Math.max(h.lower(), Math.min(max, from + notches * CARD_STRIDE));
		if (glide == null) {
			h.value(to);
			return true;
		}
		gliding = to;
		glide.play(now, to);
		return true;
	}
}
