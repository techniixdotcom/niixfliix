package app.niixfliix.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.R;
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.Translations;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.app.Constant;
import app.niixfliix.ui.Formats;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.QueueMenu;
import app.niixfliix.ui.common.RowsAdapter;
import app.niixfliix.ui.common.TabToolbar;
import app.niixfliix.ui.details.DetailsActivity;

public final class SearchFragment extends Fragment implements RowsAdapter.Listener {

	private final AppGraph graph = AppGraph.get();
	private final Runnable searchNow = this::search;
	private final Map<String, RowsAdapter.Row> rowsByType = new HashMap<>();
	private final Map<String, Set<String>> idsByType = new HashMap<>();
	private EditText input;
	@Nullable private String pendingQuery;
	private View recentSection;
	private ChipGroup recentChips;
	private RecyclerView results;
	private TextView status;
	private RowsAdapter adapter;
	private int generation;
	private int pending;

	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_search, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		TabToolbar.setUp(this, view, R.string.nav_search);
		input = view.findViewById(R.id.search_input);
		recentSection = view.findViewById(R.id.recent_section);
		recentChips = view.findViewById(R.id.recent_chips);
		results = view.findViewById(R.id.results);
		status = view.findViewById(R.id.status);
		results.setLayoutManager(new LinearLayoutManager(requireContext()));
		adapter = new RowsAdapter(this);
		results.setAdapter(adapter);
		view.findViewById(R.id.recent_clear).setOnClickListener(v -> {
			graph.searchHistory.clear();
			showRecent();
		});
		input.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				graph.main.removeCallbacks(searchNow);
				if (query().isEmpty()) {
					generation++;
					showRecent();
				} else {
					graph.main.postDelayed(searchNow, Constant.SEARCH_DEBOUNCE_MS);
				}
			}
		});
		input.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_SEARCH) {
				graph.main.removeCallbacks(searchNow);
				graph.searchHistory.add(query());
				search();
				return true;
			}
			return false;
		});
		showRecent();
		String pending = pendingQuery;
		pendingQuery = null;
		if (pending != null) {
			searchFor(pending);
		}
	}

	@Override
	public void onDestroyView() {
		graph.main.removeCallbacks(searchNow);
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

	public void searchFor(@NonNull String text) {
		if (getView() == null || input == null) {
			pendingQuery = text;
			return;
		}
		input.setText(text);
		input.setSelection(input.length());
		graph.main.removeCallbacks(searchNow);
		search();
	}

	@NonNull
	private String query() {
		return input.getText() != null ? input.getText().toString().trim() : "";
	}

	private void showRecent() {
		results.setVisibility(View.GONE);
		status.setVisibility(View.GONE);
		recentChips.removeAllViews();
		List<String> recent = graph.searchHistory.all();
		recentSection.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
		for (String q : recent) {
			Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_recent_chip, recentChips, false);
			chip.setText(q);
			chip.setOnClickListener(v -> {
				input.setText(q);
				input.setSelection(q.length());
			});
			recentChips.addView(chip);
		}
	}

	private void search() {
		String q = query();
		if (q.isEmpty() || getView() == null) {
			return;
		}
		int requestGeneration = ++generation;
		rowsByType.clear();
		idsByType.clear();
		adapter.clear();
		recentSection.setVisibility(View.GONE);
		results.setVisibility(View.VISIBLE);
		pending = 0;
		for (InstalledAddon addon : graph.addons.active()) {
			if (addon.manifest == null) {
				continue;
			}
			for (Catalog catalog : addon.manifest.validCatalogs()) {
				if (catalog.worksForSearch()) {
					pending++;
					searchCatalog(addon, catalog, q, requestGeneration);
				}
			}
		}
		if (pending > 0 && graph.translations.language() != null) {
			pending++;
			searchLocalTitles(q, requestGeneration);
		}
		setStatus(pending == 0 ? R.string.search_no_sources : R.string.search_searching);
	}

	private void searchLocalTitles(String q, int requestGeneration) {
		graph.io.execute(() -> {
			List<Translations.Found> found = graph.translations.search(q);
			graph.main.post(() -> onLocalTitles(requestGeneration, found));
		});
	}

	private void onLocalTitles(int requestGeneration, List<Translations.Found> found) {
		if (getView() == null || requestGeneration != generation) {
			return;
		}
		for (Translations.Found title : found) {
			if (isShown(title.imdbId)) {
				continue;
			}
			pending++;
			graph.io.execute(() -> {
				Meta meta = findMeta(title);
				List<Meta> result = meta != null ? Collections.singletonList(meta) : Collections.emptyList();
				graph.main.post(() -> onResults(requestGeneration, result));
			});
		}
		onResults(requestGeneration, Collections.emptyList());
	}

	@Nullable
	private Meta findMeta(Translations.Found title) {
		String other = Meta.TYPE_SERIES.equals(title.type) ? Meta.TYPE_MOVIE : Meta.TYPE_SERIES;
		for (String type : new String[] {title.type, other}) {
			Meta meta = MetaFinder.find(graph.addons, graph.addonClient, type, title.imdbId);
			if (meta != null) {
				if (meta.id == null) {
					meta.id = title.imdbId;
				}
				if (meta.type == null) {
					meta.type = type;
				}
				return meta;
			}
		}
		return null;
	}

	private boolean isShown(String id) {
		for (Set<String> ids : idsByType.values()) {
			if (ids.contains(id)) {
				return true;
			}
		}
		return false;
	}

	private void searchCatalog(InstalledAddon addon, Catalog catalog, String q, int requestGeneration) {
		graph.io.execute(() -> {
			List<Meta> metas = CatalogSource.fetch(graph.addonClient, addon, catalog,
					Collections.singletonMap(Catalog.EXTRA_SEARCH, q));
			List<Meta> result = metas != null ? metas : Collections.emptyList();
			graph.main.post(() -> onResults(requestGeneration, result));
		});
	}

	private void onResults(int requestGeneration, List<Meta> metas) {
		if (getView() == null || requestGeneration != generation) {
			return;
		}
		pending--;
		for (Meta m : metas) {
			String type = m.type != null ? m.type : "";
			Set<String> ids = idsByType.computeIfAbsent(type, t -> new HashSet<>());
			if (m.id == null || !ids.add(m.id)) {
				continue;
			}
			RowsAdapter.Row row = rowsByType.get(type);
			if (row == null) {
				row = new RowsAdapter.Row(requestGeneration + "|" + type, Formats.typeLabel(requireContext(), type), null);
				row.state = RowsAdapter.State.READY;
				rowsByType.put(type, row);
				adapter.add(row);
			}
			row.items.add(PosterItem.of(m));
		}
		for (RowsAdapter.Row row : rowsByType.values()) {
			ReleaseOrder.sortNewestFirst(row.items, item -> item.released);
			adapter.update(row);
		}
		if (!rowsByType.isEmpty()) {
			status.setVisibility(View.GONE);
		} else if (pending <= 0) {
			setStatus(R.string.search_no_results);
		}
	}

	private void setStatus(int text) {
		status.setText(text);
		status.setVisibility(View.VISIBLE);
	}

	@Override
	public void onLoadRow(@NonNull RowsAdapter.Row row) {
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		graph.searchHistory.add(query());
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
