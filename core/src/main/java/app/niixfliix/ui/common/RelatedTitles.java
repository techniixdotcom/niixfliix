package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import app.niixfliix.addon.AddonClient;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.SimilarTitle;
import app.niixfliix.addon.model.Meta;

/**
 * The other films that go with a film: the rest of its film series (Jurassic Park, Jurassic World …),
 * the films it follows or is followed by, and its other versions (remakes, other adaptations of the
 * same book). Oldest first, from Wikidata; kept on disk once looked up.
 */
public final class RelatedTitles {

	public static final int MAX_ITEMS = 30;
	private static final Pattern IMDB_ID = Pattern.compile("tt\\d{1,10}");
	private static final String POSTER = "https://images.metahub.space/poster/medium/";

	private RelatedTitles() {
	}

	/** Whether a title gets the row: films with an IMDb id. */
	public static boolean applies(@Nullable String type, @Nullable String id) {
		return Meta.TYPE_MOVIE.equals(type) && id != null && IMDB_ID.matcher(id).matches();
	}

	@WorkerThread
	@NonNull
	public static List<PosterItem> find(@NonNull AddonClient client, @Nullable String type, @Nullable String id,
			@Nullable String language) {
		if (!applies(type, id)) {
			return Collections.emptyList();
		}
		try {
			return toPosters(client.related(id, language), id);
		} catch (IOException | RuntimeException e) {
			return Collections.emptyList();
		}
	}

	@VisibleForTesting
	@NonNull
	static List<PosterItem> toPosters(@NonNull List<SimilarTitle> found, @NonNull String selfId) {
		List<SimilarTitle> sorted = new ArrayList<>(found);
		sorted.sort((a, b) -> {
			long x = ReleaseOrder.parseDate(a.released);
			long y = ReleaseOrder.parseDate(b.released);
			if ((x > 0) != (y > 0)) {
				return x > 0 ? -1 : 1;
			}
			if (x != y) {
				return Long.compare(x, y);
			}
			return a.name.compareToIgnoreCase(b.name);
		});
		List<PosterItem> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		seen.add(selfId);
		for (SimilarTitle t : sorted) {
			if (seen.add(t.imdbId)) {
				out.add(new PosterItem(t.imdbId, Meta.TYPE_MOVIE, t.name, POSTER + t.imdbId + "/img", -1));
				if (out.size() >= MAX_ITEMS) {
					break;
				}
			}
		}
		return out;
	}
}
