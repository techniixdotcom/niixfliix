package app.niixfliix.torrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.niixfliix.app.Constant;

public class MagnetTest {

	private static final String HASH = "c9e15763f722f23e98a29decdfae341b98d53056";

	@Test
	public void readsHashNameAndTrackers() {
		Magnet m = Magnet.parse("magnet:?xt=urn:btih:" + HASH.toUpperCase(Locale.ROOT) + "&dn=Big+Buck+Bunny"
				+ "&tr=udp%3A%2F%2Ftracker.example%3A1337%2Fannounce&tr=javascript%3Aalert(1)");
		assertNotNull(m);
		assertEquals(HASH, m.infoHash);
		assertEquals("Big Buck Bunny", m.name);
		assertEquals(Arrays.asList("udp://tracker.example:1337/announce"), m.trackers);
	}

	@Test
	public void base32HashIsConverted() {
		Magnet m = Magnet.parse("magnet:?xt=urn:btih:ZHQVOY7XELZD5GFCTXWN7LRUDOMNKMCW");
		assertNotNull(m);
		assertEquals(HASH, m.infoHash);
	}

	@Test
	public void rejectsJunk() {
		assertNull(Magnet.parse(null));
		assertNull(Magnet.parse("https://example.com"));
		assertNull(Magnet.parse("magnet:?dn=no-hash"));
		assertNull(Magnet.parse("magnet:?xt=urn:btih:1234"));
		StringBuilder huge = new StringBuilder("magnet:?xt=urn:btih:" + HASH + "&dn=");
		while (huge.length() <= Constant.MAX_INTENT_TEXT) {
			huge.append("x");
		}
		assertNull(Magnet.parse(huge.toString()));
	}

	@Test
	public void trackersFromAddonSources() {
		List<String> trackers = Magnet.trackersFromSources(Arrays.asList(
				"tracker:udp://open.example:80/announce", "dht:" + HASH, "tracker:file:///etc/passwd"));
		assertEquals(Arrays.asList("udp://open.example:80/announce"), trackers);
	}

	@Test
	public void roundTrip() {
		Magnet m = new Magnet(HASH, "A & B", Arrays.asList("udp://t.example:1/a"));
		Magnet again = Magnet.parse(m.toUri());
		assertNotNull(again);
		assertEquals("A & B", again.name);
		assertEquals(m.trackers, again.trackers);
		assertTrue(m.toUri().startsWith("magnet:?xt=urn:btih:" + HASH));
	}

	@Test
	public void publicTrackersAreAddedOnceAfterTheTorrentsOwn() {
		Magnet own = new Magnet(HASH, "x", Arrays.asList("udp://own.example:80/announce",
				"udp://tracker.opentrackr.org:1337/announce"));
		Magnet more = own.withPublicTrackers();
		assertEquals("udp://own.example:80/announce", more.trackers.get(0));
		assertEquals(1, Collections.frequency(more.trackers, "udp://tracker.opentrackr.org:1337/announce"));
		assertTrue(more.trackers.size() > own.trackers.size());
		assertEquals(own.infoHash, more.infoHash);
	}
}
