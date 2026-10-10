package app.niixfliix.data;

import androidx.annotation.NonNull;

public final class Prefs {

	public static final int DEFAULT_CACHE_MB = 2048;

	private static final String SETUP_DONE = "setup_done";
	private static final String HIDE_ADULT = "hide_adult";
	private static final String TORRENT_WIFI_ONLY = "torrent_wifi_only";
	private static final String TORRENT_CACHE_MB = "torrent_cache_mb";
	private static final String TORRENT_CLEAR_ON_EXIT = "torrent_clear_on_exit";
	private static final String UPDATE_CHECK_ON_START = "update_check_on_start";

	private final KeyValueStore store;

	public Prefs(@NonNull KeyValueStore store) {
		this.store = store;
	}

	public boolean isSetupDone() {
		return store.getBoolean(SETUP_DONE, false);
	}

	public void setSetupDone() {
		store.putBoolean(SETUP_DONE, true);
	}

	public boolean hideAdult() {
		return store.getBoolean(HIDE_ADULT, true);
	}

	public void setHideAdult(boolean hide) {
		store.putBoolean(HIDE_ADULT, hide);
	}

	public boolean torrentWifiOnly() {
		return store.getBoolean(TORRENT_WIFI_ONLY, false);
	}

	public void setTorrentWifiOnly(boolean wifiOnly) {
		store.putBoolean(TORRENT_WIFI_ONLY, wifiOnly);
	}

	public int torrentCacheMb() {
		return store.getInt(TORRENT_CACHE_MB, DEFAULT_CACHE_MB);
	}

	public void setTorrentCacheMb(int mb) {
		store.putInt(TORRENT_CACHE_MB, mb);
	}

	public boolean torrentClearOnExit() {
		return store.getBoolean(TORRENT_CLEAR_ON_EXIT, true);
	}

	public void setTorrentClearOnExit(boolean clear) {
		store.putBoolean(TORRENT_CLEAR_ON_EXIT, clear);
	}

	public boolean updateCheckOnStart() {
		return store.getBoolean(UPDATE_CHECK_ON_START, false);
	}

	public void setUpdateCheckOnStart(boolean check) {
		store.putBoolean(UPDATE_CHECK_ON_START, check);
	}
}
