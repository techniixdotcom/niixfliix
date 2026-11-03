package app.niixfliix.ui;

import android.content.Context;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import app.niixfliix.R;
import app.niixfliix.addon.Quality;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.ui.setup.Choices;
import app.niixfliix.util.Texts;

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
		return Texts.capitalize(type, Locale.getDefault());
	}

	@NonNull
	public static String quality(@NonNull Context context, @NonNull Quality quality) {
		return quality == Quality.OTHER ? context.getString(R.string.quality_other) : quality.shortName();
	}

	@NonNull
	public static String[] qualityLabels(@NonNull Context context) {
		String[] out = new String[Choices.QUALITIES.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = quality(context, Choices.QUALITIES.get(i));
		}
		return out;
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
}
