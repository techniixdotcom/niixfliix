package app.niixfliix.ui;

import android.content.Context;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import app.niixfliix.R;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;

public final class Formats {

	private Formats() {
	}

	@NonNull
	public static String size(@NonNull Context context, long bytes) {
		return Formatter.formatShortFileSize(context, bytes);
	}

	@NonNull
	public static String speed(@NonNull Context context, long bytesPerSecond) {
		return context.getString(R.string.speed_per_second, Formatter.formatShortFileSize(context, bytesPerSecond));
	}

	@NonNull
	public static String typeLabel(@NonNull Context context, @Nullable String type) {
		if (Meta.TYPE_MOVIE.equals(type)) {
			return context.getString(R.string.type_movies);
		}
		if (Meta.TYPE_SERIES.equals(type)) {
			return context.getString(R.string.type_series);
		}
		if (type == null || type.isEmpty()) {
			return "";
		}
		return capitalize(type, Locale.getDefault());
	}

	@NonNull
	public static String capitalize(@NonNull String text, @NonNull Locale locale) {
		return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
	}

	@NonNull
	public static String episode(@NonNull Context context, @NonNull Video video) {
		String number = context.getString(R.string.episode_number, video.seasonOrZero(), video.episodeOrZero());
		String title = video.displayTitle();
		return title.isEmpty() ? number : context.getString(R.string.episode_with_title, number, title);
	}

	@NonNull
	public static String day(@Nullable String released) {
		if (released == null) {
			return "";
		}
		return released.length() >= 10 ? released.substring(0, 10) : released;
	}

	@NonNull
	public static String language(@NonNull String code) {
		if (code.isEmpty()) {
			return "";
		}
		String name = Locale.forLanguageTag(code).getDisplayLanguage();
		return name.isEmpty() ? code : capitalize(name, Locale.getDefault());
	}
}
