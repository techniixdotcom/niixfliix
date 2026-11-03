package app.niixfliix.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
		assertFalse(p.hideAdult());
		assertNull(p.downloadsFolder());
		assertTrue(Prefs.CLEAR_TORRENTS_AFTER_PLAYBACK);
		assertFalse(p.updateCheckOnStart());
	}

	@Test
	public void aPickedDownloadsFolderIsKeptAndCanGoBackToTheDefault() {
		Prefs p = new Prefs(store);
		p.setDownloadsFolder("/home/me/Films");
		verify(store).putString("downloads_folder", "/home/me/Films");
		when(store.getString("downloads_folder", null)).thenReturn("/home/me/Films");
		assertEquals("/home/me/Films", p.downloadsFolder());
		p.setDownloadsFolder(null);
		verify(store).remove("downloads_folder");
		when(store.getString("downloads_folder", null)).thenReturn("");
		assertNull(p.downloadsFolder());
	}

	@Test
	public void choicesAreWrittenUnderStableKeys() {
		new Prefs(store).setHideAdult(true);
		verify(store).putBoolean("hide_adult", true);
	}
}
