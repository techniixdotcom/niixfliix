package app.niixfliix.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import app.niixfliix.R;
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.download.Downloads;
import app.niixfliix.download.model.DownloadItem;
import app.niixfliix.net.Network;
import app.niixfliix.player.PlaySession;
import app.niixfliix.player.PlayerLauncher;
import app.niixfliix.player.QueueResolver;
import app.niixfliix.ui.Images;
import app.niixfliix.ui.common.Grids;
import app.niixfliix.ui.common.PosterAdapter;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.common.TabToolbar;
import app.niixfliix.ui.details.DetailsActivity;

public final class LibraryFragment extends Fragment implements PosterAdapter.Listener, Downloads.Listener,
		DownloadsAdapter.Listener, QueueAdapter.Listener {

	private static final int TAB_SAVED = 0;
	private static final int TAB_HISTORY = 1;
	private static final int TAB_DOWNLOADS = 2;
	private static final int TAB_QUEUE = 3;
	private static final String STATE_TAB = "tab";
	private static final int FILTER_ALL = 0;
	private static final int FILTER_MOVIES = 1;
	private static final int FILTER_SERIES = 2;
	private static final String STATE_FILTER = "type_filter";
	private static final String KEY_WATCHED_HEADER = "watched";

	private final AppGraph graph = AppGraph.get();
	private RecyclerView list;
	private TextView empty;
	private PosterAdapter posters;
	private DownloadsAdapter downloads;
	private QueueAdapter queue;
	private View queueActions;
	private ChipGroup typeFilter;
	@Nullable private QueueResolver resolving;
	@Nullable private AlertDialog resolvingDialog;
	private int tab = TAB_SAVED;
	private int filter = FILTER_ALL;
	private int shownTab = -1;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_library, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		TabToolbar.setUp(this, view, R.string.nav_library);
		list = view.findViewById(R.id.list);
		empty = view.findViewById(R.id.empty);
		shownTab = -1;
		posters = new PosterAdapter(true, this);
		Images.pauseWhileFlinging(list);
		downloads = new DownloadsAdapter(this);
		queue = new QueueAdapter(this);
		queueActions = view.findViewById(R.id.queue_actions);
		view.findViewById(R.id.queue_play).setOnClickListener(v -> {
			QueueItem first = graph.queue.peek();
			if (first != null) {
				onQueueClick(first);
			}
		});
		view.findViewById(R.id.queue_clear).setOnClickListener(v -> {
			graph.queue.clear();
			graph.playback().queueChanged();
			show();
		});
		TabLayout tabs = view.findViewById(R.id.tabs);
		if (savedInstanceState != null) {
			tab = savedInstanceState.getInt(STATE_TAB, TAB_SAVED);
			filter = savedInstanceState.getInt(STATE_FILTER, FILTER_ALL);
		}
		typeFilter = view.findViewById(R.id.type_filter);
		typeFilter.check(chipOf(filter));
		typeFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
			int chosen = checkedIds.isEmpty() ? FILTER_ALL : filterOf(checkedIds.get(0));
			if (chosen != filter) {
				filter = chosen;
				show();
			}
		});
		TabLayout.Tab selected = tabs.getTabAt(tab);
		if (selected != null) {
			selected.select();
		}
		tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab t) {
				tab = t.getPosition();
				show();
			}

			@Override
			public void onTabUnselected(TabLayout.Tab t) {
			}

			@Override
			public void onTabReselected(TabLayout.Tab t) {
			}
		});
		graph.downloads().addListener(this);
		show();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_TAB, tab);
		outState.putInt(STATE_FILTER, filter);
	}

	@Override
	public void onDestroyView() {
		stopResolving();
		graph.downloads().removeListener(this);
		super.onDestroyView();
	}

	@Override
	public void onResume() {
		super.onResume();
		show();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) {
			show();
		}
	}

	@Override
	public void onDownloadsChanged() {
		if (getView() != null && tab == TAB_DOWNLOADS) {
			downloads.submit(graph.downloads().all());
		}
	}

	private void show() {
		if (getView() == null) {
			return;
		}
		boolean switched = shownTab != tab;
		shownTab = tab;
		queueActions.setVisibility(View.GONE);
		typeFilter.setVisibility(tab == TAB_SAVED || tab == TAB_HISTORY ? View.VISIBLE : View.GONE);
		if (tab == TAB_QUEUE) {
			if (switched) {
				list.setLayoutManager(new LinearLayoutManager(requireContext()));
				list.setAdapter(queue);
			}
			List<QueueItem> items = graph.queue.all();
			queue.submit(items);
			queueActions.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
			setEmpty(items.isEmpty(), R.string.library_queue_empty);
			return;
		}
		if (tab == TAB_DOWNLOADS) {
			if (switched) {
				list.setLayoutManager(new LinearLayoutManager(requireContext()));
				list.setAdapter(downloads);
			}
			List<DownloadItem> items = graph.downloads().all();
			downloads.submit(items);
			setEmpty(items.isEmpty(), R.string.library_downloads_empty);
			return;
		}
		if (switched) {
			GridLayoutManager grid = new GridLayoutManager(requireContext(), Grids.posterSpan(requireContext()));
			grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
				@Override
				public int getSpanSize(int position) {
					return posters.isHeader(position) ? grid.getSpanCount() : 1;
				}
			});
			list.setLayoutManager(grid);
			list.setAdapter(posters);
		}
		List<PosterItem> items;
		boolean anyAtAll;
		if (tab == TAB_SAVED) {
			List<LibraryItem> saved = graph.library.all();
			anyAtAll = !saved.isEmpty();
			items = savedItems(saved);
			refreshNewEpisodes(saved);
			graph.fillLibraryDates(this::show);
		} else {
			List<HistoryEntry> recent = graph.history.recent();
			anyAtAll = !recent.isEmpty();
			items = new ArrayList<>();
			for (HistoryEntry e : recent) {
				if (passesFilter(e.type)) {
					items.add(withNewMark(PosterItem.of(e, true)));
				}
			}
		}
		if (switched) {
			posters.replace(items);
		} else {
			posters.submit(items);
		}
		int emptyText = anyAtAll ? R.string.library_filter_empty
				: tab == TAB_SAVED ? R.string.library_saved_empty : R.string.library_history_empty;
		setEmpty(items.isEmpty(), emptyText);
	}

	private List<PosterItem> savedItems(List<LibraryItem> saved) {
		List<PosterItem> toWatch = new ArrayList<>();
		List<PosterItem> watched = new ArrayList<>();
		for (LibraryItem item : saved) {
			if (!passesFilter(item.type)) {
				continue;
			}
			PosterItem poster = withNewMark(PosterItem.of(item));
			boolean done = !Meta.TYPE_SERIES.equals(item.type) && graph.history.isWatched(item.id);
			(done ? watched : toWatch).add(poster);
		}
		Comparator<PosterItem> order = ReleaseOrder.<PosterItem>newestFirst(this::releaseOf).thenComparing(byTitle());
		toWatch.sort(order);
		watched.sort(order);
		List<PosterItem> items = new ArrayList<>(toWatch.size() + watched.size() + 1);
		items.addAll(toWatch);
		if (!watched.isEmpty()) {
			items.add(PosterItem.header(KEY_WATCHED_HEADER, getString(R.string.library_watched)));
			items.addAll(watched);
		}
		return items;
	}

	private Comparator<PosterItem> byTitle() {
		Locale locale = getResources().getConfiguration().getLocales().get(0);
		Collator collator = Collator.getInstance(locale != null ? locale : Locale.getDefault());
		collator.setStrength(Collator.PRIMARY);
		return (a, b) -> {
			String x = title(a);
			String y = title(b);
			int c = collator.compare(x, y);
			if (c == 0) {
				c = x.compareTo(y);
			}
			return c != 0 ? c : a.id.compareTo(b.id);
		};
	}

	private long releaseOf(PosterItem item) {
		return Meta.TYPE_SERIES.equals(item.type) ? Math.max(item.released, graph.newEpisodes.latestOutMs(item.id))
				: item.released;
	}

	private String title(PosterItem item) {
		String translated = graph.translations.name(item.id);
		return (translated != null ? translated : item.name).trim();
	}

	private boolean passesFilter(String type) {
		switch (filter) {
			case FILTER_MOVIES:
				return Meta.TYPE_MOVIE.equals(type);
			case FILTER_SERIES:
				return Meta.TYPE_SERIES.equals(type);
			default:
				return true;
		}
	}

	private PosterItem withNewMark(PosterItem item) {
		return Meta.TYPE_SERIES.equals(item.type) ? item.withNew(graph.newEpisodes.hasNew(item.id)) : item;
	}

	private void refreshNewEpisodes(List<LibraryItem> saved) {
		List<String> series = new ArrayList<>();
		for (LibraryItem item : saved) {
			if (Meta.TYPE_SERIES.equals(item.type)) {
				series.add(item.id);
			}
		}
		graph.newEpisodes.refresh(series,
				id -> MetaFinder.find(graph.addons, graph.addonClient, Meta.TYPE_SERIES, id),
				this::show);
	}

	private static int chipOf(int filter) {
		switch (filter) {
			case FILTER_MOVIES:
				return R.id.filter_movies;
			case FILTER_SERIES:
				return R.id.filter_series;
			default:
				return R.id.filter_all;
		}
	}

	private static int filterOf(int chipId) {
		if (chipId == R.id.filter_movies) {
			return FILTER_MOVIES;
		}
		if (chipId == R.id.filter_series) {
			return FILTER_SERIES;
		}
		return FILTER_ALL;
	}

	private void setEmpty(boolean isEmpty, int text) {
		empty.setText(text);
		empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		DetailsActivity.start(requireContext(), item);
	}

	@Override
	public boolean onPosterLongClick(@NonNull PosterItem item) {
		QueueMenu.show(requireContext(), item, tab != TAB_SAVED, getString(R.string.action_remove),
				() -> confirmRemove(item), this::show);
		return true;
	}

	private void confirmRemove(PosterItem item) {
		boolean saved = tab == TAB_SAVED;
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(item.name)
				.setMessage(saved ? R.string.library_remove_saved : R.string.library_remove_history)
				.setPositiveButton(R.string.action_remove, (d, w) -> {
					if (saved) {
						graph.library.remove(item.id);
					} else {
						graph.history.removeMeta(item.id);
					}
					show();
				})
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	@Override
	public void onQueueClick(@NonNull QueueItem item) {
		stopResolving();
		resolvingDialog = new MaterialAlertDialogBuilder(requireContext())
				.setTitle(item.label())
				.setMessage(R.string.queue_finding)
				.setNegativeButton(R.string.action_cancel, (d, w) -> stopResolving())
				.setOnCancelListener(d -> stopResolving())
				.show();
		resolving = QueueResolver.start(Network.isUnmetered(requireContext()), item, null, null, new QueueResolver.Callback() {
			@Override
			public void onReady(@NonNull PlaySession session) {
				stopResolving();
				graph.queue.remove(item.key());
				PlayerLauncher.play(requireActivity(), session);
			}

			@Override
			public void onFailed() {
				stopResolving();
				Toast.makeText(requireContext(), R.string.queue_no_stream, Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void onQueueRemove(@NonNull QueueItem item) {
		graph.queue.remove(item.key());
		graph.playback().queueChanged();
		show();
	}

	private void stopResolving() {
		if (resolving != null) {
			resolving.cancel();
			resolving = null;
		}
		if (resolvingDialog != null) {
			resolvingDialog.dismiss();
			resolvingDialog = null;
		}
	}

	@Override
	public void onDownloadClick(@NonNull DownloadItem item) {
		if (item.state == DownloadItem.State.DONE) {
			PlayerLauncher.playLocal(requireActivity(), item);
		}
	}

	@Override
	public void onDownloadToggle(@NonNull DownloadItem item) {
		if (item.isActive()) {
			graph.downloads().pause(item.id);
		} else if (item.state != DownloadItem.State.DONE) {
			graph.downloads().resume(item.id);
		}
	}

	@Override
	public void onDownloadDelete(@NonNull DownloadItem item) {
		new MaterialAlertDialogBuilder(requireContext())
				.setTitle(item.title)
				.setMessage(R.string.download_delete_message)
				.setPositiveButton(R.string.action_delete, (d, w) -> {
					graph.downloads().delete(item.id);
					show();
				})
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}
}
