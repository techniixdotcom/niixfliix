package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Flow;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Opener;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.PosterCard;
import app.niixfliix.desktop.ui.PosterGrid;
import app.niixfliix.desktop.ui.Toast;
import app.niixfliix.live.LiveCheck;
import app.niixfliix.live.LiveTv;
import app.niixfliix.live.SportSites;
import app.niixfliix.ui.common.PosterItem;

/**
 * Live, laid out like Discover: a row of chips picks the source (Sport TV, the free sports channels, each
 * live catalog), a second row picks the genre when the catalog has more than one, and the channels show
 * in a poster grid below. Only channels that play right now are listed; they appear as they're checked.
 */
public final class LivePage implements Page, PosterCard.Listener, AddonRepository.Listener {

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

	private final DesktopGraph graph = DesktopGraph.get();
	private final Box root = Box.column(0);
	private final Flow sourceChips = Flow.create(8);
	private final Flow genreChips = Flow.create(8);
	private final List<Button> sourceButtons = new ArrayList<>();
	private final List<Button> genreButtons = new ArrayList<>();
	private final Label summary = Label.wrapping(null, "dim-label");
	private final PosterGrid grid = new PosterGrid(this);
	private final Label empty = Label.wrapping(null, "empty-text");
	private final Progress loading = Progress.spinner(40);
	private final Button refresh = Button.icon(Icons.name("refresh"), I18n.t("action_retry"));
	private final List<Source> sources = new ArrayList<>();
	/** Checked channels per row key, so going back to a chip doesn't check everything again. */
	private final Map<String, List<PosterItem>> checked = new HashMap<>();

	@Nullable private Source source;
	@Nullable private LiveTv.Row row;
	@Nullable private LiveCheck.Batch batch;
	private int generation;
	/** Set while the chips are being made or picked in code, so their toggles don't load anything. */
	private boolean picking;

	public LivePage() {
		refresh.onClick(this::refreshAll);
		refresh.valign(Widget.ALIGN_CENTER);
		Box heading = Box.row(0, Label.of(I18n.t("nav_live"), "title-1"), Widget.spacer(), refresh);
		sourceChips.halign(Widget.ALIGN_FILL);
		sourceChips.addClass("genre-chips");
		genreChips.halign(Widget.ALIGN_FILL);
		genreChips.addClass("genre-chips");
		summary.halign(Widget.ALIGN_START);
		summary.visible(false);
		Box top = Box.column(14, heading, sourceChips, genreChips, summary);
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
		refresh.sensitive(true);
		rebuild();
	}

	private void refreshAll() {
		refresh.sensitive(false);
		graph.addons.refreshAll(() -> {
			graph.addonClient.clearCache();
			checked.clear();
			row = null;
			refresh.sensitive(true);
			rebuild();
		});
	}

	private void rebuild() {
		sources.clear();
		sources.add(new Source(SportSites.ROW_KEY, I18n.t("live_sport_tv"), I18n.t("live_sport_tv_summary")));
		Map<String, Source> byCatalog = new LinkedHashMap<>();
		for (LiveTv.Row live : LiveTv.rows(graph.addons)) {
			String key = live.source.addon.transportUrl + "|" + live.source.catalog.type + "|"
					+ live.source.catalog.id;
			Source s = byCatalog.get(key);
			if (s == null) {
				String name = live.source.catalog.name != null ? live.source.catalog.name : live.source.catalog.id;
				s = live.isFreeSports()
						? new Source(key, I18n.t("live_free_sports"), I18n.t("live_free_sports_summary"))
						: new Source(key, name, live.source.addon.displayName());
				byCatalog.put(key, s);
			}
			s.rows.add(live);
		}
		sources.addAll(byCatalog.values());

		String keepKey = source != null ? source.key : null;
		String keepRow = row != null ? row.key : null;
		Source keep = null;
		for (Source s : sources) {
			if (s.key.equals(keepKey) || keep == null) {
				keep = s;
			}
		}
		picking = true;
		sourceChips.clear();
		sourceButtons.clear();
		for (Source s : sources) {
			Button chip = Button.toggle(s.title, "chip");
			if (!sourceButtons.isEmpty()) {
				chip.group(sourceButtons.get(0));
			}
			chip.active(s == keep);
			chip.onToggled(() -> {
				if (!picking && chip.isActive() && s != source) {
					selectSource(s, null);
				}
			});
			sourceButtons.add(chip);
			sourceChips.add(chip);
		}
		picking = false;
		source = null;
		row = null;
		if (keep != null) {
			selectSource(keep, keepRow);
		}
		if (stillLoadingManifests()) {
			loading.visible(true);
		}
	}

