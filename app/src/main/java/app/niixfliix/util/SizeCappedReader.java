package app.niixfliix.util;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Responses aren't trusted to be small. */
public final class SizeCappedReader {

	public static final class TooLargeException extends IOException {
		public TooLargeException(long limit) {
			super("Response is larger than " + limit + " bytes");
		}
	}

	private SizeCappedReader() {
	}

	@NonNull
	public static byte[] read(@NonNull InputStream in, long maxBytes) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
		byte[] buffer = new byte[8192];
		long total = 0;
		int n;
		while ((n = in.read(buffer)) != -1) {
			total += n;
			if (total > maxBytes) {
				throw new TooLargeException(maxBytes);
			}
			out.write(buffer, 0, n);
		}
		return out.toByteArray();
	}

	@NonNull
	public static String readUtf8(@NonNull InputStream in, long maxBytes) throws IOException {
		return new String(read(in, maxBytes), StandardCharsets.UTF_8);
	}
}
