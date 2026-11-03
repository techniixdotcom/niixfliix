package app.niixfliix.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Prefs {

	public static final boolean CLEAR_TORRENTS_AFTER_PLAYBACK = true;

	private static final String HIDE_ADULT = "hide_adult";
	private static final String DOWNLOADS_FOLDER = "downloads_folder";
	private static final String UPDATE_CHECK_ON_START = "update_check_on_start";

	private final KeyValueStore store;

	public Prefs(@NonNull KeyValueStore store) {
		this.store = store;
	}

	public boolean hideAdult() {
		return store.getBoolean(HIDE_ADULT, false);
	}

	public void setHideAdult(boolean hide) {
		store.putBoolean(HIDE_ADULT, hide);
	}

	@Nullable
	public String downloadsFolder() {
		String folder = store.getString(DOWNLOADS_FOLDER, null);
		return folder != null && !folder.isEmpty() ? folder : null;
	}

	public void setDownloadsFolder(@Nullable String folder) {
		if (folder == null || folder.isEmpty()) {
			store.remove(DOWNLOADS_FOLDER);
		} else {
			store.putString(DOWNLOADS_FOLDER, folder);
		}
	}

	public boolean updateCheckOnStart() {
		return store.getBoolean(UPDATE_CHECK_ON_START, false);
	}

	public void setUpdateCheckOnStart(boolean check) {
		store.putBoolean(UPDATE_CHECK_ON_START, check);
	}
}
