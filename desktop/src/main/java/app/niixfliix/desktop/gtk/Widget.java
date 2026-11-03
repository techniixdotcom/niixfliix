package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.DOUBLE;
import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import app.niixfliix.desktop.platform.Native;

public class Widget extends GObj {

	public static final int ALIGN_FILL = 0;
	public static final int ALIGN_START = 1;
	public static final int ALIGN_END = 2;
	public static final int ALIGN_CENTER = 3;

	private static final int STATE_PRELIGHT = 1 << 1;
	private static final int OVERFLOW_HIDDEN = 1;
	private static final int ACCESSIBLE_PROPERTY_LABEL = 8;

	private static final Fn SET_VISIBLE = Fn.of(Lib.GTK, "gtk_widget_set_visible", null, PTR, INT);
	private static final Fn GET_VISIBLE = Fn.of(Lib.GTK, "gtk_widget_get_visible", INT, PTR);
	private static final Fn ADD_CLASS = Fn.of(Lib.GTK, "gtk_widget_add_css_class", null, PTR, PTR);
	private static final Fn REMOVE_CLASS = Fn.of(Lib.GTK, "gtk_widget_remove_css_class", null, PTR, PTR);
	private static final Fn HEXPAND = Fn.of(Lib.GTK, "gtk_widget_set_hexpand", null, PTR, INT);
	private static final Fn VEXPAND = Fn.of(Lib.GTK, "gtk_widget_set_vexpand", null, PTR, INT);
	private static final Fn HALIGN = Fn.of(Lib.GTK, "gtk_widget_set_halign", null, PTR, INT);
	private static final Fn VALIGN = Fn.of(Lib.GTK, "gtk_widget_set_valign", null, PTR, INT);
	private static final Fn MARGIN_TOP = Fn.of(Lib.GTK, "gtk_widget_set_margin_top", null, PTR, INT);
	private static final Fn MARGIN_END = Fn.of(Lib.GTK, "gtk_widget_set_margin_end", null, PTR, INT);
	private static final Fn MARGIN_BOTTOM = Fn.of(Lib.GTK, "gtk_widget_set_margin_bottom", null, PTR, INT);
	private static final Fn MARGIN_START = Fn.of(Lib.GTK, "gtk_widget_set_margin_start", null, PTR, INT);
	private static final Fn SIZE_REQUEST = Fn.of(Lib.GTK, "gtk_widget_set_size_request", null, PTR, INT, INT);
	private static final Fn TOOLTIP = Fn.of(Lib.GTK, "gtk_widget_set_tooltip_text", null, PTR, PTR);
	private static final Fn SET_HAS_TOOLTIP = Fn.of(Lib.GTK, "gtk_widget_set_has_tooltip", null, PTR, INT);
	private static final Fn GET_HAS_TOOLTIP = Fn.of(Lib.GTK, "gtk_widget_get_has_tooltip", INT, PTR);
	private static final Fn SENSITIVE = Fn.of(Lib.GTK, "gtk_widget_set_sensitive", null, PTR, INT);
	private static final Fn FOCUSABLE = Fn.of(Lib.GTK, "gtk_widget_set_focusable", null, PTR, INT);
	private static final Fn CAN_FOCUS = Fn.of(Lib.GTK, "gtk_widget_set_can_focus", null, PTR, INT);
	private static final Fn GRAB_FOCUS = Fn.of(Lib.GTK, "gtk_widget_grab_focus", INT, PTR);
	private static final Fn CURSOR = Fn.of(Lib.GTK, "gtk_widget_set_cursor_from_name", null, PTR, PTR);
	private static final Fn OPACITY = Fn.of(Lib.GTK, "gtk_widget_set_opacity", null, PTR, DOUBLE);
	private static final Fn WIDTH = Fn.of(Lib.GTK, "gtk_widget_get_width", INT, PTR);
	private static final Fn HEIGHT = Fn.of(Lib.GTK, "gtk_widget_get_height", INT, PTR);
	private static final Fn CAN_TARGET = Fn.of(Lib.GTK, "gtk_widget_set_can_target", null, PTR, INT);
	private static final Fn ADD_CONTROLLER = Fn.of(Lib.GTK, "gtk_widget_add_controller", null, PTR, PTR);
	private static final Fn STATE_FLAGS = Fn.of(Lib.GTK, "gtk_widget_get_state_flags", INT, PTR);
	private static final Fn OVERFLOW = Fn.of(Lib.GTK, "gtk_widget_set_overflow", null, PTR, INT);
	private static final Fn SCALE_FACTOR = Fn.of(Lib.GTK, "gtk_widget_get_scale_factor", INT, PTR);
	private static final Fn COMPUTE_BOUNDS = Fn.of(Lib.GTK, "gtk_widget_compute_bounds", INT, PTR, PTR, PTR);
	private static final Fn UNPARENT = Fn.of(Lib.GTK, "gtk_widget_unparent", null, PTR);
	private static final Fn SET_PARENT = Fn.of(Lib.GTK, "gtk_widget_set_parent", null, PTR, PTR);
	private static final Fn MAPPED = Fn.of(Lib.GTK, "gtk_widget_get_mapped", INT, PTR);
	private static final Fn IS_ANCESTOR = Fn.of(Lib.GTK, "gtk_widget_is_ancestor", INT, PTR, PTR);
	private static final Fn GET_FOCUS_CHILD = Fn.of(Lib.GTK, "gtk_widget_get_focus_child", PTR, PTR);
	private static final Fn ACCESSIBLE_LABEL = Fn.variadic(Lib.GTK, "gtk_accessible_update_property", 2, null,
			PTR, INT, PTR, INT);
	private static final Fn CLIPBOARD = Fn.of(Lib.GTK, "gtk_widget_get_clipboard", PTR, PTR);
	private static final Fn SET_TEXT = Fn.of(Lib.GTK, "gdk_clipboard_set_text", null, PTR, PTR);
	private static final Fn NAME = Fn.of(Lib.GTK, "gtk_widget_set_name", null, PTR, PTR);

