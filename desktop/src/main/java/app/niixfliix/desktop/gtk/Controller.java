package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

import app.niixfliix.desktop.platform.Native;

public final class Controller extends GObj {

	public static final int PHASE_CAPTURE = 1;
	public static final int PHASE_BUBBLE = 2;

	public static final int MODIFIER_SHIFT = 1;
	public static final int MODIFIER_CONTROL = 1 << 2;
	public static final int MODIFIER_ALT = 1 << 3;
	public static final int MODIFIER_SUPER = 1 << 26;
	public static final int MODIFIER_META = 1 << 28;

	public static final int BUTTON_ANY = 0;
	public static final int BUTTON_PRIMARY = 1;
	public static final int BUTTON_MIDDLE = 2;
	public static final int BUTTON_SECONDARY = 3;
	public static final int BUTTON_BACK = 8;

	public static final int SCROLL_VERTICAL = 1;
	public static final int SCROLL_HORIZONTAL = 2;
	public static final int SCROLL_UNIT_WHEEL = 0;

	private static final Fn CLICK_NEW = Fn.of(Lib.GTK, "gtk_gesture_click_new", PTR);
	private static final Fn SET_BUTTON = Fn.of(Lib.GTK, "gtk_gesture_single_set_button", null, PTR, INT);
	private static final Fn CURRENT_BUTTON = Fn.of(Lib.GTK, "gtk_gesture_single_get_current_button", INT, PTR);
	private static final Fn KEY_NEW = Fn.of(Lib.GTK, "gtk_event_controller_key_new", PTR);
	private static final Fn SCROLL_NEW = Fn.of(Lib.GTK, "gtk_event_controller_scroll_new", PTR, INT);
	private static final Fn SCROLL_UNIT = Fn.of(Lib.GTK, "gtk_event_controller_scroll_get_unit", INT, PTR);
	private static final Fn MOTION_NEW = Fn.of(Lib.GTK, "gtk_event_controller_motion_new", PTR);
	private static final Fn FOCUS_NEW = Fn.of(Lib.GTK, "gtk_event_controller_focus_new", PTR);
	private static final Fn PHASE = Fn.of(Lib.GTK, "gtk_event_controller_set_propagation_phase", null, PTR, INT);
	private static final Fn STATE = Fn.of(Lib.GTK, "gtk_event_controller_get_current_event_state", INT, PTR);
	private static final Fn GESTURE_STATE = Fn.of(Lib.GTK, "gtk_gesture_set_state", INT, PTR, INT);
	private static final int SEQUENCE_CLAIMED = 1;
	private static final Fn LONG_PRESS_NEW = Fn.of(Lib.GTK, "gtk_gesture_long_press_new", PTR);
	private static final Fn TOUCH_ONLY = Fn.of(Lib.GTK, "gtk_gesture_single_set_touch_only", null, PTR, INT);
	private static final Fn CURRENT_DEVICE = Fn.of(Lib.GTK, "gtk_event_controller_get_current_event_device", PTR,
			PTR);
	private static final Fn DEVICE_SOURCE = Fn.of(Lib.GTK, "gdk_device_get_source", INT, PTR);
	private static final int SOURCE_TOUCHSCREEN = 3;

	private Controller(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Controller click(int button) {
		Controller c = new Controller(CLICK_NEW.p());
		SET_BUTTON.v(c, button);
		return c;
	}

	@NonNull
	public static Controller longPress() {
		Controller c = new Controller(LONG_PRESS_NEW.p());
		TOUCH_ONLY.v(c, true);
		return c;
	}

	public static void contextMenu(@NonNull Widget widget, @NonNull Signals.Xy open) {
		Controller right = click(BUTTON_SECONDARY);
		right.onPressed((count, x, y) -> {
			right.claim();
			open.on(x, y);
		});
		widget.addController(right);
		Controller hold = longPress();
		hold.onLongPressed((x, y) -> {
			hold.claim();
			open.on(x, y);
		});
		widget.addController(hold);
	}

	@NonNull
	public static Controller keys() {
		return new Controller(KEY_NEW.p());
	}

	@NonNull
	public static Controller scroll(int flags) {
		return new Controller(SCROLL_NEW.p(flags));
	}

	/** Tells when the keyboard focus comes into the widget or one of its children, and when it leaves. */
	@NonNull
	public static Controller focus() {
		return new Controller(FOCUS_NEW.p());
	}

	@NonNull
	public static Controller motion() {
		return new Controller(MOTION_NEW.p());
	}

	public Controller phase(int phase) {
		PHASE.v(this, phase);
		return this;
	}

	public int button() {
		return CURRENT_BUTTON.i(this);
	}

	public void claim() {
		GESTURE_STATE.i(this, SEQUENCE_CLAIMED);
	}

	public boolean isTouch() {
		MemorySegment device = CURRENT_DEVICE.p(this);
		return !Native.isNull(device) && DEVICE_SOURCE.i(device) == SOURCE_TOUCHSCREEN;
	}

	public int modifiers() {
		return STATE.i(this);
	}

	public int scrollUnit() {
		return SCROLL_UNIT.exists() ? SCROLL_UNIT.i(this) : SCROLL_UNIT_WHEEL;
	}

	public Controller onPressed(@NonNull Signals.Pressed handler) {
		Signals.connect(this, "pressed", Signals.Sig.PRESSED, handler);
		return this;
	}

	public Controller onLongPressed(@NonNull Signals.Xy handler) {
		Signals.connect(this, "pressed", Signals.Sig.XY, handler);
		return this;
	}

	public Controller onReleased(@NonNull Signals.Pressed handler) {
		Signals.connect(this, "released", Signals.Sig.PRESSED, handler);
		return this;
	}

	public Controller onKey(@NonNull Signals.Key handler) {
		Signals.connect(this, "key-pressed", Signals.Sig.KEY, handler);
		return this;
	}

	public Controller onScroll(@NonNull Signals.Scroll handler) {
		Signals.connect(this, "scroll", Signals.Sig.SCROLL, handler);
		return this;
	}

	public Controller onMotion(@NonNull Signals.Xy handler) {
		Signals.connect(this, "motion", Signals.Sig.XY, handler);
		return this;
	}

	public Controller onEnter(@NonNull Signals.Xy handler) {
		Signals.connect(this, "enter", Signals.Sig.XY, handler);
		return this;
	}

	public Controller onLeave(@NonNull Runnable handler) {
		on("leave", handler);
		return this;
	}

	public Controller onFocusIn(@NonNull Runnable handler) {
		on("enter", handler);
		return this;
	}

	public Controller onFocusOut(@NonNull Runnable handler) {
		on("leave", handler);
		return this;
	}
}
