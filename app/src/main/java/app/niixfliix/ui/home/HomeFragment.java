package app.niixfliix.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.niixfliix.R;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.common.RowsAdapter;
import app.niixfliix.ui.details.DetailsActivity;

public final class HomeFragment extends Fragment implements RowsAdapter.Listener, AddonRepository.Listener {

	private static final String CONTINUE_KEY = "continue";

	private final AppGraph graph = AppGraph.get();
	private final Map<RowsAdapter.Row, CatalogSource> sources = new HashMap<>();
	private RowsAdapter adapter;
	private SwipeRefreshLayout refresh;
	private View empty;
	@Nullable private RowsAdapter.Row continueRow;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_home, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		refresh = view.findViewById(R.id.refresh);
		empty = view.findViewById(R.id.empty);
		RecyclerView list = view.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(requireContext()));
		adapter = new RowsAdapter(this);
		list.setAdapter(adapter);
		refresh.setOnRefreshListener(() -> refreshAll(true));
		graph.addons.addListener(this);
		buildRows();
	}

	@Override
	public void onDestroyView() {
		graph.addons.removeListener(this);
		super.onDestroyView();
	}

	@Override
	public void onResume() {
		super.onResume();
		updateContinueWatching();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) {
			updateContinueWatching();
		}
	}

	@Override
	public void onAddonsChanged() {
		if (getView() != null) {
			refresh.setRefreshing(false);
			buildRows();
		}
	}

	private void buildRows() {
		sources.clear();
		List<RowsAdapter.Row> rows = new ArrayList<>();
		continueRow = continueWatchingRow();
		if (continueRow != null) {
			rows.add(continueRow);
		}
		for (InstalledAddon addon : graph.addons.active()) {
			if (addon.manifest == null) {
				continue;
			}
			for (Catalog catalog : addon.manifest.validCatalogs()) {
				if (!catalog.worksWithoutExtras()) {
					continue;
				}
				String name = catalog.name != null ? catalog.name : catalog.id;
				String title = getString(R.string.row_title, name, Formats.typeLabel(requireContext(), catalog.type));
				RowsAdapter.Row row = new RowsAdapter.Row(addon.transportUrl + "|" + catalog.type + "|" + catalog.id,
						title, addon.displayName());
				sources.put(row, new CatalogSource(addon, catalog));
				rows.add(row);
			}
		}
		adapter.setContent(notice(), rows);
		boolean loading = stillLoadingManifests();
		if (loading) {
			refresh.setRefreshing(true);
		}
		empty.setVisibility(adapter.isEmpty() && !loading ? View.VISIBLE : View.GONE);
	}

	/** fresh install, manifests not fetched yet */
	private boolean stillLoadingManifests() {
		for (InstalledAddon addon : graph.addons.active()) {
			if (addon.manifest == null && !graph.addons.isUnreachable(addon)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private RowsAdapter.Notice notice() {
		List<InstalledAddon> down = graph.addons.unreachable();
		if (down.isEmpty()) {
			return null;
		}
		List<String> names = new ArrayList<>();
		for (InstalledAddon a : down) {
			names.add(a.displayName());
		}
		String text = getResources().getQuantityString(R.plurals.addons_down, down.size(), String.join(", ", names));
		return new RowsAdapter.Notice(text, getString(R.string.action_retry), () -> {
			refresh.setRefreshing(true);
			refreshAll(false);
		});
	}

	private void refreshAll(boolean clearCache) {
		graph.addons.refreshAll(() -> {
			if (getView() != null) {
				if (clearCache) {
					graph.addonClient.clearCache();
				}
				buildRows();
				refresh.setRefreshing(false);
			}
		});
	}

	@Nullable
	private RowsAdapter.Row continueWatchingRow() {
		List<HistoryEntry> entries = graph.history.continueWatching();
		if (entries.isEmpty()) {
			return null;
		}
		RowsAdapter.Row row = new RowsAdapter.Row(CONTINUE_KEY, getString(R.string.row_continue_watching), null);
		row.state = RowsAdapter.State.READY;
		for (HistoryEntry e : entries) {
			row.items.add(PosterItem.of(e, true));
		}
		return row;
	}

	/** only the continue row changes after playback */
	private void updateContinueWatching() {
		if (getView() == null) {
			return;
		}
		RowsAdapter.Row latest = continueWatchingRow();
		if (latest != null && continueRow != null) {
			continueRow.items = latest.items;
			adapter.update(continueRow);
		} else if (latest != null || continueRow != null) {
			buildRows();
		}
	}

	@Override
	public void onLoadRow(@NonNull RowsAdapter.Row row) {
		CatalogSource source = sources.get(row);
		if (source == null) {
			return;
		}
		graph.io.execute(() -> {
			List<Meta> result = source.fetch(graph.addonClient, Collections.emptyMap());
			graph.main.post(() -> {
				if (getView() == null) {
					return;
				}
				if (result == null) {
					row.state = RowsAdapter.State.FAILED;
				} else {
					row.items = PosterItem.ofMetas(result);
					row.state = result.isEmpty() ? RowsAdapter.State.EMPTY : RowsAdapter.State.READY;
				}
				adapter.update(row);
			});
		});
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		DetailsActivity.start(requireContext(), item);
	}

	@Override
	public boolean onPosterLongClick(@NonNull PosterItem item) {
		QueueMenu.show(requireContext(), item);
		return true;
	}
}
