package app.niixfliix.app;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

import app.niixfliix.account.StremioAccount;
import app.niixfliix.addon.AddonClient;
import app.niixfliix.addon.AddonJson;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.MetaFinder;
import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.StreamCache;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.Translations;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.data.History;
import app.niixfliix.data.KeyValueStore;
import app.niixfliix.data.Library;
import app.niixfliix.data.NewEpisodes;
import app.niixfliix.data.OldLiveSites;
import app.niixfliix.data.PlayQueue;
import app.niixfliix.data.Prefs;
import app.niixfliix.data.SearchHistory;
import app.niixfliix.data.SecretStore;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.live.LiveCheck;
import app.niixfliix.net.Http;
import app.niixfliix.player.PlayerPreferences;
import app.niixfliix.torrent.Magnet;
import app.niixfliix.torrent.TorrentEngine;
import app.niixfliix.ui.common.PosterItem;
import app.niixfliix.util.MainQueue;
import okhttp3.OkHttpClient;

public class Core {

	private static Core instance;

	public final MainQueue main;
	public final ExecutorService io = Executors.newFixedThreadPool(6);
	public final Gson gson = AddonJson.create();
	public final OkHttpClient http = Http.create();
	public final Prefs prefs;
	public final PlayerPreferences playerPrefs;
	public final SecretStore secrets;
	public final AddonClient addonClient;
	public final AddonRepository addons;
	public final History history;
	public final NewEpisodes newEpisodes;
	public final Library library;
	public final PlayQueue queue;
	public final StremioAccount account;
	public final SearchHistory searchHistory;
	public final Translations translations;
	public final StreamCache streams;
	public final LiveCheck liveCheck;
	protected final boolean oldLiveSitesRemoved;

	private final File torrentCacheDir;
	private TorrentEngine torrentEngine;
	@Nullable private StartAhead startAhead;
	private boolean warmedContinueWatching;
	private final Set<String> datesLookedUp = new HashSet<>();

	protected Core(@NonNull MainQueue main, @NonNull Function<String, KeyValueStore> stores,
			@NonNull SecretStore.KeySource secretKeys, @NonNull Supplier<Locale> appLocale,
			@NonNull File torrentCacheDir) {
		this.main = main;
		this.torrentCacheDir = torrentCacheDir;
		KeyValueStore settings = stores.apply("settings");
		KeyValueStore data = stores.apply("data");
		prefs = new Prefs(settings);
		playerPrefs = new PlayerPreferences(settings);
		secrets = new SecretStore(stores.apply("secrets"), secretKeys);
		addonClient = new AddonClient(http, gson);
		addonClient.setDiskCache(new File(torrentCacheDir.getParentFile(), "catalogs"), io);
		addons = new AddonRepository(addonClient, secrets, prefs, gson, io, main);
		history = new History(data, gson);
		newEpisodes = new NewEpisodes(data, gson, io, main, history);
		library = new Library(data, gson);
		queue = new PlayQueue(data, gson);
		account = new StremioAccount(http, secrets, io, main, addons, library);
		library.setChanges(account);
		searchHistory = new SearchHistory(data, gson);
		translations = new Translations(addonClient, http, data, gson, main, appLocale);
		liveCheck = new LiveCheck(addons, addonClient, http, main);
		streams = new StreamCache(addons, addonClient, io, main);
		oldLiveSitesRemoved = OldLiveSites.removeOnce(settings, secrets, gson);
		instance = this;
	}

	@NonNull
	public static Core get() {
		return instance;
	}

	@NonNull
	public final File torrentCacheDir() {
		return torrentCacheDir;
	}

	@NonNull
	public final synchronized TorrentEngine torrentEngine() {
		if (torrentEngine == null) {
			torrentEngine = new TorrentEngine(torrentCacheDir, main, torrentLimits());
		}
		return torrentEngine;
	}

