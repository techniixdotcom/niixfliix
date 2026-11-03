package app.niixfliix.desktop;

import app.niixfliix.app.AppInfo;
import app.niixfliix.desktop.platform.Caches;
import app.niixfliix.desktop.platform.FileStore;
import app.niixfliix.desktop.platform.HardwareGuard;
import app.niixfliix.desktop.platform.NativeCrashes;
import app.niixfliix.desktop.platform.Paths;
import app.niixfliix.desktop.platform.SingleInstance;
import app.niixfliix.util.CrashLog;
import app.niixfliix.util.Logs;

public final class Main {

	private static final long GTK_STACK_BYTES = 32L << 20;
	private static final long SIGNAL_QUIT_MS = 15_000;

	private Main() {
	}

	public static void main(String[] args) throws InterruptedException {
		System.setProperty("java.awt.headless", "true");
		CrashLog.install(Paths.data(), AppInfo.VERSION_NAME,
				"Linux " + System.getProperty("os.version") + (Paths.inFlatpak() ? " (Flatpak)" : ""),
				System.getProperty("os.arch") + ", Java " + Runtime.version());
		Logs.w("Main", "Starting version " + AppInfo.VERSION_NAME + ", Java " + Runtime.version(), null);
		SingleInstance instance = new SingleInstance(Paths.runtime());
		if (!instance.claim(args)) {
			return;
		}
		Caches.clear();
		int crashes = NativeCrashes.collect(Paths.data());
		boolean hardwareCrashed = HardwareGuard.wasDecodingAtExit() && crashes > 0;
		if (hardwareCrashed) {
			Logs.w("Main", "The last run crashed while the graphics card decoded a video", null);
		}
		int[] code = {0};
		Thread gtk = new Thread(null, () -> code[0] = DesktopApp.run(instance, args, hardwareCrashed), "gtk",
				GTK_STACK_BYTES);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (gtk.isAlive()) {
				DesktopApp.quitFromOutside();
				try {
					gtk.join(SIGNAL_QUIT_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			FileStore.flushAll();
			Caches.clear();
			HardwareGuard.end();
		}, "save-on-exit"));
		gtk.start();
		gtk.join();
		System.exit(code[0]);
	}
}
