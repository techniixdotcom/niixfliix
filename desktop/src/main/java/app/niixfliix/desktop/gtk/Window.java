package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.DOUBLE;
import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import app.niixfliix.desktop.platform.Native;

public final class Window extends Widget {

	private static final Fn NEW = Fn.of(Lib.ADW, "adw_application_window_new", PTR, PTR);
	private static final Fn SET_CONTENT = Fn.of(Lib.ADW, "adw_application_window_set_content", null, PTR, PTR);
	private static final Fn TITLE = Fn.of(Lib.GTK, "gtk_window_set_title", null, PTR, PTR);
	private static final Fn DEFAULT_SIZE = Fn.of(Lib.GTK, "gtk_window_set_default_size", null, PTR, INT, INT);
	private static final Fn GET_DEFAULT_SIZE = Fn.of(Lib.GTK, "gtk_window_get_default_size", null, PTR, PTR, PTR);
	private static final Fn MAXIMIZE = Fn.of(Lib.GTK, "gtk_window_maximize", null, PTR);
	private static final Fn IS_MAXIMIZED = Fn.of(Lib.GTK, "gtk_window_is_maximized", INT, PTR);
	private static final Fn FULLSCREEN = Fn.of(Lib.GTK, "gtk_window_fullscreen", null, PTR);
	private static final Fn UNFULLSCREEN = Fn.of(Lib.GTK, "gtk_window_unfullscreen", null, PTR);
	private static final Fn IS_FULLSCREEN = Fn.of(Lib.GTK, "gtk_window_is_fullscreen", INT, PTR);
	private static final Fn PRESENT = Fn.of(Lib.GTK, "gtk_window_present", null, PTR);
	private static final Fn DESTROY = Fn.of(Lib.GTK, "gtk_window_destroy", null, PTR);
	private static final Fn ICON = Fn.of(Lib.GTK, "gtk_window_set_icon_name", null, PTR, PTR);
	private static final Fn DEFAULT_ICON = Fn.of(Lib.GTK, "gtk_window_set_default_icon_name", null, PTR);
	private static final Fn NATIVE_SURFACE = Fn.of(Lib.GTK, "gtk_native_get_surface", PTR, PTR);
	private static final Fn SURFACE_SCALE = Fn.of(Lib.GTK, "gdk_surface_get_scale", DOUBLE, PTR);

	private Window(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Window create(@NonNull App app, @NonNull String title, @NonNull String iconName) {
		Window w = new Window(NEW.p(app));
		TITLE.v(w, title);
		DEFAULT_ICON.v(iconName);
		ICON.v(w, iconName);
		return w;
	}

	public Window content(@NonNull Widget content) {
		SET_CONTENT.v(this, content);
		return this;
	}

	public Window defaultSize(int width, int height) {
		DEFAULT_SIZE.v(this, width, height);
		return this;
	}

	@NonNull
	public int[] defaultSize() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment size = arena.allocate(ValueLayout.JAVA_INT, 2);
			GET_DEFAULT_SIZE.v(this, size, size.asSlice(4));
			return new int[] {size.get(ValueLayout.JAVA_INT, 0), size.get(ValueLayout.JAVA_INT, 4)};
		}
	}

	public void maximize() {
		MAXIMIZE.v(this);
	}

	public boolean isMaximized() {
		return IS_MAXIMIZED.b(this);
	}

	public void fullscreen(boolean on) {
		if (on) {
			FULLSCREEN.v(this);
		} else {
			UNFULLSCREEN.v(this);
		}
	}

	public boolean isFullscreen() {
		return IS_FULLSCREEN.b(this);
	}

	public void present() {
		PRESENT.v(this);
	}

	public void destroy() {
		DESTROY.v(this);
	}

	public void onCloseRequest(@NonNull Signals.Bool handler) {
		onBool("close-request", handler);
	}

	public void onFullscreenChanged(@NonNull Runnable action) {
		onNotify("fullscreened", action);
	}

	public double scale() {
		if (alive() && SURFACE_SCALE.exists()) {
			MemorySegment surface = NATIVE_SURFACE.p(this);
			if (!Native.isNull(surface)) {
				return SURFACE_SCALE.d(surface);
			}
		}
		return scaleFactor();
	}

	public void onScaleChanged(@NonNull Runnable action) {
		onNotify("scale-factor", action);
	}
}
