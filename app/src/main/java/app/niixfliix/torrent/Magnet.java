package app.niixfliix.torrent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.niixfliix.app.Constant;

public final class Magnet {

	private static final String PREFIX = "magnet:?";
	private static final String BTIH = "urn:btih:";
	private static final int MAX_TRACKERS = 30;
	/** added to every torrent; helps when an add-on lists only a few trackers */
	private static final List<String> PUBLIC_TRACKERS = Collections.unmodifiableList(Arrays.asList(
			"udp://tracker.opentrackr.org:1337/announce",
			"udp://open.demonii.com:1337/announce",
			"udp://open.stealth.si:80/announce",
			"udp://tracker.torrent.eu.org:451/announce",
			"udp://exodus.desync.com:6969/announce",
			"udp://explodie.org:6969/announce",
			"udp://tracker.dler.org:6969/announce"));
	private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	@NonNull public final String infoHash;
	@Nullable public final String name;
	@NonNull public final List<String> trackers;

	public Magnet(@NonNull String infoHash, @Nullable String name, @NonNull List<String> trackers) {
		this.infoHash = infoHash.toLowerCase(Locale.ROOT);
		this.name = name;
		this.trackers = Collections.unmodifiableList(new ArrayList<>(trackers));
	}

	/** null for anything odd */
	@Nullable
	public static Magnet parse(@Nullable String link) {
		if (link == null || link.length() > Constant.MAX_INTENT_TEXT
				|| !link.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			return null;
		}
		String hash = null;
		String name = null;
		List<String> trackers = new ArrayList<>();
		for (String pair : link.substring(PREFIX.length()).split("&")) {
			int eq = pair.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = pair.substring(0, eq).toLowerCase(Locale.ROOT);
			String value = decode(pair.substring(eq + 1));
			if (value == null) {
				return null;
			}
			switch (key) {
				case "xt":
					if (hash == null && value.regionMatches(true, 0, BTIH, 0, BTIH.length())) {
						hash = toHex(value.substring(BTIH.length()));
					}
					break;
				case "dn":
					name = value.length() <= 300 ? value : value.substring(0, 300);
					break;
				case "tr":
					if (isTracker(value) && trackers.size() < MAX_TRACKERS) {
						trackers.add(value);
					}
					break;
				default:
					break;
			}
		}
		return hash != null ? new Magnet(hash, name, trackers) : null;
	}

	/** sources look like "tracker:udp://...". dht: entries are skipped, DHT is on anyway. */
	@NonNull
	public static List<String> trackersFromSources(@Nullable List<String> sources) {
		List<String> out = new ArrayList<>();
		if (sources == null) {
			return out;
		}
		for (String s : sources) {
			if (s != null && s.startsWith("tracker:")) {
				String tracker = s.substring("tracker:".length());
				if (isTracker(tracker) && out.size() < MAX_TRACKERS) {
					out.add(tracker);
				}
			}
		}
		return out;
	}

	/** the same magnet with the public trackers added after its own */
	@NonNull
	public Magnet withPublicTrackers() {
		List<String> all = new ArrayList<>(trackers);
		for (String tracker : PUBLIC_TRACKERS) {
			if (!all.contains(tracker)) {
				all.add(tracker);
			}
		}
		return new Magnet(infoHash, name, all);
	}

	@NonNull
	public String toUri() {
		StringBuilder uri = new StringBuilder(PREFIX).append("xt=").append(BTIH).append(infoHash);
		if (name != null) {
			uri.append("&dn=").append(encode(name));
		}
		for (String tracker : trackers) {
			uri.append("&tr=").append(encode(tracker));
		}
		return uri.toString();
	}

	static boolean isTracker(@NonNull String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		boolean scheme = lower.startsWith("udp://") || lower.startsWith("http://") || lower.startsWith("https://")
				|| lower.startsWith("wss://");
		return scheme && url.length() <= 500 && !url.contains(" ") && !url.contains("\n");
	}

	/** hex (40 chars) or the old base32 form (32) */
	@Nullable
	static String toHex(@NonNull String hash) {
		if (hash.matches("(?i)[0-9a-f]{40}")) {
			return hash.toLowerCase(Locale.ROOT);
		}
		if (!hash.matches("(?i)[a-z2-7]{32}")) {
			return null;
		}
		String upper = hash.toUpperCase(Locale.ROOT);
		StringBuilder hex = new StringBuilder(40);
		int buffer = 0;
		int bits = 0;
		for (int i = 0; i < upper.length(); i++) {
			buffer = (buffer << 5) | BASE32.indexOf(upper.charAt(i));
			bits += 5;
			while (bits >= 4) {
				bits -= 4;
				hex.append(Character.forDigit((buffer >> bits) & 0xf, 16));
			}
		}
		return hex.toString();
	}

	@Nullable
	private static String decode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException | IllegalArgumentException e) {
			return null;
		}
	}

	private static String encode(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}
}