	@MainThread
	public final void prepareToPlay(@NonNull String type, @Nullable String videoId, @Nullable String seriesId) {
		if (startAhead != null) {
			startAhead.cancel();
			startAhead = null;
		}
		if (videoId != null) {
			streams.prefetch(type, videoId);
		}
		if (Catalog.isLiveType(type)) {
			return;
		}
		boolean unmetered = onUnmeteredNetwork();
		io.execute(() -> {
			TorrentEngine engine = torrentEngine();
			main.post(() -> {
				engine.warmUp();
				// Pre-load on every network, mobile data and roaming included, at the quality Play will pick
				// on this network, so the stream is already connected by the time Play is tapped.
				if (videoId != null && startAhead == null) {
					startAhead = StartAhead.start(streams, main, engine, playerPrefs.quality(seriesId, unmetered), type,
							videoId);
				}
			});
		});
	}

	protected boolean onUnmeteredNetwork() {
		return true;
	}

	/**
	 * When Home first shows, gets the top "Continue watching" title ready to play, the same way opening its
	 * page does: its streams are looked up and the likely torrent is connected and its start fetched, so
	 * tapping it starts straight away. Once per launch; what's opened after that takes over.
	 */
	@MainThread
	public final void warmUpContinueWatching() {
		if (warmedContinueWatching) {
			return;
		}
		warmedContinueWatching = true;
		List<HistoryEntry> entries = history.continueWatching();
		if (entries.isEmpty()) {
			return;
		}
		HistoryEntry top = entries.get(0);
		if (top.videoId.isEmpty() || top.type.isEmpty()) {
			return;
		}
		prepareToPlay(top.type, top.videoId, Meta.TYPE_SERIES.equals(top.type) ? top.metaId : null);
	}

	@NonNull
	public static Magnet magnetFor(@NonNull StreamInfo s) {
		return new Magnet(s.stream.infoHash, s.fileName != null ? s.fileName : s.releaseName,
				Magnet.trackersFromSources(s.stream.sources));
	}

	@MainThread
	public final void fillLibraryDates(@NonNull Runnable onChanged) {
		List<LibraryItem> missing = new ArrayList<>();
		for (LibraryItem item : library.all()) {
			if (item.released == 0 && datesLookedUp.add(item.id)) {
				missing.add(item);
			}
		}
		if (missing.isEmpty()) {
			return;
		}
		io.execute(() -> {
			boolean changed = false;
			long now = System.currentTimeMillis();
			for (LibraryItem item : missing) {
				Meta meta = MetaFinder.find(addons, addonClient, item.type, item.id);
				if (meta == null) {
					continue;
				}
				long released = meta.isSeries() ? ReleaseOrder.latestOut(meta, now) : ReleaseOrder.of(meta);
				if (released != 0 && library.setReleased(item.id, released)) {
					changed = true;
				}
			}
			if (changed) {
				main.post(onChanged);
			}
		});
	}

	/**
	 * Keeps the episode lists of the series the "NEW" mark is for up to date: saved series and series
	 * that were watched. {@code onChanged} runs on the main thread when a list changed.
	 */
	@MainThread
	public final void refreshNewEpisodes(@NonNull Runnable onChanged) {
		Set<String> ids = new LinkedHashSet<>();
		for (LibraryItem item : library.all()) {
			if (Meta.TYPE_SERIES.equals(item.type)) {
				ids.add(item.id);
			}
		}
		for (HistoryEntry entry : history.recent()) {
			if (Meta.TYPE_SERIES.equals(entry.type) && !entry.metaId.isEmpty()) {
				ids.add(entry.metaId);
			}
		}
		newEpisodes.refresh(ids, id -> MetaFinder.find(addons, addonClient, Meta.TYPE_SERIES, id), onChanged);
	}

	/** The poster with the "NEW" mark when every episode out was watched and a new one came out since. */
	@MainThread
	@NonNull
	public final PosterItem markNew(@NonNull PosterItem item) {
		return Meta.TYPE_SERIES.equals(item.type) && !item.header ? item.withNew(newEpisodes.hasNew(item.id)) : item;
	}

	@NonNull
	protected TorrentEngine.Limits torrentLimits() {
		return TorrentEngine.Limits.COMPUTER;
	}

	public final void housekeepTorrents(boolean clearAll) {
		if (torrentCacheDir.isDirectory()) {
			torrentEngine().housekeep(clearAll);
		}
	}
}
