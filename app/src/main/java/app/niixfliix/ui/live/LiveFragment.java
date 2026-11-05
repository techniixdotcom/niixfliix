package app.niixfliix.ui.live;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import app.niixfliix.ui.common.Grids;
import app.niixfliix.ui.common.PosterAdapter;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.TabToolbar;
import app.niixfliix.ui.details.DetailsActivity;
import app.niixfliix.ui.web.WebPlayerActivity;

/**
 * Live, laid out like Discover: a row of chips picks the source (Sport TV, the free sports channels, each
 * live catalog), a second row picks the genre when the catalog has more than one, and the channels show
 * in a poster grid below. Only channels that play right now are listed; they appear as they're checked.
 */
public final class LiveFragment extends Fragment implements PosterAdapter.Listener, AddonRepository.Listener {

	private static final long SHOW_EVERY_MS = 600;
	private static final int MAX_CHANNELS = 60;

	/** One chip in the top row: a live catalog (its genres are its rows), or the Sport TV sites. */
	private static final class Source {
		@NonNull final String key;
		@NonNull final String title;
		@Nullable final String summary;
		@NonNull final List<LiveTv.Row> rows = new ArrayList<>();

		Source(@NonNull String key, @NonNull String title, @Nullable String summary) {
			this.key = key;
			this.title = title;
			this.summary = summary;
		}

		boolean isSites() {
			return key.equals(SportSites.ROW_KEY);
		}
	}

	private final AppGraph graph = AppGraph.get();
	private final List<Source> sources = new ArrayList<>();
	/** Checked channels per row key, so going back to a chip doesn't check everything again. */
	private final Map<String, List<PosterItem>> checked = new HashMap<>();
	private ChipGroup sourceChips;
	private ChipGroup genreChips;
	private View genreScroll;
	private TextView summary;
	private TextView empty;
	private View loading;
	private SwipeRefreshLayout refresh;
	private PosterAdapter adapter;

