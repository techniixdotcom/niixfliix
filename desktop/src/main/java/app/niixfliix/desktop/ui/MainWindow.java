package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import app.niixfliix.app.AppInfo;
import app.niixfliix.desktop.gtk.App;
import app.niixfliix.desktop.gtk.Bars;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Breakpoint;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Controller;
import app.niixfliix.desktop.gtk.Icons;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Layers;
import app.niixfliix.desktop.gtk.Toasts;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.gtk.Window;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.Paths;
import app.niixfliix.desktop.ui.screens.DetailsPage;
import app.niixfliix.desktop.ui.screens.DiscoverPage;
import app.niixfliix.desktop.ui.screens.HomePage;
import app.niixfliix.desktop.ui.screens.LibraryPage;
import app.niixfliix.desktop.ui.screens.LivePage;
import app.niixfliix.desktop.ui.screens.PlayerPage;
import app.niixfliix.desktop.ui.screens.SearchPage;
import app.niixfliix.desktop.ui.screens.SettingsPage;
import app.niixfliix.ui.common.PosterItem;

public final class MainWindow {

	public enum Tab {
		HOME("nav_home", "home"),
		DISCOVER("nav_discover", "explore"),
		LIVE("nav_live", "live"),
		SEARCH("nav_search", "search"),
		LIBRARY("nav_library", "library"),
		SETTINGS("nav_settings", "settings");

		final String label;
		final String icon;

