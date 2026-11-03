package app.niixfliix.torrentio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.addon.model.StreamHints;

public class TorrentioStreamParserTest {

	private static Stream stream(String name, String title) {
		Stream s = new Stream();
		s.name = name;
		s.title = title;
		return s;
	}

	@Test
	public void plainTorrentResult() {
		Stream s = stream("Torrentio\n4k DV | HDR",
				"The.Matrix.1999.2160p.UHD.BluRay.x265-GRP\n👤 125 💾 15.2 GB ⚙️ ThePirateBay\nMulti Audio / 🇬🇧 / 🇮🇹");
		s.infoHash = "0123456789abcdef0123456789abcdef01234567";
		s.fileIdx = 0;
		StreamInfo info = TorrentioStreamParser.parse(s, "Torrentio", "https://torrentio.strem.fun/manifest.json");
		assertEquals(StreamInfo.Kind.TORRENT, info.kind);
		assertEquals(Quality.P2160, info.quality);
		assertEquals("Torrentio", info.source);
		assertEquals("The.Matrix.1999.2160p.UHD.BluRay.x265-GRP", info.releaseName);
		assertEquals(125, info.seeders);
		assertEquals((long) (15.2 * 1024 * 1024 * 1024), info.sizeBytes);
		assertEquals(3, info.languages.size());
		assertEquals("Multi Audio", info.languages.get(0));
		assertTrue(info.tags.contains("DV"));
		assertTrue(info.tags.contains("HDR"));
		assertTrue(info.tags.contains("HEVC"));
		assertNull(info.cached);
	}

	@Test
	public void flagsOnlyLanguageLineIsNotAFileName() {
		Stream s = stream("Torrentio\n1080p", "Film.2021.1080p.BluRay\n👤 12 💾 2 GB ⚙️ YTS\n🇬🇧 / 🇮🇹");
		s.infoHash = "0123456789abcdef0123456789abcdef01234567";
		StreamInfo info = TorrentioStreamParser.parse(s, "Torrentio", "u");
		assertNull(info.fileName);
		assertEquals(2, info.languages.size());
		assertEquals("🇬🇧", info.languages.get(0));
	}

	@Test
	public void releaseGroupNamedTsIsNotACamRip() {
		Stream s = stream("Torrentio\n2160p", "Movie.2024.2160p.WEB-DL.DDP5.1.Atmos-TS\n👤 40 💾 9 GB ⚙️ 1337x");
		s.infoHash = "0123456789abcdef0123456789abcdef01234567";
		assertFalse(TorrentioStreamParser.parse(s, "Torrentio", "u").tags.contains("CAM"));
	}

	@Test
	public void cachedRealDebridResult() {
		Stream s = stream("[RD+] Torrentio\n1080p",
				"Show.S01.1080p.WEB-DL.DDP5.1.H.264-GRP\nShow.S01E02.1080p.WEB-DL.mkv\n👤 50 💾 1.2 GB ⚙️ EZTV");
		s.url = "https://torrentio.strem.fun/resolve/realdebrid/KEY/abc/null/1/Show.S01E02.mkv";
		StreamInfo info = TorrentioStreamParser.parse(s, "Torrentio", "u");
		assertEquals(StreamInfo.Kind.DIRECT, info.kind);
		assertEquals(Boolean.TRUE, info.cached);
		assertFalse(info.isUncachedDebrid());
		assertEquals(Quality.P1080, info.quality);
		assertEquals("Show.S01.1080p.WEB-DL.DDP5.1.H.264-GRP", info.releaseName);
		assertEquals("Show.S01E02.1080p.WEB-DL.mkv", info.fileName);
	}

	@Test
	public void uncachedDebridResultIsMarked() {
		Stream s = stream("[AD download] Torrentio\n720p", "Movie.2020.720p.WEBRip\n👤 3 💾 900 MB ⚙️ YTS");
		s.url = "https://torrentio.strem.fun/resolve/alldebrid/KEY/x";
		StreamInfo info = TorrentioStreamParser.parse(s, "Torrentio", "u");
		assertEquals(Boolean.FALSE, info.cached);
		assertTrue(info.isUncachedDebrid());
		assertEquals(900L * 1024 * 1024, info.sizeBytes);
	}

