package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.NumberFormat;
import java.util.Locale;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.ui.setup.Choices;
import app.niixfliix.util.Texts;

public final class Fmt {

	private static final String[] UNITS = {"B", "kB", "MB", "GB", "TB"};

	private Fmt() {
	}

	@NonNull
	public static String size(long bytes) {
		double value = Math.max(0, bytes);
		int unit = 0;
		while (value >= 1000 && unit < UNITS.length - 1) {
			value /= 1000;
			unit++;
		}
		NumberFormat number = NumberFormat.getNumberInstance(I18n.locale());
		number.setMaximumFractionDigits(value < 10 && unit > 0 ? 1 : 0);
		return number.format(value) + " " + UNITS[unit];
	}

	@NonNull
	public static String speed(long bytesPerSecond) {
		return I18n.t("speed_per_second", size(bytesPerSecond));
	}

	@NonNull
	public static String time(long ms) {
		long seconds = Math.max(0, ms) / 1000;
		long h = seconds / 3600;
		long m = seconds / 60 % 60;
		long s = seconds % 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
				: String.format(Locale.ROOT, "%d:%02d", m, s);
	}

	@NonNull
	public static String quality(@NonNull Quality quality) {
		return quality == Quality.OTHER ? I18n.t("quality_other") : quality.shortName();
	}

	@NonNull
	public static String typeLabel(@Nullable String type) {
		if (Meta.TYPE_MOVIE.equals(type)) {
			return I18n.t("type_movies");
		}
		if (Meta.TYPE_SERIES.equals(type)) {
			return I18n.t("type_series");
		}
		return type == null ? "" : Texts.capitalize(type, I18n.locale());
	}

	@NonNull
	public static String episode(@NonNull Video video) {
		String number = I18n.t("episode_number", video.seasonOrZero(), video.episodeOrZero());
		String title = video.displayTitle();
		return title.isEmpty() ? number : I18n.t("episode_with_title", number, title);
	}

	@NonNull
	public static String language(@Nullable String code) {
		return code == null ? "" : Choices.languageName(code);
	}

	@NonNull
	public static String day(@Nullable String released) {
		if (released == null) {
			return "";
		}
		return released.length() >= 10 ? released.substring(0, 10) : released;
	}
}
