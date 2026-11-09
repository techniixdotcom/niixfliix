package app.niixfliix.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.addon.model.Video;

public class SeriesDownloadTest {

	private static final long NOW = 1_800_000_000_000L;

	private static Video episode(int season, int episode, String released) {
		Video v = new Video();
		v.id = "tt1:" + season + ":" + episode;
		v.season = season;
		v.episode = episode;
		v.released = released;
		return v;
	}

	private static Meta series(Video... episodes) {
		Meta m = new Meta();
		m.id = "tt1";
		m.type = Meta.TYPE_SERIES;
		m.name = "Show";
		m.videos = new ArrayList<>(Arrays.asList(episodes));
		return m;
	}

	private static StreamInfo stream(StreamInfo.Kind kind, Quality quality, Boolean cached, String url) {
		Stream s = new Stream();
		s.url = url;
		if (kind == StreamInfo.Kind.TORRENT) {
			s.infoHash = "0123456789012345678901234567890123456789";
		}
		StreamInfo info = new StreamInfo(s, "Add-on", "https://addon.example/manifest.json");
		info.kind = kind;
		info.quality = quality;
		info.cached = cached;
		return info;
	}

	@Test
	public void namesAreTheSeriesThenSeasonAndEpisode() {
		assertEquals("Breaking Bad.s1e2.mkv", SeriesDownload.fileName("Breaking Bad", 1, 2, ".mkv"));
		assertEquals("Show.s10e12.mp4", SeriesDownload.fileName(" Show ", 10, 12, ".mp4"));
		assertEquals("Episode.s1e1.mkv", SeriesDownload.fileName("", 1, 1, ".mkv"));
	}

	@Test
	public void aWholeSeriesIsEverySeasonInOrderWithoutSpecials() {
		Meta m = series(episode(2, 1, null), episode(0, 1, null), episode(1, 2, null), episode(1, 1, null));
		List<Video> all = SeriesDownload.episodes(m, null, NOW);
		assertEquals(3, all.size());
		assertEquals("tt1:1:1", all.get(0).id);
		assertEquals("tt1:1:2", all.get(1).id);
		assertEquals("tt1:2:1", all.get(2).id);
	}

	@Test
	public void oneSeasonIsJustThatOneAndSpecialsCanBeAskedFor() {
		Meta m = series(episode(1, 1, null), episode(2, 1, null), episode(2, 2, null), episode(0, 1, null));
		assertEquals(2, SeriesDownload.episodes(m, 2, NOW).size());
		assertEquals(1, SeriesDownload.episodes(m, 0, NOW).size());
		assertEquals(0, SeriesDownload.episodes(m, 5, NOW).size());
	}

	@Test
	public void episodesNotOutYetAreLeftOut() {
		Meta m = series(episode(1, 1, "2020-01-01T00:00:00.000Z"), episode(1, 2, "2099-01-01T00:00:00.000Z"),
				episode(1, 3, null));
		List<Video> out = SeriesDownload.episodes(m, 1, NOW);
		assertEquals(2, out.size());
		assertEquals("tt1:1:1", out.get(0).id);
		assertEquals("tt1:1:3", out.get(1).id);
	}

	@Test
	public void onlyLinksThatCanBeSavedArePicked() {
		StreamInfo torrent = stream(StreamInfo.Kind.TORRENT, Quality.P1080, null, null);
		StreamInfo uncached = stream(StreamInfo.Kind.DIRECT, Quality.P1080, false, "https://x/1");
		StreamInfo cached720 = stream(StreamInfo.Kind.DIRECT, Quality.P720, true, "https://x/2");
		assertSame(cached720, SeriesDownload.pick(Arrays.asList(torrent, uncached, cached720), Quality.P1080));
		assertNull(SeriesDownload.pick(Arrays.asList(torrent, uncached), Quality.P1080));
	}

	@Test
	public void thePreferredQualityWins() {
		StreamInfo p720 = stream(StreamInfo.Kind.DIRECT, Quality.P720, true, "https://x/3");
		StreamInfo p1080 = stream(StreamInfo.Kind.DIRECT, Quality.P1080, true, "https://x/4");
		assertSame(p1080, SeriesDownload.pick(Arrays.asList(p720, p1080), Quality.P1080));
		assertSame(p720, SeriesDownload.pick(Arrays.asList(p720, p1080), Quality.P720));
	}

	@Test
	public void theQuickSeasonIsTheNextEpisodesOrElseTheNewestOut() {
		Video s1e1 = episode(1, 1, "2020-01-01T00:00:00.000Z");
		Meta m = series(s1e1, episode(2, 1, "2021-01-01T00:00:00.000Z"), episode(3, 1, "2099-01-01T00:00:00.000Z"),
				episode(0, 1, null));
		assertEquals(Integer.valueOf(1), SeriesDownload.currentSeason(m, s1e1, NOW));
		assertEquals(Integer.valueOf(2), SeriesDownload.currentSeason(m, null, NOW));
		assertEquals(Integer.valueOf(0), SeriesDownload.currentSeason(series(episode(0, 1, null)), null, NOW));
		assertNull(SeriesDownload.currentSeason(series(episode(1, 1, "2099-01-01T00:00:00.000Z")), null, NOW));
	}
}
