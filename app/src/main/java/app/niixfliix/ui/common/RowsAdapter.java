package app.niixfliix.ui.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import app.niixfliix.R;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.Images;

public final class RowsAdapter extends DiffAdapter<Object, RecyclerView.ViewHolder> {

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
		@Nullable public String emptyText;
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

	public static final class Hero {
		@NonNull public final HomeHero hero;
		public final boolean saved;
		public final int count;
		public final int index;
		@NonNull public final Runnable onPlay;
		@NonNull public final Runnable onSave;
		@NonNull public final Runnable onOpen;
		@NonNull public final IntConsumer onPick;

		public Hero(@NonNull HomeHero hero, boolean saved, int count, int index, @NonNull Runnable onPlay,
				@NonNull Runnable onSave, @NonNull Runnable onOpen, @NonNull IntConsumer onPick) {
			this.hero = hero;
			this.saved = saved;
			this.count = count;
			this.index = index;
			this.onPlay = onPlay;
			this.onSave = onSave;
			this.onOpen = onOpen;
			this.onPick = onPick;
		}
	}

	public interface Listener extends PosterAdapter.Listener {
		void onLoadRow(@NonNull Row row);
	}

	private static final int TYPE_NOTICE = 0;
	private static final int TYPE_ROW = 1;
	private static final int TYPE_HERO = 2;
	private static final String NOTICE_KEY = "\u0000notice";
	private static final String HERO_KEY = "\u0000hero";
	private static final float HERO_WIDTH_RATIO = 1.25f;
	private static final float HERO_HEIGHT_SHARE = 0.68f;
	private static final long HERO_FADE_MS = 220;
	private static final long PULSE_MS = 800;

	private final Listener listener;
	private final RecyclerView.RecycledViewPool posterPool = new RecyclerView.RecycledViewPool();
	private final Map<String, Parcelable> scrollStates = new HashMap<>();

	public RowsAdapter(@NonNull Listener listener) {
		this.listener = listener;
	}

	public void setContent(@Nullable Notice notice, @NonNull List<Row> newRows) {
		setContent(null, notice, newRows);
	}

	public void setContent(@Nullable Hero hero, @Nullable Notice notice, @NonNull List<Row> newRows) {
		List<Object> entries = new ArrayList<>(newRows.size() + 2);
		if (hero != null) {
			entries.add(hero);
		}
		if (notice != null) {
			entries.add(notice);
		}
		entries.addAll(newRows);
		submit(entries);
	}

	public void update(@NonNull Row row) {
		int index = items().indexOf(row);
		if (index >= 0) {
			redraw(index);
		}
	}

	public void refreshWatched() {
		for (int i = 0; i < getItemCount(); i++) {
			if (getItem(i) instanceof Row) {
				redraw(i);
			}
		}
	}

	public void clear() {
		replace(Collections.emptyList());
	}

	public void add(@NonNull Row row) {
		append(Collections.singletonList(row));
	}

	public boolean isEmpty() {
		return getItemCount() == 0;
	}

	@Nullable
	public Row row(@NonNull String key) {
		for (Object entry : items()) {
			if (entry instanceof Row && ((Row) entry).key.equals(key)) {
				return (Row) entry;
			}
		}
		return null;
	}

	@NonNull
	@Override
	protected Object keyOf(@NonNull Object entry) {
		if (entry instanceof Hero) {
			return HERO_KEY;
		}
		return entry instanceof Row ? ((Row) entry).key : NOTICE_KEY;
	}

	@NonNull
	@Override
	protected Object contentOf(@NonNull Object entry) {
		if (entry instanceof Row) {
			Row row = (Row) entry;
			return Arrays.asList(row, row.state, row.items.size());
		}
		if (entry instanceof Hero) {
			Hero hero = (Hero) entry;
			return Arrays.asList(hero.hero.item.id, hero.hero.meta, hero.saved, hero.count, hero.index);
		}
		Notice notice = (Notice) entry;
		return Arrays.asList(notice.text, notice.action);
	}

