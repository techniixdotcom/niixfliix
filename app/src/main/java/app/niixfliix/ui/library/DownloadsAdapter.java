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

import java.util.Arrays;

import app.niixfliix.R;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.common.DiffAdapter;

final class DownloadsAdapter extends DiffAdapter<DownloadItem, DownloadsAdapter.Holder> {

	interface Listener {
		void onDownloadClick(@NonNull DownloadItem item);

		void onDownloadToggle(@NonNull DownloadItem item);

		void onDownloadDelete(@NonNull DownloadItem item);
	}

	private final Listener listener;

	DownloadsAdapter(@NonNull Listener listener) {
		this.listener = listener;
	}

	@NonNull
	@Override
	protected Object keyOf(@NonNull DownloadItem item) {
		return item.id;
	}

	@NonNull
	@Override
	protected Object contentOf(@NonNull DownloadItem item) {
		return Arrays.asList(item.title, item.state, item.bytesDone, item.bytesTotal);
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull Holder holder, int position) {
		holder.bind(getItem(position));
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
			boolean indeterminate = active && item.bytesTotal <= 0;
			if (progress.isIndeterminate() != indeterminate) {
				progress.setVisibility(View.GONE);
				progress.setIndeterminate(indeterminate);
			}
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
