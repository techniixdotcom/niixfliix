package app.niixfliix.addon;

import android.os.Handler;
import android.os.SystemClock;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.data.KeyValueStore;
import okhttp3.OkHttpClient;

/**
 * Titles, descriptions and episode names in the app's language, whatever it is. Cinemeta only knows English, so:
 * <ul>
 * <li>poster titles come from Wikidata, a row of posters per request;</li>
 * <li>a title's page also asks the TMDB add-on, which has descriptions and episode names in most languages. When
 * TMDB doesn't answer in time, the title still comes from Wikidata.</li>
 * </ul>
 * Only text is taken: ids, episodes and the rest stay Cinemeta's, so streams and watch history work the same.
 * Whatever neither source has stays in English.
 */
public final class Translations {

	private static final String TMDB = "https://94c8cb9f702d-tmdb-addon.baby-beamup.club";
	/** the title's page doesn't wait longer than this for the translation */
	private static final long WAIT_MS = 4000;
	/** after a failure a source is left alone for a while instead of slowing every screen down */
	private static final long BACK_OFF_MS = 5 * 60 * 1000;
	/** posters are collected for this long and asked for together */
	private static final long BATCH_DELAY_MS = 300;
	private static final int BATCH_SIZE = 50;
	private static final int MAX_NAMES = 5000;
	private static final long SAVE_DELAY_MS = 2000;
	private static final String NAMES = "translated_names";

	private final AddonClient client;
	private final OkHttpClient http;
	private final KeyValueStore store;
	private final Gson gson;
	private final Handler main;
	/** the title's page has its own threads, so it never queues behind a screen full of posters */
	private final ExecutorService details = Executors.newFixedThreadPool(2);
	private final ExecutorService posters = Executors.newSingleThreadExecutor();
	private final Runnable save = this::save;
	private final Runnable flush = this::flush;
	private final Source tmdb = new Source();
	private final Source wikidata = new Source();
	/** "es-ES|tt0903747" -> "Breaking Bad", oldest first. Read from the details threads too, so synchronized. */
	private final LinkedHashMap<String, String> names;
	/** main thread from here on */
	private final Map<String, List<Runnable>> waiting = new HashMap<>();
	private final Set<String> batch = new LinkedHashSet<>();
	/** asked already this session and Wikidata has nothing */
	private final Set<String> unknown = new HashSet<>();

	private static final class Source {
		volatile long downUntil;

		boolean isDown() {
			return SystemClock.elapsedRealtime() < downUntil;
		}

		void backOff() {
			downUntil = SystemClock.elapsedRealtime() + BACK_OFF_MS;
		}
	}

	public Translations(@NonNull AddonClient client, @NonNull OkHttpClient http, @NonNull KeyValueStore store,
			@NonNull Gson gson, @NonNull Handler main) {
		this.client = client;
		this.http = http;
		this.store = store;
		this.gson = gson;
		this.main = main;
		names = read();
	}

	/** the TMDB language for the app's language, null when it's English */
	@Nullable
	public static String language() {
		LocaleListCompat app = AppCompatDelegate.getApplicationLocales();
		Locale locale = !app.isEmpty() && app.get(0) != null ? app.get(0) : Locale.getDefault();
		return tmdbLanguage(locale);
	}

	@VisibleForTesting
	@Nullable
	static String tmdbLanguage(@NonNull Locale locale) {
		String language = locale.getLanguage();
		if ("iw".equals(language)) {
			// old Java name for Hebrew
			language = "he";
		}
		// Cinemeta is English already
		if (language.isEmpty() || "en".equals(language)) {
			return null;
		}
		String region = locale.getCountry();
		if ("zh".equals(language)) {
			boolean traditional = "Hant".equals(locale.getScript()) || "TW".equals(region) || "HK".equals(region)
					|| "MO".equals(region);
			return traditional ? "zh-TW" : "zh-CN";
		}
		if (!region.matches("[A-Z]{2}")) {
			region = mainRegion(language);
		}
		return language + "-" + region;
	}

