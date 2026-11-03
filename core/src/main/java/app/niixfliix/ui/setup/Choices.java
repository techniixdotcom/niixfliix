package app.niixfliix.ui.setup;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.util.Texts;

public final class Choices {

	public static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList(
			"en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "tr", "ar", "hi", "nl", "pl",
			"sv", "da", "fi", "no", "el", "he", "cs", "hu", "ro", "uk", "vi", "th", "id"));

	public static final List<Quality> QUALITIES = StreamRanker.SHOWN;

	private Choices() {
	}

	public static int qualityIndex(@NonNull Quality quality) {
		if (quality == Quality.P2160) {
			return 0;
		}
		int index = QUALITIES.indexOf(quality);
		return index >= 0 ? index : QUALITIES.indexOf(Quality.P1080);
	}

	public static int languageIndex(@NonNull String code) {
		int index = LANGUAGES.indexOf(code);
		return index >= 0 ? index : 0;
	}

	@NonNull
	public static String[] languageLabels() {
		String[] out = new String[LANGUAGES.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = languageName(LANGUAGES.get(i));
		}
		return out;
	}

	@NonNull
	public static String languageName(@NonNull String code) {
		if (code.isEmpty()) {
			return "";
		}
		String name = Locale.forLanguageTag(code).getDisplayLanguage();
		return name.isEmpty() ? code : Texts.capitalize(name, Locale.getDefault());
	}
}
