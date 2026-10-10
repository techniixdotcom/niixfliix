package app.niixfliix.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.annotation.OptIn;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

import com.tencent.mmkv.MMKV;

import java.io.File;

import app.niixfliix.BuildConfig;
import app.niixfliix.R;

@OptIn(markerClass = UnstableApi.class)
public final class App extends Application {

	public static final String CHANNEL_DOWNLOADS = "downloads";
	public static final String CHANNEL_UPDATES = "updates";

	@Override
	public void onCreate() {
		super.onCreate();
		if (!BuildConfig.DEBUG) {
			// media3 logs stream urls on errors and debrid urls contain the key
			Log.setLogLevel(Log.LOG_LEVEL_OFF);
		}
		MMKV.initialize(this);
		AppGraph.init(this);
		createChannels();
		if (AppGraph.get().prefs.torrentClearOnExit()) {
			// leftovers from a crash / killed process
			deleteQuietly(AppGraph.torrentCacheDir(this));
		}
	}

	private void createChannels() {
		NotificationManager nm = getSystemService(NotificationManager.class);
		nm.createNotificationChannel(new NotificationChannel(CHANNEL_DOWNLOADS,
				getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW));
		nm.createNotificationChannel(new NotificationChannel(CHANNEL_UPDATES,
				getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT));
	}

	private static void deleteQuietly(File f) {
		File[] children = f.listFiles();
		if (children != null) {
			for (File c : children) {
				deleteQuietly(c);
			}
		}
		//noinspection ResultOfMethodCallIgnored
		f.delete();
	}
}
