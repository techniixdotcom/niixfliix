package app.niixfliix.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.CatalogExtra;
import app.niixfliix.addon.model.InstalledAddon;

public class DiscoverGenresTest {

	private static final InstalledAddon ADDON = new InstalledAddon("https://a.example/manifest.json");

	private static CatalogSource source(String id, boolean genreRequired, String... genres) {
		Catalog c = new Catalog();
		c.type = "movie";
		c.id = id;
		c.name = id;
		CatalogExtra genre = new CatalogExtra();
		genre.name = Catalog.EXTRA_GENRE;
		genre.isRequired = genreRequired;
		genre.options = Arrays.asList(genres);
		CatalogExtra skip = new CatalogExtra();
		skip.name = Catalog.EXTRA_SKIP;
		c.extra = new ArrayList<>(Arrays.asList(genre, skip));
		return new CatalogSource(ADDON, c);
	}

	@Test
	public void genresAreMergedWithoutYearsOrRepeats() {
		CatalogSource top = source("top", false, "Action", "Comedy", "Drama");
		CatalogSource year = source("year", true, "2026", "2025", "1990s", "2020-2024");
		CatalogSource rating = source("imdbRating", false, "action", "Drama", "Sci-Fi");
		DiscoverGenres g = new DiscoverGenres(Arrays.asList(top, year, rating));
		assertEquals(Arrays.asList("Action", "Comedy", "Drama", "1990s", "Sci-Fi"), g.genres());
		assertTrue(g.hasAll());
		assertSame(top, g.sourceFor(null));
		assertSame(top, g.sourceFor("ACTION"));
		assertSame(rating, g.sourceFor("Sci-Fi"));
		assertNull(g.sourceFor("Western"));
		assertNull(g.firstChoice());
	}

	@Test
	public void withoutAnOpenCatalogTheFirstGenreIsPicked() {
		CatalogSource onlyGenres = source("byGenre", true, "Horror", "Thriller");
		DiscoverGenres g = new DiscoverGenres(Collections.singletonList(onlyGenres));
		assertFalse(g.hasAll());
		assertNull(g.sourceFor(null));
		assertEquals("Horror", g.firstChoice());
		assertSame(onlyGenres, g.sourceFor(g.firstChoice()));
	}

	@Test
	public void numbersAreNotGenres() {
		assertFalse(DiscoverGenres.isGenre("2024"));
		assertFalse(DiscoverGenres.isGenre("2010 - 2019"));
		assertFalse(DiscoverGenres.isGenre("  "));
		assertFalse(DiscoverGenres.isGenre(null));
		assertTrue(DiscoverGenres.isGenre("1990s"));
		assertTrue(DiscoverGenres.isGenre("Sci-Fi"));
		List<CatalogSource> none = Collections.emptyList();
		assertTrue(new DiscoverGenres(none).isEmpty());
	}
}
