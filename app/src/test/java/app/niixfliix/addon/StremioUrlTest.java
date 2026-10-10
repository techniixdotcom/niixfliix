package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class StremioUrlTest {

	@Test
	public void stremioSchemeBecomesHttps() {
		assertEquals("https://v3-cinemeta.strem.io/manifest.json",
				StremioUrl.toManifestUrl("stremio://v3-cinemeta.strem.io/manifest.json"));
		assertEquals("https://torrentio.strem.fun/sort=seeders|realdebrid=KEY123/manifest.json",
				StremioUrl.toManifestUrl("  STREMIO://Torrentio.Strem.Fun/sort=seeders|realdebrid=KEY123/manifest.json "));
	}

	@Test
	public void httpsIsKeptAndQueryIsDropped() {
		assertEquals("https://example.com:8443/abc/manifest.json",
				StremioUrl.toManifestUrl("https://example.com:8443/abc/manifest.json?x=1#frag"));
	}

	@Test
	public void rejectsAnythingElse() {
		assertNull(StremioUrl.toManifestUrl(null));
		assertNull(StremioUrl.toManifestUrl(""));
		assertNull(StremioUrl.toManifestUrl("http://example.com/manifest.json"));
		assertNull(StremioUrl.toManifestUrl("https://example.com/other.json"));
		assertNull(StremioUrl.toManifestUrl("https://example.com"));
		assertNull(StremioUrl.toManifestUrl("intent://example.com/manifest.json"));
		assertNull(StremioUrl.toManifestUrl("file:///sdcard/manifest.json"));
		assertNull(StremioUrl.toManifestUrl("https://exa mple.com/manifest.json"));
		assertNull(StremioUrl.toManifestUrl("https://user@example.com/manifest.json"));
		assertNull(StremioUrl.toManifestUrl("https://example.com:99999/manifest.json"));
		assertNull(StremioUrl.toManifestUrl("https://example.com/a/../manifest.json"));
		assertNull(StremioUrl.toManifestUrl("https://example.com/<script>/manifest.json"));
		StringBuilder huge = new StringBuilder("https://example.com/");
		while (huge.length() < StremioUrl.MAX_LENGTH) {
			huge.append("aaaaaaaaaa");
		}
		assertNull(StremioUrl.toManifestUrl(huge + "/manifest.json"));
	}

	@Test
	public void baseHostAndConfiguration() {
		String url = "https://torrentio.strem.fun/providers=yts|realdebrid=SECRET/manifest.json";
		assertEquals("https://torrentio.strem.fun/providers=yts|realdebrid=SECRET", StremioUrl.baseOf(url));
		assertEquals("torrentio.strem.fun", StremioUrl.hostOf(url));
		assertTrue(StremioUrl.hasConfiguration(url));
		assertEquals("providers=yts|realdebrid=SECRET", StremioUrl.configurationOf(url));
		assertFalse(StremioUrl.hasConfiguration("https://torrentio.strem.fun/manifest.json"));
	}

	@Test
	public void maskedNeverShowsTheConfiguration() {
		String masked = StremioUrl.masked("https://torrentio.strem.fun/realdebrid=SECRET/manifest.json");
		assertFalse(masked.contains("SECRET"));
		assertEquals("torrentio.strem.fun/•••/manifest.json", masked);
		assertEquals("v3-cinemeta.strem.io/manifest.json", StremioUrl.masked(DefaultAddons.CINEMETA));
	}

	@Test
	public void resourceUrlsFollowTheProtocol() {
		assertEquals("https://v3-cinemeta.strem.io/meta/series/tt0944947.json",
				AddonUrls.resource(DefaultAddons.CINEMETA, "meta", "series", "tt0944947", Collections.emptyMap()));
		assertEquals("https://torrentio.strem.fun/stream/series/tt0944947:1:2.json",
				AddonUrls.resource(DefaultAddons.TORRENTIO, "stream", "series", "tt0944947:1:2", Collections.emptyMap()));
		Map<String, String> extras = new LinkedHashMap<>();
		extras.put("genre", "Science Fiction");
		extras.put("skip", "100");
		assertEquals("https://v3-cinemeta.strem.io/catalog/movie/top/genre=Science%20Fiction&skip=100.json",
				AddonUrls.resource(DefaultAddons.CINEMETA, "catalog", "movie", "top", extras));
		assertEquals("https://v3-cinemeta.strem.io/catalog/movie/top/search=b%C3%A9b%C3%A9%20%26%20co.json",
				AddonUrls.resource(DefaultAddons.CINEMETA, "catalog", "movie", "top",
						Collections.singletonMap("search", "bébé & co")));
	}

	@Test
	public void defaultsAreTheFourKnownAddons() {
		assertEquals(Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, DefaultAddons.THEPIRATEBAY,
				DefaultAddons.OPENSUBTITLES), DefaultAddons.urls());
		for (String url : DefaultAddons.urls()) {
			assertEquals(url, StremioUrl.toManifestUrl(url));
		}
	}
}
