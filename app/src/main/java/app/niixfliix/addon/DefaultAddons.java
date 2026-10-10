package app.niixfliix.addon;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Defaults for a fresh install. If an add-on moves, change it here. */
public final class DefaultAddons {

	public static final String CINEMETA = "https://v3-cinemeta.strem.io/manifest.json";
	public static final String TORRENTIO = "https://torrentio.strem.fun/manifest.json";
	public static final String THEPIRATEBAY = "https://thepiratebay-plus.strem.fun/manifest.json";
	public static final String OPENSUBTITLES = "https://opensubtitles-v3.strem.io/manifest.json";

	public static final String TORRENTIO_HOST = "torrentio.strem.fun";
	public static final String TORRENTIO_ORIGIN = "https://" + TORRENTIO_HOST;
	public static final String TORRENTIO_CONFIGURE_PAGE = TORRENTIO_ORIGIN + "/configure";

	private DefaultAddons() {
	}

	@NonNull
	public static List<String> urls() {
		return Collections.unmodifiableList(Arrays.asList(CINEMETA, TORRENTIO, THEPIRATEBAY, OPENSUBTITLES));
	}
}
