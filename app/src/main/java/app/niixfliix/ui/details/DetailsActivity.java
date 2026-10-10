package app.niixfliix.ui.details;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.niixfliix.R;
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.ui.BaseActivity;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.Images;
import app.niixfliix.ui.Links;
import app.niixfliix.ui.WindowPadding;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.streams.StreamsSheet;

public final class DetailsActivity extends BaseActivity implements StreamsSheet.Host, EpisodesAdapter.Listener {

	private static final String EXTRA_TYPE = "type";
	private static final String EXTRA_ID = "id";
	private static final String EXTRA_NAME = "name";
	private static final String EXTRA_POSTER = "poster";
	private static final int MAX_CAST = 12;

	private final AppGraph graph = AppGraph.get();
	private String type;
	private String id;
	@Nullable private Meta meta;
	private int season = -1;

	private ImageView backdrop;
	private ImageView logo;
	private TextView title;
	private TextView info;
	private ChipGroup genres;
	private MaterialButton play;
	private MaterialButton save;
	private MaterialButton watched;
	private MaterialButton trailer;
	private TextView description;
	private TextView cast;
	private TextView notice;
	private View seriesSection;
	private MaterialAutoCompleteTextView seasonInput;
	private View loading;
	private EpisodesAdapter episodes;

	public static void start(@NonNull Context context, @NonNull PosterItem item) {
		if (item.id.isEmpty() || item.type.isEmpty()) {
			return;
		}
		context.startActivity(new Intent(context, DetailsActivity.class)
				.putExtra(EXTRA_TYPE, item.type)
				.putExtra(EXTRA_ID, item.id)
				.putExtra(EXTRA_NAME, item.name)
				.putExtra(EXTRA_POSTER, item.poster));
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_details);
		type = getIntent().getStringExtra(EXTRA_TYPE);
		id = getIntent().getStringExtra(EXTRA_ID);
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
		trailer = findViewById(R.id.trailer);
		description = findViewById(R.id.description);
		cast = findViewById(R.id.cast);
		notice = findViewById(R.id.notice);
		seriesSection = findViewById(R.id.series_section);
		seasonInput = findViewById(R.id.season_input);
		loading = findViewById(R.id.loading);
		View back = findViewById(R.id.back);
		back.setOnClickListener(v -> finish());
		WindowPadding.apply(back, WindowPadding.TOP | WindowPadding.LEFT);
		WindowPadding.apply(findViewById(R.id.content), WindowPadding.BOTTOM | WindowPadding.LEFT | WindowPadding.RIGHT);
		WindowPadding.apply(findViewById(R.id.mini_player), WindowPadding.BOTTOM | WindowPadding.LEFT | WindowPadding.RIGHT);

		RecyclerView list = findViewById(R.id.episodes);
		list.setLayoutManager(new LinearLayoutManager(this));
		episodes = new EpisodesAdapter(this, graph.history);
		list.setAdapter(episodes);

		play.setOnClickListener(v -> onPlay());
		save.setOnClickListener(v -> toggleSaved());
		watched.setOnClickListener(v -> toggleWatched());
		trailer.setOnClickListener(v -> {
			String yt = meta != null ? meta.trailerYoutubeId() : null;
			if (yt != null) {
				Links.open(this, Links.youtube(yt));
			}
		});
		seasonInput.setOnItemClickListener((parent, view, position, rowId) -> {
			List<Integer> seasons = seasonNumbers();
			if (position < seasons.size()) {
				season = seasons.get(position);
				showEpisodes();
			}
		});

		bind(metaFromIntent(), false);
		load();
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshState();
	}

	@Nullable
	@Override
	public Meta streamsMeta() {
		return meta;
	}

	private void load() {
		graph.io.execute(() -> {
			Meta found = MetaFinder.find(graph.addons, graph.addonClient, type, id);
			Meta result = found != null ? graph.translations.translate(found) : null;
			graph.main.post(() -> {
				if (isDestroyed()) {
					return;
				}
				loading.setVisibility(View.GONE);
				if (result != null) {
					bind(result, true);
				} else {
					// streams only need the id
					bind(metaFromIntent(), true);
					notice.setText(R.string.details_no_meta);
					notice.setVisibility(View.VISIBLE);
				}
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
		if (m.cast != null && !m.cast.isEmpty()) {
			List<String> names = m.cast.subList(0, Math.min(MAX_CAST, m.cast.size()));
			cast.setText(getString(R.string.details_cast, String.join(", ", names)));
			cast.setVisibility(View.VISIBLE);
		} else {
			cast.setVisibility(View.GONE);
		}
		trailer.setVisibility(m.trailerYoutubeId() != null ? View.VISIBLE : View.GONE);
		play.setEnabled(complete);
		watched.setEnabled(complete);
		if (complete) {
			refreshState();
		}
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
}
