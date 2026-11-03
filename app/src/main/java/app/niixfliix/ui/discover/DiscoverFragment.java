package app.niixfliix.ui.discover;

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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import app.niixfliix.R;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.Images;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.DiscoverGenres;
import app.niixfliix.ui.common.Grids;
import app.niixfliix.ui.common.PosterAdapter;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.common.TabToolbar;
import app.niixfliix.ui.details.DetailsActivity;

public final class DiscoverFragment extends Fragment implements PosterAdapter.Listener, AddonRepository.Listener {

	private static final int LOAD_MORE_THRESHOLD = 12;

	private final AppGraph graph = AppGraph.get();
	private final List<CatalogSource> sources = new ArrayList<>();
	private final Set<String> seenIds = new HashSet<>();
	private ChipGroup typeChips;
	private ChipGroup genreChips;
	private View genreScroll;
	private TextView empty;
	private View loading;
	private PosterAdapter adapter;

	@Nullable private String type;
	@Nullable private CatalogSource source;
	@Nullable private String genre;
	@NonNull private DiscoverGenres genres = new DiscoverGenres(new ArrayList<>());
	private boolean busy;
	private boolean reachedEnd;
	private int fetched;
	private int generation;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_discover, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		TabToolbar.setUp(this, view, R.string.nav_discover);
		typeChips = view.findViewById(R.id.type_chips);
		genreChips = view.findViewById(R.id.genre_chips);
		genreScroll = view.findViewById(R.id.genre_scroll);
		empty = view.findViewById(R.id.empty);
		loading = view.findViewById(R.id.loading);
		RecyclerView grid = view.findViewById(R.id.grid);
		GridLayoutManager layout = new GridLayoutManager(requireContext(), Grids.posterSpan(requireContext()));
		grid.setLayoutManager(layout);
		adapter = new PosterAdapter(true, this);
		grid.setAdapter(adapter);
		Images.pauseWhileFlinging(grid);
		grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
				if (dy > 0 && layout.findLastVisibleItemPosition() >= adapter.getItemCount() - LOAD_MORE_THRESHOLD) {
					loadPage();
				}
			}
		});
		typeChips.setOnCheckedStateChangeListener((group, ids) -> {
			if (ids.isEmpty()) {
				return;
			}
			Chip chip = group.findViewById(ids.get(0));
			String picked = chip != null ? (String) chip.getTag() : null;
			if (picked != null && !picked.equals(type)) {
				selectType(picked);
			}
		});
		genreChips.setOnCheckedStateChangeListener((group, ids) -> {
			if (ids.isEmpty()) {
				return;
			}
			Chip chip = group.findViewById(ids.get(0));
			if (chip == null) {
				return;
			}
			String picked = (String) chip.getTag();
			if (!Objects.equals(picked, genre)) {
				selectGenre(picked);
			}
		});
		graph.addons.addListener(this);
		rebuild();
	}

	@Override
	public void onDestroyView() {
		graph.addons.removeListener(this);
		super.onDestroyView();
	}

	@Override
	public void onResume() {
		super.onResume();
		adapter.refreshWatched();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden && getView() != null) {
			adapter.refreshWatched();
		}
	}

	@Override
	public void onAddonsChanged() {
		if (getView() != null) {
			rebuild();
		}
	}

	private void rebuild() {
		sources.clear();
		Set<String> types = new LinkedHashSet<>();
		types.add(Meta.TYPE_MOVIE);
		types.add(Meta.TYPE_SERIES);
		Set<String> available = new HashSet<>();
		for (InstalledAddon addon : graph.addons.active()) {
			if (addon.manifest == null) {
				continue;
			}
			for (Catalog c : addon.manifest.validCatalogs()) {
				if (c.worksForDiscover()) {
					sources.add(new CatalogSource(addon, c));
					types.add(c.type);
					available.add(c.type);
				}
			}
		}
		typeChips.removeAllViews();
		String keep = null;
		for (String t : types) {
			if (!available.contains(t)) {
				continue;
			}
			Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, typeChips, false);
			chip.setId(View.generateViewId());
			chip.setText(Formats.typeLabel(requireContext(), t));
			chip.setTag(t);
			typeChips.addView(chip);
			if (t.equals(type) || keep == null) {
				keep = t;
			}
		}
		if (keep == null) {
			type = null;
			source = null;
			adapter.replace(new ArrayList<>());
			showEmpty(getString(R.string.discover_no_catalogs));
			return;
		}
		type = keep;
		for (int i = 0; i < typeChips.getChildCount(); i++) {
			Chip chip = (Chip) typeChips.getChildAt(i);
			if (keep.equals(chip.getTag())) {
				chip.setChecked(true);
			}
		}
		selectType(keep);
	}

	private void selectType(@NonNull String newType) {
		type = newType;
		List<CatalogSource> ofType = new ArrayList<>();
		for (CatalogSource s : sources) {
			if (newType.equals(s.catalog.type)) {
				ofType.add(s);
			}
		}
		genres = new DiscoverGenres(ofType);
		genreChips.removeAllViews();
		if (genres.hasAll()) {
			addGenreChip(getString(R.string.discover_all_genres), null);
		}
		for (String g : genres.genres()) {
			addGenreChip(g, g);
		}
		genreScroll.setVisibility(genreChips.getChildCount() > 1 ? View.VISIBLE : View.GONE);
		genreScroll.scrollTo(0, 0);
		selectGenre(genres.firstChoice());
	}

	private void addGenreChip(@NonNull String label, @Nullable String value) {
		Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, genreChips, false);
		chip.setId(View.generateViewId());
		chip.setText(label);
		chip.setTag(value);
		genreChips.addView(chip);
	}

	private void selectGenre(@Nullable String picked) {
		genre = picked;
		source = genres.sourceFor(picked);
		for (int i = 0; i < genreChips.getChildCount(); i++) {
			Chip chip = (Chip) genreChips.getChildAt(i);
			if (Objects.equals(chip.getTag(), picked) && !chip.isChecked()) {
				chip.setChecked(true);
			}
		}
		restart();
	}

	private void restart() {
		generation++;
		busy = false;
		reachedEnd = false;
		fetched = 0;
		seenIds.clear();
		adapter.replace(new ArrayList<>());
		empty.setVisibility(View.GONE);
		if (source == null) {
			showEmpty(getString(R.string.discover_empty));
			return;
		}
		loadPage();
	}

	private void loadPage() {
		CatalogSource current = source;
		if (current == null || busy || reachedEnd) {
			return;
		}
		busy = true;
		loading.setVisibility(View.VISIBLE);
		int skip = fetched;
		int requestGeneration = generation;
		Map<String, String> extras = new LinkedHashMap<>();
		if (genre != null) {
			extras.put(Catalog.EXTRA_GENRE, genre);
		}
		boolean canPage = current.catalog.supportsExtra(Catalog.EXTRA_SKIP);
		if (skip > 0) {
			if (!canPage) {
				busy = false;
				reachedEnd = true;
				loading.setVisibility(View.GONE);
				return;
			}
			extras.put(Catalog.EXTRA_SKIP, String.valueOf(skip));
		}
		graph.io.execute(() -> {
			List<Meta> result = current.fetch(graph.addonClient, extras);
			graph.main.post(() -> onPage(requestGeneration, result));
		});
	}

	private void onPage(int requestGeneration, @Nullable List<Meta> metas) {
		if (getView() == null || requestGeneration != generation) {
			return;
		}
		busy = false;
		loading.setVisibility(View.GONE);
		if (metas == null) {
			reachedEnd = true;
			if (adapter.getItemCount() == 0) {
				showEmpty(getString(R.string.discover_failed));
			}
			return;
		}
		fetched += metas.size();
		List<PosterItem> fresh = new ArrayList<>();
		for (Meta m : metas) {
			if (m.id != null && seenIds.add(m.id)) {
				fresh.add(PosterItem.of(m));
			}
		}
		if (fresh.isEmpty()) {
			reachedEnd = true;
		}
		adapter.append(fresh);
		if (adapter.getItemCount() == 0) {
			showEmpty(getString(R.string.discover_empty));
		}
	}

	private void showEmpty(String text) {
		empty.setText(text);
		empty.setVisibility(View.VISIBLE);
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		DetailsActivity.start(requireContext(), item);
	}

	@Override
	public boolean onPosterLongClick(@NonNull PosterItem item) {
		QueueMenu.show(requireContext(), item, null, null, () -> {
			if (getView() != null) {
				adapter.refreshWatched();
			}
		});
		return true;
	}
}
