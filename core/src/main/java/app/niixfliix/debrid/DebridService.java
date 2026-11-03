package app.niixfliix.debrid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum DebridService {
	REAL_DEBRID("realdebrid", "https://real-debrid.com/apitoken", "api.real-debrid.com"),
	ALL_DEBRID("alldebrid", "https://alldebrid.com/apikeys", "api.alldebrid.com"),
	PREMIUMIZE("premiumize", "https://www.premiumize.me/account", "www.premiumize.me"),
	DEBRID_LINK("debridlink", "https://debrid-link.com/webapp/apikey", "debrid-link.com"),
	TORBOX("torbox", "https://torbox.app/settings", "api.torbox.app"),
	OFFCLOUD("offcloud", "https://offcloud.com/#/account", "offcloud.com");

	public final String torrentioKey;
	public final String keyPage;
	public final String apiHost;

	DebridService(String torrentioKey, String keyPage, String apiHost) {
		this.torrentioKey = torrentioKey;
		this.keyPage = keyPage;
		this.apiHost = apiHost;
	}

	@Nullable
	public static DebridService fromTorrentioKey(@Nullable String key) {
		for (DebridService s : values()) {
			if (s.torrentioKey.equals(key)) {
				return s;
			}
		}
		return null;
	}

	public static boolean isPlausibleKey(@Nullable String key) {
		return key != null && key.matches("[A-Za-z0-9._-]{6,200}");
	}

	@NonNull
	public static String mask(@Nullable String key) {
		if (key == null || key.isEmpty()) {
			return "";
		}
		if (key.length() <= 4) {
			return "••••";
		}
		return "••••" + key.substring(key.length() - 4);
	}
}
