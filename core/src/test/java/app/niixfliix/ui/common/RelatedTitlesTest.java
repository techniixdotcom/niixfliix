package app.niixfliix.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.niixfliix.addon.SimilarTitle;
import app.niixfliix.addon.model.Meta;

public class RelatedTitlesTest {

	@Test
	public void onlyFilmsWithAnImdbIdGetTheRow() {
		assertTrue(RelatedTitles.applies(Meta.TYPE_MOVIE, "tt0107290"));
		assertFalse(RelatedTitles.applies(Meta.TYPE_SERIES, "tt0903747"));
		assertFalse(RelatedTitles.applies(Meta.TYPE_MOVIE, "kitsu:1"));
		assertFalse(RelatedTitles.applies(Meta.TYPE_MOVIE, null));
	}

	@Test
	public void oldestFirstWithoutTheTitleItselfOrTwice() {
		List<SimilarTitle> found = Arrays.asList(
				new SimilarTitle("tt0369610", "Jurassic World", "2015-06-12T00:00:00Z", 3, false),
				new SimilarTitle("tt0119567", "The Lost World", "1997-05-23T00:00:00Z", 3, false),
				new SimilarTitle("tt0107290", "Jurassic Park", "1993-06-11T00:00:00Z", 3, false),
				new SimilarTitle("tt9999999", "Undated", null, 2, false),
				new SimilarTitle("tt0119567", "The Lost World again", null, 2, false));
		List<String> ids = new ArrayList<>();
		for (PosterItem p : RelatedTitles.toPosters(found, "tt0107290")) {
			ids.add(p.id);
			assertEquals(Meta.TYPE_MOVIE, p.type);
		}
		assertEquals(Arrays.asList("tt0119567", "tt0369610", "tt9999999"), ids);
	}
}
