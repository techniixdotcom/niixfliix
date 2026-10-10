package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Responses;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.addon.model.Subtitle;
import app.niixfliix.app.Constant;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Blocking calls, keep them off the main thread. Catalog/meta results are cached for 10 min. */
public final class AddonClient {

	private static final long CACHE_MS = 10 * 60 * 1000;
	private static final int CACHE_ENTRIES = 120;

	private final OkHttpClient http;
	private final Gson gson;
	private final Map<String, Cached> cache = new LinkedHashMap<String, Cached>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
			return size() > CACHE_ENTRIES;
		}
	};

	/** the add-on answered, just not with a 2xx */
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

	public AddonClient(@NonNull OkHttpClient http, @NonNull Gson gson) {
		this.http = http;
		this.gson = gson;
	}

	@NonNull
	public Manifest manifest(@NonNull String manifestUrl) throws IOException {
		String url = StremioUrl.toManifestUrl(manifestUrl);
		if (url == null) {
			throw new ManifestValidator.InvalidManifestException("not an https manifest URL");
		}
		Manifest manifest = AddonJson.parse(gson, get(url, Constant.MANIFEST_MAX_BYTES), Manifest.class);
		ManifestValidator.validate(manifest);
		return manifest;
	}

	@NonNull
	public List<Meta> catalog(@NonNull InstalledAddon addon, @NonNull String type, @NonNull String catalogId,
			@NonNull Map<String, String> extras) throws IOException {
		String url = AddonUrls.resource(addon.transportUrl, Manifest.RESOURCE_CATALOG, type, catalogId, extras);
		List<Meta> cached = fromCache(url);
		if (cached != null) {
			return cached;
		}
		Responses.Catalog response = AddonJson.parse(gson, get(url, Constant.CATALOG_MAX_BYTES), Responses.Catalog.class);
		List<Meta> metas = new ArrayList<>();
		if (response.metas != null) {
			for (Meta m : response.metas) {
				if (m.isValid()) {
					metas.add(m);
				}
			}
		}
		List<Meta> result = Collections.unmodifiableList(metas);
		putCache(url, result);
		return result;
	}

	@Nullable
	public Meta meta(@NonNull InstalledAddon addon, @NonNull String type, @NonNull String id) throws IOException {
		String url = AddonUrls.resource(addon.transportUrl, Manifest.RESOURCE_META, type, id, Collections.emptyMap());
		Meta cached = fromCache(url);
		if (cached != null) {
			return cached;
		}
		Responses.MetaResponse response = AddonJson.parse(gson, get(url, Constant.META_MAX_BYTES), Responses.MetaResponse.class);
		Meta meta = response.meta != null && response.meta.isValid() ? response.meta : null;
		if (meta != null) {
			putCache(url, meta);
		}
		return meta;
	}

	@NonNull
	public List<Stream> streams(@NonNull InstalledAddon addon, @NonNull String type, @NonNull String videoId)
			throws IOException {
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

	public synchronized void clearCache() {
		cache.clear();
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
