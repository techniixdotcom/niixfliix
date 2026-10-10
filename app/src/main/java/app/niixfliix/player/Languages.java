package app.niixfliix.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** Language checks on a video's tracks. "en", "eng" and "en-US" are all English here. */
@OptIn(markerClass = UnstableApi.class)
final class Languages {

	private Languages() {
	}

	static boolean matches(@Nullable String code, @NonNull String wanted) {
		if (code == null) {
			return false;
		}
		String normalized = Util.normalizeLanguageCode(code);
		int dash = normalized.indexOf('-');
		return (dash > 0 ? normalized.substring(0, dash) : normalized).equals(wanted);
	}

	static boolean hasAudio(@NonNull Tracks tracks, @NonNull String language) {
		return has(tracks, C.TRACK_TYPE_AUDIO, language);
	}

	static boolean hasText(@NonNull Tracks tracks, @NonNull String language) {
		return has(tracks, C.TRACK_TYPE_TEXT, language);
	}

	/** lots of releases don't tag their only audio track with a language */
	static boolean audioUntagged(@NonNull Tracks tracks) {
		for (Tracks.Group group : tracks.getGroups()) {
			if (group.getType() != C.TRACK_TYPE_AUDIO) {
				continue;
			}
			for (int i = 0; i < group.length; i++) {
				String language = group.getTrackFormat(i).language;
				if (language != null && !language.equals(C.LANGUAGE_UNDETERMINED)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean has(Tracks tracks, int type, String language) {
		for (Tracks.Group group : tracks.getGroups()) {
			if (group.getType() != type) {
				continue;
			}
			for (int i = 0; i < group.length; i++) {
				if (group.isTrackSupported(i) && matches(group.getTrackFormat(i).language, language)) {
					return true;
				}
			}
		}
		return false;
	}
}
