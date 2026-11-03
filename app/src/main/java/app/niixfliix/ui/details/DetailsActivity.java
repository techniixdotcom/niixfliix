package app.niixfliix.ui.details;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.niixfliix.R;
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.download.DownloadFolder;
import app.niixfliix.download.SeriesDownload;
import app.niixfliix.net.Network;
import app.niixfliix.ui.BaseActivity;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.Images;
import app.niixfliix.ui.Links;
import app.niixfliix.ui.MainActivity;
import app.niixfliix.ui.WindowPadding;
import app.niixfliix.ui.common.HorizontalSwipe;
import app.niixfliix.ui.common.MoreLikeThis;
import app.niixfliix.ui.common.PosterAdapter;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.common.TitleAbout;
import app.niixfliix.ui.streams.StreamsSheet;
import app.niixfliix.ui.web.TrailerActivity;

public final class DetailsActivity extends BaseActivity implements StreamsSheet.Host, EpisodesAdapter.Listener {

	private static final String EXTRA_TYPE = "type";
	private static final String EXTRA_ID = "id";
	private static final String EXTRA_NAME = "name";
	private static final String EXTRA_POSTER = "poster";
	private static final String EXTRA_PLAY = "play";
	private static final int TRAILER_MAX_WIDTH_DP = 560;
	private static final long PULSE_MS = 800;

	private final AppGraph graph = AppGraph.get();
	private String type;
	private String id;
	@Nullable private Meta meta;
	private int season = -1;
	private boolean pendingPlay;

