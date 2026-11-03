package app.niixfliix.addon;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.niixfliix.live.FreeSports;

public final class DefaultAddons {

	public static final String CINEMETA = "https://v3-cinemeta.strem.io/manifest.json";
	public static final String TORRENTIO = "https://torrentio.strem.fun/manifest.json";
	public static final String THEPIRATEBAY = "https://thepiratebay-plus.strem.fun/manifest.json";
	public static final String OPENSUBTITLES = "https://opensubtitles-v3.strem.io/manifest.json";
	public static final String FREE_SPORTS = FreeSports.ADDON_URL;

	public static final String TORRENTIO_HOST = "torrentio.strem.fun";
	public static final String TORRENTIO_ORIGIN = "https://" + TORRENTIO_HOST;
	public static final String TORRENTIO_CONFIGURE_PAGE = TORRENTIO_ORIGIN + "/configure";

	public static final String HYPER = "https://hyper.stravo.site/local:org/manifest.json";

	private DefaultAddons() {
	}

	@NonNull
	public static List<String> urls() {
		return Collections.unmodifiableList(Arrays.asList(CINEMETA, TORRENTIO, THEPIRATEBAY, OPENSUBTITLES,
				FREE_SPORTS));
	}

	public static boolean isQuietWhenDown(@NonNull String manifestUrl) {
		String url = StremioUrl.toManifestUrl(manifestUrl);
		return THEPIRATEBAY.equals(url) || FREE_SPORTS.equals(url);
	}

	@NonNull
	public static Map<String, String> retired() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put(HYPER, THEPIRATEBAY);
		return Collections.unmodifiableMap(m);
	}
}