	@Test
	public void descriptionWinsOverTitle() {
		Stream s = stream("Torrentio\n480p", "old text");
		s.description = "New.Format.480p.DVDRip\n👤 7 💾 700 MB ⚙️ 1337x";
		s.infoHash = "0123456789abcdef0123456789abcdef01234567";
		StreamInfo info = TorrentioStreamParser.parse(s, "Torrentio", "u");
		assertEquals("New.Format.480p.DVDRip", info.releaseName);
		assertEquals(Quality.P480, info.quality);
	}

	@Test
	public void otherAddonsWithoutTheLayout() {
		Stream s = stream("Some Addon", "Just a title");
		s.url = "https://cdn.example/video.mp4";
		StreamHints hints = new StreamHints();
		hints.filename = "Film.2019.1080p.mkv";
		hints.videoSize = 1234L;
		s.behaviorHints = hints;
		StreamInfo info = TorrentioStreamParser.parse(s, "Some Addon", "u");
		assertEquals(StreamInfo.Kind.DIRECT, info.kind);
		assertEquals(Quality.P1080, info.quality);
		assertEquals(1234L, info.sizeBytes);
		assertEquals(-1, info.seeders);
		assertEquals("Just a title", info.releaseName);
	}

	@Test
	public void plainHttpAndJunkAreNotPlayable() {
		Stream http = stream("X", "Y");
		http.url = "http://insecure.example/v.mp4";
		assertEquals(StreamInfo.Kind.UNSUPPORTED, TorrentioStreamParser.parse(http, "X", "u").kind);
		Stream badHash = stream("X", "Y");
		badHash.infoHash = "not-a-hash";
		assertEquals(StreamInfo.Kind.UNSUPPORTED, TorrentioStreamParser.parse(badHash, "X", "u").kind);
		Stream external = stream("X", "Y");
		external.externalUrl = "https://example.com/watch";
		assertEquals(StreamInfo.Kind.EXTERNAL, TorrentioStreamParser.parse(external, "X", "u").kind);
	}

	@Test
	public void camReleasesAreTagged() {
		Stream s = stream("Torrentio\nCAM", "Movie.2026.HDCAM.x264\n👤 900 💾 1.4 GB ⚙️ 1337x");
		s.infoHash = "0123456789abcdef0123456789abcdef01234567";
		StreamInfo info = TorrentioStreamParser.parse(s, "Torrentio", "u");
		assertTrue(info.tags.contains("CAM"));
		assertEquals(Quality.OTHER, info.quality);
	}

	@Test
	public void emptyStreamDoesNotCrash() {
		StreamInfo info = TorrentioStreamParser.parse(new Stream(), "X", "u");
		assertEquals(StreamInfo.Kind.UNSUPPORTED, info.kind);
		assertEquals(Quality.OTHER, info.quality);
	}

	@Test
	public void sizeUnits() {
		assertEquals(1536, TorrentioStreamParser.toBytes("1.5", "KB"));
		assertEquals(2L * 1024 * 1024 * 1024 * 1024, TorrentioStreamParser.toBytes("2", "TB"));
		assertEquals((long) (1.5 * 1024 * 1024), TorrentioStreamParser.toBytes("1,5", "MiB"));
	}

	@Test
	public void otherAddonsSeedFormats() {
		Stream tpb = stream("TPB+\n1080p", "Movie.2020.1080p.WEB-DL.x264\n👤 342 💾 2.1 GB");
		tpb.infoHash = "1123456789abcdef0123456789abcdef01234567";
		StreamInfo info = TorrentioStreamParser.parse(tpb, "ThePirateBay+", "https://thepiratebay-plus.strem.fun/manifest.json");
		assertEquals(Quality.P1080, info.quality);
		assertEquals(342, info.seeders);
		assertEquals("Movie.2020.1080p.WEB-DL.x264", info.releaseName);

		assertEquals(57, TorrentioStreamParser.parse(stream("X", "Movie.720p\nSeeds: 57"), "X", "").seeders);
		assertEquals(9, TorrentioStreamParser.parse(stream("X", "Movie.720p\n🌱 9 · 700 MB"), "X", "").seeders);
		assertEquals(-1, TorrentioStreamParser.parse(stream("X", "Movie.720p.Subs:3"), "X", "").seeders);
	}
}
