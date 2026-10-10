package app.niixfliix.update;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import app.niixfliix.R;
import app.niixfliix.app.AppGraph;

public final class UpdatePrompt {

	private static final int MAX_NOTES = 2000;

	private UpdatePrompt() {
	}

	/** manual check, always reports back */
	public static void check(@NonNull Activity activity) {
		Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
		AppGraph.get().updates().check((release, failed) -> {
			if (!alive(activity)) {
				return;
			}
			if (release != null) {
				offer(activity, release);
			} else {
				Toast.makeText(activity, failed ? R.string.update_check_failed : R.string.update_none,
						Toast.LENGTH_SHORT).show();
			}
		});
	}

	/** start-up check, silent unless there's an update */
	public static void checkQuietly(@NonNull Activity activity) {
		AppGraph.get().updates().check((release, failed) -> {
			if (release != null && alive(activity)) {
				offer(activity, release);
			}
		});
	}

	private static void offer(Activity activity, UpdateManager.Release release) {
		String notes = release.notes.length() > MAX_NOTES ? release.notes.substring(0, MAX_NOTES) + "…" : release.notes;
		new MaterialAlertDialogBuilder(activity)
				.setTitle(activity.getString(R.string.update_available, release.version))
				.setMessage(notes.isEmpty() ? activity.getString(R.string.update_no_notes) : notes)
				.setPositiveButton(R.string.update_install, (d, w) -> install(activity, release))
				.setNegativeButton(R.string.update_later, null)
				.show();
	}

	private static void install(Activity activity, UpdateManager.Release release) {
		if (!activity.getPackageManager().canRequestPackageInstalls()) {
			new MaterialAlertDialogBuilder(activity)
					.setTitle(R.string.update_permission_title)
					.setMessage(R.string.update_permission_message)
					.setPositiveButton(R.string.update_open_settings, (d, w) -> activity.startActivity(
							new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
									Uri.parse("package:" + activity.getPackageName()))))
					.setNegativeButton(R.string.action_cancel, null)
					.show();
			return;
		}
		LinearProgressIndicator bar = new LinearProgressIndicator(activity);
		bar.setIndeterminate(true);
		int padding = activity.getResources().getDimensionPixelSize(R.dimen.screen_padding);
		bar.setPadding(padding, padding, padding, 0);
		AlertDialog progress = new MaterialAlertDialogBuilder(activity)
				.setTitle(R.string.update_downloading)
				.setView(bar)
				.setCancelable(false)
				.show();
		AppGraph.get().updates().install(release, new UpdateManager.InstallCallback() {
			@Override
			public void onProgress(int percent) {
				if (percent < 0) {
					return;
				}
				if (bar.isIndeterminate()) {
					// setIndeterminate(false) only works while hidden
					bar.setVisibility(View.INVISIBLE);
					bar.setIndeterminate(false);
					bar.setVisibility(View.VISIBLE);
				}
				bar.setProgressCompat(percent, true);
			}

			@Override
			public void onFailed(@NonNull UpdateManager.Failure failure) {
				dismiss(activity, progress);
				if (alive(activity)) {
					new MaterialAlertDialogBuilder(activity)
							.setTitle(R.string.update_failed_title)
							.setMessage(message(failure))
							.setPositiveButton(R.string.action_ok, null)
							.show();
				}
			}

			@Override
			public void onHandedOver() {
				dismiss(activity, progress);
			}
		});
	}

	/** rotation = destroyed but not finishing */
	private static boolean alive(Activity activity) {
		return !activity.isFinishing() && !activity.isDestroyed();
	}

	private static void dismiss(Activity activity, AlertDialog dialog) {
		if (dialog.isShowing() && !activity.isDestroyed()) {
			dialog.dismiss();
		}
	}

	private static int message(UpdateManager.Failure failure) {
		switch (failure) {
			case TOO_LARGE:
				return R.string.update_failed_too_large;
			case CHECKSUM:
				return R.string.update_failed_checksum;
			case WRONG_PACKAGE:
			case WRONG_SIGNER:
				return R.string.update_failed_untrusted;
			case NOT_NEWER:
				return R.string.update_failed_not_newer;
			case INSTALLER:
				return R.string.update_install_failed;
			case DOWNLOAD:
			default:
				return R.string.update_failed_download;
		}
	}
}
