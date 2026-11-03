package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import app.niixfliix.app.AppInfo;
import app.niixfliix.util.Logs;

public final class Paths {

	public static final String APP_ID = "app.niixfliix.Niixfliix";
	private static final String TAG = "Paths";
	@Nullable private static volatile File pickedDownloads;

	private Paths() {
	}

	@NonNull
	public static File data() {
		return privateDir(xdg("XDG_DATA_HOME", ".local/share"));
	}

	@NonNull
	public static File cache() {
		return privateDir(xdg("XDG_CACHE_HOME", ".cache"));
	}

	@NonNull
	public static File runtime() {
		String runtime = System.getenv("XDG_RUNTIME_DIR");
		if (runtime != null && !runtime.isEmpty() && new File(runtime).isDirectory()) {
			File app = new File(runtime, "app/" + APP_ID);
			return app.isDirectory() ? app : privateDir(new File(runtime));
		}
		return cache();
	}

	@NonNull
	public static File downloads() {
		File picked = pickedDownloads;
		return picked != null && picked.isDirectory() && picked.canWrite() ? picked : defaultDownloads();
	}

	@NonNull
	public static File defaultDownloads() {
		File base = userDir("XDG_DOWNLOAD_DIR");
		return new File(base != null ? base : new File(home(), "Downloads"), AppInfo.APP_NAME);
	}

	public static void setPickedDownloads(@Nullable String path) {
		pickedDownloads = path != null && path.startsWith("/") ? new File(path) : null;
	}

	@NonNull
	public static File home() {
		return new File(System.getProperty("user.home"));
	}

	public static boolean inFlatpak() {
		return new File("/.flatpak-info").isFile();
	}

	private static File xdg(String variable, String fallback) {
		String value = System.getenv(variable);
		File base = value != null && value.startsWith("/") ? new File(value) : new File(home(), fallback);
		return inFlatpak() ? base : new File(base, AppInfo.APP_SLUG);
	}

	private static File privateDir(File dir) {
		if (!dir.isDirectory()) {
			try {
				Files.createDirectories(dir.toPath(), PosixFilePermissions.asFileAttribute(
						PosixFilePermissions.fromString("rwx------")));
			} catch (IOException | UnsupportedOperationException e) {
				Logs.w(TAG, "Could not create " + dir, e);
			}
		}
		return dir;
	}

	@Nullable
	private static File userDir(String key) {
		String config = System.getenv("XDG_CONFIG_HOME");
		File[] candidates = {
				config != null && config.startsWith("/") ? new File(config, "user-dirs.dirs") : null,
				new File(home(), ".config/user-dirs.dirs")};
		for (File file : candidates) {
			if (file == null || !file.isFile()) {
				continue;
			}
			try {
				List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
				File found = parseUserDir(lines, key);
				if (found != null) {
					return found;
				}
			} catch (IOException e) {
				Logs.w(TAG, "Could not read " + file, e);
			}
		}
		return null;
	}

	@Nullable
	static File parseUserDir(@NonNull List<String> lines, @NonNull String key) {
		for (String line : lines) {
			String trimmed = line.trim();
			if (!trimmed.startsWith(key + "=")) {
				continue;
			}
			String value = trimmed.substring(key.length() + 1).trim();
			if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
				value = value.substring(1, value.length() - 1);
			}
			if (value.startsWith("$HOME")) {
				value = home().getPath() + value.substring("$HOME".length());
			}
			if (value.startsWith("/") && !new File(value).equals(home())) {
				return new File(value);
			}
		}
		return null;
	}
}
