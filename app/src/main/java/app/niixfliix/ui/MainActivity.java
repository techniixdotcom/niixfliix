package app.niixfliix.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigationrail.NavigationRailView;

import java.util.Locale;

import app.niixfliix.R;
import app.niixfliix.addon.StremioUrl;
import app.niixfliix.app.AppGraph;
import app.niixfliix.app.Constant;
import app.niixfliix.player.PlayerLauncher;
import app.niixfliix.torrent.Magnet;
import app.niixfliix.torrentio.TorrentioConfig;
import app.niixfliix.ui.discover.DiscoverFragment;
import app.niixfliix.ui.home.HomeFragment;
import app.niixfliix.ui.library.LibraryFragment;
import app.niixfliix.ui.search.SearchFragment;
import app.niixfliix.ui.settings.SettingsFragment;
import app.niixfliix.ui.setup.SetupDialog;
import app.niixfliix.update.UpdatePrompt;

public final class MainActivity extends BaseActivity {

	public static final String EXTRA_OPEN_LIBRARY = "open_library";
	private static final String STATE_TAB = "tab";
	private static boolean refreshedThisProcess;

	private final AppGraph graph = AppGraph.get();
	private NavigationBarView nav;
	@IdRes private int currentTab = R.id.nav_home;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		nav = findViewById(R.id.nav);
		boolean rail = nav instanceof NavigationRailView;
		if (rail) {
			// tablet: list and mini bar share a column next to the rail
			WindowPadding.apply(findViewById(R.id.main_column),
					WindowPadding.TOP | WindowPadding.RIGHT | WindowPadding.BOTTOM);
		} else {
			WindowPadding.apply(findViewById(R.id.fragment_container),
					WindowPadding.TOP | WindowPadding.LEFT | WindowPadding.RIGHT);
			WindowPadding.apply(findViewById(R.id.mini_player), WindowPadding.LEFT | WindowPadding.RIGHT);
		}

		if (savedInstanceState != null) {
			currentTab = savedInstanceState.getInt(STATE_TAB, R.id.nav_home);
		}
		nav.setOnItemSelectedListener(item -> {
			showTab(item.getItemId());
			return true;
		});
		// triggers the listener -> showTab
		nav.setSelectedItemId(currentTab);

		if (!refreshedThisProcess) {
			refreshedThisProcess = true;
			graph.addons.refreshAll(null);
			graph.account.sync();
			if (graph.prefs.updateCheckOnStart()) {
				UpdatePrompt.checkQuietly(this);
			}
		}
		SetupDialog.showIfNeeded(getSupportFragmentManager());
		if (savedInstanceState == null) {
			handleIntent(getIntent());
		}
	}

	@Override
	protected void onNewIntent(@NonNull Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(STATE_TAB, currentTab);
	}

	private void showTab(@IdRes int id) {
		currentTab = id;
		FragmentManager fm = getSupportFragmentManager();
		FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
		String tag = "tab_" + id;
		for (Fragment f : fm.getFragments()) {
			if (f.getTag() != null && f.getTag().startsWith("tab_") && !f.getTag().equals(tag)) {
				tx.hide(f);
			}
		}
		Fragment existing = fm.findFragmentByTag(tag);
		if (existing != null) {
			tx.show(existing);
		} else {
			tx.add(R.id.fragment_container, newTab(id), tag);
		}
		tx.commit();
	}

	@NonNull
	private static Fragment newTab(@IdRes int id) {
		if (id == R.id.nav_discover) {
			return new DiscoverFragment();
		}
		if (id == R.id.nav_library) {
			return new LibraryFragment();
		}
		if (id == R.id.nav_search) {
			return new SearchFragment();
		}
		if (id == R.id.nav_settings) {
			return new SettingsFragment();
		}
		return new HomeFragment();
	}

	/** untrusted, validated below */
	private void handleIntent(@Nullable Intent intent) {
		if (intent == null) {
			return;
		}
		if (intent.getBooleanExtra(EXTRA_OPEN_LIBRARY, false)) {
			nav.setSelectedItemId(R.id.nav_library);
			return;
		}
		if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
			return;
		}
		Uri data = intent.getData();
		String link = data != null ? data.toString() : null;
		if (link == null || link.length() > Constant.MAX_INTENT_TEXT) {
			return;
		}
		String scheme = data.getScheme() != null ? data.getScheme().toLowerCase(Locale.ROOT) : "";
		if (scheme.equals("stremio")) {
			confirmInstall(link);
		} else if (scheme.equals("magnet")) {
			Magnet magnet = Magnet.parse(link);
			if (magnet == null) {
				Toast.makeText(this, R.string.error_bad_magnet, Toast.LENGTH_SHORT).show();
			} else {
				PlayerLauncher.playMagnet(this, magnet);
			}
		}
	}

	private void confirmInstall(String link) {
		String url = StremioUrl.toManifestUrl(link);
		if (url == null) {
			Toast.makeText(this, R.string.error_unsupported_link, Toast.LENGTH_SHORT).show();
			return;
		}
		new MaterialAlertDialogBuilder(this)
				.setTitle(R.string.addon_install_title)
				.setMessage(getString(R.string.addon_install_message, StremioUrl.masked(url)))
				.setPositiveButton(R.string.action_install, (d, w) -> {
					// configure page gives a new link each time, replace instead of adding a 2nd Torrentio
					if (TorrentioConfig.isTorrentio(url) && graph.addons.torrentio() != null) {
						graph.addons.setTorrentioUrl(url);
						Toast.makeText(this, R.string.torrentio_saved, Toast.LENGTH_SHORT).show();
					} else {
						install(url);
					}
				})
				.setNegativeButton(R.string.action_cancel, null)
				.show();
	}

	private void install(String url) {
		graph.addons.add(url, AddonErrors.toastCallback(this));
	}
}
