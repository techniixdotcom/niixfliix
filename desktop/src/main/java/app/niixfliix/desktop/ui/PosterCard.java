package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.Core;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.Picture;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.ui.common.PosterItem;

public final class PosterCard {

	public static final int WIDTH = 150;
	public static final int HEIGHT = 225;
	public static final int CARD_HEIGHT = HEIGHT + 52;

	private static final int KEY_MENU = 0xff67;
	private static final int KEY_F10 = 0xffc7;

	public interface Listener {
		void onPosterClick(@NonNull PosterItem item);

		void onPosterMenu(@NonNull PosterItem item, @NonNull Widget card, double x, double y);
	}

	private final Picture poster = Picture.create(Picture.FIT_COVER);
	private final Label fallback = Label.wrapping(null, "poster-fallback");
	private final Label badge = Label.of(I18n.t("poster_new"), "new-badge");
	private final Progress progress = Progress.bar();
	private final Label title = Label.of(null, "poster-title");
	private final Box frame;
	private final Button root;
	@Nullable private PosterItem item;

	public PosterCard(@NonNull Listener listener) {
		fallback.center();
		fallback.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER).margin(12);
		badge.halign(Widget.ALIGN_END).valign(Widget.ALIGN_START).margin(6);
		badge.visible(false);
		progress.addClass("poster-progress");
		progress.valign(Widget.ALIGN_END);
		progress.visible(false);
		Layers layers = Layers.overlay(fallback, poster, badge, progress);
		frame = Box.column(0, layers);
		frame.addClass("poster-frame");
		frame.size(WIDTH, HEIGHT);
		frame.clipChildren();
		layers.vexpand(true);
		title.lines(2).maxWidthChars(1);
		title.top();
		title.size(WIDTH, -1);
		Box content = Box.column(6, frame, title);
		content.size(WIDTH, -1);
		root = Button.wrapping(content, "flat", "poster-card");
		root.halign(Widget.ALIGN_START).valign(Widget.ALIGN_START);
		root.onClick(() -> {
			HoverPreview.dismiss();
			if (item != null) {
				listener.onPosterClick(item);
			}
		});
		HoverPreview.attach(root, () -> item);
		Controller.contextMenu(root, (x, y) -> {
			HoverPreview.dismiss();
			if (item != null) {
				listener.onPosterMenu(item, root, x, y);
			}
		});
		Controller keys = Controller.keys();
		keys.onKey((keyval, keycode, state) -> {
			boolean shiftF10 = keyval == KEY_F10 && (state & Controller.MODIFIER_SHIFT) != 0;
			if (item != null && (keyval == KEY_MENU || shiftF10)) {
				listener.onPosterMenu(item, root, WIDTH / 2.0, HEIGHT / 2.0);
				return true;
			}
			return false;
		});
		root.addController(keys);
	}

	@NonNull
	public Widget widget() {
		return root;
	}

	public void bind(@NonNull PosterItem item) {
		this.item = item;
		String name = Core.get().translations.name(item.id);
		String shown = name != null ? name : item.name;
		if (name == null) {
			Core.get().translations.request(item.id, () -> {
				if (this.item == item) {
					bind(item);
				}
			});
		}
		title.text(shown);
		fallback.text(shown);
		poster.fit(item.wholePicture ? Picture.FIT_CONTAIN : Picture.FIT_COVER);
		frame.toggleClass("whole-picture", item.wholePicture);
		ImageLoader.into(poster, item.poster, WIDTH, HEIGHT, ImageLoader.Kind.POSTER);
		badge.visible(item.isNew);
		boolean watched = isWatched(item);
		root.toggleClass("watched", watched);
		String spoken = item.isNew ? I18n.t("poster_new_episode", shown) : watched ? I18n.t("poster_seen", shown) : shown;
		if (item.progress >= 0) {
			progress.visible(true);
			progress.fraction(item.progress);
			root.accessibleLabel(I18n.t("poster_watched", spoken, Math.round(item.progress * 100)));
		} else {
			progress.visible(false);
			root.accessibleLabel(spoken);
		}
	}

	public static boolean isWatched(@NonNull PosterItem item) {
		return !item.header && !Meta.TYPE_SERIES.equals(item.type) && Core.get().history.isWatched(item.id);
	}
}
