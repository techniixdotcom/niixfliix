package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;

public final class Bars extends Widget {

	private static final Fn TOOLBAR_NEW = Fn.of(Lib.ADW, "adw_toolbar_view_new", PTR);
	private static final Fn SET_CONTENT = Fn.of(Lib.ADW, "adw_toolbar_view_set_content", null, PTR, PTR);
	private static final Fn ADD_TOP = Fn.of(Lib.ADW, "adw_toolbar_view_add_top_bar", null, PTR, PTR);
	private static final Fn ADD_BOTTOM = Fn.of(Lib.ADW, "adw_toolbar_view_add_bottom_bar", null, PTR, PTR);
	private static final Fn HEADER_NEW = Fn.of(Lib.ADW, "adw_header_bar_new", PTR);
	private static final Fn PACK_START = Fn.of(Lib.ADW, "adw_header_bar_pack_start", null, PTR, PTR);
	private static final Fn PACK_END = Fn.of(Lib.ADW, "adw_header_bar_pack_end", null, PTR, PTR);
	private static final Fn SHOW_END_BUTTONS = Fn.of(Lib.ADW, "adw_header_bar_set_show_end_title_buttons", null,
			PTR, INT);
	private static final Fn SHOW_START_BUTTONS = Fn.of(Lib.ADW, "adw_header_bar_set_show_start_title_buttons",
			null, PTR, INT);
	private static final Fn TITLE_WIDGET = Fn.of(Lib.ADW, "adw_header_bar_set_title_widget", null, PTR, PTR);
	private static final Fn HANDLE_NEW = Fn.of(Lib.GTK, "gtk_window_handle_new", PTR);
	private static final Fn HANDLE_CHILD = Fn.of(Lib.GTK, "gtk_window_handle_set_child", null, PTR, PTR);
	private static final Fn CONTROLS_NEW = Fn.of(Lib.GTK, "gtk_window_controls_new", PTR, INT);
	private static final int PACK_END_SIDE = 1;

	private Bars(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Bars of(@Nullable Widget header, @NonNull Widget content, @Nullable Widget bottom) {
		Bars b = new Bars(TOOLBAR_NEW.p());
		if (header != null) {
			ADD_TOP.v(b, header);
		}
		SET_CONTENT.v(b, content);
		if (bottom != null) {
			ADD_BOTTOM.v(b, bottom);
		}
		return b;
	}

	@NonNull
	public static Header header(@Nullable Widget title, boolean windowButtons) {
		Header h = new Header(HEADER_NEW.p());
		if (title != null) {
			TITLE_WIDGET.v(h, title);
		}
		if (!windowButtons) {
			SHOW_START_BUTTONS.v(h, false);
			SHOW_END_BUTTONS.v(h, false);
		}
		return h;
	}

	@NonNull
	public static Widget handle(@NonNull Widget child) {
		Widget h = new Widget(HANDLE_NEW.p());
		HANDLE_CHILD.v(h, child);
		return h;
	}

	@NonNull
	public static Widget windowControls() {
		return new Widget(CONTROLS_NEW.p(PACK_END_SIDE));
	}

	public static final class Header extends Widget {
		Header(MemorySegment pointer) {
			super(pointer);
		}

		public Header start(@NonNull Widget widget) {
			PACK_START.v(this, widget);
			return this;
		}

		public Header end(@NonNull Widget widget) {
			PACK_END.v(this, widget);
			return this;
		}
	}
}
