package app.niixfliix.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Logs {

	public interface Sink {
		void warn(@NonNull String tag, @NonNull String message);
	}

	private static volatile Sink sink = (tag, message) -> System.err.println(tag + ": " + message);

	private Logs() {
	}

	public static void setSink(@NonNull Sink newSink) {
		sink = newSink;
	}

	public static void w(@NonNull String tag, @NonNull String message, @Nullable Throwable error) {
		sink.warn(tag, Redactor.redact(message + describe(error)));
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
