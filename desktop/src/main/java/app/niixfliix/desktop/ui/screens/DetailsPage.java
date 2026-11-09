package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Video;
import app.niixfliix.data.SeriesWatched;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.download.SeriesDownload;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.DropDown;
import app.niixfliix.desktop.gtk.Flow;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Image;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.Picture;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.gtk.Scroller;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Opener;
import app.niixfliix.desktop.ui.Dialogs;
import app.niixfliix.desktop.ui.Fmt;
import app.niixfliix.desktop.ui.ImageLoader;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.PosterCard;
import app.niixfliix.desktop.ui.PosterMenu;
import app.niixfliix.desktop.ui.PosterRow;
import app.niixfliix.desktop.ui.QuickDownload;
import app.niixfliix.desktop.ui.StreamsDialog;
import app.niixfliix.desktop.ui.Toast;
import app.niixfliix.ui.common.MoreLikeThis;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.ui.common.RelatedTitles;
import app.niixfliix.ui.common.TitleAbout;

public final class DetailsPage implements Page {

	private static final int TRAILER_WIDTH = 480;
	private static final int TRAILER_HEIGHT = 270;
	private static final int BACKDROP_HEIGHT = 440;
	private static final int BACKDROP_MIN_WIDTH = 1280;
	private static final int HERO_OVERLAP = 140;
	private static final int LOGO_WIDTH = 420;
	private static final int LOGO_HEIGHT = 140;
	private static final int THUMB_WIDTH = 224;
	private static final int THUMB_HEIGHT = 126;
	private static final int POSTER_WIDTH = 220;
	private static final int POSTER_HEIGHT = 330;
	private static final int SUMMARY_CHARS = 110;
	private static final int TEXT_CHARS = 100;

	private final DesktopGraph graph = DesktopGraph.get();
	private final String type;
	private final String id;
	@Nullable private final String initialName;
	@Nullable private final String initialPoster;
	@Nullable private Meta meta;
	private int season = -1;
	private boolean loaded;
	private boolean pendingPlay;
	@Nullable private String imdbLink;

	private final Widget root;
	private final Picture backdrop = Picture.create(Picture.FIT_COVER);
	private final Picture logo = Picture.create(Picture.FIT_CONTAIN);
	private final Picture poster = Picture.create(Picture.FIT_COVER);
	private final Box posterFrame = Box.column(0);
	private final Box logoRoom = Box.column(0);
	private final Layers logoBox = Layers.overlay(logoRoom, logo);
	private final Label title = Label.wrapping(null, "title-1");
	private final Label info = Label.of(null, "dim-label");
	private final Flow genres = Flow.create(6);
	private final Button play = Button.iconText(Icons.name("play"), I18n.t("details_play"), "suggested-action",
			"pill");
	private final Button save = Button.icon(Icons.name("bookmark_border"), I18n.t("details_save"));
	private final Button watched = Button.icon(Icons.name("check"), I18n.t("details_mark_watched"));
	private final Button downloadEpisodes = Button.iconText(Icons.name("download"),
			I18n.t("details_download_episodes"), "flat");
	private final Label description = Label.wrapping(null);
	private final Flow castChips = Flow.create(6);
	private final Box castSection = Box.column(8);
	private final Picture trailerImage = Picture.create(Picture.FIT_COVER);
	private final Box trailerSection = Box.column(10);
	private final Box aboutFacts = Box.column(12);
	private final Button imdb = Button.text(I18n.t("details_imdb"), "flat", "link-button");
	private final Box aboutSection = Box.column(10);
	private final Box trailerAndAbout = Box.row(48);
	private final Box relatedSection = Box.column(6);
	private final PosterRow related = new PosterRow(new PosterCard.Listener() {
		@Override
		public void onPosterClick(@NonNull PosterItem item) {
			MainWindow.get().openDetails(item);
		}

		@Override
		public void onPosterMenu(@NonNull PosterItem item, @NonNull Widget card, double x, double y) {
			PosterMenu.show(card, x, y, item, null, null, () -> DetailsPage.this.related.refresh());
		}
	});
	private final Set<String> relatedIds = new HashSet<>();
	private final Box similarSection = Box.column(6);
	/** Placeholder posters, shown while the titles are looked up, so the row is seen to be coming. */
	private final Box similarComing = Box.row(12);
	private static final int SIMILAR_PLACEHOLDERS = 8;
	private final PosterRow similar = new PosterRow(new PosterCard.Listener() {
		@Override
		public void onPosterClick(@NonNull PosterItem item) {
			MainWindow.get().openDetails(item);
		}

		@Override
		public void onPosterMenu(@NonNull PosterItem item, @NonNull Widget card, double x, double y) {
			PosterMenu.show(card, x, y, item, null, null, () -> DetailsPage.this.similar.refresh());
		}
	});
	private final Label notice = Label.wrapping(null, "card", "padded");
	private final Box seriesSection = Box.column(12);
	private final DropDown seasonInput = DropDown.of(new ArrayList<>());
	private final Box episodes = Box.column(4);
	private final Box loading = Box.column(10);

