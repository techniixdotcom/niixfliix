package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Opener;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.PosterCard;
import app.niixfliix.desktop.ui.PosterRows;
import app.niixfliix.desktop.ui.Toast;
import app.niixfliix.live.LiveCheck;
import app.niixfliix.live.LiveTv;
import app.niixfliix.live.SportSites;
import app.niixfliix.ui.common.PosterItem;

public final class LivePage implements Page, PosterCard.Listener, AddonRepository.Listener {

	private static final long SHOW_EVERY_MS = 600;
	private static final int MAX_PER_ROW = 60;

	private final DesktopGraph graph = DesktopGraph.get();
	private final Box root = Box.column(0);
	private final PosterRows rows = new PosterRows(this, this::loadRow);
	private final Map<PosterRows.Row, LiveTv.Row> sources = new HashMap<>();
	private final Map<PosterRows.Row, LiveCheck.Batch> checks = new HashMap<>();
	private final Button refresh = Button.icon(Icons.name("refresh"), I18n.t("action_retry"));

	public LivePage() {
		refresh.onClick(this::refreshAll);
		Box top = Box.row(0, Label.of(I18n.t("nav_live"), "title-1"), Widget.spacer(), refresh);
		top.addClass("page-top");

		Widget center = rows.view();
		center.vexpand(true);
		root.add(top, center);
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
		rows.onShown();
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
		Map<PosterRows.Row, LiveTv.Row> old = new HashMap<>(sources);
		sources.clear();
		List<PosterRows.Row> list = new ArrayList<>();
		list.add(sportSitesRow());
		for (LiveTv.Row live : LiveTv.rows(graph.addons)) {
			String title = live.isFreeSports() ? I18n.t("live_free_sports") : live.name();
			String subtitle = live.isFreeSports() ? I18n.t("live_free_sports_summary")
					: live.source.addon.displayName();
			PosterRows.Row existing = keepLoaded ? rows.row(live.key) : null;
			boolean keep = existing != null && isDone(existing) && existing.title.equals(title)
					&& subtitle.equals(existing.subtitle);
			PosterRows.Row row = keep ? existing : new PosterRows.Row(live.key, title, subtitle);
			row.emptyText = I18n.t("live_row_nothing");
			sources.put(row, live);
			list.add(row);
		}
		for (PosterRows.Row gone : old.keySet()) {
			if (!sources.containsKey(gone)) {
				cancelCheck(gone);
			}
		}
		rows.setRows(null, list);
	}

	@NonNull
	private PosterRows.Row sportSitesRow() {
		String title = I18n.t("live_sport_tv");
		String subtitle = I18n.t("live_sport_tv_summary");
		PosterRows.Row existing = rows.row(SportSites.ROW_KEY);
		if (existing != null && existing.title.equals(title) && subtitle.equals(existing.subtitle)) {
			return existing;
		}
		PosterRows.Row row = new PosterRows.Row(SportSites.ROW_KEY, title, subtitle);
		row.items = SportSites.posters();
		row.state = PosterRows.State.READY;
		return row;
	}

	private static boolean isDone(PosterRows.Row row) {
		return row.state == PosterRows.State.READY || row.state == PosterRows.State.EMPTY;
	}

	private void cancelCheck(PosterRows.Row row) {
		LiveCheck.Batch batch = checks.remove(row);
		if (batch != null) {
			batch.cancel();
		}
	}

	private void loadRow(PosterRows.Row row) {
		LiveTv.Row live = sources.get(row);
		if (live == null) {
			return;
		}
		cancelCheck(row);
		graph.io.execute(() -> {
			List<Meta> metas = live.source.fetch(graph.addonClient, live.extras());
			graph.main.post(() -> {
				if (sources.get(row) != live) {
					return;
				}
				if (metas == null) {
					row.state = PosterRows.State.FAILED;
					rows.update(row);
					return;
				}
				check(row, metas.size() > MAX_PER_ROW ? new ArrayList<>(metas.subList(0, MAX_PER_ROW)) : metas);
			});
		});
	}

	private void check(PosterRows.Row row, List<Meta> metas) {
		Set<Meta> working = new HashSet<>();
		boolean[] showPosted = {false};
		Runnable show = () -> {
			showPosted[0] = false;
			List<PosterItem> items = new ArrayList<>();
			for (Meta m : metas) {
				if (working.contains(m)) {
					items.add(PosterItem.of(m));
				}
			}
			if (items.size() != row.items.size()) {
				row.items = items;
				row.state = PosterRows.State.READY;
				rows.update(row);
			}
		};
		LiveCheck.Batch batch = graph.liveCheck.check(metas, new LiveCheck.Listener() {
			@Override
			public void onChecked(@NonNull Meta meta, boolean works) {
				if (!works) {
					return;
				}
				working.add(meta);
				if (!showPosted[0]) {
					showPosted[0] = true;
					graph.main.postDelayed(show, row.items.isEmpty() ? 0 : SHOW_EVERY_MS);
				}
			}

			@Override
			public void onDone() {
				checks.remove(row);
				graph.main.removeCallbacks(show);
				show.run();
				if (working.isEmpty()) {
					row.items = new ArrayList<>();
					row.state = PosterRows.State.EMPTY;
					rows.update(row);
				}
			}
		});
		checks.put(row, batch);
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
