package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class StreamRanker {

	public interface DecodeCheck {
		boolean isSmooth(@NonNull StreamInfo stream);
	}

	public static final List<Quality> SHOWN = Collections.unmodifiableList(Arrays.asList(
			Quality.P1440, Quality.P1080, Quality.P720, Quality.P480));
	public static final long AUTOPLAY_GRACE_MS = 2_000;
	private static final int MAX_LIVE_EXTRA = 3;

	private static volatile DecodeCheck decodeCheck = stream -> true;

	private StreamRanker() {
	}

	public static void setDecodeCheck(@NonNull DecodeCheck check) {
		decodeCheck = check;
	}

	public static boolean isSmooth(@NonNull StreamInfo stream) {
		try {
			return decodeCheck.isSmooth(stream);
		} catch (RuntimeException e) {
			return true;
		}
	}

	@NonNull
	public static List<StreamInfo> sort(@NonNull List<StreamInfo> streams) {
		List<StreamInfo> sorted = new ArrayList<>(streams);
		sorted.sort(Comparator
				.comparingInt((StreamInfo s) -> s.quality.ordinal())
				.thenComparingInt(StreamRanker::availabilityRank)
				.thenComparingInt(s -> s.kind.ordinal()));
		return sorted;
	}

	@NonNull
	public static List<StreamInfo> bestPerQuality(@NonNull List<StreamInfo> streams) {
		Comparator<StreamInfo> better = Comparator
				.comparing((StreamInfo s) -> !isPlayable(s))
				.thenComparing(s -> !isSmooth(s))
				.thenComparingInt(s -> -s.seeders)
				.thenComparingInt(StreamRanker::availabilityRank);
		List<StreamInfo> best = new ArrayList<>();
		for (Quality quality : SHOWN) {
			StreamInfo top = null;
			for (StreamInfo s : streams) {
				if (s.quality == quality && s.seeders != 0 && (top == null || better.compare(s, top) < 0)) {
					top = s;
				}
			}
			if (top != null) {
				best.add(top);
			}
		}
		return best;
	}

	@NonNull
	public static List<StreamInfo> bestPerQuality(@NonNull List<StreamInfo> sorted, boolean live) {
		List<StreamInfo> best = bestPerQuality(sorted);
		if (!live) {
			return best;
		}
		int extra = 0;
		for (StreamInfo s : sorted) {
			if (extra >= MAX_LIVE_EXTRA) {
				break;
			}
			if (!SHOWN.contains(s.quality) && isPlayable(s)) {
				best.add(s);
				extra++;
			}
		}
		return best;
	}

	@Nullable
	public static StreamInfo pick(@NonNull List<StreamInfo> sorted, @NonNull Quality preferred,
			@Nullable String bingeGroup, boolean live) {
		StreamInfo pick = pick(sorted, preferred, bingeGroup);
		if (pick != null || !live) {
			return pick;
		}
		for (StreamInfo s : sorted) {
			if (isPlayable(s) && isSmooth(s)) {
				return s;
			}
		}
		for (StreamInfo s : sorted) {
			if (isPlayable(s)) {
				return s;
			}
		}
		return null;
	}

	public static boolean canAutoplayEarly(@NonNull List<StreamInfo> best, @NonNull Quality preferred) {
		for (StreamInfo s : best) {
			if (s.quality == preferred && isPlayable(s) && isSmooth(s)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isPlayable(@NonNull StreamInfo s) {
		if (s.seeders == 0) {
			return false;
		}
		switch (s.kind) {
			case DIRECT:
				return !s.isUncachedDebrid();
			case TORRENT:
				return true;
			default:
				return false;
		}
	}

	@Nullable
	public static StreamInfo pick(@NonNull List<StreamInfo> sorted, @NonNull Quality preferred,
			@Nullable String bingeGroup) {
		List<StreamInfo> best = bestPerQuality(sorted);
		StreamInfo smooth = pick(sorted, best, preferred, bingeGroup, true);
		return smooth != null ? smooth : pick(sorted, best, preferred, bingeGroup, false);
	}

	@Nullable
	private static StreamInfo pick(List<StreamInfo> sorted, List<StreamInfo> best, Quality preferred,
			@Nullable String bingeGroup, boolean smoothOnly) {
		if (bingeGroup != null) {
			for (StreamInfo s : sorted) {
				if (bingeGroup.equals(s.stream.bingeGroup()) && s.quality == preferred && isPlayable(s)
						&& (!smoothOnly || isSmooth(s))) {
					return s;
				}
			}
		}
		int start = 0;
		while (start < SHOWN.size() && SHOWN.get(start).ordinal() < preferred.ordinal()) {
			start++;
		}
		for (int i = start; i < SHOWN.size(); i++) {
			StreamInfo s = firstPlayable(best, SHOWN.get(i));
			if (s != null && (!smoothOnly || isSmooth(s))) {
				return s;
			}
		}
		for (int i = start - 1; i >= 0; i--) {
			StreamInfo s = firstPlayable(best, SHOWN.get(i));
			if (s != null && (!smoothOnly || isSmooth(s))) {
				return s;
			}
		}
		return null;
	}

	@Nullable
	public static StreamInfo nextSameQuality(@NonNull List<StreamInfo> sorted, @NonNull StreamInfo failed) {
		List<StreamInfo> same = new ArrayList<>();
		for (StreamInfo s : sorted) {
			if (s.quality == failed.quality && (isPlayable(s) || s.key().equals(failed.key()))) {
				same.add(s);
			}
		}
		same.sort(Comparator.comparing((StreamInfo s) -> !isSmooth(s)).thenComparingInt(s -> -s.seeders));
		for (int i = 0; i < same.size() - 1; i++) {
			if (same.get(i).key().equals(failed.key())) {
				return same.get(i + 1);
			}
		}
		return null;
	}

	@Nullable
	private static StreamInfo firstPlayable(List<StreamInfo> streams, Quality quality) {
		for (StreamInfo s : streams) {
			if (s.quality == quality && isPlayable(s)) {
				return s;
			}
		}
		return null;
	}

	private static int availabilityRank(StreamInfo s) {
		if (Boolean.TRUE.equals(s.cached)) {
			return 0;
		}
		if (s.cached == null) {
			return 1;
		}
		return 2;
	}
}
