package app.niixfliix.ui.setup;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.ui.Formats;

public final class Choices {

	public static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList(
			"en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "tr", "ar", "hi", "nl", "pl",
			"sv", "da", "fi", "no", "el", "he", "cs", "hu", "ro", "uk", "vi", "th", "id"));

	public static final List<Quality> QUALITIES = StreamRanker.SHOWN;

	private Choices() {
	}

	@NonNull
	public static String[] qualityLabels() {
		String[] out = new String[QUALITIES.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = QUALITIES.get(i).title;
		}
		return out;
	}

	/** 4K saved by an older version shows as the highest choice, anything else unknown as 1080p */
	public static int qualityIndex(@NonNull Quality quality) {
		if (quality == Quality.P2160) {
			return 0;
		}
		int index = QUALITIES.indexOf(quality);
		return index >= 0 ? index : QUALITIES.indexOf(Quality.P1080);
	}

	/** English if the code isn't in the list */
	public static int languageIndex(@NonNull String code) {
		int index = LANGUAGES.indexOf(code);
		return index >= 0 ? index : 0;
	}

	@NonNull
	public static String[] languageLabels() {
		String[] out = new String[LANGUAGES.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = Formats.language(LANGUAGES.get(i));
		}
		return out;
	}
}
