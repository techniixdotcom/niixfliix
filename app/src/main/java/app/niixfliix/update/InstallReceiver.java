package app.niixfliix.update;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.IntentCompat;

import app.niixfliix.R;
import app.niixfliix.app.App;
import app.niixfliix.util.Logs;

public final class InstallReceiver extends BroadcastReceiver {

	private static final int NOTIFICATION_ID = 4102;

	@Override
	public void onReceive(Context context, Intent intent) {
		int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
		if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
			Intent confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
			if (confirm != null) {
				confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(confirm);
				notifyConfirm(context, confirm);
			}
		} else if (status != PackageInstaller.STATUS_SUCCESS) {
			Logs.w("InstallReceiver", "Install failed, status " + status + ": "
					+ intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), null);
			Toast.makeText(context, failureMessage(status), Toast.LENGTH_LONG).show();
		}
	}

	@StringRes
	static int failureMessage(int status) {
		switch (status) {
			case PackageInstaller.STATUS_FAILURE_ABORTED:
				return R.string.update_install_cancelled;
			case PackageInstaller.STATUS_FAILURE_CONFLICT:
				return R.string.update_install_conflict;
			case PackageInstaller.STATUS_FAILURE_STORAGE:
				return R.string.update_install_storage;
			case PackageInstaller.STATUS_FAILURE_BLOCKED:
				return R.string.update_install_blocked;
			case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
				return R.string.update_install_incompatible;
			default:
				return R.string.update_install_failed;
		}
	}

	private static void notifyConfirm(Context context, Intent confirm) {
		NotificationManagerCompat manager = NotificationManagerCompat.from(context);
		if (!manager.areNotificationsEnabled()) {
			return;
		}
		PendingIntent open = PendingIntent.getActivity(context, NOTIFICATION_ID, confirm,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		try {
			manager.notify(NOTIFICATION_ID, new NotificationCompat.Builder(context, App.CHANNEL_UPDATES)
					.setSmallIcon(R.drawable.ic_download)
					.setContentTitle(context.getString(R.string.update_ready_title))
					.setContentText(context.getString(R.string.update_ready_text))
					.setContentIntent(open)
					.setAutoCancel(true)
					.build());
		} catch (SecurityException ignored) {
		}
	}
}
