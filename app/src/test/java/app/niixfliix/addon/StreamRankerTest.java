package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import app.niixfliix.addon.model.Stream;
import app.niixfliix.addon.model.StreamHints;

public class StreamRankerTest {

	private static int counter;

	private static StreamInfo direct(Quality q, Boolean cached, String binge) {
		Stream s = new Stream();
		s.url = "https://x/" + (++counter);
		if (binge != null) {
			s.behaviorHints = new StreamHints();
			s.behaviorHints.bingeGroup = binge;
		}
		StreamInfo info = new StreamInfo(s, "Torrentio", "https://torrentio.strem.fun/manifest.json");
		info.kind = StreamInfo.Kind.DIRECT;
		info.quality = q;
		info.cached = cached;
		return info;
	}

	private static StreamInfo seeded(Quality q, Boolean cached, int seeders) {
		StreamInfo info = direct(q, cached, null);
		info.seeders = seeders;
		return info;
	}

	private static StreamInfo torrent(Quality q) {
		Stream s = new Stream();
		s.infoHash = String.format("%040d", ++counter);
		StreamInfo info = new StreamInfo(s, "Torrentio", "https://torrentio.strem.fun/manifest.json");
		info.kind = StreamInfo.Kind.TORRENT;
		info.quality = q;
		return info;
	}

	@Test
	public void bestQualityFirstThenCachedFirst() {
		StreamInfo uncached1080 = direct(Quality.P1080, false, null);
		StreamInfo cached720 = direct(Quality.P720, true, null);
		StreamInfo cached1080 = direct(Quality.P1080, true, null);
		StreamInfo other = direct(Quality.OTHER, null, null);
		StreamInfo uhd = torrent(Quality.P2160);
		List<StreamInfo> sorted = StreamRanker.sort(Arrays.asList(uncached1080, cached720, other, cached1080, uhd));
		assertEquals(Arrays.asList(uhd, cached1080, uncached1080, cached720, other), sorted);
	}

	@Test
	public void onePerQualityWithTheMostSeeders() {
		StreamInfo few1080 = seeded(Quality.P1080, true, 12);
		StreamInfo many1080 = seeded(Quality.P1080, true, 900);
		StreamInfo only720 = seeded(Quality.P720, true, 40);
		StreamInfo twoK = seeded(Quality.P1440, true, 5);
		StreamInfo uhd = seeded(Quality.P2160, true, 5000);
		StreamInfo other = seeded(Quality.OTHER, true, 5000);
		List<StreamInfo> best = StreamRanker.bestPerQuality(
				Arrays.asList(few1080, uhd, many1080, other, only720, twoK));
		assertEquals(Arrays.asList(twoK, many1080, only720), best);
	}

	@Test
	public void mostSeedersAcrossAllAddonsNotOnePerAddon() {
		StreamInfo torrentio = seeded(Quality.P1080, null, 120);
		StreamInfo tpb = new StreamInfo(torrentio.stream, "ThePirateBay+", DefaultAddons.THEPIRATEBAY);
		tpb.kind = StreamInfo.Kind.DIRECT;
		tpb.quality = Quality.P1080;
		tpb.seeders = 800;
		assertEquals(Arrays.asList(tpb), StreamRanker.bestPerQuality(Arrays.asList(torrentio, tpb)));
	}

	@Test
	public void zeroSeedersAreHiddenAndNeverPicked() {
		StreamInfo dead1080 = seeded(Quality.P1080, true, 0);
		StreamInfo dead720 = torrent(Quality.P720);
		dead720.seeders = 0;
		StreamInfo alive720 = seeded(Quality.P720, true, 4);
		StreamInfo unknown480 = seeded(Quality.P480, true, -1);
		List<StreamInfo> all = Arrays.asList(dead1080, dead720, alive720, unknown480);
		assertEquals(Arrays.asList(alive720, unknown480), StreamRanker.bestPerQuality(all));
		assertSame(alive720, StreamRanker.pick(StreamRanker.sort(all), Quality.P1080, null));
		assertNull(StreamRanker.nextSameQuality(StreamRanker.sort(all), alive720));
	}

