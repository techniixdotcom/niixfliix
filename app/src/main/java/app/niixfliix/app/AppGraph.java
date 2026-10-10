package app.niixfliix.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.niixfliix.account.StremioAccount;
import app.niixfliix.addon.AddonClient;
import app.niixfliix.addon.AddonJson;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.Translations;
import app.niixfliix.data.History;
import app.niixfliix.data.KeyValueStore;
import app.niixfliix.data.Library;
import app.niixfliix.data.MmkvStore;
import app.niixfliix.data.PlayQueue;
import app.niixfliix.data.Prefs;
import app.niixfliix.data.SearchHistory;
import app.niixfliix.data.SecretStore;
import app.niixfliix.download.Downloads;
import app.niixfliix.net.Http;
import app.niixfliix.player.PlaybackController;
import app.niixfliix.player.PlayerPreferences;
import app.niixfliix.torrent.TorrentEngine;
import app.niixfliix.update.UpdateManager;
import okhttp3.OkHttpClient;

public final class AppGraph {

	private static AppGraph instance;

	public final Context context;
	public final Handler main = new Handler(Looper.getMainLooper());
	public final ExecutorService io = Executors.newFixedThreadPool(6);
	public final Gson gson = AddonJson.create();
	public final OkHttpClient http = Http.create();
	public final Prefs prefs;
	public final PlayerPreferences playerPrefs;
	public final SecretStore secrets;
	public final AddonClient addonClient;
	public final AddonRepository addons;
	public final History history;
	public final Library library;
	public final PlayQueue queue;
	public final StremioAccount account;
	public final SearchHistory searchHistory;
	public final Translations translations;

	private TorrentEngine torrentEngine;
	private UpdateManager updateManager;
	private Downloads downloads;
	private PlaybackController playback;

	private AppGraph(@NonNull Context context) {
		this.context = context;
		KeyValueStore settings = new MmkvStore("settings");
		KeyValueStore data = new MmkvStore("data");
		prefs = new Prefs(settings);
		playerPrefs = new PlayerPreferences(settings);
		secrets = new SecretStore(new MmkvStore("secrets"));
		addonClient = new AddonClient(http, gson);
		addons = new AddonRepository(addonClient, secrets, prefs, gson, io, main);
		history = new History(data, gson);
		library = new Library(data, gson);
		queue = new PlayQueue(data, gson);
		account = new StremioAccount(http, secrets, io, main, addons, library);
		library.setChanges(account);
		searchHistory = new SearchHistory(data, gson);
		translations = new Translations(addonClient, http, data, gson, main);

		Picasso picasso = new Picasso.Builder(context)
				.downloader(new OkHttp3Downloader(Http.forImages(http, context.getCacheDir())))
				.build();
		Picasso.setSingletonInstance(picasso);
	}

	static void init(@NonNull Context context) {
		instance = new AppGraph(context);
	}

	@NonNull
	public static AppGraph get() {
		return instance;
	}

	/** what's playing, app-wide; the player screen and the mini bars show it */
	@NonNull
	@MainThread
	public PlaybackController playback() {
		if (playback == null) {
			playback = new PlaybackController(context);
		}
		return playback;
	}

	@NonNull
	public synchronized TorrentEngine torrentEngine() {
		if (torrentEngine == null) {
			torrentEngine = new TorrentEngine(torrentCacheDir(context), main);
		}
		return torrentEngine;
	}

	@NonNull
	public synchronized UpdateManager updates() {
		if (updateManager == null) {
			updateManager = new UpdateManager(context, http, io, main);
		}
		return updateManager;
	}

	@NonNull
	public synchronized Downloads downloads() {
		if (downloads == null) {
			downloads = new Downloads(context, new MmkvStore("downloads"), secrets, gson);
		}
		return downloads;
	}

	@NonNull
	public static File torrentCacheDir(@NonNull Context context) {
		return new File(context.getCacheDir(), "torrents");
	}
}