	private ImageView backdrop;
	private ImageView logo;
	private TextView title;
	private TextView info;
	private ChipGroup genres;
	private MaterialButton play;
	private MaterialButton save;
	private MaterialButton watched;
	private TextView description;
	private View castSection;
	private ChipGroup castChips;
	private View trailerSection;
	private ImageView trailerImage;
	private View aboutSection;
	private ViewGroup aboutFacts;
	private MaterialButton imdb;
	private View similarSection;
	private PosterAdapter similar;
	private TextView notice;
	private View seriesSection;
	private MaterialAutoCompleteTextView seasonInput;
	private View loading;
	@Nullable private ObjectAnimator pulse;
	private EpisodesAdapter episodes;
	@Nullable private List<Video> pendingDownloads;
	private final ActivityResultLauncher<String> storagePermission = registerForActivityResult(
			new ActivityResultContracts.RequestPermission(), granted -> {
				List<Video> list = pendingDownloads;
				pendingDownloads = null;
				Meta m = meta;
				if (list == null || m == null) {
					return;
				}
				if (granted) {
					startDownloads(m, list);
				} else {
					Toast.makeText(this, R.string.download_needs_storage, Toast.LENGTH_LONG).show();
				}
			});
	private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
			new ActivityResultContracts.RequestPermission(), granted -> {
			});

	public static void start(@NonNull Context context, @NonNull PosterItem item) {
		start(context, item, false);
	}

	public static void start(@NonNull Context context, @NonNull PosterItem item, boolean play) {
		if (item.id.isEmpty() || item.type.isEmpty()) {
			return;
		}
		context.startActivity(new Intent(context, DetailsActivity.class)
				.putExtra(EXTRA_TYPE, item.type)
				.putExtra(EXTRA_ID, item.id)
				.putExtra(EXTRA_NAME, item.name)
				.putExtra(EXTRA_POSTER, item.poster)
				.putExtra(EXTRA_PLAY, play));
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_details);
		type = getIntent().getStringExtra(EXTRA_TYPE);
		id = getIntent().getStringExtra(EXTRA_ID);
		pendingPlay = savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_PLAY, false);
		if (type == null || id == null) {
			finish();
			return;
		}
		backdrop = findViewById(R.id.backdrop);
		logo = findViewById(R.id.logo);
		title = findViewById(R.id.title);
		info = findViewById(R.id.info);
		genres = findViewById(R.id.genres);
		play = findViewById(R.id.play);
		save = findViewById(R.id.save);
		watched = findViewById(R.id.watched);
		description = findViewById(R.id.description);
		castSection = findViewById(R.id.cast_section);
		castChips = findViewById(R.id.cast_chips);
		trailerSection = findViewById(R.id.trailer_section);
		trailerImage = findViewById(R.id.trailer_image);
		aboutSection = findViewById(R.id.about_section);
		aboutFacts = findViewById(R.id.about_facts);
		imdb = findViewById(R.id.imdb);
		similarSection = findViewById(R.id.similar_section);
		notice = findViewById(R.id.notice);
		seriesSection = findViewById(R.id.series_section);
		seasonInput = findViewById(R.id.season_input);
		loading = findViewById(R.id.loading);
		if (ValueAnimator.areAnimatorsEnabled()) {
			pulse = ObjectAnimator.ofFloat(loading, View.ALPHA, 1f, 0.45f);
			pulse.setDuration(PULSE_MS);
			pulse.setRepeatCount(ValueAnimator.INFINITE);
			pulse.setRepeatMode(ValueAnimator.REVERSE);
			pulse.start();
		}
		View back = findViewById(R.id.back);
		back.setOnClickListener(v -> finish());
		WindowPadding.applyAsMargin(back, WindowPadding.TOP | WindowPadding.LEFT);
		WindowPadding.apply(findViewById(R.id.content), WindowPadding.BOTTOM | WindowPadding.LEFT | WindowPadding.RIGHT);
		WindowPadding.apply(findViewById(R.id.mini_player), WindowPadding.BOTTOM | WindowPadding.LEFT | WindowPadding.RIGHT);

		RecyclerView list = findViewById(R.id.episodes);
		list.setLayoutManager(new LinearLayoutManager(this));
		episodes = new EpisodesAdapter(this, graph.history);
		list.setAdapter(episodes);

		play.setOnClickListener(v -> onPlay());
		save.setOnClickListener(v -> toggleSaved());
		watched.setOnClickListener(v -> toggleWatched());
		findViewById(R.id.trailer_card).setOnClickListener(v -> openTrailer());
		capTrailerWidth();
		RecyclerView similarList = findViewById(R.id.similar);
		similarList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
		similar = new PosterAdapter(false, new PosterAdapter.Listener() {
			@Override
			public void onPosterClick(@NonNull PosterItem item) {
				DetailsActivity.start(DetailsActivity.this, item);
			}

			@Override
			public boolean onPosterLongClick(@NonNull PosterItem item) {
				QueueMenu.show(DetailsActivity.this, item, null, null, () -> similar.refreshWatched());
				return true;
			}
		});
		similarList.setAdapter(similar);
		HorizontalSwipe.attach(similarList);
		Images.pauseWhileFlinging(similarList);
		findViewById(R.id.download_episodes).setOnClickListener(v -> downloadEpisodes());
		seasonInput.setOnItemClickListener((parent, view, position, rowId) -> {
			List<Integer> seasons = seasonNumbers();
			if (position < seasons.size()) {
				season = seasons.get(position);
				showEpisodes();
			}
		});

		bind(metaFromIntent(), false);
		load();
		if (type != null && id != null) {
			graph.prepareToPlay(type, Meta.TYPE_SERIES.equals(type) ? null : id, seriesId());
		}
	}

	@Override
	protected void onDestroy() {
		stopPulse();
		super.onDestroy();
	}

	private void stopPulse() {
		if (pulse != null) {
			pulse.cancel();
			pulse = null;
		}
		if (loading != null) {
			loading.setAlpha(1f);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshState();
		playIfAsked();
	}

	private void playIfAsked() {
		if (!pendingPlay || meta == null || isFinishing() || getSupportFragmentManager().isStateSaved()) {
			return;
		}
		pendingPlay = false;
		onPlay();
	}

	@Nullable
	@Override
	public Meta streamsMeta() {
		return meta;
	}

	private void load() {
		graph.io.execute(() -> {
			Meta found = MetaFinder.find(graph.addons, graph.addonClient, type, id);
			List<String> genresForSimilar = found != null ? new ArrayList<>(found.allGenres()) : null;
			long releasedForSimilar = found != null ? ReleaseOrder.of(found) : 0;
			graph.io.execute(() -> loadSimilar(genresForSimilar, releasedForSimilar));
			Meta result = found != null ? graph.translations.translate(found) : null;
			graph.main.post(() -> {
				if (isDestroyed()) {
					return;
				}
				stopPulse();
				loading.setVisibility(View.GONE);
				if (result != null) {
					bind(result, true);
				} else {
					bind(metaFromIntent(), true);
					notice.setText(R.string.details_no_meta);
					notice.setVisibility(View.VISIBLE);
				}
				playIfAsked();
			});
		});
	}

	@NonNull
	private Meta metaFromIntent() {
		Meta m = new Meta();
		m.id = id;
		m.type = type;
		m.name = getIntent().getStringExtra(EXTRA_NAME);
		m.poster = getIntent().getStringExtra(EXTRA_POSTER);
		return m;
	}

	private void bind(@NonNull Meta m, boolean complete) {
		if (complete) {
			meta = m;
		}
		Images.backdrop(backdrop, m.background != null ? m.background : m.poster);
		title.setText(m.name);
		logo.setVisibility(View.VISIBLE);
		title.setVisibility(View.GONE);
		Images.logo(logo, m.logo, () -> {
			logo.setVisibility(View.GONE);
			title.setVisibility(View.VISIBLE);
		});
		info.setText(infoLine(m));
		genres.removeAllViews();
		if (m.genres != null) {
			for (String g : m.genres) {
				Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_info_chip, genres, false);
				chip.setText(g);
				genres.addView(chip);
			}
		}
		description.setText(m.description);
		description.setVisibility(m.description == null || m.description.isEmpty() ? View.GONE : View.VISIBLE);
		bindCast(m);
		bindTrailer(m);
		bindAbout(m);
		play.setEnabled(complete);
		watched.setEnabled(complete);
		watched.setVisibility(Catalog.isLiveType(m.type) ? View.GONE : View.VISIBLE);
		if (complete) {
			refreshState();
		}
	}

	private void bindCast(@NonNull Meta m) {
		castChips.removeAllViews();
		LayoutInflater inflater = getLayoutInflater();
		for (String name : TitleAbout.cast(m)) {
			Chip chip = (Chip) inflater.inflate(R.layout.item_cast_chip, castChips, false);
			chip.setText(name);
			chip.setContentDescription(getString(R.string.details_search_person, name));
			chip.setOnClickListener(v -> MainActivity.search(this, name));
			castChips.addView(chip);
		}
		castSection.setVisibility(castChips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
	}

	private void bindTrailer(@NonNull Meta m) {
		String image = TitleAbout.trailerImage(m.trailerYoutubeId());
		trailerSection.setVisibility(image != null ? View.VISIBLE : View.GONE);
		if (image != null) {
			Images.backdrop(trailerImage, image);
		} else {
			Images.cancel(trailerImage);
		}
	}

	private void bindAbout(@NonNull Meta m) {
		aboutFacts.removeAllViews();
		Locale locale = getResources().getConfiguration().getLocales().get(0);
		LayoutInflater inflater = getLayoutInflater();
		for (TitleAbout.Entry entry : TitleAbout.facts(m, locale, getString(R.string.list_separator))) {
			View row = inflater.inflate(R.layout.item_fact, aboutFacts, false);
			((TextView) row.findViewById(R.id.fact_label)).setText(factLabel(entry.fact));
			((TextView) row.findViewById(R.id.fact_value)).setText(entry.value);
			aboutFacts.addView(row);
		}
		String link = TitleAbout.imdbUrl(m);
		imdb.setVisibility(link != null ? View.VISIBLE : View.GONE);
		imdb.setOnClickListener(v -> Links.open(this, link));
		aboutSection.setVisibility(aboutFacts.getChildCount() > 0 || link != null ? View.VISIBLE : View.GONE);
	}

	private int factLabel(@NonNull TitleAbout.Fact fact) {
		switch (fact) {
			case DIRECTOR:
				return R.string.details_director;
			case WRITERS:
				return R.string.details_writers;
			case RELEASED:
				return R.string.details_released;
			case COUNTRY:
				return R.string.details_country;
			case AWARDS:
			default:
				return R.string.details_awards;
		}
	}

	private void openTrailer() {
		String yt = meta != null ? meta.trailerYoutubeId() : null;
		if (yt != null) {
			TrailerActivity.start(this, yt);
		}
	}

	private void capTrailerWidth() {
		View card = findViewById(R.id.trailer_card);
		int max = Math.round(TRAILER_MAX_WIDTH_DP * getResources().getDisplayMetrics().density);
		card.post(() -> {
			if (card.getWidth() > max) {
				ViewGroup.LayoutParams lp = card.getLayoutParams();
				lp.width = max;
				card.setLayoutParams(lp);
			}
		});
	}

	private void loadSimilar(@Nullable List<String> genres, long released) {
		List<PosterItem> items = MoreLikeThis.find(graph.addons, graph.addonClient, type, id, genres, released,
				graph.translations.language());
		graph.main.post(() -> {
			if (isDestroyed()) {
				return;
			}
			similar.replace(items);
			similarSection.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
		});
	}

	@NonNull
	private String infoLine(@NonNull Meta m) {
		List<String> parts = new ArrayList<>();
		if (m.releaseInfo != null && !m.releaseInfo.isEmpty()) {
			parts.add(m.releaseInfo);
		}
		if (m.runtime != null && !m.runtime.isEmpty()) {
			parts.add(m.runtime);
		}
		if (m.imdbRating != null && !m.imdbRating.isEmpty()) {
			parts.add(getString(R.string.details_rating, m.imdbRating));
		}
		return String.join(" · ", parts);
	}

	private void refreshState() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		boolean saved = graph.library.contains(id);
		save.setIconResource(saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
		save.setContentDescription(getString(saved ? R.string.details_unsave : R.string.details_save));
		boolean isWatched = isAllWatched(m);
		watched.setIconResource(isWatched ? R.drawable.ic_check_circle : R.drawable.ic_check);
		watched.setContentDescription(getString(isWatched ? R.string.details_mark_unwatched : R.string.details_mark_watched));

		List<Integer> seasons = seasonNumbers();
		if (m.isSeries() && !seasons.isEmpty()) {
			seriesSection.setVisibility(View.VISIBLE);
			Video next = graph.history.nextUp(m);
			graph.prepareToPlay(type, next != null ? next.id : null, seriesId());
			if (season < 0 || !seasons.contains(season)) {
				season = next != null ? next.seasonOrZero() : seasons.get(0);
			}
			List<String> labels = new ArrayList<>();
			for (int s : seasons) {
				labels.add(seasonLabel(s));
			}
			seasonInput.setSimpleItems(labels.toArray(new String[0]));
			seasonInput.setText(seasonLabel(season), false);
			showEpisodes();
			if (next != null) {
				long resume = graph.history.resumePosition(next.id);
				String episode = getString(R.string.episode_number, next.seasonOrZero(), next.episodeOrZero());
				play.setText(resume > 0 ? getString(R.string.details_continue_episode, episode)
						: getString(R.string.details_play_episode, episode));
			} else {
				play.setText(R.string.details_play);
			}
		} else {
			seriesSection.setVisibility(View.GONE);
			String videoId = m.playableVideoId();
			graph.prepareToPlay(type, videoId, seriesId());
			long resume = videoId != null ? graph.history.resumePosition(videoId) : 0;
			play.setText(resume > 0
					? getString(R.string.details_continue_at, DateUtils.formatElapsedTime(resume / 1000))
					: getString(R.string.details_play));
			play.setEnabled(videoId != null);
		}
	}

	private void showEpisodes() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		Map<Integer, List<Video>> seasons = m.seasons();
		List<Video> list = seasons.get(season);
		Video next = graph.history.nextUp(m);
		episodes.submit(list != null ? list : new ArrayList<>(), next != null ? next.id : null);
	}

	@NonNull
	private List<Integer> seasonNumbers() {
		return meta != null ? new ArrayList<>(meta.seasons().keySet()) : new ArrayList<>();
	}

	@NonNull
	private String seasonLabel(int number) {
		return number == 0 ? getString(R.string.details_specials) : getString(R.string.details_season_number, number);
	}

	private boolean isAllWatched(@NonNull Meta m) {
		if (!m.isSeries()) {
			String videoId = m.playableVideoId();
			return videoId != null && graph.history.isWatched(videoId);
		}
		if (m.videosOrEmpty().isEmpty()) {
			return false;
		}
		for (String episodeId : m.episodeIds()) {
			if (!graph.history.isWatched(episodeId)) {
				return false;
			}
		}
		return true;
	}

	private void onPlay() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		if (m.isSeries()) {
			Video next = graph.history.nextUp(m);
			if (next != null) {
				onEpisodeClick(next);
			}
			return;
		}
		String videoId = m.playableVideoId();
		if (videoId != null) {
			StreamsSheet.show(getSupportFragmentManager(), m, videoId, m.nameOrEmpty(), null);
		}
	}

	@Override
	public void onEpisodeClick(@NonNull Video video) {
		Meta m = meta;
		if (m == null || video.id == null) {
			return;
		}
		StreamsSheet.show(getSupportFragmentManager(), m, video.id, m.nameOrEmpty(),
				Formats.episode(this, video));
	}

	@Override
	public void onEpisodeLongClick(@NonNull Video video) {
		Meta m = meta;
		String videoId = video.id;
		if (m == null || videoId == null) {
			return;
		}
		QueueItem item = QueueItem.of(id, type, m.nameOrEmpty(), m.poster);
		item.videoId = videoId;
		item.episode = Formats.episode(this, video);
		boolean watched = graph.history.isWatched(videoId);
		QueueMenu.showEpisode(this, item, watched, () -> {
			graph.history.setWatched(videoId, !watched);
			refreshState();
		});
	}

	private void toggleSaved() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		if (graph.library.contains(id)) {
			graph.library.remove(id);
		} else {
			LibraryItem item = new LibraryItem();
			item.id = id;
			item.type = type;
			item.name = m.nameOrEmpty();
			item.poster = m.poster;
			item.released = m.isSeries() ? ReleaseOrder.latestOut(m, System.currentTimeMillis()) : ReleaseOrder.of(m);
			graph.library.add(item);
		}
		refreshState();
	}

	private void toggleWatched() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		boolean mark = !isAllWatched(m);
		String videoId = m.playableVideoId();
		if (m.isSeries()) {
			graph.history.setWatched(m.episodeIds(), mark);
		} else if (videoId != null) {
			graph.history.setWatched(videoId, mark);
		}
		refreshState();
	}

	@Nullable
	private String seriesId() {
		return Meta.TYPE_SERIES.equals(type) ? id : null;
	}

	private void downloadEpisodes() {
		Meta m = meta;
		if (m == null || !m.isSeries()) {
			return;
		}
		long now = System.currentTimeMillis();
		List<Video> inSeason = SeriesDownload.episodes(m, season, now);
		List<Video> all = SeriesDownload.episodes(m, null, now);
		if (inSeason.isEmpty() && all.isEmpty()) {
			Toast.makeText(this, R.string.download_episodes_none_out, Toast.LENGTH_SHORT).show();
			return;
		}
		String example = SeriesDownload.fileName(m.nameOrEmpty(), 1, 2, ".mkv");
		new MaterialAlertDialogBuilder(this)
				.setTitle(R.string.details_download_episodes)
				.setMessage(getString(R.string.download_episodes_message, example))
				.setPositiveButton(getString(R.string.download_episodes_all, episodeCount(all.size())),
						(d, w) -> requestDownloads(m, all))
				.setNeutralButton(getString(R.string.download_episodes_season, seasonLabel(season),
						episodeCount(inSeason.size())), (d, w) -> requestDownloads(m, inSeason))
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	private String episodeCount(int count) {
		return getResources().getQuantityString(R.plurals.episode_count, count, count);
	}

	private void requestDownloads(Meta m, List<Video> list) {
		if (DownloadFolder.needsStoragePermission(this, graph.prefs)) {
			pendingDownloads = list;
			storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			return;
		}
		startDownloads(m, list);
	}

	private void startDownloads(Meta m, List<Video> list) {
		if (list.isEmpty()) {
			Toast.makeText(this, R.string.download_episodes_none_out, Toast.LENGTH_SHORT).show();
			return;
		}
		if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
				Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
		}
		Context app = getApplicationContext();
		Toast.makeText(app, getResources().getQuantityString(R.plurals.download_episodes_looking, list.size(),
				list.size()), Toast.LENGTH_SHORT).show();
		String name = m.nameOrEmpty();
		SeriesDownload.start(graph.streams, graph.downloads(), m, list, graph.playerPrefs.quality(m.id,
				Network.isUnmetered(app)), v -> name + " · " + Formats.episode(app, v),
				result -> Toast.makeText(app, resultMessage(app, result), Toast.LENGTH_LONG).show());
	}

	@NonNull
	static String resultMessage(@NonNull Context context, @NonNull SeriesDownload.Result r) {
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
}
