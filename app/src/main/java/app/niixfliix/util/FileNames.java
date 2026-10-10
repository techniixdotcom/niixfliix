package app.niixfliix.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class FileNames {

	private static final String FALLBACK = "video";

	private FileNames() {
	}

	/** Safe on common file systems, cut to maxBytes of UTF-8, keeps the extension. */
	@NonNull
	public static String sanitize(@Nullable String name, int maxBytes) {
		if (name == null) {
			return FALLBACK;
		}
		StringBuilder clean = new StringBuilder(name.length());
		boolean lastWasSpace = false;
		for (int i = 0; i < name.length(); ) {
			int cp = name.codePointAt(i);
			i += Character.charCount(cp);
			boolean space = Character.isWhitespace(cp);
			if (!space && (Character.isISOControl(cp) || "/\\:*?\"<>|".indexOf(cp) >= 0)) {
				cp = '_';
			}
			if (space) {
				if (!lastWasSpace) {
					clean.append(' ');
				}
			} else {
				clean.appendCodePoint(cp);
			}
			lastWasSpace = space;
		}
		String result = trimDotsAndSpaces(clean.toString());
		if (result.isEmpty()) {
			return FALLBACK;
		}
		return cut(result, maxBytes);
	}

	@NonNull
	public static String mimeType(@NonNull String fileName, @NonNull String fallback) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".mkv")) {
			return "video/x-matroska";
		}
		if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) {
			return "video/mp4";
		}
		if (lower.endsWith(".webm")) {
			return "video/webm";
		}
		if (lower.endsWith(".avi")) {
			return "video/x-msvideo";
		}
		if (lower.endsWith(".ts")) {
			return "video/mp2t";
		}
		return fallback;
	}

	private static String trimDotsAndSpaces(String s) {
		int start = 0;
		int end = s.length();
		while (start < end && (s.charAt(start) == ' ' || s.charAt(start) == '.')) {
			start++;
		}
		while (end > start && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '.')) {
			end--;
		}
		return s.substring(start, end);
	}

	private static String cut(String name, int maxBytes) {
		if (utf8Length(name) <= maxBytes) {
			return name;
		}
		String extension = "";
		int dot = name.lastIndexOf('.');
		// short tail only, not half a release name
		if (dot > 0 && name.length() - dot <= 6) {
			extension = name.substring(dot);
			name = name.substring(0, dot);
		}
		int budget = Math.max(1, maxBytes - utf8Length(extension));
		StringBuilder out = new StringBuilder();
		int used = 0;
		for (int i = 0; i < name.length(); ) {
			int cp = name.codePointAt(i);
			int size = utf8Length(new String(Character.toChars(cp)));
			if (used + size > budget) {
				break;
			}
			out.appendCodePoint(cp);
			used += size;
			i += Character.charCount(cp);
		}
		String base = trimDotsAndSpaces(out.toString());
		if (base.isEmpty()) {
			base = FALLBACK;
		}
		return base + extension;
	}

	private static int utf8Length(String s) {
		return s.getBytes(StandardCharsets.UTF_8).length;
	}
}
