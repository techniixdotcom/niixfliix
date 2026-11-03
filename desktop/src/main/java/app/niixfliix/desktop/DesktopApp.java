package app.niixfliix.desktop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;

import app.niixfliix.app.AppInfo;
import app.niixfliix.desktop.gtk.App;
import app.niixfliix.desktop.gtk.Gui;
import app.niixfliix.desktop.gtk.MainLoop;
import app.niixfliix.desktop.gtk.Window;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Caches;
import app.niixfliix.desktop.platform.FileStore;
import app.niixfliix.desktop.platform.HardwareGuard;
import app.niixfliix.desktop.platform.Paths;
import app.niixfliix.desktop.platform.SingleInstance;
import app.niixfliix.desktop.ui.Dialogs;
import app.niixfliix.desktop.ui.ImageLoader;
import app.niixfliix.desktop.ui.Links;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Updates;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.util.Logs;

public final class DesktopApp {

	private static final String TAG = "DesktopApp";
	private static final long TORRENT_STOP_MS = 10_000;

	@Nullable private static volatile App running;

	private final App app;
	private final SingleInstance instance;
	private final List<String> startArgs;
	private final boolean hardwareCrashed;
	@Nullable private DesktopGraph graph;
	@Nullable private Window window;
	private boolean quitConfirmed;

	private DesktopApp(@NonNull App app, @NonNull SingleInstance instance, @NonNull String[] args,
			boolean hardwareCrashed) {
		this.app = app;
		this.instance = instance;
		this.startArgs = List.of(args);
		this.hardwareCrashed = hardwareCrashed;
	}

	static int run(@NonNull SingleInstance instance, @NonNull String[] args, boolean hardwareCrashed) {
		App app = App.create(Paths.APP_ID, AppInfo.APP_NAME);
		DesktopApp desktop = new DesktopApp(app, instance, args, hardwareCrashed);
		app.onActivate(desktop::activate);
		running = app;
		int code;
		try {
			code = app.run();
		} finally {
			running = null;
		}
		desktop.shutdown();
		return code;
	}

	static void quitFromOutside() {
		App a = running;
		if (a != null) {
			MainLoop.post(a::quit);
		}
	}

	private void activate() {
		if (window != null) {
			window.present();
			return;
		}
		Gui.setUp(new File(Paths.cache(), "icons"), Paths.APP_ID);
		DesktopGraph g = DesktopGraph.create(MainLoop.executor());
		graph = g;
		I18n.use(g.desktopPrefs.language());
		ImageLoader.init(g.http, Caches.images());
		if (hardwareCrashed) {
			g.desktopPrefs.setHardwareDecoding(false);
		}
		Window w = MainWindow.create(app).window();
		window = w;
		int[] size = g.desktopPrefs.windowSize();
		if (size != null) {
			w.defaultSize(size[0], size[1]);
		}
		if (g.desktopPrefs.windowMaximized()) {
			w.maximize();
		}
		w.onCloseRequest(this::onCloseRequest);
		w.onScaleChanged(() -> ImageLoader.setScale(w.scale()));
		w.present();
		ImageLoader.setScale(w.scale());

		instance.listen(args -> MainLoop.post(() -> onLinks(args)));
		g.addons.refreshOnStart();
		g.account.sync();
		if (g.prefs.updateCheckOnStart()) {
			Updates.checkQuietly();
		}
		if (hardwareCrashed) {
			Dialogs.message(I18n.t("hardware_decoding_crash_title"), I18n.t("hardware_decoding_crash"));
		}
		onLinks(startArgs);
	}

	private boolean onCloseRequest() {
		Window w = window;
		if (w == null) {
			return false;
		}
		if (!quitConfirmed && downloading()) {
			Dialogs.confirm(null, I18n.t("quit_downloads_running"), I18n.t("action_quit"), () -> {
				quitConfirmed = true;
				saveSize(w);
				w.destroy();
			});
			return true;
		}
		saveSize(w);
		return false;
	}

	private void shutdown() {
		DesktopGraph g = graph;
		if (g != null) {
			try {
				g.playback().stop();
				g.downloads().pauseAll();
				g.torrentEngine().shutdown(true, TORRENT_STOP_MS);
			} catch (RuntimeException e) {
				Logs.w(TAG, "Shutting down failed", e);
			}
		}
		FileStore.flushAll();
		Caches.clear();
		HardwareGuard.end();
		instance.close();
	}

	private void onLinks(List<String> args) {
		if (!args.isEmpty() && window != null) {
			window.present();
		}
		for (String arg : args) {
			Links.handle(arg);
		}
	}

	private boolean downloading() {
		DesktopGraph g = graph;
		if (g == null) {
			return false;
		}
		for (DownloadItem item : g.downloads().all()) {
			if (item.isActive()) {
				return true;
			}
		}
		return false;
	}

	private void saveSize(Window w) {
		DesktopGraph g = graph;
		if (g == null) {
			return;
		}
		g.desktopPrefs.setWindowMaximized(w.isMaximized());
		int[] size = w.defaultSize();
		if (size[0] > 0 && size[1] > 0) {
			g.desktopPrefs.setWindowSize(size[0], size[1]);
		}
	}
}
