package app.niixfliix.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.ui.Images;

final class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {

	interface Listener {
		void onQueueClick(@NonNull QueueItem item);

		void onQueueRemove(@NonNull QueueItem item);
	}

	private final Listener listener;
	private final List<QueueItem> items = new ArrayList<>();

	QueueAdapter(@NonNull Listener listener) {
		this.listener = listener;
	}

	void submit(@NonNull List<QueueItem> newItems) {
		items.clear();
		items.addAll(newItems);
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue, parent, false));
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
		private final ImageView poster;
		private final TextView title;
		@Nullable private QueueItem item;

		Holder(@NonNull View view) {
			super(view);
			poster = view.findViewById(R.id.queue_poster);
			title = view.findViewById(R.id.queue_title);
			view.setOnClickListener(v -> {
				if (item != null) {
					listener.onQueueClick(item);
				}
			});
			view.findViewById(R.id.queue_remove).setOnClickListener(v -> {
				if (item != null) {
					listener.onQueueRemove(item);
				}
			});
		}

		void bind(QueueItem queued) {
			item = queued;
			title.setText(queued.label());
			Images.poster(poster, queued.poster);
		}
	}
}
