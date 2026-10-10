package app.niixfliix.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.niixfliix.data.model.QueueItem;

public class PlayQueueTest {

	/** just enough of a store to check what survives a restart */
	private static final class MemoryStore implements KeyValueStore {
		final Map<String, Object> values = new HashMap<>();

		@Override
		public String getString(String key, String def) {
			return values.containsKey(key) ? (String) values.get(key) : def;
		}

		@Override
		public void putString(String key, String value) {
			values.put(key, value);
		}

		@Override
		public boolean getBoolean(String key, boolean def) {
			return values.containsKey(key) ? (Boolean) values.get(key) : def;
		}

		@Override
		public void putBoolean(String key, boolean value) {
			values.put(key, value);
		}

		@Override
		public int getInt(String key, int def) {
			return values.containsKey(key) ? (Integer) values.get(key) : def;
		}

		@Override
		public void putInt(String key, int value) {
			values.put(key, value);
		}

		@Override
		public void remove(String key) {
			values.remove(key);
		}
	}

	private final MemoryStore store = new MemoryStore();
	private final Gson gson = new Gson();

	private static QueueItem movie(String id) {
		return QueueItem.of(id, "movie", "Movie " + id, null);
	}

	private static QueueItem episode(String showId, String videoId) {
		QueueItem item = QueueItem.of(showId, "series", "Show", null);
		item.videoId = videoId;
		item.episode = "S1 E1";
		return item;
	}

	private static List<String> keys(PlayQueue queue) {
		List<String> out = new ArrayList<>();
		for (QueueItem item : queue.all()) {
			out.add(item.key());
		}
		return out;
	}

	@Test
	public void addsInOrderAndSkipsDuplicates() {
		PlayQueue queue = new PlayQueue(store, gson);
		assertTrue(queue.add(movie("tt1"), false));
		assertTrue(queue.add(movie("tt2"), false));
		assertFalse(queue.add(movie("tt1"), false));
		assertEquals(Arrays.asList("tt1", "tt2"), keys(queue));
	}

	@Test
	public void playNextMovesToTheFront() {
		PlayQueue queue = new PlayQueue(store, gson);
		queue.add(movie("tt1"), false);
		queue.add(movie("tt2"), false);
		queue.add(movie("tt2"), true);
		queue.add(movie("tt3"), true);
		assertEquals(Arrays.asList("tt3", "tt2", "tt1"), keys(queue));
	}

	@Test
	public void episodesOfOneShowAreSeparateEntries() {
		PlayQueue queue = new PlayQueue(store, gson);
		queue.add(episode("tt9", "tt9:1:1"), false);
		queue.add(episode("tt9", "tt9:1:2"), false);
		queue.add(QueueItem.of("tt9", "series", "Show", null), false);
		assertEquals(Arrays.asList("tt9|tt9:1:1", "tt9|tt9:1:2", "tt9"), keys(queue));
		assertEquals("Show · S1 E1", queue.peek().label());
	}

	@Test
	public void survivesARestart() {
		PlayQueue queue = new PlayQueue(store, gson);
		queue.add(movie("tt1"), false);
		queue.add(episode("tt9", "tt9:1:2"), false);
		queue.remove("tt1");
		PlayQueue reopened = new PlayQueue(store, gson);
		assertEquals(Arrays.asList("tt9|tt9:1:2"), keys(reopened));
		reopened.clear();
		assertNull(new PlayQueue(store, gson).peek());
	}

	@Test
	public void corruptDataStartsEmpty() {
		store.putString("play_queue", "{not json");
		assertNull(new PlayQueue(store, gson).peek());
	}
}
