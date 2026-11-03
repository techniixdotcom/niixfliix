package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import app.niixfliix.util.Logs;

public final class HardwareGuard {

	private static final String TAG = "HardwareGuard";
	private static final String MARK = "hardware-video";

	private HardwareGuard() {
	}

	@NonNull
	private static File mark() {
		return new File(Paths.data(), MARK);
	}

	public static synchronized void begin() {
		File mark = mark();
		try {
			if (!mark.exists() && !mark.createNewFile()) {
				Logs.w(TAG, "Could not mark hardware decoding", null);
			}
		} catch (IOException e) {
			Logs.w(TAG, "Could not mark hardware decoding", e);
		}
	}

	public static synchronized void end() {
		File mark = mark();
		if (mark.exists() && !mark.delete()) {
			Logs.w(TAG, "Could not clear the hardware decoding mark", null);
		}
	}

	public static synchronized boolean wasDecodingAtExit() {
		File mark = mark();
		boolean was = mark.exists();
		end();
		return was;
	}
}
