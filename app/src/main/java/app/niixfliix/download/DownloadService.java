package app.niixfliix.download;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import app.niixfliix.R;
import app.niixfliix.app.App;
import app.niixfliix.app.AppGraph;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.ui.MainActivity;
import app.niixfliix.util.FileNames;

public final class DownloadService extends Service implements Downloads.Target {

	private static final int NOTIFICATION_ID = 4101;

	@Nullable private Thread worker;
	private boolean again;
	private int lastStartId;

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(getString(R.string.download_preparing), -1),
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0);
		synchronized (this) {
			again = true;
			lastStartId = startId;
			if (worker == null) {
				worker = new Thread(this::runQueue, "downloads");
				worker.start();
			}
		}
		return START_NOT_STICKY;
	}

	private void runQueue() {
		Downloads downloads = AppGraph.get().downloads();
		while (true) {
			synchronized (this) {
				if (!again) {
					worker = null;
					ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
					stopSelf(lastStartId);
					return;
				}
				again = false;
			}
			downloads.drain(AppGraph.get().http, this);
		}
	}

	@Override
	public void onTimeout(int startId, int fgsType) {
		AppGraph.get().downloads().pauseAll();
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
		stopSelf();
	}

	@NonNull
	@Override
	public OutputStream open(@NonNull DownloadItem item, boolean append) throws IOException {
		if (item.contentUri == null) {
			Uri tree = DownloadFolder.picked(this, AppGraph.get().prefs);
			Uri file = tree != null ? DownloadFolder.createFile(this, tree,
					FileNames.mimeType(item.fileName, "video/x-matroska"), item.fileName) : null;
			if (file != null) {
				item.contentUri = file.toString();
			}
		}
		Uri saved = item.contentUri != null ? Uri.parse(item.contentUri) : null;
		if (saved != null && DownloadFolder.isDocument(this, saved)) {
			OutputStream out = getContentResolver().openOutputStream(saved, append ? "wa" : "wt");
			if (out == null) {
				throw new IOException("Cannot open output");
			}
			return out;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ContentResolver resolver = getContentResolver();
			Uri uri = item.contentUri != null ? Uri.parse(item.contentUri) : null;
			if (uri == null) {
				ContentValues values = new ContentValues();
				values.put(MediaStore.Downloads.DISPLAY_NAME, item.fileName);
				values.put(MediaStore.Downloads.MIME_TYPE, FileNames.mimeType(item.fileName, "video/x-matroska"));
				values.put(MediaStore.Downloads.RELATIVE_PATH,
						Environment.DIRECTORY_DOWNLOADS + "/" + getString(R.string.app_name));
				values.put(MediaStore.Downloads.IS_PENDING, 1);
				uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
				if (uri == null) {
					throw new IOException("MediaStore refused the file");
				}
				item.contentUri = uri.toString();
			}
			OutputStream out = resolver.openOutputStream(uri, append ? "wa" : "wt");
			if (out == null) {
				throw new IOException("Cannot open output");
			}
			return out;
		}
		File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
				getString(R.string.app_name));
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("Cannot create the download folder");
		}
		File file = item.contentUri != null && item.contentUri.startsWith("file:")
				? new File(Uri.parse(item.contentUri).getPath())
				: FileNames.unique(dir, item.fileName);
		item.contentUri = Uri.fromFile(file).toString();
		return new FileOutputStream(file, append);
	}

	@Override
	public void completed(@NonNull DownloadItem item) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.contentUri != null
				&& item.contentUri.startsWith("content:")
				&& !DownloadFolder.isDocument(this, Uri.parse(item.contentUri))) {
			ContentValues values = new ContentValues();
			values.put(MediaStore.Downloads.IS_PENDING, 0);
			getContentResolver().update(Uri.parse(item.contentUri), values, null, null);
		}
	}

	@Override
	public void progress(@NonNull DownloadItem item) {
		if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
			try {
				NotificationManagerCompat.from(this).notify(NOTIFICATION_ID,
						notification(item.title, item.bytesTotal > 0 ? item.percent() : -1));
			} catch (SecurityException ignored) {
			}
		}
	}

	private Notification notification(String title, int percent) {
		Intent open = new Intent(this, MainActivity.class)
				.putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
				.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent content = PendingIntent.getActivity(this, 0, open,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		return new NotificationCompat.Builder(this, App.CHANNEL_DOWNLOADS)
				.setSmallIcon(R.drawable.ic_download)
				.setContentTitle(title)
				.setContentText(getString(R.string.download_running))
				.setProgress(100, Math.max(0, percent), percent < 0)
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.setContentIntent(content)
				.build();
	}
}
