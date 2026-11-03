package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Stream;

public final class StreamFilter {

	private static final Pattern PAID = Pattern.compile(
			"\\b(premium|vip|pro|paid|plus)[ -]?(only|required|needed|members?|account|plan|subscription|users?|tier"
					+ "|feature)\\b"
					+ "|\\b(get|go|buy|become|requires?|need|with)\\s+(an?\\s+)?(premium|vip|pro|paid)\\b"
					+ "|\\bupgrade (to|your|now|plan|account)\\b"
					+ "|\\bsubscri(be|ption) (to|required|needed|now)\\b"
					+ "|\\b(purchase|buy) (a |an )?(plan|subscription|access|token|key|premium)\\b"
					+ "|\\bdonat(e|ion) (to|required|needed)\\b"
					+ "|\\bfree trial\\b"
					+ "|\\b(api|access) key (is )?(required|missing|invalid|needed)\\b"
					+ "|\\btoken (is )?(required|missing|invalid|expired)\\b"
					+ "|\\binvalid (token|api key|key)\\b"
					+ "|\\b(sign|log)[ -]?in (is )?(required|needed|to (watch|continue|play))\\b"
					+ "|\\bnot configured\\b"
					+ "|\\bconfigure (the |this )?add-?on\\b");
	private static final Pattern TRAILER_LABEL = Pattern.compile(
			"^(official |watch (the )?)?(trailer|teaser|preview|sample|clip)s?( \\d+)?$");
	private static final Pattern NOT_LETTERS = Pattern.compile("[^\\p{L}\\p{N} ]+");
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final String[] TRAILER_HOSTS = {"youtube.com", "youtu.be", "googlevideo.com", "ytimg.com",
			"vimeo.com", "vimeocdn.com", "dailymotion.com"};
	private static final long MIN_MOVIE_BYTES = 60L << 20;
	private static final long MIN_EPISODE_BYTES = 20L << 20;
	private static final Pattern LOW_SOURCE = Pattern.compile("(?i)(?<![a-z0-9])(cam|camrip|hdcam|hqcam|ts|hdts|tsrip"
			+ "|telesync|pdvd|predvd|tc|hdtc|telecine|scr|screener|dvdscr|dvdscreener|bdscr|webscr|r5|r6|workprint"
			+ "|upscaled?|ai ?upscaled?)(?![a-z0-9])");
	private static final Pattern NAME_PART_END = Pattern.compile("(?i)(?<![a-z0-9])((19|20)\\d\\d|s\\d{1,2}\\s?e\\d{1,3}"
			+ "|\\d{1,2}x\\d{2,3}|2160p|1440p|1080p|720p|576p|480p|4k|uhd)(?![a-z0-9])");
	private static final Pattern EXTENSION = Pattern.compile("(?i)\\.(mkv|mp4|m4v|avi|webm|ts|m2ts|mov|wmv)$");
	private static final long MB = 1L << 20;
	private static final long[] MIN_MOVIE_BYTES_BY_QUALITY = {2560 * MB, 1536 * MB, 900 * MB, 450 * MB, 250 * MB};
	private static final long[] MIN_EPISODE_BYTES_BY_QUALITY = {1024 * MB, 600 * MB, 250 * MB, 150 * MB, 80 * MB};

	private StreamFilter() {
	}

	public static boolean isPlaceholder(@NonNull StreamInfo info, @NonNull String type) {
		Stream s = info.stream;
		if (s.ytId != null && !s.ytId.isEmpty()) {
			return true;
		}
		if (s.url == null && s.infoHash == null && s.externalUrl != null) {
			return true;
		}
		if (isTrailerHost(s.url) || pathSaysTrailer(s.url)) {
			return true;
		}
		String text = lower(s.name) + "\n" + lower(s.title) + "\n" + lower(s.description);
		if (PAID.matcher(text).find()) {
			return true;
		}
		if (isTrailerLabel(s.name) || isTrailerLabel(s.title) || isTrailerLabel(s.description)) {
			return true;
		}
		long size = info.sizeBytes > 0 ? info.sizeBytes
				: s.behaviorHints != null && s.behaviorHints.videoSize != null ? s.behaviorHints.videoSize : -1;
		if (size > 0) {
			if (Meta.TYPE_MOVIE.equals(type) && size < MIN_MOVIE_BYTES) {
				return true;
			}
			return Meta.TYPE_SERIES.equals(type) && size < MIN_EPISODE_BYTES;
		}
		return false;
	}

