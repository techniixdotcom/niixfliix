package app.niixfliix.addon;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.niixfliix.addon.model.Stream;
import app.niixfliix.addon.model.StreamHints;
import app.niixfliix.torrentio.TorrentioStreamParser;

public class StreamFilterTest {

	private static Stream stream(String name, String title, String url) {
		Stream s = new Stream();
		s.name = name;
		s.title = title;
		s.url = url;
		return s;
	}

	private static boolean placeholder(Stream s, String type) {
		return StreamFilter.isPlaceholder(TorrentioStreamParser.parse(s, "Add-on", "https://a.example/manifest.json"),
				type);
	}

	@Test
	public void realStreamsStay() {
		assertFalse(placeholder(stream("Torrentio\n1080p", "Movie.2020.1080p.BluRay.x264\n👤 120 💾 2.1 GB ⚙️ YTS",
				"https://a.example/movie.mkv"), "movie"));
		assertFalse(placeholder(stream("[RD+] Torrentio\n4k", "Movie.2020.2160p\n💾 18.4 GB",
				"https://real-debrid.example/d/abc"), "movie"));
		assertFalse(placeholder(stream("Torrentio\n720p", "Trailer.Park.Boys.S01E01.720p\n💾 400 MB",
				"https://a.example/tpb.mkv"), "series"));
		assertFalse(placeholder(stream("Torrentio\n1080p", "Upgrade.2018.1080p.WEB\n💾 1.6 GB",
				"https://a.example/upgrade.mkv"), "movie"));
		assertFalse(placeholder(stream("Torrentio\n1080p", "Unlocked.2017.1080p\n💾 1.9 GB",
				"https://a.example/unlocked.mkv"), "movie"));
		assertFalse(placeholder(stream("Premiumize\n1080p", "Movie.2020.1080p", "https://premiumize.example/f/1"),
				"movie"));
		assertFalse(placeholder(stream("Channel\nHD", "Live", "https://tv.example/live.m3u8"), "tv"));
	}

	@Test
	public void trailersAreLeftOut() {
		Stream yt = new Stream();
		yt.ytId = "dQw4w9WgXcQ";
		yt.title = "Watch";
		assertTrue(placeholder(yt, "movie"));
		assertTrue(placeholder(stream("Hyper\n1080p", "Official Trailer", "https://cdn.example/x.mp4"), "movie"));
		assertTrue(placeholder(stream("Hyper\n1080p", "Teaser 2", "https://cdn.example/x.mp4"), "movie"));
		assertTrue(placeholder(stream("Add-on", "Movie 1080p", "https://www.youtube.com/watch?v=abc"), "movie"));
		assertTrue(placeholder(stream("Add-on", "Movie 1080p", "https://r3.googlevideo.com/videoplayback?x=1"),
				"movie"));
		assertTrue(placeholder(stream("Add-on", "Movie 1080p", "https://cdn.example/trailers/movie.mp4"), "movie"));
		assertTrue(placeholder(stream("Add-on", "Movie 1080p", "https://cdn.example/m/trailer.mp4?t=1"), "movie"));
		assertTrue(StreamFilter.isTrailerLabel("🎬 Trailer"));
		assertTrue(StreamFilter.isTrailerLabel("Official teaser"));
		assertFalse(StreamFilter.isTrailerLabel("Trailer Park Boys"));
	}

	@Test
	public void paidNoticesAreLeftOut() {
		assertTrue(placeholder(stream("Add-on VIP", "Premium only - upgrade to watch", "https://a.example/x.mp4"),
				"movie"));
		assertTrue(placeholder(stream("Add-on", "Upgrade to Pro for 4K", "https://a.example/x.mp4"), "movie"));
		assertTrue(placeholder(stream("Add-on", "Subscription required", "https://a.example/x.mp4"), "tv"));
		assertTrue(placeholder(stream("Add-on", "Invalid API key", "https://a.example/x.mp4"), "movie"));
		assertTrue(placeholder(stream("Add-on", "Sign in to watch", "https://a.example/x.mp4"), "tv"));
		assertTrue(placeholder(stream("Add-on", "Configure the add-on first", "https://a.example/x.mp4"), "movie"));
		Stream site = new Stream();
		site.name = "Add-on";
		site.externalUrl = "https://a.example/subscribe";
		assertTrue(placeholder(site, "movie"));
	}

	@Test
	public void filmsTooSmallToBeTheFilmAreSamples() {
		Stream small = stream("Add-on\n1080p", "Movie 1080p", "https://a.example/x.mp4");
		small.behaviorHints = new StreamHints();
		small.behaviorHints.videoSize = 30L << 20;
		assertTrue(placeholder(small, "movie"));
		small.behaviorHints.videoSize = 900L << 20;
		assertFalse(placeholder(small, "movie"));
		small.behaviorHints.videoSize = 30L << 20;
		assertFalse(placeholder(small, "series"));
	}
}
