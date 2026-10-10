package app.niixfliix.update;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import app.niixfliix.BuildConfig;
import app.niixfliix.app.Constant;
import app.niixfliix.util.Logs;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * GitHub releases. Installs only if the sha256 matches the published .sha256, same package,
 * higher version, same signer.
 */
public final class UpdateManager {

	public static final class Release {
		@NonNull public final String version;
		@NonNull public final String notes;
		@NonNull final String apkUrl;
		@NonNull final String checksumUrl;
		final long size;

		Release(@NonNull String version, @NonNull String notes, @NonNull String apkUrl, @NonNull String checksumUrl,
				long size) {
			this.version = version;
			this.notes = notes;
			this.apkUrl = apkUrl;
			this.checksumUrl = checksumUrl;
			this.size = size;
		}
	}

	public interface CheckCallback {
		@MainThread
		void onResult(@Nullable Release newer, boolean failed);
	}

	public interface InstallCallback {
		@MainThread
		void onProgress(int percent);

		@MainThread
		void onFailed(@NonNull Failure failure);

		/** system installer took over */
		@MainThread
		void onHandedOver();
	}

	public enum Failure {
		DOWNLOAD,
		TOO_LARGE,
		CHECKSUM,
		WRONG_PACKAGE,
		NOT_NEWER,
		WRONG_SIGNER,
		INSTALLER
	}

	private static final String TAG = "UpdateManager";
	private static final String API = "https://api.github.com/repos/";
	private static final long REQUEST_TIMEOUT_MS = 20_000;

	private final Context context;
	private final OkHttpClient http;
	private final ExecutorService io;
	private final Handler main;

	public UpdateManager(@NonNull Context context, @NonNull OkHttpClient http, @NonNull ExecutorService io,
			@NonNull Handler main) {
		this.context = context;
		this.http = http;
		this.io = io;
		this.main = main;
	}

	public void check(@NonNull CheckCallback callback) {
		io.execute(() -> {
			try {
				Release release = latest();
				Release newer = release != null && isNewer(release.version, BuildConfig.VERSION_NAME) ? release : null;
				main.post(() -> callback.onResult(newer, false));
			} catch (IOException | RuntimeException e) {
				Logs.w(TAG, "Update check failed", e);
				main.post(() -> callback.onResult(null, true));
			}
		});
	}

	public void install(@NonNull Release release, @NonNull InstallCallback callback) {
		io.execute(() -> {
			File apk = new File(new File(context.getCacheDir(), "updates"), BuildConfig.APP_SLUG + ".apk");
			try {
				if (release.size > Constant.UPDATE_MAX_BYTES) {
					throw new FailureException(Failure.TOO_LARGE);
				}
				String expected = fetchChecksum(release.checksumUrl);
				String actual = download(release.apkUrl, apk, callback);
				if (!actual.equalsIgnoreCase(expected)) {
					throw new FailureException(Failure.CHECKSUM);
				}
				verifyPackage(apk);
				startInstaller(apk);
				main.post(callback::onHandedOver);
			} catch (FailureException e) {
				failed(apk, e.failure, callback);
			} catch (SizeCappedReader.TooLargeException e) {
				failed(apk, Failure.TOO_LARGE, callback);
			} catch (IOException | RuntimeException e) {
				Logs.w(TAG, "Update failed", e);
				failed(apk, Failure.DOWNLOAD, callback);
			}
		});
	}

	private void failed(File apk, Failure failure, InstallCallback callback) {
		//noinspection ResultOfMethodCallIgnored
		apk.delete();
		main.post(() -> callback.onFailed(failure));
	}

	private static final class FailureException extends Exception {
		final Failure failure;

		FailureException(Failure failure) {
			this.failure = failure;
		}
	}

