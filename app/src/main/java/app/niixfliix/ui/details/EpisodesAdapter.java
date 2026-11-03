package app.niixfliix.ui.details;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Arrays;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.addon.model.Video;
import app.niixfliix.data.History;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.Images;
import app.niixfliix.ui.common.DiffAdapter;

final class EpisodesAdapter extends DiffAdapter<Video, EpisodesAdapter.Holder> {

	interface Listener {
		void onEpisodeClick(@NonNull Video video);

		void onEpisodeLongClick(@NonNull Video video);
	}

	private final Listener listener;
	private final History history;
	@Nullable private String nextUpId;

	EpisodesAdapter(@NonNull Listener listener, @NonNull History history) {
		this.listener = listener;
		this.history = history;
	}

	void submit(@NonNull List<Video> list, @Nullable String nextUpId) {
		this.nextUpId = nextUpId;
		submit(list);
	}

	@NonNull
	@Override
	protected Object keyOf(@NonNull Video video) {
		return video.id != null ? video.id : video.seasonOrZero() + ":" + video.episodeOrZero();
	}

	@NonNull
	@Override
	protected Object contentOf(@NonNull Video video) {
		boolean isWatched = video.id != null && history.isWatched(video.id);
		HistoryEntry entry = video.id != null ? history.get(video.id) : null;
		int percent = entry != null ? Math.round(entry.progress() * 100) : -1;
		boolean isNext = video.id != null && video.id.equals(nextUpId);
		return Arrays.asList(video.displayTitle(), video.season, video.episode, video.released, video.thumbnail,
				isWatched, percent, isNext);
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull Holder holder, int position) {
		holder.bind(getItem(position));
	}

	final class Holder extends RecyclerView.ViewHolder {
		private final ImageView thumb;
		private final TextView title;
		private final TextView date;
		private final TextView next;
		private final ImageView watched;
		private final LinearProgressIndicator progress;
		private final int defaultBackground;
		@Nullable private Video video;

		Holder(@NonNull View view) {
			super(view);
			thumb = view.findViewById(R.id.episode_thumb);
			title = view.findViewById(R.id.episode_title);
			date = view.findViewById(R.id.episode_date);
			next = view.findViewById(R.id.episode_next);
			watched = view.findViewById(R.id.episode_watched);
			progress = view.findViewById(R.id.episode_progress);
			TypedValue value = new TypedValue();
			view.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
			defaultBackground = value.resourceId;
			view.setOnClickListener(v -> {
				if (video != null) {
					listener.onEpisodeClick(video);
				}
			});
			view.setOnLongClickListener(v -> {
				if (video != null) {
					listener.onEpisodeLongClick(video);
				}
				return true;
			});
		}

		void bind(Video video) {
			this.video = video;
			Context context = itemView.getContext();
			title.setText(Formats.episode(context, video));
			date.setText(Formats.day(video.released));
			Images.poster(thumb, video.thumbnail);
			boolean isWatched = video.id != null && history.isWatched(video.id);
			watched.setVisibility(isWatched ? View.VISIBLE : View.INVISIBLE);
			Images.watched(thumb, isWatched);
			title.setAlpha(isWatched ? Images.WATCHED_ALPHA : 1f);
			boolean isNext = video.id != null && video.id.equals(nextUpId);
			next.setVisibility(isNext ? View.VISIBLE : View.GONE);
			itemView.setBackgroundResource(isNext ? R.drawable.bg_next_episode : defaultBackground);
			HistoryEntry entry = video.id != null ? history.get(video.id) : null;
			if (entry != null && !isWatched && entry.progress() > 0) {
				progress.setVisibility(View.VISIBLE);
				progress.setProgress(Math.round(entry.progress() * 100));
			} else {
				progress.setVisibility(View.GONE);
			}
		}
	}
}
