package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum Quality {
	P2160("2160p", "4K"),
	P1440("1440p", "2K"),
	P1080("1080p", "1080p"),
	P720("720p", "720p"),
	P480("480p", "480p"),
	OTHER("other", "other");

	private static final Pattern RESOLUTION = Pattern.compile("(?i)(?<![0-9])(2160|1440|1080|720|576|480|360)[pi](?![a-z0-9])");
	private static final Pattern UHD = Pattern.compile("(?i)(?<![a-z0-9])(4k|uhd)(?![a-z0-9])");

	/** stored in prefs, don't change */
	public final String label;
	/** what the user sees */
	public final String title;

	Quality(String label, String title) {
		this.label = label;
		this.title = title;
	}

	@NonNull
	public static Quality fromLabel(@Nullable String label) {
		for (Quality q : values()) {
			if (q.label.equals(label)) {
				return q;
			}
		}
		return OTHER;
	}

	@Nullable
	public static Quality detect(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		Matcher m = RESOLUTION.matcher(text);
		if (m.find()) {
			switch (m.group(1)) {
				case "2160":
					return P2160;
				case "1440":
					return P1440;
				case "1080":
					return P1080;
				case "720":
					return P720;
				default:
					return P480;
			}
		}
		if (UHD.matcher(text).find()) {
			return P2160;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		if (lower.contains("dvdrip") || lower.contains("sdtv")) {
			return P480;
		}
		return null;
	}
}
