package app.niixfliix.desktop.gtk;

import androidx.annotation.NonNull;

import java.io.File;

public final class Gui {

	private static final String RESOURCES = "/app/niixfliix/desktop/";

	private Gui() {
	}

	public static void setUp(@NonNull File iconDir, @NonNull String appId) {
		Styles.apply(RESOURCES + "colors.css", RESOURCES + "colors-variables.css", RESOURCES + "theme.css");
		Icons.install(iconDir, appId);
	}
}
