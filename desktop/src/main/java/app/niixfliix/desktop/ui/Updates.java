package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import app.niixfliix.app.AppInfo;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Dialog;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Opener;
import app.niixfliix.desktop.platform.Paths;
import app.niixfliix.update.Releases;
import app.niixfliix.util.FileNames;

public final class Updates {

	private static final int MAX_NOTES = 2000;
	private static final int PROGRESS_WIDTH = 420;
	private static final int MAX_NAME_BYTES = 200;
	private static boolean busy;

	private Updates() {
	}

	public static void check() {
		if (busy) {
			return;
		}
		busy = true;
		Toast.show(I18n.t("update_checking"));
		run(result -> {
			if (result.release != null) {
				offer(result.release);
			} else if (result.status == Releases.Status.UP_TO_DATE) {
				Toast.show(I18n.t("update_none"));
			} else {
				Dialogs.message(I18n.t("update_check_failed"), explain(result));
			}
		});
	}

	public static void checkQuietly() {
		if (busy) {
			return;
		}
		busy = true;
		run(result -> {
			if (result.release != null) {
				offer(result.release);
			}
		});
	}

	private interface Done {
		void on(@NonNull Releases.CheckResult result);
	}

	private static void run(Done done) {
		DesktopGraph graph = DesktopGraph.get();
		graph.io.execute(() -> {
			Releases.CheckResult result = graph.releases.check();
			graph.main.post(() -> {
				busy = false;
				done.on(result);
			});
		});
	}

	private static void offer(Releases.Release release) {
		String notes = release.notes.length() > MAX_NOTES ? release.notes.substring(0, MAX_NOTES) + "…"
				: release.notes;
		Dialogs.confirm(I18n.t("update_available", release.version),
				notes.isEmpty() ? I18n.t("update_no_notes") : notes, I18n.t("update_install"),
				() -> download(release));
	}

	@NonNull
	private static String explain(@NonNull Releases.CheckResult result) {
		String repo = AppInfo.GITHUB_REPO;
		String version = result.version != null ? result.version : "";
		switch (result.status) {
			case REPO_NOT_FOUND:
				return I18n.t("update_repo_not_found", repo);
			case NO_RELEASES:
				return I18n.t("update_no_releases", repo);
			case NO_FILE:
				return I18n.t("update_no_bundle", version);
			case NO_CHECKSUM:
				return I18n.t("update_no_bundle_checksum", version);
			case RATE_LIMITED:
				return I18n.t("update_rate_limited");
			case NETWORK:
			default:
				return I18n.t("update_network");
		}
	}

	private static void download(Releases.Release release) {
		DesktopGraph graph = DesktopGraph.get();
		Progress bar = Progress.bar();
		bar.pulse();
		Dialog progress = Dialogs.locked(I18n.t("update_downloading"), bar, PROGRESS_WIDTH);
		busy = true;
		graph.io.execute(() -> {
			File dir = Paths.downloads();
			File target = null;
			Releases.Problem problem = Releases.Problem.DOWNLOAD;
			if (dir.isDirectory() || dir.mkdirs()) {
				target = FileNames.unique(dir, FileNames.sanitize(release.fileName, MAX_NAME_BYTES));
				try {
					graph.releases.download(release, target, percent -> graph.main.post(() -> {
						if (percent >= 0) {
							bar.fraction(percent / 100.0);
						} else {
							bar.pulse();
						}
					}));
					problem = null;
				} catch (Releases.DownloadException e) {
					problem = e.problem;
				}
			}
			File file = target;
			Releases.Problem failed = problem;
			graph.main.post(() -> {
				busy = false;
				progress.forceClose();
				if (failed != null || file == null) {
					Dialogs.message(I18n.t("update_failed_title"), message(failed));
					return;
				}
				Opener.openFile(file);
				Dialogs.message(I18n.t("update_ready_title"),
						I18n.t("update_ready_desktop", file.getAbsolutePath(), "flatpak install --user \""
								+ file.getAbsolutePath() + "\""));
			});
		});
	}

	@NonNull
	private static String message(@Nullable Releases.Problem problem) {
		if (problem == null) {
			return I18n.t("update_failed_download");
		}
		switch (problem) {
			case TOO_LARGE:
				return I18n.t("update_failed_too_large");
			case CHECKSUM:
				return I18n.t("update_failed_checksum");
			case DOWNLOAD:
			default:
				return I18n.t("update_failed_download");
		}
	}
}
