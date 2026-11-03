package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.niixfliix.addon.model.Stream;

public class StreamInfoTest {

	private static StreamInfo info(String url, String releaseName, String fileName) {
		Stream stream = new Stream();
		stream.url = url;
		StreamInfo s = new StreamInfo(stream, "Add-on", "https://addon.example/");
		s.releaseName = releaseName;
		s.fileName = fileName;
		return s;
	}

	@Test
	public void keepsAFileNameThatHasAVideoExtension() {
		assertEquals("Film.2020.1080p.MP4", info("https://x/y", "", "Film.2020.1080p.MP4").downloadFileName("Film"));
	}

	@Test
	public void takesTheExtensionFromTheLink() {
		assertEquals("Film 2020.webm",
				info("https://x/video.WEBM?token=1", "Film 2020", null).downloadFileName("Film"));
	}

	@Test
	public void fallsBackToTheTitleAndMkv() {
		assertEquals("Film · S1E2.mkv", info("https://x/play", "", null).downloadFileName("Film · S1E2"));
	}

	@Test
	public void theExtensionComesFromTheFileNameOrTheLink() {
		assertEquals(".mp4", info("https://x/y", "", "Film.2020.1080p.MP4").downloadExtension());
		assertEquals(".webm", info("https://x/video.WEBM?token=1", "Film 2020", null).downloadExtension());
		assertEquals(".mkv", info("https://x/play", "", null).downloadExtension());
	}

	@Test
	public void onlyReadyHttpsLinksCanBeDownloaded() {
		StreamInfo direct = info("https://x/y", "", null);
		direct.kind = StreamInfo.Kind.DIRECT;
		assertTrue(direct.isDownloadable());
		direct.cached = false;
		assertFalse(direct.isDownloadable());
		StreamInfo torrent = info(null, "", null);
		torrent.kind = StreamInfo.Kind.TORRENT;
		assertFalse(torrent.isDownloadable());
	}
}
