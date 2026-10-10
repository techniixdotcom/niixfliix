package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Best quality first, then cached before uncached, then direct before torrent. Ties keep the add-on's order. */
public final class StreamRanker {

	/** The only qualities the streams list shows and auto-play picks from, best first. */
	public static final List<Quality> SHOWN = Collections.unmodifiableList(Arrays.asList(
			Quality.P1440, Quality.P1080, Quality.P720, Quality.P480));

	private StreamRanker() {
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

	/**
	 * One stream per shown quality, from any add-on: the most seeders among what can start right away.
	 * Uncached debrid only if nothing else has that quality, zero seeders never. Ties keep the add-on's order.
	 */
	@NonNull
	public static List<StreamInfo> bestPerQuality(@NonNull List<StreamInfo> streams) {
		Comparator<StreamInfo> better = Comparator
				.comparing((StreamInfo s) -> !isPlayable(s))
				.thenComparingInt(s -> -s.seeders)
				.thenComparingInt(StreamRanker::availabilityRank);
		List<StreamInfo> best = new ArrayList<>();
		for (Quality quality : SHOWN) {
			StreamInfo top = null;
			for (StreamInfo s : streams) {
				// zero seeders is dead, never pick it
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

	/**
	 * Starts right away: a ready link, or a torrent for the built-in engine. Uncached debrid has to wait,
	 * and anything reported with zero seeders is dead.
	 */
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

	/**
	 * Same binge group wins (keeps release/audio for the next ep), else the best stream of the preferred
	 * quality, then lower, then higher. Only looks at the shown qualities.
	 */
	@Nullable
	public static StreamInfo pick(@NonNull List<StreamInfo> sorted, @NonNull Quality preferred,
			@Nullable String bingeGroup) {
		if (bingeGroup != null) {
			for (StreamInfo s : sorted) {
				if (bingeGroup.equals(s.stream.bingeGroup()) && SHOWN.contains(s.quality)
						&& isPlayable(s)) {
					return s;
				}
			}
		}
		List<StreamInfo> best = bestPerQuality(sorted);
		// 4K from an older version starts at the top, "other" at the bottom
		int start = 0;
		while (start < SHOWN.size() && SHOWN.get(start).ordinal() < preferred.ordinal()) {
			start++;
		}
		for (int i = start; i < SHOWN.size(); i++) {
			StreamInfo s = firstPlayable(best, SHOWN.get(i));
			if (s != null) {
				return s;
			}
		}
		for (int i = start - 1; i >= 0; i--) {
			StreamInfo s = firstPlayable(best, SHOWN.get(i));
			if (s != null) {
				return s;
			}
		}
		return null;
	}

	/** After a playback error: the next one of the same quality, going down by seeders. */
	@Nullable
	public static StreamInfo nextSameQuality(@NonNull List<StreamInfo> sorted, @NonNull StreamInfo failed) {
		List<StreamInfo> same = new ArrayList<>();
		for (StreamInfo s : sorted) {
			if (s.quality == failed.quality && (isPlayable(s) || s.key().equals(failed.key()))) {
				same.add(s);
			}
		}
		same.sort(Comparator.comparingInt((StreamInfo s) -> -s.seeders));
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
