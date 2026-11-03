package app.niixfliix.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import app.niixfliix.addon.Quality;
import app.niixfliix.data.KeyValueStore;

public final class PlayerPreferences {

	public enum SubtitleColor {
		WHITE,
		YELLOW,
		CYAN
	}

	public enum SubtitleBackground {
		NONE,
		TRANSLUCENT,
		BLACK
	}

	public static final int[] SUBTITLE_SIZES = {75, 100, 125, 150, 200};

	private static final String QUALITY_WIFI = "quality_wifi";
	private static final String QUALITY_MOBILE = "quality_mobile";
	private static final String LANGUAGE = "language";
	private static final String SUBTITLE_SIZE = "subtitle_size";
	private static final String SUBTITLE_COLOR = "subtitle_color";
	private static final String SUBTITLE_BACKGROUND = "subtitle_background";
	private static final String FULLSCREEN = "fullscreen";
	private static final String SERIES_QUALITY = "series_quality_";

	private final KeyValueStore store;

	public PlayerPreferences(@NonNull KeyValueStore store) {
		this.store = store;
	}

	@NonNull
	public Quality quality(boolean wifi) {
		String def = wifi ? Quality.P1080.label : Quality.P720.label;
		return Quality.fromLabel(store.getString(wifi ? QUALITY_WIFI : QUALITY_MOBILE, def));
	}

	public void setQuality(boolean wifi, @NonNull Quality quality) {
		store.putString(wifi ? QUALITY_WIFI : QUALITY_MOBILE, quality.label);
	}

	public void setSeriesQuality(@NonNull String seriesId, @NonNull Quality quality) {
		store.putString(SERIES_QUALITY + seriesId, quality.label);
	}

	@NonNull
	public Quality quality(@Nullable String seriesId, boolean wifi) {
		String picked = seriesId != null ? store.getString(SERIES_QUALITY + seriesId, null) : null;
		return picked != null ? Quality.fromLabel(picked) : quality(wifi);
	}

	@NonNull
	public String language() {
		String code = store.getString(LANGUAGE, null);
		if (code == null || !code.matches("[a-z]{2,3}")) {
			code = Locale.getDefault().getLanguage();
		}
		return code.matches("[a-z]{2,3}") ? code : "en";
	}

	public void setLanguage(@NonNull String code) {
		store.putString(LANGUAGE, code);
	}

	public int subtitleSize() {
		int size = store.getInt(SUBTITLE_SIZE, 100);
		for (int allowed : SUBTITLE_SIZES) {
			if (allowed == size) {
				return size;
			}
		}
		return 100;
	}

	public void setSubtitleSize(int percent) {
		store.putInt(SUBTITLE_SIZE, percent);
	}

	@NonNull
	public SubtitleColor subtitleColor() {
		return enumAt(SubtitleColor.values(), store.getInt(SUBTITLE_COLOR, 0));
	}

	public void setSubtitleColor(@NonNull SubtitleColor color) {
		store.putInt(SUBTITLE_COLOR, color.ordinal());
	}

	@NonNull
	public SubtitleBackground subtitleBackground() {
		return enumAt(SubtitleBackground.values(), store.getInt(SUBTITLE_BACKGROUND, SubtitleBackground.TRANSLUCENT.ordinal()));
	}

	public void setSubtitleBackground(@NonNull SubtitleBackground background) {
		store.putInt(SUBTITLE_BACKGROUND, background.ordinal());
	}

	public boolean fullscreen() {
		return store.getBoolean(FULLSCREEN, true);
	}

	public void setFullscreen(boolean fullscreen) {
		store.putBoolean(FULLSCREEN, fullscreen);
	}

	private static <E> E enumAt(E[] values, int index) {
		return index >= 0 && index < values.length ? values[index] : values[0];
	}
}
