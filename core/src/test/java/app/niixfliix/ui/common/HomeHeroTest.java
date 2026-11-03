package app.niixfliix.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.niixfliix.addon.model.Meta;

public class HomeHeroTest {

	private static PosterItem poster(String id, String type) {
		return new PosterItem(id, type, "Name " + id, "https://img.example/" + id + ".jpg", -1);
	}

	@Test
	public void theFirstMovieOrShowIsPicked() {
		PosterItem channel = poster("ch", "tv");
		PosterItem broken = poster("", "movie");
		PosterItem movie = poster("tt1", "movie");
		assertSame(movie, HomeHero.pick(Arrays.asList(channel, broken, movie)));
		assertNull(HomeHero.pick(Collections.singletonList(channel)));
	}

	@Test
	public void theFullMetaIsPreferredOverTheCard() {
		PosterItem item = poster("tt1", "movie");
		HomeHero bare = new HomeHero(item, null);
		assertEquals("Name tt1", bare.name());
		assertEquals("https://img.example/tt1.jpg", bare.backdrop());
		assertEquals("", bare.genres());
		assertNull(bare.logo());

		Meta meta = new Meta();
		meta.name = "Tenzing";
		meta.background = "https://img.example/bg.jpg";
		meta.logo = "https://img.example/logo.png";
		meta.genres = Arrays.asList("Adventure", " ", "Drama", "History", "Biography");
		HomeHero full = new HomeHero(item, meta);
		assertEquals("Tenzing", full.name());
		assertEquals("https://img.example/bg.jpg", full.backdrop());
		assertEquals("https://img.example/logo.png", full.logo());
		assertEquals("Adventure • Drama • History", full.genres());
		assertTrue(full.sameTitle(bare));
		assertFalse(full.sameTitle(new HomeHero(poster("tt2", "movie"), null)));
	}

	@Test
	public void upToFiveDifferentTitlesRotate() {
		List<PosterItem> items = new ArrayList<>();
		items.add(poster("ch", "tv"));
		for (int i = 0; i < 8; i++) {
			items.add(poster("tt" + i, "movie"));
		}
		items.add(2, poster("tt0", "movie"));
		List<PosterItem> picked = HomeHero.pickAll(items, HomeHero.ROTATION);
		assertEquals(5, picked.size());
		assertEquals("tt0", picked.get(0).id);
		assertEquals("tt1", picked.get(1).id);
		assertEquals("tt4", picked.get(4).id);
	}
}
