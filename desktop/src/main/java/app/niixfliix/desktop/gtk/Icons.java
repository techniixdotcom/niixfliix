package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import app.niixfliix.util.Logs;

public final class Icons {

	public static final String CHECK = "check";

	private static final String TAG = "Icons";
	private static final String PREFIX = "niixfliix-";
	private static final String SUFFIX = "-symbolic";
	private static final String RESOURCES = "/app/niixfliix/desktop/icons/";
	private static final String INDEX = "[Icon Theme]\nName=Hicolor\nComment=Fallback icon theme\nHidden=true\n"
			+ "Directories=scalable/actions,scalable/apps\n\n"
			+ "[scalable/actions]\nSize=16\nMinSize=8\nMaxSize=512\nContext=Actions\nType=Scalable\n\n"
			+ "[scalable/apps]\nSize=128\nMinSize=8\nMaxSize=512\nContext=Applications\nType=Scalable\n";

	private static final Fn DISPLAY = Fn.of(Lib.GTK, "gdk_display_get_default", PTR);
	private static final Fn THEME = Fn.of(Lib.GTK, "gtk_icon_theme_get_for_display", PTR, PTR);
	private static final Fn ADD_PATH = Fn.of(Lib.GTK, "gtk_icon_theme_add_search_path", null, PTR, PTR);

	private Icons() {
	}

	@NonNull
	public static String name(@NonNull String icon) {
		return PREFIX + icon + SUFFIX;
	}

	static void install(@NonNull File dir, @NonNull String appId) {
		File theme = new File(dir, "hicolor");
		File actions = new File(theme, "scalable/actions");
		File apps = new File(theme, "scalable/apps");
		try {
			Files.createDirectories(actions.toPath());
			Files.createDirectories(apps.toPath());
			Files.writeString(new File(theme, "index.theme").toPath(), INDEX, StandardCharsets.UTF_8);
			String index = read(RESOURCES + "index.txt");
			for (String line : index.split("\n")) {
				String icon = line.trim();
				if (!icon.isEmpty()) {
					copy(RESOURCES + icon + ".svg", new File(actions, name(icon) + ".svg"));
				}
			}
			copy("/app/niixfliix/desktop/app-icon.svg", new File(apps, appId + ".svg"));
		} catch (IOException e) {
			Logs.w(TAG, "Could not write the icons", e);
		}
		ADD_PATH.v(THEME.p(DISPLAY.p()), dir.getAbsolutePath());
	}

	private static String read(String resource) throws IOException {
		try (InputStream in = Icons.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IOException(resource + " is missing from the build");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void copy(String resource, File target) throws IOException {
		try (InputStream in = Icons.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IOException(resource + " is missing from the build");
			}
			Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
