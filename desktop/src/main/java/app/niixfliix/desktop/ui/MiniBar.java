package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;

import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Picture;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.player.DesktopPlayback;
import app.niixfliix.desktop.player.VideoPlayer;
import app.niixfliix.player.PlaySession;

final class MiniBar implements DesktopPlayback.Listener {

	private static final int THUMB_WIDTH = 40;
	private static final int THUMB_HEIGHT = 60;
	private static final long TICK_MS = 1000;

	private final DesktopPlayback playback = DesktopGraph.get().playback();
	private final Box root = Box.column(0);
	private final Picture poster = Picture.create(Picture.FIT_COVER);
	private final Label title = Label.of(null, "heading");
	private final Label subtitle = Label.of(null, "caption", "dim-label");
	private final Button playPause = Button.icon(Icons.name("play"), I18n.t("player_play_pause"));
	private final Button next = Button.icon(Icons.name("skip_next"), I18n.t("action_next"));
	private final Progress progress = Progress.bar();
	private final Runnable tick = this::tick;

	MiniBar(@NonNull Runnable open) {
		Widget thumb = poster.fixed(THUMB_WIDTH, THUMB_HEIGHT);
		thumb.addClass("mini-poster");
		thumb.clipChildren();
		thumb.valign(Widget.ALIGN_CENTER);
		title.ellipsize();
		subtitle.ellipsize();
		Box text = Box.column(2, title, subtitle);
		text.valign(Widget.ALIGN_CENTER).hexpand(true);
		Button openPlayer = Button.wrapping(Box.row(12, thumb, text), "flat", "mini-open");
		openPlayer.hexpand(true);
		openPlayer.tooltip(I18n.t("player_open"));
		openPlayer.onClick(open);
		playPause.onClick(playback::togglePlay);
		next.onClick(playback::playNextNow);
		Button close = Button.icon(Icons.name("close"), I18n.t("action_close"));
		close.onClick(playback::stop);
		Box bar = Box.row(6, openPlayer, playPause, next, close);
		bar.addClass("mini-bar");
		progress.addClass("mini-progress");
		root.add(progress, bar);
		root.visible(false);
		playback.addListener(this);
	}

	@NonNull
	Widget view() {
		return root;
	}

	@Override
	public void onNowPlayingChanged() {
		boolean show = playback.inBackground();
		root.visible(show);
		DesktopGraph.get().main.removeCallbacks(tick);
		if (!show) {
			return;
		}
		PlaySession session = playback.session();
		title.text(playback.title());
		subtitle.text(playback.subtitle());
		ImageLoader.into(poster, session != null ? session.poster : null, THUMB_WIDTH, THUMB_HEIGHT,
				ImageLoader.Kind.POSTER);
		VideoPlayer player = playback.player();
		boolean playing = player != null && player.isPlaying();
		playPause.iconName(Icons.name(playing ? "pause" : "play"));
		next.visible(playback.hasNext());
		tick();
	}

	private void tick() {
		VideoPlayer player = playback.player();
		long duration = player != null ? player.durationMs() : 0;
		progress.fraction(duration > 0 ? (double) player.positionMs() / duration : 0);
		if (playback.inBackground()) {
			DesktopGraph.get().main.postDelayed(tick, TICK_MS);
		}
	}
}