	@Nullable private Source source;
	@Nullable private LiveTv.Row row;
	@Nullable private LiveCheck.Batch batch;
	private int generation;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_live, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		TabToolbar.setUp(this, view, R.string.nav_live);
		sourceChips = view.findViewById(R.id.source_chips);
		genreChips = view.findViewById(R.id.genre_chips);
		genreScroll = view.findViewById(R.id.genre_scroll);
		summary = view.findViewById(R.id.summary);
		empty = view.findViewById(R.id.empty);
		loading = view.findViewById(R.id.loading);
		refresh = view.findViewById(R.id.refresh);
		RecyclerView grid = view.findViewById(R.id.grid);
		grid.setLayoutManager(new GridLayoutManager(requireContext(), Grids.posterSpan(requireContext())));
		adapter = new PosterAdapter(true, this);
		grid.setAdapter(adapter);
		Images.pauseWhileFlinging(grid);
		sourceChips.setOnCheckedStateChangeListener((group, ids) -> {
			if (ids.isEmpty()) {
				return;
			}
			Chip chip = group.findViewById(ids.get(0));
			Source picked = chip != null ? find((String) chip.getTag()) : null;
			if (picked != null && picked != source) {
				selectSource(picked, null);
			}
		});
		genreChips.setOnCheckedStateChangeListener((group, ids) -> {
			if (ids.isEmpty() || source == null) {
				return;
			}
			Chip chip = group.findViewById(ids.get(0));
			if (chip == null) {
				return;
			}
			for (LiveTv.Row r : source.rows) {
				if (r.key.equals(chip.getTag()) && r != row) {
					selectRow(r);
				}
			}
		});
		refresh.setOnRefreshListener(this::refreshAll);
		graph.addons.addListener(this);
		rebuild();
	}

	@Override
	public void onDestroyView() {
		graph.addons.removeListener(this);
		cancelCheck();
		super.onDestroyView();
	}

	@Override
	public void onAddonsChanged() {
		if (getView() != null) {
			refresh.setRefreshing(false);
			rebuild();
		}
	}

	private void refreshAll() {
		graph.addons.refreshAll(() -> {
			if (getView() != null) {
				graph.addonClient.clearCache();
				checked.clear();
				row = null;
				rebuild();
				refresh.setRefreshing(false);
			}
		});
	}

	@Nullable
	private Source find(@Nullable String key) {
		for (Source s : sources) {
			if (s.key.equals(key)) {
				return s;
			}
		}
		return null;
	}

	private void rebuild() {
		sources.clear();
		sources.add(new Source(SportSites.ROW_KEY, getString(R.string.live_sport_tv),
				getString(R.string.live_sport_tv_summary)));
		Map<String, Source> byCatalog = new LinkedHashMap<>();
		for (LiveTv.Row live : LiveTv.rows(graph.addons)) {
			String key = live.source.addon.transportUrl + "|" + live.source.catalog.type + "|"
					+ live.source.catalog.id;
			Source s = byCatalog.get(key);
			if (s == null) {
				String name = live.source.catalog.name != null ? live.source.catalog.name : live.source.catalog.id;
				s = live.isFreeSports()
						? new Source(key, getString(R.string.live_free_sports), getString(R.string.live_free_sports_summary))
						: new Source(key, name, live.source.addon.displayName());
				byCatalog.put(key, s);
			}
			s.rows.add(live);
		}
		sources.addAll(byCatalog.values());

		String keepKey = source != null ? source.key : null;
		String keepRow = row != null ? row.key : null;
		sourceChips.removeAllViews();
		Source keep = null;
		for (Source s : sources) {
			Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, sourceChips, false);
			chip.setId(View.generateViewId());
			chip.setText(s.title);
			chip.setTag(s.key);
			sourceChips.addView(chip);
			if (s.key.equals(keepKey) || keep == null) {
				keep = s;
			}
		}
		source = null;
		row = null;
		selectSource(keep, keepRow);
		if (stillLoadingManifests()) {
			refresh.setRefreshing(true);
		}
	}

	private void selectSource(@NonNull Source picked, @Nullable String keepRow) {
		source = picked;
		for (int i = 0; i < sourceChips.getChildCount(); i++) {
			Chip chip = (Chip) sourceChips.getChildAt(i);
			if (picked.key.equals(chip.getTag()) && !chip.isChecked()) {
				chip.setChecked(true);
			}
		}
		summary.setText(picked.summary);
		summary.setVisibility(picked.summary != null && !picked.summary.isEmpty() ? View.VISIBLE : View.GONE);
		genreChips.removeAllViews();
		LiveTv.Row first = null;
		for (LiveTv.Row r : picked.rows) {
			Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, genreChips, false);
			chip.setId(View.generateViewId());
			chip.setText(r.genre != null ? r.genre : r.name());
			chip.setTag(r.key);
			genreChips.addView(chip);
			if (first == null || r.key.equals(keepRow)) {
				first = r;
			}
		}
		genreScroll.setVisibility(picked.rows.size() > 1 ? View.VISIBLE : View.GONE);
		genreScroll.scrollTo(0, 0);
		if (picked.isSites() || first == null) {
			row = null;
			show(picked);
		} else {
			selectRow(first);
		}
	}

	private void selectRow(@NonNull LiveTv.Row picked) {
		row = picked;
		for (int i = 0; i < genreChips.getChildCount(); i++) {
			Chip chip = (Chip) genreChips.getChildAt(i);
			if (picked.key.equals(chip.getTag()) && !chip.isChecked()) {
				chip.setChecked(true);
			}
		}
		load(picked);
	}

	private void show(@NonNull Source picked) {
		generation++;
		cancelCheck();
		empty.setVisibility(View.GONE);
		loading.setVisibility(View.GONE);
		adapter.replace(picked.isSites() ? SportSites.posters() : new ArrayList<>());
		if (!picked.isSites()) {
			showEmpty(getString(R.string.live_row_nothing));
		}
	}

	private void load(@NonNull LiveTv.Row live) {
		int requestGeneration = ++generation;
		cancelCheck();
		empty.setVisibility(View.GONE);
		List<PosterItem> done = checked.get(live.key);
		if (done != null) {
			loading.setVisibility(View.GONE);
			adapter.replace(done);
			if (done.isEmpty()) {
				showEmpty(getString(R.string.live_row_nothing));
			}
			return;
		}
		adapter.replace(new ArrayList<>());
		loading.setVisibility(View.VISIBLE);
		graph.io.execute(() -> {
			List<Meta> metas = live.source.fetch(graph.addonClient, live.extras());
			graph.main.post(() -> {
				if (getView() == null || requestGeneration != generation) {
					return;
				}
				if (metas == null) {
					loading.setVisibility(View.GONE);
					showEmpty(getString(R.string.discover_failed));
					return;
				}
				check(live, requestGeneration,
						metas.size() > MAX_CHANNELS ? new ArrayList<>(metas.subList(0, MAX_CHANNELS)) : metas);
			});
		});
	}

	private void check(@NonNull LiveTv.Row live, int requestGeneration, @NonNull List<Meta> metas) {
		Set<Meta> working = new HashSet<>();
		boolean[] showPosted = {false};
		Runnable show = () -> {
			showPosted[0] = false;
			if (getView() == null || requestGeneration != generation) {
				return;
			}
			List<PosterItem> items = new ArrayList<>();
			for (Meta m : metas) {
				if (working.contains(m)) {
					items.add(PosterItem.of(m));
				}
			}
			if (items.size() != adapter.getItemCount()) {
				adapter.replace(items);
			}
		};
		batch = graph.liveCheck.check(metas, new LiveCheck.Listener() {
			@Override
			public void onChecked(@NonNull Meta meta, boolean works) {
				if (!works || requestGeneration != generation) {
					return;
				}
				working.add(meta);
				if (!showPosted[0]) {
					showPosted[0] = true;
					graph.main.postDelayed(show, adapter.getItemCount() == 0 ? 0 : SHOW_EVERY_MS);
				}
			}

			@Override
			public void onDone() {
				graph.main.removeCallbacks(show);
				if (getView() == null || requestGeneration != generation) {
					return;
				}
				batch = null;
				show.run();
				loading.setVisibility(View.GONE);
				checked.put(live.key, new ArrayList<>(adapter.items()));
				if (working.isEmpty()) {
					showEmpty(getString(R.string.live_row_nothing));
				}
			}
		});
	}

	private void cancelCheck() {
		if (batch != null) {
			batch.cancel();
			batch = null;
		}
	}

	private boolean stillLoadingManifests() {
		for (InstalledAddon addon : graph.addons.active()) {
			if (addon.manifest == null && !graph.addons.isUnreachable(addon)) {
				return true;
			}
		}
		return false;
	}

	private void showEmpty(@NonNull String text) {
		empty.setText(text);
		empty.setVisibility(View.VISIBLE);
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
