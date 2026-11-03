package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Picture;
import app.niixfliix.desktop.gtk.Popup;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.ui.common.HomeHero;
import app.niixfliix.ui.common.LibraryToggle;
import app.niixfliix.ui.common.PosterItem;

public final class HoverPreview {

	private static final long OPEN_DELAY_MS = 700;
	private static final long CLOSE_DELAY_MS = 250;
	private static final int ART_WIDTH = 340;
	private static final int ART_HEIGHT = 191;
	private static final int TEXT_CHARS = 40;

	@Nullable private static HoverPreview shown;

	private final DesktopGraph graph = DesktopGraph.get();
	private final Widget card;
	private final Runnable open = this::open;
	private final Runnable close = this::closeIfLeft;
	@Nullable private PosterItem item;
	@Nullable private Popup popup;
	private boolean overCard;
	private boolean overPopup;

	private HoverPreview(@NonNull Widget card) {
		this.card = card;
	}

	public static void attach(@NonNull Widget card, @NonNull ItemSource source) {
		HoverPreview preview = new HoverPreview(card);
		Controller motion = Controller.motion();
		motion.onEnter((x, y) -> {
			if (motion.isTouch()) {
				return;
			}
			preview.overCard = true;
			preview.item = source.current();
			preview.graph.main.removeCallbacks(preview.close);
			if (preview.popup == null && preview.item != null && worthIt(preview.item)) {
				preview.graph.main.postDelayed(preview.open, OPEN_DELAY_MS);
			}
		});
		motion.onLeave(() -> {
			preview.overCard = false;
			preview.graph.main.removeCallbacks(preview.open);
			preview.closeSoon();
		});
		card.addController(motion);
	}

	public interface ItemSource {
		@Nullable
		PosterItem current();
	}

	public static void dismiss() {
		HoverPreview current = shown;
		if (current != null) {
			current.closeNow();
		}
	}

	private static boolean worthIt(@NonNull PosterItem item) {
		return !item.header && !item.id.isEmpty() && !Catalog.isLiveType(item.type)
				&& (Meta.TYPE_MOVIE.equals(item.type) || Meta.TYPE_SERIES.equals(item.type));
	}

	private void closeSoon() {
		graph.main.removeCallbacks(close);
		graph.main.postDelayed(close, CLOSE_DELAY_MS);
	}

	private void open() {
		PosterItem target = item;
		if (target == null || !overCard || !card.alive() || !card.isMapped() || popup != null) {
			return;
		}
		if (shown != null && shown != this) {
			shown.closeNow();
		}
		Picture art = Picture.create(Picture.FIT_COVER);
		Box artFrame = Box.column(0, art.fixed(ART_WIDTH, ART_HEIGHT));
		artFrame.addClass("preview-art");
		artFrame.clipChildren();
		Label title = Label.wrapping(target.name, "preview-title");
		title.start();
		title.maxWidthChars(TEXT_CHARS);
		Label info = Label.wrapping(null, "preview-info");
		info.start();
		info.maxWidthChars(TEXT_CHARS);
		info.visible(false);
		Label description = Label.wrapping(null, "preview-description");
		description.start();
		description.lines(3);
		description.maxWidthChars(TEXT_CHARS);
		description.visible(false);
		Button play = Button.iconText(Icons.name("play"), I18n.t("details_play"), "hero-play", "compact");
		play.onClick(() -> {
			closeNow();
			MainWindow.get().openDetails(target, true);
		});
		boolean saved = graph.library.contains(target.id);
		Button save = Button.icon(Icons.name(saved ? "check" : "add"),
				I18n.t(saved ? "details_unsave" : "details_save"));
		save.addClass("preview-round");
		save.onClick(() -> {
			boolean nowSaved = LibraryToggle.toggle(graph.library, target);
			save.iconName(Icons.name(nowSaved ? "check" : "add"));
			save.tooltip(I18n.t(nowSaved ? "details_unsave" : "details_save"));
			Toast.show(I18n.t(nowSaved ? "library_added" : "library_removed"));
		});
		Button more = Button.icon(Icons.name("info"), I18n.t("hero_info"));
		more.addClass("preview-round");
		more.onClick(() -> {
			closeNow();
			MainWindow.get().openDetails(target);
		});
		Box actions = Box.row(8, play, save, more);
		actions.halign(Widget.ALIGN_START);
		Box text = Box.column(6, title, info, description, actions);
		text.margins(12, 14, 14, 14);
		Box content = Box.column(0, artFrame, text);
		content.size(ART_WIDTH, -1);
		Controller motion = Controller.motion();
		motion.onEnter((x, y) -> {
			overPopup = true;
			graph.main.removeCallbacks(close);
		});
		motion.onLeave(() -> {
			overPopup = false;
			closeSoon();
		});
		content.addController(motion);
		Popup next = new Popup(content, "hover-preview");
		next.onClosed(() -> {
			if (popup == next) {
				popup = null;
			}
			if (shown == this) {
				shown = null;
			}
		});
		popup = next;
		shown = this;
		ImageLoader.into(art, target.poster, ART_WIDTH, ART_HEIGHT, ImageLoader.Kind.POSTER);
		next.showBeside(card, Popup.SIDE_RIGHT);
		graph.io.execute(() -> {
			Meta meta = MetaFinder.find(graph.addons, graph.addonClient, target.type, target.id);
			graph.main.post(() -> {
				if (meta == null || popup != next || !next.isOpen()) {
					return;
				}
				HomeHero hero = new HomeHero(target, meta);
				String backdrop = meta.background != null && !meta.background.isEmpty() ? meta.background : null;
				if (backdrop != null) {
					ImageLoader.into(art, backdrop, ART_WIDTH, ART_HEIGHT, ImageLoader.Kind.BACKDROP);
				}
				info.textOrHide(infoLine(meta, hero.genres()));
				description.textOrHide(meta.description);
			});
		});
	}

	@Nullable
	private static String infoLine(@NonNull Meta meta, @NonNull String genres) {
		List<String> parts = new ArrayList<>();
		if (meta.releaseInfo != null && !meta.releaseInfo.isEmpty()) {
			parts.add(meta.releaseInfo);
		}
		if (meta.runtime != null && !meta.runtime.isEmpty()) {
			parts.add(meta.runtime);
		}
		if (!genres.isEmpty()) {
			parts.add(genres);
		}
		return parts.isEmpty() ? null : String.join(" · ", parts);
	}

	private void closeIfLeft() {
		if (!overCard && !overPopup) {
			closeNow();
		}
	}

	private void closeNow() {
		graph.main.removeCallbacks(open);
		graph.main.removeCallbacks(close);
		overPopup = false;
		Popup current = popup;
		if (current != null) {
			current.close();
		}
	}
}
