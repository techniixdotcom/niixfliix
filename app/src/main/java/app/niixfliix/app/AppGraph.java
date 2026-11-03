package app.niixfliix.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import java.io.File;
import java.util.Locale;

import app.niixfliix.addon.StreamRanker;
import app.niixfliix.data.KeystoreKeys;
import app.niixfliix.data.MmkvStore;
import app.niixfliix.download.DownloadFolder;
import app.niixfliix.download.DownloadService;
import app.niixfliix.download.Downloads;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.net.Network;
import app.niixfliix.player.DeviceDecoders;
import app.niixfliix.player.PlaybackController;
import app.niixfliix.torrent.TorrentEngine;
import app.niixfliix.update.UpdateManager;
import app.niixfliix.util.AndroidMainQueue;
import app.niixfliix.util.Logs;

public final class AppGraph extends Core {

	private static final String TAG = "AppGraph";
	private static final String OLD_PICTURE_CACHE = "images";

	public final Context context;

	private UpdateManager updateManager;
	private Downloads downloads;
	private PlaybackController playback;

	private AppGraph(@NonNull Context context) {
		super(new AndroidMainQueue(), MmkvStore::new, new KeystoreKeys(), AppGraph::appLocale,
				torrentCacheDir(context));
		this.context = context;
		if (oldLiveSitesRemoved) {
			main.post(() -> {
				try {
					CookieManager.getInstance().removeAllCookies(null);
					WebStorage.getInstance().deleteAllData();
				} catch (RuntimeException e) {
					Logs.w(TAG, "Could not clear the old Live sites' web data", e);
				}
			});
		}
		StreamRanker.setDecodeCheck(DeviceDecoders::isSmooth);
		io.execute(DeviceDecoders::warmUp);
		io.execute(() -> deleteFolder(new File(context.getCacheDir(), OLD_PICTURE_CACHE)));
	}

	static void init(@NonNull Context context) {
		new AppGraph(context);
	}

	@NonNull
	public static AppGraph get() {
		return (AppGraph) Core.get();
	}

	@Override
	protected boolean onUnmeteredNetwork() {
		return Network.isUnmetered(context);
	}

	@NonNull
	@Override
	protected TorrentEngine.Limits torrentLimits() {
		return TorrentEngine.Limits.PHONE;
	}

	@NonNull
	private static Locale appLocale() {
		LocaleListCompat app = AppCompatDelegate.getApplicationLocales();
		Locale first = app.isEmpty() ? null : app.get(0);
		return first != null ? first : Locale.getDefault();
	}

	@NonNull
	@MainThread
	public PlaybackController playback() {
		if (playback == null) {
			playback = new PlaybackController(context);
		}
		return playback;
	}

	@NonNull
	public synchronized UpdateManager updates() {
		if (updateManager == null) {
			updateManager = new UpdateManager(context, http, io, main);
		}
		return updateManager;
	}

	@NonNull
	public synchronized Downloads downloads() {
		if (downloads == null) {
			downloads = new Downloads(new MmkvStore("downloads"), secrets, gson, main, new Downloads.Platform() {
				@Override
				public void start() {
					ContextCompat.startForegroundService(context, new Intent(context, DownloadService.class));
				}

				@Override
				public void deleteFile(@NonNull DownloadItem item) {
					deleteDownload(item);
				}
			});
		}
		return downloads;
	}

	private void deleteDownload(DownloadItem item) {
		Uri uri = Uri.parse(item.contentUri);
		try {
			if (ContentResolver.SCHEME_FILE.equals(uri.getScheme()) && uri.getPath() != null) {
				File file = new File(uri.getPath());
				if (file.exists() && !file.delete()) {
					Logs.w(TAG, "Could not delete a downloaded file", null);
				}
			} else if (DownloadFolder.isDocument(context, uri)) {
				DownloadFolder.delete(context, uri);
			} else {
				context.getContentResolver().delete(uri, null, null);
			}
		} catch (SecurityException | IllegalArgumentException e) {
			Logs.w(TAG, "Could not delete a downloaded file", e);
		}
	}

	@NonNull
	private static File torrentCacheDir(@NonNull Context context) {
		return new File(context.getCacheDir(), "torrents");
	}

	private static void deleteFolder(@NonNull File dir) {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File f : files) {
			if (!f.delete()) {
				Logs.w(TAG, "Could not delete " + f.getName(), null);
			}
		}
		if (!dir.delete()) {
			Logs.w(TAG, "Could not delete " + dir.getName(), null);
		}
	}
}
