package app.niixfliix.ui.setup;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import app.niixfliix.R;
import app.niixfliix.app.AppGraph;
import app.niixfliix.player.PlayerPreferences;
import app.niixfliix.ui.common.Dropdown;

/** Asks once which quality auto-play should use on Wi-Fi and on mobile data. The streams sheet reopens it too. */
public final class QualityDialog extends DialogFragment {

	public static final String RESULT_KEY = "quality_chosen";
	private static final String TAG = "quality";

	private Dropdown wifi;
	private Dropdown mobile;

	public static void show(@NonNull FragmentManager fm) {
		if (fm.findFragmentByTag(TAG) == null) {
			new QualityDialog().show(fm, TAG);
		}
	}

	@NonNull
	@Override
	@SuppressLint("InflateParams")
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		View view = getLayoutInflater().inflate(R.layout.dialog_quality, null);
		PlayerPreferences prefs = AppGraph.get().playerPrefs;
		wifi = new Dropdown(view.findViewById(R.id.quality_wifi), Choices.qualityLabels(),
				Choices.qualityIndex(prefs.quality(true)));
		mobile = new Dropdown(view.findViewById(R.id.quality_mobile), Choices.qualityLabels(),
				Choices.qualityIndex(prefs.quality(false)));
		setCancelable(prefs.qualityChosen());
		return new MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.quality_title)
				.setView(view)
				.setPositiveButton(R.string.action_done, (d, w) -> save(prefs))
				.create();
	}

	private void save(PlayerPreferences prefs) {
		prefs.setQuality(true, Choices.QUALITIES.get(wifi.selected()));
		prefs.setQuality(false, Choices.QUALITIES.get(mobile.selected()));
		getParentFragmentManager().setFragmentResult(RESULT_KEY, new Bundle());
	}
}
