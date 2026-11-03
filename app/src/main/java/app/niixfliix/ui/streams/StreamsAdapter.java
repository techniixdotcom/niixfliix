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
import java.util.Arrays;

import app.niixfliix.R;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.common.DiffAdapter;

final class StreamsAdapter extends DiffAdapter<StreamInfo, StreamsAdapter.Holder> {

	interface Listener {
		void onStreamClick(@NonNull StreamInfo stream);

		void onStreamLongClick(@NonNull StreamInfo stream);
	}

	private final Listener listener;
	private final String title;

	StreamsAdapter(@NonNull Listener listener, @NonNull String title) {
		this.listener = listener;
		this.title = title;
	}

	@NonNull
	@Override
	protected Object keyOf(@NonNull StreamInfo stream) {
		return stream.quality;
	}

	@NonNull
	@Override
	protected Object contentOf(@NonNull StreamInfo stream) {
		return Arrays.asList(stream.key(), stream.sizeBytes, new ArrayList<>(stream.languages),
				new ArrayList<>(stream.tags));
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stream, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull Holder holder, int position) {
		holder.bind(getItem(position));
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
			quality.setText(Formats.quality(quality.getContext(), s.quality));
			name.setText(title);

			chips.removeAllViews();
			if (!StreamRanker.isSmooth(s)) {
				addChip(context.getString(R.string.stream_software_decoding));
			}
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
