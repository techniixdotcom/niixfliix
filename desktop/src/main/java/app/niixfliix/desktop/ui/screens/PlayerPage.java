package app.niixfliix.desktop.ui.screens;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.app.Constant;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.gtk.Bars;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.FileChooser;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Image;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.MainLoop;
import app.niixfliix.desktop.gtk.Menu;
import app.niixfliix.desktop.gtk.Progress;
import app.niixfliix.desktop.gtk.Scale;
import app.niixfliix.desktop.gtk.Texture;
import app.niixfliix.desktop.gtk.VideoView;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.gtk.Window;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Paths;
import app.niixfliix.desktop.player.AddonSubtitle;
import app.niixfliix.desktop.player.DesktopPlayback;
import app.niixfliix.desktop.player.VideoPlayer;
import app.niixfliix.desktop.ui.Dialogs;
import app.niixfliix.desktop.ui.Fmt;
import app.niixfliix.desktop.ui.MainWindow;
import app.niixfliix.desktop.ui.Page;
import app.niixfliix.desktop.ui.Toast;
import app.niixfliix.player.PlaySession;

public final class PlayerPage implements Page, DesktopPlayback.Screen, DesktopPlayback.Listener {

	private static final long HIDE_CONTROLS_MS = 3000;
	private static final long INDICATOR_MS = 800;
	private static final long UP_NEXT_OVERLAY_MS = 30_000;
	private static final long TICK_MS = 250;
	private static final long SEEK_SETTLE_MS = 200;
	private static final int VOLUME_STEP = 5;
	private static final int FADE_MS = 200;
	private static final int UP_NEXT_WIDTH = 360;

	private static final int KEY_SPACE = 0x020;
	private static final int KEY_ESCAPE = 0xff1b;
	private static final int KEY_LEFT = 0xff51;
	private static final int KEY_UP = 0xff52;
	private static final int KEY_RIGHT = 0xff53;
	private static final int KEY_DOWN = 0xff54;
	private static final int KEY_F11 = 0xffc8;
	private static final int KEY_PLAY = 0x1008ff14;
	private static final int KEY_PAUSE = 0x1008ff31;
	private static final int KEY_NEXT = 0x1008ff17;
	private static final int KEY_MUTE = 0x1008ff12;
	private static final int KEY_REWIND = 0x1008ff3e;
	private static final int KEY_FORWARD = 0x1008ff97;

	private final MainWindow window;
	private final DesktopGraph graph = DesktopGraph.get();
	private final DesktopPlayback playback = graph.playback();
	private final VideoView video = new VideoView();
	private final Frames frames = new Frames();
	private final Box loadingBox = Box.column(12);
	private final Label loadingText = Label.wrapping(null, "loading-text");
	private final Label title = Label.of(null, "player-title");
	private final Label subtitle = Label.of(null, "player-subtitle");
	private final Scale seek = Scale.horizontal(0, 1, 1000);
	private final Label position = Label.of("0:00", "player-time");
	private final Label duration = Label.of("0:00", "player-time");
	private final Button playPause = Button.icon(Icons.name("play"), I18n.t("player_play_pause"));
	private final Button next = Button.icon(Icons.name("skip_next"), I18n.t("action_next"));
	private final Button mute = Button.icon(Icons.name("volume_up"), I18n.t("player_mute"));
	private final Scale volume = Scale.horizontal(0, 100, 1);
	private final Button fullscreen = Button.icon(Icons.name("fullscreen"), I18n.t("player_fullscreen"));
	private final Box topBar;
	private final Widget windowButtons = Bars.windowControls();
	private final Box bottomBar;
	private final Layers topControls;
	private final Layers bottomControls;
	private final Box errorCard = Box.column(14);
	private final Label errorText = Label.wrapping(null);
	private final Button tryNext = Dialogs.button(I18n.t("player_try_next"), true);
	private final Box upNextCard = Box.column(6);
	private final Label upNextLabel = Label.of(null, "player-subtitle", "caption");
	private final Label upNextTitle = Label.wrapping(null, "player-title");
	private final Progress upNextProgress = Progress.bar();
	private final Label indicator = Label.of(null, "player-indicator");
	private final Layers root;
	private final Runnable tick = this::tick;
	private final Runnable hideControls = this::hideControlsNow;
	private final Runnable hideIndicator = () -> indicator.visible(false);
	private final Runnable seekNow = this::seekNow;

