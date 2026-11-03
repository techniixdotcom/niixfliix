package app.niixfliix.live;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.ProxyHeaders;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.addon.model.StreamHints;

public final class FreeSports {

	public static final String ADDON_URL = "https://iptv-org.github.io/iptv/categories/sports/manifest.json";
	public static final String PLAYLIST_URL = "https://iptv-org.github.io/iptv/categories/sports.m3u";
	public static final long MAX_BYTES = 4L << 20;
	public static final long FRESH_MS = 6L * 60 * 60 * 1000;
	public static final String TYPE = "tv";
	public static final String CATALOG_ID = "free-sports";
	public static final String ID_PREFIX = "freesports:";
	public static final String MANIFEST_JSON = "{\"id\":\"app.niixfliix.freesports\",\"version\":\"1.0.0\","
			+ "\"name\":\"Free sports channels\",\"description\":\"Free, publicly available sports channels\","
			+ "\"resources\":[\"catalog\",\"meta\",\"stream\"],\"types\":[\"" + TYPE + "\"],"
			+ "\"idPrefixes\":[\"" + ID_PREFIX + "\"],"
			+ "\"catalogs\":[{\"type\":\"" + TYPE + "\",\"id\":\"" + CATALOG_ID + "\",\"name\":\"Sports channels\"}]}";

