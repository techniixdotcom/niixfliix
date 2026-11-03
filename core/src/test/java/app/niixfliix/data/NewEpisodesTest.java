package app.niixfliix.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.addon.model.Video;
import app.niixfliix.data.model.EpisodeList;

public class NewEpisodesTest {

	private static final long DAY = 24L * 60 * 60 * 1000;
	private static final long NOW = 1_800_000_000_000L;

	private static EpisodeList.Episode ep(int season, int episode, long releasedMs) {
		return new EpisodeList.Episode("tt1:" + season + ":" + episode, season, episode, releasedMs);
	}

	private static boolean hasNew(List<EpisodeList.Episode> episodes, Set<String> watched, Map<String, Long> at) {
		return NewEpisodes.hasNew(episodes, watched::contains, id -> at.getOrDefault(id, 0L), NOW);
	}

	@Test
	public void newEpisodeAfterCatchingUp() {
		List<EpisodeList.Episode> eps = Arrays.asList(ep(1, 1, NOW - 30 * DAY), ep(1, 2, NOW - 20 * DAY),
				ep(1, 3, NOW - 2 * DAY));
		Set<String> watched = new HashSet<>(Arrays.asList("tt1:1:1", "tt1:1:2"));
		Map<String, Long> at = new HashMap<>();
		at.put("tt1:1:2", NOW - 10 * DAY);
		assertTrue(hasNew(eps, watched, at));
	}

	@Test
	public void episodeAlreadyOutWhenWatchedIsNotNew() {
		List<EpisodeList.Episode> eps = Arrays.asList(ep(1, 1, NOW - 30 * DAY), ep(1, 2, NOW - 20 * DAY));
		Set<String> watched = new HashSet<>(Collections.singletonList("tt1:1:1"));
		Map<String, Long> at = new HashMap<>();
		at.put("tt1:1:1", NOW - 5 * DAY);
		assertFalse(hasNew(eps, watched, at));
	}

	@Test
	public void allWatchedIsNotNew() {
		List<EpisodeList.Episode> eps = Arrays.asList(ep(1, 1, NOW - 30 * DAY), ep(1, 2, NOW - 20 * DAY));
		Set<String> watched = new HashSet<>(Arrays.asList("tt1:1:1", "tt1:1:2"));
		assertFalse(hasNew(eps, watched, new HashMap<>()));
	}

	@Test
	public void nothingWatchedIsNotNew() {
		List<EpisodeList.Episode> eps = Collections.singletonList(ep(1, 1, NOW - DAY));
		assertFalse(hasNew(eps, new HashSet<>(), new HashMap<>()));
	}

	@Test
	public void gapBeforeLastWatchedIsNotNew() {
		List<EpisodeList.Episode> eps = Arrays.asList(ep(1, 1, NOW - 30 * DAY), ep(1, 2, NOW - 20 * DAY),
				ep(1, 3, NOW - DAY));
		Set<String> watched = new HashSet<>(Collections.singletonList("tt1:1:2"));
		Map<String, Long> at = new HashMap<>();
		at.put("tt1:1:2", NOW - 10 * DAY);
		assertFalse(hasNew(eps, watched, at));
	}

	@Test
	public void announcedButNotOutIsNotNew() {
		List<EpisodeList.Episode> eps = Arrays.asList(ep(1, 1, NOW - 30 * DAY), ep(1, 2, NOW + 3 * DAY),
				ep(1, 3, 0));
		Set<String> watched = new HashSet<>(Collections.singletonList("tt1:1:1"));
		Map<String, Long> at = new HashMap<>();
		at.put("tt1:1:1", NOW - 10 * DAY);
		assertFalse(hasNew(eps, watched, at));
	}

	@Test
	public void markedWatchedByHandUsesRecentWindow() {
		Set<String> watched = new HashSet<>(Collections.singletonList("tt1:1:1"));
		List<EpisodeList.Episode> recent = Arrays.asList(ep(1, 1, NOW - 60 * DAY), ep(1, 2, NOW - 3 * DAY));
		assertTrue(hasNew(recent, watched, new HashMap<>()));
		List<EpisodeList.Episode> old = Arrays.asList(ep(1, 1, NOW - 60 * DAY),
				ep(1, 2, NOW - NewEpisodes.RECENT_MS - DAY));
		assertFalse(hasNew(old, watched, new HashMap<>()));
	}

	@Test
	public void newSeasonCounts() {
		List<EpisodeList.Episode> eps = Arrays.asList(ep(1, 1, NOW - 400 * DAY), ep(2, 1, NOW - DAY));
		Set<String> watched = new HashSet<>(Collections.singletonList("tt1:1:1"));
		Map<String, Long> at = new HashMap<>();
		at.put("tt1:1:1", NOW - 300 * DAY);
		assertTrue(hasNew(eps, watched, at));
	}

	@Test
	public void episodesSkipSpecialsAndSort() {
		Video special = video("s0", 0, 1, "2024-01-01T00:00:00.000Z");
		Video b = video("b", 2, 1, "2024-03-01T00:00:00.000Z");
		Video a = video("a", 1, 2, null);
		Video noId = video(null, 1, 1, "2024-01-01T00:00:00.000Z");
		List<EpisodeList.Episode> eps = NewEpisodes.episodes(Arrays.asList(special, b, a, noId));
		assertEquals(2, eps.size());
		assertEquals("a", eps.get(0).videoId);
		assertEquals(0, eps.get(0).releasedMs);
		assertEquals("b", eps.get(1).videoId);
		assertEquals(1709251200000L, eps.get(1).releasedMs);
	}

	@Test
	public void badDatesAreUnknown() {
		assertEquals(0, NewEpisodes.releasedMs("soon"));
		assertEquals(0, NewEpisodes.releasedMs(""));
		assertEquals(0, NewEpisodes.releasedMs(null));
	}

	private static Video video(String id, int season, int episode, String released) {
		Video v = new Video();
		v.id = id;
		v.season = season;
		v.episode = episode;
		v.released = released;
		return v;
	}
}
