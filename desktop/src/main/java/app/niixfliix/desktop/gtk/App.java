package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class App extends GObj {

	private static final int FLAGS_NON_UNIQUE = 1 << 5;

	private static final Fn PRGNAME = Fn.of(Lib.GLIB, "g_set_prgname", null, PTR);
	private static final Fn APP_NAME = Fn.of(Lib.GLIB, "g_set_application_name", null, PTR);
	private static final Fn NEW = Fn.of(Lib.ADW, "adw_application_new", PTR, PTR, INT);
	private static final Fn RUN = Fn.of(Lib.GIO, "g_application_run", INT, PTR, INT, PTR);
	private static final Fn QUIT = Fn.of(Lib.GIO, "g_application_quit", null, PTR);

	private App(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static App create(@NonNull String id, @NonNull String name) {
		MainLoop.bindToCurrentThread();
		PRGNAME.v(id);
		APP_NAME.v(name);
		return new App(NEW.p(id, FLAGS_NON_UNIQUE));
	}

	public App onActivate(@NonNull Runnable action) {
		on("activate", action);
		return this;
	}

	public int run() {
		return RUN.i(this, 0, MemorySegment.NULL);
	}

	public void quit() {
		QUIT.v(this);
	}
}
