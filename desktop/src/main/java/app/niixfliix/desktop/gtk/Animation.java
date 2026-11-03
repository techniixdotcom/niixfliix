package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.DOUBLE;
import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;

import app.niixfliix.desktop.platform.Native;

public final class Animation extends GObj {

	private static final Fn TARGET_NEW = Fn.of(Lib.ADW, "adw_property_animation_target_new", PTR, PTR, PTR);
	private static final Fn NEW = Fn.of(Lib.ADW, "adw_timed_animation_new", PTR, PTR, DOUBLE, DOUBLE, INT, PTR);
	private static final Fn FROM = Fn.of(Lib.ADW, "adw_timed_animation_set_value_from", null, PTR, DOUBLE);
	private static final Fn TO = Fn.of(Lib.ADW, "adw_timed_animation_set_value_to", null, PTR, DOUBLE);
	private static final Fn PLAY = Fn.of(Lib.ADW, "adw_animation_play", null, PTR);

	private Animation(MemorySegment pointer) {
		super(pointer);
	}

	@Nullable
	public static Animation of(@NonNull Widget widget, @NonNull GObj object, @NonNull String property,
			int durationMs) {
		if (!TARGET_NEW.exists() || !NEW.exists() || !FROM.exists() || !TO.exists() || !PLAY.exists()) {
			return null;
		}
		MemorySegment target = TARGET_NEW.p(object, property);
		if (Native.isNull(target)) {
			return null;
		}
		Animation a = new Animation(NEW.p(widget, 0.0, 0.0, durationMs, target));
		widget.on("destroy", a::unref);
		return a;
	}

	public void play(double from, double to) {
		if (!alive()) {
			return;
		}
		FROM.v(this, from);
		TO.v(this, to);
		PLAY.v(this);
	}
}
