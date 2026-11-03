package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Responses;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.addon.model.Subtitle;
import app.niixfliix.app.Constant;
import app.niixfliix.live.FreeSports;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class AddonClient {

	private static final long CACHE_MS = 10 * 60 * 1000;
	private static final int CACHE_ENTRIES = 120;
	private static final long CATALOG_FRESH_MS = 24 * 60 * 60 * 1000L;
	private static final long META_FRESH_MS = 6 * 60 * 60 * 1000L;
	private static final long DISK_STALE_MS = 7 * 24 * 60 * 60 * 1000L;
	private static final long DISK_MAX_BYTES = 16L << 20;
	private static final long SIMILAR_FRESH_MS = 14 * 24 * 60 * 60 * 1000L;
	private static final long SIMILAR_STALE_MS = 90 * 24 * 60 * 60 * 1000L;
	private static final long SIMILAR_MAX_BYTES = 1L << 20;

	private final OkHttpClient http;
	private final Gson gson;
	private final Map<String, Cached> cache = new LinkedHashMap<String, Cached>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
			return size() > CACHE_ENTRIES;
		}
	};

	public static final class HttpException extends IOException {
		public final int code;

		HttpException(int code) {
			super("HTTP " + code);
			this.code = code;
		}
	}

	private static final class Cached {
		final Object value;
		final long at;

		Cached(Object value, long at) {
			this.value = value;
			this.at = at;
		}
	}

	@Nullable private DiskCache disk;
	@Nullable private Executor refresher;
	private volatile boolean diskFirst = true;
	private final Set<String> refreshing = Collections.synchronizedSet(new HashSet<>());

	public AddonClient(@NonNull OkHttpClient http, @NonNull Gson gson) {
		this.http = http;
		this.gson = gson;
	}

	public void setDiskCache(@NonNull File dir, @NonNull Executor refresher) {
		this.disk = new DiskCache(dir, DISK_MAX_BYTES);
		this.refresher = refresher;
	}

	@NonNull
	public Manifest manifest(@NonNull String manifestUrl) throws IOException {
		String url = StremioUrl.toManifestUrl(manifestUrl);
		if (url == null) {
			throw new ManifestValidator.InvalidManifestException("not an https manifest URL");
		}
		String body = FreeSports.isIt(url) ? FreeSports.MANIFEST_JSON : get(url, Constant.MANIFEST_MAX_BYTES);
		Manifest manifest = AddonJson.parse(gson, body, Manifest.class);
		ManifestValidator.validate(manifest);
		return manifest;
	}

	@NonNull
	public List<Meta> catalog(@NonNull InstalledAddon addon, @NonNull String type, @NonNull String catalogId,
			@NonNull Map<String, String> extras) throws IOException {
		if (FreeSports.isIt(addon.transportUrl)) {
			return FreeSports.metas(freeSports());
		}
		String url = AddonUrls.resource(addon.transportUrl, Manifest.RESOURCE_CATALOG, type, catalogId, extras);
		List<Meta> cached = fromCache(url);
		if (cached != null) {
			return cached;
		}
		boolean keep = !Catalog.isLiveType(type) && !extras.containsKey(Catalog.EXTRA_SEARCH);
		return fetchKept(url, Constant.CATALOG_MAX_BYTES, keep, CATALOG_FRESH_MS, this::parseCatalog);
	}

	private interface Parser<T> {
		@NonNull
		T parse(@NonNull String body) throws IOException;
	}

	@NonNull
	private <T> T fetchKept(String url, long maxBytes, boolean keep, long freshMs, Parser<T> parser)
			throws IOException {
		DiskCache kept = keep ? disk : null;
		if (kept != null && diskFirst) {
			T fromDisk = fromDisk(kept, url, maxBytes, freshMs, parser);
			if (fromDisk != null) {
				refreshInBackground(kept, url, maxBytes, parser);
				return fromDisk;
			}
		}
		String body;
		try {
			body = get(url, maxBytes);
		} catch (IOException e) {
			T older = kept != null ? fromDisk(kept, url, maxBytes, DISK_STALE_MS, parser) : null;
			if (older != null) {
				return older;
			}
			throw e;
		}
		T result = parser.parse(body);
		putCache(url, result);
		if (kept != null) {
			kept.write(url, body);
		}
		return result;
	}

	@NonNull
	private List<Meta> parseCatalog(@NonNull String body) throws IOException {
		Responses.Catalog response = AddonJson.parse(gson, body, Responses.Catalog.class);
		List<Meta> metas = new ArrayList<>();
		if (response.metas != null) {
			for (Meta m : response.metas) {
				if (m.isValid()) {
					metas.add(m);
				}
			}
		}
		return Collections.unmodifiableList(metas);
	}

	@Nullable
	private <T> T fromDisk(DiskCache kept, String url, long maxBytes, long maxAgeMs, Parser<T> parser) {
		DiskCache.Entry entry = kept.read(url, maxBytes);
		if (entry == null || entry.ageMs > maxAgeMs) {
			return null;
		}
		try {
			T result = parser.parse(entry.body);
			putCache(url, result);
			return result;
		} catch (IOException | RuntimeException e) {
			kept.remove(url);
			return null;
		}
	}

	private <T> void refreshInBackground(DiskCache kept, String url, long maxBytes, Parser<T> parser) {
		Executor executor = refresher;
		if (executor == null || !refreshing.add(url)) {
			return;
		}
		executor.execute(() -> {
			try {
				String body = get(url, maxBytes);
				putCache(url, parser.parse(body));
				kept.write(url, body);
			} catch (IOException | RuntimeException e) {
			} finally {
				refreshing.remove(url);
			}
		});
	}

	@NonNull
	@WorkerThread
	public List<SimilarTitle> similar(@NonNull String imdbId, boolean series, @Nullable String language)
			throws IOException {
		List<String> languages = language != null ? Wikidata.languages(language) : Collections.emptyList();
		String key = "wikidata-similar-v3|" + (series ? "series|" : "movie|") + String.join(",", languages) + "|"
				+ imdbId;
		DiskCache kept = disk;
		if (kept != null) {
			List<SimilarTitle> fresh = similarFromDisk(kept, key, SIMILAR_FRESH_MS);
			if (fresh != null) {
				return fresh;
			}
		}
		try {
			String body = Wikidata.similarBody(http, imdbId, series, languages);
			List<SimilarTitle> result = Wikidata.parseSimilar(body);
			if (kept != null) {
				kept.write(key, body);
			}
			return result;
		} catch (IOException | RuntimeException e) {
			List<SimilarTitle> older = kept != null ? similarFromDisk(kept, key, SIMILAR_STALE_MS) : null;
			if (older != null) {
				return older;
			}
			throw e instanceof IOException ? (IOException) e : new IOException(e);
		}
	}

	@Nullable
	private static List<SimilarTitle> similarFromDisk(@NonNull DiskCache kept, @NonNull String key, long maxAgeMs) {
		DiskCache.Entry entry = kept.read(key, SIMILAR_MAX_BYTES);
		if (entry == null || entry.ageMs > maxAgeMs) {
			return null;
		}
		try {
			return Wikidata.parseSimilar(entry.body);
		} catch (IOException | RuntimeException e) {
			kept.remove(key);
			return null;
		}
	}

	@Nullable
	public Meta meta(@NonNull InstalledAddon addon, @NonNull String type, @NonNull String id) throws IOException {
		if (FreeSports.isIt(addon.transportUrl)) {
			FreeSports.Channel channel = FreeSports.find(freeSports(), id);
			return channel != null ? FreeSports.meta(channel) : null;
		}
		String url = AddonUrls.resource(addon.transportUrl, Manifest.RESOURCE_META, type, id, Collections.emptyMap());
		Meta cached = fromCache(url);
		if (cached != null) {
			return cached;
		}
		try {
			return fetchKept(url, Constant.META_MAX_BYTES, !Catalog.isLiveType(type), META_FRESH_MS, this::parseMeta);
		} catch (NoSuchMeta e) {
			return null;
		}
	}

	private static final class NoSuchMeta extends IOException {
		NoSuchMeta() {
			super("No meta");
		}
	}

	@NonNull
	private Meta parseMeta(@NonNull String body) throws IOException {
		Responses.MetaResponse response = AddonJson.parse(gson, body, Responses.MetaResponse.class);
		if (response.meta == null || !response.meta.isValid()) {
			throw new NoSuchMeta();
		}
		return response.meta;
	}

	@NonNull
	public List<Stream> streams(@NonNull InstalledAddon addon, @NonNull String type, @NonNull String videoId)
			throws IOException {
		if (FreeSports.isIt(addon.transportUrl)) {
			FreeSports.Channel channel = FreeSports.find(freeSports(), videoId);
			return channel != null ? FreeSports.streams(channel) : Collections.emptyList();
		}
		String url = AddonUrls.resource(addon.transportUrl, Manifest.RESOURCE_STREAM, type, videoId, Collections.emptyMap());
		Responses.Streams response = AddonJson.parse(gson, get(url, Constant.STREAMS_MAX_BYTES), Responses.Streams.class);
		return response.streams != null ? response.streams : Collections.emptyList();
	}

	@NonNull
	public List<Subtitle> subtitles(@NonNull InstalledAddon addon, @NonNull String type, @NonNull String videoId,
			@NonNull Map<String, String> extras) throws IOException {
		String url = AddonUrls.resource(addon.transportUrl, Manifest.RESOURCE_SUBTITLES, type, videoId, extras);
		Responses.Subtitles response = AddonJson.parse(gson, get(url, Constant.SUBTITLES_MAX_BYTES), Responses.Subtitles.class);
		List<Subtitle> out = new ArrayList<>();
		if (response.subtitles != null) {
			for (Subtitle s : response.subtitles) {
				if (s.url != null && s.url.startsWith("https://")) {
					out.add(s);
				}
			}
		}
		return out;
	}

	@NonNull
	private List<FreeSports.Channel> freeSports() throws IOException {
		List<FreeSports.Channel> cached = fromCache(FreeSports.PLAYLIST_URL);
		if (cached != null) {
			return cached;
		}
		String country = Locale.getDefault().getCountry();
		return fetchKept(FreeSports.PLAYLIST_URL, FreeSports.MAX_BYTES, true, FreeSports.FRESH_MS,
				body -> FreeSports.parse(body, country));
	}

	public boolean answers(@NonNull String manifestUrl) {
		String url = StremioUrl.toManifestUrl(manifestUrl);
		if (url == null) {
			return false;
		}
		if (FreeSports.isIt(url)) {
			url = FreeSports.PLAYLIST_URL;
		}
		try {
			Request request = new Request.Builder().url(url).header("Accept", "application/json").build();
			Call call = http.newCall(request);
			call.timeout().timeout(Constant.ADDON_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			try (Response response = call.execute()) {
				try {
					SizeCappedReader.read(response.body().byteStream(), Constant.MANIFEST_MAX_BYTES);
				} catch (SizeCappedReader.TooLargeException e) {
				}
				return true;
			}
		} catch (IOException | IllegalArgumentException e) {
			return false;
		}
	}

	public synchronized void clearCache() {
		cache.clear();
		diskFirst = false;
	}

	@NonNull
	private String get(@NonNull String url, long maxBytes) throws IOException {
		Request request = new Request.Builder().url(url).header("Accept", "application/json").build();
		Call call = http.newCall(request);
		call.timeout().timeout(Constant.ADDON_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		try (Response response = call.execute()) {
			if (!response.isSuccessful()) {
				throw new HttpException(response.code());
			}
			ResponseBody body = response.body();
			if (body == null) {
				throw new IOException("Empty body");
			}
			if (body.contentLength() > maxBytes) {
				throw new SizeCappedReader.TooLargeException(maxBytes);
			}
			return SizeCappedReader.readUtf8(body.byteStream(), maxBytes);
		}
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private synchronized <T> T fromCache(String url) {
		Cached c = cache.get(url);
		if (c == null || System.currentTimeMillis() - c.at > CACHE_MS) {
			return null;
		}
		return (T) c.value;
	}

	private synchronized void putCache(String url, Object value) {
		cache.put(url, new Cached(value, System.currentTimeMillis()));
	}
}
