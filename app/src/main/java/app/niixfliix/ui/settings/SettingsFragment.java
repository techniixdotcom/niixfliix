package app.niixfliix.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import app.niixfliix.BuildConfig;
import app.niixfliix.R;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.Prefs;
import app.niixfliix.download.DownloadFolder;
import app.niixfliix.player.PlayerPreferences;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.Links;
import app.niixfliix.ui.setup.Choices;
import app.niixfliix.ui.setup.SignInDialog;
import app.niixfliix.update.UpdatePrompt;
import app.niixfliix.util.CrashLog;
import app.niixfliix.util.Texts;

public final class SettingsFragment extends Fragment {

	private static final String[] APP_LANGUAGES = {"", "en", "es", "fr", "ja", "ko", "ru", "tr", "zh-CN", "zh-TW"};

	private final AppGraph graph = AppGraph.get();
	private LinearLayout list;
	private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
			new ActivityResultContracts.OpenDocumentTree(), tree -> {
				if (tree == null || getView() == null) {
					return;
				}
				if (DownloadFolder.pick(requireContext(), graph.prefs, tree)) {
					build();
				} else {
					toast(R.string.settings_downloads_not_writable);
				}
			});

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_settings, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		getChildFragmentManager().setFragmentResultListener(SignInDialog.RESULT_KEY, getViewLifecycleOwner(),
				(key, result) -> build());
		list = view.findViewById(R.id.settings_list);
		build();
	}

	@Override
	public void onResume() {
		super.onResume();
		build();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) {
			build();
		}
	}

	private void build() {
		if (list == null) {
			return;
		}
		list.removeAllViews();
		Prefs prefs = graph.prefs;
		PlayerPreferences player = graph.playerPrefs;

		header(R.string.account_section);
		if (graph.account.isSignedIn()) {
			String email = graph.account.email();
			row(R.string.account_sign_out, email != null ? getString(R.string.account_signed_in, email) : null,
					v -> confirmSignOut());
		} else {
			row(R.string.account_sign_in, getString(R.string.account_sign_in_summary),
					v -> SignInDialog.show(getChildFragmentManager()));
		}

		header(R.string.settings_playback);
		row(R.string.settings_quality_wifi, qualityTitle(player, true), v -> choose(R.string.settings_quality_wifi,
				Formats.qualityLabels(requireContext()), Choices.qualityIndex(player.quality(true)),
				i -> player.setQuality(true, Choices.QUALITIES.get(i))));
		row(R.string.settings_quality_mobile, qualityTitle(player, false), v -> choose(R.string.settings_quality_mobile,
				Formats.qualityLabels(requireContext()), Choices.qualityIndex(player.quality(false)),
				i -> player.setQuality(false, Choices.QUALITIES.get(i))));
		row(R.string.setup_language, Choices.languageName(player.language()), v -> choose(R.string.setup_language,
				Choices.languageLabels(), Choices.languageIndex(player.language()),
				i -> player.setLanguage(Choices.LANGUAGES.get(i))));

		header(R.string.settings_subtitles);
		row(R.string.settings_subtitle_size, getString(R.string.percent, player.subtitleSize()), v -> chooseSubtitleSize());
		String[] colors = getResources().getStringArray(R.array.subtitle_colors);
		row(R.string.settings_subtitle_color, colors[player.subtitleColor().ordinal()], v -> choose(
				R.string.settings_subtitle_color, colors, player.subtitleColor().ordinal(),
				i -> player.setSubtitleColor(PlayerPreferences.SubtitleColor.values()[i])));
		String[] backgrounds = getResources().getStringArray(R.array.subtitle_backgrounds);
		row(R.string.settings_subtitle_background, backgrounds[player.subtitleBackground().ordinal()], v -> choose(
				R.string.settings_subtitle_background, backgrounds, player.subtitleBackground().ordinal(),
				i -> player.setSubtitleBackground(PlayerPreferences.SubtitleBackground.values()[i])));

		header(R.string.settings_app);
		row(R.string.settings_language, currentLanguageLabel(), v -> chooseLanguage());
		toggle(R.string.setup_hide_adult, 0, prefs.hideAdult(), hide -> {
			prefs.setHideAdult(hide);
			graph.addons.filtersChanged();
		});
		row(R.string.settings_check_updates, null, v -> UpdatePrompt.check(requireActivity()));
		toggle(R.string.settings_check_on_start, R.string.settings_check_on_start_summary, prefs.updateCheckOnStart(),
				prefs::setUpdateCheckOnStart);

		header(R.string.settings_storage);
		row(R.string.settings_downloads_folder, DownloadFolder.label(requireContext(), prefs),
				v -> chooseDownloadsFolder());
		row(R.string.settings_clear_history, null, v -> confirm(R.string.settings_clear_history_confirm, () -> {
			graph.history.clear();
			toast(R.string.settings_cleared);
		}));
		row(R.string.settings_clear_searches, null, v -> {
			graph.searchHistory.clear();
			toast(R.string.settings_cleared);
		});

		header(R.string.settings_about);
		String source = "https://github.com/" + BuildConfig.GITHUB_REPO;
		row(R.string.settings_source_code, source, v -> Links.open(requireContext(), source));
		TextView crashSummary = row(R.string.settings_crash_log, getString(R.string.settings_crash_log_none),
				v -> showCrashLog());
		showCrashSummary(crashSummary);
		row(R.string.settings_version_title, BuildConfig.VERSION_NAME,
				v -> info(R.string.settings_licences, R.string.licences_text));
	}

	private void showCrashSummary(@NonNull TextView summary) {
		graph.io.execute(() -> {
			long when = CrashLog.lastCrashMillis();
			graph.main.post(() -> {
				if (getView() != null && summary.isAttachedToWindow() && when > 0) {
					summary.setText(getString(R.string.settings_crash_log_summary, DateUtils.formatDateTime(
							requireContext(), when, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME)));
				}
			});
		});
	}

	private void showCrashLog() {
		graph.io.execute(() -> {
			String log = CrashLog.read();
			graph.main.post(() -> {
				if (getView() == null) {
					return;
				}
				if (log == null) {
					toast(R.string.settings_crash_log_none);
					return;
				}
				AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
						.setTitle(R.string.settings_crash_log)
						.setMessage(log)
						.setPositiveButton(R.string.action_copy, (d, w) -> copyCrashLog(log))
						.setNeutralButton(R.string.action_delete, (d, w) -> graph.io.execute(() -> {
							CrashLog.clear();
							graph.main.post(() -> {
								if (getView() != null) {
									toast(R.string.settings_crash_log_deleted);
									build();
								}
							});
						}))
						.setNegativeButton(R.string.action_close, null)
						.show();
				TextView message = dialog.findViewById(android.R.id.message);
				if (message != null) {
					message.setTextIsSelectable(true);
					message.setTypeface(Typeface.MONOSPACE);
					message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
				}
			});
		});
	}

	private void copyCrashLog(@NonNull String log) {
		ClipboardManager clipboard = requireContext().getSystemService(ClipboardManager.class);
		if (clipboard == null) {
			return;
		}
		clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.settings_crash_log), log));
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			toast(R.string.settings_crash_log_copied);
		}
	}

	private void header(@StringRes int title) {
		TextView view = (TextView) getLayoutInflater().inflate(R.layout.item_setting_header, list, false);
		view.setText(title);
		list.addView(view);
	}

	@NonNull
	private TextView row(@StringRes int title, @Nullable String summary, @NonNull View.OnClickListener onClick) {
		View view = getLayoutInflater().inflate(R.layout.item_setting, list, false);
		((TextView) view.findViewById(R.id.setting_title)).setText(title);
		TextView summaryView = view.findViewById(R.id.setting_summary);
		summaryView.setText(summary);
		summaryView.setVisibility(summary == null || summary.isEmpty() ? View.GONE : View.VISIBLE);
		view.setOnClickListener(onClick);
		list.addView(view);
		return summaryView;
	}

	private void toggle(@StringRes int title, @StringRes int summary, boolean on, @NonNull Consumer<Boolean> onToggle) {
		View view = getLayoutInflater().inflate(R.layout.item_setting, list, false);
		((TextView) view.findViewById(R.id.setting_title)).setText(title);
		TextView summaryView = view.findViewById(R.id.setting_summary);
		if (summary != 0) {
			summaryView.setText(summary);
		} else {
			summaryView.setVisibility(View.GONE);
		}
		MaterialSwitch sw = view.findViewById(R.id.setting_switch);
		sw.setVisibility(View.VISIBLE);
		sw.setChecked(on);
		view.setOnClickListener(v -> {
			sw.setChecked(!sw.isChecked());
			onToggle.accept(sw.isChecked());
		});
		list.addView(view);
	}

	private void choose(@StringRes int title, @NonNull String[] labels, int checked, @NonNull IntConsumer choice) {
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(title)
				.setSingleChoiceItems(labels, checked, (dialog, which) -> {
					dialog.dismiss();
					choice.accept(which);
					build();
				})
				.show();
	}

	private void confirm(@StringRes int message, @NonNull Runnable action) {
		new MaterialAlertDialogBuilder(requireContext())
				.setMessage(message)
				.setPositiveButton(R.string.action_ok, (d, w) -> action.run())
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	private void info(@StringRes int title, @StringRes int text) {
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(title)
				.setMessage(text)
				.setPositiveButton(R.string.action_ok, null)
				.show();
	}

	private void confirmSignOut() {
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.account_sign_out)
				.setMessage(R.string.account_sign_out_confirm)
				.setPositiveButton(R.string.account_sign_out, (d, w) -> {
					graph.account.signOut();
					build();
				})
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	private void toast(@StringRes int text) {
		Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show();
	}

	@NonNull
	private String qualityTitle(@NonNull PlayerPreferences player, boolean wifi) {
		return Formats.quality(requireContext(), Choices.QUALITIES.get(Choices.qualityIndex(player.quality(wifi))));
	}

	private void chooseSubtitleSize() {
		PlayerPreferences player = graph.playerPrefs;
		int[] sizes = PlayerPreferences.SUBTITLE_SIZES;
		String[] labels = new String[sizes.length];
		int checked = 0;
		for (int i = 0; i < sizes.length; i++) {
			labels[i] = getString(R.string.percent, sizes[i]);
			if (sizes[i] == player.subtitleSize()) {
				checked = i;
			}
		}
		choose(R.string.settings_subtitle_size, labels, checked, i -> player.setSubtitleSize(sizes[i]));
	}

	private void chooseDownloadsFolder() {
		if (DownloadFolder.picked(requireContext(), graph.prefs) == null) {
			pickDownloadsFolder();
			return;
		}
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.settings_downloads_folder)
				.setMessage(DownloadFolder.label(requireContext(), graph.prefs))
				.setPositiveButton(R.string.settings_downloads_choose, (d, w) -> pickDownloadsFolder())
				.setNeutralButton(R.string.settings_downloads_use_default, (d, w) -> {
					DownloadFolder.useDefault(requireContext(), graph.prefs);
					build();
				})
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	private void pickDownloadsFolder() {
		try {
			folderPicker.launch(null);
		} catch (ActivityNotFoundException e) {
			toast(R.string.settings_downloads_no_picker);
		}
	}

	@NonNull
	private String currentLanguageLabel() {
		LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
		return locales.isEmpty() || locales.get(0) == null
				? getString(R.string.settings_language_system)
				: languageName(locales.get(0).toLanguageTag());
	}

	private void chooseLanguage() {
		String[] labels = new String[APP_LANGUAGES.length];
		labels[0] = getString(R.string.settings_language_system);
		for (int i = 1; i < APP_LANGUAGES.length; i++) {
			labels[i] = languageName(APP_LANGUAGES[i]);
		}
		LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
		String tag = current.isEmpty() || current.get(0) == null ? "" : current.get(0).toLanguageTag();
		int checked = 0;
		for (int i = 1; i < APP_LANGUAGES.length; i++) {
			if (tag.equalsIgnoreCase(APP_LANGUAGES[i]) || tag.startsWith(APP_LANGUAGES[i] + "-")
					&& !APP_LANGUAGES[i].contains("-")) {
				checked = i;
			}
		}
		choose(R.string.settings_language, labels, checked, i -> AppCompatDelegate.setApplicationLocales(
				i == 0 ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(APP_LANGUAGES[i])));
	}

	@NonNull
	private static String languageName(@NonNull String tag) {
		Locale locale = Locale.forLanguageTag(tag);
		String name = locale.getDisplayName(locale);
		return name.isEmpty() ? tag : Texts.capitalize(name, locale);
	}
}
