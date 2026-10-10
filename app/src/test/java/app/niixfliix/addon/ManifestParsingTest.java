package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;

import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.addon.model.Responses;

public class ManifestParsingTest {

	private final Gson gson = AddonJson.create();

	private static final String CINEMETA_LIKE = "{"
			+ "\"id\":\"com.linvo.cinemeta\",\"version\":\"3.0.13\",\"name\":\"Cinemeta\","
			+ "\"description\":\"The official add-on for movie and series catalogs\","
			+ "\"resources\":[\"catalog\",\"meta\",\"addon_catalog\"],"
			+ "\"types\":[\"movie\",\"series\"],\"idPrefixes\":[\"tt\"],"
			+ "\"catalogs\":["
			+ "{\"type\":\"movie\",\"id\":\"top\",\"name\":\"Popular\",\"extra\":["
			+ "{\"name\":\"genre\",\"options\":[\"Action\",\"Comedy\"]},{\"name\":\"search\"},{\"name\":\"skip\"}]},"
			+ "{\"type\":\"series\",\"id\":\"top\",\"name\":\"Popular\",\"extraSupported\":[\"search\",\"genre\",\"skip\"]},"
			+ "{\"type\":\"movie\",\"id\":\"year\",\"name\":\"New\",\"extra\":[{\"name\":\"genre\",\"isRequired\":true,"
			+ "\"options\":[\"2026\",\"2025\"]}]}"
			+ "],\"behaviorHints\":{\"newEpisodeNotifications\":true}}";

	private static final String TORRENTIO_LIKE = "{"
			+ "\"id\":\"com.stremio.torrentio.addon\",\"version\":\"0.0.15\",\"name\":\"Torrentio\","
			+ "\"resources\":[{\"name\":\"stream\",\"types\":[\"movie\",\"series\",\"anime\"],"
			+ "\"idPrefixes\":[\"tt\",\"kitsu\"]}],"
			+ "\"types\":[\"movie\",\"series\",\"anime\",\"other\"],\"catalogs\":[],"
			+ "\"behaviorHints\":{\"configurable\":true,\"configurationRequired\":false}}";

	@Test
	public void readsCatalogsAndResources() throws IOException {
		Manifest m = AddonJson.parse(gson, CINEMETA_LIKE, Manifest.class);
		ManifestValidator.validate(m);
		assertEquals("Cinemeta", m.displayName());
		assertEquals(3, m.validCatalogs().size());
		assertTrue(m.supports(Manifest.RESOURCE_META, "movie", "tt0111161"));
		assertFalse(m.supports(Manifest.RESOURCE_STREAM, "movie", "tt0111161"));
		assertFalse(m.supports(Manifest.RESOURCE_META, "movie", "kitsu:1"));
		assertFalse(m.supports(Manifest.RESOURCE_META, "channel", "tt0111161"));
	}

	@Test
	public void understandsBothWaysOfDescribingExtras() throws IOException {
		Manifest m = AddonJson.parse(gson, CINEMETA_LIKE, Manifest.class);
		Catalog movies = m.validCatalogs().get(0);
		Catalog series = m.validCatalogs().get(1);
		Catalog byYear = m.validCatalogs().get(2);
		assertTrue(movies.worksForSearch());
		assertTrue(series.worksForSearch());
		assertTrue(series.supportsExtra(Catalog.EXTRA_SKIP));
		assertEquals(2, movies.genres().size());
		assertTrue(movies.worksWithoutExtras());
		assertFalse(byYear.worksWithoutExtras());
		assertTrue(byYear.worksForDiscover());
		assertFalse(byYear.worksForSearch());
	}