	@Nullable
	private Release latest() throws IOException {
		String json = get(API + BuildConfig.GITHUB_REPO + "/releases/latest", Constant.UPDATE_INFO_MAX_BYTES);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		String tag = string(root, "tag_name");
		JsonElement assets = root.get("assets");
		if (tag == null || assets == null || !assets.isJsonArray()) {
			return null;
		}
		String apkUrl = null;
		String checksumUrl = null;
		long size = -1;
		for (JsonElement element : (JsonArray) assets) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject asset = element.getAsJsonObject();
			String name = string(asset, "name");
			String url = string(asset, "browser_download_url");
			if (name == null || url == null || !url.startsWith("https://github.com/")) {
				continue;
			}
			String lower = name.toLowerCase(Locale.ROOT);
			if (lower.startsWith(BuildConfig.APP_SLUG) && lower.endsWith(".apk")) {
				apkUrl = url;
				size = asset.has("size") ? asset.get("size").getAsLong() : -1;
			} else if (lower.startsWith(BuildConfig.APP_SLUG) && lower.endsWith(".apk.sha256")) {
				checksumUrl = url;
			}
		}
		if (apkUrl == null || checksumUrl == null) {
			// no checksum asset, nothing to verify against
			return null;
		}
		String notes = string(root, "body");
		return new Release(tag.startsWith("v") ? tag.substring(1) : tag, notes != null ? notes : "", apkUrl,
				checksumUrl, size);
	}

	@NonNull
	private String fetchChecksum(String url) throws IOException, FailureException {
		String text = get(url, 4096).trim();
		String hex = text.split("\\s+")[0];
		if (!hex.matches("(?i)[0-9a-f]{64}")) {
			throw new FailureException(Failure.CHECKSUM);
		}
		return hex;
	}

	/** returns the sha256 of the written file */
	@NonNull
	private String download(String url, File target, InstallCallback callback) throws IOException {
		File dir = target.getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("Cannot create " + dir);
		}
		MessageDigest digest = sha256();
		Call call = http.newCall(new Request.Builder().url(url).build());
		call.timeout().timeout(10, TimeUnit.MINUTES);
		try (Response response = call.execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("HTTP " + response.code());
			}
			ResponseBody body = response.body();
			long length = body.contentLength();
			if (length > Constant.UPDATE_MAX_BYTES) {
				throw new SizeCappedReader.TooLargeException(Constant.UPDATE_MAX_BYTES);
			}
			try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(target)) {
				byte[] buffer = new byte[64 * 1024];
				long total = 0;
				int lastPercent = -1;
				int n;
				while ((n = in.read(buffer)) != -1) {
					total += n;
					if (total > Constant.UPDATE_MAX_BYTES) {
						throw new SizeCappedReader.TooLargeException(Constant.UPDATE_MAX_BYTES);
					}
					out.write(buffer, 0, n);
					digest.update(buffer, 0, n);
					int percent = length > 0 ? (int) (total * 100 / length) : -1;
					if (percent != lastPercent) {
						lastPercent = percent;
						main.post(() -> callback.onProgress(percent));
					}
				}
			}
		}
		return hex(digest.digest());
	}

	@SuppressWarnings("deprecation")
	private void verifyPackage(File apk) throws FailureException {
		PackageManager pm = context.getPackageManager();
		int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
				? PackageManager.GET_SIGNING_CERTIFICATES
				: PackageManager.GET_SIGNATURES;
		PackageInfo update = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
		PackageInfo installed;
		try {
			installed = pm.getPackageInfo(context.getPackageName(), flags);
		} catch (PackageManager.NameNotFoundException e) {
			throw new FailureException(Failure.WRONG_PACKAGE);
		}
		if (update == null || !context.getPackageName().equals(update.packageName)) {
			throw new FailureException(Failure.WRONG_PACKAGE);
		}
		if (versionCode(update) <= versionCode(installed)) {
			throw new FailureException(Failure.NOT_NEWER);
		}
		Set<String> mine = signers(installed);
		if (mine.isEmpty() || !mine.equals(signers(update))) {
			throw new FailureException(Failure.WRONG_SIGNER);
		}
	}

	@SuppressWarnings("deprecation")
	private static long versionCode(PackageInfo info) {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
	}

	@SuppressWarnings("deprecation")
	private static Set<String> signers(PackageInfo info) {
		Signature[] signatures;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			SigningInfo signing = info.signingInfo;
			if (signing == null) {
				return new HashSet<>();
			}
			signatures = signing.getApkContentsSigners();
		} else {
			signatures = info.signatures;
		}
		Set<String> out = new HashSet<>();
		if (signatures != null) {
			MessageDigest digest = sha256();
			for (Signature s : signatures) {
				out.add(hex(digest.digest(s.toByteArray())));
			}
		}
		return out;
	}

	@SuppressLint("RequestInstallPackagesPolicy")
	private void startInstaller(File apk) throws IOException, FailureException {
		PackageInstaller installer = context.getPackageManager().getPackageInstaller();
		PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
				PackageInstaller.SessionParams.MODE_FULL_INSTALL);
		params.setAppPackageName(context.getPackageName());
		int id = installer.createSession(params);
		try (PackageInstaller.Session session = installer.openSession(id)) {
			try (InputStream in = new FileInputStream(apk);
					OutputStream out = session.openWrite("update.apk", 0, apk.length())) {
				byte[] buffer = new byte[64 * 1024];
				int n;
				while ((n = in.read(buffer)) != -1) {
					out.write(buffer, 0, n);
				}
				session.fsync(out);
			}
			Intent intent = new Intent(context, InstallReceiver.class);
			// mutable: the installer adds status extras (explicit intent, so fine)
			int flags = PendingIntent.FLAG_UPDATE_CURRENT
					| (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
			PendingIntent pending = PendingIntent.getBroadcast(context, id, intent, flags);
			session.commit(pending.getIntentSender());
		} catch (IOException e) {
			installer.abandonSession(id);
			throw e;
		} catch (SecurityException e) {
			installer.abandonSession(id);
			throw new FailureException(Failure.INSTALLER);
		}
	}

	@NonNull
	private String get(String url, long maxBytes) throws IOException {
		Request request = new Request.Builder().url(url).header("Accept", "application/vnd.github+json").build();
		Call call = http.newCall(request);
		call.timeout().timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		try (Response response = call.execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("HTTP " + response.code());
			}
			return SizeCappedReader.readUtf8(response.body().byteStream(), maxBytes);
		}
	}

	/** 1.10.0 > 1.9.2 */
	static boolean isNewer(@NonNull String candidate, @NonNull String current) {
		int[] a = parts(candidate);
		int[] b = parts(current);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0;
			int y = i < b.length ? b[i] : 0;
			if (x != y) {
				return x > y;
			}
		}
		return false;
	}

	private static int[] parts(String version) {
		String clean = version.startsWith("v") ? version.substring(1) : version;
		int dash = clean.indexOf('-');
		if (dash >= 0) {
			clean = clean.substring(0, dash);
		}
		String[] pieces = clean.split("\\.");
		int[] out = new int[pieces.length];
		for (int i = 0; i < pieces.length; i++) {
			try {
				out[i] = Integer.parseInt(pieces[i]);
			} catch (NumberFormatException e) {
				out[i] = 0;
			}
		}
		return out;
	}

	@Nullable
	private static String string(JsonObject object, String name) {
		JsonElement e = object.get(name);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
		}
		return out.toString();
	}
}
