package app.niixfliix.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import app.niixfliix.data.model.LibraryItem;

public class StremioAccountTest {

	private static JsonObject item(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	public void readsSavedTitlesOnly() {
		LibraryItem saved = StremioAccount.title(item("{\"_id\":\"tt0111161\",\"name\":\"The Shawshank Redemption\","
				+ "\"type\":\"movie\",\"poster\":\"https://img/p.jpg\",\"removed\":false,\"temp\":false,"
				+ "\"_ctime\":\"2024-03-01T10:00:00.000Z\",\"_mtime\":\"2024-03-01T10:00:00.000Z\",\"state\":{}}"));
		assertNotNull(saved);
		assertEquals("tt0111161", saved.id);
		assertEquals("movie", saved.type);
		assertEquals("https://img/p.jpg", saved.poster);
		assertEquals(1709287200000L, saved.addedAt);

		assertNull(StremioAccount.title(item("{\"_id\":\"a\",\"name\":\"A\",\"type\":\"movie\",\"removed\":true,\"temp\":false}")));
		assertNull(StremioAccount.title(item("{\"_id\":\"b\",\"name\":\"B\",\"type\":\"movie\",\"removed\":false,\"temp\":true}")));
		assertNull(StremioAccount.title(item("{\"name\":\"no id\",\"type\":\"movie\"}")));
	}

	@Test
	public void plainHttpPostersAreDropped() {
		LibraryItem item = StremioAccount.title(item("{\"_id\":\"x\",\"name\":\"X\",\"type\":\"series\","
				+ "\"poster\":\"http://img/p.jpg\",\"removed\":false,\"temp\":false}"));
		assertNotNull(item);
		assertNull(item.poster);
		assertEquals(0, item.addedAt);
	}

	/** stremio-core rejects library items missing any of these, so a typo here would break the account */
	@Test
	public void newItemsHaveEveryFieldStremioExpects() {
		LibraryItem local = new LibraryItem();
		local.id = "tt0944947";
		local.name = "Game of Thrones";
		local.type = "series";
		JsonObject o = StremioAccount.fresh(local, false);
		assertEquals(new HashSet<>(Arrays.asList("_id", "name", "type", "poster", "posterShape", "removed", "temp",
				"_ctime", "_mtime", "state")), o.keySet());
		assertFalse(o.get("removed").getAsBoolean());
		assertFalse(o.get("temp").getAsBoolean());
		assertEquals("", o.get("poster").getAsString());
		Set<String> state = o.getAsJsonObject("state").keySet();
		assertEquals(new HashSet<>(Arrays.asList("lastWatched", "timeWatched", "timeOffset", "overallTimeWatched",
				"timesWatched", "flaggedWatched", "duration", "video_id", "watched", "noNotif")), state);
		assertTrue(o.get("_mtime").getAsString().endsWith("Z"));
		assertTrue(StremioAccount.fresh(local, true).get("removed").getAsBoolean());
	}
}
