package app.niixfliix.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import app.niixfliix.addon.AddonJson;
import app.niixfliix.addon.model.Meta;

public class TitleAboutTest {

	private final Gson gson = AddonJson.create();

	private Meta meta(String json) {
		return gson.fromJson(json, Meta.class);
	}

	@Test
	public void cinemetaFactsAreShownInOrder() {
		Meta m = meta("{\"id\":\"tt1375666\",\"type\":\"movie\",\"name\":\"Inception\","
				+ "\"director\":[\"Christopher Nolan\"],\"writer\":[\"Christopher Nolan\"],"
				+ "\"released\":\"2010-07-16T00:00:00.000Z\",\"country\":\"United States, United Kingdom\","
				+ "\"awards\":\"Won 4 Oscars. 159 wins & 220 nominations total\",\"cast\":[\"Leonardo DiCaprio\"]}");
		List<TitleAbout.Entry> facts = TitleAbout.facts(m, Locale.ENGLISH, ", ");
		assertEquals(5, facts.size());
		assertEquals(TitleAbout.Fact.DIRECTOR, facts.get(0).fact);
		assertEquals("Christopher Nolan", facts.get(0).value);
		assertEquals("July 16, 2010", facts.get(2).value);
		assertEquals("United States, United Kingdom", facts.get(3).value);
		assertEquals(Arrays.asList("Leonardo DiCaprio"), TitleAbout.cast(m));
		assertEquals("https://www.imdb.com/title/tt1375666/", TitleAbout.imdbUrl(m));
	}

	@Test
	public void linksFillInAndOddShapesDontBreakTheMeta() {
		Meta m = meta("{\"id\":\"x:1\",\"type\":\"movie\",\"name\":\"X\",\"imdb_id\":\"tt0111161\","
				+ "\"country\":[\"France\",\"Belgium\"],\"awards\":{\"won\":3},\"director\":\"Solo\","
				+ "\"links\":[{\"name\":\"Ann\",\"category\":\"Directors\",\"url\":\"stremio:///search?search=Ann\"},"
				+ "{\"name\":\"Bob\",\"category\":\"Cast\"},{\"name\":\"Cy\",\"category\":\"Writers\"}]}");
		assertTrue(m.isValid());
		List<TitleAbout.Entry> facts = TitleAbout.facts(m, Locale.ENGLISH, ", ");
		assertEquals("Ann", facts.get(0).value);
		assertEquals("Cy", facts.get(1).value);
		assertEquals("France, Belgium", facts.get(2).value);
		assertEquals(3, facts.size());
		assertEquals(Arrays.asList("Bob"), TitleAbout.cast(m));
		assertEquals("https://www.imdb.com/title/tt0111161/", TitleAbout.imdbUrl(m));
	}

	@Test
	public void missingOrBadValuesAreLeftOut() {
		Meta m = meta("{\"id\":\"kitsu:1\",\"type\":\"series\",\"name\":\"Y\",\"awards\":\"N/A\",\"released\":\"soon\"}");
		assertTrue(TitleAbout.facts(m, Locale.ENGLISH, ", ").isEmpty());
		assertNull(TitleAbout.imdbUrl(m));
		assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg", TitleAbout.trailerImage("dQw4w9WgXcQ"));
		assertNull(TitleAbout.trailerImage("../../evil"));
		assertNull(TitleAbout.trailerImage(null));
	}
}
