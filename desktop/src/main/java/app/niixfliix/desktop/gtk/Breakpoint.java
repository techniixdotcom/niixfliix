package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

import app.niixfliix.desktop.platform.Native;

public final class Breakpoint extends GObj {

	private static final Fn PARSE = Fn.of(Lib.ADW, "adw_breakpoint_condition_parse", PTR, PTR);
	private static final Fn NEW = Fn.of(Lib.ADW, "adw_breakpoint_new", PTR, PTR);
	private static final Fn ADD = Fn.of(Lib.ADW, "adw_application_window_add_breakpoint", null, PTR, PTR);

	private Breakpoint(MemorySegment pointer) {
		super(pointer);
	}

	public static boolean supported() {
		return PARSE.exists() && NEW.exists() && ADD.exists();
	}

	public static boolean add(@NonNull Window window, @NonNull String condition, @NonNull Runnable apply,
			@NonNull Runnable unapply) {
		if (!supported()) {
			return false;
		}
		MemorySegment parsed = PARSE.p(condition);
		if (Native.isNull(parsed)) {
			return false;
		}
		Breakpoint b = new Breakpoint(NEW.p(parsed));
		b.on("apply", apply);
		b.on("unapply", unapply);
		ADD.v(window, b);
		return true;
	}
}
