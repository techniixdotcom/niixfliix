package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.DOUBLE;
import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class Progress extends Widget {

	private static final Fn BAR_NEW = Fn.of(Lib.GTK, "gtk_progress_bar_new", PTR);
	private static final Fn FRACTION = Fn.of(Lib.GTK, "gtk_progress_bar_set_fraction", null, PTR, DOUBLE);
	private static final Fn PULSE = Fn.of(Lib.GTK, "gtk_progress_bar_pulse", null, PTR);
	private static final Fn SPINNER_NEW = Fn.of(Lib.GTK, "gtk_spinner_new", PTR);
	private static final Fn SPINNING = Fn.of(Lib.GTK, "gtk_spinner_set_spinning", null, PTR, INT);

	private final boolean spinner;

	private Progress(MemorySegment pointer, boolean spinner) {
		super(pointer);
		this.spinner = spinner;
	}

	@NonNull
	public static Progress bar() {
		return new Progress(BAR_NEW.p(), false);
	}

	@NonNull
	public static Progress spinner(int size) {
		Progress p = new Progress(SPINNER_NEW.p(), true);
		SPINNING.v(p, true);
		p.size(size, size);
		return p;
	}

	public Progress fraction(double fraction) {
		if (!spinner) {
			FRACTION.v(this, Math.max(0, Math.min(1, fraction)));
		}
		return this;
	}

	public void pulse() {
		if (!spinner) {
			PULSE.v(this);
		}
	}

	@Override
	public Progress visible(boolean visible) {
		super.visible(visible);
		if (spinner) {
			SPINNING.v(this, visible);
		}
		return this;
	}
}
