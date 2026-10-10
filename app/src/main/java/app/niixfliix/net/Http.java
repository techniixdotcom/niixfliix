package app.niixfliix.net;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import app.niixfliix.BuildConfig;
import app.niixfliix.app.Constant;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class Http {

	private static final long IMAGE_CACHE_BYTES = 100L << 20;
	private static final long READ_TIMEOUT_MS = 30_000;

	private Http() {
	}

	/** Shared client (player too). No https -> http redirects, credentials dropped on cross-host redirects. */
	@NonNull
	public static OkHttpClient create() {
		return new OkHttpClient.Builder()
				.connectTimeout(Constant.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
				.readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
				.followRedirects(true)
				.followSslRedirects(false)
				.addInterceptor(new UserAgent())
				.addInterceptor(new SlowLocalReads())
				.addInterceptor(new SameHostCredentials.MarkOrigin())
				.addNetworkInterceptor(new SameHostCredentials())
				.build();
	}

	@NonNull
	public static OkHttpClient forImages(@NonNull OkHttpClient base, @NonNull File cacheDir) {
		return base.newBuilder()
				.cache(new Cache(new File(cacheDir, "images"), IMAGE_CACHE_BYTES))
				.build();
	}

	@NonNull
	public static String userAgent() {
		return BuildConfig.APP_SLUG + "/" + BuildConfig.VERSION_NAME;
	}

	/** The local torrent server blocks until the next piece is in, which can take a while on a slow swarm. */
	private static final class SlowLocalReads implements Interceptor {
		@NonNull
		@Override
		public Response intercept(@NonNull Chain chain) throws IOException {
			if ("127.0.0.1".equals(chain.request().url().host())) {
				return chain.withReadTimeout(5, TimeUnit.MINUTES).proceed(chain.request());
			}
			return chain.proceed(chain.request());
		}
	}

	private static final class UserAgent implements Interceptor {
		@NonNull
		@Override
		public Response intercept(@NonNull Chain chain) throws IOException {
			Request request = chain.request();
			if (request.header("User-Agent") != null) {
				return chain.proceed(request);
			}
			return chain.proceed(request.newBuilder().header("User-Agent", userAgent()).build());
		}
	}
}
