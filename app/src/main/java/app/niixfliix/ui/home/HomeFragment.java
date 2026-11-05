package app.niixfliix.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
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
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.Images;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.HomeHero;
import app.niixfliix.ui.common.LibraryToggle;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.common.RowsAdapter;
import app.niixfliix.ui.common.TabToolbar;
import app.niixfliix.ui.details.DetailsActivity;

public final class HomeFragment extends Fragment implements RowsAdapter.Listener, AddonRepository.Listener {

	private static final String CONTINUE_KEY = "continue";
	private static final float SOLID_TOOLBAR_AT_DP = 120;
	private static final float STOP_ROTATING_AT_DP = 240;

	private final AppGraph graph = AppGraph.get();
	private final Map<RowsAdapter.Row, CatalogSource> sources = new HashMap<>();
	private RowsAdapter adapter;
	private SwipeRefreshLayout refresh;
	private View empty;
	@Nullable private RowsAdapter.Row continueRow;
	@Nullable private RowsAdapter.Row heroRow;
	private final List<HomeHero> heroes = new ArrayList<>();
	private final Runnable rotate = this::rotate;
	private int heroIndex;
	private RecyclerView list;
	private View toolbar;
	private int toolbarHeight;
	@NonNull private List<RowsAdapter.Row> shownRows = new ArrayList<>();
	@Nullable private RowsAdapter.Notice shownNotice;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_home, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		TabToolbar.setUp(this, view, R.string.app_name);
		refresh = view.findViewById(R.id.refresh);
		empty = view.findViewById(R.id.empty);
		list = view.findViewById(R.id.list);
		toolbar = view.findViewById(R.id.tab_toolbar);
		TypedValue size = new TypedValue();
		toolbarHeight = requireContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, size, true)
				? TypedValue.complexToDimensionPixelSize(size.data, getResources().getDisplayMetrics()) : 0;
		list.setLayoutManager(new LinearLayoutManager(requireContext()));
		adapter = new RowsAdapter(this);
		list.setAdapter(adapter);
		Images.pauseWhileFlinging(list);
		list.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
				updateToolbar();
			}
		});
		refresh.setProgressViewOffset(false, toolbarHeight / 2, toolbarHeight + dp(48));
		refresh.setOnRefreshListener(() -> refreshAll(true));
		graph.addons.addListener(this);
		buildRows(false);
	}

	@Override
	public void onDestroyView() {
		graph.main.removeCallbacks(rotate);
		graph.addons.removeListener(this);
		super.onDestroyView();
	}

	@Override
	public void onResume() {
		super.onResume();
		refreshWatched();
		scheduleRotate();
	}

	@Override
	public void onPause() {
		graph.main.removeCallbacks(rotate);
		super.onPause();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) {
			refreshWatched();
			scheduleRotate();
		} else {
			graph.main.removeCallbacks(rotate);
		}
	}

	private int dp(float value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	private void updateToolbar() {
		if (getView() == null) {
			return;
		}
		float fraction = heroes.isEmpty() ? 1f
				: Math.min(1f, scrollY() / (float) dp(SOLID_TOOLBAR_AT_DP));
		int color = ContextCompat.getColor(requireContext(), R.color.background);
		toolbar.setBackgroundColor(ColorUtils.setAlphaComponent(color, Math.round(fraction * 255)));
	}

	private int scrollY() {
		RecyclerView.LayoutManager manager = list.getLayoutManager();
		if (!(manager instanceof LinearLayoutManager)) {
			return 0;
		}
		LinearLayoutManager layout = (LinearLayoutManager) manager;
		int first = layout.findFirstVisibleItemPosition();
		if (first > 0) {
			return Integer.MAX_VALUE / 2;
		}
		View top = layout.findViewByPosition(0);
		return top == null ? 0 : Math.max(0, list.getPaddingTop() - top.getTop());
	}

	private void scheduleRotate() {
		graph.main.removeCallbacks(rotate);
		if (getView() != null && isResumed() && !isHidden() && heroes.size() > 1) {
			graph.main.postDelayed(rotate, HomeHero.ROTATE_EVERY_MS);
		}
	}

	private void rotate() {
		if (getView() == null || heroes.size() < 2) {
			return;
		}
		boolean nearTop = scrollY() < dp(STOP_ROTATING_AT_DP);
		boolean idle = list.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
		if (nearTop && idle) {
			heroIndex = (heroIndex + 1) % heroes.size();
			showContent();
		}
		scheduleRotate();
	}

	private void refreshWatched() {
		if (getView() == null) {
			return;
		}
		updateContinueWatching();
		adapter.refreshWatched();
		showContent();
	}

	@Override
	public void onAddonsChanged() {
		if (getView() != null) {
			refresh.setRefreshing(false);
			buildRows(true);
		}
	}

	private void buildRows(boolean keepLoaded) {
		sources.clear();
		heroRow = null;
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
				if (!catalog.worksForHome()) {
					continue;
				}
				String name = catalog.name != null ? catalog.name : catalog.id;
				String title = getString(R.string.row_title, name, Formats.typeLabel(requireContext(), catalog.type));
				String key = addon.transportUrl + "|" + catalog.type + "|" + catalog.id;
				String subtitle = addon.displayName();
				RowsAdapter.Row old = keepLoaded ? adapter.row(key) : null;
				RowsAdapter.Row row = old != null && isLoaded(old) && old.title.equals(title)
						&& subtitle.equals(old.subtitle) ? old : new RowsAdapter.Row(key, title, subtitle);
				sources.put(row, new CatalogSource(addon, catalog));
				rows.add(row);
				if (heroRow == null) {
					heroRow = row;
				}
			}
		}
		if (heroRow == null) {
			heroes.clear();
		} else if (heroRow.state == RowsAdapter.State.READY) {
			pickHero(heroRow.items);
		}
		shownRows = rows;
		shownNotice = notice();
		showContent();
		boolean loading = stillLoadingManifests();
		if (loading) {
			refresh.setRefreshing(true);
		} else {
			graph.warmUpContinueWatching();
		}
		empty.setVisibility(adapter.isEmpty() && !loading ? View.VISIBLE : View.GONE);
	}

	private void showContent() {
		if (getView() == null) {
			return;
		}
		RowsAdapter.Hero entry = heroEntry();
		// At the top before this update: stay at the top after it. The banner arrives after the first
		// catalog loads and is inserted above rows already on screen, which would otherwise keep their
		// place and leave the banner scrolled out of sight.
		boolean atTop = !list.canScrollVertically(-1);
		int top = entry == null ? toolbarHeight : 0;
		if (list.getPaddingTop() != top) {
			list.setPadding(list.getPaddingLeft(), top, list.getPaddingRight(), list.getPaddingBottom());
		}
		adapter.setContent(entry, shownNotice, shownRows);
		if (atTop) {
			list.scrollToPosition(0);
		}
		updateToolbar();
	}

	@Nullable
	private RowsAdapter.Hero heroEntry() {
		if (heroes.isEmpty()) {
			return null;
		}
		heroIndex = Math.min(heroIndex, heroes.size() - 1);
		HomeHero current = heroes.get(heroIndex);
		PosterItem item = current.item;
		return new RowsAdapter.Hero(current, graph.library.contains(item.id), heroes.size(), heroIndex,
				() -> DetailsActivity.start(requireContext(), item, true),
				() -> {
					boolean saved = LibraryToggle.toggle(graph.library, item);
					Toast.makeText(requireContext(), saved ? R.string.library_added : R.string.library_removed,
							Toast.LENGTH_SHORT).show();
					showContent();
				},
				() -> DetailsActivity.start(requireContext(), item),
				index -> {
					heroIndex = index;
					showContent();
					scheduleRotate();
				});
	}

	private void pickHero(@NonNull List<PosterItem> items) {
		List<PosterItem> picked = HomeHero.pickAll(items, HomeHero.ROTATION);
		if (picked.isEmpty() || sameHeroes(picked)) {
			return;
		}
		heroes.clear();
		for (PosterItem p : picked) {
			heroes.add(new HomeHero(p, null));
		}
		heroIndex = 0;
		showContent();
		scheduleRotate();
		for (PosterItem p : picked) {
			graph.io.execute(() -> {
				Meta found = MetaFinder.find(graph.addons, graph.addonClient, p.type, p.id);
				Meta meta = found != null ? graph.translations.translate(found) : null;
				graph.main.post(() -> {
					if (getView() == null || meta == null) {
						return;
					}
					for (int i = 0; i < heroes.size(); i++) {
						if (heroes.get(i).item.id.equals(p.id)) {
							heroes.set(i, new HomeHero(p, meta));
							if (i == heroIndex) {
								showContent();
							}
						}
					}
				});
			});
		}
	}

	private boolean sameHeroes(@NonNull List<PosterItem> picked) {
		if (picked.size() != heroes.size()) {
			return false;
		}
		for (int i = 0; i < picked.size(); i++) {
			if (!picked.get(i).id.equals(heroes.get(i).item.id)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isLoaded(@NonNull RowsAdapter.Row row) {
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

	@Nullable
	private RowsAdapter.Notice notice() {
		List<InstalledAddon> down = graph.addons.unreachableToReport();
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
				buildRows(false);
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

	private void updateContinueWatching() {
		if (getView() == null) {
			return;
		}
		RowsAdapter.Row latest = continueWatchingRow();
		if (latest != null && continueRow != null) {
			continueRow.items = latest.items;
			adapter.update(continueRow);
		} else if (latest != null || continueRow != null) {
			buildRows(true);
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
				if (row == heroRow && row.state == RowsAdapter.State.READY) {
					pickHero(row.items);
				}
			});
		});
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		DetailsActivity.start(requireContext(), item);
	}

	@Override
	public boolean onPosterLongClick(@NonNull PosterItem item) {
		boolean inContinue = continueRow != null && continueRow.items.contains(item);
		QueueMenu.show(requireContext(), item,
				inContinue ? getString(R.string.continue_remove) : null,
				inContinue ? () -> {
					graph.history.dismiss(item.id);
					updateContinueWatching();
				} : null,
				this::refreshWatched);
		return true;
	}
}
