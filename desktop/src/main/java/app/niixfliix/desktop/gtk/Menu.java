package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class Menu {

	private static final int POSITION_TOP = 2;
	private static final int POSITION_BOTTOM = 3;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_popover_new", PTR);
	private static final Fn SET_CHILD = Fn.of(Lib.GTK, "gtk_popover_set_child", null, PTR, PTR);
	private static final Fn POINTING_TO = Fn.of(Lib.GTK, "gtk_popover_set_pointing_to", null, PTR, PTR);
	private static final Fn POSITION = Fn.of(Lib.GTK, "gtk_popover_set_position", null, PTR, INT);
	private static final Fn HAS_ARROW = Fn.of(Lib.GTK, "gtk_popover_set_has_arrow", null, PTR, INT);
	private static final Fn POPUP = Fn.of(Lib.GTK, "gtk_popover_popup", null, PTR);
	private static final Fn POPDOWN = Fn.of(Lib.GTK, "gtk_popover_popdown", null, PTR);

	private final Popover popover;
	private final Box items = Box.column(0);
	private boolean empty = true;
	@Nullable private Runnable onClosed;

	public Menu() {
		popover = new Popover(NEW.p());
		HAS_ARROW.v(popover, false);
		items.addClass("menu-items");
		Scroller scroll = Scroller.vertical(items).fitContent(480);
		SET_CHILD.v(popover, scroll);
		popover.addClass("menu");
	}

	private static final class Popover extends Widget {
		Popover(MemorySegment pointer) {
			super(pointer);
		}
	}

	public Menu item(@NonNull String label, @NonNull Runnable action) {
		return add(label, null, action);
	}

	public Menu choice(@NonNull String label, boolean checked, @NonNull Runnable action) {
		return add(label, checked ? Icons.CHECK : "", action);
	}

	public Menu note(@NonNull String text) {
		Label label = Label.of(text, "dim-label");
		label.margins(6, 12, 6, 12);
		items.add(label);
		empty = false;
		return this;
	}

	public Menu separator() {
		if (!empty) {
			items.add(Box.column(0).addClass("menu-separator"));
		}
		return this;
	}

	private Menu add(String text, @Nullable String tick, Runnable action) {
		Label label = Label.of(text).ellipsize().maxWidthChars(48);
		Box row = Box.row(8);
		if (tick != null) {
			row.add(tick.isEmpty() ? Box.row(0).size(16, 16) : Image.icon(Icons.name(tick), 16));
		}
		row.add(label);
		Button button = Button.wrapping(row, "flat", "menu-item");
		button.onClick(() -> {
			POPDOWN.v(popover);
			action.run();
		});
		items.add(button);
		empty = false;
		return this;
	}

	public boolean isEmpty() {
		return empty;
	}

	public Menu onClosed(@NonNull Runnable action) {
		onClosed = action;
		return this;
	}

	public void showAt(@NonNull Widget anchor, double x, double y) {
		show(anchor, (int) Math.round(x), (int) Math.round(y), 1, 1, POSITION_BOTTOM);
	}

	public void showBelow(@NonNull Widget anchor) {
		show(anchor, 0, 0, anchor.width(), anchor.height(), POSITION_BOTTOM);
	}

	public void showAbove(@NonNull Widget anchor) {
		show(anchor, 0, 0, anchor.width(), anchor.height(), POSITION_TOP);
	}

	private void show(Widget anchor, int x, int y, int width, int height, int position) {
		if (!anchor.alive()) {
			return;
		}
		popover.setParent(anchor);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment rect = arena.allocate(ValueLayout.JAVA_INT, 4);
			rect.setAtIndex(ValueLayout.JAVA_INT, 0, x);
			rect.setAtIndex(ValueLayout.JAVA_INT, 1, y);
			rect.setAtIndex(ValueLayout.JAVA_INT, 2, Math.max(1, width));
			rect.setAtIndex(ValueLayout.JAVA_INT, 3, Math.max(1, height));
			POINTING_TO.v(popover, rect);
		}
		POSITION.v(popover, position);
		boolean hadTooltip = anchor.hasTooltip();
		if (hadTooltip) {
			anchor.hasTooltip(false);
		}
		popover.on("closed", () -> {
			if (hadTooltip && anchor.alive()) {
				anchor.hasTooltip(true);
			}
			MainLoop.post(popover::unparent);
			if (onClosed != null) {
				onClosed.run();
			}
		});
		POPUP.v(popover);
	}

	public void close() {
		POPDOWN.v(popover);
	}
}
