package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.DOUBLE;
import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class Scroller extends Widget {

	private static final int POLICY_AUTOMATIC = 1;
	private static final int POLICY_NEVER = 2;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_scrolled_window_new", PTR);
	private static final Fn SET_CHILD = Fn.of(Lib.GTK, "gtk_scrolled_window_set_child", null, PTR, PTR);
	private static final Fn POLICY = Fn.of(Lib.GTK, "gtk_scrolled_window_set_policy", null, PTR, INT, INT);
	private static final Fn VADJUSTMENT = Fn.of(Lib.GTK, "gtk_scrolled_window_get_vadjustment", PTR, PTR);
	private static final Fn HADJUSTMENT = Fn.of(Lib.GTK, "gtk_scrolled_window_get_hadjustment", PTR, PTR);
	private static final Fn NATURAL_HEIGHT = Fn.of(Lib.GTK, "gtk_scrolled_window_set_propagate_natural_height", null,
			PTR, INT);
	private static final Fn NATURAL_WIDTH = Fn.of(Lib.GTK, "gtk_scrolled_window_set_propagate_natural_width", null,
			PTR, INT);
	private static final Fn MAX_HEIGHT = Fn.of(Lib.GTK, "gtk_scrolled_window_set_max_content_height", null, PTR, INT);

	private Adjustment vertical;
	private Adjustment horizontal;

	private Scroller(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Scroller vertical(@NonNull Widget child) {
		Scroller s = new Scroller(NEW.p());
		POLICY.v(s, POLICY_NEVER, POLICY_AUTOMATIC);
		SET_CHILD.v(s, child);
		return s;
	}

	@NonNull
	public static Scroller horizontal(@NonNull Widget child) {
		Scroller s = new Scroller(NEW.p());
		POLICY.v(s, POLICY_AUTOMATIC, POLICY_NEVER);
		SET_CHILD.v(s, child);
		return s;
	}

	public Scroller fitContent(int maxHeight) {
		NATURAL_WIDTH.v(this, true);
		NATURAL_HEIGHT.v(this, true);
		MAX_HEIGHT.v(this, maxHeight);
		return this;
	}

	@NonNull
	public Adjustment vadjustment() {
		if (vertical == null) {
			vertical = new Adjustment(VADJUSTMENT.p(this));
		}
		return vertical;
	}

	@NonNull
	public Adjustment hadjustment() {
		if (horizontal == null) {
			horizontal = new Adjustment(HADJUSTMENT.p(this));
		}
		return horizontal;
	}

	public static final class Adjustment extends GObj {

		private static final Fn GET_VALUE = Fn.of(Lib.GTK, "gtk_adjustment_get_value", DOUBLE, PTR);
		private static final Fn SET_VALUE = Fn.of(Lib.GTK, "gtk_adjustment_set_value", null, PTR, DOUBLE);
		private static final Fn UPPER = Fn.of(Lib.GTK, "gtk_adjustment_get_upper", DOUBLE, PTR);
		private static final Fn LOWER = Fn.of(Lib.GTK, "gtk_adjustment_get_lower", DOUBLE, PTR);
		private static final Fn PAGE_SIZE = Fn.of(Lib.GTK, "gtk_adjustment_get_page_size", DOUBLE, PTR);

		Adjustment(MemorySegment pointer) {
			super(pointer);
		}

		public double value() {
			return GET_VALUE.d(this);
		}

		public void value(double value) {
			double max = Math.max(lower(), upper() - pageSize());
			SET_VALUE.v(this, Math.max(lower(), Math.min(max, value)));
		}

		public double upper() {
			return UPPER.d(this);
		}

		public double lower() {
			return LOWER.d(this);
		}

		public double pageSize() {
			return PAGE_SIZE.d(this);
		}

		public void onChange(@NonNull Runnable action) {
			on("value-changed", action);
			on("changed", action);
		}
	}
}
