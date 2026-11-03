package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import app.niixfliix.util.Logs;

public final class Styles {

	private static final String TAG = "Styles";
	private static final int COLOR_SCHEME_FORCE_DARK = 4;
	private static final int PRIORITY_APPLICATION = 600;

	private static final Fn STYLE_MANAGER = Fn.of(Lib.ADW, "adw_style_manager_get_default", PTR);
	private static final Fn COLOR_SCHEME = Fn.of(Lib.ADW, "adw_style_manager_set_color_scheme", null, PTR, INT);
	private static final Fn PROVIDER_NEW = Fn.of(Lib.GTK, "gtk_css_provider_new", PTR);
	private static final Fn LOAD = Fn.of(Lib.GTK, "gtk_css_provider_load_from_string", null, PTR, PTR);
	private static final Fn ADD_PROVIDER = Fn.of(Lib.GTK, "gtk_style_context_add_provider_for_display", null,
			PTR, PTR, INT);
	private static final Fn DISPLAY = Fn.of(Lib.GTK, "gdk_display_get_default", PTR);
	private static final Fn GTK_MINOR = Fn.of(Lib.GTK, "gtk_get_minor_version", INT);
	private static final Fn ADW_MINOR = Fn.of(Lib.ADW, "adw_get_minor_version", INT);

	private Styles() {
	}

	static void apply(@NonNull String colorsResource, @NonNull String variablesResource,
			@NonNull String stylesResource) {
		COLOR_SCHEME.v(STYLE_MANAGER.p(), COLOR_SCHEME_FORCE_DARK);
		StringBuilder css = new StringBuilder(read(colorsResource));
		if (adwMinor() >= 6) {
			css.append('\n').append(read(variablesResource));
		}
		css.append('\n').append(read(stylesResource));
		var provider = PROVIDER_NEW.p();
		LOAD.v(provider, css.toString());
		ADD_PROVIDER.v(DISPLAY.p(), provider, PRIORITY_APPLICATION);
	}

	public static int gtkMinor() {
		return GTK_MINOR.i();
	}

	public static int adwMinor() {
		return ADW_MINOR.i();
	}

	private static String read(String resource) {
		try (InputStream in = Styles.class.getResourceAsStream(resource)) {
			if (in == null) {
				Logs.w(TAG, resource + " is missing from the build", null);
				return "";
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Logs.w(TAG, "Could not read " + resource, e);
			return "";
		}
	}
}
