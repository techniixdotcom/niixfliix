package app.niixfliix.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import app.niixfliix.addon.Quality;
import app.niixfliix.data.KeyValueStore;

public class PlayerPreferencesTest {

	private KeyValueStore store;

	@Before
	public void emptyStore() {
		store = mock(KeyValueStore.class);
		when(store.getString(anyString(), any())).thenAnswer(call -> call.getArgument(1));
		when(store.getBoolean(anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
		when(store.getInt(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
	}

	@Test
	public void playbackDefaults() {
		PlayerPreferences p = new PlayerPreferences(store);
		assertEquals(Quality.P1080, p.quality(true));
		assertEquals(Quality.P720, p.quality(false));
		assertTrue(p.language().matches("[a-z]{2,3}"));
		assertEquals(100, p.subtitleSize());
		assertEquals(PlayerPreferences.SubtitleColor.WHITE, p.subtitleColor());
		assertEquals(PlayerPreferences.SubtitleBackground.TRANSLUCENT, p.subtitleBackground());
		assertTrue(p.fullscreen());
	}

	@Test
	public void brokenStoredValuesFallBackToDefaults() {
		when(store.getString(anyString(), any())).thenReturn("garbage");
		when(store.getInt(anyString(), anyInt())).thenReturn(999);
		PlayerPreferences player = new PlayerPreferences(store);
		assertEquals(Quality.OTHER, player.quality(true));
		assertEquals(100, player.subtitleSize());
		assertEquals(PlayerPreferences.SubtitleColor.WHITE, player.subtitleColor());
	}

	@Test
	public void languageIsKeptAndJunkIgnored() {
		when(store.getString(anyString(), any())).thenReturn("fr");
		assertEquals("fr", new PlayerPreferences(store).language());
		when(store.getString(anyString(), any())).thenReturn("<script>");
		assertTrue(new PlayerPreferences(store).language().matches("[a-z]{2,3}"));
		new PlayerPreferences(store).setLanguage("ja");
		verify(store).putString("language", "ja");
	}

	@Test
	public void choicesAreWrittenUnderStableKeys() {
		PlayerPreferences p = new PlayerPreferences(store);
		p.setQuality(false, Quality.P480);
		verify(store).putString("quality_mobile", "480p");
	}
}
