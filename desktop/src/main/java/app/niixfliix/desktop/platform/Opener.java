package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import app.niixfliix.util.Logs;

public final class Opener {

	private static final String TAG = "Opener";

	private Opener() {
	}

	public static boolean openWebLink(@NonNull String url) {
		String lower = url.trim().toLowerCase(Locale.ROOT);
		if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
			return false;
		}
		return run(url.trim());
	}

	public static boolean openFile(@NonNull File file) {
		return file.exists() && run(file.toURI().toString());
	}

	private static boolean run(String target) {
		try {
			new ProcessBuilder("xdg-open", target)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			return true;
		} catch (IOException e) {
			Logs.w(TAG, "xdg-open failed", e);
			return false;
		}
	}
}
