package app.niixfliix.desktop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.niixfliix.data.KeyValueStore;

public final class DesktopPrefs {

	private static final String LANGUAGE = "desktop_language";
	private static final String VOLUME = "desktop_volume";
	private static final String WINDOW_WIDTH = "desktop_window_width";
	private static final String WINDOW_HEIGHT = "desktop_window_height";
	private static final String WINDOW_MAXIMIZED = "desktop_window_maximized";
	private static final String PLAYER_FULLSCREEN = "desktop_player_fullscreen";
	private static final String HARDWARE_DECODING = "desktop_hardware_decoding";
	private static final int NOT_SET = Integer.MIN_VALUE;

	private final KeyValueStore store;

	public DesktopPrefs(@NonNull KeyValueStore store) {
		this.store = store;
	}

	@Nullable
	public String language() {
		return store.getString(LANGUAGE, null);
	}

	public void setLanguage(@Nullable String language) {
		store.putString(LANGUAGE, language);
	}

	public int volume() {
		return Math.max(0, Math.min(100, store.getInt(VOLUME, 100)));
	}

	public void setVolume(int percent) {
		store.putInt(VOLUME, percent);
	}

	@Nullable
	public int[] windowSize() {
		int width = store.getInt(WINDOW_WIDTH, NOT_SET);
		int height = store.getInt(WINDOW_HEIGHT, NOT_SET);
		if (width == NOT_SET || height == NOT_SET || width <= 0 || height <= 0) {
			return null;
		}
		return new int[] {width, height};
	}

	public void setWindowSize(int width, int height) {
		store.putInt(WINDOW_WIDTH, width);
		store.putInt(WINDOW_HEIGHT, height);
	}

	public boolean windowMaximized() {
		return store.getBoolean(WINDOW_MAXIMIZED, false);
	}

	public void setWindowMaximized(boolean maximized) {
		store.putBoolean(WINDOW_MAXIMIZED, maximized);
	}

	public boolean playerFullscreen() {
		return store.getBoolean(PLAYER_FULLSCREEN, false);
	}

	public void setPlayerFullscreen(boolean fullscreen) {
		store.putBoolean(PLAYER_FULLSCREEN, fullscreen);
	}

	public boolean hardwareDecoding() {
		return store.getBoolean(HARDWARE_DECODING, true);
	}

	public void setHardwareDecoding(boolean on) {
		store.putBoolean(HARDWARE_DECODING, on);
	}
}
