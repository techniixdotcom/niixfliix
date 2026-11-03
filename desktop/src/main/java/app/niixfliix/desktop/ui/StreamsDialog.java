package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamCache;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.addon.StreamSearch;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.Dialog;
import app.niixfliix.desktop.gtk.Flow;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Menu;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Opener;
import app.niixfliix.download.SeriesDownload;
import app.niixfliix.player.PlaySession;

public final class StreamsDialog implements StreamSearch.Listener {

	private static final int AUTOPLAY_SECONDS = 3;
	private static final int WIDTH = 580;
	@Nullable private static StreamsDialog open;

	private final DesktopGraph graph = DesktopGraph.get();
	private final Meta meta;
	private final String videoId;
	private final String fullTitle;
	private final List<StreamInfo> all = new ArrayList<>();
	private List<StreamInfo> sorted = new ArrayList<>();
	private List<StreamInfo> best = new ArrayList<>();
	@Nullable private StreamCache.Handle search;
	private long firstResultsAt;
	private final Runnable graceOver = this::maybeAutoplay;
	private boolean finished;
	private int secondsLeft;
	private boolean autoplayStopped;
	@Nullable private StreamInfo autoplayPick;
	private final Runnable tick = this::tick;
	private boolean ticking;

	private final Label status = Label.of(null, "dim-label");
	private final Progress progress = Progress.spinner(20);
	private final Label empty = Label.wrapping(null);
	private final Button qualityLine = Button.text("", "flat", "link-button");
	private final Label autoplayText = Label.wrapping(null);
	private final Box autoplayCard = Box.row(12);
	private final Box list = Box.column(4);
	private Dialog handle;

	private StreamsDialog(Meta meta, String videoId, String title, @Nullable String subtitle) {
		this.meta = meta;
		this.videoId = videoId;
		this.fullTitle = subtitle == null ? title : title + " · " + subtitle;
	}

	public static void show(@NonNull Meta meta, @NonNull String videoId, @NonNull String title,
			@Nullable String subtitle) {
		if (open != null || meta.id == null || meta.type == null) {
			return;
		}
		StreamsDialog dialog = new StreamsDialog(meta, videoId, title, subtitle);
		open = dialog;
		dialog.build(title, subtitle);
		dialog.start();
	}

	private void build(String title, @Nullable String subtitle) {
		Box body = Box.column(12);
		if (subtitle != null) {
			body.add(Label.wrapping(subtitle, "dim-label"));
		}
		qualityLine.halign(Widget.ALIGN_START);
		qualityLine.onClick(() -> {
			stopAutoplay();
			QualityDialog.show(this::onQualityChosen);
		});
		showQualityLine();
		Box statusRow = Box.row(10, progress, status);
		autoplayText.hexpand(true);
		Button choose = Dialogs.button(I18n.t("streams_choose_myself"), false);
		choose.onClick(this::stopAutoplay);
		autoplayCard.add(autoplayText, choose);
		autoplayCard.addClass("card", "padded");
		autoplayCard.visible(false);
		empty.visible(false);
		body.add(qualityLine, statusRow, autoplayCard, empty, list);
		body.size(WIDTH - 2 * 18, -1);
		Button close = Dialogs.button(I18n.t("action_close"), false);
		handle = Dialogs.custom(title, body, WIDTH, close);
		handle.followsContent(true);
		close.onClick(handle::close);
		handle.onClosed(this::onClosed);
	}

	private void onClosed() {
		cancelCountdown();
		graph.main.removeCallbacks(graceOver);
		if (search != null) {
			search.cancel();
		}
		open = null;
	}

	private void onQualityChosen() {
		if (handle.isClosed()) {
			return;
		}
		showQualityLine();
		autoplayStopped = false;
		maybeAutoplay();
	}

	private void showQualityLine() {
		qualityLine.label(I18n.t("streams_quality_desktop", Fmt.quality(graph.playerPrefs.quality(true))));
	}

	private void start() {
		search = graph.streams.open(meta.type, videoId, this);
		updateStatus();
	}

	@Override
	public void onResults(@NonNull InstalledAddon addon, @NonNull List<StreamInfo> streams) {
		if (handle.isClosed()) {
			return;
		}
		for (StreamInfo s : streams) {
			if (s.kind != StreamInfo.Kind.UNSUPPORTED) {
				all.add(s);
			}
		}
		sorted = StreamRanker.sort(all);
		best = StreamRanker.bestPerQuality(sorted, isLive());
		drawList();
		updateStatus();
		if (firstResultsAt == 0) {
			firstResultsAt = System.currentTimeMillis();
			graph.main.postDelayed(graceOver, StreamRanker.AUTOPLAY_GRACE_MS);
		}
		maybeAutoplay();
	}

	@Override
	public void onAddonFailed(@NonNull InstalledAddon addon) {
	}

	@Override
	public void onFinished() {
		if (handle.isClosed()) {
			return;
		}
		finished = true;
		progress.visible(false);
		updateStatus();
		if (best.isEmpty()) {
			String text;
			if (search != null && search.addonCount() == 0) {
				text = I18n.t("streams_no_addons");
			} else if (sorted.isEmpty() || hasShownQuality(sorted)) {
				text = I18n.t("streams_none_found");
			} else {
				text = I18n.t("streams_none_in_range");
			}
			empty.text(text);
			empty.visible(true);
		} else {
			maybeAutoplay();
		}
	}

