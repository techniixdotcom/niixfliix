package app.niixfliix.desktop.i18n;

import androidx.annotation.NonNull;

final class PluralRules {

	private PluralRules() {
	}

	@NonNull
	static String select(@NonNull String language, int count) {
		int n = Math.abs(count);
		switch (language) {
			case "en":
			case "tr":
				return n == 1 ? "one" : "other";
			case "es":
				if (n == 1) {
					return "one";
				}
				return n != 0 && n % 1_000_000 == 0 ? "many" : "other";
			case "fr":
				if (n == 0 || n == 1) {
					return "one";
				}
				return n % 1_000_000 == 0 ? "many" : "other";
			case "ru": {
				int mod10 = n % 10;
				int mod100 = n % 100;
				if (mod10 == 1 && mod100 != 11) {
					return "one";
				}
				if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
					return "few";
				}
				return "many";
			}
			default:
				return "other";
		}
	}
}
