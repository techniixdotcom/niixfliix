package app.niixfliix.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * All logging goes through here so it gets redacted. Throwables are flattened on purpose, their message often
 * has the URL in it.
 */
public final class Logs {

	private Logs() {
	}

	public static void w(@NonNull String tag, @NonNull String message, @Nullable Throwable error) {
		Log.w(tag, Redactor.redact(message + describe(error)));
	}

	@NonNull
	private static String describe(@Nullable Throwable error) {
		if (error == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		Throwable t = error;
		for (int depth = 0; t != null && depth < 3; depth++, t = t.getCause()) {
			out.append(depth == 0 ? ": " : " <- ").append(t.getClass().getSimpleName());
			if (t.getMessage() != null) {
				out.append(' ').append(t.getMessage());
			}
		}
		return out.toString();
	}
}
