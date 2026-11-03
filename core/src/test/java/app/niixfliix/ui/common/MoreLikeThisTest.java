package app.niixfliix.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.SimilarTitle;
import app.niixfliix.addon.model.Meta;

public class MoreLikeThisTest {

	private static Meta meta(String id, String year, String... genres) {
		Meta m = new Meta();
		m.id = id;
		m.type = "movie";
		m.name = "Name " + id;
		m.releaseInfo = year;
		m.genres = genres.length == 0 ? null : Arrays.asList(genres);
		return m;
	}

	private static List<String> ids(List<PosterItem> items) {
		List<String> out = new ArrayList<>();
		for (PosterItem p : items) {
			out.add(p.id);
		}
		return out;
	}

	@Test
	public void titlesSharingMoreGenresComeFirstAndCartoonsDontMixIn() {
		List<String> titleGenres = Arrays.asList("Comedy", "Drama");
		long released = ReleaseOrder.parseYear("2026");
		List<Meta> comedy = Arrays.asList(
				meta("sheep", "2026", "Animation", "Comedy", "Family"),
				meta("onlyComedy", "2025", "Comedy"),
				meta("both", "2024", "Comedy", "Drama"),
				meta("self", "2026", "Comedy", "Drama"));
		List<Meta> drama = Arrays.asList(
				meta("onlyDrama", "2026", "Drama"),
				meta("both", "2024", "Comedy", "Drama"),
				meta("oldBoth", "1975", "Comedy", "Drama"));
		List<String> order = ids(MoreLikeThis.rank("self", titleGenres, released, Arrays.asList(comedy, drama)));
		assertEquals("both", order.get(0));
		assertEquals("oldBoth", order.get(1));
		assertEquals("sheep", order.get(order.size() - 1));
		assertFalse(order.contains("self"));
	}

	@Test
	public void withoutGenresOnTheCardsAppearingInMoreListsWins() {
		List<Meta> a = Arrays.asList(meta("x", null), meta("y", null));
		List<Meta> b = Arrays.asList(meta("y", null), meta("z", null));
		List<String> order = ids(MoreLikeThis.rank("self", Arrays.asList("Action", "Thriller"), 0, Arrays.asList(a, b)));
		assertEquals(Arrays.asList("y", "x", "z"), order);
	}

	@Test
	public void theRowIsCappedAndBrokenEntriesSkipped() {
		List<Meta> many = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			many.add(meta("m" + i, "2020", "Action"));
		}
		many.add(new Meta());
		many.add(null);
		List<PosterItem> items = MoreLikeThis.rank("x", Collections.singletonList("Action"), 0,
				Arrays.asList(many, null));
		assertEquals(MoreLikeThis.MAX_ITEMS, items.size());
		assertTrue(MoreLikeThis.rank("x", Collections.singletonList("Action"), 0, new ArrayList<>()).isEmpty());
	}

	@Test
	public void wikidataMatchesComeFirstWithPostersAndCartoonsSink() {
		List<SimilarTitle> found = Arrays.asList(
				new SimilarTitle("tt0000002", "Same director", "2012-01-01T00:00:00Z", 5, false),
				new SimilarTitle("tt0000003", "Cartoon", "2010-01-01T00:00:00Z", 12, true),
				new SimilarTitle("tt0000004", "Director, genre and cast", "2015-01-01T00:00:00Z", 11, false),
				new SimilarTitle("tt0000005", "One actor", "2010-01-01T00:00:00Z", 2, false),
				new SimilarTitle("tt0000001", "Itself", "2010-01-01T00:00:00Z", 30, false));
		List<PosterItem> items = MoreLikeThis.fromWikidata(found, "tt0000001", "movie",
				Arrays.asList("Thriller", "Drama"), ReleaseOrder.parseYear("2010"));
		assertEquals(Arrays.asList("tt0000004", "tt0000002", "tt0000003"), ids(items));
		assertEquals("https://images.metahub.space/poster/medium/tt0000004/img", items.get(0).poster);
		assertEquals("movie", items.get(0).type);
	}

	@Test
	public void forACartoonOtherCartoonsWin() {
		List<SimilarTitle> found = Arrays.asList(
				new SimilarTitle("tt0000002", "Live action", null, 8, false),
				new SimilarTitle("tt0000003", "Cartoon", null, 6, true));
		List<PosterItem> items = MoreLikeThis.fromWikidata(found, "tt0000001", "movie",
				Arrays.asList("Animation", "Comedy"), 0);
		assertEquals("tt0000003", items.get(0).id);
	}

	@Test
	public void genreMatchesFillUpWithoutRepeats() {
		List<PosterItem> first = Arrays.asList(new PosterItem("a", "movie", "A", null, -1));
		List<PosterItem> then = Arrays.asList(new PosterItem("a", "movie", "A", null, -1),
				new PosterItem("b", "movie", "B", null, -1));
		assertEquals(Arrays.asList("a", "b"), ids(MoreLikeThis.merge(first, then)));
	}
}
