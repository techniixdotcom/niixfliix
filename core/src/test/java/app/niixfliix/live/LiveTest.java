package app.niixfliix.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.CatalogExtra;

public class LiveTest {

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	@Test
	public void playlistsAndVideoAnswerErrorPagesDont() {
		assertTrue(LiveCheck.looksLikeVideo("/live/index.m3u8", "application/vnd.apple.mpegurl",
				bytes("#EXTM3U\n#EXT-X-VERSION:3\n")));
		assertTrue(LiveCheck.looksLikeVideo("/live", "text/plain", bytes("ï»¿#EXTM3U\n")));
		assertTrue(LiveCheck.looksLikeVideo("/v.ts", "video/mp2t", new byte[] {0x47, 0x40, 0x11, 0x10}));
		assertTrue(LiveCheck.looksLikeVideo("/v.mp4", "video/mp4", bytes("\u0000\u0000\u0000\u0018ftypmp42")));
		assertTrue(LiveCheck.looksLikeVideo("/m.mpd", "application/dash+xml", bytes("<?xml version=\"1.0\"?><MPD>")));
		assertFalse(LiveCheck.looksLikeVideo("/live/index.m3u8", "text/html",
				bytes("<!doctype html><html>Subscribe</html>")));
		assertFalse(LiveCheck.looksLikeVideo("/live/index.m3u8", "application/vnd.apple.mpegurl", bytes("")));
		assertFalse(LiveCheck.looksLikeVideo("/play", "application/json", bytes("{\"error\":\"premium\"}")));
		assertFalse(LiveCheck.looksLikeVideo("/play", null, bytes("<html><body>No</body></html>")));
	}

	@Test
	public void liveCatalogsGoToTheLiveTabOnly() {
		Catalog tv = catalog("tv");
		assertTrue(tv.isLive());
		assertTrue(tv.worksForLive());
		assertFalse(tv.worksForHome());
		assertFalse(tv.worksForDiscover());
		Catalog events = catalog("Events");
		events.extra = Collections.singletonList(genre(true, "Football", "Tennis"));
		assertTrue(events.worksForLive());
		Catalog noOptions = catalog("events");
		noOptions.extra = Collections.singletonList(genre(true));
		assertFalse(noOptions.worksForLive());
		Catalog movies = catalog("movie");
		assertFalse(movies.isLive());
		assertTrue(movies.worksForHome());
		assertFalse(movies.worksForLive());
		assertFalse(catalog("channel").isLive());
	}

	@Test
	public void rowKeysAndNames() {
		Catalog events = catalog("events");
		events.name = "Football";
		events.extra = Collections.singletonList(genre(true, "Super League"));
		app.niixfliix.addon.model.InstalledAddon addon =
				new app.niixfliix.addon.model.InstalledAddon("https://a.example/manifest.json");
		LiveTv.Row row = new LiveTv.Row(new app.niixfliix.ui.common.CatalogSource(addon, events), "Super League");
		assertEquals("Football · Super League", row.name());
		assertEquals(Collections.singletonMap("genre", "Super League"), row.extras());
		assertEquals("https://a.example/manifest.json|events|c|Super League", row.key);
	}

	private static Catalog catalog(String type) {
		Catalog c = new Catalog();
		c.type = type;
		c.id = "c";
		return c;
	}

	private static CatalogExtra genre(boolean required, String... options) {
		CatalogExtra e = new CatalogExtra();
		e.name = "genre";
		e.isRequired = required;
		e.options = options.length > 0 ? Arrays.asList(options) : null;
		return e;
	}
}