	public DetailsPage(@NonNull String type, @NonNull String id, @Nullable String name, @Nullable String poster,
			boolean play) {
		this.pendingPlay = play;
		this.type = type;
		this.id = id;
		this.initialName = name;
		this.initialPoster = poster;
		root = build();
		bind(stub(), false);
		load();
		graph.prepareToPlay(type, Meta.TYPE_SERIES.equals(type) ? null : id, seriesId());
	}

	private Widget build() {
		Box fade = Box.column(0);
		fade.addClass("details-fade");
		Layers hero = Layers.overlay(backdrop.fixed(-1, BACKDROP_HEIGHT), fade);
		hero.valign(Widget.ALIGN_START);

		logoBox.halign(Widget.ALIGN_START);
		logoBox.visible(false);
		info.visible(false);

		play.onClick(this::onPlay);
		save.onClick(this::toggleSaved);
		watched.onClick(this::toggleWatched);
		Box actions = Box.row(10, play, save, watched);
		for (Widget w : new Widget[] {play, save, watched}) {
			w.valign(Widget.ALIGN_CENTER);
		}

		description.maxWidthChars(TEXT_CHARS);
		castChips.halign(Widget.ALIGN_FILL);
		castChips.hexpand(true);
		castSection.add(Label.of(I18n.t("details_cast_title"), "title-4"), castChips);
		castSection.visible(false);
		Box badge = Box.column(0, Image.icon(Icons.name("play"), 28));
		badge.addClass("play-badge");
		badge.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER);
		Layers trailerArt = Layers.overlay(trailerImage.fixed(TRAILER_WIDTH, TRAILER_HEIGHT), badge);
		trailerArt.addClass("trailer-art");
		trailerArt.clipChildren();
		Button trailerCard = Button.wrapping(trailerArt, "trailer-card");
		trailerCard.tooltip(I18n.t("details_trailer"));
		trailerCard.halign(Widget.ALIGN_START);
		trailerCard.onClick(this::openTrailer);
		trailerSection.add(Label.of(I18n.t("details_trailer"), "title-4"), trailerCard);
		trailerSection.visible(false);
		imdb.halign(Widget.ALIGN_START);
		imdb.onClick(() -> {
			if (imdbLink != null && !Opener.openWebLink(imdbLink)) {
				Toast.show(I18n.t("error_no_app_for_link"));
			}
		});
		aboutSection.add(Label.of(I18n.t("details_about"), "title-4"), aboutFacts, imdb);
		aboutSection.visible(false);
		Label similarTitle = Label.of(I18n.t("details_more_like_this"), "title-4");
		similarTitle.halign(Widget.ALIGN_START);
		for (int i = 0; i < SIMILAR_PLACEHOLDERS; i++) {
			similarComing.add(Box.column(0).size(PosterCard.WIDTH, PosterCard.HEIGHT).addClass("skeleton"));
		}
		similarComing.margins(6, 0, PosterRow.HEIGHT - PosterCard.HEIGHT - 6, 18);
		similar.widget().visible(false);
		similarSection.add(similarTitle, similarComing, similar.widget());
		similarSection.visible(false);
		Label relatedTitle = Label.of(I18n.t("details_related"), "title-4");
		relatedTitle.halign(Widget.ALIGN_START);
		relatedSection.add(relatedTitle, related.widget());
		relatedSection.visible(false);
		notice.maxWidthChars(TEXT_CHARS);
		notice.visible(false);

