package app.niixfliix.live;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.niixfliix.ui.common.PosterItem;

public final class SportSites {

	public static final String ROW_KEY = "\u0000sport-sites";
	public static final String ID_PREFIX = "sportsite:";
	public static final String TYPE = "tv";

	public static final class Site {
		@NonNull public final String id;
		@NonNull public final String name;
		@NonNull public final String url;
		public final boolean desktopByDefault;

		Site(@NonNull String id, @NonNull String name, @NonNull String url, boolean desktopByDefault) {
			this.id = id;
			this.name = name;
			this.url = url;
			this.desktopByDefault = desktopByDefault;
		}
	}

	public static final Site BLUE_SPORT = new Site("blue-sport", "blue Sport", "https://tv.blue.ch/home", true);
	public static final Site SRF_SPORT = new Site("srf-sport", "SRF Sport",
			"https://www.srf.ch/play/tv/sport-livestreams", false);
	public static final List<Site> ALL = Collections.unmodifiableList(Arrays.asList(BLUE_SPORT, SRF_SPORT));

	private static final String[] TRUSTED_DOMAINS = {"blue.ch", "blueplus.ch", "bluewin.ch", "swisscom.ch",
			"srf.ch", "srgssr.ch"};

	private SportSites() {
	}

	@NonNull
	public static List<PosterItem> posters() {
		List<PosterItem> out = new ArrayList<>(ALL.size());
		for (Site site : ALL) {
			out.add(new PosterItem(ID_PREFIX + site.id, TYPE, site.name, null, -1));
		}
		return out;
	}

	@Nullable
	public static Site find(@Nullable String id) {
		if (id == null) {
			return null;
		}
		String bare = id.startsWith(ID_PREFIX) ? id.substring(ID_PREFIX.length()) : id;
		for (Site site : ALL) {
			if (site.id.equals(bare)) {
				return site;
			}
		}
		return null;
	}

	public static boolean isSite(@Nullable String id) {
		return id != null && id.startsWith(ID_PREFIX) && find(id) != null;
	}

	@Nullable
	public static String httpsHost(@Nullable String url) {
		if (url == null || url.length() > 8192) {
			return null;
		}
		try {
			URI uri = new URI(url.trim());
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null) {
				return null;
			}
			String host = uri.getHost();
			if (host == null || host.isEmpty()) {
				return null;
			}
			host = host.toLowerCase(Locale.ROOT);
			return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
		} catch (URISyntaxException e) {
			return null;
		}
	}

	public static boolean isTrusted(@Nullable String url) {
		String host = httpsHost(url);
		if (host == null) {
			return false;
		}
		for (String domain : TRUSTED_DOMAINS) {
			if (host.equals(domain) || host.endsWith("." + domain)) {
				return true;
			}
		}
		return false;
	}
}
