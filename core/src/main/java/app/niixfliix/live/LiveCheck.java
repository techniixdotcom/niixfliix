package app.niixfliix.live;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.niixfliix.addon.AddonClient;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.StreamFilter;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.torrentio.TorrentioStreamParser;
import app.niixfliix.util.MainQueue;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class LiveCheck {

	public interface Listener {
		@MainThread
		void onChecked(@NonNull Meta meta, boolean works);

		@MainThread
		void onDone();
	}

	public static final class Batch {
		private final AtomicBoolean cancelled = new AtomicBoolean();

		public void cancel() {
			cancelled.set(true);
		}
	}

	private static final long WORKS_FOR_MS = 5 * 60_000;
	private static final long DEAD_FOR_MS = 2 * 60_000;
	private static final int THREADS = 6;
	private static final int MAX_PROBES = 3;
	private static final int PROBE_TIMEOUT_MS = 8_000;
	private static final int PEEK_BYTES = 2048;
	private static final int MAX_REMEMBERED = 2000;

	private static final class Result {
		final boolean works;
		final long until;

		Result(boolean works, long until) {
			this.works = works;
			this.until = until;
		}
	}

	private static final Map<String, Result> TITLES = new ConcurrentHashMap<>();
	private static final Map<String, Long> DEAD = new ConcurrentHashMap<>();

	private final AddonRepository addons;
	private final AddonClient client;
	private final OkHttpClient probeHttp;
	private final MainQueue main;
	private final ExecutorService pool;

	public LiveCheck(@NonNull AddonRepository addons, @NonNull AddonClient client, @NonNull OkHttpClient http,
			@NonNull MainQueue main) {
		this.addons = addons;
		this.client = client;
		this.main = main;
		this.probeHttp = http.newBuilder()
				.callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
				.connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
				.readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
				.build();
		AtomicInteger count = new AtomicInteger();
		ThreadPoolExecutor executor = new ThreadPoolExecutor(THREADS, THREADS, 30, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(), r -> {
					Thread t = new Thread(r, "live-check-" + count.incrementAndGet());
					t.setDaemon(true);
					t.setPriority(Thread.NORM_PRIORITY - 1);
					return t;
				});
		executor.allowCoreThreadTimeOut(true);
		this.pool = executor;
	}

	@Nullable
	public static Boolean known(@NonNull String type, @NonNull String id) {
		Result r = TITLES.get(key(type, id));
		return r != null && r.until > System.currentTimeMillis() ? r.works : null;
	}

	public static boolean isKnownDead(@NonNull Stream stream) {
		if (stream.url == null) {
			return false;
		}
		Long until = DEAD.get(stream.url);
		return until != null && until > System.currentTimeMillis();
	}

	@NonNull
	public Batch check(@NonNull List<Meta> metas, @NonNull Listener listener) {
		Batch batch = new Batch();
		if (metas.isEmpty()) {
			main.post(() -> {
				if (!batch.cancelled.get()) {
					listener.onDone();
				}
			});
			return batch;
		}
		AtomicInteger left = new AtomicInteger(metas.size());
		for (Meta meta : metas) {
			pool.execute(() -> {
				boolean works = !batch.cancelled.get() && meta.id != null && meta.type != null
						&& works(meta.type, meta.id);
				main.post(() -> {
					boolean last = left.decrementAndGet() == 0;
					if (batch.cancelled.get()) {
						return;
					}
					listener.onChecked(meta, works);
					if (last) {
						listener.onDone();
					}
				});
			});
		}
		return batch;
	}

	@WorkerThread
	public boolean works(@NonNull String type, @NonNull String id) {
		Boolean cached = known(type, id);
		if (cached != null) {
			return cached;
		}
		boolean works = findWorkingStream(type, id);
		remember(TITLES, key(type, id), new Result(works,
				System.currentTimeMillis() + (works ? WORKS_FOR_MS : DEAD_FOR_MS)));
		return works;
	}

	private boolean findWorkingStream(String type, String id) {
		int probes = 0;
		for (InstalledAddon addon : addons.supporting(Manifest.RESOURCE_STREAM, type, id)) {
			List<Stream> streams;
			try {
				streams = client.streams(addon, type, id);
			} catch (IOException | RuntimeException e) {
				continue;
			}
			for (Stream s : streams) {
				StreamInfo info = TorrentioStreamParser.parse(s, addon.displayName(), addon.transportUrl);
				if (StreamFilter.isPlaceholder(info, type)) {
					continue;
				}
				if (info.kind == StreamInfo.Kind.TORRENT) {
					return true;
				}
				if (info.kind != StreamInfo.Kind.DIRECT || isKnownDead(s)) {
					continue;
				}
				if (probes >= MAX_PROBES) {
					return false;
				}
				probes++;
				if (answers(s)) {
					return true;
				}
				remember(DEAD, s.url, System.currentTimeMillis() + DEAD_FOR_MS);
			}
		}
		return false;
	}

	private boolean answers(Stream stream) {
		String url = stream.url;
		if (url == null) {
			return false;
		}
		Request request;
		try {
			Request.Builder builder = new Request.Builder().url(url).get();
			for (Map.Entry<String, String> h : stream.requestHeaders().entrySet()) {
				builder.header(h.getKey(), h.getValue());
			}
			request = builder.build();
		} catch (IllegalArgumentException e) {
			return false;
		}
		try (Response response = probeHttp.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				return false;
			}
			ResponseBody body = response.body();
			if (body == null) {
				return false;
			}
			byte[] start = readStart(body.byteStream());
			String contentType = response.header("Content-Type");
			return looksLikeVideo(response.request().url().encodedPath(), contentType, start);
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	private static byte[] readStart(InputStream in) throws IOException {
		byte[] buffer = new byte[PEEK_BYTES];
		int filled = 0;
		while (filled < buffer.length) {
			int n = in.read(buffer, filled, buffer.length - filled);
			if (n < 0) {
				break;
			}
			filled += n;
		}
		return Arrays.copyOf(buffer, filled);
	}

	@VisibleForTesting
	static boolean looksLikeVideo(@NonNull String path, @Nullable String contentType, @NonNull byte[] start) {
		String head = new String(start, StandardCharsets.ISO_8859_1).replace("ï»¿", "").trim();
		String type = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
		if (head.startsWith("#EXTM3U")) {
			return true;
		}
		boolean playlist = path.toLowerCase(Locale.ROOT).endsWith(".m3u8") || type.contains("mpegurl");
		if (playlist || head.isEmpty()) {
			return false;
		}
		if (head.startsWith("<")) {
			return head.toLowerCase(Locale.ROOT).contains("<mpd");
		}
		return !type.contains("html") && !type.contains("json") && !type.startsWith("text/")
				&& !head.startsWith("{");
	}

	private static <V> void remember(Map<String, V> map, String key, V value) {
		if (map.size() >= MAX_REMEMBERED) {
			map.clear();
		}
		map.put(key, value);
	}

	private static String key(String type, String id) {
		return type + "|" + id;
	}
}
