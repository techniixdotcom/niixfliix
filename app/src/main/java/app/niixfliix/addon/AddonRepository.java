package app.niixfliix.addon;

import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.data.Prefs;
import app.niixfliix.data.SecretStore;
import app.niixfliix.torrentio.TorrentioConfig;
import app.niixfliix.util.Logs;
import app.niixfliix.util.SizeCappedReader;

/** Installed add-ons in user order. Stored encrypted since a configured URL can contain a debrid key. */
public final class AddonRepository {

	public interface Listener {
		@MainThread
		void onAddonsChanged();
	}

	public interface AddCallback {
		@MainThread
		void onAdded(@NonNull InstalledAddon addon);

		@MainThread
		void onFailed(@NonNull AddError error);
	}

	public enum AddError {
		INVALID_URL,
		ALREADY_INSTALLED,
		UNREACHABLE,
		INVALID_MANIFEST,
		STORAGE
	}

	private static final String TAG = "AddonRepository";
	private static final String KEY = "installed_addons";
	private static final String KEY_DEFAULTS_SEEN = "default_addons_seen";
	/** what versions before the seen-list shipped with */
	private static final List<String> FIRST_DEFAULTS = Arrays.asList(
			DefaultAddons.CINEMETA, DefaultAddons.TORRENTIO, DefaultAddons.OPENSUBTITLES);

	private final AddonClient client;
	private final SecretStore secrets;
	private final Prefs prefs;
	private final Gson gson;
	private final ExecutorService io;
	private final Handler main;
	private final List<InstalledAddon> addons = new CopyOnWriteArrayList<>();
	private final Set<String> down = Collections.synchronizedSet(new HashSet<>());
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	public AddonRepository(@NonNull AddonClient client, @NonNull SecretStore secrets, @NonNull Prefs prefs,
			@NonNull Gson gson, @NonNull ExecutorService io, @NonNull Handler main) {
		this.client = client;
		this.secrets = secrets;
		this.prefs = prefs;
		this.gson = gson;
		this.io = io;
		this.main = main;
		load();
	}

