package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.Translations;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.Constant;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Entry;
import app.niixfliix.desktop.gtk.Flow;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.ui.Fmt;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.PosterCard;
import app.niixfliix.desktop.ui.PosterMenu;
import app.niixfliix.desktop.ui.PosterRows;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.PosterItem;

public final class SearchPage implements Page, PosterCard.Listener {

	private static final int FIELD_WIDTH = 720;

	private final DesktopGraph graph = DesktopGraph.get();
	private final Box root = Box.column(0);
	private final Entry input = Entry.search(I18n.t("search_hint"), Constant.SEARCH_DEBOUNCE_MS);
	private final Box recentSection = Box.column(10);
	private final Flow recentChips = Flow.create(8);
	private final PosterRows results = new PosterRows(this, row -> {
	});
	private final Label status = Label.wrapping(null, "empty-text");
	private final Map<String, PosterRows.Row> rowsByType = new LinkedHashMap<>();
	private final Map<String, Set<String>> idsByType = new HashMap<>();
	private int generation;
	private int pending;
	private String searched = "";

	public SearchPage() {
		input.addClass("search-field");
		input.size(FIELD_WIDTH, -1).halign(Widget.ALIGN_START);
		input.onSearch(() -> {
			if (query().isEmpty()) {
				generation++;
				searched = "";
				showRecent();
			} else if (!query().equals(searched)) {
				search();
			}
		});
		input.onActivate(() -> {
			graph.searchHistory.add(query());
			search();
		});
		Button clear = Button.text(I18n.t("action_clear"), "flat");
		clear.onClick(() -> {
			graph.searchHistory.clear();
			showRecent();
		});
		Box recentHeader = Box.row(0, Label.of(I18n.t("search_recent"), "title-4"), Widget.spacer(), clear);
		recentHeader.size(FIELD_WIDTH, -1).halign(Widget.ALIGN_START);
		recentSection.add(recentHeader, recentChips);
		recentSection.margins(16, 24, 0, 24);
		recentSection.valign(Widget.ALIGN_START);
		status.center();
		status.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER).margin(32);
		status.canTarget(false);
		Box top = Box.column(14, Label.of(I18n.t("nav_search"), "title-1"), input);
		top.margins(20, 24, 8, 24);
		Layers center = Layers.overlay(results.view(), status, recentSection);
		center.vexpand(true);
		root.add(top, center);
		showRecent();
	}

	@NonNull
	@Override
	public Widget view() {
		return root;
	}

	@Override
	public void onShow() {
		results.refreshWatched();
		results.onShown();
		input.grabFocus();
	}

	public void searchFor(@NonNull String text) {
		input.text(text);
		search();
	}

	@NonNull
	private String query() {
		return input.text().trim();
	}

	private void showRecent() {
		results.view().visible(false);
		status.visible(false);
		recentChips.clear();
		List<String> recent = graph.searchHistory.all();
		recentSection.visible(!recent.isEmpty());
		for (String q : recent) {
			Button chip = Button.text(q, "chip");
			chip.onClick(() -> {
				input.text(q);
				search();
			});
			recentChips.add(chip);
		}
	}

	private void search() {
		String q = query();
		if (q.isEmpty()) {
			return;
		}
		int requestGeneration = ++generation;
		searched = q;
		rowsByType.clear();
		idsByType.clear();
		results.setRows(null, new ArrayList<>());
		results.scrollToTop();
		recentSection.visible(false);
		results.view().visible(true);
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
		setStatus(I18n.t(pending == 0 ? "search_no_sources" : "search_searching"));
	}

	private void searchLocalTitles(String q, int requestGeneration) {
		graph.io.execute(() -> {
			List<Translations.Found> found = graph.translations.search(q);
			graph.main.post(() -> onLocalTitles(requestGeneration, found));
		});
	}

	private void onLocalTitles(int requestGeneration, List<Translations.Found> found) {
		if (requestGeneration != generation) {
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
		if (requestGeneration != generation) {
			return;
		}
		pending--;
		boolean added = false;
		for (Meta m : metas) {
			String type = m.type != null ? m.type : "";
			Set<String> ids = idsByType.computeIfAbsent(type, t -> new HashSet<>());
			if (m.id == null || !ids.add(m.id)) {
				continue;
			}
			PosterRows.Row row = rowsByType.get(type);
			if (row == null) {
				row = new PosterRows.Row(requestGeneration + "|" + type, Fmt.typeLabel(type), null);
				row.state = PosterRows.State.READY;
				row.requested = true;
				rowsByType.put(type, row);
				added = true;
			}
			row.items.add(PosterItem.of(m));
		}
		for (PosterRows.Row row : rowsByType.values()) {
			ReleaseOrder.sortNewestFirst(row.items, item -> item.released);
		}
		if (added) {
			results.setRows(null, new ArrayList<>(rowsByType.values()));
		} else {
			for (PosterRows.Row row : rowsByType.values()) {
				results.update(row);
			}
		}
		if (!rowsByType.isEmpty()) {
			status.visible(false);
		} else if (pending <= 0) {
			setStatus(I18n.t("search_no_results"));
		}
	}

	private void setStatus(String text) {
		status.text(text);
		status.visible(true);
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		graph.searchHistory.add(query());
		MainWindow.get().openDetails(item);
	}

	@Override
	public void onPosterMenu(@NonNull PosterItem item, @NonNull Widget card, double x, double y) {
		PosterMenu.show(card, x, y, item, null, null, results::refreshWatched);
	}
}