	public static boolean isLowQuality(@NonNull StreamInfo info, @NonNull String type) {
		if (Catalog.isLiveType(type)) {
			return false;
		}
		if (info.quality == Quality.OTHER) {
			return true;
		}
		Stream s = info.stream;
		for (String text : new String[] {s.name, s.title, s.description, info.releaseName, info.fileName}) {
			if (text == null) {
				continue;
			}
			for (String line : text.split("\n")) {
				if (LOW_SOURCE.matcher(afterTitle(EXTENSION.matcher(line.trim()).replaceFirst(""))).find()) {
					return true;
				}
			}
		}
		return tooSmallFor(info, type);
	}

	@NonNull
	static String afterTitle(@NonNull String releaseName) {
		Matcher m = NAME_PART_END.matcher(releaseName);
		return m.find() ? releaseName.substring(m.start()) : releaseName;
	}

	private static boolean tooSmallFor(StreamInfo info, String type) {
		long size = info.sizeBytes > 0 ? info.sizeBytes : info.stream.behaviorHints != null
				&& info.stream.behaviorHints.videoSize != null ? info.stream.behaviorHints.videoSize : -1;
		if (size <= 0 || info.quality.ordinal() >= MIN_MOVIE_BYTES_BY_QUALITY.length) {
			return false;
		}
		if (Meta.TYPE_MOVIE.equals(type)) {
			return size < MIN_MOVIE_BYTES_BY_QUALITY[info.quality.ordinal()];
		}
		return Meta.TYPE_SERIES.equals(type) && size < MIN_EPISODE_BYTES_BY_QUALITY[info.quality.ordinal()];
	}

	static boolean isTrailerLabel(@Nullable String label) {
		if (label == null) {
			return false;
		}
		String plain = SPACES.matcher(NOT_LETTERS.matcher(label.toLowerCase(Locale.ROOT)).replaceAll(" "))
				.replaceAll(" ").trim();
		return TRAILER_LABEL.matcher(plain).matches();
	}

	private static boolean isTrailerHost(@Nullable String url) {
		if (url == null) {
			return false;
		}
		String host = StremioUrl.hostOf(url);
		if (host == null) {
			return false;
		}
		for (String trailerHost : TRAILER_HOSTS) {
			if (host.equals(trailerHost) || host.endsWith("." + trailerHost)) {
				return true;
			}
		}
		return false;
	}

	private static boolean pathSaysTrailer(@Nullable String url) {
		if (url == null) {
			return false;
		}
		int start = url.indexOf("://");
		int slash = url.indexOf('/', start >= 0 ? start + 3 : 0);
		if (slash < 0) {
			return false;
		}
		int end = url.length();
		for (char c : new char[] {'?', '#'}) {
			int at = url.indexOf(c, slash);
			if (at >= 0 && at < end) {
				end = at;
			}
		}
		String path = url.substring(slash, end).toLowerCase(Locale.ROOT);
		for (String segment : path.split("/")) {
			String name = segment.replaceFirst("\\.[a-z0-9]{2,4}$", "");
			if (name.equals("trailer") || name.equals("trailers") || name.equals("teaser") || name.equals("sample")
					|| name.equals("preview")) {
				return true;
			}
		}
		return false;
	}

	private static String lower(@Nullable String text) {
		return text != null ? text.toLowerCase(Locale.ROOT) : "";
	}
}
