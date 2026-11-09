package app.niixfliix.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;

public class SeriesWatchedTest {

	private static final long NOW = 1_800_000_000_000L;

	private final History history = new History(new MemoryStore(), new Gson());

	private static Video episode(int season, int episode, String released) {
		Video v = new Video();
		v.id = "tt1:" + season + ":" + episode;
		v.season = season;
		v.episode = episode;
		v.released = released;
		return v;
	}

	private static Meta series() {
		Meta m = new Meta();
		m.id = "tt1";
		m.type = Meta.TYPE_SERIES;
		m.videos = new ArrayList<>(Arrays.asList(episode(1, 1, "2020-01-01T00:00:00.000Z"),
				episode(1, 2, "2020-01-08T00:00:00.000Z"), episode(2, 1, "2021-01-01T00:00:00.000Z"),
				episode(2, 2, "2099-01-01T00:00:00.000Z"), episode(0, 1, null)));
		return m;
	}

	@Test
	public void onlyEpisodesThatAreOutCount() {
		Meta m = series();
		assertEquals(Arrays.asList("tt1:1:1", "tt1:1:2", "tt1:2:1"), SeriesWatched.out(m, null, NOW));
		assertEquals(Arrays.asList("tt1:2:1"), SeriesWatched.out(m, 2, NOW));
		assertEquals(Arrays.asList("tt1:0:1"), SeriesWatched.out(m, 0, NOW));
	}

	@Test
	public void aSeasonIsMarkedOnItsOwn() {
		Meta m = series();
		assertTrue(SeriesWatched.toggle(history, m, 1, NOW));
		assertTrue(history.isWatched("tt1:1:1"));
		assertTrue(history.isWatched("tt1:1:2"));
		assertFalse(history.isWatched("tt1:2:1"));
		assertTrue(SeriesWatched.isWatched(history, m, 1, NOW));
		assertFalse(SeriesWatched.isWatched(history, m, null, NOW));
	}

	@Test
	public void theWholeSeriesLeavesEpisodesNotOutYetUnwatched() {
		Meta m = series();
		assertTrue(SeriesWatched.toggle(history, m, null, NOW));
		assertTrue(history.isWatched("tt1:2:1"));
		assertFalse(history.isWatched("tt1:2:2"));
		assertFalse(history.isWatched("tt1:0:1"));
		assertFalse(SeriesWatched.toggle(history, m, null, NOW));
		assertFalse(history.isWatched("tt1:1:1"));
		assertFalse(history.isWatched("tt1:2:1"));
	}

	@Test
	public void nothingOutIsNeverWatched() {
		Meta m = new Meta();
		m.type = Meta.TYPE_SERIES;
		m.videos = new ArrayList<>(Arrays.asList(episode(1, 1, "2099-01-01T00:00:00.000Z")));
		assertFalse(SeriesWatched.isWatched(history, m, null, NOW));
	}
}
