package app.niixfliix.update;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.IntentCompat;

import app.niixfliix.R;
import app.niixfliix.app.App;

/** not exported, only our own PendingIntent reaches it */
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
				// blocked if we're in the background by now, so post a notification too
				notifyConfirm(context, confirm);
			}
		} else if (status != PackageInstaller.STATUS_SUCCESS) {
			Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show();
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
			// no permission
		}
	}
}
