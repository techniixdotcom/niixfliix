package app.niixfliix.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentCallbacks2;
import android.os.Build;

import androidx.annotation.OptIn;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

import com.tencent.mmkv.MMKV;

import app.niixfliix.BuildConfig;
import app.niixfliix.R;
import app.niixfliix.util.CrashLog;
import app.niixfliix.util.Logs;

@OptIn(markerClass = UnstableApi.class)
public final class App extends Application {

	public static final String CHANNEL_DOWNLOADS = "downloads";
	public static final String CHANNEL_UPDATES = "updates";

	@Override
	public void onCreate() {
		super.onCreate();
		String version = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
				+ (BuildConfig.DEBUG ? " debug" : "");
		CrashLog.install(getNoBackupFilesDir(), version,
				"Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
				Build.MANUFACTURER + " " + Build.MODEL);
		Logs.setSink(android.util.Log::w);
		if (!BuildConfig.DEBUG) {
			Log.setLogLevel(Log.LOG_LEVEL_OFF);
		}
		MMKV.initialize(this);
		AppGraph.init(this);
		createChannels();
		AppGraph graph = AppGraph.get();
		graph.housekeepTorrents(true);
	}

	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
			AppGraph.get().housekeepTorrents(false);
		}
	}

	private void createChannels() {
		NotificationManager nm = getSystemService(NotificationManager.class);
		nm.createNotificationChannel(new NotificationChannel(CHANNEL_DOWNLOADS,
				getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW));
		nm.createNotificationChannel(new NotificationChannel(CHANNEL_UPDATES,
				getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT));
	}
}
