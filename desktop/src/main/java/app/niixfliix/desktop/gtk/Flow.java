package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class Flow extends Widget {

	private static final int SELECTION_NONE = 0;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_flow_box_new", PTR);
	private static final Fn APPEND = Fn.of(Lib.GTK, "gtk_flow_box_append", null, PTR, PTR);
	private static final Fn REMOVE_ALL = Fn.of(Lib.GTK, "gtk_flow_box_remove_all", null, PTR);
	private static final Fn SELECTION = Fn.of(Lib.GTK, "gtk_flow_box_set_selection_mode", null, PTR, INT);
	private static final Fn COLUMN_SPACING = Fn.of(Lib.GTK, "gtk_flow_box_set_column_spacing", null, PTR, INT);
	private static final Fn ROW_SPACING = Fn.of(Lib.GTK, "gtk_flow_box_set_row_spacing", null, PTR, INT);
	private static final Fn MAX_PER_LINE = Fn.of(Lib.GTK, "gtk_flow_box_set_max_children_per_line", null, PTR, INT);

	private int count;

	private Flow(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Flow create(int spacing) {
		Flow f = new Flow(NEW.p());
		SELECTION.v(f, SELECTION_NONE);
		COLUMN_SPACING.v(f, spacing);
		ROW_SPACING.v(f, spacing);
		MAX_PER_LINE.v(f, 100);
		f.valign(ALIGN_START);
		return f;
	}

	public Flow add(@NonNull Widget child) {
		APPEND.v(this, child);
		count++;
		return this;
	}

	public void clear() {
		REMOVE_ALL.v(this);
		count = 0;
	}

	public boolean isEmpty() {
		return count == 0;
	}
}
