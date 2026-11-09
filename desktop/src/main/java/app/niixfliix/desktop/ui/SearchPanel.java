package app.niixfliix.desktop.ui;

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
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.Entry;
import app.niixfliix.desktop.gtk.Flow;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.MainLoop;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.PosterItem;

/**
 * Search, at the top of Discover. While the field has text its results take the place of the browsing
 * below it; while the empty field has focus the recent searches do. {@link Listener#onSearchShown}
 * says which of the two shows.
 */
public final class SearchPanel implements PosterCard.Listener {

	public interface Listener {
		/** {@code shown}: the search area is in place of the browsing. */
		void onSearchShown(boolean shown);
	}

	public static final int FIELD_WIDTH = 720;

	private final DesktopGraph graph = DesktopGraph.get();
	private final Listener listener;
	private final Entry input = Entry.search(I18n.t("search_hint"), Constant.SEARCH_DEBOUNCE_MS);
	private final Box recentSection = Box.column(10);
	private final Flow recentChips = Flow.create(8);
	private final PosterRows results = new PosterRows(this, row -> {
	});
	private final Label status = Label.wrapping(null, "empty-text");
	private final Layers area;
	private final Map<String, PosterRows.Row> rowsByType = new LinkedHashMap<>();
	private final Map<String, Set<String>> idsByType = new HashMap<>();
	private boolean shown;
	private boolean recentShown;
	/** The field was emptied by {@link #close}, so its coming search-changed doesn't show the recent searches. */
	private boolean closing;
	private boolean focusInField;
	private boolean focusInRecent;
	private boolean focusCheckPosted;
	private int generation;
	private int pending;
	private String searched = "";

	public SearchPanel(@NonNull Listener listener) {
		this.listener = listener;
		input.addClass("search-field");
		input.size(FIELD_WIDTH, -1).halign(Widget.ALIGN_START);
		input.onSearch(() -> {
			boolean closed = closing;
			closing = false;
			if (query().isEmpty()) {
				generation++;
				searched = "";
				if (!closed) {
					showRecent();
				}
			} else if (!query().equals(searched)) {
				search();
			}
		});
		input.onActivate(() -> {
			graph.searchHistory.add(query());
			search();
		});
		input.onStopSearch(() -> {
			if (shown) {
				close();
			}
		});
		input.addController(Controller.focus()
				.onFocusIn(() -> focusMoved(true, focusInRecent))
				.onFocusOut(() -> focusMoved(false, focusInRecent)));
		recentSection.addController(Controller.focus()
				.onFocusIn(() -> focusMoved(focusInField, true))
				.onFocusOut(() -> focusMoved(focusInField, false)));
		Button clear = Button.text(I18n.t("action_clear"), "flat");
		clear.set("focus-on-click", false);
		clear.onClick(() -> {
			graph.searchHistory.clear();
			showRecent();
		});
		Box recentHeader = Box.row(0, Label.of(I18n.t("search_recent"), "title-4"), Widget.spacer(), clear);
		recentHeader.size(FIELD_WIDTH, -1).halign(Widget.ALIGN_START);
		recentSection.add(recentHeader, recentChips);
		recentSection.margins(16, 24, 0, 24);
		recentSection.valign(Widget.ALIGN_START);
		showRecentSection(false);
		status.center();
		status.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER).margin(32);
		status.canTarget(false);
		status.visible(false);
		results.view().visible(false);
		area = Layers.overlay(results.view(), status, recentSection);
		area.vexpand(true);
		area.visible(false);
	}

	/** The search field, for above the browsing. */
	@NonNull
	public Widget field() {
		return input;
	}

	/** The results and recent searches, for in place of the browsing. */
	@NonNull
	public Widget area() {
		return area;
	}

	/** Whether the search area shows, in place of the browsing. */
	public boolean isShown() {
		return shown;
	}

	public void focus() {
		input.grabFocus();
	}

	public void searchFor(@NonNull String text) {
		closing = false;
		input.text(text);
		search();
	}

	/** Back to browsing: empties the field and leaves it. */
	public void close() {
		generation++;
		searched = "";
		closing = !input.text().isEmpty();
		input.text("");
		showRecentSection(false);
		setShown(false);
	}

	public void onShown() {
		results.refreshWatched();
		results.onShown();
	}

	@NonNull
	private String query() {
		return input.text().trim();
	}

	/** Focus events come one by one as it moves; what's shown follows where it ends up. */
	private void focusMoved(boolean inField, boolean inRecent) {
		focusInField = inField;
		focusInRecent = inRecent;
		if (focusCheckPosted) {
			return;
		}
		focusCheckPosted = true;
		MainLoop.post(() -> {
			focusCheckPosted = false;
			boolean wanted = focusInField || focusInRecent;
			if (query().isEmpty() && wanted != (shown && recentShown)) {
				showRecent();
			}
		});
	}

	private void showRecentSection(boolean show) {
		recentShown = show;
		recentSection.visible(show);
	}

	private void setShown(boolean show) {
		area.visible(show);
		if (show != shown) {
			shown = show;
			listener.onSearchShown(show);
		}
	}

	/** The empty field: recent searches while it has focus, otherwise the browsing. */
	private void showRecent() {
		results.view().visible(false);
		status.visible(false);
		recentChips.clear();
		List<String> recent = graph.searchHistory.all();
		boolean showRecent = (focusInField || focusInRecent) && !recent.isEmpty();
		showRecentSection(showRecent);
		if (showRecent) {
			for (String q : recent) {
				Button chip = Button.text(q, "chip");
				chip.set("focus-on-click", false);
				chip.onClick(() -> {
					input.text(q);
					search();
				});
				recentChips.add(chip);
			}
		}
		setShown(showRecent);
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
		showRecentSection(false);
		results.view().visible(true);
		setShown(true);
		results.onShown();
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
			if (isListed(title.imdbId)) {
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

	private boolean isListed(String id) {
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
