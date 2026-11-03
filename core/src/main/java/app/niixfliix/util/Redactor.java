package app.niixfliix.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Redactor {

	private static final Pattern URL = Pattern.compile("(?i)\\b(https?|stremio|magnet)(://|:\\?)([^/\\s?#]*)[^\\s]*");
	private static final Pattern KEY_VALUE = Pattern.compile(
			"(?i)(realdebrid|alldebrid|premiumize|debridlink|torbox|offcloud|putio|apikey|api_key|token|key|password)=([^|&/\\s]+)");
	private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)\\S+");
	private static final Pattern JSON_SECRET = Pattern.compile(
			"(?i)(\"(?:authkey|password|apikey|api_key|token|key)\"\\s*:\\s*)\"[^\"]*\"");
	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private Redactor() {
	}

	@NonNull
	public static String redact(@Nullable String text) {
		if (text == null) {
			return "";
		}
		String out = KEY_VALUE.matcher(text).replaceAll("$1=***");
		out = BEARER.matcher(out).replaceAll("$1***");
		out = JSON_SECRET.matcher(out).replaceAll("$1\"***\"");
		Matcher m = URL.matcher(out);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String scheme = m.group(1);
			String host = m.group(3);
			int at = host.lastIndexOf('@');
			if (at >= 0) {
				host = host.substring(at + 1);
			}
			String replacement = "magnet".equalsIgnoreCase(scheme)
					? "magnet:…"
					: scheme + "://" + host + "/…";
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return EMAIL.matcher(sb.toString()).replaceAll("***@***");
	}
}
