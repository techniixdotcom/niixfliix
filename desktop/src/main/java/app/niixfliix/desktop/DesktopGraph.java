package app.niixfliix.desktop;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

import app.niixfliix.app.AppInfo;
import app.niixfliix.app.Core;
import app.niixfliix.data.KeyValueStore;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Caches;
import app.niixfliix.desktop.platform.FileStore;
import app.niixfliix.desktop.platform.KeyringKeys;
import app.niixfliix.desktop.platform.Paths;
import app.niixfliix.desktop.platform.UiQueue;
import app.niixfliix.desktop.player.DesktopPlayback;
import app.niixfliix.download.Downloads;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.update.Releases;
import app.niixfliix.util.FileNames;
import app.niixfliix.util.Logs;

public final class DesktopGraph extends Core {

	private static final String TAG = "DesktopGraph";

	public final DesktopPrefs desktopPrefs;
	public final Releases releases;

	private DesktopPlayback playback;
	private Downloads downloads;
	private Thread downloadWorker;
	private boolean downloadsAgain;

	private DesktopGraph(@NonNull Executor ui, @NonNull Function<String, KeyValueStore> stores) {
		super(new UiQueue(ui), stores, new KeyringKeys(Paths.data()), I18n::locale,
				Caches.torrents());
		desktopPrefs = new DesktopPrefs(stores.apply("settings"));
		Paths.setPickedDownloads(prefs.downloadsFolder());
		releases = new Releases(http, AppInfo.GITHUB_REPO, AppInfo.APP_SLUG, AppInfo.VERSION_NAME, ".flatpak");
	}

	@NonNull
	static DesktopGraph create(@NonNull Executor ui) {
		File data = Paths.data();
		Map<String, KeyValueStore> open = new HashMap<>();
		return new DesktopGraph(ui, name -> open.computeIfAbsent(name, n -> new FileStore(data, n)));
	}

	@NonNull
	public static DesktopGraph get() {
		return (DesktopGraph) Core.get();
	}

	@NonNull
	@MainThread
	public DesktopPlayback playback() {
		if (playback == null) {
			playback = new DesktopPlayback(this);
		}
		return playback;
	}

	@NonNull
	public synchronized Downloads downloads() {
		if (downloads == null) {
			downloads = new Downloads(new FileStore(Paths.data(), "downloads"), secrets, gson, main,
					new Downloads.Platform() {
						@Override
						public void start() {
							startDownloadWorker();
						}

						@Override
						public void deleteFile(@NonNull DownloadItem item) {
							File file = downloadedFile(item);
							if (file != null && file.exists() && !file.delete()) {
								Logs.w(TAG, "Could not delete " + file.getName(), null);
							}
						}
					});
		}
		return downloads;
	}

	public static File downloadedFile(@NonNull DownloadItem item) {
		if (item.contentUri == null || !item.contentUri.startsWith("file:")) {
			return null;
		}
		try {
			return new File(URI.create(item.contentUri));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private synchronized void startDownloadWorker() {
		downloadsAgain = true;
		if (downloadWorker != null) {
			return;
		}
		downloadWorker = new Thread(this::runDownloads, "downloads");
		downloadWorker.setDaemon(true);
		downloadWorker.start();
	}

	private void runDownloads() {
		Downloads.Target target = new Downloads.Target() {
			@NonNull
			@Override
			public OutputStream open(@NonNull DownloadItem item, boolean append) throws IOException {
				File file = downloadedFile(item);
				if (file == null) {
					File dir = Paths.downloads();
					if (!dir.isDirectory() && !dir.mkdirs()) {
						throw new IOException("Cannot create " + dir);
					}
					file = FileNames.unique(dir, item.fileName);
					item.contentUri = file.toURI().toString();
				}
				return new FileOutputStream(file, append);
			}

			@Override
			public void completed(@NonNull DownloadItem item) {
			}

			@Override
			public void progress(@NonNull DownloadItem item) {
			}
		};
		while (true) {
			synchronized (this) {
				if (!downloadsAgain) {
					downloadWorker = null;
					return;
				}
				downloadsAgain = false;
			}
			downloads().drain(http, target);
		}
	}
}
