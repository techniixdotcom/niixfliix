package app.niixfliix.ui.setup;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import app.niixfliix.R;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.Prefs;
import app.niixfliix.player.PlayerPreferences;
import app.niixfliix.torrentio.TorrentioConfig;
import app.niixfliix.ui.common.Dropdown;

/** First-start popup, Done works with all defaults. */
public final class SetupDialog extends DialogFragment {

	private static final String TAG = "setup";

	private final AppGraph graph = AppGraph.get();
	private Dropdown qualityWifi;
	private Dropdown qualityMobile;
	private Dropdown language;

	public static void showIfNeeded(@NonNull FragmentManager fm) {
		if (!AppGraph.get().prefs.isSetupDone()) {
			show(fm);
		}
	}

	public static void show(@NonNull FragmentManager fm) {
		if (fm.findFragmentByTag(TAG) == null) {
			new SetupDialog().show(fm, TAG);
		}
	}

	@NonNull
	@Override
	@SuppressLint("InflateParams")
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		View view = getLayoutInflater().inflate(R.layout.dialog_setup, null);
		Prefs prefs = graph.prefs;
		PlayerPreferences playerPrefs = graph.playerPrefs;

		InstalledAddon torrentio = graph.addons.torrentio();
		TorrentioConfig config = TorrentioConfig.parse(torrentio != null ? torrentio.transportUrl : null);
		DebridFields debrid = new DebridFields(view, config.debrid, config.hasDebrid() ? config.debridKey : null, false);

		qualityWifi = new Dropdown(view.findViewById(R.id.quality_wifi), Choices.qualityLabels(),
				Choices.qualityIndex(playerPrefs.quality(true)));
		qualityMobile = new Dropdown(view.findViewById(R.id.quality_mobile), Choices.qualityLabels(),
				Choices.qualityIndex(playerPrefs.quality(false)));
		language = new Dropdown(view.findViewById(R.id.language), Choices.languageLabels(),
				Choices.languageIndex(playerPrefs.language()));
		MaterialSwitch hideAdult = view.findViewById(R.id.hide_adult);
		hideAdult.setChecked(prefs.hideAdult());

		AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.setup_title)
				.setView(view)
				.setPositiveButton(R.string.action_done, null)
				.setNeutralButton(R.string.setup_use_account, null)
				.create();
		dialog.setCanceledOnTouchOutside(false);
		setCancelable(false);
		// set after show() so a bad key or the sign-in button keep the dialog open
		dialog.setOnShowListener(d -> {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
				if (save(debrid, config, hideAdult.isChecked())) {
					dismiss();
				}
			});
			View signIn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
			signIn.setVisibility(graph.account.isSignedIn() ? View.GONE : View.VISIBLE);
			signIn.setOnClickListener(v -> SignInDialog.show(getParentFragmentManager()));
			getParentFragmentManager().setFragmentResultListener(SignInDialog.RESULT_KEY, this,
					(key, result) -> signIn.setVisibility(View.GONE));
		});
		return dialog;
	}

	/** a debrid key is optional: without one, torrents play on the phone */
	private boolean save(DebridFields debrid, TorrentioConfig config, boolean hideAdult) {
		if (!debrid.applyTo(config)) {
			return false;
		}
		if (config.hasDebrid()) {
			graph.addons.setTorrentioUrl(config.toManifestUrl());
		}
		Prefs prefs = graph.prefs;
		PlayerPreferences playerPrefs = graph.playerPrefs;
		playerPrefs.setQuality(true, Choices.QUALITIES.get(qualityWifi.selected()));
		playerPrefs.setQuality(false, Choices.QUALITIES.get(qualityMobile.selected()));
		playerPrefs.setLanguage(Choices.LANGUAGES.get(language.selected()));
		if (prefs.hideAdult() != hideAdult) {
			prefs.setHideAdult(hideAdult);
			graph.addons.filtersChanged();
		}
		prefs.setSetupDone();
		return true;
	}
}
