package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.Quality;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Dialog;
import app.niixfliix.desktop.gtk.DropDown;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.player.PlayerPreferences;
import app.niixfliix.ui.setup.Choices;

public final class QualityDialog {

	private static final int WIDTH = 460;

	private QualityDialog() {
	}

	public static void show(@Nullable Runnable onDone) {
		PlayerPreferences prefs = DesktopGraph.get().playerPrefs;
		DropDown input = picker(prefs.quality(true));
		Box body = Box.column(14, Label.wrapping(I18n.t("quality_hint_desktop")), input);
		Button done = Dialogs.button(I18n.t("action_done"), true);
		Dialog dialog = Dialogs.custom(I18n.t("quality_title"), body, WIDTH, done);
		dialog.defaultWidget(done);
		done.onClick(() -> {
			save(input);
			dialog.close();
			if (onDone != null) {
				onDone.run();
			}
		});
	}

	@NonNull
	public static DropDown picker(@NonNull Quality selected) {
		DropDown input = DropDown.of(labels());
		input.select(Choices.qualityIndex(selected));
		return input;
	}

	@NonNull
	public static List<String> labels() {
		List<String> labels = new ArrayList<>();
		for (Quality q : Choices.QUALITIES) {
			labels.add(Fmt.quality(q));
		}
		return labels;
	}

	public static void save(@NonNull DropDown input) {
		int index = Math.max(0, input.selected());
		DesktopGraph.get().playerPrefs.setQuality(true, Choices.QUALITIES.get(index));
	}
}
