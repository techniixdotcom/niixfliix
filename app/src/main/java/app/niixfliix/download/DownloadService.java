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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import app.niixfliix.R;
import app.niixfliix.app.App;
import app.niixfliix.app.AppGraph;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.download.model.DownloadSource;
import app.niixfliix.ui.MainActivity;
import app.niixfliix.util.FileNames;
import app.niixfliix.util.Logs;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** One download at a time into Downloads/<app name>/, resumable via Range. */
public final class DownloadService extends Service {

	private static final String TAG = "DownloadService";
	private static final int NOTIFICATION_ID = 4101;
	private static final long PROGRESS_INTERVAL_MS = 750;

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
				// decide under the same lock as onStartCommand or a download queued right now gets stranded
				if (!again) {
					worker = null;
					ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
					stopSelf(lastStartId);
					return;
				}
				again = false;
			}
			DownloadItem item;
			while ((item = downloads.claimNextQueued()) != null) {
				download(downloads, item);
			}
		}
	}

	/** Android 15+ gives dataSync services ~6h a day. Pause instead of getting killed. */
	@Override
	public void onTimeout(int startId, int fgsType) {
		Downloads downloads = AppGraph.get().downloads();
		for (DownloadItem item : downloads.all()) {
			if (item.state == DownloadItem.State.RUNNING || item.state == DownloadItem.State.QUEUED) {
				downloads.pause(item.id);
			}
		}
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
		stopSelf();
	}

	private void download(Downloads downloads, DownloadItem item) {
		DownloadSource source = downloads.source(item.id);
		if (source == null || source.url == null) {
			finish(downloads, item, DownloadItem.State.FAILED);
			return;
		}
		Request.Builder request = new Request.Builder().url(source.url);
		for (Map.Entry<String, String> h : source.headers.entrySet()) {
			request.header(h.getKey(), h.getValue());
		}
		if (item.bytesDone > 0) {
			request.header("Range", "bytes=" + item.bytesDone + "-");
		}
		try (Response response = AppGraph.get().http.newCall(request.build()).execute()) {
			if (response.code() == 416 && item.bytesTotal > 0 && item.bytesDone >= item.bytesTotal) {
				complete(downloads, item);
				return;
			}
			if (!response.isSuccessful()) {
				throw new IOException("HTTP " + response.code());
			}
			boolean resumed = response.code() == 206 && item.bytesDone > 0;
			if (!resumed) {
				item.bytesDone = 0;
			}
			ResponseBody body = response.body();
			long length = body.contentLength();
			item.bytesTotal = length >= 0 ? item.bytesDone + length : -1;
			try (OutputStream out = open(item, resumed); InputStream in = body.byteStream()) {
				if (!copy(downloads, item, in, out)) {
					return;
				}
			}
			complete(downloads, item);
		} catch (IOException | RuntimeException e) {
			// headers etc come from the add-on, a bad one shouldn't crash the app
			Logs.w(TAG, "Download failed", e);
			finish(downloads, item, DownloadItem.State.FAILED);
		}
	}

	/** false if paused/deleted midway */
	private boolean copy(Downloads downloads, DownloadItem item, InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[64 * 1024];
		long lastUpdate = 0;
		int n;
		while ((n = in.read(buffer)) != -1) {
			out.write(buffer, 0, n);
			item.bytesDone += n;
			long now = System.currentTimeMillis();
			if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
				lastUpdate = now;
				DownloadItem current = downloads.get(item.id);
				if (current == null || current.state != DownloadItem.State.RUNNING) {
					downloads.changed();
					return false;
				}
				downloads.changed();
				showProgress(item);
			}
		}
		return true;
	}

	private OutputStream open(DownloadItem item, boolean append) throws IOException {
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
				: unique(dir, item.fileName);
		item.contentUri = Uri.fromFile(file).toString();
		return new FileOutputStream(file, append);
	}

	private static File unique(File dir, String name) {
		File file = new File(dir, name);
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String ext = dot > 0 ? name.substring(dot) : "";
		for (int i = 1; file.exists() && i < 1000; i++) {
			file = new File(dir, base + " (" + i + ")" + ext);
		}
		return file;
	}

	private void complete(Downloads downloads, DownloadItem item) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.contentUri != null
				&& item.contentUri.startsWith("content:")) {
			ContentValues values = new ContentValues();
			values.put(MediaStore.Downloads.IS_PENDING, 0);
			getContentResolver().update(Uri.parse(item.contentUri), values, null, null);
		}
		finish(downloads, item, DownloadItem.State.DONE);
	}

	private void finish(Downloads downloads, DownloadItem item, DownloadItem.State state) {
		item.state = state;
		downloads.changed();
	}

	private void showProgress(DownloadItem item) {
		if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
			try {
				NotificationManagerCompat.from(this).notify(NOTIFICATION_ID,
						notification(item.title, item.bytesTotal > 0 ? item.percent() : -1));
			} catch (SecurityException ignored) {
				// permission revoked, keep going
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
