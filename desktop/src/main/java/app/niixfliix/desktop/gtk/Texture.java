package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.LONG;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;

public final class Texture extends GObj {

	private static final int FORMAT_BGRA_PREMULTIPLIED = 0;

	private static final Fn BYTES_NEW = Fn.of(Lib.GLIB, "g_bytes_new", PTR, PTR, LONG);
	private static final Fn BYTES_UNREF = Fn.of(Lib.GLIB, "g_bytes_unref", null, PTR);
	private static final Fn MEMORY_TEXTURE = Fn.of(Lib.GTK, "gdk_memory_texture_new", PTR, INT, INT, INT, PTR, LONG);

	public final int width;
	public final int height;

	private Texture(MemorySegment pointer, int width, int height) {
		super(pointer);
		this.width = width;
		this.height = height;
	}

	@NonNull
	public static Texture ofArgbPre(int width, int height, @NonNull MemorySegment pixels) {
		long stride = 4L * width;
		if (pixels.byteSize() < stride * height) {
			throw new IllegalArgumentException("Too few pixels");
		}
		MemorySegment bytes = BYTES_NEW.p(pixels, stride * height);
		try {
			return new Texture(MEMORY_TEXTURE.p(width, height, FORMAT_BGRA_PREMULTIPLIED, bytes, stride), width, height);
		} finally {
			BYTES_UNREF.v(bytes);
		}
	}

	public long byteSize() {
		return 4L * width * height;
	}
}
