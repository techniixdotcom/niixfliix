package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class Image extends Widget {

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_image_new_from_icon_name", PTR, PTR);
	private static final Fn SET_NAME = Fn.of(Lib.GTK, "gtk_image_set_from_icon_name", null, PTR, PTR);
	private static final Fn PIXEL_SIZE = Fn.of(Lib.GTK, "gtk_image_set_pixel_size", null, PTR, INT);

	private Image(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static Image icon(@NonNull String iconName) {
		return new Image(NEW.p(iconName));
	}

	@NonNull
	public static Image icon(@NonNull String iconName, int pixels) {
		return icon(iconName).pixels(pixels);
	}

	public Image name(@NonNull String iconName) {
		SET_NAME.v(this, iconName);
		return this;
	}

	public Image pixels(int size) {
		PIXEL_SIZE.v(this, size);
		return this;
	}
}
