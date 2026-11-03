package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class Label extends Widget {

	private static final int WRAP_WORD_CHAR = 2;
	private static final int ELLIPSIZE_END = 3;
	private static final int JUSTIFY_CENTER = 2;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_label_new", PTR, PTR);
	private static final Fn SET_TEXT = Fn.of(Lib.GTK, "gtk_label_set_text", null, PTR, PTR);
	private static final Fn GET_TEXT = Fn.of(Lib.GTK, "gtk_label_get_text", PTR, PTR);
	private static final Fn WRAP = Fn.of(Lib.GTK, "gtk_label_set_wrap", null, PTR, INT);
	private static final Fn WRAP_MODE = Fn.of(Lib.GTK, "gtk_label_set_wrap_mode", null, PTR, INT);
	private static final Fn XALIGN = Fn.of(Lib.GTK, "gtk_label_set_xalign", null, PTR, ValueLayout.JAVA_FLOAT);
	private static final Fn YALIGN = Fn.of(Lib.GTK, "gtk_label_set_yalign", null, PTR, ValueLayout.JAVA_FLOAT);
	private static final Fn ELLIPSIZE = Fn.of(Lib.GTK, "gtk_label_set_ellipsize", null, PTR, INT);
	private static final Fn LINES = Fn.of(Lib.GTK, "gtk_label_set_lines", null, PTR, INT);
	private static final Fn JUSTIFY = Fn.of(Lib.GTK, "gtk_label_set_justify", null, PTR, INT);
	private static final Fn MAX_WIDTH_CHARS = Fn.of(Lib.GTK, "gtk_label_set_max_width_chars", null, PTR, INT);
	private static final Fn WIDTH_CHARS = Fn.of(Lib.GTK, "gtk_label_set_width_chars", null, PTR, INT);
	private static final Fn SELECTABLE = Fn.of(Lib.GTK, "gtk_label_set_selectable", null, PTR, INT);

	private Label(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Label of(@Nullable String text, @NonNull String... classes) {
		Label label = new Label(NEW.p(text != null ? text : ""));
		XALIGN.v(label, 0f);
		label.addClass(classes);
		return label;
	}

	@NonNull
	public static Label wrapping(@Nullable String text, @NonNull String... classes) {
		return of(text, classes).wrap();
	}

	public Label text(@Nullable String text) {
		SET_TEXT.v(this, text != null ? text : "");
		return this;
	}

	@NonNull
	public String text() {
		String text = GET_TEXT.s(this);
		return text != null ? text : "";
	}

	public Label textOrHide(@Nullable String text) {
		text(text);
		visible(text != null && !text.isEmpty());
		return this;
	}

	public Label wrap() {
		WRAP.v(this, true);
		WRAP_MODE.v(this, WRAP_WORD_CHAR);
		return this;
	}

	public Label lines(int lines) {
		wrap();
		ELLIPSIZE.v(this, ELLIPSIZE_END);
		LINES.v(this, lines);
		return this;
	}

	public Label ellipsize() {
		ELLIPSIZE.v(this, ELLIPSIZE_END);
		return this;
	}

	public Label start() {
		XALIGN.v(this, 0f);
		JUSTIFY.v(this, 0);
		return this;
	}

	public Label center() {
		XALIGN.v(this, 0.5f);
		JUSTIFY.v(this, JUSTIFY_CENTER);
		return this;
	}

	public Label top() {
		YALIGN.v(this, 0f);
		return this;
	}

	public Label maxWidthChars(int chars) {
		MAX_WIDTH_CHARS.v(this, chars);
		return this;
	}

	public Label widthChars(int chars) {
		WIDTH_CHARS.v(this, chars);
		return this;
	}

	public Label selectable() {
		SELECTABLE.v(this, true);
		return this;
	}
}
