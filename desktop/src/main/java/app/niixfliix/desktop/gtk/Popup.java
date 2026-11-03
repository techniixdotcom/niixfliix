package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class Popup {

	public static final int SIDE_RIGHT = 1;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_popover_new", PTR);
	private static final Fn SET_CHILD = Fn.of(Lib.GTK, "gtk_popover_set_child", null, PTR, PTR);
	private static final Fn POINTING_TO = Fn.of(Lib.GTK, "gtk_popover_set_pointing_to", null, PTR, PTR);
	private static final Fn POSITION = Fn.of(Lib.GTK, "gtk_popover_set_position", null, PTR, INT);
	private static final Fn HAS_ARROW = Fn.of(Lib.GTK, "gtk_popover_set_has_arrow", null, PTR, INT);
	private static final Fn AUTOHIDE = Fn.of(Lib.GTK, "gtk_popover_set_autohide", null, PTR, INT);
	private static final Fn POPUP = Fn.of(Lib.GTK, "gtk_popover_popup", null, PTR);
	private static final Fn POPDOWN = Fn.of(Lib.GTK, "gtk_popover_popdown", null, PTR);

	private final Pop popover;
	private boolean open;
	@Nullable private Runnable onClosed;

	public Popup(@NonNull Widget child, @NonNull String... classes) {
		popover = new Pop(NEW.p());
		HAS_ARROW.v(popover, false);
		AUTOHIDE.v(popover, false);
		SET_CHILD.v(popover, child);
		popover.addClass(classes);
	}

	private static final class Pop extends Widget {
		Pop(MemorySegment pointer) {
			super(pointer);
		}
	}

	public Popup onClosed(@NonNull Runnable action) {
		onClosed = action;
		return this;
	}

	public boolean isOpen() {
		return open;
	}

	public void showBeside(@NonNull Widget anchor, int side) {
		if (open || !anchor.alive()) {
			return;
		}
		open = true;
		popover.setParent(anchor);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment rect = arena.allocate(ValueLayout.JAVA_INT, 4);
			rect.setAtIndex(ValueLayout.JAVA_INT, 0, 0);
			rect.setAtIndex(ValueLayout.JAVA_INT, 1, 0);
			rect.setAtIndex(ValueLayout.JAVA_INT, 2, Math.max(1, anchor.width()));
			rect.setAtIndex(ValueLayout.JAVA_INT, 3, Math.max(1, anchor.height()));
			POINTING_TO.v(popover, rect);
		}
		POSITION.v(popover, side);
		popover.on("closed", () -> {
			open = false;
			MainLoop.post(popover::unparent);
			if (onClosed != null) {
				onClosed.run();
			}
		});
		POPUP.v(popover);
	}

	public void close() {
		if (open) {
			POPDOWN.v(popover);
		}
	}
}
