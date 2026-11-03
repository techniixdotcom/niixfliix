package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.data.KeyValueStore;
import app.niixfliix.data.Prefs;
import app.niixfliix.data.SecretStore;
import app.niixfliix.util.MainQueue;
import okhttp3.OkHttpClient;

public class DefaultAddonsUpgradeTest {

	private static final String CUSTOM = "https://example.org/some:config/manifest.json";
	private static final List<String> OLD_DEFAULTS = Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO,
			DefaultAddons.HYPER, DefaultAddons.OPENSUBTITLES);

	private static final class Store implements KeyValueStore {
		final Map<String, Object> values = new HashMap<>();

		@Override
		public String getString(String key, String def) {
			return values.containsKey(key) ? (String) values.get(key) : def;
		}

		@Override
		public void putString(String key, String value) {
			if (value == null) {
				values.remove(key);
			} else {
				values.put(key, value);
			}
		}

		@Override
		public boolean getBoolean(String key, boolean def) {
			return values.containsKey(key) ? (Boolean) values.get(key) : def;
		}

		@Override
		public void putBoolean(String key, boolean value) {
			values.put(key, value);
		}

		@Override
		public int getInt(String key, int def) {
			return values.containsKey(key) ? (Integer) values.get(key) : def;
		}

		@Override
		public void putInt(String key, int value) {
			values.put(key, value);
		}

		@Override
		public void remove(String key) {
			values.remove(key);
		}
	}

	private static final SecretKey KEY = new SecretKeySpec(new byte[32], "AES");
	private static final MainQueue MAIN = new MainQueue() {
		@Override
		public void post(Runnable task) {
			task.run();
		}

		@Override
		public void postDelayed(Runnable task, long delayMs) {
			task.run();
		}

		@Override
		public void removeCallbacks(Runnable task) {
		}
	};

	private final Gson gson = new Gson();
	private final Store settings = new Store();
	private final Store secretValues = new Store();
	private SecretStore secrets;
	private ExecutorService io;

	@Before
	public void setUp() {
		secrets = new SecretStore(secretValues, () -> KEY);
		io = Executors.newSingleThreadExecutor();
	}

	@After
	public void tearDown() {
		io.shutdownNow();
	}

	private List<String> start() {
		AddonRepository repository = new AddonRepository(new AddonClient(new OkHttpClient(), gson), secrets,
				new Prefs(settings), gson, io, MAIN);
		List<String> urls = new ArrayList<>();
		for (InstalledAddon a : repository.all()) {
			urls.add(a.transportUrl);
		}
		return urls;
	}

	private void savedByOldVersion(String... installed) {
		List<InstalledAddon> list = new ArrayList<>();
		for (String url : installed) {
			list.add(new InstalledAddon(url));
		}
		secrets.put("installed_addons", gson.toJson(list));
		secrets.put("default_addons_seen", String.join("\n", OLD_DEFAULTS));
	}

	@Test
	public void freshInstallHasThePirateBay() {
		assertEquals(Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, DefaultAddons.THEPIRATEBAY,
				DefaultAddons.OPENSUBTITLES, DefaultAddons.FREE_SPORTS), start());
	}

	@Test
	public void updateFrom171PutsThePirateBayBackInHypersPlace() {
		savedByOldVersion(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, CUSTOM, DefaultAddons.HYPER,
				DefaultAddons.OPENSUBTITLES);
		List<String> expected = Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, CUSTOM,
				DefaultAddons.THEPIRATEBAY, DefaultAddons.OPENSUBTITLES, DefaultAddons.FREE_SPORTS);
		assertEquals(expected, start());
		assertEquals(expected, start());
	}

	@Test
	public void hyperAddedAgainLaterStays() {
		savedByOldVersion(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, DefaultAddons.HYPER);
		start();
		List<InstalledAddon> list = new ArrayList<>();
		for (String url : Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.HYPER)) {
			list.add(new InstalledAddon(url));
		}
		secrets.put("installed_addons", gson.toJson(list));
		assertEquals(Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.HYPER), start());
	}

	@Test
	public void thePirateBayComesAfterTorrentioWhenHyperWasRemovedBefore() {
		savedByOldVersion(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, DefaultAddons.OPENSUBTITLES);
		assertEquals(Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, DefaultAddons.THEPIRATEBAY,
				DefaultAddons.OPENSUBTITLES, DefaultAddons.FREE_SPORTS), start());
	}

	@Test
	public void thePirateBayInstalledAlreadyIsNotAddedTwice() {
		savedByOldVersion(DefaultAddons.THEPIRATEBAY, DefaultAddons.CINEMETA, DefaultAddons.HYPER);
		assertEquals(Arrays.asList(DefaultAddons.THEPIRATEBAY, DefaultAddons.CINEMETA, DefaultAddons.FREE_SPORTS),
				start());
	}

	@Test
	public void olderVersionsKeepTheirList() {
		List<InstalledAddon> list = new ArrayList<>();
		for (String url : Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, DefaultAddons.THEPIRATEBAY)) {
			list.add(new InstalledAddon(url));
		}
		secrets.put("installed_addons", gson.toJson(list));
		secrets.put("default_addons_seen", String.join("\n", DefaultAddons.urls()));
		assertEquals(Arrays.asList(DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, DefaultAddons.THEPIRATEBAY),
				start());
	}
}
