package app.niixfliix.desktop.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;

import app.niixfliix.util.Logs;

public final class I18n {

	public static final List<String> LANGUAGES = Collections.unmodifiableList(
			Arrays.asList("en", "es", "fr", "ja", "ko", "ru", "tr", "zh", "zh-TW"));

	private static final String TAG = "I18n";
	private static final String DIR = "/app/niixfliix/desktop/strings/";

	private static volatile Locale locale = Locale.ENGLISH;
	private static volatile List<JsonObject> tables = Collections.emptyList();

	private I18n() {
	}

	public static synchronized void use(@Nullable String language) {
		String tag = language != null && LANGUAGES.contains(language) ? language : match(Locale.getDefault());
		Locale chosen = Locale.forLanguageTag(tag);
		List<JsonObject> chain = new ArrayList<>();
		for (String candidate : chainFor(tag)) {
			JsonObject table = read(candidate);
			if (table != null) {
				chain.add(table);
			}
		}
		locale = chosen;
		tables = chain;
		Locale.setDefault(chosen);
	}

	@NonNull
	public static Locale locale() {
		return locale;
	}

	@NonNull
	static String match(@NonNull Locale system) {
		String language = system.getLanguage();
		if ("zh".equals(language)) {
			String region = system.getCountry();
			boolean traditional = "Hant".equals(system.getScript()) || "TW".equals(region) || "HK".equals(region)
					|| "MO".equals(region);
			return traditional ? "zh-TW" : "zh";
		}
		return LANGUAGES.contains(language) ? language : "en";
	}

	@NonNull
	static List<String> chainFor(@NonNull String tag) {
		List<String> out = new ArrayList<>();
		out.add(tag);
		int dash = tag.indexOf('-');
		if (dash > 0) {
			out.add(tag.substring(0, dash));
		}
		if (!out.contains("en")) {
			out.add("en");
		}
		return out;
	}

	@NonNull
	public static String t(@NonNull String key, @NonNull Object... args) {
		String pattern = find("strings", key);
		if (pattern == null) {
			Logs.w(TAG, "Missing string " + key, null);
			return key;
		}
		return format(pattern, args);
	}

	@NonNull
	public static String plural(@NonNull String key, int count, @NonNull Object... args) {
		for (JsonObject table : tables) {
			JsonElement forms = table.getAsJsonObject("plurals").get(key);
			if (forms == null || !forms.isJsonObject()) {
				continue;
			}
			JsonObject f = forms.getAsJsonObject();
			String quantity = PluralRules.select(locale.getLanguage(), count);
			JsonElement chosen = f.has(quantity) ? f.get(quantity) : f.get("other");
			if (chosen != null) {
				return format(chosen.getAsString(), args);
			}
		}
		Logs.w(TAG, "Missing plural " + key, null);
		return key;
	}

	@NonNull
	public static List<String> array(@NonNull String key) {
		for (JsonObject table : tables) {
			JsonElement items = table.getAsJsonObject("arrays").get(key);
			if (items != null && items.isJsonArray()) {
				List<String> out = new ArrayList<>();
				for (JsonElement item : (JsonArray) items) {
					out.add(item.getAsString());
				}
				return out;
			}
		}
		Logs.w(TAG, "Missing array " + key, null);
		return Collections.emptyList();
	}

	@Nullable
	private static String find(String kind, String key) {
		for (JsonObject table : tables) {
			JsonElement value = table.getAsJsonObject(kind).get(key);
			if (value != null && value.isJsonPrimitive()) {
				return value.getAsString();
			}
		}
		return null;
	}

	@NonNull
	private static String format(String pattern, Object... args) {
		if (args.length == 0 && !pattern.contains("%%")) {
			return pattern;
		}
		try {
			return String.format(locale, pattern, args);
		} catch (IllegalFormatException e) {
			Logs.w(TAG, "Bad format in " + pattern, e);
			return pattern;
		}
	}

	@Nullable
	private static JsonObject read(String tag) {
		try (InputStream in = I18n.class.getResourceAsStream(DIR + tag + ".json")) {
			if (in == null) {
				return null;
			}
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
		} catch (IOException | JsonParseException e) {
			Logs.w(TAG, "Unreadable strings for " + tag, e);
			return null;
		}
	}
}
