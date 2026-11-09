package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import app.niixfliix.addon.AddonClient;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.SimilarTitle;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;

/**
 * "More like this" under a title: titles Wikidata links to it (same series, same source, director,
 * writer, specific genre, cast), then titles from the add-ons' catalogs of the same genres.
 * <p>
 * Wikidata's query can take a long while, so it never holds the row up: it starts the moment a
 * title's page opens (or earlier, for titles likely to be opened next: {@link #warm}), the genre
 * titles show as soon as the catalogs answer, and the Wikidata ones are put in front when they
 * come. Once looked up, a title's answer is kept on disk, so the next time it's there at once.
 */
public final class MoreLikeThis {

	public static final int MAX_ITEMS = 24;
	private static final int ENOUGH_FROM_WIKIDATA = 10;
	private static final int MIN_WIKIDATA_SCORE = 4;
	private static final int WIKIDATA_STYLE_MISMATCH = 120;
	private static final int WIKIDATA_PER_YEAR_APART = 2;
	private static final Pattern IMDB_ID = Pattern.compile("tt\\d{1,10}");
	private static final String POSTER = "https://images.metahub.space/poster/medium/";
	private static final long GENRE_WAIT_MS = 20_000;
	/** How long the genre titles wait for Wikidata before showing on their own (a kept answer is quicker). */
	private static final long QUICK_WAIT_MS = 1500;
	private static final ExecutorService WIKIDATA = Executors.newFixedThreadPool(2, daemon("wikidata"));
	/** Look-ups for titles nobody opened yet, one at a time, so they never hold up an opened one. */
	private static final ExecutorService WARMING = Executors.newSingleThreadExecutor(daemon("wikidata-warm"));
	private static final ConcurrentHashMap<String, CompletableFuture<List<SimilarTitle>>> RUNNING =
			new ConcurrentHashMap<>();
	private static final Set<String> WARMED = ConcurrentHashMap.newKeySet();
	private static final int MAX_WARMED = 200;
	private static final int FETCHER_THREADS = 3;
	private static final ExecutorService FETCHERS = Executors.newFixedThreadPool(FETCHER_THREADS, r -> {
		Thread t = new Thread(r, "more-like-this");
		t.setDaemon(true);
		return t;
	});
	private static final int MAX_GENRES = 3;
	private static final long YEAR_MS = 365L * 24 * 60 * 60 * 1000;
	private static final int SHARED_GENRE = 100;
	private static final int OTHER_GENRE = 15;
	private static final int STYLE_MISMATCH = 250;
	private static final int PER_YEAR_APART = 2;
	private static final int MAX_YEARS_COUNTED = 25;
	private static final Set<String> STYLE_GENRES = new HashSet<>(Arrays.asList(
			"animation", "anime", "documentary", "family", "kids", "children", "reality", "reality-tv", "talk-show",
			"news", "game-show", "short"));

	private MoreLikeThis() {
	}

	@NonNull
	private static ThreadFactory daemon(@NonNull String name) {
		return r -> {
			Thread t = new Thread(r, name);
			t.setDaemon(true);
			return t;
		};
	}

	private static boolean wikidataKnows(@Nullable String type, @Nullable String id) {
		return id != null && (Meta.TYPE_SERIES.equals(type) || Meta.TYPE_MOVIE.equals(type))
				&& IMDB_ID.matcher(id).matches();
	}

	/**
	 * Starts looking the title up on Wikidata, or joins the look-up already running for it, and returns
	 * at once. A title's page calls this as it opens, before its details are in, so the slow part has a
	 * head start; {@link #find} then picks up the same look-up.
	 */
	@NonNull
	public static CompletableFuture<List<SimilarTitle>> lookUp(@NonNull AddonClient client, @Nullable String type,
			@Nullable String id, @Nullable String language) {
		return lookUp(client, type, id, language, WIKIDATA);
	}

	/**
	 * Looks a title up ahead of time, quietly and one at a time: for titles likely to be opened next
	 * (the banner on Home, Continue watching), so their row is there the moment they are. Once per
	 * title while the app runs; the answer is kept on disk.
	 */
	public static void warm(@NonNull AddonClient client, @Nullable String type, @Nullable String id,
			@Nullable String language) {
		if (!wikidataKnows(type, id) || WARMED.size() >= MAX_WARMED || !WARMED.add(key(type, id, language))) {
			return;
		}
		lookUp(client, type, id, language, WARMING);
	}

