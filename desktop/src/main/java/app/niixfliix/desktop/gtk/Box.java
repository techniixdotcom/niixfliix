package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

import app.niixfliix.desktop.platform.Native;

public final class Box extends Widget {

	private static final int HORIZONTAL = 0;
	private static final int VERTICAL = 1;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_box_new", PTR, INT, INT);
	private static final Fn APPEND = Fn.of(Lib.GTK, "gtk_box_append", null, PTR, PTR);
	private static final Fn PREPEND = Fn.of(Lib.GTK, "gtk_box_prepend", null, PTR, PTR);
	private static final Fn REMOVE = Fn.of(Lib.GTK, "gtk_box_remove", null, PTR, PTR);
	private static final Fn HOMOGENEOUS = Fn.of(Lib.GTK, "gtk_box_set_homogeneous", null, PTR, INT);
	private static final Fn ORIENTATION = Fn.of(Lib.GTK, "gtk_orientable_set_orientation", null, PTR, INT);
	private static final Fn SPACING = Fn.of(Lib.GTK, "gtk_box_set_spacing", null, PTR, INT);
	private static final Fn FIRST_CHILD = Fn.of(Lib.GTK, "gtk_widget_get_first_child", PTR, PTR);

	private Box(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Box row(int spacing, @NonNull Widget... children) {
		return new Box(NEW.p(HORIZONTAL, spacing)).add(children);
	}

	@NonNull
	public static Box column(int spacing, @NonNull Widget... children) {
		return new Box(NEW.p(VERTICAL, spacing)).add(children);
	}

	public Box add(@NonNull Widget... children) {
		for (Widget child : children) {
			APPEND.v(this, child);
		}
		return this;
	}

	public Box addFirst(@NonNull Widget child) {
		PREPEND.v(this, child);
		return this;
	}

	public void remove(@NonNull Widget child) {
		REMOVE.v(this, child);
	}

	public void clear() {
		if (!alive()) {
			return;
		}
		MemorySegment child;
		while (!Native.isNull(child = FIRST_CHILD.p(this))) {
			REMOVE.v(this, child);
		}
	}

	public Box vertical(boolean vertical, int spacing) {
		ORIENTATION.v(this, vertical ? VERTICAL : HORIZONTAL);
		SPACING.v(this, spacing);
		return this;
	}

	public Box homogeneous(boolean same) {
		HOMOGENEOUS.v(this, same);
		return this;
	}
}