		seasonInput.size(220, -1);
		seasonInput.onPicked(position -> {
			List<Integer> seasons = seasonNumbers();
			if (position >= 0 && position < seasons.size()) {
				season = seasons.get(position);
				showEpisodes();
				showWatched();
			}
		});
		downloadEpisodes.valign(Widget.ALIGN_CENTER);
		downloadEpisodes.onClick(this::downloadEpisodes);
		Box seasonRow = Box.row(14, Label.of(I18n.t("details_season"), "title-4"), seasonInput, downloadEpisodes);
		seriesSection.add(seasonRow, episodes);
		seriesSection.margins(12, 0, 0, 0);
		seriesSection.visible(false);
		loading.halign(Widget.ALIGN_START);
		for (int width : new int[] {520, 500, 360}) {
			loading.add(Box.column(0).size(width, -1).addClass("skeleton-line"));
		}
		loading.accessibleLabel(I18n.t("loading"));

		posterFrame.add(poster.fixed(POSTER_WIDTH, POSTER_HEIGHT));
		posterFrame.addClass("details-poster", "skeleton");
		posterFrame.clipChildren();
		posterFrame.valign(Widget.ALIGN_START);
		Controller posterClick = Controller.click(Controller.BUTTON_PRIMARY);
		posterClick.onReleased((count, x, y) -> {
			if (count == 1) {
				toggleSeriesWatched();
			}
		});
		posterFrame.addController(posterClick);
		Controller.contextMenu(posterFrame, (x, y) -> toggleSeriesWatched());
		Box facts = Box.column(16, logoBox, title, info, genres, actions, notice, loading, description, castSection);
		facts.hexpand(true).valign(Widget.ALIGN_START);
		facts.margins(POSTER_HEIGHT / 2, 0, 0, 0);
		Box top = Box.row(32, posterFrame, facts);
		trailerSection.valign(Widget.ALIGN_START);
		aboutSection.valign(Widget.ALIGN_START).hexpand(true);
		trailerAndAbout.add(trailerSection, aboutSection);
		trailerAndAbout.visible(false);
		Box details = Box.column(28, top, seriesSection, trailerAndAbout, relatedSection, similarSection);
		details.margins(BACKDROP_HEIGHT - HERO_OVERLAP - POSTER_HEIGHT / 2, 40, 40, 40);
		details.valign(Widget.ALIGN_START);
		Layers content = Layers.overlay(Box.column(0, hero)).overMeasured(details);
		Scroller scroll = Scroller.vertical(content);
		similar.wheelScrollsRow(scroll);
		related.wheelScrollsRow(scroll);

