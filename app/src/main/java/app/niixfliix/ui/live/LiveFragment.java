package app.niixfliix.ui.live;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.R;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.live.LiveCheck;
import app.niixfliix.live.LiveTv;
import app.niixfliix.live.SportSites;
import app.niixfliix.ui.Images;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.RowsAdapter;
import app.niixfliix.ui.common.TabToolbar;
import app.niixfliix.ui.details.DetailsActivity;
import app.niixfliix.ui.web.WebPlayerActivity;

public final class LiveFragment extends Fragment implements RowsAdapter.Listener, AddonRepository.Listener {

	private static final long SHOW_EVERY_MS = 600;
	private static final int MAX_PER_ROW = 60;

	private final AppGraph graph = AppGraph.get();
	private final Map<RowsAdapter.Row, LiveTv.Row> sources = new HashMap<>();
	private final Map<RowsAdapter.Row, LiveCheck.Batch> checks = new HashMap<>();
	private RowsAdapter adapter;
	private SwipeRefreshLayout refresh;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_live, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		TabToolbar.setUp(this, view, R.string.nav_live);
		refresh = view.findViewById(R.id.refresh);
		RecyclerView list = view.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(requireContext()));
		adapter = new RowsAdapter(this);
		list.setAdapter(adapter);
		Images.pauseWhileFlinging(list);
		refresh.setOnRefreshListener(this::refreshAll);
		graph.addons.addListener(this);
		buildRows(false);
	}

	@Override
	public void onDestroyView() {
		graph.addons.removeListener(this);
		for (LiveCheck.Batch batch : checks.values()) {
			batch.cancel();
		}
		checks.clear();
		sources.clear();
		super.onDestroyView();
	}

	@Override
	public void onAddonsChanged() {
		if (getView() != null) {
			refresh.setRefreshing(false);
			buildRows(true);
		}
	}

	private void refreshAll() {
		graph.addons.refreshAll(() -> {
			if (getView() != null) {
				graph.addonClient.clearCache();
				buildRows(false);
				refresh.setRefreshing(false);
			}
		});
	}

	private void buildRows(boolean keepLoaded) {
		Map<RowsAdapter.Row, LiveTv.Row> old = new HashMap<>(sources);
		sources.clear();
		List<RowsAdapter.Row> rows = new ArrayList<>();
		rows.add(sportSitesRow());
		for (LiveTv.Row live : LiveTv.rows(graph.addons)) {
			String title = live.isFreeSports() ? getString(R.string.live_free_sports) : live.name();
			String subtitle = live.isFreeSports() ? getString(R.string.live_free_sports_summary)
					: live.source.addon.displayName();
			RowsAdapter.Row existing = keepLoaded ? adapter.row(live.key) : null;
			boolean keep = existing != null && isDone(existing) && existing.title.equals(title)
					&& subtitle.equals(existing.subtitle);
			RowsAdapter.Row row = keep ? existing : new RowsAdapter.Row(live.key, title, subtitle);
			row.emptyText = getString(R.string.live_row_nothing);
			sources.put(row, live);
			rows.add(row);
		}
		for (RowsAdapter.Row gone : old.keySet()) {
			if (!sources.containsKey(gone)) {
				cancelCheck(gone);
			}
		}
		adapter.setContent(null, rows);
		if (stillLoadingManifests()) {
			refresh.setRefreshing(true);
		}
	}

	@NonNull
	private RowsAdapter.Row sportSitesRow() {
		String title = getString(R.string.live_sport_tv);
		String subtitle = getString(R.string.live_sport_tv_summary);
		RowsAdapter.Row existing = adapter.row(SportSites.ROW_KEY);
		if (existing != null && existing.title.equals(title) && subtitle.equals(existing.subtitle)) {
			return existing;
		}
		RowsAdapter.Row row = new RowsAdapter.Row(SportSites.ROW_KEY, title, subtitle);
		row.items = SportSites.posters();
		row.state = RowsAdapter.State.READY;
		return row;
	}

	private static boolean isDone(@NonNull RowsAdapter.Row row) {
		return row.state == RowsAdapter.State.READY || row.state == RowsAdapter.State.EMPTY;
	}

	private boolean stillLoadingManifests() {
		for (InstalledAddon addon : graph.addons.active()) {
			if (addon.manifest == null && !graph.addons.isUnreachable(addon)) {
				return true;
			}
		}
		return false;
	}

	private void cancelCheck(RowsAdapter.Row row) {
		LiveCheck.Batch batch = checks.remove(row);
		if (batch != null) {
			batch.cancel();
		}
	}

	@Override
	public void onLoadRow(@NonNull RowsAdapter.Row row) {
		LiveTv.Row live = sources.get(row);
		if (live == null) {
			return;
		}
		cancelCheck(row);
		graph.io.execute(() -> {
			List<Meta> metas = live.source.fetch(graph.addonClient, live.extras());
			graph.main.post(() -> {
				if (getView() == null || sources.get(row) != live) {
					return;
				}
				if (metas == null) {
					row.state = RowsAdapter.State.FAILED;
					adapter.update(row);
					return;
				}
				check(row, metas.size() > MAX_PER_ROW ? new ArrayList<>(metas.subList(0, MAX_PER_ROW)) : metas);
			});
		});
	}

	private void check(RowsAdapter.Row row, List<Meta> metas) {
		Set<Meta> working = new HashSet<>();
		boolean[] showPosted = {false};
		Runnable show = () -> {
			showPosted[0] = false;
			if (getView() == null) {
				return;
			}
			List<PosterItem> items = new ArrayList<>();
			for (Meta m : metas) {
				if (working.contains(m)) {
					items.add(PosterItem.of(m));
				}
			}
			if (items.size() != row.items.size()) {
				row.items = items;
				row.state = RowsAdapter.State.READY;
				adapter.update(row);
			}
		};
		LiveCheck.Batch batch = graph.liveCheck.check(metas, new LiveCheck.Listener() {
			@Override
			public void onChecked(@NonNull Meta meta, boolean works) {
				if (!works) {
					return;
				}
				working.add(meta);
				if (!showPosted[0]) {
					showPosted[0] = true;
					graph.main.postDelayed(show, row.items.isEmpty() ? 0 : SHOW_EVERY_MS);
				}
			}

			@Override
			public void onDone() {
				checks.remove(row);
				graph.main.removeCallbacks(show);
				show.run();
				if (working.isEmpty() && getView() != null) {
					row.items = new ArrayList<>();
					row.state = RowsAdapter.State.EMPTY;
					adapter.update(row);
				}
			}
		});
		checks.put(row, batch);
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		SportSites.Site site = SportSites.find(item.id);
		if (site != null && SportSites.isSite(item.id)) {
			WebPlayerActivity.start(requireContext(), site);
			return;
		}
		DetailsActivity.start(requireContext(), item);
	}

	@Override
	public boolean onPosterLongClick(@NonNull PosterItem item) {
		return false;
	}
}
