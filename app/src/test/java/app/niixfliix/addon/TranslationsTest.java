package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;

public class TranslationsTest {

	@Test
	public void englishIsLeftAlone() {
		assertNull(Translations.tmdbLanguage(Locale.ENGLISH));
		assertNull(Translations.tmdbLanguage(Locale.UK));
	}

	@Test
	public void phoneRegionIsKept() {
		assertEquals("es-MX", Translations.tmdbLanguage(new Locale("es", "MX")));
		assertEquals("fr-CA", Translations.tmdbLanguage(Locale.CANADA_FRENCH));
	}

	@Test
	public void missingRegionGetsTheMainOne() {
		assertEquals("es-ES", Translations.tmdbLanguage(new Locale("es")));
		assertEquals("ja-JP", Translations.tmdbLanguage(Locale.JAPANESE));
		assertEquals("ko-KR", Translations.tmdbLanguage(Locale.KOREAN));
	}

	@Test
	public void chineseScripts() {
		assertEquals("zh-CN", Translations.tmdbLanguage(Locale.SIMPLIFIED_CHINESE));
		assertEquals("zh-TW", Translations.tmdbLanguage(Locale.TRADITIONAL_CHINESE));
		assertEquals("zh-TW", Translations.tmdbLanguage(Locale.forLanguageTag("zh-Hant")));
		assertEquals("zh-CN", Translations.tmdbLanguage(Locale.forLanguageTag("zh")));
	}

	@Test
	public void placeholdersAreSpotted() {
		assertTrue(Translations.isPlaceholder("Episodio 5", 5));
		assertTrue(Translations.isPlaceholder("第5集", 5));
		assertFalse(Translations.isPlaceholder("Piloto", 1));
		assertFalse(Translations.isPlaceholder("Capítulo 5: El regreso", 5));
		assertFalse(Translations.isPlaceholder("1984", 3));
	}

	@Test
	public void onlyTextIsTaken() {
		Meta meta = meta("tt0903747", "Breaking Bad", "A chemistry teacher...",
				video("tt0903747:1:1", 1, 1, "Pilot"), video("tt0903747:1:2", 1, 2, "Cat's in the Bag..."));
		Meta local = meta("tmdb:1396", "Breaking Bad", "Un profesor de química...",
				video("tt0903747:1:1", 1, 1, "Piloto"), video("tt0903747:1:2", 1, 2, "Episodio 2"));

		Translations.apply(meta, local);

		assertEquals("tt0903747", meta.id);
		assertEquals("Un profesor de química...", meta.description);
		assertEquals("Piloto", meta.videos.get(0).displayTitle());
		assertEquals("tt0903747:1:1", meta.videos.get(0).id);
		// TMDB had no real title for this one
		assertEquals("Cat's in the Bag...", meta.videos.get(1).displayTitle());
	}

	private static Meta meta(String id, String name, String description, Video... videos) {
		Meta m = new Meta();
		m.id = id;
		m.type = Meta.TYPE_SERIES;
		m.name = name;
		m.description = description;
		m.videos = new ArrayList<>(Arrays.asList(videos));
		return m;
	}

	private static Video video(String id, int season, int episode, String title) {
		Video v = new Video();
		v.id = id;
		v.season = season;
		v.episode = episode;
		v.title = title;
		return v;
	}
}