	@Test
	public void mostSeedersWinsEvenOverCached() {
		StreamInfo cached = seeded(Quality.P1080, true, 3);
		StreamInfo tor = torrent(Quality.P1080);
		tor.seeders = 50;
		assertEquals(Arrays.asList(tor), StreamRanker.bestPerQuality(Arrays.asList(cached, tor)));
	}

	@Test
	public void uncachedDebridOnlyWhenNothingElseHasThatQuality() {
		StreamInfo cached = seeded(Quality.P1080, true, 3);
		StreamInfo uncached1080 = seeded(Quality.P1080, false, 3000);
		StreamInfo uncached720 = seeded(Quality.P720, false, 10);
		assertEquals(Arrays.asList(cached, uncached720),
				StreamRanker.bestPerQuality(Arrays.asList(uncached1080, cached, uncached720)));
	}

	@Test
	public void sameBingeGroupWinsForTheNextEpisode() {
		StreamInfo best = direct(Quality.P1440, true, "torrentio|1440p|HDR");
		StreamInfo same = direct(Quality.P720, true, "torrentio|720p|GRP");
		List<StreamInfo> sorted = StreamRanker.sort(Arrays.asList(best, same));
		assertSame(same, StreamRanker.pick(sorted, Quality.P1080, "torrentio|720p|GRP"));
	}

	@Test
	public void preferredQualityThenLowerThenHigher() {
		StreamInfo twoK = direct(Quality.P1440, true, null);
		StreamInfo hd = direct(Quality.P720, true, null);
		StreamInfo sd = direct(Quality.P480, true, null);
		List<StreamInfo> sorted = StreamRanker.sort(Arrays.asList(twoK, hd, sd));
		assertSame(hd, StreamRanker.pick(sorted, Quality.P1080, null));
		assertSame(sd, StreamRanker.pick(sorted, Quality.P480, null));
		assertSame(twoK, StreamRanker.pick(sorted, Quality.P2160, null));
		assertSame(twoK, StreamRanker.pick(StreamRanker.sort(Arrays.asList(twoK)), Quality.P720, null));
	}

	@Test
	public void neverPicks4kOrUnknown() {
		List<StreamInfo> sorted = StreamRanker.sort(Arrays.asList(
				direct(Quality.P2160, true, "torrentio|2160p|HDR"), direct(Quality.OTHER, true, null)));
		assertNull(StreamRanker.pick(sorted, Quality.P1080, "torrentio|2160p|HDR"));
	}

	@Test
	public void skipsWhatCannotPlayRightAway() {
		StreamInfo uncached = direct(Quality.P1080, false, null);
		assertNull(StreamRanker.pick(Arrays.asList(uncached), Quality.P1080, null));
		StreamInfo tor = torrent(Quality.P1080);
		assertSame(tor, StreamRanker.pick(StreamRanker.sort(Arrays.asList(uncached, tor)), Quality.P1080, null));
	}

	@Test
	public void nextSameQualityAfterAFailure() {
		StreamInfo a = direct(Quality.P1080, true, null);
		StreamInfo b = direct(Quality.P1080, false, null);
		StreamInfo c = direct(Quality.P1080, true, null);
		StreamInfo d = direct(Quality.P720, true, null);
		List<StreamInfo> sorted = StreamRanker.sort(Arrays.asList(a, b, c, d));
		assertSame(c, StreamRanker.nextSameQuality(sorted, a));
		assertNull(StreamRanker.nextSameQuality(sorted, c));
	}

	@Test
	public void retryGoesDownBySeeders() {
		StreamInfo top = seeded(Quality.P1080, true, 900);
		StreamInfo low = seeded(Quality.P1080, true, 5);
		StreamInfo mid = seeded(Quality.P1080, true, 60);
		List<StreamInfo> sorted = StreamRanker.sort(Arrays.asList(low, top, mid));
		assertSame(mid, StreamRanker.nextSameQuality(sorted, top));
		assertSame(low, StreamRanker.nextSameQuality(sorted, mid));
	}
}