	private void drawList() {
		list.clear();
		for (StreamInfo s : best) {
			list.add(row(s));
		}
	}

	private Widget row(StreamInfo s) {
		Box content = Box.column(4, Label.of(Fmt.quality(s.quality), "title-4"),
				Label.wrapping(fullTitle, "caption", "dim-label"));
		Flow chips = Flow.create(6);
		if (s.sizeBytes > 0) {
			chips.add(chip(Fmt.size(s.sizeBytes)));
		}
		for (String language : s.languages) {
			chips.add(chip(language));
		}
		for (String tag : s.tags) {
			chips.add(chip(tag));
		}
		if (!chips.isEmpty()) {
			content.add(chips);
		}
		Button row = Button.wrapping(content, "flat", "selectable-row");
		row.onClick(() -> onStreamClick(s));
		Controller.contextMenu(row, (x, y) -> onStreamMenu(row, s, x, y));
		return row;
	}

	private static Label chip(String text) {
		return Label.of(text, "chip-static");
	}

	private void maybeAutoplay() {
		if (autoplayStopped || ticking || handle.isClosed() || best.isEmpty()) {
			return;
		}
		Quality preferred = graph.playerPrefs.quality(seriesId(), true);
		boolean waited = firstResultsAt > 0
				&& System.currentTimeMillis() - firstResultsAt >= StreamRanker.AUTOPLAY_GRACE_MS;
		if (!finished && !waited && !StreamRanker.canAutoplayEarly(best, preferred)) {
			return;
		}
		StreamInfo pick = StreamRanker.pick(best, preferred, null, isLive());
		if (pick == null) {
			return;
		}
		autoplayPick = pick;
		secondsLeft = AUTOPLAY_SECONDS;
		autoplayCard.visible(true);
		ticking = true;
		tick();
	}

	private void tick() {
		StreamInfo pick = autoplayPick;
		if (!ticking || pick == null) {
			return;
		}
		if (secondsLeft == 0) {
			ticking = false;
			StreamInfo now = StreamRanker.pick(best, graph.playerPrefs.quality(seriesId(), true), null, isLive());
			play(now != null ? now : pick);
			return;
		}
		autoplayText.text(I18n.t("streams_autoplay", Fmt.quality(pick.quality), secondsLeft));
		secondsLeft--;
		graph.main.postDelayed(tick, 1000);
	}

	private void stopAutoplay() {
		autoplayStopped = true;
		cancelCountdown();
	}

	private void cancelCountdown() {
		ticking = false;
		autoplayPick = null;
		graph.main.removeCallbacks(tick);
		autoplayCard.visible(false);
	}

	private boolean isLive() {
		return Catalog.isLiveType(meta.type);
	}

	private static boolean hasShownQuality(List<StreamInfo> streams) {
		for (StreamInfo s : streams) {
			if (StreamRanker.SHOWN.contains(s.quality)) {
				return true;
			}
		}
		return false;
	}

	private void updateStatus() {
		status.text(finished ? I18n.plural("streams_found", best.size(), best.size()) : I18n.t("queue_finding"));
	}

	private void onStreamClick(StreamInfo s) {
		stopAutoplay();
		switch (s.kind) {
			case DIRECT:
			case TORRENT:
				String seriesId = seriesId();
				if (seriesId != null) {
					graph.playerPrefs.setSeriesQuality(seriesId, s.quality);
				}
				play(s);
				break;
			case EXTERNAL:
				Stream stream = s.stream;
				String link = stream.externalUrl != null ? stream.externalUrl
						: stream.ytId != null ? youtube(stream.ytId) : null;
				if (link == null || !Opener.openWebLink(link)) {
					Toast.show(I18n.t("error_no_app_for_link"));
				}
				break;
			default:
				Toast.show(I18n.t("streams_unsupported"));
				break;
		}
	}

	@NonNull
	public static String youtube(@NonNull String videoId) {
		return "https://www.youtube.com/watch?v=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8);
	}

	@Nullable
	private String seriesId() {
		return Meta.TYPE_SERIES.equals(meta.type) ? meta.id : null;
	}

	private void play(StreamInfo s) {
		if (meta.id == null || meta.type == null) {
			return;
		}
		PlaySession session = new PlaySession(meta, meta.type, meta.id, meta.nameOrEmpty(), meta.poster, videoId,
				meta.video(videoId), sorted, s);
		handle.close();
		Launch.play(session);
	}

	private void onStreamMenu(Widget owner, StreamInfo s, double x, double y) {
		if (s.kind != StreamInfo.Kind.DIRECT || s.stream.url == null) {
			return;
		}
		stopAutoplay();
		String url = s.stream.url;
		Menu menu = new Menu().item(I18n.t("streams_copy_link"), () -> {
			owner.copyText(url);
			Toast.show(I18n.t("streams_copied"));
		});
		if (!s.isUncachedDebrid()) {
			menu.item(I18n.t("streams_download"), () -> startDownload(s));
		}
		menu.showAt(owner, x, y);
	}

	private void startDownload(StreamInfo s) {
		if (s.stream.url == null) {
			return;
		}
		boolean ok = graph.downloads().enqueue(s.stream.url, s.stream.requestHeaders(), fullTitle,
				SeriesDownload.fileNameFor(meta, videoId, s, fullTitle));
		Toast.show(I18n.t(ok ? "download_started" : "download_could_not_start"));
	}
}
