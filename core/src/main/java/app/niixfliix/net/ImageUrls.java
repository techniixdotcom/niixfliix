package app.niixfliix.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImageUrls {

	private static final Pattern TMDB = Pattern.compile(
			"(?i)^(https://image\\.tmdb\\.org/t/p/)(original|w\\d{2,4}|h\\d{2,4})(/[^?#]+)$");
	private static final int[] POSTER_WIDTHS = {92, 154, 185, 342, 500, 780};
	private static final int[] BACKDROP_WIDTHS = {300, 780, 1280};
	private static final Pattern METAHUB_POSTER = Pattern.compile(
			"(?i)^(https://images\\.metahub\\.space/poster/)large(/[^/?#]+/img)$");
	private static final int METAHUB_MEDIUM_MAX_PX = 400;

	private ImageUrls() {
	}

	public static boolean isHttps(@Nullable String url) {
		return url != null && url.regionMatches(true, 0, "https://", 0, 8);
	}

	@Nullable
	public static String secure(@Nullable String url) {
		if (url == null) {
			return null;
		}
		String trimmed = url.trim();
		if (isHttps(trimmed)) {
			return trimmed;
		}
		if (trimmed.regionMatches(true, 0, "http://", 0, 7) && trimmed.length() > 7) {
			return "https://" + trimmed.substring(7);
		}
		return null;
	}

	@NonNull
	public static String poster(@NonNull String url, int widthPx) {
		Matcher metahub = METAHUB_POSTER.matcher(url);
		if (metahub.matches() && widthPx > 0 && widthPx <= METAHUB_MEDIUM_MAX_PX) {
			return metahub.group(1) + "medium" + metahub.group(2);
		}
		return sized(url, widthPx, POSTER_WIDTHS);
	}

	@NonNull
	public static String backdrop(@NonNull String url, int widthPx) {
		return sized(url, widthPx, BACKDROP_WIDTHS);
	}

	@VisibleForTesting
	@NonNull
	static String sized(@NonNull String url, int targetPx, @NonNull int[] widths) {
		Matcher m = TMDB.matcher(url);
		if (!m.matches() || targetPx <= 0) {
			return url;
		}
		String size = m.group(2).toLowerCase(Locale.ROOT);
		if (size.startsWith("h")) {
			return url;
		}
		int given = size.startsWith("w") ? Integer.parseInt(size.substring(1)) : Integer.MAX_VALUE;
		int pick = widths[widths.length - 1];
		for (int w : widths) {
			if (w >= targetPx) {
				pick = w;
				break;
			}
		}
		if (pick >= given) {
			return url;
		}
		return m.group(1) + "w" + pick + m.group(3);
	}
}
