package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.DOUBLE;
import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;
import java.util.function.DoubleConsumer;

public final class Scale extends Widget {

	private static final int HORIZONTAL = 0;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_scale_new_with_range", PTR, INT, DOUBLE, DOUBLE, DOUBLE);
	private static final Fn DRAW_VALUE = Fn.of(Lib.GTK, "gtk_scale_set_draw_value", null, PTR, INT);
	private static final Fn SET_VALUE = Fn.of(Lib.GTK, "gtk_range_set_value", null, PTR, DOUBLE);
	private static final Fn GET_VALUE = Fn.of(Lib.GTK, "gtk_range_get_value", DOUBLE, PTR);
	private static final Fn SET_RANGE = Fn.of(Lib.GTK, "gtk_range_set_range", null, PTR, DOUBLE, DOUBLE);
	private static final Fn INCREMENTS = Fn.of(Lib.GTK, "gtk_range_set_increments", null, PTR, DOUBLE, DOUBLE);

	private Scale(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Scale horizontal(double min, double max, double step) {
		Scale s = new Scale(NEW.p(HORIZONTAL, min, max, step));
		DRAW_VALUE.v(s, false);
		return s;
	}

	public Scale value(double value) {
		SET_VALUE.v(this, value);
		return this;
	}

	public double value() {
		return GET_VALUE.d(this);
	}

	public Scale range(double min, double max) {
		SET_RANGE.v(this, min, max);
		return this;
	}

	public Scale steps(double step, double page) {
		INCREMENTS.v(this, step, page);
		return this;
	}

	public Scale onUserChange(@NonNull DoubleConsumer action) {
		Signals.connect(this, "change-value", Signals.Sig.CHANGE_VALUE, (Signals.ChangeValue) (type, value) -> {
			action.accept(value);
			return false;
		});
		return this;
	}

	public Scale onChanged(@NonNull Runnable action) {
		on("value-changed", action);
		return this;
	}
}
