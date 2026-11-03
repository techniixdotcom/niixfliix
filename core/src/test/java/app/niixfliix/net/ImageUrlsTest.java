package app.niixfliix.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ImageUrlsTest {

	private static final int[] POSTERS = {92, 154, 185, 342, 500, 780};
	private static final int[] BACKDROPS = {300, 780, 1280};

	@Test
	public void tmdbPostersComeAtTheSizeTheyreShown() {
		assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg",
				ImageUrls.sized("https://image.tmdb.org/t/p/original/abc.jpg", 300, POSTERS));
		assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg",
				ImageUrls.sized("https://image.tmdb.org/t/p/w500/abc.jpg", 342, POSTERS));
		assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg",
				ImageUrls.sized("https://image.tmdb.org/t/p/original/b.jpg", 1440, BACKDROPS));
	}

	@Test
	public void neverBiggerThanWhatTheAddonGave() {
		assertEquals("https://image.tmdb.org/t/p/w185/abc.jpg",
				ImageUrls.sized("https://image.tmdb.org/t/p/w185/abc.jpg", 300, POSTERS));
	}

	@Test
	public void otherImagesAreLeftAlone() {
		String metahub = "https://images.metahub.space/poster/medium/tt0068646/img";
		assertEquals(metahub, ImageUrls.sized(metahub, 300, POSTERS));
		String height = "https://image.tmdb.org/t/p/h632/abc.jpg";
		assertEquals(height, ImageUrls.sized(height, 300, POSTERS));
		String query = "https://image.tmdb.org/t/p/original/abc.jpg?x=1";
		assertEquals(query, ImageUrls.sized(query, 300, POSTERS));
		assertEquals("https://image.tmdb.org/t/p/original/abc.jpg",
				ImageUrls.sized("https://image.tmdb.org/t/p/original/abc.jpg", 0, POSTERS));
	}

	@Test
	public void cinemetaLargePostersAreMediumOnCards() {
		String large = "https://images.metahub.space/poster/large/tt0068646/img";
		assertEquals("https://images.metahub.space/poster/medium/tt0068646/img", ImageUrls.poster(large, 300));
		assertEquals(large, ImageUrls.poster(large, 900));
		assertEquals(large, ImageUrls.poster(large, 0));
		String small = "https://images.metahub.space/poster/small/tt0068646/img";
		assertEquals(small, ImageUrls.poster(small, 300));
		String background = "https://images.metahub.space/background/large/tt0068646/img";
		assertEquals(background, ImageUrls.poster(background, 300));
		assertEquals(background, ImageUrls.backdrop(background, 300));
	}

	@Test
	public void picturesOnlyEverComeOverHttps() {
		assertEquals("https://a.example/p.jpg", ImageUrls.secure("https://a.example/p.jpg"));
		assertEquals("https://a.example/p.jpg", ImageUrls.secure("http://a.example/p.jpg"));
		assertEquals("https://a.example/p.jpg", ImageUrls.secure("HTTP://a.example/p.jpg"));
		assertEquals("https://a.example/p.jpg", ImageUrls.secure(" https://a.example/p.jpg "));
		assertNull(ImageUrls.secure("http://"));
		assertNull(ImageUrls.secure("file:///etc/passwd"));
		assertNull(ImageUrls.secure("data:image/png;base64,AAAA"));
		assertNull(ImageUrls.secure(null));
	}
}
