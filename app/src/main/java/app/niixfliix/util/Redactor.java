package app.niixfliix.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keys can be in URL paths, so URLs get cut down to the host. */
public final class Redactor {

	private static final Pattern URL = Pattern.compile("(?i)\\b(https?|stremio|magnet)(://|:\\?)([^/\\s?#]*)[^\\s]*");
	private static final Pattern KEY_VALUE = Pattern.compile(
			"(?i)(realdebrid|alldebrid|premiumize|debridlink|torbox|offcloud|putio|apikey|api_key|token|key|password)=([^|&/\\s]+)");
	private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)\\S+");

	private Redactor() {
	}

	@NonNull
	public static String redact(@Nullable String text) {
		if (text == null) {
			return "";
		}
		String out = KEY_VALUE.matcher(text).replaceAll("$1=***");
		out = BEARER.matcher(out).replaceAll("$1***");
		Matcher m = URL.matcher(out);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String scheme = m.group(1);
			String replacement = "magnet".equalsIgnoreCase(scheme)
					? "magnet:…"
					: scheme + "://" + m.group(3) + "/…";
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
