package app.niixfliix.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.niixfliix.data.model.HistoryEntry;

public class HistoryTest {

	private final MemoryStore store = new MemoryStore();
	private final Gson gson = new Gson();

	private static HistoryEntry entry(String metaId, String videoId, long positionMs) {
		HistoryEntry e = new HistoryEntry();
		e.metaId = metaId;
		e.videoId = videoId;
		e.type = "series";
		e.name = metaId;
		e.positionMs = positionMs;
		e.durationMs = 60 * 60_000;
		return e;
	}

	private static List<String> continueIds(History history) {
		List<String> out = new ArrayList<>();
		for (HistoryEntry e : history.continueWatching()) {
			out.add(e.metaId);
		}
		return out;
	}

	private static void tick() throws InterruptedException {
		Thread.sleep(5);
	}

	@Test
	public void dismissedTitleLeavesContinueWatchingButKeepsItsHistory() {
		History history = new History(store, gson);
		history.save(entry("tt1", "tt1:1:1", 10 * 60_000));
		history.save(entry("tt2", "tt2", 20 * 60_000));
		history.dismiss("tt1");
		assertEquals(Collections.singletonList("tt2"), continueIds(history));
		assertEquals(2, history.recent().size());
		assertEquals(10 * 60_000, history.resumePosition("tt1:1:1"));
	}

	@Test
	public void playingAgainBringsItBack() throws InterruptedException {
		History history = new History(store, gson);
		history.save(entry("tt1", "tt1:1:1", 10 * 60_000));
		history.dismiss("tt1");
		tick();
		history.save(entry("tt1", "tt1:1:2", 5 * 60_000));
		assertEquals(Collections.singletonList("tt1"), continueIds(history));
	}

	@Test
	public void survivesARestart() {
		History history = new History(store, gson);
		history.save(entry("tt1", "tt1:1:1", 10 * 60_000));
		history.dismiss("tt1");
		assertTrue(continueIds(new History(store, gson)).isEmpty());
	}

	@Test
	public void liveTvLeavesNoHistory() {
		History history = new History(store, gson);
		HistoryEntry live = entry("mocktv:1", "mocktv:1", 20 * 60_000);
		live.type = "tv";
		history.save(live);
		assertTrue(history.continueWatching().isEmpty());
		assertEquals(0, history.resumePosition("mocktv:1"));
	}
}
