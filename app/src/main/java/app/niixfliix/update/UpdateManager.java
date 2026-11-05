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

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import app.niixfliix.BuildConfig;
import app.niixfliix.util.Logs;
import app.niixfliix.util.MainQueue;
import okhttp3.OkHttpClient;

public final class UpdateManager {

	public interface CheckCallback {
		@MainThread
		void onResult(@NonNull Releases.CheckResult result);
	}

	public interface InstallCallback {
		@MainThread
		void onProgress(int percent);

		@MainThread
		void onFailed(@NonNull Failure failure);

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

	private final Context context;
	private final ExecutorService io;
	private final MainQueue main;
	private final Releases releases;

	public UpdateManager(@NonNull Context context, @NonNull OkHttpClient http, @NonNull ExecutorService io,
			@NonNull MainQueue main) {
		this.context = context;
		this.io = io;
		this.main = main;
		releases = new Releases(http, BuildConfig.GITHUB_REPO, BuildConfig.APP_SLUG, BuildConfig.VERSION_NAME, ".apk");
	}

	public void check(@NonNull CheckCallback callback) {
		io.execute(() -> {
			Releases.CheckResult result = releases.check();
			main.post(() -> callback.onResult(result));
		});
	}

	public void install(@NonNull Releases.Release release, @NonNull InstallCallback callback) {
		io.execute(() -> {
			File apk = new File(new File(context.getCacheDir(), "updates"), BuildConfig.APP_SLUG + ".apk");
			try {
				releases.download(release, apk, percent -> main.post(() -> callback.onProgress(percent)));
				verifyPackage(apk);
				startInstaller(apk);
				main.post(callback::onHandedOver);
			} catch (Releases.DownloadException e) {
				Failure failure = e.problem == Releases.Problem.TOO_LARGE ? Failure.TOO_LARGE
						: e.problem == Releases.Problem.CHECKSUM ? Failure.CHECKSUM : Failure.DOWNLOAD;
				main.post(() -> callback.onFailed(failure));
			} catch (FailureException e) {
				failed(apk, e.failure, callback);
			} catch (IOException | RuntimeException e) {
				Logs.w(TAG, "Update failed", e);
				failed(apk, Failure.INSTALLER, callback);
			}
		});
	}

	private void failed(File apk, Failure failure, InstallCallback callback) {
		if (apk.exists() && !apk.delete()) {
			Logs.w(TAG, "Could not delete the downloaded update", null);
		}
		main.post(() -> callback.onFailed(failure));
	}

	private static final class FailureException extends Exception {
		final Failure failure;

		FailureException(Failure failure) {
			this.failure = failure;
		}
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
		if (!sameSigner(update, installed)) {
			throw new FailureException(Failure.WRONG_SIGNER);
		}
	}

	/**
	 * Whether the update is signed with the installed app's key. One change of key is allowed, the
	 * way Android allows it (key rotation, APK Signature Scheme v3, Android 9 and newer): an APK
	 * signed with a new key that carries the installed key's own signed approval of it. Only
	 * whoever holds the installed key can make that approval, and from then on only the new key is
	 * accepted. Android 8 never changes key; rotated APKs are still signed with the old key for it.
	 */
	private static boolean sameSigner(PackageInfo update, PackageInfo installed) {
		Set<String> mine = signers(installed);
		Set<String> theirs = signers(update);
		if (mine.isEmpty() || theirs.isEmpty()) {
			return false;
		}
		if (mine.equals(theirs)) {
			return true;
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || mine.size() != 1) {
			return false;
		}
		SigningInfo signing = update.signingInfo;
		if (signing == null || signing.hasMultipleSigners() || !signing.hasPastSigningCertificates()) {
			return false;
		}
		Set<String> history = hashes(signing.getSigningCertificateHistory());
		return history.size() > 1 && history.containsAll(mine);
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
		return hashes(signatures);
	}

	private static Set<String> hashes(Signature[] signatures) {
		Set<String> out = new HashSet<>();
		if (signatures != null) {
			MessageDigest digest = Releases.sha256();
			for (Signature s : signatures) {
				if (s != null) {
					out.add(Releases.hex(digest.digest(s.toByteArray())));
				}
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
}
