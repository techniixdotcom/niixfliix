package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WikidataTest {

	private static final String ANSWER = "{\"head\":{\"vars\":[\"imdb\",\"label\"]},\"results\":{\"bindings\":["
			+ "{\"imdb\":{\"type\":\"literal\",\"value\":\"tt0068646\"},"
			+ "\"label\":{\"xml:lang\":\"zh\",\"type\":\"literal\",\"value\":\"教父\"}},"
			+ "{\"imdb\":{\"type\":\"literal\",\"value\":\"tt0068646\"},"
			+ "\"label\":{\"xml:lang\":\"zh-tw\",\"type\":\"literal\",\"value\":\"教父（台灣）\"}},"
			+ "{\"imdb\":{\"type\":\"literal\",\"value\":\"tt0133093\"},"
			+ "\"label\":{\"xml:lang\":\"zh-hant\",\"type\":\"literal\",\"value\":\"駭客任務\"}},"
			+ "{\"imdb\":{\"type\":\"literal\",\"value\":\"tt0000001\"},"
			+ "\"label\":{\"xml:lang\":\"zh-tw\",\"type\":\"literal\",\"value\":\"  \"}}"
			+ "]}}";

	@Test
	public void bestLanguageWins() throws IOException {
		Map<String, String> titles = Wikidata.parse(ANSWER, Wikidata.languages("zh-TW"));
		assertEquals("教父（台灣）", titles.get("tt0068646"));
		assertEquals("駭客任務", titles.get("tt0133093"));
		assertNull(titles.get("tt0000001"));
	}

	@Test
	public void languageCodes() {
		assertEquals(Arrays.asList("es-es", "es"), Wikidata.languages("es-ES"));
		assertEquals(Arrays.asList("pt-br", "pt"), Wikidata.languages("pt-BR"));
		assertEquals(Arrays.asList("zh-cn", "zh-hans", "zh"), Wikidata.languages("zh-CN"));
		assertEquals(Arrays.asList("zh-tw", "zh-hant", "zh-hk", "zh"), Wikidata.languages("zh-TW"));
	}

	@Test
	public void queryOnlyTakesRealIds() {
		List<String> ids = Arrays.asList("tt0068646", "tt1\" } ; DROP", "nm0000001");
		String query = Wikidata.query(ids, Arrays.asList("es-es", "es"));
		assertTrue(query.contains("\"tt0068646\""));
		assertFalse(query.contains("DROP"));
		assertFalse(query.contains("nm0000001"));
		assertTrue(query.contains("IN (\"es-es\", \"es\")"));
		assertNull(Wikidata.query(Collections.singletonList("bad"), Arrays.asList("es")));
		assertNull(Wikidata.query(Collections.singletonList("tt1"), Arrays.asList("\")} x")));
	}

	@Test
	public void garbageIsAnError() {
		try {
			Wikidata.parse("<html>busy</html>", Arrays.asList("es"));
			fail();
		} catch (IOException expected) {
		}
		try {
			Wikidata.parse("{\"results\":{}}", Arrays.asList("es"));
			fail();
		} catch (IOException expected) {
		}
	}

	private static final String SEARCH_ANSWER = "{\"head\":{\"vars\":[\"imdb\",\"label\",\"series\"]},"
			+ "\"results\":{\"bindings\":["
			+ "{\"imdb\":{\"type\":\"literal\",\"value\":\"tt0068646\"},"
			+ "\"label\":{\"xml:lang\":\"es\",\"type\":\"literal\",\"value\":\" El padrino \"},"
			+ "\"series\":{\"datatype\":\"http://www.w3.org/2001/XMLSchema#boolean\",\"type\":\"literal\","
			+ "\"value\":\"false\"}},"
			+ "{\"imdb\":{\"type\":\"literal\",\"value\":\"tt0068646\"},"
			+ "\"series\":{\"type\":\"literal\",\"value\":\"false\"}},"
			+ "{\"imdb\":{\"type\":\"literal\",\"value\":\"tt0903747\"},"
			+ "\"series\":{\"type\":\"literal\",\"value\":\"true\"}},"
			+ "{\"imdb\":{\"type\":\"literal\",\"value\":\"nm0000008\"}},"
			+ "{\"label\":{\"type\":\"literal\",\"value\":\"no id\"}}"
			+ "]}}";

	@Test
	public void searchKeepsWikidatasOrderAndEachTitleOnce() throws IOException {
		List<Wikidata.Hit> hits = Wikidata.parseSearch(SEARCH_ANSWER);
		assertEquals(2, hits.size());
		assertEquals("tt0068646", hits.get(0).imdbId);
		assertEquals("El padrino", hits.get(0).title);
		assertFalse(hits.get(0).series);
		assertEquals("tt0903747", hits.get(1).imdbId);
		assertEquals("", hits.get(1).title);
		assertTrue(hits.get(1).series);
	}

	@Test
	public void searchTextCantBreakOutOfTheQuery() {
		String query = Wikidata.searchQuery("El \"padrino\" \\ } DROP\n", "es");
		assertTrue(query.contains("mwapi:search \"El \\\"padrino\\\" \\\\ } DROP \" ;"));
		assertTrue(query.contains("mwapi:language \"es\""));
		assertTrue(query.endsWith("LIMIT " + Wikidata.MAX_HITS));
		assertNull(Wikidata.searchQuery("   \n\t ", "es"));
		assertNull(Wikidata.searchQuery("padrino", "es\" . } x"));
		assertNull(Wikidata.searchQuery("padrino", "ES"));
	}

	@Test
	public void searchTextIsCut() {
		StringBuilder longText = new StringBuilder();
		for (int i = 0; i < 300; i++) {
			longText.append('a');
		}
		String literal = Wikidata.sparqlString(longText.toString());
		assertEquals(102, literal.length());
		assertEquals("\"\uD83C\uDFAC x\"", Wikidata.sparqlString("\uD83C\uDFAC x"));
		assertEquals("\"  x\"", Wikidata.sparqlString("\uD83C x"));
	}

	@Test
	public void searchGarbageIsAnError() {
		try {
			Wikidata.parseSearch("{\"results\":[]}");
			fail();
		} catch (IOException expected) {
		}
	}
}
