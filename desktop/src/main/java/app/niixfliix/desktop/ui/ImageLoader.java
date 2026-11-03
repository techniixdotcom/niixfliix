package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.URI;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import app.niixfliix.desktop.gtk.MainLoop;
import app.niixfliix.desktop.gtk.Picture;
import app.niixfliix.desktop.gtk.Texture;
import app.niixfliix.net.Http;
import app.niixfliix.net.ImageUrls;
import app.niixfliix.util.Logs;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ImageLoader {

	public enum Kind {
		POSTER,
		BACKDROP,
		OTHER
	}

	private static final String TAG = "ImageLoader";
	private static final long MAX_BYTES = 15L << 20;
	private static final long MAX_PIXELS = 16_000_000L;
	private static final long MEMORY_BYTES = 128L << 20;
	private static final long DISK_BYTES = 256L << 20;
	private static final String DISK_CACHE_CONTROL = "public, max-age=2592000";
	private static final int THREADS = 6;
	private static final long[] RETRY_DELAYS_MS = {500, 1500};
	private static final int MAX_REDIRECTS = 5;
	private static final long FAILED_PAUSE_MS = 60_000;
	private static final int FAILED_ENTRIES = 1000;
	private static final String ACCEPT = "image/webp,image/jpeg,image/png,image/*;q=0.8,*/*;q=0.5";
	private static final ValueLayout.OfInt PIXEL = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

	private static ImageLoader instance;

	private final OkHttpClient http;
	private final ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
		Thread t = new Thread(r, "images");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});
	private final LinkedHashMap<String, Texture> memory = new LinkedHashMap<>(256, 0.75f, true);
	private long memoryBytes;
	private final Map<String, Job> jobs = new HashMap<>();
	private final Map<Picture, Job> waiting = new WeakHashMap<>();
	private final Map<String, Long> failed = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
			return size() > FAILED_ENTRIES;
		}
	};
	private double scale = 1;

	private static final class Job {
		final String key;
		final String source;
		final int width;
		final int height;
		final Set<Picture> views = Collections.newSetFromMap(new WeakHashMap<>());
		final Map<Picture, Runnable> onShown = new WeakHashMap<>();
		volatile boolean cancelled;
		volatile boolean started;

		Job(String key, String source, int width, int height) {
			this.key = key;
			this.source = source;
			this.width = width;
			this.height = height;
		}
	}

	private static final class Pixels {
		final int width;
		final int height;
		final MemorySegment argbPre;

		Pixels(int width, int height, MemorySegment argbPre) {
			this.width = width;
			this.height = height;
			this.argbPre = argbPre;
		}
	}

	private ImageLoader(@NonNull OkHttpClient base, @NonNull File cacheDir) {
		http = Http.forImages(base, cacheDir, DISK_BYTES).newBuilder()
				.addNetworkInterceptor(new KeepOnDisk())
				.build();
		ImageIO.setUseCache(false);
	}

	public static void init(@NonNull OkHttpClient base, @NonNull File cacheDir) {
		instance = new ImageLoader(base, cacheDir);
	}

	public static void setScale(double scale) {
		if (instance != null && scale > 0) {
			instance.scale = Math.min(4, scale);
		}
	}

	public static void into(@NonNull Picture view, @Nullable String url, int boxWidth, int boxHeight,
			@NonNull Kind kind) {
		instance.load(view, url, boxWidth, boxHeight, kind, null);
	}

	public static void into(@NonNull Picture view, @Nullable String url, int boxWidth, int boxHeight,
			@NonNull Kind kind, @NonNull Runnable onShown) {
		instance.load(view, url, boxWidth, boxHeight, kind, onShown);
	}

	private void load(Picture view, @Nullable String url, int boxWidth, int boxHeight, Kind kind,
			@Nullable Runnable onShown) {
		stopWaiting(view);
		String secure = ImageUrls.secure(url);
		if (secure == null) {
			view.show((Texture) null);
			return;
		}
		int width = (int) Math.ceil(boxWidth * scale);
		int height = (int) Math.ceil(boxHeight * scale);
		String source = kind == Kind.POSTER ? ImageUrls.poster(secure, width)
				: kind == Kind.BACKDROP ? ImageUrls.backdrop(secure, width) : secure;
		String key = source + "@" + width + "x" + height;
		Texture cached = memory.get(key);
		if (cached != null && cached.alive()) {
			view.show(cached);
			if (onShown != null) {
				onShown.run();
			}
			return;
		}
		view.show((Texture) null);
		Long when = failed.get(key);
		if (when != null && System.currentTimeMillis() - when < FAILED_PAUSE_MS) {
			return;
		}
		Job job = jobs.get(key);
		if (job == null) {
			job = new Job(key, source, width, height);
			jobs.put(key, job);
			Job queued = job;
			pool.execute(() -> run(queued));
		}
		job.views.add(view);
		if (onShown != null) {
			job.onShown.put(view, onShown);
		}
		waiting.put(view, job);
	}

	private void stopWaiting(Picture view) {
		Job old = waiting.remove(view);
		if (old == null) {
			return;
		}
		old.views.remove(view);
		old.onShown.remove(view);
		if (old.views.isEmpty() && !old.started) {
			old.cancelled = true;
			jobs.remove(old.key, old);
		}
	}

	private void run(Job job) {
		job.started = true;
		if (job.cancelled) {
			return;
		}
		Pixels pixels = fetch(job.source, job.width, job.height);
		MainLoop.post(() -> done(job, pixels));
	}

	private void done(Job job, @Nullable Pixels pixels) {
		jobs.remove(job.key, job);
		Texture texture = null;
		if (pixels == null) {
			failed.put(job.key, System.currentTimeMillis());
		} else {
			try {
				texture = Texture.ofArgbPre(pixels.width, pixels.height, pixels.argbPre);
				toMemory(job.key, texture);
			} catch (RuntimeException e) {
				Logs.w(TAG, "Could not make a texture", e);
			}
		}
		for (Picture view : new ArrayList<>(job.views)) {
			if (waiting.get(view) != job) {
				continue;
			}
			waiting.remove(view);
			if (texture != null) {
				view.show(texture);
				Runnable shown = job.onShown.get(view);
				if (shown != null) {
					shown.run();
				}
			}
		}
	}

	private void toMemory(String key, Texture texture) {
		Texture old = memory.put(key, texture);
		if (old != null) {
			memoryBytes -= old.byteSize();
			old.unref();
		}
		memoryBytes += texture.byteSize();
		Iterator<Map.Entry<String, Texture>> eldest = memory.entrySet().iterator();
		while (memoryBytes > MEMORY_BYTES && memory.size() > 1 && eldest.hasNext()) {
			Map.Entry<String, Texture> entry = eldest.next();
			memoryBytes -= entry.getValue().byteSize();
			entry.getValue().unref();
			eldest.remove();
		}
	}

	@Nullable
	private Pixels fetch(String url, int width, int height) {
		String target = url;
		String problem = "no answer";
		int attempt = 0;
		int redirects = 0;
		while (attempt <= RETRY_DELAYS_MS.length) {
			Request request = new Request.Builder().url(target).header("Accept", ACCEPT).build();
			boolean retry = false;
			try (Response response = http.newCall(request).execute()) {
				int code = response.code();
				if (isRedirect(code)) {
					String location = response.header("Location");
					String secure = location != null ? ImageUrls.secure(URI.create(target).resolve(location.trim())
							.toString()) : null;
					if (secure != null && redirects < MAX_REDIRECTS && !secure.equals(target)) {
						redirects++;
						target = secure;
						continue;
					}
					problem = "HTTP " + code + " redirect that can't be followed over https";
					break;
				}
				if (!response.isSuccessful()) {
					problem = "HTTP " + code;
					retry = code == 408 || code == 425 || code == 429 || code >= 500;
				} else {
					long length = response.body().contentLength();
					if (length > MAX_BYTES) {
						problem = "too big (" + length + " bytes)";
						break;
					}
					byte[] bytes = SizeCappedReader.read(response.body().byteStream(), MAX_BYTES);
					Pixels pixels = decode(bytes, width, height);
					if (pixels != null) {
						return pixels;
					}
					String type = response.header("Content-Type");
					problem = "can't read it (" + (type != null ? type : "no type") + ", " + bytes.length + " bytes)";
					break;
				}
			} catch (IOException e) {
				problem = e.getClass().getSimpleName() + ": " + e.getMessage();
				retry = true;
			} catch (RuntimeException e) {
				problem = e.toString();
				break;
			}
			if (!retry || attempt == RETRY_DELAYS_MS.length || !pause(RETRY_DELAYS_MS[attempt])) {
				break;
			}
			attempt++;
		}
		Logs.w(TAG, "Image failed from " + host(url) + ": " + problem, null);
		return null;
	}

	@Nullable
	static Pixels decode(@NonNull byte[] bytes, int boxWidth, int boxHeight) {
		BufferedImage read = read(bytes, boxWidth, boxHeight);
		if (read == null) {
			return null;
		}
		int[] size = coverSize(read.getWidth(), read.getHeight(), boxWidth, boxHeight);
		BufferedImage out = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB_PRE);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(read, 0, 0, size[0], size[1], null);
		} finally {
			g.dispose();
		}
		int[] argb = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
		MemorySegment pixels = Arena.ofAuto().allocate(4L * argb.length, 4);
		MemorySegment.copy(argb, 0, pixels, PIXEL, 0, argb.length);
		return new Pixels(size[0], size[1], pixels);
	}

	@NonNull
	static int[] coverSize(int width, int height, int boxWidth, int boxHeight) {
		if (boxWidth <= 0 || boxHeight <= 0) {
			return new int[] {width, height};
		}
		double scale = Math.min(1, Math.max((double) boxWidth / width, (double) boxHeight / height));
		return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
	}

	@Nullable
	private static BufferedImage read(byte[] bytes, int boxWidth, int boxHeight) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			if (in == null) {
				return null;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			while (readers.hasNext()) {
				ImageReader reader = readers.next();
				try {
					in.seek(0);
					reader.setInput(in, true, true);
					int w = reader.getWidth(0);
					int h = reader.getHeight(0);
					if (w <= 0 || h <= 0) {
						continue;
					}
					int[] cover = coverSize(w, h, boxWidth, boxHeight);
					int step = Math.max(1, Math.min(w / Math.max(1, cover[0]), h / Math.max(1, cover[1])));
					long pixels = (long) w * h;
					if (pixels / ((long) step * step) > MAX_PIXELS) {
						step = (int) Math.ceil(Math.sqrt((double) pixels / MAX_PIXELS));
					}
					ImageReadParam param = reader.getDefaultReadParam();
					param.setSourceSubsampling(step, step, 0, 0);
					BufferedImage read = reader.read(0, param);
					if (read != null) {
						return read;
					}
				} catch (IOException | RuntimeException e) {
				} finally {
					reader.dispose();
				}
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
		return null;
	}

	private static boolean pause(long ms) {
		try {
			Thread.sleep(ms);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static final class KeepOnDisk implements Interceptor {
		@NonNull
		@Override
		public Response intercept(@NonNull Chain chain) throws IOException {
			Response response = chain.proceed(chain.request());
			String type = response.header("Content-Type");
			if (response.code() != 200 || type == null || !type.startsWith("image/")) {
				return response;
			}
			return response.newBuilder()
					.header("Cache-Control", DISK_CACHE_CONTROL)
					.removeHeader("Pragma")
					.removeHeader("Expires")
					.build();
		}
	}

	private static boolean isRedirect(int code) {
		return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
	}

	private static String host(String url) {
		try {
			String host = URI.create(url).getHost();
			return host != null ? host : "?";
		} catch (IllegalArgumentException e) {
			return "?";
		}
	}
}
