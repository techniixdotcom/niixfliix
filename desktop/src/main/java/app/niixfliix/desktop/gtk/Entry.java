package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;

public final class Entry extends Widget {

	private static final Fn ENTRY_NEW = Fn.of(Lib.GTK, "gtk_entry_new", PTR);
	private static final Fn SEARCH_NEW = Fn.of(Lib.GTK, "gtk_search_entry_new", PTR);
	private static final Fn PASSWORD_NEW = Fn.of(Lib.GTK, "gtk_password_entry_new", PTR);
	private static final Fn PEEK = Fn.of(Lib.GTK, "gtk_password_entry_set_show_peek_icon", null, PTR, INT);
	private static final Fn SEARCH_DELAY = Fn.of(Lib.GTK, "gtk_search_entry_set_search_delay", null, PTR, INT);
	private static final Fn GET_TEXT = Fn.of(Lib.GTK, "gtk_editable_get_text", PTR, PTR);
	private static final Fn SET_TEXT = Fn.of(Lib.GTK, "gtk_editable_set_text", null, PTR, PTR);
	private static final Fn SET_POSITION = Fn.of(Lib.GTK, "gtk_editable_set_position", null, PTR, INT);

	private Entry(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Entry plain(@NonNull String placeholder) {
		Entry e = new Entry(ENTRY_NEW.p());
		e.set("placeholder-text", placeholder);
		return e;
	}

	@NonNull
	public static Entry search(@NonNull String placeholder, int delayMs) {
		Entry e = new Entry(SEARCH_NEW.p());
		e.set("placeholder-text", placeholder);
		SEARCH_DELAY.v(e, delayMs);
		return e;
	}

	@NonNull
	public static Entry password(@NonNull String placeholder) {
		Entry e = new Entry(PASSWORD_NEW.p());
		e.set("placeholder-text", placeholder);
		PEEK.v(e, true);
		return e;
	}

	@NonNull
	public String text() {
		String text = GET_TEXT.s(this);
		return text != null ? text : "";
	}

	public Entry text(@Nullable String text) {
		SET_TEXT.v(this, text != null ? text : "");
		SET_POSITION.v(this, -1);
		return this;
	}

	public Entry onActivate(@NonNull Runnable action) {
		on("activate", action);
		return this;
	}

	public Entry onChanged(@NonNull Runnable action) {
		on("changed", action);
		return this;
	}

	public Entry onSearch(@NonNull Runnable action) {
		on("search-changed", action);
		return this;
	}
}
