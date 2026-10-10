package app.niixfliix.net;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * On a cross-host redirect, strip everything but plain HTTP headers. OkHttp already drops
 * Authorization; this also catches cookies and add-on proxy headers.
 */
final class SameHostCredentials implements Interceptor {

	private static final Set<String> SAFE = new HashSet<>(Arrays.asList(
			"user-agent", "accept", "accept-encoding", "accept-language", "range", "if-range",
			"if-none-match", "if-modified-since", "connection", "host", "icy-metadata", "content-type",
			"content-length"));

	static final class Origin {
		final String host;

		Origin(String host) {
			this.host = host;
		}
	}

	/** app interceptor, runs once before any redirect */
	static final class MarkOrigin implements Interceptor {
		@NonNull
		@Override
		public Response intercept(@NonNull Chain chain) throws IOException {
			Request request = chain.request();
			if (request.tag(Origin.class) != null) {
				return chain.proceed(request);
			}
			Origin origin = new Origin(request.url().host().toLowerCase(Locale.ROOT));
			return chain.proceed(request.newBuilder().tag(Origin.class, origin).build());
		}
	}

	@NonNull
	@Override
	public Response intercept(@NonNull Chain chain) throws IOException {
		Request request = chain.request();
		Origin origin = request.tag(Origin.class);
		String host = request.url().host().toLowerCase(Locale.ROOT);
		if (origin == null || origin.host.equals(host)) {
			return chain.proceed(request);
		}
		Request.Builder clean = request.newBuilder();
		for (String name : request.headers().names()) {
			if (!SAFE.contains(name.toLowerCase(Locale.ROOT))) {
				clean.removeHeader(name);
			}
		}
		return chain.proceed(clean.build());
	}
}
