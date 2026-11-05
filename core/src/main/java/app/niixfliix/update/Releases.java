package app.niixfliix.update;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.niixfliix.app.Constant;
import app.niixfliix.util.Logs;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class Releases {

	public static final class Release {
		@NonNull public final String version;
		@NonNull public final String notes;
		@NonNull public final String fileName;
		@NonNull final String fileUrl;
		@NonNull final String checksumUrl;
		/** The file's Ed25519 signature (.sig), when the release has one. */
		@Nullable final String signatureUrl;
		/** The signing-key handovers ({@link Signatures}), when the release has them. */
		@Nullable final String handoverUrl;
		public final long size;

		Release(@NonNull String version, @NonNull String notes, @NonNull String fileName, @NonNull String fileUrl,
				@NonNull String checksumUrl, @Nullable String signatureUrl, @Nullable String handoverUrl, long size) {
			this.version = version;
			this.notes = notes;
			this.fileName = fileName;
			this.fileUrl = fileUrl;
			this.checksumUrl = checksumUrl;
			this.signatureUrl = signatureUrl;
			this.handoverUrl = handoverUrl;
			this.size = size;
		}
	}

	public enum Status {
		UPDATE,
		UP_TO_DATE,
		REPO_NOT_FOUND,
		NO_RELEASES,
		NO_FILE,
		NO_CHECKSUM,
		NO_SIGNATURE,
		RATE_LIMITED,
		NETWORK
	}

	public static final class CheckResult {
		@NonNull public final Status status;
		@Nullable public final Release release;
		@Nullable public final String version;

		CheckResult(@NonNull Status status, @Nullable Release release, @Nullable String version) {
			this.status = status;
			this.release = release;
			this.version = version;
		}

		static CheckResult of(@NonNull Status status) {
			return new CheckResult(status, null, null);
		}
	}

	public enum Problem {
		DOWNLOAD,
		TOO_LARGE,
		CHECKSUM,
		SIGNATURE
	}

	public static final class DownloadException extends Exception {
		@NonNull public final Problem problem;

		DownloadException(@NonNull Problem problem, @Nullable Throwable cause) {
			super(problem.name(), cause);
			this.problem = problem;
		}
	}

	private static final String TAG = "Releases";
	private static final String API = "https://api.github.com/repos/";
	private static final long REQUEST_TIMEOUT_MS = 20_000;
	private static final int RELEASES_PER_PAGE = 30;
	private static final Pattern VERSION = Pattern.compile("(\\d+(?:\\.\\d+)+)");
	/** The release asset with the signing-key handovers: the slug followed by this. */
	static final String HANDOVER_SUFFIX = "-linux-key-handover.txt";
	private static final int MAX_SIGNATURE_BYTES = 1024;
	private static final int MAX_HANDOVER_BYTES = 64 * 1024;

	private final OkHttpClient http;
	private final String repo;
	private final String slug;
	private final String current;
	private final String extension;
	private final List<String> trustedKeys;

	/** Releases checked by their SHA-256 only. Android: the APK's own signature is checked on top. */
	public Releases(@NonNull OkHttpClient http, @NonNull String repo, @NonNull String slug,
			@NonNull String currentVersion, @NonNull String extension) {
		this(http, repo, slug, currentVersion, extension, new ArrayList<>());
	}

	/**
	 * Releases that also have to be signed with one of {@code trustedKeys} (Ed25519, hex), or a key
	 * one of them handed over to. With no keys, nothing is required.
	 */
	public Releases(@NonNull OkHttpClient http, @NonNull String repo, @NonNull String slug,
			@NonNull String currentVersion, @NonNull String extension, @NonNull List<String> trustedKeys) {
		this.http = http;
		this.repo = repo;
		this.slug = slug;
		this.current = currentVersion;
		this.extension = extension;
		this.trustedKeys = new ArrayList<>(trustedKeys);
	}

	@WorkerThread
	@NonNull
	public CheckResult check() {
		try {
			return choose(get(API + repo + "/releases?per_page=" + RELEASES_PER_PAGE, Constant.UPDATE_INFO_MAX_BYTES,
					true), current, slug, extension, !trustedKeys.isEmpty());
		} catch (HttpStatusException e) {
			Logs.w(TAG, "Update check failed", e);
			return CheckResult.of(e.code == 404 ? Status.REPO_NOT_FOUND
					: e.code == 403 || e.code == 429 ? Status.RATE_LIMITED : Status.NETWORK);
		} catch (IOException | RuntimeException e) {
			Logs.w(TAG, "Update check failed", e);
			return CheckResult.of(Status.NETWORK);
		}
	}

	@WorkerThread
	public void download(@NonNull Release release, @NonNull File target, @NonNull IntConsumer percent)
			throws DownloadException {
		try {
			if (release.size > Constant.UPDATE_MAX_BYTES) {
				throw new DownloadException(Problem.TOO_LARGE, null);
			}
			String expected = fetchChecksum(release.checksumUrl);
			String actual = save(release.fileUrl, target, percent);
			if (!actual.equalsIgnoreCase(expected)) {
				throw new DownloadException(Problem.CHECKSUM, null);
			}
			if (!trustedKeys.isEmpty()) {
				checkSignature(release, actual);
			}
		} catch (DownloadException e) {
			delete(target);
			throw e;
		} catch (SizeCappedReader.TooLargeException e) {
			delete(target);
			throw new DownloadException(Problem.TOO_LARGE, e);
		} catch (IOException | RuntimeException e) {
			Logs.w(TAG, "Update download failed", e);
			delete(target);
			throw new DownloadException(Problem.DOWNLOAD, e);
		}
	}

	/**
	 * The SHA-256 only proves the file arrived as it was uploaded. The signature proves it was
	 * made with the app's own key, which nobody can add to a release without having that key.
	 */
	private void checkSignature(Release release, String sha256) throws IOException, DownloadException {
		if (release.signatureUrl == null) {
			throw new DownloadException(Problem.SIGNATURE, null);
		}
		byte[] signature = getBytes(release.signatureUrl, MAX_SIGNATURE_BYTES);
		String handovers = release.handoverUrl != null ? get(release.handoverUrl, MAX_HANDOVER_BYTES, false) : "";
		if (!Signatures.verifyRelease(sha256, signature, Signatures.trusted(trustedKeys, handovers))) {
			throw new DownloadException(Problem.SIGNATURE, null);
		}
	}

	private static void delete(File file) {
		if (file.exists() && !file.delete()) {
			Logs.w(TAG, "Could not delete " + file.getName(), null);
		}
	}

	@VisibleForTesting
	@NonNull
	static CheckResult choose(@NonNull String json, @NonNull String current, @NonNull String slug,
			@NonNull String extension) {
		return choose(json, current, slug, extension, false);
	}

	@VisibleForTesting
	@NonNull
	static CheckResult choose(@NonNull String json, @NonNull String current, @NonNull String slug,
			@NonNull String extension, boolean needSignature) {
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			return CheckResult.of(Status.NETWORK);
		}
		if (!root.isJsonArray()) {
			return CheckResult.of(Status.NETWORK);
		}
		String suffix = extension.toLowerCase(Locale.ROOT);
		String bestVersion = null;
		JsonObject best = null;
		Asset bestFile = null;
		Asset bestChecksum = null;
		Asset bestSignature = null;
		Asset bestHandover = null;
		for (JsonElement element : root.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject release = element.getAsJsonObject();
			JsonElement draft = release.get("draft");
			if (draft != null && draft.isJsonPrimitive() && draft.getAsBoolean()) {
				continue;
			}
			Asset file = null;
			Asset checksum = null;
			Asset handover = null;
			List<Asset> checksums = new ArrayList<>();
			List<Asset> signatures = new ArrayList<>();
			JsonElement assets = release.get("assets");
			if (assets != null && assets.isJsonArray()) {
				for (JsonElement a : assets.getAsJsonArray()) {
					Asset asset = Asset.of(a, slug);
					if (asset == null) {
						continue;
					}
					if (asset.lowerName.endsWith(suffix) && file == null) {
						file = asset;
					} else if (asset.lowerName.endsWith(suffix + ".sha256")) {
						checksums.add(asset);
					} else if (asset.lowerName.endsWith(suffix + ".sig")) {
						signatures.add(asset);
					} else if (asset.lowerName.equals(slug + HANDOVER_SUFFIX)) {
						handover = asset;
					}
				}
			}
			for (Asset c : checksums) {
				if (file != null && c.lowerName.equals(file.lowerName + ".sha256")) {
					checksum = c;
					break;
				}
				if (checksum == null) {
					checksum = c;
				}
			}
			Asset signature = null;
			for (Asset s : signatures) {
				if (file != null && s.lowerName.equals(file.lowerName + ".sig")) {
					signature = s;
					break;
				}
			}
			String version = versionIn(string(release, "tag_name"));
			if (version == null && file != null) {
				version = versionIn(file.lowerName);
			}
			if (version != null && (bestVersion == null || isNewer(version, bestVersion))) {
				bestVersion = version;
				best = release;
				bestFile = file;
				bestChecksum = checksum;
				bestSignature = signature;
				bestHandover = handover;
			}
		}
		if (best == null) {
			return CheckResult.of(Status.NO_RELEASES);
		}
		if (!isNewer(bestVersion, current)) {
			return CheckResult.of(Status.UP_TO_DATE);
		}
		if (bestFile == null) {
			return new CheckResult(Status.NO_FILE, null, bestVersion);
		}
		if (bestChecksum == null) {
			return new CheckResult(Status.NO_CHECKSUM, null, bestVersion);
		}
		if (needSignature && bestSignature == null) {
			return new CheckResult(Status.NO_SIGNATURE, null, bestVersion);
		}
		String notes = string(best, "body");
		return new CheckResult(Status.UPDATE, new Release(bestVersion, notes != null ? notes : "", bestFile.name,
				bestFile.url, bestChecksum.url, bestSignature != null ? bestSignature.url : null,
				bestHandover != null ? bestHandover.url : null, bestFile.size), bestVersion);
	}

	private static final class Asset {
		final String name;
		final String lowerName;
		final String url;
		final long size;

		private Asset(String name, String url, long size) {
			this.name = name;
			this.lowerName = name.toLowerCase(Locale.ROOT);
			this.url = url;
			this.size = size;
		}

		@Nullable
		static Asset of(JsonElement element, String slug) {
			if (!element.isJsonObject()) {
				return null;
			}
			JsonObject asset = element.getAsJsonObject();
			String name = string(asset, "name");
			String url = string(asset, "browser_download_url");
			if (name == null || url == null || !url.startsWith("https://github.com/")) {
				return null;
			}
			String lower = name.toLowerCase(Locale.ROOT);
			if (!lower.startsWith(slug) || lower.contains("debug") || name.contains("/") || name.contains("\\")) {
				return null;
			}
			JsonElement size = asset.get("size");
			return new Asset(name, url, size != null && size.isJsonPrimitive() ? size.getAsLong() : -1);
		}
	}

	@VisibleForTesting
	@Nullable
	static String versionIn(@Nullable String text) {
		if (text == null) {
			return null;
		}
		Matcher m = VERSION.matcher(text);
		return m.find() ? m.group(1) : null;
	}

	@NonNull
	private String fetchChecksum(String url) throws IOException, DownloadException {
		String text = get(url, 4096, false).trim();
		String hex = text.split("\\s+")[0];
		if (!hex.matches("(?i)[0-9a-f]{64}")) {
			throw new DownloadException(Problem.CHECKSUM, null);
		}
		return hex;
	}

	@NonNull
	private String save(String url, File target, IntConsumer onPercent) throws IOException {
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
				int lastPercent = -2;
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
						onPercent.accept(percent);
					}
				}
			}
		}
		return hex(digest.digest());
	}

	@NonNull
	private byte[] getBytes(String url, int maxBytes) throws IOException {
		Call call = http.newCall(new Request.Builder().url(url).build());
		call.timeout().timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		try (Response response = call.execute()) {
			if (!response.isSuccessful()) {
				throw new HttpStatusException(response.code());
			}
			ResponseBody body = response.body();
			if (body.contentLength() > maxBytes) {
				throw new SizeCappedReader.TooLargeException(maxBytes);
			}
			try (InputStream in = body.byteStream()) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				int n;
				while ((n = in.read(buffer)) != -1) {
					if (out.size() + n > maxBytes) {
						throw new SizeCappedReader.TooLargeException(maxBytes);
					}
					out.write(buffer, 0, n);
				}
				return out.toByteArray();
			}
		}
	}

	@NonNull
	private String get(String url, long maxBytes, boolean api) throws IOException {
		Request.Builder request = new Request.Builder().url(url);
		if (api) {
			request.header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28");
		}
		Call call = http.newCall(request.build());
		call.timeout().timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		try (Response response = call.execute()) {
			if (!response.isSuccessful()) {
				throw new HttpStatusException(response.code());
			}
			return SizeCappedReader.readUtf8(response.body().byteStream(), maxBytes);
		}
	}

	private static final class HttpStatusException extends IOException {
		final int code;

		HttpStatusException(int code) {
			super("HTTP " + code);
			this.code = code;
		}
	}

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

	@NonNull
	public static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@NonNull
	public static String hex(@NonNull byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
		}
		return out.toString();
	}
}
