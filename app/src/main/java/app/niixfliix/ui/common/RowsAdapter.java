package app.niixfliix.ui.common;

import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.niixfliix.R;

/** Home/Search rows. Each row loads the first time it's bound. */
public final class RowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

	public enum State {
		LOADING,
		READY,
		EMPTY,
		FAILED
	}

	public static final class Row {
		@NonNull public final String key;
		@NonNull public final String title;
		@Nullable public final String subtitle;
		@NonNull public State state = State.LOADING;
		@NonNull public List<PosterItem> items = new ArrayList<>();
		boolean requested;

		public Row(@NonNull String key, @NonNull String title, @Nullable String subtitle) {
			this.key = key;
			this.title = title;
			this.subtitle = subtitle;
		}
	}

	public static final class Notice {
		@NonNull public final String text;
		@Nullable public final String action;
		@Nullable public final Runnable onAction;

		public Notice(@NonNull String text, @Nullable String action, @Nullable Runnable onAction) {
			this.text = text;
			this.action = action;
			this.onAction = onAction;
		}
	}

	public interface Listener extends PosterAdapter.Listener {
		/** call update(row) when done; called again on retry */
		void onLoadRow(@NonNull Row row);
	}

	private static final int TYPE_NOTICE = 0;
	private static final int TYPE_ROW = 1;

	private final Listener listener;
	private final RecyclerView.RecycledViewPool posterPool = new RecyclerView.RecycledViewPool();
	private final Map<String, Parcelable> scrollStates = new HashMap<>();
	@Nullable private Notice notice;
	private final List<Row> rows = new ArrayList<>();

	public RowsAdapter(@NonNull Listener listener) {
		this.listener = listener;
	}

	public void setContent(@Nullable Notice notice, @NonNull List<Row> newRows) {
		this.notice = notice;
		rows.clear();
		rows.addAll(newRows);
		notifyDataSetChanged();
	}

	public void update(@NonNull Row row) {
		int index = rows.indexOf(row);
		if (index >= 0) {
			notifyItemChanged(index + offset());
		}
	}

	public void add(@NonNull Row row) {
		rows.add(row);
		notifyItemInserted(rows.size() - 1 + offset());
	}

	public boolean isEmpty() {
		return rows.isEmpty() && notice == null;
	}

	private int offset() {
		return notice != null ? 1 : 0;
	}

	@Override
	public int getItemViewType(int position) {
		return notice != null && position == 0 ? TYPE_NOTICE : TYPE_ROW;
	}

	@Override
	public int getItemCount() {
		return rows.size() + offset();
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		if (viewType == TYPE_NOTICE) {
			return new NoticeHolder(inflater.inflate(R.layout.item_notice, parent, false));
		}
		return new RowHolder(inflater.inflate(R.layout.item_catalog_row, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		if (holder instanceof NoticeHolder) {
			((NoticeHolder) holder).bind(notice);
			return;
		}
		Row row = rows.get(position - offset());
		((RowHolder) holder).bind(row);
		if (!row.requested && row.state == State.LOADING) {
			row.requested = true;
			listener.onLoadRow(row);
		}
	}

	@Override
	public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
		if (holder instanceof RowHolder) {
			((RowHolder) holder).saveScroll();
		}
	}

	static final class NoticeHolder extends RecyclerView.ViewHolder {
		private final TextView text;
		private final Button action;

		NoticeHolder(@NonNull View view) {
			super(view);
			text = view.findViewById(R.id.notice_text);
			action = view.findViewById(R.id.notice_action);
		}

		void bind(@Nullable Notice notice) {
			if (notice == null) {
				return;
			}
			text.setText(notice.text);
			if (notice.action != null && notice.onAction != null) {
				action.setVisibility(View.VISIBLE);
				action.setText(notice.action);
				action.setOnClickListener(v -> notice.onAction.run());
			} else {
				action.setVisibility(View.GONE);
			}
		}
	}

	final class RowHolder extends RecyclerView.ViewHolder {
		private final TextView title;
		private final TextView subtitle;
		private final TextView status;
		private final View loading;
		private final RecyclerView list;
		private final PosterAdapter adapter;
		@Nullable private Row row;

		RowHolder(@NonNull View view) {
			super(view);
			title = view.findViewById(R.id.row_title);
			subtitle = view.findViewById(R.id.row_subtitle);
			status = view.findViewById(R.id.row_status);
			loading = view.findViewById(R.id.row_loading);
			list = view.findViewById(R.id.row_list);
			adapter = new PosterAdapter(false, listener);
			list.setLayoutManager(new LinearLayoutManager(view.getContext(), RecyclerView.HORIZONTAL, false));
			list.setRecycledViewPool(posterPool);
			list.setAdapter(adapter);
			status.setOnClickListener(v -> {
				if (row != null && row.state == State.FAILED) {
					row.state = State.LOADING;
					bind(row);
					listener.onLoadRow(row);
				}
			});
		}

		void bind(Row row) {
			if (this.row != null && this.row != row) {
				saveScroll();
			}
			this.row = row;
			title.setText(row.title);
			subtitle.setText(row.subtitle);
			subtitle.setVisibility(row.subtitle == null || row.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
			loading.setVisibility(row.state == State.LOADING ? View.VISIBLE : View.GONE);
			list.setVisibility(row.state == State.READY ? View.VISIBLE : View.GONE);
			switch (row.state) {
				case FAILED:
					status.setVisibility(View.VISIBLE);
					status.setText(R.string.row_failed);
					break;
				case EMPTY:
					status.setVisibility(View.VISIBLE);
					status.setText(R.string.row_empty);
					break;
				default:
					status.setVisibility(View.GONE);
					break;
			}
			adapter.submit(row.items);
			Parcelable saved = scrollStates.get(row.key);
			RecyclerView.LayoutManager lm = list.getLayoutManager();
			if (saved != null && lm != null) {
				lm.onRestoreInstanceState(saved);
			} else {
				list.scrollToPosition(0);
			}
		}

		void saveScroll() {
			RecyclerView.LayoutManager lm = list.getLayoutManager();
			if (row != null && lm != null) {
				scrollStates.put(row.key, lm.onSaveInstanceState());
			}
		}
	}
}
