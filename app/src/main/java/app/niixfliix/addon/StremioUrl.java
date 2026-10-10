package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Normalizes pasted / intent links to an https manifest URL, or null.
 * Not using java.net.URI on purpose: Torrentio puts '|' in the path and URI chokes on it.
 */
public final class StremioUrl {

	public static final int MAX_LENGTH = 2048;
	private static final String MANIFEST = "/manifest.json";
	private static final String HTTPS = "https://";
	private static final String STREMIO = "stremio://";

	private StremioUrl() {
	}

	@Nullable
	public static String toManifestUrl(@Nullable String input) {
		if (input == null) {
			return null;
		}
		String text = input.trim();
		if (text.isEmpty() || text.length() > MAX_LENGTH) {
			return null;
		}
		String rest;
		if (text.regionMatches(true, 0, STREMIO, 0, STREMIO.length())) {
			rest = text.substring(STREMIO.length());
		} else if (text.regionMatches(true, 0, HTTPS, 0, HTTPS.length())) {
			rest = text.substring(HTTPS.length());
		} else {
			return null;
		}
		int slash = rest.indexOf('/');
		if (slash <= 0) {
			return null;
		}
		String authority = rest.substring(0, slash).toLowerCase(Locale.ROOT);
		String path = rest.substring(slash);
		int cut = indexOfAny(path, "?#");
		if (cut >= 0) {
			path = path.substring(0, cut);
		}
		if (!isValidAuthority(authority) || !isValidPath(path) || !path.endsWith(MANIFEST)) {
			return null;
		}
		return HTTPS + authority + path;
	}

	@NonNull
	public static String baseOf(@NonNull String manifestUrl) {
		int q = indexOfAny(manifestUrl, "?#");
		String clean = q >= 0 ? manifestUrl.substring(0, q) : manifestUrl;
		if (clean.endsWith(MANIFEST)) {
			return clean.substring(0, clean.length() - MANIFEST.length());
		}
		return clean;
	}

	@Nullable
	public static String hostOf(@NonNull String url) {
		int start = url.indexOf("://");
		if (start < 0) {
			return null;
		}
		String rest = url.substring(start + 3);
		int end = indexOfAny(rest, "/?#");
		String authority = end >= 0 ? rest.substring(0, end) : rest;
		int colon = authority.indexOf(':');
		String host = colon >= 0 ? authority.substring(0, colon) : authority;
		return host.isEmpty() ? null : host.toLowerCase(Locale.ROOT);
	}

	/** anything between host and manifest.json is config (and maybe a key) */
	public static boolean hasConfiguration(@NonNull String manifestUrl) {
		String path = pathOf(manifestUrl);
		return path != null && !path.equals(MANIFEST);
	}

	@NonNull
	public static String masked(@NonNull String manifestUrl) {
		String host = hostOf(manifestUrl);
		if (host == null) {
			return "";
		}
		return hasConfiguration(manifestUrl) ? host + "/•••" + MANIFEST : host + MANIFEST;
	}

	@NonNull
	public static String configurationOf(@NonNull String manifestUrl) {
		String path = pathOf(manifestUrl);
		if (path == null || !path.endsWith(MANIFEST)) {
			return "";
		}
		String middle = path.substring(0, path.length() - MANIFEST.length());
		while (middle.startsWith("/")) {
			middle = middle.substring(1);
		}
		return middle;
	}

	@Nullable
	private static String pathOf(@NonNull String url) {
		int start = url.indexOf("://");
		if (start < 0) {
			return null;
		}
		String rest = url.substring(start + 3);
		int slash = rest.indexOf('/');
		if (slash < 0) {
			return null;
		}
		String path = rest.substring(slash);
		int cut = indexOfAny(path, "?#");
		return cut >= 0 ? path.substring(0, cut) : path;
	}

	private static boolean isValidAuthority(String authority) {
		int colon = authority.indexOf(':');
		String host = colon >= 0 ? authority.substring(0, colon) : authority;
		if (host.isEmpty() || host.length() > 253 || host.startsWith(".") || host.endsWith(".")) {
			return false;
		}
		for (int i = 0; i < host.length(); i++) {
			char c = host.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.')) {
				return false;
			}
		}
		if (colon >= 0) {
			String port = authority.substring(colon + 1);
			if (port.isEmpty() || port.length() > 5) {
				return false;
			}
			for (int i = 0; i < port.length(); i++) {
				if (!Character.isDigit(port.charAt(i))) {
					return false;
				}
			}
			int value = Integer.parseInt(port);
			return value > 0 && value <= 65535;
		}
		return true;
	}

	private static boolean isValidPath(String path) {
		if (path.contains("/../") || path.contains("//")) {
			return false;
		}
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (c <= ' ' || c >= 0x7f || "\\\"<>^`{}".indexOf(c) >= 0) {
				return false;
			}
		}
		return true;
	}

	private static int indexOfAny(String s, String chars) {
		for (int i = 0; i < s.length(); i++) {
			if (chars.indexOf(s.charAt(i)) >= 0) {
				return i;
			}
		}
		return -1;
	}
}
