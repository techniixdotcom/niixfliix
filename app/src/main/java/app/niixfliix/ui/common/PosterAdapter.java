package app.niixfliix.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import app.niixfliix.R;
import app.niixfliix.addon.Translations;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.ui.Images;

public final class PosterAdapter extends DiffAdapter<PosterItem, RecyclerView.ViewHolder> {

	public interface Listener {
		void onPosterClick(@NonNull PosterItem item);

		boolean onPosterLongClick(@NonNull PosterItem item);
	}

	private static final int TYPE_POSTER = 0;
	private static final int TYPE_HEADER = 1;

	private final boolean grid;
	private final Listener listener;

	public PosterAdapter(boolean grid, @NonNull Listener listener) {
		this.grid = grid;
		this.listener = listener;
	}

	@NonNull
	@Override
	protected Object keyOf(@NonNull PosterItem item) {
		return item.type + "|" + item.id;
	}

	@NonNull
	@Override
	protected Object contentOf(@NonNull PosterItem item) {
		return Arrays.asList(item.name, item.poster, item.progress, item.isNew, item.wholePicture, isWatched(item));
	}

	private static boolean isWatched(@NonNull PosterItem item) {
		return !item.header && !Meta.TYPE_SERIES.equals(item.type) && AppGraph.get().history.isWatched(item.id);
	}

	public void refreshWatched() {
		submit(items());
	}

	/** Shows each poster again as {@code mark} gives it back, such as with or without the "NEW" mark. */
	public void refreshMarks(@NonNull UnaryOperator<PosterItem> mark) {
		List<PosterItem> marked = new ArrayList<>();
		for (PosterItem item : items()) {
			marked.add(mark.apply(item));
		}
		submit(marked);
	}

	@Override
	public int getItemViewType(int position) {
		return getItem(position).header ? TYPE_HEADER : TYPE_POSTER;
	}

	public boolean isHeader(int position) {
		return position >= 0 && position < getItemCount() && getItem(position).header;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		if (viewType == TYPE_HEADER) {
			return new HeaderHolder(LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_poster_header, parent, false));
		}
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poster, parent, false);
		if (grid) {
			view.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
		}
		return new Holder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		if (holder instanceof HeaderHolder) {
			((HeaderHolder) holder).text.setText(getItem(position).name);
		} else {
			((Holder) holder).bind(getItem(position));
		}
	}

	@Override
	public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
		if (holder instanceof Holder) {
			((Holder) holder).recycle();
		}
	}

	static final class HeaderHolder extends RecyclerView.ViewHolder {
		final TextView text;

		HeaderHolder(@NonNull View view) {
			super(view);
			text = (TextView) view;
			ViewCompat.setAccessibilityHeading(view, true);
		}
	}

	final class Holder extends RecyclerView.ViewHolder {
		private final MaterialCardView card;
		private final ImageView poster;
		private final TextView fallback;
		private final TextView title;
		private final LinearProgressIndicator progress;
		private final View newBadge;
		@Nullable private PosterItem item;

		Holder(@NonNull View view) {
			super(view);
			card = view.findViewById(R.id.card);
			poster = view.findViewById(R.id.poster);
			fallback = view.findViewById(R.id.poster_fallback);
			title = view.findViewById(R.id.title);
			progress = view.findViewById(R.id.progress);
			newBadge = view.findViewById(R.id.new_badge);
			card.setOnClickListener(v -> {
				if (item != null) {
					listener.onPosterClick(item);
				}
			});
			card.setOnLongClickListener(v -> item != null && listener.onPosterLongClick(item));
		}

		void bind(PosterItem item) {
			this.item = item;
			Translations translations = AppGraph.get().translations;
			String name = translations.name(item.id);
			if (name == null) {
				name = item.name;
				translations.request(item.id, () -> {
					if (this.item == item) {
						bind(item);
					}
				});
			}
			title.setText(name);
			fallback.setText(item.wholePicture ? null : name);
			if (item.wholePicture) {
				Images.logo(poster, item.poster);
			} else {
				poster.setBackground(null);
				Images.poster(poster, item.poster);
			}
			newBadge.setVisibility(item.isNew ? View.VISIBLE : View.GONE);
			boolean watched = isWatched(item);
			Images.watched(poster, watched);
			title.setAlpha(watched ? Images.WATCHED_ALPHA : 1f);
			fallback.setAlpha(watched ? Images.WATCHED_ALPHA : 1f);
			String spoken = item.isNew ? card.getContext().getString(R.string.poster_new_episode, name)
					: watched ? card.getContext().getString(R.string.poster_seen, name) : name;
			if (item.progress >= 0) {
				int percent = Math.round(item.progress * 100);
				progress.setVisibility(View.VISIBLE);
				progress.setProgress(percent);
				card.setContentDescription(card.getContext().getString(R.string.poster_watched, spoken, percent));
			} else {
				card.setContentDescription(spoken);
				progress.setVisibility(View.GONE);
			}
		}

		void recycle() {
			item = null;
			Images.cancel(poster);
		}
	}
}
