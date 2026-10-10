package app.niixfliix.debrid;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import app.niixfliix.app.Constant;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Checks a key against the service's own API. Blocking. */
public final class DebridKeyCheck {

	public enum Result {
		VALID,
		INVALID,
		/** couldn't tell, key might still be fine */
		UNKNOWN
	}

	private static final long MAX_BYTES = 256 * 1024;

	private DebridKeyCheck() {
	}

	@NonNull
	public static Result check(@NonNull OkHttpClient http, @NonNull DebridService service, @NonNull String key) {
		if (!DebridService.isPlausibleKey(key)) {
			return Result.INVALID;
		}
		Call call = http.newBuilder().followRedirects(false).build().newCall(request(service, key));
		call.timeout().timeout(Constant.ADDON_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		try (Response response = call.execute()) {
			if (response.code() == 401 || response.code() == 403) {
				return Result.INVALID;
			}
			if (!response.isSuccessful()) {
				return Result.UNKNOWN;
			}
			String body = SizeCappedReader.readUtf8(response.body().byteStream(), MAX_BYTES);
			return readBody(service, body);
		} catch (IOException e) {
			return Result.UNKNOWN;
		}
	}

	@NonNull
	static Request request(@NonNull DebridService service, @NonNull String key) {
		HttpUrl.Builder url = new HttpUrl.Builder().scheme("https").host(service.apiHost);
		Request.Builder request = new Request.Builder();
		switch (service) {
			case REAL_DEBRID:
				url.addPathSegments("rest/1.0/user");
				request.header("Authorization", "Bearer " + key);
				break;
			case ALL_DEBRID:
				url.addPathSegments("v4/user").addQueryParameter("agent", "niixfliix");
				request.header("Authorization", "Bearer " + key);
				break;
			case PREMIUMIZE:
				url.addPathSegments("api/account/info").addQueryParameter("apikey", key);
				break;
			case DEBRID_LINK:
				url.addPathSegments("api/v2/account/infos");
				request.header("Authorization", "Bearer " + key);
				break;
			case TORBOX:
				url.addPathSegments("v1/api/user/me");
				request.header("Authorization", "Bearer " + key);
				break;
			case OFFCLOUD:
			default:
				url.addPathSegments("api/account/stats").addQueryParameter("key", key);
				break;
		}
		return request.url(url.build()).build();
	}

	// some of them answer 200 with an error in the body
	@NonNull
	static Result readBody(@NonNull DebridService service, @NonNull String body) {
		String compact = body.replace(" ", "");
		switch (service) {
			case ALL_DEBRID:
			case PREMIUMIZE:
				if (compact.contains("\"status\":\"success\"")) {
					return Result.VALID;
				}
				return compact.contains("\"status\":\"error\"") ? Result.INVALID : Result.UNKNOWN;
			case DEBRID_LINK:
			case TORBOX:
				if (compact.contains("\"success\":true")) {
					return Result.VALID;
				}
				return compact.contains("\"success\":false") ? Result.INVALID : Result.UNKNOWN;
			default:
				return compact.contains("\"error\"") ? Result.INVALID : Result.VALID;
		}
	}
}