	@NonNull
	private static CompletableFuture<List<SimilarTitle>> lookUp(@NonNull AddonClient client, @Nullable String type,
			@Nullable String id, @Nullable String language, @NonNull ExecutorService on) {
		if (!wikidataKnows(type, id)) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}
		String key = key(type, id, language);
		CompletableFuture<List<SimilarTitle>> mine = new CompletableFuture<>();
		CompletableFuture<List<SimilarTitle>> running = RUNNING.putIfAbsent(key, mine);
		if (running != null) {
			return running;
		}
		boolean series = Meta.TYPE_SERIES.equals(type);
		try {
			on.execute(() -> {
				try {
					mine.complete(client.similar(id, series, language));
				} catch (IOException | RuntimeException e) {
					mine.completeExceptionally(e);
				} finally {
					RUNNING.remove(key, mine);
				}
			});
		} catch (RuntimeException e) {
			RUNNING.remove(key, mine);
			mine.completeExceptionally(e);
		}
		return mine;
	}

	@NonNull
	private static String key(@Nullable String type, @Nullable String id, @Nullable String language) {
		return type + "|" + id + "|" + (language != null ? language : "");
	}

	@NonNull
	public static List<CatalogSource> sourcesOf(@NonNull AddonRepository addons, @NonNull String type) {
		List<CatalogSource> out = new ArrayList<>();
		for (InstalledAddon addon : addons.active()) {
			if (addon.manifest == null) {
				continue;
			}
			for (Catalog c : addon.manifest.validCatalogs()) {
				if (c.worksForDiscover() && type.equals(c.type)) {
					out.add(new CatalogSource(addon, c));
				}
			}
		}
		return out;
	}

	/** Where {@link #find} hands the row's titles. */
	public interface Sink {
		/**
		 * The titles to show. Called once or twice, in order, on a background thread: {@code last} is the
		 * final list, which may be empty (then there is no row).
		 */
		void show(@NonNull List<PosterItem> items, boolean last);
	}

	/**
	 * Finds the row's titles and hands them to {@code sink}. When Wikidata answers within
	 * {@link #QUICK_WAIT_MS} (it does when its answer is kept from before), that's one call with
	 * everything. Otherwise the titles from the genre catalogs go first, as soon as they're in, and the
	 * list with the Wikidata titles in front follows when it answers. Returns once the first call is
	 * made; it doesn't wait for Wikidata, so it never holds a thread for long.
	 */
	@WorkerThread
	public static void find(@NonNull AddonRepository addons, @NonNull AddonClient client,
			@Nullable String type, @Nullable String id, @Nullable List<String> genres, long released,
			@Nullable String language, @NonNull Sink sink) {
		if (type == null || id == null || Catalog.isLiveType(type)) {
			sink.show(Collections.emptyList(), true);
			return;
		}
		List<String> titleGenres = genres != null ? genres : Collections.emptyList();
		CompletableFuture<List<SimilarTitle>> wikidata = lookUp(client, type, id, language);
		List<Future<List<Meta>>> genreLists = startGenreLists(addons, client, type, titleGenres);

		List<SimilarTitle> found = wikidataWithin(wikidata, QUICK_WAIT_MS);
		if (found != null) {
			List<PosterItem> related = fromWikidata(found, id, type, titleGenres, released);
			if (related.size() >= ENOUGH_FROM_WIKIDATA || genreLists.isEmpty()) {
				cancel(genreLists);
				sink.show(related, true);
				return;
			}
			sink.show(merge(related, rank(id, titleGenres, released, awaitAll(genreLists))), true);
			return;
		}

		List<PosterItem> byGenre = genreLists.isEmpty() ? Collections.emptyList()
				: rank(id, titleGenres, released, awaitAll(genreLists));
		found = wikidataWithin(wikidata, 0);
		if (found != null) {
			sink.show(merge(fromWikidata(found, id, type, titleGenres, released), byGenre), true);
			return;
		}
		// First the genre titles; the Wikidata ones follow (handed over after this call, so in order)
		sink.show(byGenre, false);
		wikidata.whenComplete((answer, failure) -> {
			List<PosterItem> related = answer != null
					? fromWikidata(answer, id, type, titleGenres, released) : Collections.emptyList();
			sink.show(related.isEmpty() ? byGenre : merge(related, byGenre), true);
		});
	}

	/** Wikidata's answer if it comes within {@code ms}; null if not yet, empty if it failed. */
	@Nullable
	private static List<SimilarTitle> wikidataWithin(@NonNull CompletableFuture<List<SimilarTitle>> wikidata,
			long ms) {
		try {
			List<SimilarTitle> found = wikidata.get(ms, TimeUnit.MILLISECONDS);
			return found != null ? found : Collections.emptyList();
		} catch (TimeoutException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Collections.emptyList();
		} catch (ExecutionException | RuntimeException e) {
			return Collections.emptyList();
		}
	}

	@NonNull
	private static List<List<Meta>> awaitAll(@NonNull List<Future<List<Meta>>> genreLists) {
		List<List<Meta>> lists = new ArrayList<>();
		for (Future<List<Meta>> f : genreLists) {
			lists.add(await(f));
		}
		return lists;
	}

	private static void cancel(@NonNull List<Future<List<Meta>>> genreLists) {
		for (Future<List<Meta>> f : genreLists) {
			f.cancel(true);
		}
	}

	@NonNull
	private static List<Meta> await(@NonNull Future<List<Meta>> f) {
		try {
			List<Meta> metas = f.get(GENRE_WAIT_MS, TimeUnit.MILLISECONDS);
			return metas != null ? metas : Collections.emptyList();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			f.cancel(true);
			return Collections.emptyList();
		} catch (ExecutionException | TimeoutException | RuntimeException e) {
			f.cancel(true);
			return Collections.emptyList();
		}
	}

	@NonNull
	static List<PosterItem> fromWikidata(@NonNull List<SimilarTitle> found, @NonNull String selfId,
			@NonNull String type, @NonNull List<String> titleGenres, long titleReleased) {
		boolean titleStyled = false;
		for (String g : titleGenres) {
			if (g != null && STYLE_GENRES.contains(key(g))) {
				titleStyled = true;
				break;
			}
		}
		List<SimilarTitle> kept = new ArrayList<>();
		Map<SimilarTitle, Integer> points = new LinkedHashMap<>();
		for (SimilarTitle t : found) {
			if (t.score < MIN_WIKIDATA_SCORE || t.imdbId.equals(selfId)) {
				continue;
			}
			int value = t.score * 10;
			if (t.styled != titleStyled) {
				value -= WIKIDATA_STYLE_MISMATCH;
			}
			long theirs = ReleaseOrder.parseDate(t.released);
			if (titleReleased > 0 && theirs > 0) {
				long years = Math.abs(titleReleased - theirs) / YEAR_MS;
				value -= (int) Math.min(years, MAX_YEARS_COUNTED) * WIKIDATA_PER_YEAR_APART;
			}
			kept.add(t);
			points.put(t, value);
		}
		kept.sort((a, b) -> Integer.compare(points.get(b), points.get(a)));
		List<PosterItem> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (SimilarTitle t : kept) {
			if (seen.add(t.imdbId)) {
				out.add(new PosterItem(t.imdbId, type, t.name, POSTER + t.imdbId + "/img", -1));
				if (out.size() >= MAX_ITEMS) {
					break;
				}
			}
		}
		return out;
	}

	@NonNull
	static List<PosterItem> merge(@NonNull List<PosterItem> first, @NonNull List<PosterItem> then) {
		List<PosterItem> out = new ArrayList<>(first);
		Set<String> seen = new HashSet<>();
		for (PosterItem p : first) {
			seen.add(p.id);
		}
		for (PosterItem p : then) {
			if (out.size() >= MAX_ITEMS) {
				break;
			}
			if (seen.add(p.id)) {
				out.add(p);
			}
		}
		return out.size() > MAX_ITEMS ? new ArrayList<>(out.subList(0, MAX_ITEMS)) : out;
	}

	@NonNull
	private static List<Future<List<Meta>>> startGenreLists(@NonNull AddonRepository addons,
			@NonNull AddonClient client, @NonNull String type, @NonNull List<String> genres) {
		List<Future<List<Meta>>> out = new ArrayList<>();
		if (genres.isEmpty()) {
			return out;
		}
		DiscoverGenres catalogs = new DiscoverGenres(sourcesOf(addons, type));
		Set<String> used = new HashSet<>();
		for (String genre : genres) {
			if (used.size() >= MAX_GENRES || genre == null) {
				continue;
			}
			CatalogSource source = catalogs.sourceFor(genre);
			if (source == null || !used.add(key(genre))) {
				continue;
			}
			out.add(FETCHERS.submit(() -> client.catalog(source.addon, source.catalog.type, source.catalog.id,
					Collections.singletonMap(Catalog.EXTRA_GENRE, genre))));
		}
		return out;
	}

	@NonNull
	static List<PosterItem> rank(@NonNull String selfId, @NonNull List<String> titleGenres, long titleReleased,
			@NonNull List<List<Meta>> lists) {
		Set<String> wanted = new HashSet<>();
		for (String g : titleGenres) {
			if (g != null && !g.trim().isEmpty()) {
				wanted.add(key(g));
			}
		}
		Map<String, Candidate> candidates = new LinkedHashMap<>();
		for (List<Meta> list : lists) {
			if (list == null) {
				continue;
			}
			Set<String> seenInList = new HashSet<>();
			for (int position = 0; position < list.size(); position++) {
				Meta m = list.get(position);
				if (m == null || !m.isValid() || selfId.equals(m.id) || !seenInList.add(m.id)) {
					continue;
				}
				Candidate c = candidates.get(m.id);
				if (c == null) {
					c = new Candidate(m, candidates.size());
					candidates.put(m.id, c);
				}
				c.lists++;
				c.bestPosition = Math.min(c.bestPosition, position);
			}
		}
		List<Candidate> ranked = new ArrayList<>(candidates.values());
		for (Candidate c : ranked) {
			c.score = score(c, wanted, titleReleased);
		}
		ranked.sort((a, b) -> {
			if (a.score != b.score) {
				return Integer.compare(b.score, a.score);
			}
			if (a.bestPosition != b.bestPosition) {
				return Integer.compare(a.bestPosition, b.bestPosition);
			}
			return Integer.compare(a.order, b.order);
		});
		List<PosterItem> out = new ArrayList<>();
		for (Candidate c : ranked) {
			out.add(PosterItem.of(c.meta));
			if (out.size() >= MAX_ITEMS) {
				break;
			}
		}
		return out;
	}

	private static int score(@NonNull Candidate c, @NonNull Set<String> wanted, long titleReleased) {
		int shared = c.lists;
		int other = 0;
		boolean styleMismatch = false;
		List<String> candidateGenres = c.meta.allGenres();
		if (!candidateGenres.isEmpty()) {
			Set<String> theirs = new HashSet<>();
			for (String g : candidateGenres) {
				if (g != null && !g.trim().isEmpty()) {
					theirs.add(key(g));
				}
			}
			int known = 0;
			for (String g : theirs) {
				if (wanted.contains(g)) {
					known++;
				} else {
					other++;
				}
			}
			shared = Math.max(shared, known);
			for (String style : STYLE_GENRES) {
				if (wanted.contains(style) != theirs.contains(style)) {
					styleMismatch = true;
					break;
				}
			}
		}
		int score = shared * SHARED_GENRE - other * OTHER_GENRE;
		if (styleMismatch) {
			score -= STYLE_MISMATCH;
		}
		long theirReleased = ReleaseOrder.of(c.meta);
		if (titleReleased > 0 && theirReleased > 0) {
			long years = Math.abs(titleReleased - theirReleased) / YEAR_MS;
			score -= (int) Math.min(years, MAX_YEARS_COUNTED) * PER_YEAR_APART;
		}
		return score;
	}

	@NonNull
	private static String key(@NonNull String genre) {
		return genre.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
	}

	private static final class Candidate {
		@NonNull final Meta meta;
		final int order;
		int lists;
		int bestPosition = Integer.MAX_VALUE;
		int score;

		Candidate(@NonNull Meta meta, int order) {
			this.meta = meta;
			this.order = order;
		}
	}
}