	private void selectSource(@NonNull Source picked, @Nullable String keepRow) {
		source = picked;
		picking = true;
		for (int i = 0; i < sourceButtons.size() && i < sources.size(); i++) {
			if (sources.get(i) == picked && !sourceButtons.get(i).isActive()) {
				sourceButtons.get(i).active(true);
			}
		}
		summary.textOrHide(picked.summary);
		genreChips.clear();
		genreButtons.clear();
		LiveTv.Row first = null;
		for (LiveTv.Row r : picked.rows) {
			if (first == null || r.key.equals(keepRow)) {
				first = r;
			}
		}
		for (LiveTv.Row r : picked.rows) {
			Button chip = Button.toggle(r.genre != null ? r.genre : r.name(), "chip");
			if (!genreButtons.isEmpty()) {
				chip.group(genreButtons.get(0));
			}
			chip.active(r == first);
			chip.onToggled(() -> {
				if (!picking && chip.isActive() && r != row) {
					selectRow(r);
				}
			});
			genreButtons.add(chip);
			genreChips.add(chip);
		}
		picking = false;
		genreChips.visible(picked.rows.size() > 1);
		if (picked.isSites() || first == null) {
			row = null;
			show(picked);
		} else {
			selectRow(first);
		}
	}

	private void selectRow(@NonNull LiveTv.Row picked) {
		row = picked;
		Source current = source;
		if (current != null) {
			picking = true;
			for (int i = 0; i < genreButtons.size() && i < current.rows.size(); i++) {
				if (current.rows.get(i) == picked && !genreButtons.get(i).isActive()) {
					genreButtons.get(i).active(true);
				}
			}
			picking = false;
		}
		load(picked);
	}

	private void show(@NonNull Source picked) {
		generation++;
		cancelCheck();
		empty.visible(false);
		loading.visible(false);
		grid.show(picked.isSites() ? SportSites.posters() : new ArrayList<>());
		if (!picked.isSites()) {
			showEmpty(I18n.t("live_row_nothing"));
		}
	}

	private void load(@NonNull LiveTv.Row live) {
		int requestGeneration = ++generation;
		cancelCheck();
		empty.visible(false);
		List<PosterItem> done = checked.get(live.key);
		if (done != null) {
			loading.visible(false);
			grid.show(done);
			if (done.isEmpty()) {
				showEmpty(I18n.t("live_row_nothing"));
			}
			return;
		}
		grid.show(new ArrayList<>());
		loading.visible(true);
		graph.io.execute(() -> {
			List<Meta> metas = live.source.fetch(graph.addonClient, live.extras());
			graph.main.post(() -> {
				if (requestGeneration != generation) {
					return;
				}
				if (metas == null) {
					loading.visible(false);
					showEmpty(I18n.t("discover_failed"));
					return;
				}
				check(live, requestGeneration,
						metas.size() > MAX_CHANNELS ? new ArrayList<>(metas.subList(0, MAX_CHANNELS)) : metas);
			});
		});
	}

	private void check(@NonNull LiveTv.Row live, int requestGeneration, @NonNull List<Meta> metas) {
		Set<Meta> working = new HashSet<>();
		List<PosterItem> shown = new ArrayList<>();
		boolean[] showPosted = {false};
		Runnable show = () -> {
			showPosted[0] = false;
			if (requestGeneration != generation) {
				return;
			}
			List<PosterItem> items = new ArrayList<>();
			for (Meta m : metas) {
				if (working.contains(m)) {
					items.add(PosterItem.of(m));
				}
			}
			if (items.size() != shown.size()) {
				shown.clear();
				shown.addAll(items);
				grid.show(items);
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
					graph.main.postDelayed(show, shown.isEmpty() ? 0 : SHOW_EVERY_MS);
				}
			}

			@Override
			public void onDone() {
				graph.main.removeCallbacks(show);
				if (requestGeneration != generation) {
					return;
				}
				batch = null;
				show.run();
				loading.visible(false);
				checked.put(live.key, new ArrayList<>(shown));
				if (working.isEmpty()) {
					showEmpty(I18n.t("live_row_nothing"));
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
		empty.text(text);
		empty.visible(true);
	}

	@Override
	public void onPosterClick(@NonNull PosterItem item) {
		SportSites.Site site = SportSites.find(item.id);
		if (site != null && SportSites.isSite(item.id)) {
			if (!Opener.openWebLink(site.url)) {
				Toast.show(I18n.t("error_no_app_for_link"));
			}
			return;
		}
		MainWindow.get().openDetails(item);
	}

	@Override
	public void onPosterMenu(@NonNull PosterItem item, @NonNull Widget card, double x, double y) {
	}
}