	/** TMDB wants a region; when the phone doesn't say, the main one for the language */
	private static String mainRegion(String language) {
		switch (language) {
			case "ja": return "JP";
			case "ko": return "KR";
			case "uk": return "UA";
			case "cs": return "CZ";
			case "da": return "DK";
			case "sv": return "SE";
			case "el": return "GR";
			case "he": return "IL";
			case "hi": return "IN";
			case "vi": return "VN";
			case "ar": return "SA";
			case "fa": return "IR";
			case "nb": return "NO";
			case "ca": return "ES";
			default: return language.toUpperCase(Locale.ROOT);
		}
	}

	/**
	 * Puts the translated text into {@code meta} (Cinemeta's) and returns it. TMDB and Wikidata are asked at the same
	 * time and waited for a few seconds at most; whatever hasn't answered by then stays in English.
	 */
	@WorkerThread
	@NonNull
	public Meta translate(@NonNull Meta meta) {
		String language = language();
		String id = meta.id;
		String type = meta.type;
		if (language == null || id == null || type == null || !id.startsWith("tt")) {
			return meta;
		}
		long deadline = SystemClock.elapsedRealtime() + WAIT_MS;
		String known = known(language + "|" + id);
		Future<Meta> fromTmdb = tmdb.isDown() ? null : details.submit(() -> client.meta(tmdbAddon(language), type, id));
		Future<Map<String, String>> fromWikidata = known != null || wikidata.isDown() ? null
				: details.submit(() -> Wikidata.titles(http, Collections.singleton(id), Wikidata.languages(language)));

		Meta local = await(fromTmdb, deadline, tmdb);
		String title = null;
		if (local != null) {
			apply(meta, local);
			title = notEmpty(local.name) ? local.name : null;
		}
		if (title == null) {
			Map<String, String> titles = await(fromWikidata, deadline, wikidata);
			title = known != null ? known : titles != null ? titles.get(id) : null;
			if (title != null) {
				meta.name = title;
			}
		} else if (fromWikidata != null) {
			fromWikidata.cancel(true);
		}
		if (title != null && !title.equals(known)) {
			String found = title;
			main.post(() -> remember(language, id, found));
		}
		return meta;
	}

