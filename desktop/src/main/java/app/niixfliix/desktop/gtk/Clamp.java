package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class Clamp extends Widget {

	private static final Fn NEW = Fn.of(Lib.ADW, "adw_clamp_new", PTR);
	private static final Fn SET_CHILD = Fn.of(Lib.ADW, "adw_clamp_set_child", null, PTR, PTR);
	private static final Fn MAXIMUM = Fn.of(Lib.ADW, "adw_clamp_set_maximum_size", null, PTR, INT);
	private static final Fn THRESHOLD = Fn.of(Lib.ADW, "adw_clamp_set_tightening_threshold", null, PTR, INT);

	private Clamp(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Clamp of(@NonNull Widget child, int maxWidth) {
		Clamp c = new Clamp(NEW.p());
		MAXIMUM.v(c, maxWidth);
		THRESHOLD.v(c, maxWidth);
		SET_CHILD.v(c, child);
		return c;
	}
}
