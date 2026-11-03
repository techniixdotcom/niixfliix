package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;

public final class ReleaseOrder {

	private static final Pattern YEAR = Pattern.compile("(?<!\\d)(1[89]\\d\\d|2\\d\\d\\d)(?!\\d)");

	private ReleaseOrder() {
	}

	public static long of(@NonNull Meta meta) {
		return parse(meta.released, meta.releaseInfo);
	}

	public static long parse(@Nullable String released, @Nullable String releaseInfo) {
		long exact = parseDate(released);
		return exact != 0 ? exact : parseYear(releaseInfo);
	}

	public static long latestOut(@NonNull Meta meta, long nowMs) {
		long latest = 0;
		for (Video v : meta.videosOrEmpty()) {
			long at = parseDate(v.released);
			if (at > latest && at <= nowMs) {
				latest = at;
			}
		}
		return latest != 0 ? latest : of(meta);
	}

	public static long parseDate(@Nullable String text) {
		if (text == null) {
			return 0;
		}
		String t = text.trim();
		if (t.isEmpty()) {
			return 0;
		}
		long instant = instant(t);
		if (instant != 0) {
			return instant;
		}
		try {
			String day = t.length() >= 10 ? t.substring(0, 10) : t;
			return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
		} catch (DateTimeParseException e) {
			return 0;
		}
	}

	private static long instant(String text) {
		try {
			return OffsetDateTime.parse(text).toInstant().toEpochMilli();
		} catch (DateTimeParseException e) {
			return 0;
		}
	}

	public static long parseYear(@Nullable String text) {
		if (text == null) {
			return 0;
		}
		Matcher m = YEAR.matcher(text);
		if (!m.find()) {
			return 0;
		}
		int year = Integer.parseInt(m.group(1));
		return LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
	}

	@NonNull
	public static List<Meta> newestFirst(@NonNull List<Meta> metas) {
		List<Meta> sorted = new ArrayList<>(metas);
		sortNewestFirst(sorted, ReleaseOrder::of);
		return sorted;
	}

	public static <T> void sortNewestFirst(@NonNull List<T> items, @NonNull ToLongFunction<T> date) {
		items.sort(newestFirst(date));
	}

	@NonNull
	public static <T> Comparator<T> newestFirst(@NonNull ToLongFunction<T> date) {
		return (a, b) -> {
			long x = date.applyAsLong(a);
			long y = date.applyAsLong(b);
			if (x == 0 || y == 0) {
				return x == y ? 0 : x == 0 ? 1 : -1;
			}
			return Long.compare(y, x);
		};
	}
}
