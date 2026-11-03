package app.niixfliix.addon;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.util.MainQueue;

public final class StreamCache {

	public final class Handle {
		private final Entry entry;
		private final StreamSearch.Listener listener;

		Handle(Entry entry, StreamSearch.Listener listener) {
			this.entry = entry;
			this.listener = listener;
		}

		public int addonCount() {
			return entry.addonCount;
		}

		public void cancel() {
			entry.listeners.remove(listener);
		}
	}

	private static final long KEEP_MS = 5 * 60_000;
	private static final long KEEP_LIVE_MS = 60_000;
	private static final long KEEP_PARTIAL_MS = 30_000;
	private static final int MAX_ENTRIES = 16;

	private final class Entry implements StreamSearch.Listener {
		final String type;
		final List<InstalledAddon> answered = new ArrayList<>();
		final List<List<StreamInfo>> results = new ArrayList<>();
		final List<InstalledAddon> failed = new ArrayList<>();
		final List<StreamSearch.Listener> listeners = new ArrayList<>();
		int addonCount;
		boolean finished;
		long finishedAt;

		Entry(String type) {
			this.type = type;
		}

		boolean isFresh(long now) {
			if (!finished) {
				return true;
			}
			if (answered.isEmpty()) {
				return false;
			}
			long keep = Catalog.isLiveType(type) ? KEEP_LIVE_MS : failed.isEmpty() ? KEEP_MS : KEEP_PARTIAL_MS;
			return now - finishedAt < keep;
		}

		@Override
		public void onResults(@NonNull InstalledAddon addon, @NonNull List<StreamInfo> streams) {
			answered.add(addon);
			results.add(streams);
			for (StreamSearch.Listener l : new ArrayList<>(listeners)) {
				l.onResults(addon, streams);
			}
		}

		@Override
		public void onAddonFailed(@NonNull InstalledAddon addon) {
			failed.add(addon);
			for (StreamSearch.Listener l : new ArrayList<>(listeners)) {
				l.onAddonFailed(addon);
			}
		}

		@Override
		public void onFinished() {
			finished = true;
			finishedAt = System.currentTimeMillis();
			for (StreamSearch.Listener l : new ArrayList<>(listeners)) {
				l.onFinished();
			}
		}
	}

	private final AddonRepository addons;
	private final AddonClient client;
	private final ExecutorService io;
	private final MainQueue main;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public StreamCache(@NonNull AddonRepository addons, @NonNull AddonClient client, @NonNull ExecutorService io,
			@NonNull MainQueue main) {
		this.addons = addons;
		this.client = client;
		this.io = io;
		this.main = main;
		addons.addListener(entries::clear);
	}

	@MainThread
	public void prefetch(@NonNull String type, @NonNull String videoId) {
		entry(type, videoId);
	}

	@MainThread
	@NonNull
	public Handle open(@NonNull String type, @NonNull String videoId, @NonNull StreamSearch.Listener listener) {
		Entry entry = entry(type, videoId);
		Handle handle = new Handle(entry, listener);
		main.post(() -> {
			for (int i = 0; i < entry.answered.size(); i++) {
				listener.onResults(entry.answered.get(i), entry.results.get(i));
			}
			for (InstalledAddon addon : new ArrayList<>(entry.failed)) {
				listener.onAddonFailed(addon);
			}
			if (entry.finished) {
				listener.onFinished();
			} else {
				entry.listeners.add(listener);
			}
		});
		return handle;
	}

	@MainThread
	public void forget(@NonNull String type, @NonNull String videoId) {
		entries.remove(key(type, videoId));
	}

	private Entry entry(String type, String videoId) {
		long now = System.currentTimeMillis();
		String key = key(type, videoId);
		Entry entry = entries.get(key);
		if (entry != null && entry.isFresh(now)) {
			return entry;
		}
		entry = new Entry(type);
		entries.remove(key);
		entries.put(key, entry);
		trim(now);
		entry.addonCount = StreamSearch.start(addons, client, io, main, type, videoId, entry).addonCount();
		return entry;
	}

	private void trim(long now) {
		Iterator<Entry> it = entries.values().iterator();
		while (it.hasNext()) {
			Entry e = it.next();
			if (!e.isFresh(now) || entries.size() > MAX_ENTRIES && e.finished && e.listeners.isEmpty()) {
				it.remove();
			}
		}
	}

	private static String key(String type, String videoId) {
		return type + "|" + videoId;
	}
}