	@Test
	public void resourceObjectNarrowsTypesAndPrefixes() throws IOException {
		Manifest m = AddonJson.parse(gson, TORRENTIO_LIKE, Manifest.class);
		ManifestValidator.validate(m);
		assertTrue(m.supports(Manifest.RESOURCE_STREAM, "series", "tt0944947:1:2"));
		assertTrue(m.supports(Manifest.RESOURCE_STREAM, "anime", "kitsu:42"));
		assertFalse(m.supports(Manifest.RESOURCE_STREAM, "other", "tt1"));
		assertFalse(m.supports(Manifest.RESOURCE_STREAM, "movie", "yt:abc"));
		assertTrue(m.isConfigurable());
		assertFalse(m.needsConfiguration());
	}

	@Test
	public void brokenEntriesAreDroppedNotFatal() throws IOException {
		String json = "{\"id\":\"x\",\"name\":\"X\",\"resources\":[\"stream\",42,{\"name\":\"meta\"}],"
				+ "\"types\":[\"movie\"],\"catalogs\":[{\"type\":\"movie\",\"id\":\"a\"},\"nonsense\",{\"id\":\"no-type\"}],"
				+ "\"behaviorHints\":\"not an object\",\"unknownField\":{\"deep\":[1,2,3]}}";
		Manifest m = AddonJson.parse(gson, json, Manifest.class);
		ManifestValidator.validate(m);
		assertEquals(1, m.validCatalogs().size());
		assertTrue(m.supports(Manifest.RESOURCE_META, "movie", "tt1"));
		assertNull(m.behaviorHints);
	}

	@Test
	public void missingNameOrResourcesIsRejected() throws IOException {
		assertInvalid("{\"id\":\"x\",\"resources\":[\"stream\"],\"types\":[\"movie\"]}");
		assertInvalid("{\"id\":\"x\",\"name\":\"X\",\"resources\":[],\"types\":[\"movie\"]}");
		assertInvalid("{\"id\":\"x\",\"name\":\"X\",\"resources\":[\"stream\"]}");
	}

	@Test
	public void insecureLogoIsDropped() throws IOException {
		Manifest m = AddonJson.parse(gson, "{\"id\":\"x\",\"name\":\"X\",\"logo\":\"http://evil/logo.png\","
				+ "\"resources\":[\"stream\"],\"types\":[\"movie\"]}", Manifest.class);
		ManifestValidator.validate(m);
		assertNull(m.logo);
	}

	@Test
	public void notJsonOrNotAnObjectFails() {
		assertBadJson("<html>Bad gateway</html>");
		assertBadJson("[1,2,3]");
		assertBadJson("");
	}

	@Test
	public void streamResponseKeepsGoodStreams() throws IOException {
		String json = "{\"streams\":[{\"name\":\"A\",\"url\":\"https://x/v.mp4\","
				+ "\"behaviorHints\":{\"bingeGroup\":\"g1\",\"proxyHeaders\":{\"request\":{\"Referer\":\"https://x\"}}}},"
				+ "\"garbage\",{\"name\":\"B\",\"infoHash\":\"0123456789abcdef0123456789abcdef01234567\",\"fileIdx\":\"oops\"}]}";
		Responses.Streams r = AddonJson.parse(gson, json, Responses.Streams.class);
		assertNotNull(r.streams);
		assertEquals(1, r.streams.size());
		assertEquals("g1", r.streams.get(0).bingeGroup());
		assertEquals("https://x", r.streams.get(0).requestHeaders().get("Referer"));
	}

	private void assertInvalid(String json) throws IOException {
		Manifest m = AddonJson.parse(gson, json, Manifest.class);
		try {
			ManifestValidator.validate(m);
			fail("Expected the manifest to be rejected: " + json);
		} catch (ManifestValidator.InvalidManifestException expected) {
			// expected
		}
	}

	private void assertBadJson(String text) {
		try {
			AddonJson.parse(gson, text, Manifest.class);
			fail("Expected a parse failure for: " + text);
		} catch (AddonJson.BadJsonException expected) {
			// expected
		} catch (IOException e) {
			fail("Wrong exception: " + e);
		}
	}
}