	private static final String[] LICENSED_HOSTS = {"jmp2.uk", "amagi.tv", "wurl.com", "wurl.tv", "sofast.tv",
			"otteravision.com", "getpublica.com", "zype.com", "pluto.tv", "plex.tv", "samsungtvplus.com",
			"xumo.com", "xumo.tv", "rakuten.tv", "stirr.com", "redbull.tv", "redbull.com", "fifa.com",
			"olympics.com"};
	private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z-]+)=\"([^\"]*)\"");
	private static final Pattern RESOLUTION = Pattern.compile("\\s*\\((\\d{3,4})[pi]\\)");
	private static final Pattern LABEL = Pattern.compile("\\s*\\[[^\\]]*\\]");
	private static final Pattern UNSAFE_ID = Pattern.compile("[^A-Za-z0-9._@-]");

	public static final class Feed {
		@NonNull public final String url;
		@Nullable public final String referrer;
		@Nullable public final String userAgent;
		@NonNull public final String quality;

		Feed(@NonNull String url, @Nullable String referrer, @Nullable String userAgent, @NonNull String quality) {
			this.url = url;
			this.referrer = referrer;
			this.userAgent = userAgent;
			this.quality = quality;
		}
	}

	public static final class Channel {
		@NonNull public final String id;
		@NonNull public final String name;
		@Nullable public final String logo;
		@NonNull public final String country;
		@NonNull public final List<Feed> feeds = new ArrayList<>();

		Channel(@NonNull String id, @NonNull String name, @Nullable String logo, @NonNull String country) {
			this.id = id;
			this.name = name;
			this.logo = logo;
			this.country = country;
		}
	}

	private FreeSports() {
	}

	public static boolean isIt(@Nullable String transportUrl) {
		return ADDON_URL.equals(transportUrl);
	}

	@NonNull
	public static List<Channel> parse(@NonNull String playlist, @NonNull String homeCountry) {
		Map<String, Channel> channels = new LinkedHashMap<>();
		Map<String, String> info = null;
		String name = null;
		String referrer = null;
		String userAgent = null;
		for (String raw : playlist.split("\n")) {
			String line = raw.trim();
			if (line.startsWith("#EXTINF")) {
				info = attributes(line);
				name = nameOf(line);
				referrer = null;
				userAgent = null;
			} else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
				referrer = line.substring("#EXTVLCOPT:http-referrer=".length()).trim();
			} else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
				userAgent = line.substring("#EXTVLCOPT:http-user-agent=".length()).trim();
			} else if (!line.isEmpty() && !line.startsWith("#")) {
				if (info != null && name != null && isLicensed(line)) {
					add(channels, info, name, line, referrer, userAgent);
				}
				info = null;
				name = null;
			}
		}
		List<Channel> out = new ArrayList<>(channels.values());
		String home = homeCountry.toLowerCase(Locale.ROOT);
		out.sort((a, b) -> {
			boolean x = !home.isEmpty() && home.equals(a.country);
			boolean y = !home.isEmpty() && home.equals(b.country);
			if (x != y) {
				return x ? -1 : 1;
			}
			return a.name.compareToIgnoreCase(b.name);
		});
		return out;
	}

	private static void add(Map<String, Channel> channels, Map<String, String> info, String rawName, String url,
			@Nullable String referrer, @Nullable String userAgent) {
		String tvgId = info.get("tvg-id");
		String name = LABEL.matcher(RESOLUTION.matcher(rawName).replaceAll("")).replaceAll("").trim();
		if (name.isEmpty()) {
			return;
		}
		String key = tvgId != null && !tvgId.isEmpty() ? tvgId : name;
		String id = ID_PREFIX + UNSAFE_ID.matcher(key).replaceAll("_");
		Channel channel = channels.get(id);
		if (channel == null) {
			String logo = info.get("tvg-logo");
			channel = new Channel(id, name, logo != null && logo.startsWith("https://") ? logo : null,
					countryOf(tvgId));
			channels.put(id, channel);
		}
		Matcher resolution = RESOLUTION.matcher(rawName);
		String quality = resolution.find() ? resolution.group(1) + "p" : "";
		channel.feeds.add(new Feed(url, empty(referrer), empty(userAgent), quality));
	}

	@Nullable
	private static String empty(@Nullable String value) {
		return value == null || value.isEmpty() ? null : value;
	}

	@NonNull
	static String countryOf(@Nullable String tvgId) {
		if (tvgId == null) {
			return "";
		}
		int at = tvgId.indexOf('@');
		String base = at >= 0 ? tvgId.substring(0, at) : tvgId;
		int dot = base.lastIndexOf('.');
		return dot >= 0 ? base.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
	}

	private static Map<String, String> attributes(String line) {
		Map<String, String> out = new LinkedHashMap<>();
		Matcher m = ATTRIBUTE.matcher(line);
		while (m.find()) {
			out.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2));
		}
		return out;
	}

	@Nullable
	private static String nameOf(String line) {
		int quote = line.lastIndexOf('"');
		int comma = line.indexOf(',', Math.max(quote, 0));
		return comma >= 0 ? line.substring(comma + 1).trim() : null;
	}

	static boolean isLicensed(@NonNull String url) {
		if (!url.regionMatches(true, 0, "https://", 0, 8)) {
			return false;
		}
		int end = url.length();
		for (char c : new char[] {'/', ':', '?', '#'}) {
			int at = url.indexOf(c, 8);
			if (at >= 0 && at < end) {
				end = at;
			}
		}
		String host = url.substring(8, end).toLowerCase(Locale.ROOT);
		if (host.contains("mediatailor.") && host.endsWith(".amazonaws.com")) {
			return true;
		}
		for (String allowed : LICENSED_HOSTS) {
			if (host.equals(allowed) || host.endsWith("." + allowed)) {
				return true;
			}
		}
		return false;
	}

	@NonNull
	public static List<Meta> metas(@NonNull List<Channel> channels) {
		List<Meta> out = new ArrayList<>(channels.size());
		for (Channel c : channels) {
			out.add(meta(c));
		}
		return out;
	}

	@NonNull
	public static Meta meta(@NonNull Channel c) {
		Meta m = new Meta();
		m.id = c.id;
		m.type = TYPE;
		m.name = c.name;
		m.poster = c.logo;
		m.posterShape = "square";
		m.genres = Collections.singletonList("Sports");
		return m;
	}

	@Nullable
	public static Channel find(@NonNull List<Channel> channels, @NonNull String id) {
		for (Channel c : channels) {
			if (c.id.equals(id)) {
				return c;
			}
		}
		return null;
	}

	@NonNull
	public static List<Stream> streams(@NonNull Channel c) {
		List<Stream> out = new ArrayList<>(c.feeds.size());
		for (Feed f : c.feeds) {
			Stream s = new Stream();
			s.name = "Live" + (f.quality.isEmpty() ? "" : "\n" + f.quality);
			s.title = c.name;
			s.url = f.url;
			Map<String, String> headers = new LinkedHashMap<>();
			if (f.referrer != null) {
				headers.put("Referer", f.referrer);
			}
			if (f.userAgent != null) {
				headers.put("User-Agent", f.userAgent);
			}
			if (!headers.isEmpty()) {
				s.behaviorHints = new StreamHints();
				s.behaviorHints.proxyHeaders = new ProxyHeaders();
				s.behaviorHints.proxyHeaders.request = headers;
			}
			out.add(s);
		}
		return out;
	}
}