	public void addListener(@NonNull Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(@NonNull Listener listener) {
		listeners.remove(listener);
	}

	@NonNull
	public List<InstalledAddon> all() {
		return new ArrayList<>(addons);
	}

	/** Minus adult add-ons when those are hidden. */
	@NonNull
	public List<InstalledAddon> active() {
		boolean hideAdult = prefs.hideAdult();
		List<InstalledAddon> out = new ArrayList<>();
		for (InstalledAddon a : addons) {
			if (!(hideAdult && a.manifest != null && a.manifest.isAdult())) {
				out.add(a);
			}
		}
		return out;
	}

	@NonNull
	public List<InstalledAddon> supporting(@NonNull String resource, @NonNull String type, @NonNull String id) {
		List<InstalledAddon> out = new ArrayList<>();
		for (InstalledAddon a : active()) {
			if (a.supports(resource, type, id)) {
				out.add(a);
			}
		}
		return out;
	}

	@NonNull
	public List<InstalledAddon> unreachable() {
		List<InstalledAddon> out = new ArrayList<>();
		for (InstalledAddon a : addons) {
			if (down.contains(a.transportUrl)) {
				out.add(a);
			}
		}
		return out;
	}

	public boolean isUnreachable(@NonNull InstalledAddon addon) {
		return down.contains(addon.transportUrl);
	}

	@Nullable
	public InstalledAddon torrentio() {
		for (InstalledAddon a : addons) {
			if (TorrentioConfig.isTorrentio(a.transportUrl)) {
				return a;
			}
		}
		return null;
	}

	/** Failed add-ons keep their last good manifest. */
	public void refreshAll(@Nullable Runnable done) {
		List<InstalledAddon> snapshot = all();
		if (snapshot.isEmpty()) {
			if (done != null) {
				main.post(done);
			}
			return;
		}
		AtomicInteger left = new AtomicInteger(snapshot.size());
		for (InstalledAddon addon : snapshot) {
			io.execute(() -> {
				fetchManifest(addon);
				if (left.decrementAndGet() == 0) {
					save();
					main.post(() -> {
						notifyChanged();
						if (done != null) {
							done.run();
						}
					});
				}
			});
		}
	}

	public void add(@NonNull String input, @NonNull AddCallback callback) {
		String url = StremioUrl.toManifestUrl(input);
		if (url == null) {
			callback.onFailed(AddError.INVALID_URL);
			return;
		}
		if (indexOf(url) >= 0) {
			callback.onFailed(AddError.ALREADY_INSTALLED);
			return;
		}
		io.execute(() -> {
			InstalledAddon addon = new InstalledAddon(url);
			AddError error = null;
			try {
				addon.manifest = client.manifest(url);
			} catch (ManifestValidator.InvalidManifestException | AddonJson.BadJsonException
					| SizeCappedReader.TooLargeException e) {
				error = AddError.INVALID_MANIFEST;
			} catch (IOException e) {
				Logs.w(TAG, "Could not fetch " + url, e);
				error = AddError.UNREACHABLE;
			}
			AddError result = error;
			main.post(() -> {
				if (result != null) {
					callback.onFailed(result);
					return;
				}
				if (indexOf(url) >= 0) {
					callback.onFailed(AddError.ALREADY_INSTALLED);
					return;
				}
				addons.add(addon);
				if (!save()) {
					addons.remove(addon);
					callback.onFailed(AddError.STORAGE);
					return;
				}
				notifyChanged();
				callback.onAdded(addon);
			});
		});
	}

	/**
	 * Replaces the Torrentio entry in place (or re-adds it). The old manifest is kept until the
	 * new one arrives, it's the same with or without config anyway.
	 */
	@MainThread
	public boolean setTorrentioUrl(@NonNull String manifestUrl) {
		String url = StremioUrl.toManifestUrl(manifestUrl);
		if (url == null || !TorrentioConfig.isTorrentio(url)) {
			return false;
		}
		InstalledAddon old = torrentio();
		InstalledAddon updated = new InstalledAddon(url);
		int index = old != null ? addons.indexOf(old) : -1;
		if (old != null) {
			updated.manifest = old.manifest;
			addons.set(index, updated);
		} else {
			addons.add(updated);
		}
		if (!save()) {
			if (old != null) {
				addons.set(index, old);
			} else {
				addons.remove(updated);
			}
			return false;
		}
		if (old != null) {
			down.remove(old.transportUrl);
		}
		notifyChanged();
		refreshOne(updated);
		return true;
	}

	@MainThread
	public void filtersChanged() {
		notifyChanged();
	}

	@MainThread
	public void remove(@NonNull InstalledAddon addon) {
		addons.remove(addon);
		down.remove(addon.transportUrl);
		save();
		notifyChanged();
	}

	@MainThread
	public void move(int from, int to) {
		if (from < 0 || to < 0 || from >= addons.size() || to >= addons.size() || from == to) {
			return;
		}
		InstalledAddon moved = addons.remove(from);
		addons.add(to, moved);
		save();
		notifyChanged();
	}

	@MainThread
	public void resetToDefaults() {
		addons.clear();
		down.clear();
		for (String url : DefaultAddons.urls()) {
			addons.add(new InstalledAddon(url));
		}
		save();
		notifyChanged();
		refreshAll(null);
	}

	private void refreshOne(InstalledAddon addon) {
		io.execute(() -> {
			fetchManifest(addon);
			save();
			main.post(this::notifyChanged);
		});
	}

	private void fetchManifest(InstalledAddon addon) {
		try {
			addon.manifest = client.manifest(addon.transportUrl);
			down.remove(addon.transportUrl);
		} catch (IOException e) {
			Logs.w(TAG, "Manifest fetch failed for " + addon.transportUrl, e);
			down.add(addon.transportUrl);
		}
	}

	private int indexOf(String url) {
		for (int i = 0; i < addons.size(); i++) {
			if (addons.get(i).transportUrl.equals(url)) {
				return i;
			}
		}
		return -1;
	}

	private void load() {
		String json = secrets.get(KEY);
		boolean readable = false;
		if (json != null) {
			try {
				List<InstalledAddon> saved = gson.fromJson(json, new TypeToken<List<InstalledAddon>>() { }.getType());
				readable = saved != null;
				if (readable) {
					for (InstalledAddon a : saved) {
						if (a != null && StremioUrl.toManifestUrl(a.transportUrl) != null) {
							addons.add(a);
						}
					}
				}
			} catch (JsonParseException e) {
				Logs.w(TAG, "Installed add-ons were unreadable, starting from the defaults", e);
			}
		}
		// "[]" means the user removed everything on purpose
		if (!readable) {
			addons.clear();
			for (String url : DefaultAddons.urls()) {
				addons.add(new InstalledAddon(url));
			}
		} else if (addNewDefaults()) {
			save();
		}
		secrets.put(KEY_DEFAULTS_SEEN, String.join("\n", DefaultAddons.urls()));
	}

	/** A default that came with an update is added once. If the user removes it later it stays removed. */
	private boolean addNewDefaults() {
		String seen = secrets.get(KEY_DEFAULTS_SEEN);
		List<String> known = seen != null ? Arrays.asList(seen.split("\n")) : FIRST_DEFAULTS;
		int at = -1;
		for (String url : DefaultAddons.urls()) {
			if (known.contains(url) || indexOf(url) >= 0) {
				continue;
			}
			if (at < 0) {
				InstalledAddon torrentio = torrentio();
				at = torrentio != null ? addons.indexOf(torrentio) + 1 : addons.size();
			}
			// at++ keeps several new ones in their DefaultAddons order
			addons.add(at++, new InstalledAddon(url));
		}
		return at >= 0;
	}

	private synchronized boolean save() {
		return secrets.put(KEY, gson.toJson(new ArrayList<>(addons)));
	}

	private void notifyChanged() {
		for (Listener l : listeners) {
			l.onAddonsChanged();
		}
	}
}
