package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.download.SeriesDownload;
import app.niixfliix.ui.common.PosterItem;

/** Downloads straight from a poster's menu, with the stream auto-play would pick among those that can be saved. */
public final class QuickDownload {

	public enum What {
		/** A movie, or another single title. */
		TITLE,
		/** The season with the next episode to watch. */
		SEASON,
		/** Every episode that is out. */
		SERIES
	}

	private QuickDownload() {
	}

	public static void start(@NonNull PosterItem poster, @NonNull What what) {
		DesktopGraph graph = DesktopGraph.get();
		Toast.show(I18n.t("download_looking"));
		graph.io.execute(() -> {
			Meta found = MetaFinder.find(graph.addons, graph.addonClient, poster.type, poster.id);
			graph.main.post(() -> {
				if (found == null) {
					Toast.show(I18n.t("download_could_not_start"));
					return;
				}
				if (what == What.TITLE || !found.isSeries()) {
					SeriesDownload.startTitle(graph.streams, graph.downloads(), found,
							graph.playerPrefs.quality(null, true), result -> Toast.show(titleMessage(result)));
				} else {
					downloadEpisodes(found, what == What.SEASON);
				}
			});
		});
	}

	public static void downloadEpisodes(@NonNull Meta series, @NonNull List<Video> list) {
		if (list.isEmpty()) {
			Toast.show(I18n.t("download_episodes_none_out"));
			return;
		}
		DesktopGraph graph = DesktopGraph.get();
		Toast.show(I18n.plural("download_episodes_looking", list.size(), list.size()));
		String name = series.nameOrEmpty();
		SeriesDownload.start(graph.streams, graph.downloads(), series, list,
				graph.playerPrefs.quality(series.id, true), v -> name + " · " + Fmt.episode(v),
				result -> Toast.show(episodesMessage(result)));
	}

	private static void downloadEpisodes(Meta series, boolean seasonOnly) {
		DesktopGraph graph = DesktopGraph.get();
		long now = System.currentTimeMillis();
		List<Video> list;
		if (seasonOnly) {
			Integer season = SeriesDownload.currentSeason(series, graph.history.nextUp(series), now);
			list = season != null ? SeriesDownload.episodes(series, season, now) : new ArrayList<>();
		} else {
			list = SeriesDownload.episodes(series, null, now);
		}
		downloadEpisodes(series, list);
	}

	@NonNull
	private static String titleMessage(@NonNull SeriesDownload.Result r) {
		if (r.added > 0) {
			return I18n.t("download_started");
		}
		return I18n.t(r.alreadyThere > 0 ? "download_already_there" : "download_title_nothing");
	}

	@NonNull
	public static String episodesMessage(@NonNull SeriesDownload.Result r) {
		if (r.added == 0 && r.alreadyThere == 0) {
			return I18n.t("download_episodes_nothing");
		}
		List<String> parts = new ArrayList<>();
		if (r.added > 0) {
			parts.add(I18n.plural("download_episodes_added", r.added, r.added));
		}
		if (r.alreadyThere > 0) {
			parts.add(I18n.plural("download_episodes_already", r.alreadyThere, r.alreadyThere));
		}
		if (r.notDownloadable > 0) {
			parts.add(I18n.plural("download_episodes_skipped", r.notDownloadable, r.notDownloadable));
		}
		return String.join(" ", parts);
	}

	public static boolean canDownload(@Nullable String type) {
		return Meta.TYPE_MOVIE.equals(type) || Meta.TYPE_SERIES.equals(type);
	}
}
