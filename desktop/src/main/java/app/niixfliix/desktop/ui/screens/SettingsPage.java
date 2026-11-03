package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

import app.niixfliix.app.AppInfo;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Clamp;
import app.niixfliix.desktop.gtk.Dialog;
import app.niixfliix.desktop.gtk.FileChooser;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Image;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Prefs;
import app.niixfliix.desktop.gtk.Scroller;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Opener;
import app.niixfliix.desktop.platform.Paths;
import app.niixfliix.desktop.ui.Dialogs;
import app.niixfliix.desktop.ui.Fmt;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.QualityDialog;
import app.niixfliix.desktop.ui.SignInDialog;
import app.niixfliix.desktop.ui.Toast;
import app.niixfliix.desktop.ui.Updates;
import app.niixfliix.player.PlayerPreferences;
import app.niixfliix.ui.setup.Choices;
import app.niixfliix.util.CrashLog;
import app.niixfliix.util.Logs;
import app.niixfliix.util.Texts;

public final class SettingsPage implements Page {

	private static final int PREFERENCES_WIDTH = 600;

	private static final int CRASH_LOG_WIDTH = 760;

	private final DesktopGraph graph = DesktopGraph.get();
	private final Box root = Box.column(0);
	private final Prefs.Row account;
	private final Prefs.Row quality;
	private final Prefs.Row language;
	private final Prefs.Row subtitleSize;
	private final Prefs.Row subtitleColor;
	private final Prefs.Row subtitleBackground;
	private final Prefs.Switch hardware;
	private final Prefs.Row appLanguage;
	private final Prefs.Switch hideAdult;
	private final Prefs.Switch checkOnStart;
	private final Prefs.Row downloadsFolder;
	private final Prefs.Row crash;

	public SettingsPage() {
		var prefs = graph.prefs;
		PlayerPreferences player = graph.playerPrefs;
		Prefs.Page page = Prefs.page();

		account = link(I18n.t("account_sign_in"), this::onAccount);
		page.add(group(I18n.t("account_section"), account));

		quality = row(I18n.t("quality_title"), () -> choose(I18n.t("quality_title"), QualityDialog.labels(),
				Choices.qualityIndex(player.quality(true)), i -> player.setQuality(true, Choices.QUALITIES.get(i))));
		language = row(I18n.t("setup_language"), () -> choose(I18n.t("setup_language"),
				Arrays.asList(Choices.languageLabels()), Choices.languageIndex(player.language()),
				i -> player.setLanguage(Choices.LANGUAGES.get(i))));
		hardware = Prefs.toggle(I18n.t("settings_hardware_decoding"),
				I18n.t("settings_hardware_decoding_summary"), graph.desktopPrefs.hardwareDecoding(),
				graph.desktopPrefs::setHardwareDecoding);
		page.add(group(I18n.t("settings_playback"), quality, language, hardware));

		subtitleSize = row(I18n.t("settings_subtitle_size"), this::chooseSubtitleSize);
		List<String> colors = I18n.array("subtitle_colors");
		subtitleColor = row(I18n.t("settings_subtitle_color"), () -> choose(I18n.t("settings_subtitle_color"),
				colors, player.subtitleColor().ordinal(), i -> {
					player.setSubtitleColor(PlayerPreferences.SubtitleColor.values()[i]);
					graph.playback().applySubtitleStyle();
				}));
		List<String> backgrounds = I18n.array("subtitle_backgrounds");
		subtitleBackground = row(I18n.t("settings_subtitle_background"), () -> choose(
				I18n.t("settings_subtitle_background"), backgrounds, player.subtitleBackground().ordinal(), i -> {
					player.setSubtitleBackground(PlayerPreferences.SubtitleBackground.values()[i]);
					graph.playback().applySubtitleStyle();
				}));
		page.add(group(I18n.t("settings_subtitles"), subtitleSize, subtitleColor, subtitleBackground));

		appLanguage = row(I18n.t("settings_language"), this::chooseAppLanguage);
		hideAdult = Prefs.toggle(I18n.t("setup_hide_adult"), null, prefs.hideAdult(),
				hide -> {
					prefs.setHideAdult(hide);
					graph.addons.filtersChanged();
				});
		checkOnStart = Prefs.toggle(I18n.t("settings_check_on_start"),
				I18n.t("settings_check_on_start_summary"), prefs.updateCheckOnStart(), prefs::setUpdateCheckOnStart);
		page.add(group(I18n.t("settings_app"), appLanguage, hideAdult,
				row(I18n.t("settings_check_updates"), Updates::check), checkOnStart));

		downloadsFolder = row(I18n.t("settings_downloads_folder"), this::chooseDownloadsFolder);
		page.add(group(I18n.t("settings_storage"), downloadsFolder,
				row(I18n.t("settings_clear_history"), () -> Dialogs.confirm(null,
						I18n.t("settings_clear_history_confirm"), I18n.t("action_clear"), () -> {
							graph.history.clear();
							Toast.show(I18n.t("settings_cleared"));
						})),
				row(I18n.t("settings_clear_searches"), () -> {
					graph.searchHistory.clear();
					Toast.show(I18n.t("settings_cleared"));
				})));

		String source = "https://github.com/" + AppInfo.GITHUB_REPO;
		crash = row(I18n.t("settings_crash_log"), this::showCrashLog);
		Prefs.Row version = row(I18n.t("settings_version_title"), () -> Dialogs.message(I18n.t("settings_licences"),
				I18n.t("licences_text_desktop"))).subtitle(AppInfo.VERSION_NAME);
		page.add(group(I18n.t("settings_about"),
				row(I18n.t("settings_source_code"), () -> Opener.openWebLink(source)).subtitle(source),
				crash, version));

		page.vexpand(true);
		Label title = Label.of(I18n.t("nav_settings"), "title-1");
		title.margins(20, 12, 4, 12);
		root.add(Clamp.of(title, PREFERENCES_WIDTH), page);
	}

