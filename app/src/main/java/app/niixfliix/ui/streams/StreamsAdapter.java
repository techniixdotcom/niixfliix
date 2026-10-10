package app.niixfliix.ui.streams;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.ui.Formats;

/**
 * One row per quality: the quality, the title of what's playing, and size/language chips.
 * Where a stream comes from (add-on, release name, seeders) stays out of the list on purpose.
 */
final class StreamsAdapter extends RecyclerView.Adapter<StreamsAdapter.Holder> {

	interface Listener {
		void onStreamClick(@NonNull StreamInfo stream);

		void onStreamLongClick(@NonNull StreamInfo stream);
	}

	private final Listener listener;
	private final String title;
	private final List<StreamInfo> rows = new ArrayList<>();

	/** @param title movie name, or "Show · S1E2" for an episode */
	StreamsAdapter(@NonNull Listener listener, @NonNull String title) {
		this.listener = listener;
		this.title = title;
	}

	void submit(@NonNull List<StreamInfo> best) {
		rows.clear();
		rows.addAll(best);
		notifyDataSetChanged();
	}

	@Override
	public int getItemCount() {
		return rows.size();
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stream, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull Holder holder, int position) {
		holder.bind(rows.get(position));
	}

	final class Holder extends RecyclerView.ViewHolder {
		private final TextView quality;
		private final TextView name;
		private final ChipGroup chips;
		@Nullable private StreamInfo stream;

		Holder(@NonNull View view) {
			super(view);
			quality = view.findViewById(R.id.stream_quality);
			name = view.findViewById(R.id.stream_title);
			chips = view.findViewById(R.id.stream_chips);
			view.setOnClickListener(v -> {
				if (stream != null) {
					listener.onStreamClick(stream);
				}
			});
			view.setOnLongClickListener(v -> {
				if (stream != null) {
					listener.onStreamLongClick(stream);
				}
				return true;
			});
		}

		void bind(StreamInfo s) {
			stream = s;
			Context context = itemView.getContext();
			quality.setText(s.quality.title);
			name.setText(title);

			chips.removeAllViews();
			if (s.sizeBytes > 0) {
				addChip(Formats.size(context, s.sizeBytes));
			}
			for (String language : s.languages) {
				addChip(language);
			}
			for (String tag : s.tags) {
				addChip(tag);
			}
			chips.setVisibility(chips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
		}

		private void addChip(String text) {
			Chip chip = (Chip) LayoutInflater.from(chips.getContext()).inflate(R.layout.item_info_chip, chips, false);
			chip.setText(text);
			chips.addView(chip);
		}
	}
}