	@Override
	public int getItemViewType(int position) {
		Object entry = getItem(position);
		if (entry instanceof Hero) {
			return TYPE_HERO;
		}
		return entry instanceof Notice ? TYPE_NOTICE : TYPE_ROW;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		if (viewType == TYPE_NOTICE) {
			return new NoticeHolder(inflater.inflate(R.layout.item_notice, parent, false));
		}
		if (viewType == TYPE_HERO) {
			return new HeroHolder(inflater.inflate(R.layout.item_home_hero, parent, false));
		}
		return new RowHolder(inflater.inflate(R.layout.item_catalog_row, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		Object entry = getItem(position);
		if (holder instanceof NoticeHolder) {
			((NoticeHolder) holder).bind((Notice) entry);
			return;
		}
		if (holder instanceof HeroHolder) {
			((HeroHolder) holder).bind((Hero) entry);
			return;
		}
		Row row = (Row) entry;
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
			((RowHolder) holder).pulse(false);
		}
	}

	@Override
	public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
		if (holder instanceof RowHolder) {
			((RowHolder) holder).pulse(true);
		}
	}

	@Override
	public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
		if (holder instanceof RowHolder) {
			((RowHolder) holder).pulse(false);
		}
	}

	static final class HeroHolder extends RecyclerView.ViewHolder {
		private final ImageView backdrop;
		private final ImageView logo;
		private final TextView type;
		private final TextView title;
		private final TextView genres;
		private final MaterialButton play;
		private final MaterialButton save;
		private final ViewGroup dots;
		private final View art;
		private final View text;
		@Nullable private String shownId;
		@Nullable private Object shownMeta;

		HeroHolder(@NonNull View view) {
			super(view);
			backdrop = view.findViewById(R.id.hero_backdrop);
			logo = view.findViewById(R.id.hero_logo);
			type = view.findViewById(R.id.hero_type);
			title = view.findViewById(R.id.hero_title);
			genres = view.findViewById(R.id.hero_genres);
			play = view.findViewById(R.id.hero_play);
			save = view.findViewById(R.id.hero_save);
			dots = view.findViewById(R.id.hero_dots);
			art = backdrop;
			text = view.findViewById(R.id.hero_text);
			DisplayMetrics dm = view.getResources().getDisplayMetrics();
			int height = Math.round(Math.min(dm.widthPixels * HERO_WIDTH_RATIO, dm.heightPixels * HERO_HEIGHT_SHARE));
			ViewGroup.LayoutParams lp = view.getLayoutParams();
			lp.height = height;
			view.setLayoutParams(lp);
		}

		void bind(@NonNull Hero entry) {
			HomeHero hero = entry.hero;
			String name = hero.name();
			itemView.setContentDescription(name);
			itemView.setOnClickListener(v -> entry.onOpen.run());
			play.setOnClickListener(v -> entry.onPlay.run());
			save.setOnClickListener(v -> entry.onSave.run());
			save.setText(entry.saved ? R.string.hero_saved : R.string.hero_save);
			save.setIconResource(entry.saved ? R.drawable.ic_check : R.drawable.ic_add);
			type.setText(Formats.typeLabel(itemView.getContext(), hero.item.type));
			String line = hero.genres();
			genres.setText(line);
			genres.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
			bindDots(entry);
			if (hero.item.id.equals(shownId) && hero.meta == shownMeta) {
				return;
			}
			boolean changedTitle = shownId != null && !hero.item.id.equals(shownId);
			shownId = hero.item.id;
			shownMeta = hero.meta;
			if (changedTitle && ValueAnimator.areAnimatorsEnabled()) {
				art.animate().cancel();
				text.animate().cancel();
				art.animate().alpha(0f).setDuration(HERO_FADE_MS).withEndAction(() -> {
					showArt(hero, name);
					art.animate().alpha(1f).setDuration(HERO_FADE_MS).start();
				}).start();
				text.animate().alpha(0f).setDuration(HERO_FADE_MS)
						.withEndAction(() -> text.animate().alpha(1f).setDuration(HERO_FADE_MS).start()).start();
				return;
			}
			art.setAlpha(1f);
			text.setAlpha(1f);
			showArt(hero, name);
		}

		private void bindDots(@NonNull Hero entry) {
			if (entry.count <= 1) {
				dots.setVisibility(View.GONE);
				dots.removeAllViews();
				return;
			}
			dots.setVisibility(View.VISIBLE);
			if (dots.getChildCount() != entry.count) {
				dots.removeAllViews();
				LayoutInflater inflater = LayoutInflater.from(dots.getContext());
				for (int i = 0; i < entry.count; i++) {
					inflater.inflate(R.layout.item_hero_dot, dots, true);
				}
			}
			for (int i = 0; i < dots.getChildCount(); i++) {
				View dot = dots.getChildAt(i);
				int index = i;
				dot.setSelected(i == entry.index);
				dot.setContentDescription(dots.getContext().getString(R.string.hero_show_title, i + 1, entry.count));
				dot.setOnClickListener(v -> entry.onPick.accept(index));
			}
		}

		private void showArt(@NonNull HomeHero hero, @NonNull String name) {
			Images.backdrop(backdrop, hero.backdrop());
			title.setText(name);
			if (hero.logo() != null) {
				logo.setVisibility(View.VISIBLE);
				title.setVisibility(View.GONE);
				Images.logo(logo, hero.logo(), () -> {
					logo.setVisibility(View.GONE);
					title.setVisibility(View.VISIBLE);
				});
			} else {
				Images.cancel(logo);
				logo.setVisibility(View.GONE);
				title.setVisibility(View.VISIBLE);
			}
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

		void bind(@NonNull Notice notice) {
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
		@Nullable private ObjectAnimator pulse;
		@Nullable private Row row;

		RowHolder(@NonNull View view) {
			super(view);
			title = view.findViewById(R.id.row_title);
			subtitle = view.findViewById(R.id.row_subtitle);
			status = view.findViewById(R.id.row_status);
			ViewGroup placeholders = view.findViewById(R.id.row_loading);
			loading = placeholders;
			fillPlaceholders(placeholders);
			list = view.findViewById(R.id.row_list);
			adapter = new PosterAdapter(false, listener);
			LinearLayoutManager layout = new LinearLayoutManager(view.getContext(), RecyclerView.HORIZONTAL, false);
			layout.setInitialPrefetchItemCount(4);
			list.setLayoutManager(layout);
			list.setRecycledViewPool(posterPool);
			list.setAdapter(adapter);
			HorizontalSwipe.attach(list);
			Images.pauseWhileFlinging(list);
			status.setOnClickListener(v -> {
				if (row != null && row.state == State.FAILED) {
					row.state = State.LOADING;
					bind(row);
					listener.onLoadRow(row);
				}
			});
		}

		void bind(Row row) {
			boolean sameRow = this.row == row;
			if (this.row != null && !sameRow) {
				saveScroll();
			}
			this.row = row;
			title.setText(row.title);
			subtitle.setText(row.subtitle);
			subtitle.setVisibility(row.subtitle == null || row.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
			loading.setVisibility(row.state == State.LOADING ? View.VISIBLE : View.GONE);
			pulse(true);
			list.setVisibility(row.state == State.READY ? View.VISIBLE : View.GONE);
			switch (row.state) {
				case FAILED:
					status.setVisibility(View.VISIBLE);
					status.setText(R.string.row_failed);
					break;
				case EMPTY:
					status.setVisibility(View.VISIBLE);
					if (row.emptyText != null) {
						status.setText(row.emptyText);
					} else {
						status.setText(R.string.row_empty);
					}
					break;
				default:
					status.setVisibility(View.GONE);
					break;
			}
			if (sameRow) {
				adapter.submit(row.items);
				return;
			}
			adapter.replace(row.items);
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

		private void fillPlaceholders(@NonNull ViewGroup into) {
			Resources res = into.getResources();
			float posterWidth = res.getDimension(R.dimen.poster_width);
			float screenWidth = res.getConfiguration().screenWidthDp * res.getDisplayMetrics().density;
			int count = Math.max(3, (int) Math.ceil(screenWidth / posterWidth) + 1);
			LayoutInflater inflater = LayoutInflater.from(into.getContext());
			for (int i = 0; i < count; i++) {
				inflater.inflate(R.layout.item_poster_skeleton, into, true);
			}
		}

		void pulse(boolean wanted) {
			boolean run = wanted && row != null && row.state == State.LOADING && itemView.isAttachedToWindow()
					&& ValueAnimator.areAnimatorsEnabled();
			if (!run) {
				if (pulse != null) {
					pulse.cancel();
					pulse = null;
				}
				loading.setAlpha(1f);
				return;
			}
			if (pulse == null) {
				pulse = ObjectAnimator.ofFloat(loading, View.ALPHA, 1f, 0.4f);
				pulse.setDuration(PULSE_MS);
				pulse.setRepeatCount(ValueAnimator.INFINITE);
				pulse.setRepeatMode(ValueAnimator.REVERSE);
				pulse.start();
			}
		}
	}
}