		Tab(String label, String icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private static final int MIN_WIDTH = 900;
	private static final int MIN_HEIGHT = 600;
	private static final int DEFAULT_WIDTH = 1280;
	private static final int DEFAULT_HEIGHT = 800;
	private static final String NARROW = "max-width: 1100sp";
	private static final int KEY_ESCAPE = 0xff1b;
	private static final int KEY_F = 0x066;
	private static MainWindow instance;

	private final Window window;
	private final Layers pages = Layers.stack(0, false);
	private final Map<Tab, Page> tabs = new EnumMap<>(Tab.class);
	private final Map<Tab, Button> railButtons = new EnumMap<>(Tab.class);
	private final Box rail = Box.column(6);
	private final Label wordmark = Label.of(null, "wordmark");
	private final Deque<Page> stack = new ArrayDeque<>();
	private final Layers layers = Layers.stack(150, true);
	private final Box shell;
	private final Toasts toasts;
	private final MiniBar miniBar;
	@Nullable private PlayerPage player;
	private Tab tab = Tab.HOME;

	private MainWindow(@NonNull App app) {
		window = Window.create(app, AppInfo.APP_NAME, Paths.APP_ID);
		window.size(MIN_WIDTH, MIN_HEIGHT);
		window.defaultSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
		miniBar = new MiniBar(this::showPlayer);
		rail.addClass("rail");
		wordmark.accessibleLabel(AppInfo.APP_NAME);
		rail.add(wordmark);
		Button first = null;
		for (Tab t : Tab.values()) {
			Button button = Button.toggleStacked(Icons.name(t.icon), I18n.t(t.label), "flat", "rail-button");
			if (first == null) {
				first = button;
			} else {
				button.group(first);
			}
			button.onToggled(() -> {
				if (button.isActive() && (tab != t || !stack.isEmpty())) {
					showTab(t);
				}
			});
			button.onClick(() -> {
				if (tab == t && !stack.isEmpty()) {
					showTab(t);
				}
			});
			if (t == Tab.SETTINGS) {
				rail.add(Box.column(0).vexpand(true));
			}
			rail.add(button);
			railButtons.put(t, button);
		}
		setRailCompact(false);
		Breakpoint.add(window, NARROW, () -> setRailCompact(true), () -> setRailCompact(false));
		pages.vexpand(true).hexpand(true);
		Box main = Box.column(0, pages, miniBar.view());
		main.hexpand(true);
		Bars.Header titleBar = Bars.header(null, true);
		titleBar.addClass("flat", "window-bar");
		Box body = Box.row(0, rail, main);
		body.vexpand(true);
		shell = Box.column(0, titleBar, body);
		layers.add(shell);
		layers.show(shell);
		toasts = Toasts.around(layers);
		Toast.setHost(toasts::show);
		window.content(toasts);
		installKeys();
	}

	@NonNull
	public static MainWindow create(@NonNull App app) {
		instance = new MainWindow(app);
		instance.showTab(Tab.HOME);
		return instance;
	}

	@NonNull
	public static MainWindow get() {
		return instance;
	}

	@NonNull
	public Window window() {
		return window;
	}

	@NonNull
	public Widget root() {
		return toasts;
	}

	private void setRailCompact(boolean compact) {
		rail.toggleClass("expanded", !compact);
		String name = AppInfo.APP_NAME.toUpperCase(Locale.ROOT);
		wordmark.text(compact ? name.substring(0, Math.min(1, name.length())) : name);
		for (Button button : railButtons.values()) {
			button.stacked(compact);
		}
	}

	private void installKeys() {
		Controller keys = Controller.keys();
		keys.onKey((keyval, keycode, state) -> {
			if (isPlayerShowing() || Dialogs.isOpen()) {
				return false;
			}
			if (keyval == KEY_ESCAPE && (state & Controller.MODIFIER_CONTROL) == 0) {
				return back();
			}
			if (keyval == KEY_F && (state & Controller.MODIFIER_CONTROL) != 0) {
				showTab(Tab.SEARCH);
				return true;
			}
			return false;
		});
		window.addController(keys);
		Controller backButton = Controller.click(Controller.BUTTON_BACK);
		backButton.phase(Controller.PHASE_CAPTURE);
		backButton.onPressed((count, x, y) -> {
			if (!isPlayerShowing() && !Dialogs.isOpen()) {
				back();
			}
		});
		window.addController(backButton);
	}

	public void showTab(@NonNull Tab target) {
		Page old = current();
		Deque<Page> closed = new ArrayDeque<>(stack);
		stack.clear();
		tab = target;
		Button button = railButtons.get(target);
		if (!button.isActive()) {
			button.active(true);
		}
		Page page = tabs.computeIfAbsent(target, this::makeTab);
		swap(old, page);
		for (Page p : closed) {
			pages.remove(p.view());
		}
	}

	public void search(@NonNull String query) {
		showTab(Tab.SEARCH);
		Page page = tabs.get(Tab.SEARCH);
		if (page instanceof SearchPage) {
			((SearchPage) page).searchFor(query);
		}
	}

	@NonNull
	public Tab currentTab() {
		return tab;
	}

	public void push(@NonNull Page page) {
		Page old = current();
		stack.push(page);
		swap(old, page);
	}

	public boolean back() {
		if (stack.isEmpty()) {
			return false;
		}
		Page old = stack.pop();
		swap(old, current());
		pages.remove(old.view());
		return true;
	}

	public void openDetails(@NonNull PosterItem item) {
		openDetails(item, false);
	}

	public void openDetails(@NonNull PosterItem item, boolean play) {
		push(new DetailsPage(item.type, item.id, item.name, item.poster, play));
	}

	public void showPlayer() {
		if (player == null) {
			player = new PlayerPage(this);
			layers.add(player.view());
		}
		if (layers.shown() != player.view()) {
			Page under = current();
			if (under != null) {
				under.onHide();
			}
			layers.show(player.view());
			player.onShow();
		}
	}

	public void hidePlayer() {
		if (player == null || layers.shown() != player.view()) {
			return;
		}
		player.onHide();
		layers.show(shell);
		window.fullscreen(false);
		Page under = current();
		if (under != null) {
			under.onShow();
		}
	}

	public boolean isPlayerShowing() {
		return player != null && layers.shown() == player.view();
	}

	@Nullable
	private Page current() {
		return stack.isEmpty() ? tabs.get(tab) : stack.peek();
	}

	private void swap(@Nullable Page old, @Nullable Page next) {
		HoverPreview.dismiss();
		if (old != null && old != next) {
			old.onHide();
		}
		if (next == null) {
			return;
		}
		pages.show(next.view());
		next.onShow();
	}

	private Page makeTab(Tab t) {
		switch (t) {
			case DISCOVER:
				return new DiscoverPage();
			case LIVE:
				return new LivePage();
			case SEARCH:
				return new SearchPage();
			case LIBRARY:
				return new LibraryPage();
			case SETTINGS:
				return new SettingsPage();
			case HOME:
			default:
				return new HomePage();
		}
	}
}