		Button back = Button.icon(Icons.name("back"), I18n.t("action_back"));
		back.addClass("osd", "circular", "details-back");
		back.halign(Widget.ALIGN_START).valign(Widget.ALIGN_START).margin(14);
		back.onClick(() -> MainWindow.get().back());
		return Layers.overlay(scroll, back);
	}

	@NonNull
	@Override
	public Widget view() {
		return root;
	}

	@Override
	public void onShow() {
		refreshState();
	}

	private void load() {
		// The Wikidata part of "More like this" is slow, so it starts now, next to the title's details,
		// and the row shows as coming straight away (not for live channels: they have no row)
		MoreLikeThis.lookUp(graph.addonClient, type, id, graph.translations.language());
		if (!Catalog.isLiveType(type)) {
			similarSection.visible(true);
		}
		loadRelated();
		graph.io.execute(() -> {
			Meta found = MetaFinder.find(graph.addons, graph.addonClient, type, id);
			List<String> genresForSimilar = found != null ? new ArrayList<>(found.allGenres()) : null;
			long releasedForSimilar = found != null ? ReleaseOrder.of(found) : 0;
			graph.io.execute(() -> loadSimilar(genresForSimilar, releasedForSimilar));
			Meta result = found != null ? graph.translations.translate(found) : null;
			graph.main.post(() -> {
				loading.visible(false);
				if (result != null) {
					bind(result, true);
				} else {
					bind(stub(), true);
					notice.text(I18n.t("details_no_meta"));
					notice.visible(true);
				}
				if (pendingPlay) {
					pendingPlay = false;
					if (root.isMapped()) {
						onPlay();
					}
				}
			});
		});
	}

	@NonNull
	private Meta stub() {
		Meta m = new Meta();
		m.id = id;
		m.type = type;
		m.name = initialName;
		m.poster = initialPoster;
		return m;
	}

	private void bind(@NonNull Meta m, boolean complete) {
		if (complete) {
			meta = m;
			loaded = true;
		}
		int width = Math.max(BACKDROP_MIN_WIDTH, MainWindow.get().root().width());
		ImageLoader.into(backdrop, m.background != null ? m.background : m.poster, width, BACKDROP_HEIGHT,
				ImageLoader.Kind.BACKDROP);
		title.text(m.name);
		title.visible(true);
		logoBox.visible(false);
		ImageLoader.into(logo, m.logo, LOGO_WIDTH, LOGO_HEIGHT, ImageLoader.Kind.OTHER, this::showLogo);
		poster.fit(m.hasWidePoster() ? Picture.FIT_CONTAIN : Picture.FIT_COVER);
		ImageLoader.into(poster, m.poster, POSTER_WIDTH, POSTER_HEIGHT, ImageLoader.Kind.POSTER);
		info.textOrHide(infoLine(m));
		genres.clear();
		if (m.genres != null) {
			for (String g : m.genres) {
				genres.add(Label.of(g, "chip-static"));
			}
		}
		genres.visible(!genres.isEmpty());
		description.textOrHide(m.description);
		bindCast(m);
		bindTrailer(m);
		bindAbout(m);
		play.label(I18n.t("details_play"));
		play.sensitive(complete);
		save.sensitive(complete);
		watched.sensitive(complete);
		watched.visible(!Catalog.isLiveType(m.type));
		refreshState();
	}

	private void bindCast(@NonNull Meta m) {
		castChips.clear();
		List<String> names = TitleAbout.cast(m);
		for (String name : names) {
			Button chip = Button.text(name, "chip");
			chip.tooltip(I18n.t("details_search_person", name));
			chip.onClick(() -> MainWindow.get().search(name));
			castChips.add(chip);
		}
		castSection.visible(!names.isEmpty());
	}

	private void bindTrailer(@NonNull Meta m) {
		String image = TitleAbout.trailerImage(m.trailerYoutubeId());
		trailerSection.visible(image != null);
		trailerAndAbout.visible(trailerSection.isVisible() || aboutSection.isVisible());
		ImageLoader.into(trailerImage, image, TRAILER_WIDTH, TRAILER_HEIGHT, ImageLoader.Kind.OTHER);
	}

	private void bindAbout(@NonNull Meta m) {
		aboutFacts.clear();
		List<TitleAbout.Entry> entries = TitleAbout.facts(m, I18n.locale(), I18n.t("list_separator"));
		for (TitleAbout.Entry entry : entries) {
			Label label = Label.of(I18n.t(factKey(entry.fact)), "caption", "dim-label");
			label.halign(Widget.ALIGN_START);
			Label value = Label.wrapping(entry.value);
			value.start();
			value.maxWidthChars(TEXT_CHARS);
			value.halign(Widget.ALIGN_START);
			aboutFacts.add(Box.column(2, label, value));
		}
		String link = TitleAbout.imdbUrl(m);
		imdbLink = link;
		imdb.visible(link != null);
		aboutSection.visible(!entries.isEmpty() || link != null);
		trailerAndAbout.visible(trailerSection.isVisible() || aboutSection.isVisible());
	}

	@NonNull
	private static String factKey(@NonNull TitleAbout.Fact fact) {
		switch (fact) {
			case DIRECTOR:
				return "details_director";
			case WRITERS:
				return "details_writers";
			case RELEASED:
				return "details_released";
			case COUNTRY:
				return "details_country";
			case AWARDS:
			default:
				return "details_awards";
		}
	}

	private void openTrailer() {
		String yt = meta != null ? meta.trailerYoutubeId() : null;
		if (yt != null && !Opener.openWebLink(StreamsDialog.youtube(yt))) {
			Toast.show(I18n.t("error_no_app_for_link"));
		}
	}

	private void loadSimilar(@Nullable List<String> genres, long released) {
		MoreLikeThis.find(graph.addons, graph.addonClient, type, id, genres, released,
				graph.translations.language(), (items, last) -> graph.main.post(() -> showSimilar(items, last)));
	}

	/** Shows the titles; {@code last} is the final list, which may be empty (then the row goes away). */
	private void showSimilar(@NonNull List<PosterItem> items, boolean last) {
		if (items.isEmpty() && !last) {
			return;
		}
		if (!items.isEmpty() || similarFound.isEmpty()) {
			similarFound = new ArrayList<>(items);
		}
		similarDone = similarDone || last;
		showSimilarRow();
	}

	/** "More like this" without the titles the related row already shows. */
	private void showSimilarRow() {
		List<PosterItem> shown = new ArrayList<>();
		for (PosterItem item : similarFound) {
			if (!relatedIds.contains(item.id)) {
				shown.add(item);
			}
		}
		if (shown.isEmpty()) {
			if (similarDone) {
				similarSection.visible(false);
			}
			return;
		}
		similar.show(shown);
		similarComing.visible(false);
		similar.widget().visible(true);
		similarSection.visible(true);
	}

	/** "More like this" as found, before the titles already in the related row are left out. */
	private List<PosterItem> similarFound = new ArrayList<>();
	private boolean similarDone;

	/** The rest of a film's series and its other versions, in their own row. */
	private void loadRelated() {
		if (!RelatedTitles.applies(type, id)) {
			return;
		}
		String language = graph.translations.language();
		graph.io.execute(() -> {
			List<PosterItem> found = RelatedTitles.find(graph.addonClient, type, id, language);
			graph.main.post(() -> showRelated(found));
		});
	}

	private void showRelated(@NonNull List<PosterItem> items) {
		if (items.isEmpty()) {
			return;
		}
		related.show(items);
		relatedSection.visible(true);
		relatedIds.clear();
		for (PosterItem item : items) {
			relatedIds.add(item.id);
		}
		if (!similarFound.isEmpty()) {
			showSimilarRow();
		}
	}

	private void showLogo() {
		int[] size = logo.pictureSize();
		if (size[0] <= 0 || size[1] <= 0) {
			return;
		}
		double scale = Math.min((double) LOGO_WIDTH / size[0], (double) LOGO_HEIGHT / size[1]);
		logoRoom.size((int) Math.round(size[0] * scale), (int) Math.round(size[1] * scale));
		logoBox.visible(true);
		title.visible(false);
	}

	@NonNull
	private static String infoLine(@NonNull Meta m) {
		List<String> parts = new ArrayList<>();
		if (m.releaseInfo != null && !m.releaseInfo.isEmpty()) {
			parts.add(m.releaseInfo);
		}
		if (m.runtime != null && !m.runtime.isEmpty()) {
			parts.add(m.runtime);
		}
		if (m.imdbRating != null && !m.imdbRating.isEmpty()) {
			parts.add(I18n.t("details_rating", m.imdbRating));
		}
		return String.join(" · ", parts);
	}

	private void refreshState() {
		boolean saved = graph.library.contains(id);
		String saveText = I18n.t(saved ? "details_unsave" : "details_save");
		save.iconName(Icons.name(saved ? "bookmark" : "bookmark_border")).tooltip(saveText);
		save.accessibleLabel(saveText);
		Meta m = meta;
		showWatched();
		if (m == null || !loaded) {
			return;
		}
		List<Integer> seasons = seasonNumbers();
		if (m.isSeries() && !seasons.isEmpty()) {
			seriesSection.visible(true);
			Video next = graph.history.nextUp(m);
			graph.prepareToPlay(type, next != null ? next.id : null, seriesId());
			if (season < 0 || !seasons.contains(season)) {
				season = next != null ? next.seasonOrZero() : seasons.get(0);
			}
			List<String> labels = new ArrayList<>();
			for (int s : seasons) {
				labels.add(seasonLabel(s));
			}
			seasonInput.choices(labels, seasons.indexOf(season));
			showEpisodes();
			showWatched();
			if (next != null) {
				long resume = next.id != null ? graph.history.resumePosition(next.id) : 0;
				String episode = I18n.t("episode_number", next.seasonOrZero(), next.episodeOrZero());
				play.label(I18n.t(resume > 0 ? "details_continue_episode" : "details_play_episode", episode));
				play.sensitive(true);
			} else {
				play.label(I18n.t("details_play"));
				play.sensitive(false);
			}
		} else {
			seriesSection.visible(false);
			String videoId = m.playableVideoId();
			graph.prepareToPlay(type, videoId, seriesId());
			long resume = videoId != null ? graph.history.resumePosition(videoId) : 0;
			play.label(resume > 0 ? I18n.t("details_continue_at", Fmt.time(resume)) : I18n.t("details_play"));
			play.sensitive(videoId != null);
		}
	}

	private void showEpisodes() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		Map<Integer, List<Video>> seasons = m.seasons();
		List<Video> list = seasons.get(season);
		Video next = graph.history.nextUp(m);
		String nextId = next != null ? next.id : null;
		episodes.clear();
		if (list == null) {
			return;
		}
		for (Video video : list) {
			episodes.add(episodeRow(video, video.id != null && video.id.equals(nextId)));
		}
	}

	private Widget episodeRow(Video video, boolean isNext) {
		Picture thumb = Picture.create(Picture.FIT_COVER);
		Box thumbBox = Box.column(0, thumb.fixed(THUMB_WIDTH, THUMB_HEIGHT));
		thumbBox.addClass("episode-thumb", "skeleton");
		thumbBox.clipChildren();
		thumbBox.valign(Widget.ALIGN_CENTER);
		ImageLoader.into(thumb, video.thumbnail, THUMB_WIDTH, THUMB_HEIGHT, ImageLoader.Kind.OTHER);

		boolean isWatched = video.id != null && graph.history.isWatched(video.id);
		Box text = Box.column(4);
		if (isNext) {
			text.add(Label.of(I18n.t("details_up_next"), "accent-text"));
		}
		text.add(Label.wrapping(Fmt.episode(video), "episode-name"), Label.of(Fmt.day(video.released),
				"caption", "dim-label"));
		String summary = video.summary();
		if (summary != null) {
			Label about = Label.wrapping(summary, "episode-summary", "dim-label");
			about.lines(2).maxWidthChars(SUMMARY_CHARS);
			about.top();
			text.add(about);
		}
		HistoryEntry entry = video.id != null ? graph.history.get(video.id) : null;
		if (entry != null && !isWatched && entry.progress() > 0) {
			Progress progress = Progress.bar().fraction(entry.progress());
			progress.size(200, -1).halign(Widget.ALIGN_START);
			text.add(progress);
		}
		text.hexpand(true).valign(Widget.ALIGN_CENTER);
		Image mark = Image.icon(Icons.name("check_circle"), 22);
		mark.valign(Widget.ALIGN_CENTER);
		mark.visible(isWatched);
		mark.tooltip(I18n.t("details_watched"));
		Box content = Box.row(16, thumbBox, text, mark);
		Button row = Button.wrapping(content, "flat", "selectable-row");
		row.toggleClass("watched", isWatched);
		row.toggleClass("next-episode", isNext);
		row.onClick(() -> onEpisodeClick(video));
		Controller.contextMenu(row, (x, y) -> onEpisodeMenu(row, video, x, y));
		return row;
	}

	@NonNull
	private List<Integer> seasonNumbers() {
		return meta != null ? new ArrayList<>(meta.seasons().keySet()) : new ArrayList<>();
	}

	@NonNull
	private static String seasonLabel(int number) {
		return number == 0 ? I18n.t("details_specials") : I18n.t("details_season_number", number);
	}

	/** The button marks the season being looked at for a series, or the title itself. */
	private void showWatched() {
		Meta m = meta;
		boolean series = m != null && m.isSeries();
		boolean isWatched = m != null && isWatched(m);
		String watchedText = I18n.t(series
				? isWatched ? "details_mark_season_unwatched" : "details_mark_season_watched"
				: isWatched ? "details_mark_unwatched" : "details_mark_watched");
		watched.iconName(Icons.name(isWatched ? "check_circle" : "check")).tooltip(watchedText);
		watched.accessibleLabel(watchedText);
		boolean wholeSeries = series && loaded && !m.videosOrEmpty().isEmpty();
		posterFrame.tooltip(wholeSeries ? I18n.t(SeriesWatched.isWatched(graph.history, m, null,
				System.currentTimeMillis()) ? "details_poster_series_unwatched" : "details_poster_series_watched")
				: null);
		posterFrame.cursor(wholeSeries ? "pointer" : null);
	}

	private boolean isWatched(@NonNull Meta m) {
		if (!m.isSeries()) {
			String videoId = m.playableVideoId();
			return videoId != null && graph.history.isWatched(videoId);
		}
		return season >= 0 && SeriesWatched.isWatched(graph.history, m, season, System.currentTimeMillis());
	}

	private void onPlay() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		if (m.isSeries()) {
			Video next = graph.history.nextUp(m);
			if (next != null) {
				onEpisodeClick(next);
			}
			return;
		}
		String videoId = m.playableVideoId();
		if (videoId != null) {
			StreamsDialog.show(m, videoId, m.nameOrEmpty(), null);
		}
	}

	private void onEpisodeClick(@NonNull Video video) {
		Meta m = meta;
		if (m == null || video.id == null) {
			return;
		}
		StreamsDialog.show(m, video.id, m.nameOrEmpty(), Fmt.episode(video));
	}

	private void onEpisodeMenu(Widget owner, @NonNull Video video, double x, double y) {
		Meta m = meta;
		String videoId = video.id;
		if (m == null || videoId == null) {
			return;
		}
		QueueItem item = QueueItem.of(id, type, m.nameOrEmpty(), m.poster);
		item.videoId = videoId;
		item.episode = Fmt.episode(video);
		boolean isWatched = graph.history.isWatched(videoId);
		PosterMenu.showEpisode(owner, x, y, item, isWatched, () -> {
			graph.history.setWatched(videoId, !isWatched);
			refreshState();
		}, () -> QuickDownload.downloadEpisodes(m, Collections.singletonList(video)));
	}

	private void toggleSaved() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		if (graph.library.contains(id)) {
			graph.library.remove(id);
		} else {
			LibraryItem item = new LibraryItem();
			item.id = id;
			item.type = type;
			item.name = m.nameOrEmpty();
			item.poster = m.poster;
			item.released = m.isSeries() ? ReleaseOrder.latestOut(m, System.currentTimeMillis()) : ReleaseOrder.of(m);
			graph.library.add(item);
		}
		refreshState();
	}

	private void toggleWatched() {
		Meta m = meta;
		if (m == null) {
			return;
		}
		if (m.isSeries()) {
			if (season >= 0) {
				SeriesWatched.toggle(graph.history, m, season, System.currentTimeMillis());
			}
		} else {
			String videoId = m.playableVideoId();
			if (videoId != null) {
				graph.history.setWatched(videoId, !graph.history.isWatched(videoId));
			}
		}
		refreshState();
	}

	/** Clicking the main image marks the whole series, where the button marks one season. */
	private void toggleSeriesWatched() {
		Meta m = meta;
		if (m == null || !loaded || !m.isSeries() || m.videosOrEmpty().isEmpty()) {
			return;
		}
		boolean nowWatched = SeriesWatched.toggle(graph.history, m, null, System.currentTimeMillis());
		Toast.show(I18n.t(nowWatched ? "details_series_marked_watched" : "details_series_marked_unwatched"));
		refreshState();
	}

	@Nullable
	private String seriesId() {
		return Meta.TYPE_SERIES.equals(type) ? id : null;
	}

	private void downloadEpisodes() {
		Meta m = meta;
		if (m == null || !m.isSeries()) {
			return;
		}
		long now = System.currentTimeMillis();
		List<Video> inSeason = SeriesDownload.episodes(m, season, now);
		List<Video> all = SeriesDownload.episodes(m, null, now);
		if (inSeason.isEmpty() && all.isEmpty()) {
			Toast.show(I18n.t("download_episodes_none_out"));
			return;
		}
		String example = SeriesDownload.fileName(m.nameOrEmpty(), 1, 2, ".mkv");
		Dialogs.either(I18n.t("details_download_episodes"), I18n.t("download_episodes_message", example),
				I18n.t("download_episodes_season", seasonLabel(season),
						I18n.plural("episode_count", inSeason.size(), inSeason.size())),
				() -> startDownloads(m, inSeason),
				I18n.t("download_episodes_all", I18n.plural("episode_count", all.size(), all.size())),
				() -> startDownloads(m, all));
	}

	private void startDownloads(Meta m, List<Video> list) {
		QuickDownload.downloadEpisodes(m, list);
	}
}
