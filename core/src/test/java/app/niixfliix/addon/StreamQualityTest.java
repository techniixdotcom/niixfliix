package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.niixfliix.addon.model.Stream;
import app.niixfliix.torrentio.TorrentioStreamParser;

public class StreamQualityTest {

	private static boolean low(String name, String title, String type) {
		Stream s = new Stream();
		s.name = name;
		s.title = title;
		s.infoHash = "0123456789012345678901234567890123456789";
		return StreamFilter.isLowQuality(TorrentioStreamParser.parse(s, "Torrentio", "https://a.example/manifest.json"),
				type);
	}

	@Test
	public void fullQualityReleasesStay() {
		assertFalse(low("Torrentio\n1080p", "Movie.2023.1080p.BluRay.x264-GRP\n👤 120 💾 2.1 GB ⚙️ YTS", "movie"));
		assertFalse(low("Torrentio\n4k DV", "Movie.2023.2160p.WEB-DL.DDP5.1.Atmos.DV.HEVC\n💾 14.8 GB", "movie"));
		assertFalse(low("Torrentio\n720p", "Show.S01E02.720p.HDTV.x264\n💾 450 MB", "series"));
		assertFalse(low("Torrentio\n1080p", "Show.S02E05.1080p.WEB.H264\n💾 1.2 GB ⚙️ EZTV", "series"));
		assertFalse(low("Torrentio\n1080p", "Movie.2023.1080p.WEB-DL.DTS-HD.MA.5.1\n💾 3.4 GB", "movie"));
		assertFalse(low("Torrentio\n1080p", "Movie.2023.1080p.BluRay\n👤 50", "movie"));
	}

	@Test
	public void camsTelesyncsAndScreenersGo() {
		assertTrue(low("Torrentio\nCAM", "Movie.2025.CAM.x264\n💾 1.4 GB", "movie"));
		assertTrue(low("Torrentio\n1080p", "Movie.2025.1080p.HDCAM.x264\n💾 2 GB", "movie"));
		assertTrue(low("Torrentio\n720p", "Movie 2025 720p HDTS x264\n💾 1.1 GB", "movie"));
		assertTrue(low("Torrentio\n1080p", "Movie.2025.1080p.TeleSync.AAC\n💾 2.2 GB", "movie"));
		assertTrue(low("Torrentio\n1080p", "Movie.2025.1080p.TC.x264\n💾 2.2 GB", "movie"));
		assertTrue(low("Torrentio\n720p", "Movie.2025.720p.DVDSCR\n💾 900 MB", "movie"));
		assertTrue(low("Torrentio\n1080p", "Movie.2025.1080p.PreDVD\n💾 2 GB", "movie"));
		assertTrue(low("Torrentio\n4k", "Movie.2003.2160p.AI.Upscaled.x265\n💾 9 GB", "movie"));
	}

	@Test
	public void aTitleWithACamWordIsNotACam() {
		assertFalse(low("Torrentio\n1080p", "Cam.2018.1080p.WEB-DL.x264\n💾 2.0 GB", "movie"));
		assertFalse(low("Torrentio\n1080p", "TC.Story.2021.1080p.BluRay\n💾 2.0 GB", "movie"));
	}

	@Test
	public void anUnknownResolutionGoes() {
		assertTrue(low("Torrentio", "Movie.2023.WEBRip\n💾 2 GB", "movie"));
		assertFalse(low("Channel", "Live", "tv"));
	}

	@Test
	public void filesTooSmallForTheirResolutionGo() {
		assertTrue(low("Torrentio\n1080p", "Movie.2023.1080p.WEB.x265\n💾 500 MB", "movie"));
		assertTrue(low("Torrentio\n4k", "Movie.2023.2160p.WEB.x265\n💾 1.9 GB", "movie"));
		assertTrue(low("Torrentio\n1080p", "Show.S01E01.1080p.WEB.x265\n💾 120 MB", "series"));
		assertFalse(low("Torrentio\n1080p", "Show.S01E01.1080p.WEB.x265\n💾 400 MB", "series"));
		assertFalse(low("Torrentio\n720p", "Movie.2023.720p.BluRay\n💾 800 MB", "movie"));
	}

	@Test
	public void theTitlePartIsCutAtTheYearOrEpisode() {
		assertEquals("2018.1080p.WEB", StreamFilter.afterTitle("Cam.2018.1080p.WEB"));
		assertEquals("S01E02.720p", StreamFilter.afterTitle("Show.Name.S01E02.720p"));
		assertEquals("Torrentio", StreamFilter.afterTitle("Torrentio"));
	}
}
