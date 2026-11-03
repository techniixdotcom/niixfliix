package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Flow;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.ui.Fmt;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.PosterCard;
import app.niixfliix.desktop.ui.PosterGrid;
import app.niixfliix.desktop.ui.PosterMenu;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.DiscoverGenres;
import app.niixfliix.ui.common.PosterItem;

public final class DiscoverPage implements Page, PosterCard.Listener, AddonRepository.Listener {

	private final DesktopGraph graph = DesktopGraph.get();
	private final Box root = Box.column(0);
	private final Box typeChips = Box.row(8);
	private final Flow genreChips = Flow.create(8);
	private final List<Button> genreButtons = new ArrayList<>();
	private final PosterGrid grid = new PosterGrid(this);
	private final Label empty = Label.wrapping(null, "empty-text");
	private final Progress loading = Progress.spinner(40);
	private final List<CatalogSource> sources = new ArrayList<>();
	private final Set<String> seenIds = new HashSet<>();
	private final List<PosterItem> items = new ArrayList<>();

	@Nullable private String type;
	@Nullable private CatalogSource source;
	@Nullable private String genre;
	@NonNull private DiscoverGenres genres = new DiscoverGenres(new ArrayList<>());
	private boolean busy;
	private boolean reachedEnd;
	private int fetched;
	private int generation;

	public DiscoverPage() {
		typeChips.halign(Widget.ALIGN_START);
		genreChips.halign(Widget.ALIGN_FILL);
		genreChips.addClass("genre-chips");
		Box top = Box.column(14, Label.of(I18n.t("nav_discover"), "title-1"), typeChips, genreChips);
		top.margins(20, 24, 8, 24);
		empty.center();
		empty.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER).margin(32);
		empty.canTarget(false);
		empty.visible(false);
		loading.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_END).margin(16);
		loading.visible(false);
		Layers center = Layers.overlay(grid.view(), empty, loading);
		center.vexpand(true);
		root.add(top, center);
		grid.setOnEnd(this::loadPage);
		graph.addons.addListener(this);
		rebuild();
	}

	@NonNull
	@Override
	public Widget view() {
		return root;
	}

	@Override
	public void onShow() {
		grid.refresh();
		grid.onShown();
	}

	@Override
	public void onAddonsChanged() {
		rebuild();
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
		typeChips.clear();
		String keep = null;
		for (String t : types) {
			if (available.contains(t) && (t.equals(type) || keep == null)) {
				keep = t;
			}
		}
		Button first = null;
		for (String t : types) {
			if (!available.contains(t)) {
				continue;
			}
			Button chip = Button.toggle(Fmt.typeLabel(t), "chip");
			if (first == null) {
				first = chip;
			} else {
				chip.group(first);
			}
			chip.active(t.equals(keep));
			chip.onToggled(() -> {
				if (chip.isActive() && !t.equals(type)) {
					selectType(t);
				}
			});
			typeChips.add(chip);
		}
		if (keep == null) {
			type = null;
			source = null;
			items.clear();
			grid.show(items);
			showEmpty(I18n.t("discover_no_catalogs"));
			return;
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
		genreChips.clear();
		genreButtons.clear();
		String first = genres.firstChoice();
		if (genres.hasAll()) {
			addGenreChip(I18n.t("discover_all_genres"), null, first);
		}
		for (String g : genres.genres()) {
			addGenreChip(g, g, first);
		}
		genreChips.visible(genreButtons.size() > 1);
		genre = first;
		source = genres.sourceFor(first);
		restart();
	}

	private void addGenreChip(@NonNull String label, @Nullable String value, @Nullable String selected) {
		Button chip = Button.toggle(label, "chip");
		if (!genreButtons.isEmpty()) {
			chip.group(genreButtons.get(0));
		}
		chip.active(Objects.equals(value, selected));
		chip.onToggled(() -> {
			if (chip.isActive() && !Objects.equals(value, genre)) {
				genre = value;
				source = genres.sourceFor(value);
				restart();
			}
		});
		genreButtons.add(chip);
		genreChips.add(chip);
	}

	private void restart() {
		generation++;
		busy = false;
		reachedEnd = false;
		fetched = 0;
		seenIds.clear();
		items.clear();
		grid.show(items);
		empty.visible(false);
		if (source == null) {
			showEmpty(I18n.t("discover_empty"));
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
		loading.visible(true);
		int skip = fetched;
		int requestGeneration = generation;
		Map<String, String> extras = new LinkedHashMap<>();
		if (genre != null) {
			extras.put(Catalog.EXTRA_GENRE, genre);
		}
		if (skip > 0) {
			if (!current.catalog.supportsExtra(Catalog.EXTRA_SKIP)) {
				busy = false;
				reachedEnd = true;
				loading.visible(false);
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
		if (requestGeneration != generation) {
			return;
		}
		busy = false;
		loading.visible(false);
		if (metas == null) {
			reachedEnd = true;
			if (items.isEmpty()) {
				showEmpty(I18n.t("discover_failed"));
			}
			return;
		}
		fetched += metas.size();
		int before = items.size();
		for (Meta m : metas) {
			if (m.id != null && seenIds.add(m.id)) {
				items.add(PosterItem.of(m));
			}
		}
		if (items.size() == before) {
			reachedEnd = true;
		}
		grid.show(items);
		if (items.isEmpty()) {
			showEmpty(I18n.t("discover_empty"));
		}
	}

	private void showEmpty(String text) {
		empty.text(text);
		empty.visible(true);
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		MainWindow.get().openDetails(item);
	}

	@Override
	public void onPosterMenu(@NonNull PosterItem item, @NonNull Widget card, double x, double y) {
		PosterMenu.show(card, x, y, item, null, null, grid::refresh);
	}
}
