package app.niixfliix.addon;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

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
import java.util.function.Supplier;

import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.data.KeyValueStore;
import app.niixfliix.util.MainQueue;
import okhttp3.OkHttpClient;

public final class Translations {

	private static final String TMDB = "https://94c8cb9f702d-tmdb-addon.baby-beamup.club";
	private static final long WAIT_MS = 4000;
	private static final long BACK_OFF_MS = 5 * 60 * 1000;
	private static final long BATCH_DELAY_MS = 300;
	private static final int BATCH_SIZE = 50;
	private static final int MAX_NAMES = 5000;
	private static final long SAVE_DELAY_MS = 2000;
	private static final String NAMES = "translated_names";

	private final AddonClient client;
	private final OkHttpClient http;
	private final KeyValueStore store;
	private final Gson gson;
	private final MainQueue main;
	private final Supplier<Locale> appLocale;
	private final ExecutorService details = Executors.newFixedThreadPool(2);
	private final ExecutorService posters = Executors.newSingleThreadExecutor();
	private final Runnable save = this::save;
	private final Runnable flush = this::flush;
	private final Source tmdb = new Source();
	private final Source wikidata = new Source();
	private final Source wikidataSearch = new Source();
	private final LinkedHashMap<String, String> names;
	private final Map<String, List<Runnable>> waiting = new HashMap<>();
	private final Set<String> batch = new LinkedHashSet<>();
	private final Set<String> unknown = new HashSet<>();

	private static final class Source {
		volatile long downUntil;

		boolean isDown() {
			return now() < downUntil;
		}

		void backOff() {
			downUntil = now() + BACK_OFF_MS;
		}
	}

	public Translations(@NonNull AddonClient client, @NonNull OkHttpClient http, @NonNull KeyValueStore store,
			@NonNull Gson gson, @NonNull MainQueue main, @NonNull Supplier<Locale> appLocale) {
		this.client = client;
		this.http = http;
		this.store = store;
		this.gson = gson;
		this.main = main;
		this.appLocale = appLocale;
		names = read();
	}

	@Nullable
	public String language() {
		return tmdbLanguage(appLocale.get());
	}

	private static long now() {
		return System.nanoTime() / 1_000_000;
	}

	@VisibleForTesting
	@Nullable
	static String tmdbLanguage(@NonNull Locale locale) {
		String language = locale.getLanguage();
		if ("iw".equals(language)) {
			language = "he";
		}
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

	@WorkerThread
	@NonNull
	public Meta translate(@NonNull Meta meta) {
		String language = language();
		String id = meta.id;
		String type = meta.type;
		if (language == null || id == null || type == null || !id.startsWith("tt")) {
			return meta;
		}
		long deadline = now() + WAIT_MS;
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
			return lookup.get(Math.max(0, deadline - now()), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			lookup.cancel(true);
			source.backOff();
		} catch (ExecutionException e) {
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

	public static final class Found {
		@NonNull public final String imdbId;
		@NonNull public final String type;

		Found(@NonNull String imdbId, @NonNull String type) {
			this.imdbId = imdbId;
			this.type = type;
		}
	}

	@WorkerThread
	@NonNull
	public List<Found> search(@NonNull String text) {
		String language = language();
		String trimmed = text.trim();
		if (language == null || wikidataSearch.isDown() || trimmed.codePointCount(0, trimmed.length()) < 2) {
			return Collections.emptyList();
		}
		List<String> codes = Wikidata.languages(language);
		String searchLanguage = codes.get(codes.size() - 1);
		List<Wikidata.Hit> hits;
		try {
			hits = Wikidata.search(http, trimmed, searchLanguage);
		} catch (IOException | RuntimeException e) {
			wikidataSearch.backOff();
			return Collections.emptyList();
		}
		List<Found> found = new ArrayList<>(hits.size());
		Map<String, String> titles = new HashMap<>();
		for (Wikidata.Hit hit : hits) {
			found.add(new Found(hit.imdbId, hit.series ? Meta.TYPE_SERIES : Meta.TYPE_MOVIE));
			if (!hit.title.isEmpty()) {
				titles.put(hit.imdbId, hit.title);
			}
		}
		if (!titles.isEmpty()) {
			main.post(() -> {
				for (Map.Entry<String, String> t : titles.entrySet()) {
					if (known(language + "|" + t.getKey()) == null) {
						remember(language, t.getKey(), t.getValue());
					}
				}
			});
		}
		return found;
	}

	@MainThread
	@Nullable
	public String name(@NonNull String id) {
		String language = language();
		return language != null ? known(language + "|" + id) : null;
	}

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
			}
		}
		return new LinkedHashMap<>();
	}
}
