package app.niixfliix.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import app.niixfliix.ui.common.PosterItem;

public class SportSitesTest {

	@Test
	public void everySiteIsATileThatFindsItsWayBack() {
		List<PosterItem> posters = SportSites.posters();
		assertEquals(SportSites.ALL.size(), posters.size());
		for (int i = 0; i < posters.size(); i++) {
			PosterItem item = posters.get(i);
			assertTrue(SportSites.isSite(item.id));
			assertSame(SportSites.ALL.get(i), SportSites.find(item.id));
			assertTrue(SportSites.isTrusted(SportSites.ALL.get(i).url));
		}
		assertNull(SportSites.find("sportsite:nothing"));
		assertFalse(SportSites.isSite("blue-sport"));
		assertFalse(SportSites.isSite(null));
	}

	@Test
	public void onlyTheProvidersOwnHttpsPagesAreTrusted() {
		assertTrue(SportSites.isTrusted("https://tv.blue.ch/home"));
		assertTrue(SportSites.isTrusted("https://login.swisscom.ch/auth"));
		assertTrue(SportSites.isTrusted("https://www.srf.ch/play/tv/sport-livestreams"));
		assertTrue(SportSites.isTrusted("https://il.srgssr.ch/integrationlayer"));
		assertTrue(SportSites.isTrusted("https://TV.BLUE.CH./home"));
		assertFalse(SportSites.isTrusted("http://tv.blue.ch/home"));
		assertFalse(SportSites.isTrusted("https://blue.ch.evil.com/"));
		assertFalse(SportSites.isTrusted("https://notblue.ch/"));
		assertFalse(SportSites.isTrusted("https://tv.blue.ch@evil.com/"));
		assertFalse(SportSites.isTrusted("https://evil.com/?u=https://tv.blue.ch"));
		assertFalse(SportSites.isTrusted("intent://tv.blue.ch#Intent;end"));
		assertFalse(SportSites.isTrusted("javascript:alert(1)"));
		assertFalse(SportSites.isTrusted("https://bad host/"));
		assertFalse(SportSites.isTrusted(null));
	}
}
