package app.niixfliix.torrentio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.niixfliix.addon.DefaultAddons;
import app.niixfliix.addon.StremioUrl;
import app.niixfliix.debrid.DebridService;

public class TorrentioConfigTest {

	@Test
	public void defaultsGiveThePlainManifest() {
		assertEquals(DefaultAddons.TORRENTIO, TorrentioConfig.parse(null).toManifestUrl());
	}

	@Test
	public void debridKeyGoesLastWithDebridOptions() {
		TorrentioConfig c = TorrentioConfig.parse(null);
		c.debrid = DebridService.REAL_DEBRID;
		c.debridKey = "ABCDEF123456";
		assertEquals("https://torrentio.strem.fun/debridoptions=nodownloadlinks,nocatalog|realdebrid=ABCDEF123456/manifest.json",
				c.toManifestUrl());
	}

	@Test
	public void allOptionsInTorrentioOrder() {
		TorrentioConfig c = TorrentioConfig.parse(null);
		c.providers.add("yts");
		c.providers.add("eztv");
		c.sort = TorrentioConfig.Sort.QUALITY_SIZE;
		c.languages.add("french");
		c.languages.add("spanish");
		c.qualityFilter.add("cam");
		c.qualityFilter.add("scr");
		c.limitPerQuality = 3;
		c.debrid = DebridService.ALL_DEBRID;
		c.debridKey = "key_abc.123";
		c.hideDebridCatalog = false;
		String url = c.toManifestUrl();
		assertEquals("https://torrentio.strem.fun/providers=yts,eztv|sort=qualitysize|language=french,spanish"
				+ "|qualityfilter=cam,scr|limit=3|debridoptions=nodownloadlinks|alldebrid=key_abc.123/manifest.json", url);
		assertEquals(url, StremioUrl.toManifestUrl(url));
	}

	@Test
	public void roundTripKeepsUnknownOptions() {
		String fromConfigurePage = "https://torrentio.strem.fun/providers=yts,1337x|sort=seeders|qualityfilter=4k"
				+ "|sizefilter=10GB|debridoptions=nodownloadlinks,nototal|premiumize=PMKEY123456/manifest.json";
		TorrentioConfig c = TorrentioConfig.parse(fromConfigurePage);
		assertEquals(TorrentioConfig.Sort.SEEDERS, c.sort);
		assertEquals(DebridService.PREMIUMIZE, c.debrid);
		assertEquals("PMKEY123456", c.debridKey);
		assertTrue(c.hasDebrid());
		assertTrue(c.hideDownloadLinks);
		assertFalse(c.hideDebridCatalog);
		String rebuilt = c.toManifestUrl();
		assertTrue(rebuilt.contains("sizefilter=10GB"));
		assertTrue(rebuilt.contains("debridoptions=nodownloadlinks,nototal"));
		assertEquals(TorrentioConfig.parse(rebuilt).toManifestUrl(), rebuilt);
	}

	@Test
	public void encodedPipesFromOlderLinks() {
		TorrentioConfig c = TorrentioConfig.parse(
				"https://torrentio.strem.fun/sort=size%7Crealdebrid=RDKEY12345/manifest.json");
		assertEquals(TorrentioConfig.Sort.SIZE, c.sort);
		assertEquals(DebridService.REAL_DEBRID, c.debrid);
	}

	@Test
	public void otherHostsAndGarbageGiveDefaults() {
		TorrentioConfig c = TorrentioConfig.parse("https://evil.example/realdebrid=STOLEN/manifest.json");
		assertNull(c.debrid);
		assertEquals(DefaultAddons.TORRENTIO, c.toManifestUrl());
		assertNull(TorrentioConfig.parse(null).debrid);
	}

	@Test
	public void implausibleKeyIsNotWrittenIntoTheUrl() {
		TorrentioConfig c = TorrentioConfig.parse(null);
		c.debrid = DebridService.REAL_DEBRID;
		c.debridKey = "abc|sort=seeders/../";
		assertFalse(c.hasDebrid());
		assertEquals(DefaultAddons.TORRENTIO, c.toManifestUrl());
	}

	@Test
	public void allProvidersMeansNoProviderOption() {
		TorrentioConfig c = TorrentioConfig.parse(null);
		c.providers.addAll(TorrentioConfig.PROVIDERS);
		assertEquals(DefaultAddons.TORRENTIO, c.toManifestUrl());
	}
}
