package app.niixfliix.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class PrefsTest {

	private KeyValueStore store;

	@Before
	public void emptyStore() {
		store = mock(KeyValueStore.class);
		when(store.getBoolean(anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
		when(store.getInt(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
	}

	@Test
	public void defaults() {
		Prefs p = new Prefs(store);
		assertFalse(p.isSetupDone());
		assertTrue(p.hideAdult());
		assertFalse(p.torrentWifiOnly());
		assertEquals(2048, p.torrentCacheMb());
		assertTrue(p.torrentClearOnExit());
		assertFalse(p.updateCheckOnStart());
	}

	@Test
	public void choicesAreWrittenUnderStableKeys() {
		new Prefs(store).setHideAdult(false);
		verify(store).putBoolean("hide_adult", false);
	}
}
