package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

import app.niixfliix.addon.model.Stream;

public class StreamTraitsTest {

	@After
	public void everythingSmoothAgain() {
		StreamRanker.setDecodeCheck(stream -> true);
	}

	@Test
	public void codecFromTheReleaseName() {
		assertEquals(StreamTraits.Codec.AVC, StreamTraits.of("Movie.2023.1080p.WEB-DL.x264-GRP").codec);
		assertEquals(StreamTraits.Codec.AVC, StreamTraits.of("Movie 2023 1080p WEB-DL").codec);
		assertEquals(StreamTraits.Codec.HEVC, StreamTraits.of("Movie.2023.1080p.BluRay.x265-GRP").codec);
		assertEquals(StreamTraits.Codec.HEVC, StreamTraits.of("Movie 2023 1080p WEB-DL DDP5.1 H.265").codec);
		assertEquals(StreamTraits.Codec.HEVC, StreamTraits.of("Movie_2023_HEVC_1080p").codec);
		assertEquals(StreamTraits.Codec.AV1, StreamTraits.of("Movie.2023.1080p.WEB.AV1.Opus").codec);
	}

	@Test
	public void tenBitAndDolbyVision() {
		assertTrue(StreamTraits.of("Movie.2023.1080p.x265.10bit").tenBit);
		assertTrue(StreamTraits.of("Movie 2023 2160p HDR10 HEVC").tenBit);
		assertTrue(StreamTraits.of("Movie.2023.2160p.DV.HEVC").tenBit);
		assertFalse(StreamTraits.of("Movie.2023.1080p.x264").tenBit);
		assertFalse(StreamTraits.of("Movie.1999.DVDRip.x264").tenBit);
		assertTrue(StreamTraits.of("Movie.2023.2160p.DV.HEVC").dolbyVisionOnly);
		assertFalse(StreamTraits.of("Movie.2023.2160p.DV.HDR10.HEVC").dolbyVisionOnly);
		assertFalse(StreamTraits.of("Movie.2023.2160p.HDR.HEVC").dolbyVisionOnly);
	}

	@Test
	public void tagsCount() {
		Stream s = new Stream();
		s.infoHash = "0123456789012345678901234567890123456789";
		StreamInfo info = new StreamInfo(s, "Torrentio", "https://torrentio.strem.fun/manifest.json");
		info.releaseName = "Movie 2023";
		info.tags.add("HDR");
		info.tags.add("HEVC");
		StreamTraits traits = StreamTraits.of(info);
		assertEquals(StreamTraits.Codec.HEVC, traits.codec);
		assertTrue(traits.tenBit);
	}

	private static StreamInfo stream(Quality quality, String release, int seeders) {
		Stream s = new Stream();
		s.infoHash = String.format("%040d", seeders * 10 + quality.ordinal());
		StreamInfo info = new StreamInfo(s, "Torrentio", "https://torrentio.strem.fun/manifest.json");
		info.kind = StreamInfo.Kind.TORRENT;
		info.quality = quality;
		info.releaseName = release;
		info.seeders = seeders;
		return info;
	}

	@Test
	public void smoothStreamsComeFirst() {
		StreamRanker.setDecodeCheck(s -> !StreamTraits.of(s).tenBit);
		StreamInfo hevc10 = stream(Quality.P1080, "Movie 1080p x265 10bit", 500);
		StreamInfo avc = stream(Quality.P1080, "Movie 1080p x264", 20);
		StreamInfo avc720 = stream(Quality.P720, "Movie 720p x264", 300);
		assertSame(avc, StreamRanker.pick(StreamRanker.sort(Arrays.asList(hevc10, avc, avc720)), Quality.P1080, null));
		assertEquals(Arrays.asList(avc, avc720), StreamRanker.bestPerQuality(Arrays.asList(hevc10, avc, avc720)));
	}

	@Test
	public void lowerSmoothQualityBeatsAStutteringOne() {
		StreamRanker.setDecodeCheck(s -> !StreamTraits.of(s).tenBit);
		StreamInfo hevc10 = stream(Quality.P1080, "Movie 1080p x265 10bit", 500);
		StreamInfo avc720 = stream(Quality.P720, "Movie 720p x264", 30);
		assertSame(avc720, StreamRanker.pick(StreamRanker.sort(Arrays.asList(hevc10, avc720)), Quality.P1080, null));
	}

	@Test
	public void stutteringOnlyWhenNothingElse() {
		StreamRanker.setDecodeCheck(s -> !StreamTraits.of(s).tenBit);
		StreamInfo hevc10 = stream(Quality.P1080, "Movie 1080p x265 10bit", 500);
		assertSame(hevc10, StreamRanker.pick(StreamRanker.sort(Arrays.asList(hevc10)), Quality.P1080, null));
	}

	@Test
	public void aBrokenCheckChangesNothing() {
		StreamRanker.setDecodeCheck(s -> {
			throw new IllegalStateException("no codec list");
		});
		StreamInfo a = stream(Quality.P1080, "Movie 1080p x265", 50);
		StreamInfo b = stream(Quality.P1080, "Movie 1080p x264", 20);
		assertSame(a, StreamRanker.pick(StreamRanker.sort(Arrays.asList(a, b)), Quality.P1080, null));
	}
}