	@NonNull
	@Override
	public Widget view() {
		return root;
	}

	@Override
	public void onShow() {
		refresh();
	}

	private void refresh() {
		PlayerPreferences player = graph.playerPrefs;
		if (graph.account.isSignedIn()) {
			String email = graph.account.email();
			account.title(I18n.t("account_sign_out")).subtitle(email != null ? I18n.t("account_signed_in", email) : null);
		} else {
			account.title(I18n.t("account_sign_in")).subtitle(I18n.t("account_sign_in_summary"));
		}
		quality.subtitle(Fmt.quality(player.quality(true)));
		language.subtitle(Choices.languageName(player.language()));
		subtitleSize.subtitle(I18n.t("percent", player.subtitleSize()));
		subtitleColor.subtitle(item(I18n.array("subtitle_colors"), player.subtitleColor().ordinal()));
		subtitleBackground.subtitle(item(I18n.array("subtitle_backgrounds"), player.subtitleBackground().ordinal()));
		hardware.active(graph.desktopPrefs.hardwareDecoding());
		hideAdult.active(graph.prefs.hideAdult());
		checkOnStart.active(graph.prefs.updateCheckOnStart());
		downloadsFolder.subtitle(Paths.downloads().getAbsolutePath());
		appLanguage.subtitle(appLanguageLabel());
		showCrashSummary();
	}

	private void onAccount() {
		if (graph.account.isSignedIn()) {
			Dialogs.confirm(I18n.t("account_sign_out"), I18n.t("account_sign_out_confirm_desktop"),
					I18n.t("account_sign_out"), () -> {
						graph.account.signOut();
						refresh();
					});
		} else {
			SignInDialog.show(this::refresh);
		}
	}

	private static Prefs.Group group(String title, Widget... rows) {
		Prefs.Group group = Prefs.group(title, null);
		for (Widget row : rows) {
			group.add(row);
		}
		return group;
	}

	private static Prefs.Row row(String title, Runnable onClick) {
		return Prefs.row(title, null, onClick);
	}

	private static Prefs.Row link(String title, Runnable onClick) {
		Image arrow = Image.icon(Icons.name("chevron_right"));
		return row(title, onClick).suffix(arrow);
	}

	private void choose(String title, List<String> labels, int checked, IntConsumer choice) {
		Dialogs.choose(title, labels, checked, i -> {
			choice.accept(i);
			refresh();
		});
	}

	private static String item(List<String> items, int index) {
		return index >= 0 && index < items.size() ? items.get(index) : "";
	}

	private void chooseSubtitleSize() {
		PlayerPreferences player = graph.playerPrefs;
		int[] sizes = PlayerPreferences.SUBTITLE_SIZES;
		List<String> labels = new ArrayList<>();
		int checked = 0;
		for (int i = 0; i < sizes.length; i++) {
			labels.add(I18n.t("percent", sizes[i]));
			if (sizes[i] == player.subtitleSize()) {
				checked = i;
			}
		}
		choose(I18n.t("settings_subtitle_size"), labels, checked, i -> {
			player.setSubtitleSize(sizes[i]);
			graph.playback().applySubtitleStyle();
		});
	}

