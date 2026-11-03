package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;

import app.niixfliix.desktop.platform.Native;

public final class Layers extends Widget {

	private static final int TRANSITION_NONE = 0;
	private static final int TRANSITION_CROSSFADE = 1;

	private static final Fn OVERLAY_NEW = Fn.of(Lib.GTK, "gtk_overlay_new", PTR);
	private static final Fn OVERLAY_CHILD = Fn.of(Lib.GTK, "gtk_overlay_set_child", null, PTR, PTR);
	private static final Fn OVERLAY_ADD = Fn.of(Lib.GTK, "gtk_overlay_add_overlay", null, PTR, PTR);
	private static final Fn OVERLAY_MEASURE = Fn.of(Lib.GTK, "gtk_overlay_set_measure_overlay", null, PTR, PTR, INT);
	private static final Fn STACK_NEW = Fn.of(Lib.GTK, "gtk_stack_new", PTR);
	private static final Fn STACK_ADD = Fn.of(Lib.GTK, "gtk_stack_add_child", PTR, PTR, PTR);
	private static final Fn STACK_REMOVE = Fn.of(Lib.GTK, "gtk_stack_remove", null, PTR, PTR);
	private static final Fn STACK_PAGE = Fn.of(Lib.GTK, "gtk_stack_get_page", PTR, PTR, PTR);
	private static final Fn STACK_SHOW = Fn.of(Lib.GTK, "gtk_stack_set_visible_child", null, PTR, PTR);
	private static final Fn STACK_TRANSITION = Fn.of(Lib.GTK, "gtk_stack_set_transition_type", null, PTR, INT);
	private static final Fn STACK_DURATION = Fn.of(Lib.GTK, "gtk_stack_set_transition_duration", null, PTR, INT);
	private static final Fn STACK_HHOMOGENEOUS = Fn.of(Lib.GTK, "gtk_stack_set_hhomogeneous", null, PTR, INT);
	private static final Fn STACK_VHOMOGENEOUS = Fn.of(Lib.GTK, "gtk_stack_set_vhomogeneous", null, PTR, INT);
	private static final Fn REVEALER_NEW = Fn.of(Lib.GTK, "gtk_revealer_new", PTR);
	private static final Fn REVEALER_CHILD = Fn.of(Lib.GTK, "gtk_revealer_set_child", null, PTR, PTR);
	private static final Fn REVEAL = Fn.of(Lib.GTK, "gtk_revealer_set_reveal_child", null, PTR, INT);
	private static final Fn REVEALED = Fn.of(Lib.GTK, "gtk_revealer_get_reveal_child", INT, PTR);
	private static final Fn REVEALER_TRANSITION = Fn.of(Lib.GTK, "gtk_revealer_set_transition_type", null, PTR, INT);
	private static final Fn REVEALER_DURATION = Fn.of(Lib.GTK, "gtk_revealer_set_transition_duration", null,
			PTR, INT);

	private enum Kind {
		OVERLAY,
		STACK,
		REVEALER
	}

	private final Kind kind;
	@Nullable private Widget shown;

	private Layers(MemorySegment pointer, Kind kind) {
		super(pointer);
		this.kind = kind;
	}

	@NonNull
	public static Layers overlay(@NonNull Widget base, @NonNull Widget... over) {
		Layers l = new Layers(OVERLAY_NEW.p(), Kind.OVERLAY);
		OVERLAY_CHILD.v(l, base);
		for (Widget w : over) {
			l.over(w);
		}
		return l;
	}

	@NonNull
	public static Layers stack(int fadeMs, boolean sameSize) {
		Layers l = new Layers(STACK_NEW.p(), Kind.STACK);
		STACK_TRANSITION.v(l, fadeMs > 0 ? TRANSITION_CROSSFADE : TRANSITION_NONE);
		STACK_DURATION.v(l, fadeMs);
		STACK_HHOMOGENEOUS.v(l, sameSize);
		STACK_VHOMOGENEOUS.v(l, sameSize);
		return l;
	}

	@NonNull
	public static Layers revealer(@NonNull Widget child, int fadeMs) {
		Layers l = new Layers(REVEALER_NEW.p(), Kind.REVEALER);
		REVEALER_TRANSITION.v(l, TRANSITION_CROSSFADE);
		REVEALER_DURATION.v(l, fadeMs);
		REVEALER_CHILD.v(l, child);
		return l;
	}

	public Layers over(@NonNull Widget widget) {
		OVERLAY_ADD.v(this, widget);
		return this;
	}

	public Layers overMeasured(@NonNull Widget widget) {
		over(widget);
		OVERLAY_MEASURE.v(this, widget, true);
		return this;
	}

	public Layers add(@NonNull Widget child) {
		STACK_ADD.p(this, child);
		return this;
	}

	public void remove(@NonNull Widget child) {
		if (shown == child) {
			shown = null;
		}
		STACK_REMOVE.v(this, child);
	}

	public Layers show(@NonNull Widget child) {
		if (kind != Kind.STACK) {
			throw new IllegalStateException("Not a stack");
		}
		if (alive() && child.alive() && Native.isNull(STACK_PAGE.p(this, child))) {
			add(child);
		}
		STACK_SHOW.v(this, child);
		shown = child;
		return this;
	}

	@Nullable
	public Widget shown() {
		return shown;
	}

	public Layers reveal(boolean reveal) {
		REVEAL.v(this, reveal);
		return this;
	}

	public boolean revealed() {
		return REVEALED.b(this);
	}
}