	@Nullable
	private static <T> T await(@Nullable Future<T> lookup, long deadline, Source source) {
		if (lookup == null) {
			return null;
		}
		try {
			return lookup.get(Math.max(0, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			lookup.cancel(true);
			source.backOff();
		} catch (ExecutionException e) {
			// a title the add-on doesn't have is fine, the server not answering properly is not
			Throwable cause = e.getCause();
			boolean missing = cause instanceof AddonClient.HttpException && ((AddonClient.HttpException) cause).code < 500;
			if (!missing) {
				source.backOff();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	/** the translated name if it's known already, for posters */
	@MainThread
	@Nullable
	public String name(@NonNull String id) {
		String language = language();
		return language != null ? known(language + "|" + id) : null;
	}

	/** looks the name up in the background; {@code onFound} runs on the main thread if one turns up */
	@MainThread
	public void request(@NonNull String id, @NonNull Runnable onFound) {
		String language = language();
		if (language == null || !id.startsWith("tt") || wikidata.isDown()) {
			return;
		}
		String key = language + "|" + id;
		if (known(key) != null || unknown.contains(key)) {
			return;
		}
		List<Runnable> callbacks = waiting.get(key);
		if (callbacks != null) {
			callbacks.add(onFound);
			return;
		}
		callbacks = new ArrayList<>();
		callbacks.add(onFound);
		waiting.put(key, callbacks);
		batch.add(key);
		main.removeCallbacks(flush);
		if (batch.size() >= BATCH_SIZE) {
			flush();
		} else {
			main.postDelayed(flush, BATCH_DELAY_MS);
		}
	}

	@MainThread
	private void flush() {
		Map<String, List<String>> byLanguage = new HashMap<>();
		for (String key : batch) {
			String language = key.substring(0, key.indexOf('|'));
			List<String> keys = byLanguage.get(language);
			if (keys == null) {
				keys = new ArrayList<>();
				byLanguage.put(language, keys);
			}
			keys.add(key);
		}
		batch.clear();
		for (Map.Entry<String, List<String>> group : byLanguage.entrySet()) {
			String language = group.getKey();
			List<String> keys = group.getValue();
			List<String> ids = new ArrayList<>();
			for (String key : keys) {
				ids.add(key.substring(language.length() + 1));
			}
			posters.execute(() -> {
				Map<String, String> found = null;
				try {
					found = Wikidata.titles(http, ids, Wikidata.languages(language));
				} catch (IOException | RuntimeException e) {
					wikidata.backOff();
				}
				Map<String, String> titles = found;
				main.post(() -> deliver(language, keys, titles));
			});
		}
	}

	@MainThread
	private void deliver(String language, List<String> keys, @Nullable Map<String, String> titles) {
		for (String key : keys) {
			List<Runnable> callbacks = waiting.remove(key);
			if (titles == null) {
				// Wikidata failed, these get asked again once it's back
				continue;
			}
			String title = titles.get(key.substring(language.length() + 1));
			if (title == null) {
				unknown.add(key);
				continue;
			}
			remember(language, key.substring(language.length() + 1), title);
			if (callbacks != null) {
				for (Runnable r : callbacks) {
					r.run();
				}
			}
		}
	}

	@VisibleForTesting
	static void apply(@NonNull Meta meta, @NonNull Meta local) {
		if (notEmpty(local.name)) {
			meta.name = local.name;
		}
		if (notEmpty(local.description)) {
			meta.description = local.description;
		}
		if (meta.videos == null || local.videos == null) {
			return;
		}
		Map<String, String> titles = new HashMap<>();
		for (Video v : local.videos) {
			String title = v.displayTitle();
			if (!title.isEmpty() && !isPlaceholder(title, v.episodeOrZero())) {
				titles.put(v.seasonOrZero() + ":" + v.episodeOrZero(), title);
			}
		}
		for (Video v : meta.videos) {
			String title = titles.get(v.seasonOrZero() + ":" + v.episodeOrZero());
			if (title != null) {
				v.title = title;
			}
		}
	}

	/** TMDB fills untranslated episodes with "Episodio 5", "第5集" and the like; the English title is better */
	@VisibleForTesting
	static boolean isPlaceholder(@NonNull String title, int episode) {
		if (episode <= 0 || !title.contains(String.valueOf(episode))) {
			return false;
		}
		String rest = title.replaceAll("\\d+", "").trim();
		return rest.isEmpty() || !rest.contains(" ");
	}

	private static boolean notEmpty(@Nullable String s) {
		return s != null && !s.trim().isEmpty();
	}

	private static InstalledAddon tmdbAddon(String language) {
		return new InstalledAddon(TMDB + "/" + AddonUrls.encode("{\"language\":\"" + language + "\"}")
				+ "/manifest.json");
	}

	@Nullable
	private String known(String key) {
		synchronized (names) {
			return names.get(key);
		}
	}

	@MainThread
	private void remember(String language, String id, String name) {
		String key = language + "|" + id;
		synchronized (names) {
			if (name.equals(names.get(key))) {
				return;
			}
			names.remove(key);
			names.put(key, name);
			Iterator<String> oldest = names.keySet().iterator();
			while (names.size() > MAX_NAMES && oldest.hasNext()) {
				oldest.next();
				oldest.remove();
			}
		}
		main.removeCallbacks(save);
		main.postDelayed(save, SAVE_DELAY_MS);
	}

	private void save() {
		String json;
		synchronized (names) {
			json = gson.toJson(names);
		}
		store.putString(NAMES, json);
	}

	private LinkedHashMap<String, String> read() {
		String json = store.getString(NAMES, null);
		if (json != null) {
			try {
				LinkedHashMap<String, String> saved = gson.fromJson(json,
						new TypeToken<LinkedHashMap<String, String>>() { }.getType());
				if (saved != null) {
					return saved;
				}
			} catch (JsonParseException e) {
				// corrupt list, start empty
			}
		}
		return new LinkedHashMap<>();
	}
}
