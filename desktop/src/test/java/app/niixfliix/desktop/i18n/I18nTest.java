package app.niixfliix.desktop.i18n;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Locale;

public class I18nTest {

	@Test
	public void pluralCategories() {
		assertEquals("one", PluralRules.select("en", 1));
		assertEquals("other", PluralRules.select("en", 0));
		assertEquals("one", PluralRules.select("fr", 0));
		assertEquals("many", PluralRules.select("fr", 2_000_000));
		assertEquals("other", PluralRules.select("es", 0));
		assertEquals("one", PluralRules.select("ru", 21));
		assertEquals("few", PluralRules.select("ru", 23));
		assertEquals("many", PluralRules.select("ru", 12));
		assertEquals("many", PluralRules.select("ru", 25));
		assertEquals("other", PluralRules.select("ja", 1));
	}

	@Test
	public void systemLanguageIsMatched() {
		assertEquals("en", I18n.match(Locale.US));
		assertEquals("en", I18n.match(Locale.GERMANY));
		assertEquals("es", I18n.match(Locale.of("es", "MX")));
		assertEquals("zh-TW", I18n.match(Locale.TRADITIONAL_CHINESE));
		assertEquals("zh-TW", I18n.match(Locale.forLanguageTag("zh-Hant-HK")));
		assertEquals("zh", I18n.match(Locale.SIMPLIFIED_CHINESE));
		assertEquals(Arrays.asList("zh-TW", "zh", "en"), I18n.chainFor("zh-TW"));
		assertEquals(Arrays.asList("en"), I18n.chainFor("en"));
	}

	@Test
	public void textsComeFromTheExportedTranslations() {
		I18n.use("ru");
		assertEquals("Фильмы", I18n.t("type_movies"));
		assertEquals("Найдено 3 потока", I18n.plural("streams_found", 3, 3));
		assertEquals("Найдено 5 потоков", I18n.plural("streams_found", 5, 5));
		I18n.use("zh-TW");
		assertEquals("電影", I18n.t("type_movies"));
		I18n.use("en");
		assertEquals("50%", I18n.t("percent", 50));
		assertEquals(", ", I18n.t("list_separator"));
		assertEquals("no_such_key", I18n.t("no_such_key"));
	}
}
