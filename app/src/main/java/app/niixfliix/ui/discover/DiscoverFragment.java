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
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.R;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.Grids;
import app.niixfliix.ui.common.PosterAdapter;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.details.DetailsActivity;

public final class DiscoverFragment extends Fragment implements PosterAdapter.Listener, AddonRepository.Listener {

	private static final int LOAD_MORE_THRESHOLD = 12;

	private final AppGraph graph = AppGraph.get();
	private final List<CatalogSource> sources = new ArrayList<>();
	private final List<CatalogSource> shown = new ArrayList<>();
	private final Set<String> seenIds = new HashSet<>();
	private ChipGroup typeChips;
	private TextInputLayout genreLayout;
	private MaterialAutoCompleteTextView catalogInput;
	private MaterialAutoCompleteTextView genreInput;
	private TextView empty;
	private View loading;
	private PosterAdapter adapter;

	@Nullable private String type;
	@Nullable private CatalogSource source;
	@Nullable private String genre;
	private boolean busy;
	private boolean reachedEnd;
	/** raw count incl. duplicates, that's what skip expects */
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
		typeChips = view.findViewById(R.id.type_chips);
		genreLayout = view.findViewById(R.id.genre_layout);
		catalogInput = view.findViewById(R.id.catalog_input);
		genreInput = view.findViewById(R.id.genre_input);
		// filled from code, restored text wouldn't match
		catalogInput.setSaveEnabled(false);
		genreInput.setSaveEnabled(false);
		empty = view.findViewById(R.id.empty);
		loading = view.findViewById(R.id.loading);
		RecyclerView grid = view.findViewById(R.id.grid);
		GridLayoutManager layout = new GridLayoutManager(requireContext(), Grids.posterSpan(requireContext()));
		grid.setLayoutManager(layout);
		adapter = new PosterAdapter(true, this);
		grid.setAdapter(adapter);
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
		catalogInput.setOnItemClickListener((parent, v, position, id) -> {
			if (position < shown.size()) {
				selectSource(shown.get(position));
			}
		});
		genreInput.setOnItemClickListener((parent, v, position, id) -> {
			List<String> genres = genreOptions();
			boolean hasAll = source != null && !source.catalog.isExtraRequired(Catalog.EXTRA_GENRE);
			genre = position >= genres.size() || hasAll && position == 0 ? null : genres.get(position);
			restart();
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
			adapter.submit(new ArrayList<>());
			showEmpty(getString(R.string.discover_no_catalogs));
			return;
		}
		// set first so the listener ignores the check below
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
		shown.clear();
		List<String> labels = new ArrayList<>();
		for (CatalogSource s : sources) {
			if (newType.equals(s.catalog.type)) {
				shown.add(s);
				labels.add(s.label());
			}
		}
		catalogInput.setSimpleItems(labels.toArray(new String[0]));
		if (!shown.isEmpty()) {
			selectSource(shown.get(0));
		}
	}

	private void selectSource(@NonNull CatalogSource picked) {
		source = picked;
		catalogInput.setText(picked.label(), false);
		List<String> genres = genreOptions();
		boolean required = picked.catalog.isExtraRequired(Catalog.EXTRA_GENRE);
		genreLayout.setVisibility(genres.size() > 1 || required ? View.VISIBLE : View.GONE);
		genreInput.setSimpleItems(genres.toArray(new String[0]));
		genre = required && !picked.catalog.genres().isEmpty() ? picked.catalog.genres().get(0) : null;
		genreInput.setText(genre != null ? genre : getString(R.string.discover_all_genres), false);
		restart();
	}

	@NonNull
	private List<String> genreOptions() {
		List<String> out = new ArrayList<>();
		if (source == null) {
			return out;
		}
		if (!source.catalog.isExtraRequired(Catalog.EXTRA_GENRE)) {
			out.add(getString(R.string.discover_all_genres));
		}
		if (source.catalog.supportsExtra(Catalog.EXTRA_GENRE)) {
			out.addAll(source.catalog.genres());
		}
		return out;
	}

	private void restart() {
		generation++;
		busy = false;
		reachedEnd = false;
		fetched = 0;
		seenIds.clear();
		adapter.submit(new ArrayList<>());
		empty.setVisibility(View.GONE);
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
		// add-on ignored skip
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
		QueueMenu.show(requireContext(), item);
		return true;
	}
}
