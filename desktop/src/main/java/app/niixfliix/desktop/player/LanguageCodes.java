package app.niixfliix.desktop.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

final class LanguageCodes {

	private static final Map<String, String> BIBLIOGRAPHIC = new HashMap<>();
	private static final Map<String, String> THREE_TO_TWO = new HashMap<>();

	static {
		String[][] pairs = {{"alb", "sqi"}, {"arm", "hye"}, {"baq", "eus"}, {"bur", "mya"}, {"chi", "zho"},
				{"cze", "ces"}, {"dut", "nld"}, {"fre", "fra"}, {"geo", "kat"}, {"ger", "deu"}, {"gre", "ell"},
				{"ice", "isl"}, {"mac", "mkd"}, {"mao", "mri"}, {"may", "msa"}, {"per", "fas"}, {"rum", "ron"},
				{"slo", "slk"}, {"tib", "bod"}, {"wel", "cym"}};
		for (String[] p : pairs) {
			BIBLIOGRAPHIC.put(p[0], p[1]);
		}
		for (String two : Locale.getISOLanguages()) {
			try {
				THREE_TO_TWO.put(Locale.of(two).getISO3Language(), two);
			} catch (MissingResourceException ignored) {
			}
		}
	}

	private LanguageCodes() {
	}

	@Nullable
	static String normalize(@Nullable String code) {
		if (code == null) {
			return null;
		}
		String c = code.trim().toLowerCase(Locale.ROOT).replace('_', '-');
		int dash = c.indexOf('-');
		if (dash > 0) {
			c = c.substring(0, dash);
		}
		if (c.isEmpty() || c.equals("und") || c.equals("mul") || c.equals("zxx")) {
			return null;
		}
		if (c.length() == 3) {
			String terminology = BIBLIOGRAPHIC.getOrDefault(c, c);
			String two = THREE_TO_TWO.get(terminology);
			return two != null ? two : terminology;
		}
		return c.equals("iw") ? "he" : c;
	}

	static boolean matches(@Nullable String code, @NonNull String wanted) {
		String normalized = normalize(code);
		return normalized != null && normalized.equals(normalize(wanted));
	}
}
