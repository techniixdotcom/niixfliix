package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;

public final class VideoView {

	private static final Fn OFFLOAD_NEW = Fn.of(Lib.GTK, "gtk_graphics_offload_new", PTR, PTR);
	private static final Fn BLACK_BACKGROUND = Fn.of(Lib.GTK, "gtk_graphics_offload_set_black_background", null,
			PTR, INT);

	private final Picture picture = Picture.create(Picture.FIT_CONTAIN);
	private final Widget root;

	public VideoView() {
		Widget top = picture;
		if (OFFLOAD_NEW.exists()) {
			Offload offload = new Offload(OFFLOAD_NEW.p(picture));
			if (BLACK_BACKGROUND.exists()) {
				BLACK_BACKGROUND.v(offload, true);
			}
			top = offload;
		}
		root = top;
		root.addClass("video");
		root.hexpand(true).vexpand(true);
	}

	private static final class Offload extends Widget {
		Offload(MemorySegment pointer) {
			super(pointer);
		}
	}

	@NonNull
	public Widget widget() {
		return root;
	}

	@NonNull
	public Widget picture() {
		return picture;
	}

	public void show(@Nullable MemorySegment paintable) {
		if (paintable == null) {
			picture.show((GObj) null);
		} else {
			picture.show(paintable);
		}
	}

	public void show(@NonNull Texture frame) {
		picture.show(frame);
	}
}
