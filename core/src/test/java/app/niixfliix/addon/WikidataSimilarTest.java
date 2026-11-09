package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WikidataSimilarTest {

	@Test
	public void theQueryOnlyTakesImdbIdsAndAsksForTheRightKind() {
		assertNull(Wikidata.similarQuery("tt1\" } DROP", false, Collections.emptyList()));
		assertNull(Wikidata.similarQuery("nm0000001", false, Collections.emptyList()));
		String film = Wikidata.similarQuery("tt1375666", false, Collections.emptyList());
		assertTrue(film.contains("wdt:P345 \"tt1375666\""));
		assertTrue(film.contains("wd:Q11424"));
		assertFalse(film.contains("wd:Q5398426"));
		String series = Wikidata.similarQuery("tt0903747", true, Arrays.asList("de-ch", "de", "x\" }"));
		assertTrue(series.contains("wd:Q5398426"));
		assertTrue(series.contains("IN (\"de-ch\", \"de\")"));
		assertTrue(series.contains("COALESCE(?local, ?english)"));
		assertFalse(film.contains("?local"));
	}

	@Test
	public void answersAreReadAndBadRowsSkipped() throws IOException {
		String json = "{\"results\":{\"bindings\":["
				+ "{\"imdb\":{\"value\":\"tt0482571\"},\"score\":{\"value\":\"11\"},\"name\":{\"value\":\"The Prestige\"},"
				+ "\"released\":{\"value\":\"2006-10-17T00:00:00Z\"},\"styled\":{\"value\":\"0\"}},"
				+ "{\"imdb\":{\"value\":\"tt0482571\"},\"score\":{\"value\":\"9\"},\"name\":{\"value\":\"Again\"}},"
				+ "{\"imdb\":{\"value\":\"Q42\"},\"score\":{\"value\":\"9\"},\"name\":{\"value\":\"Bad\"}},"
				+ "{\"imdb\":{\"value\":\"tt0000002\"},\"score\":{\"value\":\"x\"},\"name\":{\"value\":\"Bad\"}},"
				+ "{\"imdb\":{\"value\":\"tt0000003\"},\"score\":{\"value\":\"4\"}},"
				+ "{\"imdb\":{\"value\":\"tt0000004\"},\"score\":{\"value\":\"6\"},\"name\":{\"value\":\"Toon\"},"
				+ "\"styled\":{\"value\":\"1\"}}]}}";
		List<SimilarTitle> found = Wikidata.parseSimilar(json);
		assertEquals(2, found.size());
		assertEquals("The Prestige", found.get(0).name);
		assertEquals(11, found.get(0).score);
		assertFalse(found.get(0).styled);
		assertTrue(found.get(1).styled);
	}

	@Test(expected = IOException.class)
	public void aBrokenAnswerFails() throws IOException {
		Wikidata.parseSimilar("{\"nope\":1}");
	}

	@Test
	public void relatedQueryIsForFilmsOfTheSameSeriesOrSource() {
		assertNull(Wikidata.relatedQuery("tt1\" } DROP", Collections.emptyList()));
		String query = Wikidata.relatedQuery("tt0107290", Arrays.asList("fr", "x\" }"));
		assertTrue(query.contains("wdt:P345 \"tt0107290\""));
		assertTrue(query.contains("wdt:P179"));
		assertTrue(query.contains("wdt:P144|wdt:P4969|wdt:P155|wdt:P156"));
		assertTrue(query.contains("wd:Q11424"));
		assertTrue(query.contains("IN (\"fr\")"));
		assertFalse(Wikidata.relatedQuery("tt0107290", Collections.emptyList()).contains("?local"));
	}
}
