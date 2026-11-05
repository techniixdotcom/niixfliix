package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.ui.Fmt;
import app.niixfliix.desktop.ui.HeroBanner;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.PosterCard;
import app.niixfliix.desktop.ui.PosterMenu;
import app.niixfliix.desktop.ui.PosterRows;
import app.niixfliix.desktop.ui.Toast;
import app.niixfliix.ui.common.CatalogSource;
import app.niixfliix.ui.common.HomeHero;
import app.niixfliix.ui.common.LibraryToggle;
import app.niixfliix.ui.common.PosterItem;

public final class HomePage implements Page, PosterCard.Listener, AddonRepository.Listener, HeroBanner.Listener {

	private static final String CONTINUE_KEY = "continue";

	private final DesktopGraph graph = DesktopGraph.get();
	private static final int BAR_HEIGHT = 72;
	private static final double SOLID_BAR_AFTER_PX = 8;
	private static final double STOP_ROTATING_AFTER_PX = 320;

	private final Widget root;
	private final Box bar;
	private final Box barSpace = Box.column(0);
	private final List<HomeHero> heroes = new ArrayList<>();
	private final Runnable rotate = this::rotate;
	private int heroIndex;
	private boolean showing;
	private boolean hovered;
	private final PosterRows rows = new PosterRows(this, this::loadRow);
	private final Map<PosterRows.Row, CatalogSource> sources = new HashMap<>();
	private final Label empty = Label.wrapping(I18n.t("home_empty"), "empty-text");
	private final Button refresh = Button.icon(Icons.name("refresh"), I18n.t("action_retry"));
	@Nullable private PosterRows.Row continueRow;
	@Nullable private PosterRows.Row heroRow;
	private final HeroBanner banner = new HeroBanner(this);
	private final Box header = Box.column(0);
	@Nullable private Widget shownNotice;

