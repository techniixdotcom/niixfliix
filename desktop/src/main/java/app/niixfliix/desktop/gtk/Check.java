package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class Check extends Widget {

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_check_button_new_with_label", PTR, PTR);
	private static final Fn SET_ACTIVE = Fn.of(Lib.GTK, "gtk_check_button_set_active", null, PTR, INT);
	private static final Fn GET_ACTIVE = Fn.of(Lib.GTK, "gtk_check_button_get_active", INT, PTR);
	private static final Fn SET_GROUP = Fn.of(Lib.GTK, "gtk_check_button_set_group", null, PTR, PTR);

	private Check(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Check of(@NonNull String label, boolean active) {
		Check c = new Check(NEW.p(label));
		SET_ACTIVE.v(c, active);
		return c;
	}

	public Check group(@NonNull Check first) {
		if (first != this) {
			SET_GROUP.v(this, first);
		}
		return this;
	}

	public boolean isActive() {
		return GET_ACTIVE.b(this);
	}

	public Check onToggled(@NonNull Runnable action) {
		on("toggled", action);
		return this;
	}
}
