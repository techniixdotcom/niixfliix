package app.niixfliix.ui.common;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DiffAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

	private static final Object CHANGED = new Object();

	private List<T> items = new ArrayList<>();
	private List<Object> keys = new ArrayList<>();
	private List<Object> contents = new ArrayList<>();

	@NonNull
	protected abstract Object keyOf(@NonNull T item);

	@NonNull
	protected abstract Object contentOf(@NonNull T item);

	public void submit(@NonNull List<? extends T> newItems) {
		List<T> nextItems = new ArrayList<>(newItems);
		List<Object> nextKeys = new ArrayList<>(nextItems.size());
		List<Object> nextContents = new ArrayList<>(nextItems.size());
		for (T item : nextItems) {
			nextKeys.add(keyOf(item));
			nextContents.add(contentOf(item));
		}
		List<Object> oldKeys = keys;
		List<Object> oldContents = contents;
		DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
			@Override
			public int getOldListSize() {
				return oldKeys.size();
			}

			@Override
			public int getNewListSize() {
				return nextKeys.size();
			}

			@Override
			public boolean areItemsTheSame(int oldPosition, int newPosition) {
				return oldKeys.get(oldPosition).equals(nextKeys.get(newPosition));
			}

			@Override
			public boolean areContentsTheSame(int oldPosition, int newPosition) {
				return oldContents.get(oldPosition).equals(nextContents.get(newPosition));
			}

			@Nullable
			@Override
			public Object getChangePayload(int oldPosition, int newPosition) {
				return CHANGED;
			}
		}, false);
		items = nextItems;
		keys = nextKeys;
		contents = nextContents;
		diff.dispatchUpdatesTo(this);
	}

	@SuppressLint("NotifyDataSetChanged")
	public void replace(@NonNull List<? extends T> newItems) {
		items = new ArrayList<>(newItems);
		keys = new ArrayList<>(items.size());
		contents = new ArrayList<>(items.size());
		for (T item : items) {
			keys.add(keyOf(item));
			contents.add(contentOf(item));
		}
		notifyDataSetChanged();
	}

	public void append(@NonNull List<? extends T> more) {
		if (more.isEmpty()) {
			return;
		}
		int start = items.size();
		for (T item : more) {
			items.add(item);
			keys.add(keyOf(item));
			contents.add(contentOf(item));
		}
		notifyItemRangeInserted(start, more.size());
	}

	public void redraw(int position) {
		notifyItemChanged(position, CHANGED);
	}

	public void move(int from, int to) {
		if (from == to || from < 0 || to < 0 || from >= items.size() || to >= items.size()) {
			return;
		}
		items.add(to, items.remove(from));
		keys.add(to, keys.remove(from));
		contents.add(to, contents.remove(from));
		notifyItemMoved(from, to);
	}

	@NonNull
	protected T getItem(int position) {
		return items.get(position);
	}

	@NonNull
	public List<T> items() {
		return Collections.unmodifiableList(items);
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	@Override
	public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
		onBindViewHolder(holder, position);
	}
}