	private boolean showing;
	private boolean controlsShown = true;
	private boolean upNextShowing;
	private long upNextWindow = 1;
	private long upNextRemaining = Long.MAX_VALUE;
	private double pendingSeekMs = -1;
	@Nullable private Menu openMenu;
	private double lastPointerX = Double.NaN;
	private double lastPointerY = Double.NaN;

	public PlayerPage(@NonNull MainWindow window) {
		this.window = window;

		Progress spinner = Progress.spinner(56);
		spinner.halign(Widget.ALIGN_CENTER);
		loadingText.center();
		loadingText.maxWidthChars(48);
		loadingBox.add(spinner, loadingText);
		loadingBox.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER);
		loadingBox.canTarget(false);
		loadingBox.visible(false);

		topBar = buildTop();
		bottomBar = buildBottom();
		topControls = Layers.revealer(Bars.handle(topBar), FADE_MS).reveal(true);
		topControls.valign(Widget.ALIGN_START);
		bottomControls = Layers.revealer(bottomBar, FADE_MS).reveal(true);
		bottomControls.valign(Widget.ALIGN_END);
		buildErrorCard();
		buildUpNextCard();
		indicator.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER);
		indicator.canTarget(false);
		indicator.visible(false);

		Box base = Box.column(0, video.widget());
		base.addClass("player");
		root = Layers.overlay(base, loadingBox, topControls, bottomControls, indicator, upNextCard, errorCard);
		root.hexpand(true).vexpand(true);

		Controller clicks = Controller.click(Controller.BUTTON_PRIMARY);
		clicks.onPressed((count, x, y) -> {
			if (clicks.isTouch()) {
				onTap(count, x);
				return;
			}
			if (count == 1) {
				playback.togglePlay();
			} else if (count == 2) {
				playback.togglePlay();
				toggleFullscreen();
			}
		});
		video.picture().addController(clicks);
		Controller wheel = Controller.scroll(Controller.SCROLL_VERTICAL);
		wheel.onScroll((dx, dy) -> {
			if (dy != 0) {
				changeVolume(dy < 0 ? VOLUME_STEP : -VOLUME_STEP);
			}
			return true;
		});
		video.picture().addController(wheel);
		Controller motion = Controller.motion();
		motion.onMotion((x, y) -> {
			if (x != lastPointerX || y != lastPointerY) {
				lastPointerX = x;
				lastPointerY = y;
				showControls();
			}
		});
		root.addController(motion);
		Controller keys = Controller.keys();
		keys.phase(Controller.PHASE_CAPTURE);
		keys.onKey(this::onKey);
		window.window().addController(keys);
		window.window().onFullscreenChanged(this::updateFullscreenIcon);
		updatePlayIcon();
		updateMuteIcon();
		updateFullscreenIcon();
	}

	private Box buildTop() {
		Button minimize = Button.icon(Icons.name("minimize"), I18n.t("player_minimize"));
		minimize.onClick(window::hidePlayer);
		title.ellipsize();
		subtitle.ellipsize();
		Box titles = Box.column(2, title, subtitle);
		titles.valign(Widget.ALIGN_CENTER).hexpand(true);
		windowButtons.valign(Widget.ALIGN_CENTER);
		Box top = Box.row(12, minimize, titles, windowButtons);
		top.addClass("player-top");
		return top;
	}

	private Box buildBottom() {
		seek.hexpand(true);
		seek.addClass("seek-bar");
		seek.onUserChange(value -> {
			pendingSeekMs = value;
			position.text(Fmt.time((long) value));
			graph.main.removeCallbacks(seekNow);
			graph.main.postDelayed(seekNow, SEEK_SETTLE_MS);
		});
		Box seekRow = Box.row(12, position, seek, duration);

		playPause.onClick(playback::togglePlay);
		Button back = Button.icon(Icons.name("fast_rewind"),
				I18n.t("player_seek_back", Constant.SEEK_STEP_MS / 1000));
		back.onClick(() -> seekBy(-Constant.SEEK_STEP_MS));
		Button forward = Button.icon(Icons.name("fast_forward"),
				I18n.t("player_seek_forward", Constant.SEEK_STEP_MS / 1000));
		forward.onClick(() -> seekBy(Constant.SEEK_STEP_MS));
		next.onClick(playback::playNextNow);
		mute.onClick(this::toggleMute);
		volume.size(110, -1);
		volume.steps(VOLUME_STEP, VOLUME_STEP * 2);
		volume.addClass("volume-bar");
		volume.value(graph.desktopPrefs.volume());
		volume.onUserChange(value -> {
			playback.setVolume((int) Math.round(Math.max(0, Math.min(100, value))));
			updateMuteIcon();
		});
		Button subtitles = Button.icon(Icons.name("subtitles"), I18n.t("player_subtitles"));
		subtitles.onClick(() -> showSubtitles(subtitles));
		Button audio = Button.icon(Icons.name("audio"), I18n.t("player_audio"));
		audio.onClick(() -> showAudio(audio));
		Button streams = Button.icon(Icons.name("quality"), I18n.t("player_switch_stream"));
		streams.onClick(() -> showStreams(streams));
		fullscreen.onClick(this::toggleFullscreen);
		Box buttons = Box.row(6, playPause, back, forward, next, mute, volume, Widget.spacer(), subtitles, audio,
				streams, fullscreen);
		for (Widget w : new Widget[] {playPause, back, forward, next, mute, volume, subtitles, audio, streams,
				fullscreen}) {
			w.valign(Widget.ALIGN_CENTER);
		}
		Box bottom = Box.column(6, seekRow, buttons);
		bottom.addClass("player-bottom");
		return bottom;
	}

	private void buildErrorCard() {
		errorText.maxWidthChars(52);
		tryNext.onClick(playback::tryNextStream);
		Button close = Dialogs.button(I18n.t("action_close"), false);
		close.onClick(playback::stop);
		Box actions = Box.row(10, close, tryNext);
		actions.halign(Widget.ALIGN_END);
		Image icon = Image.icon(Icons.name("error"), 40);
		icon.halign(Widget.ALIGN_START);
		errorCard.add(icon, errorText, actions);
		errorCard.addClass("player-card");
		errorCard.halign(Widget.ALIGN_CENTER).valign(Widget.ALIGN_CENTER);
		errorCard.visible(false);
	}

	private void buildUpNextCard() {
		Button close = Button.icon(Icons.name("close"), I18n.t("player_up_next_cancel"));
		close.onClick(playback::cancelUpNext);
		upNextLabel.hexpand(true);
		Box header = Box.row(8, upNextLabel, close);
		upNextCard.add(header, upNextTitle, upNextProgress);
		upNextCard.addClass("player-card");
		upNextCard.size(UP_NEXT_WIDTH, -1);
		upNextCard.halign(Widget.ALIGN_END).valign(Widget.ALIGN_END);
		upNextCard.cursor("pointer");
		Controller click = Controller.click(Controller.BUTTON_PRIMARY);
		click.onReleased((count, x, y) -> playback.playNextNow());
		upNextCard.addController(click);
		upNextCard.visible(false);
	}

	@NonNull
	@Override
	public Widget view() {
		return root;
	}

	@Override
	public void onShow() {
		showing = true;
		playback.setFrameSink(frames);
		playback.attach(this);
		playback.addListener(this);
		onVideoChanged();
		if (graph.desktopPrefs.playerFullscreen()) {
			window.window().fullscreen(true);
		}
		volume.value(graph.desktopPrefs.volume());
		tick();
		onNowPlayingChanged();
		showControls();
		playPause.grabFocus();
	}

	@Override
	public void onHide() {
		showing = false;
		graph.main.removeCallbacks(tick);
		closeMenu();
		graph.main.removeCallbacks(hideControls);
		root.cursor(null);
		playback.removeListener(this);
		playback.detach(this);
		playback.setFrameSink(null);
	}

	@Override
	public void onTitle(@NonNull String newTitle, @NonNull String newSubtitle) {
		if (!newTitle.equals(title.text()) || !newSubtitle.equals(subtitle.text())) {
			frames.clear();
		}
		title.text(newTitle);
		subtitle.textOrHide(newSubtitle);
	}

	@Override
	public void onLoading(@Nullable String text) {
		loadingBox.visible(text != null);
		loadingText.text(text);
	}

	@Override
	public void onError(@Nullable String message, boolean offerNext) {
		errorCard.visible(message != null);
		errorText.text(message);
		tryNext.visible(offerNext);
		if (message != null) {
			showControls();
		}
	}

	@Override
	public void onUpNext(@Nullable String nextTitle, long remainingMs) {
		if (nextTitle != null && !upNextShowing) {
			upNextWindow = Math.max(1, remainingMs);
		}
		upNextShowing = nextTitle != null;
		upNextRemaining = nextTitle != null ? remainingMs : Long.MAX_VALUE;
		if (nextTitle != null) {
			upNextTitle.text(nextTitle);
			long seconds = (remainingMs + 999) / 1000;
			upNextLabel.text(I18n.t("player_up_next_in",
					String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)));
			upNextProgress.fraction(1 - Math.min(1, (double) remainingMs / upNextWindow));
		}
		updateUpNextVisibility();
	}

	@Override
	public void onVideoChanged() {
		VideoPlayer p = playback.player();
		MemorySegment paintable = p != null ? p.paintable() : null;
		if (paintable != null) {
			video.show(paintable);
		} else if (p == null || !frames.hasFrame()) {
			video.show((MemorySegment) null);
		}
	}

	@Override
	public void onClosed() {
		window.hidePlayer();
	}

	@Override
	public void onNowPlayingChanged() {
		next.visible(playback.hasNext());
		updatePlayIcon();
		if (!isPlaying()) {
			showControls();
		} else {
			scheduleHide();
		}
	}

	private boolean isPlaying() {
		VideoPlayer p = playback.player();
		return p != null && p.isPlaying();
	}

	private void tick() {
		graph.main.removeCallbacks(tick);
		VideoPlayer p = playback.player();
		long total = p != null ? Math.max(0, p.durationMs()) : 0;
		long now = p != null ? Math.max(0, p.positionMs()) : 0;
		duration.text(Fmt.time(total));
		seek.sensitive(total > 0);
		if (pendingSeekMs < 0) {
			seek.range(0, Math.max(1, total));
			seek.value(Math.min(now, Math.max(1, total)));
			position.text(Fmt.time(now));
		}
		updatePlayIcon();
		if (showing) {
			graph.main.postDelayed(tick, TICK_MS);
		}
	}

	private void seekNow() {
		VideoPlayer p = playback.player();
		double target = pendingSeekMs;
		pendingSeekMs = -1;
		if (p != null && target >= 0 && p.durationMs() > 0) {
			p.seekTo((long) target);
		}
	}

	private void seekBy(long deltaMs) {
		playback.seekBy(deltaMs);
		showIndicator(I18n.t(deltaMs > 0 ? "player_seek_forward" : "player_seek_back", Math.abs(deltaMs) / 1000));
	}

	private void changeVolume(int delta) {
		VideoPlayer p = playback.player();
		if (p != null && p.isMuted()) {
			p.setMuted(false);
		}
		int value = (int) Math.max(0, Math.min(100, Math.round(volume.value()) + delta));
		volume.value(value);
		playback.setVolume(value);
		updateMuteIcon();
		showIndicator(I18n.t("percent", value));
	}

	private void toggleMute() {
		VideoPlayer p = playback.player();
		if (p == null) {
			return;
		}
		p.setMuted(!p.isMuted());
		updateMuteIcon();
	}

	private void updatePlayIcon() {
		playPause.iconName(Icons.name(isPlaying() ? "pause" : "play"));
	}

	private void updateMuteIcon() {
		VideoPlayer p = playback.player();
		boolean silent = p != null && p.isMuted() || volume.value() <= 0;
		mute.iconName(Icons.name(silent ? "volume_off" : "volume_up"));
	}

	private void toggleFullscreen() {
		Window w = window.window();
		boolean on = !w.isFullscreen();
		w.fullscreen(on);
		graph.desktopPrefs.setPlayerFullscreen(on);
	}

	private void updateFullscreenIcon() {
		boolean on = window.window().isFullscreen();
		windowButtons.visible(!on);
		String text = I18n.t(on ? "player_exit_fullscreen" : "player_fullscreen");
		fullscreen.iconName(Icons.name(on ? "fullscreen_exit" : "fullscreen")).tooltip(text);
		fullscreen.accessibleLabel(text);
	}

	private void onTap(int count, double x) {
		if (count == 1) {
			toggleControlsByTap();
			return;
		}
		if (count == 2) {
			toggleControlsByTap();
		}
		int width = video.picture().width();
		if (width > 0 && x < width / 3.0) {
			seekBy(-Constant.SEEK_STEP_MS);
		} else if (width > 0 && x > width * 2 / 3.0) {
			seekBy(Constant.SEEK_STEP_MS);
		} else if (count == 2) {
			playback.togglePlay();
		}
	}

	private void toggleControlsByTap() {
		if (!controlsShown) {
			showControls();
			return;
		}
		if (openMenu != null || errorCard.isVisible() || Dialogs.isOpen()) {
			return;
		}
		graph.main.removeCallbacks(hideControls);
		controlsShown = false;
		topControls.reveal(false);
		bottomControls.reveal(false);
		updateUpNextVisibility();
	}

	private void showControls() {
		if (!controlsShown) {
			controlsShown = true;
			topControls.reveal(true);
			bottomControls.reveal(true);
			root.cursor(null);
			updateUpNextVisibility();
		}
		scheduleHide();
	}

	private void scheduleHide() {
		graph.main.removeCallbacks(hideControls);
		if (showing && isPlaying()) {
			graph.main.postDelayed(hideControls, HIDE_CONTROLS_MS);
		}
	}

	private void hideControlsNow() {
		if (!showing || !isPlaying() || openMenu != null || errorCard.isVisible() || Dialogs.isOpen()) {
			return;
		}
		if (topBar.isHovered() || bottomBar.isHovered() || upNextCard.isHovered()) {
			scheduleHide();
			return;
		}
		controlsShown = false;
		topControls.reveal(false);
		bottomControls.reveal(false);
		root.cursor("none");
		updateUpNextVisibility();
	}

	private void updateUpNextVisibility() {
		upNextCard.visible(upNextShowing && (controlsShown || upNextRemaining <= UP_NEXT_OVERLAY_MS));
		upNextCard.margins(0, 24, controlsShown ? 132 : 24, 0);
	}

	private void showIndicator(String text) {
		indicator.text(text);
		indicator.visible(true);
		graph.main.removeCallbacks(hideIndicator);
		graph.main.postDelayed(hideIndicator, INDICATOR_MS);
	}

	private boolean onKey(int keyval, int keycode, int state) {
		if (!showing || Dialogs.isOpen()
				|| (state & (Controller.MODIFIER_CONTROL | Controller.MODIFIER_ALT | Controller.MODIFIER_SUPER)) != 0) {
			return false;
		}
		int key = keyval >= 'A' && keyval <= 'Z' ? keyval + ('a' - 'A') : keyval;
		switch (key) {
			case KEY_SPACE:
			case 'k':
			case KEY_PLAY:
			case KEY_PAUSE:
				playback.togglePlay();
				break;
			case KEY_LEFT:
			case 'j':
			case KEY_REWIND:
				seekBy(-Constant.SEEK_STEP_MS);
				break;
			case KEY_RIGHT:
			case 'l':
			case KEY_FORWARD:
				seekBy(Constant.SEEK_STEP_MS);
				break;
			case KEY_UP:
				changeVolume(VOLUME_STEP);
				break;
			case KEY_DOWN:
				changeVolume(-VOLUME_STEP);
				break;
			case 'f':
			case KEY_F11:
				toggleFullscreen();
				break;
			case 'm':
			case KEY_MUTE:
				toggleMute();
				break;
			case 'n':
			case KEY_NEXT:
				if (playback.hasNext()) {
					playback.playNextNow();
				}
				break;
			case KEY_ESCAPE:
				if (openMenu != null) {
					closeMenu();
				} else if (window.window().isFullscreen()) {
					window.window().fullscreen(false);
					graph.desktopPrefs.setPlayerFullscreen(false);
				} else {
					window.hidePlayer();
				}
				break;
			default:
				return false;
		}
		showControls();
		return true;
	}

	private void showMenu(Widget anchor, Menu menu) {
		closeMenu();
		openMenu = menu;
		menu.onClosed(() -> {
			if (openMenu == menu) {
				openMenu = null;
				scheduleHide();
			}
		});
		menu.showAbove(anchor);
	}

	private void closeMenu() {
		if (openMenu != null) {
			Menu menu = openMenu;
			openMenu = null;
			menu.close();
		}
	}

	private void showSubtitles(Widget anchor) {
		VideoPlayer p = playback.player();
		if (p == null) {
			return;
		}
		Menu menu = new Menu();
		int current = p.currentText();
		int external = p.externalText();
		String externalUri = external >= 0 && current == external ? p.subtitleUri() : null;
		menu.choice(I18n.t("player_subtitles_off"), current < 0, playback::subtitlesOff);
		int number = 1;
		for (VideoPlayer.Track t : p.textTracks()) {
			if (t.index == external) {
				continue;
			}
			menu.choice(trackLabel(t, number++), t.index == current, () -> playback.selectText(t));
		}
		boolean externalShown = false;
		List<AddonSubtitle> fromAddons = playback.addonSubtitles();
		if (!fromAddons.isEmpty()) {
			menu.separator();
		}
		for (AddonSubtitle s : fromAddons) {
			String language = s.language != null ? Fmt.language(s.language) : "";
			String label = I18n.t("player_subtitle_from_addon",
					language.isEmpty() ? I18n.t("player_track_unknown") : language, s.addonName);
			boolean selected = s.url.equals(externalUri) && !externalShown;
			externalShown |= selected;
			menu.choice(label, selected, () -> playback.selectAddonSubtitle(s));
		}
		if (externalUri != null && !externalShown) {
			menu.choice(fileName(externalUri), true, () -> { });
		}
		menu.separator();
		menu.item(I18n.t("player_subtitles_from_file"), this::pickSubtitleFile);
		showMenu(anchor, menu);
	}

	private static String fileName(String uri) {
		int slash = uri.lastIndexOf('/');
		String name = slash >= 0 ? uri.substring(slash + 1) : uri;
		try {
			name = URLDecoder.decode(name, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
		}
		return name.isEmpty() ? I18n.t("player_subtitle_file") : name;
	}

	private void pickSubtitleFile() {
		FileChooser.open(root, I18n.t("player_subtitles_from_file"), Paths.downloads(), file -> {
			if (file.isFile()) {
				playback.selectLocalSubtitle(file);
			}
		}, new FileChooser.Filter(I18n.t("player_subtitle_files"), "*.srt", "*.vtt", "*.ass", "*.ssa", "*.sub",
				"*.smi"), new FileChooser.Filter(I18n.t("player_all_files"), "*"));
	}

	private void showAudio(Widget anchor) {
		VideoPlayer p = playback.player();
		if (p == null) {
			return;
		}
		Menu menu = new Menu();
		int current = p.currentAudio();
		int number = 1;
		for (VideoPlayer.Track t : p.audioTracks()) {
			menu.choice(trackLabel(t, number++), t.index == current, () -> playback.selectAudio(t));
		}
		if (menu.isEmpty()) {
			menu.note(I18n.t("player_no_audio_tracks"));
		}
		showMenu(anchor, menu);
	}

	private static String trackLabel(VideoPlayer.Track t, int number) {
		String language = t.language != null ? Fmt.language(t.language) : "";
		if (t.title != null && !t.title.isEmpty()) {
			return language.isEmpty() || t.title.contains(language) ? t.title
					: I18n.t("player_track_label", language, t.title);
		}
		return language.isEmpty() ? I18n.t("player_track_number", number) : language;
	}

	private void showStreams(Widget anchor) {
		PlaySession session = playback.session();
		if (session == null) {
			return;
		}
		List<StreamInfo> choices = new ArrayList<>(StreamRanker.bestPerQuality(session.streams));
		if (!choices.contains(session.stream)) {
			choices.add(0, session.stream);
		}
		Menu menu = new Menu();
		int count = 0;
		for (StreamInfo s : choices) {
			if (!StreamRanker.isPlayable(s)) {
				continue;
			}
			String quality = Fmt.quality(s.quality);
			String label = s.sizeBytes > 0 ? I18n.t("player_stream_label", quality, Fmt.size(s.sizeBytes)) : quality;
			boolean current = s.key().equals(session.stream.key());
			menu.choice(label, current, () -> {
				if (!current) {
					playback.pick(s);
				}
			});
			count++;
		}
		if (count <= 1) {
			Toast.show(I18n.t("player_no_other_streams"));
			return;
		}
		showMenu(anchor, menu);
	}

	private final class Frames implements VideoPlayer.FrameSink {
		private final Object lock = new Object();
		private final AtomicBoolean posted = new AtomicBoolean();
		@Nullable private MemorySegment staging;
		private int width;
		private int height;
		private boolean fresh;
		private boolean shownOne;

		@Override
		public void frame(int frameWidth, int frameHeight, @NonNull MemorySegment bgra) {
			long bytes = (long) frameWidth * frameHeight * 4;
			if (frameWidth <= 0 || frameHeight <= 0 || bgra.byteSize() < bytes) {
				return;
			}
			synchronized (lock) {
				if (staging == null || staging.byteSize() != bytes) {
					staging = Arena.ofAuto().allocate(bytes, 8);
				}
				staging.copyFrom(bgra.asSlice(0, bytes));
				width = frameWidth;
				height = frameHeight;
				fresh = true;
			}
			if (posted.compareAndSet(false, true)) {
				MainLoop.post(this::show);
			}
		}

		private void show() {
			posted.set(false);
			Texture texture;
			synchronized (lock) {
				if (!fresh || staging == null || !showing) {
					return;
				}
				fresh = false;
				texture = Texture.ofArgbPre(width, height, staging);
			}
			video.show(texture);
			texture.unref();
			shownOne = true;
		}

		boolean hasFrame() {
			return shownOne;
		}

		void clear() {
			synchronized (lock) {
				fresh = false;
				staging = null;
			}
			if (shownOne) {
				shownOne = false;
				video.show((MemorySegment) null);
			}
		}
	}
}