	private void chooseDownloadsFolder() {
		if (graph.prefs.downloadsFolder() == null) {
			pickDownloadsFolder();
			return;
		}
		Dialogs.either(I18n.t("settings_downloads_folder"), Paths.downloads().getAbsolutePath(),
				I18n.t("settings_downloads_use_default"), () -> {
					graph.prefs.setDownloadsFolder(null);
					Paths.setPickedDownloads(null);
					refresh();
				},
				I18n.t("settings_downloads_choose"), this::pickDownloadsFolder);
	}

	private void pickDownloadsFolder() {
		FileChooser.folder(root, I18n.t("settings_downloads_choose"), Paths.downloads(), folder -> {
			if (!canWriteTo(folder)) {
				Toast.show(I18n.t("settings_downloads_not_writable"));
				return;
			}
			graph.prefs.setDownloadsFolder(folder.getAbsolutePath());
			Paths.setPickedDownloads(folder.getAbsolutePath());
			refresh();
		});
	}

	private static boolean canWriteTo(File folder) {
		try {
			File probe = File.createTempFile(".niixfliix-", ".tmp", folder);
			if (!probe.delete()) {
				probe.deleteOnExit();
			}
			return true;
		} catch (IOException | SecurityException e) {
			Logs.w("SettingsPage", "Can't write to " + folder, e);
			return false;
		}
	}

	private String appLanguageLabel() {
		String language = graph.desktopPrefs.language();
		return language == null ? I18n.t("settings_language_system") : languageName(language);
	}

	private void chooseAppLanguage() {
		List<String> labels = new ArrayList<>();
		labels.add(I18n.t("settings_language_system"));
		for (String tag : I18n.LANGUAGES) {
			labels.add(languageName(tag));
		}
		String current = graph.desktopPrefs.language();
		int checked = current == null ? 0 : I18n.LANGUAGES.indexOf(current) + 1;
		choose(I18n.t("settings_language"), labels, Math.max(0, checked), i -> {
			String chosen = i == 0 ? null : I18n.LANGUAGES.get(i - 1);
			boolean same = chosen == null ? current == null : chosen.equals(current);
			graph.desktopPrefs.setLanguage(chosen);
			if (!same) {
				Dialogs.message(I18n.t("settings_language"), I18n.t("settings_language_restart"));
			}
		});
	}

	@NonNull
	private static String languageName(@NonNull String tag) {
		Locale locale = Locale.forLanguageTag(tag);
		String name = locale.getDisplayName(locale);
		return name.isEmpty() ? tag : Texts.capitalize(name, locale);
	}

	private void showCrashSummary() {
		graph.io.execute(() -> {
			long when = CrashLog.lastCrashMillis();
			graph.main.post(() -> {
				if (when > 0) {
					String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, I18n.locale())
							.format(new Date(when));
					crash.subtitle(I18n.t("settings_crash_log_summary_desktop", date));
				} else {
					crash.subtitle(I18n.t("settings_crash_log_none"));
				}
			});
		});
	}

	private void showCrashLog() {
		graph.io.execute(() -> {
			String log = CrashLog.read();
			graph.main.post(() -> {
				if (log == null) {
					Toast.show(I18n.t("settings_crash_log_none"));
					return;
				}
				Label text = Label.of(log, "monospace", "crash-log").selectable();
				Scroller scroll = Scroller.horizontal(text);
				Button delete = Dialogs.button(I18n.t("action_delete"), false);
				Button close = Dialogs.button(I18n.t("action_close"), false);
				Button copy = Dialogs.button(I18n.t("action_copy"), true);
				Dialog dialog = Dialogs.custom(I18n.t("settings_crash_log"), scroll, CRASH_LOG_WIDTH, delete, close,
						copy);
				close.onClick(dialog::close);
				copy.onClick(() -> {
					copy.copyText(log);
					Toast.show(I18n.t("settings_crash_log_copied"));
				});
				delete.onClick(() -> {
					dialog.close();
					graph.io.execute(() -> {
						CrashLog.clear();
						graph.main.post(() -> {
							Toast.show(I18n.t("settings_crash_log_deleted"));
							refresh();
						});
					});
				});
			});
		});
	}
}
