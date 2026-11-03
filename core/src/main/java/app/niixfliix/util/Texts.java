package app.niixfliix.util;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class Texts {

	private Texts() {
	}

	@NonNull
	public static String capitalize(@NonNull String text, @NonNull Locale locale) {
		return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
	}
}
