package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.Picture;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.ui.common.HomeHero;

public final class HeroBanner {

	public interface Listener {
		void onPlay(@NonNull HomeHero hero);

		void onSave(@NonNull HomeHero hero);

		void onInfo(@NonNull HomeHero hero);

		void onPick(int index);

		void onHover(boolean inside);
	}

	private static final int HEIGHT = 560;
	private static final long FADE_MS = 300;
	private static final int MIN_WIDTH = 1280;
	private static final int LOGO_WIDTH = 440;
	private static final int LOGO_HEIGHT = 150;
	private static final int TEXT_CHARS = 64;

	private final Picture backdrop = Picture.create(Picture.FIT_COVER);
	private final Picture logo = Picture.create(Picture.FIT_CONTAIN);
	private final Box logoRoom = Box.column(0);
	private final Layers logoBox = Layers.overlay(logoRoom, logo);
	private final Label type = Label.of(null, "hero-type");
	private final Label title = Label.wrapping(null, "hero-title");
	private final Label genres = Label.of(null, "hero-genres");
	private final Label description = Label.wrapping(null, "hero-description");
	private final Button play = Button.iconText(Icons.name("play"), I18n.t("details_play"), "hero-play");
	private final Button save = Button.iconText(Icons.name("add"), I18n.t("hero_save"), "hero-secondary");
	private final Button info = Button.iconText(Icons.name("info"), I18n.t("hero_info"), "hero-secondary");
	private final Box dots = Box.row(6);
	private final Layers banner;
	private final Widget root;
	private final Listener listener;
	@Nullable private HomeHero hero;
	@Nullable private String shownId;
	@Nullable private Object shownMeta;

	public HeroBanner(@NonNull Listener listener) {
		this.listener = listener;
		logoBox.halign(Widget.ALIGN_START);
		logoBox.visible(false);
		title.start();
		title.maxWidthChars(28);
		description.start();
		description.lines(3);
		description.maxWidthChars(TEXT_CHARS);
		type.halign(Widget.ALIGN_START);
		genres.halign(Widget.ALIGN_START);
		play.onClick(() -> {
			if (hero != null) {
				listener.onPlay(hero);
			}
		});
		save.onClick(() -> {
			if (hero != null) {
				listener.onSave(hero);
			}
		});
		info.onClick(() -> {
			if (hero != null) {
				listener.onInfo(hero);
			}
		});
		Box actions = Box.row(12, play, save, info);
		actions.halign(Widget.ALIGN_START);
		actions.margins(8, 0, 0, 0);
		dots.halign(Widget.ALIGN_START);
		dots.margins(10, 0, 0, 0);
		dots.visible(false);
		Box text = Box.column(10, type, logoBox, title, genres, description, actions, dots);
		text.addClass("hero-text");
		text.halign(Widget.ALIGN_START).valign(Widget.ALIGN_END);
		text.margins(0, 40, 36, 40);
		Box fade = Box.column(0);
		fade.addClass("hero-fade");
		Widget art = backdrop.fixed(-1, HEIGHT);
		art.addClass("hero-art");
		banner = Layers.overlay(art, fade, text);
		banner.addClass("hero");
		banner.clipChildren();
		Controller pointer = Controller.motion();
		pointer.onEnter((x, y) -> listener.onHover(true));
		pointer.onLeave(() -> listener.onHover(false));
		banner.addController(pointer);
		root = banner;
	}

	@NonNull
	public Widget view() {
		return root;
	}

	public void showDots(int count, int current) {
		dots.clear();
		dots.visible(count > 1);
		if (count <= 1) {
			return;
		}
		for (int i = 0; i < count; i++) {
			int index = i;
			Button dot = Button.wrapping(Box.column(0), "hero-dot");
			dot.tooltip(String.valueOf(i + 1));
			dot.toggleClass("active", i == current);
			dot.onClick(() -> listener.onPick(index));
			dots.add(dot);
		}
	}

	public void fadeTo(@NonNull HomeHero next, boolean saved, @NonNull Runnable after) {
		if (next.item.id.equals(shownId)) {
			show(next, saved);
			after.run();
			return;
		}
		banner.addClass("hero-dim");
		DesktopGraph.get().main.postDelayed(() -> {
			show(next, saved);
			banner.removeClass("hero-dim");
			after.run();
		}, FADE_MS);
	}

	public void show(@NonNull HomeHero next, boolean saved) {
		hero = next;
		save.label(I18n.t(saved ? "hero_saved" : "hero_save"));
		save.iconName(Icons.name(saved ? "check" : "add"));
		type.text(Fmt.typeLabel(next.item.type));
		String line = next.genres();
		genres.textOrHide(line.isEmpty() ? null : line);
		description.textOrHide(next.description());
		if (next.item.id.equals(shownId) && next.meta == shownMeta) {
			return;
		}
		shownId = next.item.id;
		shownMeta = next.meta;
		int width = Math.max(MIN_WIDTH, MainWindow.get().root().width());
		ImageLoader.into(backdrop, next.backdrop(), width, HEIGHT, ImageLoader.Kind.BACKDROP);
		title.text(next.name());
		title.visible(true);
		logoBox.visible(false);
		ImageLoader.into(logo, next.logo(), LOGO_WIDTH, LOGO_HEIGHT, ImageLoader.Kind.OTHER, this::showLogo);
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
}
