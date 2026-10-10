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
		// blank labels don't count
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
}
