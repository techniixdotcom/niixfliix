package app.niixfliix.ui.common;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.Quality;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.app.AppGraph;
import app.niixfliix.download.DownloadFolder;
import app.niixfliix.download.SeriesDownload;
import app.niixfliix.net.Network;
import app.niixfliix.ui.Formats;

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

	private static final int REQUEST_STORAGE = 0x4e01;
	private static final int REQUEST_NOTIFICATIONS = 0x4e02;

	private QuickDownload() {
	}

	@MainThread
	public static void start(@NonNull Context context, @NonNull PosterItem poster, @NonNull What what) {
		AppGraph graph = AppGraph.get();
		Context app = context.getApplicationContext();
		Activity activity = context instanceof Activity ? (Activity) context : null;
		if (DownloadFolder.needsStoragePermission(app, graph.prefs)) {
			if (activity != null) {
				ActivityCompat.requestPermissions(activity, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
						REQUEST_STORAGE);
			}
			Toast.makeText(app, R.string.download_allow_storage, Toast.LENGTH_LONG).show();
			return;
		}
		if (Build.VERSION.SDK_INT >= 33 && activity != null && ContextCompat.checkSelfPermission(app,
				Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(activity, new String[] {Manifest.permission.POST_NOTIFICATIONS},
					REQUEST_NOTIFICATIONS);
		}
		Toast.makeText(app, R.string.download_looking, Toast.LENGTH_SHORT).show();
		graph.io.execute(() -> {
			Meta found = MetaFinder.find(graph.addons, graph.addonClient, poster.type, poster.id);
			graph.main.post(() -> {
				if (found == null) {
					Toast.makeText(app, R.string.download_could_not_start, Toast.LENGTH_SHORT).show();
					return;
				}
				if (what == What.TITLE || !found.isSeries()) {
					downloadTitle(app, found);
				} else {
					downloadEpisodes(app, found, what == What.SEASON);
				}
			});
		});
	}

	@MainThread
	private static void downloadTitle(Context app, Meta title) {
		AppGraph graph = AppGraph.get();
		Quality quality = graph.playerPrefs.quality(null, Network.isUnmetered(app));
		SeriesDownload.startTitle(graph.streams, graph.downloads(), title, quality,
				result -> Toast.makeText(app, titleMessage(result), Toast.LENGTH_LONG).show());
	}

	@MainThread
	private static void downloadEpisodes(Context app, Meta series, boolean seasonOnly) {
		AppGraph graph = AppGraph.get();
		long now = System.currentTimeMillis();
		List<Video> list;
		if (seasonOnly) {
			Integer season = SeriesDownload.currentSeason(series, graph.history.nextUp(series), now);
			list = season != null ? SeriesDownload.episodes(series, season, now) : new ArrayList<>();
		} else {
			list = SeriesDownload.episodes(series, null, now);
		}
		if (list.isEmpty()) {
			Toast.makeText(app, R.string.download_episodes_none_out, Toast.LENGTH_SHORT).show();
			return;
		}
		Toast.makeText(app, app.getResources().getQuantityString(R.plurals.download_episodes_looking, list.size(),
				list.size()), Toast.LENGTH_SHORT).show();
		String name = series.nameOrEmpty();
		SeriesDownload.start(graph.streams, graph.downloads(), series, list,
				graph.playerPrefs.quality(series.id, Network.isUnmetered(app)),
				v -> name + " · " + Formats.episode(app, v),
				result -> Toast.makeText(app, episodesMessage(app, result), Toast.LENGTH_LONG).show());
	}

	private static int titleMessage(@NonNull SeriesDownload.Result r) {
		if (r.added > 0) {
			return R.string.download_started;
		}
		return r.alreadyThere > 0 ? R.string.download_already_there : R.string.download_title_nothing;
	}

	@NonNull
	public static String episodesMessage(@NonNull Context context, @NonNull SeriesDownload.Result r) {
		if (r.added == 0 && r.alreadyThere == 0) {
			return context.getString(R.string.download_episodes_nothing);
		}
		List<String> parts = new ArrayList<>();
		if (r.added > 0) {
			parts.add(context.getResources().getQuantityString(R.plurals.download_episodes_added, r.added, r.added));
		}
		if (r.alreadyThere > 0) {
			parts.add(context.getResources().getQuantityString(R.plurals.download_episodes_already, r.alreadyThere,
					r.alreadyThere));
		}
		if (r.notDownloadable > 0) {
			parts.add(context.getResources().getQuantityString(R.plurals.download_episodes_skipped,
					r.notDownloadable, r.notDownloadable));
		}
		return String.join(" ", parts);
	}

	/** For a title without a known type, the menu offers no download. */
	public static boolean canDownload(@Nullable String type) {
		return Meta.TYPE_MOVIE.equals(type) || Meta.TYPE_SERIES.equals(type);
	}
}
