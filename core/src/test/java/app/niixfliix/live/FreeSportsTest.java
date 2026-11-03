package app.niixfliix.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Stream;

public class FreeSportsTest {

	private static final String PLAYLIST = "#EXTM3U\n"
			+ "#EXTINF:-1 tvg-id=\"ACCNetwork.us@SD\" tvg-logo=\"https://i.imgur.com/a.png\" group-title=\"Sports\","
			+ "ACC Network (720p)\n"
			+ "http://23.239.31.26:8989/accnetwork/index.m3u8\n"
			+ "#EXTINF:-1 tvg-id=\"Adjarasport1.ge@SD\" tvg-logo=\"https://i.imgur.com/b.png\" group-title=\"Sports\","
			+ "Adjarasport 1\n"
			+ "https://live20.bozztv.com/dvrfl05/gin-adjara/index.m3u8\n"
			+ "#EXTINF:-1 tvg-id=\"RedBullTV.at@DE\" tvg-logo=\"https://images.pluto.tv/rb.png\" group-title=\"Sports\","
			+ "Red Bull TV DE (1080p)\n"
			+ "https://46cf.mediatailor.us-west-2.amazonaws.com/v1/master/x/playlist.m3u8\n"
			+ "#EXTINF:-1 tvg-id=\"FIFAPlus.uk@German\" tvg-logo=\"https://upload.wikimedia.org/f.png\" "
			+ "group-title=\"Sports\",FIFA+ German (720p)\n"
			+ "https://4397879b.wurl.com/master/abc/playlist.m3u8\n"
			+ "#EXTINF:-1 tvg-id=\"F1Channel.us@SD\" tvg-logo=\"https://i.imgur.com/f1.png\" group-title=\"Sports\","
			+ "F1 Channel (1080p) [Geo-blocked]\n"
			+ "#EXTVLCOPT:http-referrer=https://example.com/\n"
			+ "#EXTVLCOPT:http-user-agent=Mozilla/5.0\n"
			+ "https://f1-samsung.amagi.tv/playlist.m3u8\n"
			+ "#EXTINF:-1 tvg-id=\"F1Channel.us@SD\" tvg-logo=\"https://i.imgur.com/f1.png\" group-title=\"Sports\","
			+ "F1 Channel (720p)\n"
			+ "https://jmp2.uk/sam-f1.m3u8\n"
			+ "#EXTINF:-1 tvg-id=\"Fake.ch@SD\" tvg-logo=\"http://insecure/logo.png\" group-title=\"Sports\",Fake\n"
			+ "https://evil.amagi.tv.example.com/playlist.m3u8\n";

	@Test
	public void onlyChannelsFromLicensedFreeTvDistributorsAreKept() {
		List<FreeSports.Channel> channels = FreeSports.parse(PLAYLIST, "");
		assertEquals(3, channels.size());
		assertEquals("F1 Channel", channels.get(0).name);
		assertEquals("FIFA+ German", channels.get(1).name);
		assertEquals("Red Bull TV DE", channels.get(2).name);
	}

	@Test
	public void feedsOfOneChannelAreTogetherWithTheirHeaders() {
		FreeSports.Channel f1 = FreeSports.find(FreeSports.parse(PLAYLIST, ""), "freesports:F1Channel.us@SD");
		assertEquals(2, f1.feeds.size());
		List<Stream> streams = FreeSports.streams(f1);
		assertEquals("https://f1-samsung.amagi.tv/playlist.m3u8", streams.get(0).url);
		assertEquals("Live\n1080p", streams.get(0).name);
		assertEquals("https://example.com/", streams.get(0).requestHeaders().get("Referer"));
		assertEquals("Mozilla/5.0", streams.get(0).requestHeaders().get("User-Agent"));
		assertTrue(streams.get(1).requestHeaders().isEmpty());
	}

	@Test
	public void theHomeCountryComesFirst() {
		List<FreeSports.Channel> channels = FreeSports.parse(PLAYLIST, "AT");
		assertEquals("Red Bull TV DE", channels.get(0).name);
		assertEquals("at", channels.get(0).country);
	}

	@Test
	public void channelsBecomeLiveTitles() {
		FreeSports.Channel fifa = FreeSports.parse(PLAYLIST, "").get(1);
		Meta m = FreeSports.meta(fifa);
		assertEquals(FreeSports.TYPE, m.type);
		assertEquals("https://upload.wikimedia.org/f.png", m.poster);
		assertTrue(m.isValid());
		assertTrue(m.id.startsWith(FreeSports.ID_PREFIX));
	}

	@Test
	public void hostsAreMatchedExactly() {
		assertTrue(FreeSports.isLicensed("https://jmp2.uk/plu-1.m3u8"));
		assertTrue(FreeSports.isLicensed("https://x.amagi.tv:443/a.m3u8"));
		assertFalse(FreeSports.isLicensed("http://x.amagi.tv/a.m3u8"));
		assertFalse(FreeSports.isLicensed("https://amagi.tv.evil.com/a.m3u8"));
		assertFalse(FreeSports.isLicensed("https://notamagi.tv/a.m3u8"));
		assertNull(FreeSports.find(FreeSports.parse(PLAYLIST, ""), "freesports:nothing"));
	}
}
