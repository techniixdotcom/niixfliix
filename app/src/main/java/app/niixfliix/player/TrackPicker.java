package app.niixfliix.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Tracks;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.ui.Formats;

final class TrackPicker {

	static final class AddonSubtitle {
		@NonNull final String id;
		@NonNull final String url;
		@Nullable final String language;
		@NonNull final String addonName;

		AddonSubtitle(@NonNull String id, @NonNull String url, @Nullable String language, @NonNull String addonName) {
			this.id = id;
			this.url = url;
			this.language = language;
			this.addonName = addonName;
		}

		@NonNull
		PlayerEngine.SideSubtitle toSide() {
			String label = language != null ? Formats.language(language) : addonName;
			return new PlayerEngine.SideSubtitle("addon-" + id, Uri.parse(url), language, label,
					PlayerEngine.subtitleMimeType(url));
		}
	}

	interface SubtitleChoice {
		void off();

		void track(@NonNull Tracks.Group group, int index);

		void addon(@NonNull AddonSubtitle subtitle);

		void localFile();
	}

	interface AudioChoice {
		void track(@NonNull Tracks.Group group, int index);
	}

	private TrackPicker() {
	}

	static void subtitles(@NonNull Context context, @NonNull Tracks tracks, boolean enabled,
			@NonNull List<AddonSubtitle> fromAddons, @NonNull SubtitleChoice choice) {
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		int checked = 0;
		labels.add(context.getString(R.string.player_subtitles_off));
		actions.add(choice::off);
		for (Tracks.Group group : tracks.getGroups()) {
			if (group.getType() != C.TRACK_TYPE_TEXT) {
				continue;
			}
			for (int i = 0; i < group.length; i++) {
				if (!group.isTrackSupported(i)) {
					continue;
				}
				if (enabled && group.isTrackSelected(i)) {
					checked = labels.size();
				}
				labels.add(label(context, group.getTrackFormat(i), labels.size()));
				int index = i;
				actions.add(() -> choice.track(group, index));
			}
		}
		for (AddonSubtitle s : fromAddons) {
			String language = s.language != null ? Formats.language(s.language) : "";
			labels.add(context.getString(R.string.player_subtitle_from_addon,
					language.isEmpty() ? context.getString(R.string.player_track_unknown) : language, s.addonName));
			actions.add(() -> choice.addon(s));
		}
		labels.add(context.getString(R.string.player_subtitles_from_file));
		actions.add(choice::localFile);
		show(context, R.string.player_subtitles, labels, checked, actions);
	}

	static void audio(@NonNull Context context, @NonNull Tracks tracks, @NonNull AudioChoice choice) {
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		int checked = -1;
		for (Tracks.Group group : tracks.getGroups()) {
			if (group.getType() != C.TRACK_TYPE_AUDIO) {
				continue;
			}
			for (int i = 0; i < group.length; i++) {
				if (!group.isTrackSupported(i)) {
					continue;
				}
				if (group.isTrackSelected(i)) {
					checked = labels.size();
				}
				Format format = group.getTrackFormat(i);
				String label = label(context, format, labels.size() + 1);
				if (format.channelCount >= 6) {
					label = context.getString(R.string.player_audio_surround, label, channels(format.channelCount));
				}
				labels.add(label);
				int index = i;
				actions.add(() -> choice.track(group, index));
			}
		}
		if (labels.isEmpty()) {
			labels.add(context.getString(R.string.player_no_audio_tracks));
			actions.add(() -> { });
		}
		show(context, R.string.player_audio, labels, checked, actions);
	}

	private static String label(Context context, Format format, int number) {
		String language = format.language != null ? Formats.language(format.language) : "";
		if (format.label != null && !format.label.isEmpty()) {
			return language.isEmpty() || format.label.contains(language)
					? format.label
					: context.getString(R.string.player_track_label, language, format.label);
		}
		return language.isEmpty() ? context.getString(R.string.player_track_number, number) : language;
	}

	private static String channels(int count) {
		switch (count) {
			case 6:
				return "5.1";
			case 7:
				return "6.1";
			case 8:
				return "7.1";
			default:
				return count + "ch";
		}
	}

	private static void show(Context context, int title, List<String> labels, int checked, List<Runnable> actions) {
		new MaterialAlertDialogBuilder(context)
				.setTitle(title)
				.setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
					dialog.dismiss();
					actions.get(which).run();
				})
				.show();
	}
}
