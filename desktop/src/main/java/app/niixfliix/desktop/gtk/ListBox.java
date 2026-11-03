package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class ListBox extends Widget {

	private static final int SELECTION_NONE = 0;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_list_box_new", PTR);
	private static final Fn APPEND = Fn.of(Lib.GTK, "gtk_list_box_append", null, PTR, PTR);
	private static final Fn REMOVE = Fn.of(Lib.GTK, "gtk_list_box_remove", null, PTR, PTR);
	private static final Fn REMOVE_ALL = Fn.of(Lib.GTK, "gtk_list_box_remove_all", null, PTR);
	private static final Fn SELECTION = Fn.of(Lib.GTK, "gtk_list_box_set_selection_mode", null, PTR, INT);

	private ListBox(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static ListBox create(boolean boxed) {
		ListBox l = new ListBox(NEW.p());
		SELECTION.v(l, SELECTION_NONE);
		if (boxed) {
			l.addClass("boxed-list");
		}
		l.valign(ALIGN_START);
		return l;
	}

	public ListBox add(@NonNull Widget row) {
		APPEND.v(this, row);
		return this;
	}

	public void remove(@NonNull Widget row) {
		REMOVE.v(this, row);
	}

	public void clear() {
		REMOVE_ALL.v(this);
	}
}
