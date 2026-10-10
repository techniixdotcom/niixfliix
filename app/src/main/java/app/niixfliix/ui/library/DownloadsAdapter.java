package app.niixfliix.ui.library;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.ui.Formats;

final class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.Holder> {

	interface Listener {
		void onDownloadClick(@NonNull DownloadItem item);

		void onDownloadToggle(@NonNull DownloadItem item);

		void onDownloadDelete(@NonNull DownloadItem item);
	}

	private final Listener listener;
	private final List<DownloadItem> items = new ArrayList<>();

	DownloadsAdapter(@NonNull Listener listener) {
		this.listener = listener;
	}

	void submit(@NonNull List<DownloadItem> newItems) {
		items.clear();
		items.addAll(newItems);
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false));
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
		private final TextView title;
		private final TextView status;
		private final LinearProgressIndicator progress;
		private final ImageButton toggle;
		@Nullable private DownloadItem item;

		Holder(@NonNull View view) {
			super(view);
			title = view.findViewById(R.id.download_title);
			status = view.findViewById(R.id.download_status);
			progress = view.findViewById(R.id.download_progress);
			toggle = view.findViewById(R.id.download_toggle);
			ImageButton delete = view.findViewById(R.id.download_delete);
			view.setOnClickListener(v -> {
				if (item != null) {
					listener.onDownloadClick(item);
				}
			});
			toggle.setOnClickListener(v -> {
				if (item != null) {
					listener.onDownloadToggle(item);
				}
			});
			delete.setOnClickListener(v -> {
				if (item != null) {
					listener.onDownloadDelete(item);
				}
			});
		}

		void bind(DownloadItem item) {
			this.item = item;
			Context context = itemView.getContext();
			title.setText(item.title);
			status.setText(statusText(context, item));
			boolean active = item.isActive();
			// can only switch modes while hidden
			progress.setVisibility(View.GONE);
			progress.setIndeterminate(active && item.bytesTotal <= 0);
			progress.setProgress(item.percent());
			progress.setVisibility(item.state == DownloadItem.State.DONE ? View.GONE : View.VISIBLE);
			toggle.setVisibility(item.state == DownloadItem.State.DONE ? View.GONE : View.VISIBLE);
			toggle.setImageResource(active ? R.drawable.ic_pause : R.drawable.ic_download);
			toggle.setContentDescription(context.getString(active ? R.string.download_pause : R.string.download_resume));
		}

		private String statusText(Context context, DownloadItem item) {
			String done = Formats.size(context, item.bytesDone);
			switch (item.state) {
				case DONE:
					return context.getString(R.string.download_done, done);
				case PAUSED:
					return context.getString(R.string.download_paused, done);
				case FAILED:
					return context.getString(R.string.download_failed);
				case QUEUED:
					return context.getString(R.string.download_queued);
				case RUNNING:
				default:
					if (item.bytesTotal > 0) {
						return context.getString(R.string.download_progress, item.percent(), done,
								Formats.size(context, item.bytesTotal));
					}
					return done;
			}
		}
	}
}
