package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class Toasts extends Widget {

	private static final int TIMEOUT_S = 3;

	private static final Fn NEW = Fn.of(Lib.ADW, "adw_toast_overlay_new", PTR);
	private static final Fn SET_CHILD = Fn.of(Lib.ADW, "adw_toast_overlay_set_child", null, PTR, PTR);
	private static final Fn ADD_TOAST = Fn.of(Lib.ADW, "adw_toast_overlay_add_toast", null, PTR, PTR);
	private static final Fn TOAST_NEW = Fn.of(Lib.ADW, "adw_toast_new", PTR, PTR);
	private static final Fn TOAST_TIMEOUT = Fn.of(Lib.ADW, "adw_toast_set_timeout", null, PTR, INT);
	private static final Fn TOAST_MARKUP = Fn.of(Lib.ADW, "adw_toast_set_use_markup", null, PTR, INT);
	private static final Fn TOAST_TITLE = Fn.of(Lib.ADW, "adw_toast_set_title", null, PTR, PTR);

	private Toasts(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Toasts around(@NonNull Widget child) {
		Toasts t = new Toasts(NEW.p());
		SET_CHILD.v(t, child);
		return t;
	}

	public void show(@NonNull String text) {
		if (!alive()) {
			return;
		}
		MemorySegment toast = TOAST_NEW.p("");
		TOAST_MARKUP.v(toast, false);
		TOAST_TITLE.v(toast, text);
		TOAST_TIMEOUT.v(toast, TIMEOUT_S);
		ADD_TOAST.v(this, toast);
	}
}
