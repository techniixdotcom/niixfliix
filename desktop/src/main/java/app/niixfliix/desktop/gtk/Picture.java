package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;

import app.niixfliix.desktop.platform.Native;

public final class Picture extends Widget {

	public static final int FIT_FILL = 0;
	public static final int FIT_CONTAIN = 1;
	public static final int FIT_COVER = 2;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_picture_new", PTR);
	private static final Fn SET_PAINTABLE = Fn.of(Lib.GTK, "gtk_picture_set_paintable", null, PTR, PTR);
	private static final Fn CONTENT_FIT = Fn.of(Lib.GTK, "gtk_picture_set_content_fit", null, PTR, INT);
	private static final Fn CAN_SHRINK = Fn.of(Lib.GTK, "gtk_picture_set_can_shrink", null, PTR, INT);
	private static final Fn GET_PAINTABLE = Fn.of(Lib.GTK, "gtk_picture_get_paintable", PTR, PTR);
	private static final Fn INTRINSIC_WIDTH = Fn.of(Lib.GTK, "gdk_paintable_get_intrinsic_width", INT, PTR);
	private static final Fn INTRINSIC_HEIGHT = Fn.of(Lib.GTK, "gdk_paintable_get_intrinsic_height", INT, PTR);

	private Picture(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Picture create(int fit) {
		Picture p = new Picture(NEW.p());
		CONTENT_FIT.v(p, fit);
		CAN_SHRINK.v(p, true);
		return p;
	}

	public Picture fit(int fit) {
		CONTENT_FIT.v(this, fit);
		return this;
	}

	public Picture show(@Nullable GObj paintable) {
		SET_PAINTABLE.v(this, paintable);
		return this;
	}

	public Picture show(@NonNull MemorySegment paintable) {
		SET_PAINTABLE.v(this, paintable);
		return this;
	}

	@NonNull
	public Widget fixed(int width, int height) {
		Box room = Box.column(0);
		room.size(width, height);
		return Layers.overlay(room, this);
	}

	@NonNull
	public int[] pictureSize() {
		MemorySegment paintable = GET_PAINTABLE.p(this);
		if (Native.isNull(paintable)) {
			return new int[] {0, 0};
		}
		return new int[] {INTRINSIC_WIDTH.i(paintable), INTRINSIC_HEIGHT.i(paintable)};
	}
}
