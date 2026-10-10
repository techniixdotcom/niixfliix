package app.niixfliix.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.addon.Translations;
import app.niixfliix.app.AppGraph;
import app.niixfliix.ui.Images;

public final class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {

	public interface Listener {
		void onPosterClick(@NonNull PosterItem item);

		boolean onPosterLongClick(@NonNull PosterItem item);
	}

	private final boolean grid;
	private final Listener listener;
	private final List<PosterItem> items = new ArrayList<>();

	public PosterAdapter(boolean grid, @NonNull Listener listener) {
		this.grid = grid;
		this.listener = listener;
	}

	public void submit(@NonNull List<PosterItem> newItems) {
		items.clear();
		items.addAll(newItems);
		notifyDataSetChanged();
	}

	public void append(@NonNull List<PosterItem> more) {
		int start = items.size();
		items.addAll(more);
		notifyItemRangeInserted(start, more.size());
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poster, parent, false);
		if (grid) {
			view.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
		}
		return new Holder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull Holder holder, int position) {
		holder.bind(items.get(position));
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	final class Holder extends RecyclerView.ViewHolder {
		private final MaterialCardView card;
		private final ImageView poster;
		private final TextView title;
		private final LinearProgressIndicator progress;
		@Nullable private PosterItem item;

		Holder(@NonNull View view) {
			super(view);
			card = view.findViewById(R.id.card);
			poster = view.findViewById(R.id.poster);
			title = view.findViewById(R.id.title);
			progress = view.findViewById(R.id.progress);
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
					// the holder may show another poster by now
					if (this.item == item) {
						bind(item);
					}
				});
			}
			title.setText(name);
			card.setContentDescription(name);
			Images.poster(poster, item.poster);
			if (item.progress >= 0) {
				progress.setVisibility(View.VISIBLE);
				progress.setProgress(Math.round(item.progress * 100));
			} else {
				progress.setVisibility(View.GONE);
			}
		}
	}
}