	protected Widget(@NonNull MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Widget spacer() {
		return Box.row(0).hexpand(true);
	}

	public Widget visible(boolean visible) {
		SET_VISIBLE.v(this, visible);
		return this;
	}

	public final boolean isVisible() {
		return GET_VISIBLE.b(this);
	}

	public final boolean isMapped() {
		return MAPPED.b(this);
	}

	public Widget addClass(@NonNull String... classes) {
		for (String c : classes) {
			ADD_CLASS.v(this, c);
		}
		return this;
	}

	public Widget removeClass(@NonNull String name) {
		REMOVE_CLASS.v(this, name);
		return this;
	}

	public Widget toggleClass(@NonNull String name, boolean on) {
		return on ? addClass(name) : removeClass(name);
	}

	public Widget hexpand(boolean expand) {
		HEXPAND.v(this, expand);
		return this;
	}

	public Widget vexpand(boolean expand) {
		VEXPAND.v(this, expand);
		return this;
	}

	public Widget halign(int align) {
		HALIGN.v(this, align);
		return this;
	}

	public Widget valign(int align) {
		VALIGN.v(this, align);
		return this;
	}

	public Widget margins(int top, int end, int bottom, int start) {
		MARGIN_TOP.v(this, top);
		MARGIN_END.v(this, end);
		MARGIN_BOTTOM.v(this, bottom);
		MARGIN_START.v(this, start);
		return this;
	}

	public Widget margin(int all) {
		return margins(all, all, all, all);
	}

	public Widget size(int width, int height) {
		SIZE_REQUEST.v(this, width, height);
		return this;
	}

	public Widget hasTooltip(boolean has) {
		SET_HAS_TOOLTIP.v(this, has);
		return this;
	}

	public boolean hasTooltip() {
		return GET_HAS_TOOLTIP.b(this);
	}

	public Widget tooltip(@Nullable String text) {
		TOOLTIP.v(this, text);
		return this;
	}

	public Widget sensitive(boolean sensitive) {
		SENSITIVE.v(this, sensitive);
		return this;
	}

	public Widget focusable(boolean focusable) {
		FOCUSABLE.v(this, focusable);
		return this;
	}

	public Widget canFocus(boolean canFocus) {
		CAN_FOCUS.v(this, canFocus);
		return this;
	}

	public void grabFocus() {
		GRAB_FOCUS.i(this);
	}

	public Widget cursor(@Nullable String name) {
		CURSOR.v(this, name);
		return this;
	}

	public Widget opacity(double opacity) {
		OPACITY.v(this, opacity);
		return this;
	}

	public Widget canTarget(boolean canTarget) {
		CAN_TARGET.v(this, canTarget);
		return this;
	}

	public Widget clipChildren() {
		OVERFLOW.v(this, OVERFLOW_HIDDEN);
		return this;
	}

	public Widget name(@NonNull String name) {
		NAME.v(this, name);
		return this;
	}

	public final int width() {
		return WIDTH.i(this);
	}

	public final int height() {
		return HEIGHT.i(this);
	}

	public final int scaleFactor() {
		return Math.max(1, SCALE_FACTOR.i(this));
	}

	public final boolean isHovered() {
		return (STATE_FLAGS.i(this) & STATE_PRELIGHT) != 0;
	}

	public final boolean hasFocusInside() {
		return !Native.isNull(GET_FOCUS_CHILD.p(this));
	}

	public final boolean isInside(@NonNull Widget ancestor) {
		return IS_ANCESTOR.b(this, ancestor);
	}

	@Nullable
	public final float[] boundsIn(@NonNull Widget other) {
		if (!alive() || !other.alive()) {
			return null;
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment rect = arena.allocate(ValueLayout.JAVA_FLOAT, 4);
			if (!COMPUTE_BOUNDS.b(this, other, rect)) {
				return null;
			}
			return rect.toArray(ValueLayout.JAVA_FLOAT);
		}
	}

	public final void addController(@NonNull Controller controller) {
		ADD_CONTROLLER.v(this, controller);
	}

	final void setParent(@NonNull Widget parent) {
		SET_PARENT.v(this, parent);
	}

	final void unparent() {
		UNPARENT.v(this);
	}

	public Widget accessibleLabel(@NonNull String text) {
		ACCESSIBLE_LABEL.v(this, ACCESSIBLE_PROPERTY_LABEL, text, -1);
		return this;
	}

	public final void copyText(@NonNull String text) {
		MemorySegment clipboard = CLIPBOARD.p(this);
		if (!Native.isNull(clipboard)) {
			SET_TEXT.v(clipboard, text);
		}
	}
}