	public HomePage() {
		refresh.onClick(this::refreshAll);
		banner.view().visible(false);
		barSpace.size(-1, BAR_HEIGHT);
		header.add(barSpace, banner.view());
		bar = Box.row(0, Label.of(I18n.t("nav_home"), "title-1"), Widget.spacer(), refresh);
		bar.addClass("page-top", "home-bar");
		bar.size(-1, BAR_HEIGHT);
		bar.valign(Widget.ALIGN_START);
		empty.center();
		empty.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER).margin(32);
		empty.canTarget(false);
		empty.visible(false);
		Layers center = Layers.overlay(rows.view(), empty);
		center.vexpand(true);
		root = Layers.overlay(center, bar);
		rows.onScrolled(this::updateBar);
		graph.addons.addListener(this);
		buildRows(false);
	}

	@NonNull
	@Override
	public Widget view() {
		return root;
	}

	@Override
	public void onShow() {
		showing = true;
		updateContinueWatching();
		rows.refreshWatched();
		rows.onShown();
		showHero();
		scheduleRotate();
	}

	@Override
	public void onHide() {
		showing = false;
		graph.main.removeCallbacks(rotate);
	}

	@Override
	public void onAddonsChanged() {
		refresh.sensitive(true);
		buildRows(true);
	}

	private void refreshAll() {
		refresh.sensitive(false);
		graph.addons.refreshAll(() -> {
			graph.addonClient.clearCache();
			refresh.sensitive(true);
			buildRows(false);
		});
	}

	private void buildRows(boolean keepLoaded) {
		sources.clear();
		heroRow = null;
		List<PosterRows.Row> list = new ArrayList<>();
		continueRow = continueWatchingRow();
		if (continueRow != null) {
			list.add(continueRow);
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
				String title = I18n.t("row_title", name, Fmt.typeLabel(catalog.type));
				String key = addon.transportUrl + "|" + catalog.type + "|" + catalog.id;
				String subtitle = addon.displayName();
				PosterRows.Row old = keepLoaded ? rows.row(key) : null;
				PosterRows.Row row = old != null && isLoaded(old) && old.title.equals(title)
						&& subtitle.equals(old.subtitle) ? old : new PosterRows.Row(key, title, subtitle);
				sources.put(row, new CatalogSource(addon, catalog));
				list.add(row);
				if (heroRow == null) {
					heroRow = row;
				}
			}
		}
		if (shownNotice != null) {
			header.remove(shownNotice);
		}
		shownNotice = notice();
		if (shownNotice != null) {
			header.add(shownNotice);
		}
		if (heroRow == null) {
			heroes.clear();
		} else if (heroRow.state == PosterRows.State.READY) {
			pickHero(heroRow.items);
		}
		showHero();
		rows.setRows(header, list);
		boolean loading = stillLoadingManifests();
		empty.visible(rows.isEmpty() && !loading);
		if (!loading) {
			graph.warmUpContinueWatching();
		}
	}

	@Nullable
	private HomeHero currentHero() {
		return heroes.isEmpty() ? null : heroes.get(Math.min(heroIndex, heroes.size() - 1));
	}

	private void showHero() {
		HomeHero current = currentHero();
		banner.view().visible(current != null);
		barSpace.visible(current == null);
		if (current != null) {
			banner.show(current, graph.library.contains(current.item.id));
			banner.showDots(heroes.size(), heroIndex);
		}
		updateBar();
	}

	private void updateBar() {
		bar.toggleClass("scrolled", heroes.isEmpty() || rows.scrollOffset() > SOLID_BAR_AFTER_PX);
	}

	private void scheduleRotate() {
		graph.main.removeCallbacks(rotate);
		if (showing && heroes.size() > 1) {
			graph.main.postDelayed(rotate, HomeHero.ROTATE_EVERY_MS);
		}
	}

	private void rotate() {
		if (showing && !hovered && heroes.size() > 1 && rows.scrollOffset() < STOP_ROTATING_AFTER_PX) {
			moveTo((heroIndex + 1) % heroes.size());
		}
		scheduleRotate();
	}

	private void moveTo(int index) {
		if (index < 0 || index >= heroes.size()) {
			return;
		}
		heroIndex = index;
		HomeHero next = heroes.get(index);
		banner.fadeTo(next, graph.library.contains(next.item.id), () -> banner.showDots(heroes.size(), heroIndex));
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
		showHero();
		scheduleRotate();
		for (PosterItem p : picked) {
			graph.io.execute(() -> {
				Meta found = MetaFinder.find(graph.addons, graph.addonClient, p.type, p.id);
				Meta meta = found != null ? graph.translations.translate(found) : null;
				graph.main.post(() -> {
					if (meta == null) {
						return;
					}
					for (int i = 0; i < heroes.size(); i++) {
						if (heroes.get(i).item.id.equals(p.id)) {
							heroes.set(i, new HomeHero(p, meta));
							if (i == heroIndex) {
								showHero();
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

	@Override
	public void onPick(int index) {
		moveTo(index);
		scheduleRotate();
	}

	@Override
	public void onHover(boolean inside) {
		hovered = inside;
		if (!inside) {
			scheduleRotate();
		}
	}

	@Override
	public void onPlay(@NonNull HomeHero clicked) {
		MainWindow.get().openDetails(clicked.item, true);
	}

	@Override
	public void onSave(@NonNull HomeHero clicked) {
		boolean saved = LibraryToggle.toggle(graph.library, clicked.item);
		Toast.show(I18n.t(saved ? "library_added" : "library_removed"));
		showHero();
	}

	@Override
	public void onInfo(@NonNull HomeHero clicked) {
		MainWindow.get().openDetails(clicked.item);
	}

	private static boolean isLoaded(PosterRows.Row row) {
		return row.state == PosterRows.State.READY || row.state == PosterRows.State.EMPTY;
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
	private Widget notice() {
		List<InstalledAddon> down = graph.addons.unreachableToReport();
		if (down.isEmpty()) {
			return null;
		}
		List<String> names = new ArrayList<>();
		for (InstalledAddon a : down) {
			names.add(a.displayName());
		}
		Label text = Label.wrapping(I18n.plural("addons_down", down.size(),
				String.join(I18n.t("list_separator"), names)));
		text.hexpand(true);
		Button retry = Button.text(I18n.t("action_retry"), "flat");
		retry.valign(Widget.ALIGN_CENTER);
		retry.onClick(this::refreshAll);
		Box box = Box.row(12, text, retry);
		box.addClass("card", "padded");
		box.margins(12, 24, 0, 24);
		return box;
	}

	@Nullable
	private PosterRows.Row continueWatchingRow() {
		List<HistoryEntry> entries = graph.history.continueWatching();
		if (entries.isEmpty()) {
			return null;
		}
		PosterRows.Row row = new PosterRows.Row(CONTINUE_KEY, I18n.t("row_continue_watching"), null);
		row.state = PosterRows.State.READY;
		row.requested = true;
		for (HistoryEntry e : entries) {
			row.items.add(PosterItem.of(e, true));
		}
		return row;
	}

	private void updateContinueWatching() {
		PosterRows.Row latest = continueWatchingRow();
		if (latest != null && continueRow != null) {
			continueRow.items = latest.items;
			rows.update(continueRow);
		} else if (latest != null || continueRow != null) {
			buildRows(true);
		}
	}

	private void loadRow(PosterRows.Row row) {
		CatalogSource source = sources.get(row);
		if (source == null) {
			return;
		}
		graph.io.execute(() -> {
			List<Meta> result = source.fetch(graph.addonClient, Collections.emptyMap());
			graph.main.post(() -> {
				if (result == null) {
					row.state = PosterRows.State.FAILED;
				} else {
					row.items = PosterItem.ofMetas(result);
					row.state = result.isEmpty() ? PosterRows.State.EMPTY : PosterRows.State.READY;
				}
				rows.update(row);
				if (row == heroRow && row.state == PosterRows.State.READY) {
					pickHero(row.items);
				}
			});
		});
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		MainWindow.get().openDetails(item);
	}

	@Override
	public void onPosterMenu(@NonNull PosterItem item, @NonNull Widget card, double x, double y) {
		boolean inContinue = continueRow != null && continueRow.items.contains(item);
		PosterMenu.show(card, x, y, item,
				inContinue ? I18n.t("continue_remove") : null,
				inContinue ? () -> {
					graph.history.dismiss(item.id);
					updateContinueWatching();
				} : null,
				this::onShow);
	}
}
