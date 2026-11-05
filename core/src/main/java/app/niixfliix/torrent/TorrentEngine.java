package app.niixfliix.torrent;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.libtorrent4j.AlertListener;
import org.libtorrent4j.FileStorage;
import org.libtorrent4j.Priority;
import org.libtorrent4j.SessionHandle;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.SessionParams;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.Sha1Hash;
import org.libtorrent4j.TorrentFlags;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.alerts.AddTorrentAlert;
import org.libtorrent4j.alerts.Alert;
import org.libtorrent4j.alerts.AlertType;
import org.libtorrent4j.alerts.MetadataReceivedAlert;
import org.libtorrent4j.alerts.PieceFinishedAlert;
import org.libtorrent4j.alerts.TorrentAlert;
import org.libtorrent4j.alerts.TorrentErrorAlert;
import org.libtorrent4j.swig.settings_pack;
import org.libtorrent4j.swig.torrent_flags_t;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import app.niixfliix.util.Logs;
import app.niixfliix.util.MainQueue;

public final class TorrentEngine {

	public interface Listener {
		@MainThread
		void onStatus(@NonNull Status status);

		@MainThread
		void onReady(@NonNull String url, @NonNull String fileName);

		@MainThread
		void onError(@NonNull Error error, long fileSize);

		/**
		 * The stream given to {@link #race} got its start in first (or the one given to {@link #play}
		 * failed while the rival was still going), so the rival is what plays now. From here on the
		 * status and {@link #onReady} calls are about that torrent.
		 */
		@MainThread
		default void onSwitched(@NonNull String infoHash) {
		}
	}

	public enum Error {
		NO_VIDEO_FILE,
		NO_SPACE,
		NOT_ON_WIFI,
		FAILED
	}

	public enum Phase {
		METADATA,
		BUFFERING,
		PLAYING
	}

	public static final class Status {
		@NonNull public final Phase phase;
		public final int peers;
		public final long bytesPerSecond;
		public final int bufferedPercent;

		Status(@NonNull Phase phase, int peers, long bytesPerSecond, int bufferedPercent) {
			this.phase = phase;
			this.peers = peers;
			this.bytesPerSecond = bytesPerSecond;
			this.bufferedPercent = bufferedPercent;
		}
	}

	private static final String TAG = "TorrentEngine";
	// What must be downloaded before playback starts: the first ~2 MB (at least one piece) and the last
	// piece. The player reads on from there while the rest streams in sequentially.
	private static final long START_BUFFER_BYTES = 2L << 20;
	private static final int AHEAD_SECONDS = 5 * 60;
	private static final int ASSUMED_RUNTIME_SECONDS = 2 * 60 * 60;
	private static final long MIN_AHEAD_BYTES = 16L << 20;
	private static final long MAX_AHEAD_BYTES = 512L << 20;
	private static final int TAIL_PIECES = 2;
	private static final int PREFETCH_DEADLINE_MS = 20_000;
	private static final long STATUS_INTERVAL_MS = 1000;
	/** While connecting and buffering, check 4 times a second so playback starts as soon as it can. */
	private static final long STARTING_INTERVAL_MS = 250;
	/** Extra DHT routers, so a fresh session finds peers sooner than with libtorrent's single default. */
	private static final String DHT_BOOTSTRAP = "dht.libtorrent.org:25401,router.bittorrent.com:6881,"
			+ "router.utorrent.com:6881,dht.transmissionbt.com:6881,dht.aelitis.com:6881";
	private static final long LOW_SPACE_BYTES = 1L << 30;
	private static final long KEEP_FREE_BYTES = 256L << 20;
	private static final String[] VIDEO_EXTENSIONS = {".mkv", ".mp4", ".m4v", ".webm", ".avi", ".mov", ".ts", ".wmv"};

	private static final long WARM_FOR_MS = 3 * 60_000;
	private static final int MAX_STATE_BYTES = 1 << 20;

	private final File cacheRoot;
	private final File stateFile;
	private final MainQueue main;
	private final Runnable coolDown = this::coolDown;
	private final Runnable dropPreload = this::dropUnplayedPreload;
	private final ExecutorService worker = Executors.newSingleThreadExecutor();
	private final Object pieceLock = new Object();

	private SessionManager session;
	@Nullable private volatile Playback current;
	@Nullable private volatile Playback prefetch;
	/** A second stream started next to {@link #current}: whichever has its start in first plays. */
	@Nullable private volatile Playback rival;

	private final class Playback {
		final String infoHash;
		final Integer wantedFile;
		volatile Listener listener;
		volatile int deadlineOffset;
		final Runnable tick = this::tick;
		volatile TorrentHandle handle;
		volatile int fileIndex = -1;
		long fileSize;
		long fileOffset;
		int pieceLength;
		int firstPiece;
		int lastPiece;
		int startPieces;
		int aheadPieces;
		long bytesPerSecond;
		String filePath;
		String fileName;
		LocalStreamServer server;
		volatile boolean stopped;
		volatile int readPiece;
		Phase phase = Phase.METADATA;
		boolean wifiLost;
		volatile boolean preload;
		volatile boolean held;
		@Nullable volatile Status lastStatus;

		Playback(String infoHash, @Nullable Integer wantedFile, Listener listener) {
			this.infoHash = infoHash;
			this.wantedFile = wantedFile;
			this.listener = listener;
		}

		void tick() {
			if (stopped) {
				return;
			}
			main.postDelayed(tick, phase == Phase.PLAYING ? STATUS_INTERVAL_MS : STARTING_INTERVAL_MS);
			TorrentHandle h = handle;
			Status status;
			try {
				if (h == null || !h.isValid()) {
					return;
				}
				TorrentStatus s = h.status();
				int buffered = phase == Phase.METADATA ? 0 : startProgress(h);
				status = new Status(phase, s.numPeers(), s.downloadPayloadRate(), buffered);
			} catch (RuntimeException e) {
				Logs.w(TAG, "Torrent status failed", e);
				return;
			}
			lastStatus = status;
			listener.onStatus(status);
			if (phase == Phase.BUFFERING && status.bufferedPercent >= 100) {
				phase = Phase.PLAYING;
				if (preload) {
					hold(this);
				}
				if (!settleRace(this)) {
					return;
				}
				listener.onReady(server.url(), fileName);
			}
		}

		int startProgress(TorrentHandle h) {
			int have = 0;
			for (int p = firstPiece; p < firstPiece + startPieces; p++) {
				if (h.havePiece(p)) {
					have++;
				}
			}
			boolean tail = h.havePiece(lastPiece);
			int total = startPieces + 1;
			return (have + (tail ? 1 : 0)) * 100 / total;
		}
	}

	public static final class Limits {
		public static final Limits COMPUTER = new Limits(200, 100, 0);
		public static final Limits PHONE = new Limits(120, 60, 1 << 20);

		final int connections;
		final int connectSpeed;
		final int uploadBytesPerSecond;

		public Limits(int connections, int connectSpeed, int uploadBytesPerSecond) {
			this.connections = connections;
			this.connectSpeed = connectSpeed;
			this.uploadBytesPerSecond = uploadBytesPerSecond;
		}
	}

	private final Limits limits;

	private static final Listener IGNORED = new Listener() {
		@Override
		public void onStatus(@NonNull Status status) {
		}

		@Override
		public void onReady(@NonNull String url, @NonNull String fileName) {
		}

		@Override
		public void onError(@NonNull Error error, long fileSize) {
		}
	};

	public TorrentEngine(@NonNull File cacheRoot, @NonNull MainQueue main) {
		this(cacheRoot, main, Limits.COMPUTER);
	}

	public TorrentEngine(@NonNull File cacheRoot, @NonNull MainQueue main, @NonNull Limits limits) {
		this.cacheRoot = cacheRoot;
		File parent = cacheRoot.getAbsoluteFile().getParentFile();
		this.stateFile = new File(parent != null ? parent : cacheRoot, "torrent-session.dat");
		this.main = main;
		this.limits = limits;
	}

	@MainThread
	public void prefetch(@NonNull Magnet magnet, @Nullable Integer fileIdx, @NonNull Listener listener) {
		Playback old = prefetch;
		if (old != null && old.infoHash.equalsIgnoreCase(magnet.infoHash) && Objects.equals(old.wantedFile, fileIdx)) {
			old.listener = listener;
			if (old.preload) {
				old.preload = false;
				main.removeCallbacks(dropPreload);
				run(() -> {
					if (old.held) {
						old.held = false;
						resumeHeld(old);
					}
				});
			}
			return;
		}
		dropPrefetch();
		Playback next = new Playback(magnet.infoHash, fileIdx, listener);
		next.deadlineOffset = PREFETCH_DEADLINE_MS;
		prefetch = next;
		start(next, magnet);
	}

	@MainThread
	public void dropPrefetch() {
		Playback old = prefetch;
		prefetch = null;
		if (old != null) {
			release(old, true);
		}
	}

	@MainThread
	public boolean promote(@NonNull String infoHash, boolean deleteOld, @NonNull Listener listener) {
		Playback next = prefetch;
		if (next == null || !next.infoHash.equalsIgnoreCase(infoHash)) {
			return false;
		}
		prefetch = null;
		stop(deleteOld);
		next.listener = listener;
		next.preload = false;
		current = next;
		main.removeCallbacks(dropPreload);
		worker.execute(() -> {
			if (next.held) {
				next.held = false;
				resumeHeld(next);
			}
			synchronized (next) {
				next.deadlineOffset = 0;
				if (next.stopped || next.fileIndex < 0) {
					return;
				}
				focus(next);
				next.handle.clearPieceDeadlines();
				pushWindow(next, next.readPiece);
				pushTail(next);
			}
		});
		return true;
	}

	@MainThread
	public void play(@NonNull Magnet magnet, @Nullable Integer fileIdx, boolean deleteOld,
			@NonNull Listener listener) {
		Playback fetched = prefetch;
		if (fetched != null && fetched.infoHash.equalsIgnoreCase(magnet.infoHash)
				&& Objects.equals(fetched.wantedFile, fileIdx)) {
			promote(magnet.infoHash, deleteOld, listener);
			if (fetched.phase == Phase.PLAYING) {
				main.post(() -> {
					if (current == fetched && !fetched.stopped) {
						fetched.listener.onReady(fetched.server.url(), fetched.fileName);
					}
				});
			}
			return;
		}
		stop(deleteOld);
		dropPrefetch();
		Playback playback = new Playback(magnet.infoHash, fileIdx, listener);
		current = playback;
		start(playback, magnet);
	}

	/**
	 * Starts a second stream next to the one just given to {@link #play}, at the same priority. Whichever
	 * has its start in first plays and the other is dropped. Streams that look alike on paper often differ a
	 * lot in how fast real peers turn up, so this takes the long waits out of starting. Does nothing when
	 * what's playing is already under way, or is this same torrent.
	 */
	@MainThread
	public void race(@NonNull Magnet magnet, @Nullable Integer fileIdx) {
		Playback playing = current;
		if (playing == null || playing.stopped || playing.phase == Phase.PLAYING
				|| playing.infoHash.equalsIgnoreCase(magnet.infoHash)) {
			return;
		}
		Playback ahead = prefetch;
		if (ahead != null && ahead.infoHash.equalsIgnoreCase(magnet.infoHash)) {
			return;
		}
		dropRival();
		Playback next = new Playback(magnet.infoHash, fileIdx, IGNORED);
		rival = next;
		start(next, magnet);
	}

	/** Whether a stream given to {@link #race} is still going and has peers, past finding the torrent. */
	@MainThread
	public boolean isRivalMoving() {
		Playback r = rival;
		Status status = r != null && !r.stopped ? r.lastStatus : null;
		return status != null && status.peers > 0 && status.phase != Phase.METADATA;
	}

	@MainThread
	private void dropRival() {
		Playback old = rival;
		rival = null;
		if (old != null) {
			release(old, true);
		}
	}

	/**
	 * Called when {@code ready} has its start in. Returns whether it should be announced: the first of the two
	 * to get there plays and the other is dropped.
	 */
	@MainThread
	private boolean settleRace(Playback ready) {
		if (ready == rival) {
			Playback loser = current;
			if (loser == null || loser.stopped || loser.phase == Phase.PLAYING) {
				dropRival();
				return false;
			}
			takeOver(ready, loser);
			return true;
		}
		if (ready == current) {
			dropRival();
		}
		return true;
	}

	@MainThread
	private void takeOver(Playback winner, Playback loser) {
		rival = null;
		current = winner;
		winner.listener = loser.listener;
		release(loser, true);
		winner.listener.onSwitched(winner.infoHash);
	}

	private void start(Playback p, Magnet magnet) {
		main.post(p.tick);
		worker.execute(() -> {
			try {
				ensureSession();
				if (session.isPaused()) {
					session.resume();
				}
				if (isLowOnSpace()) {
					trimCache(0);
				}
				File dir = new File(cacheRoot, p.infoHash);
				if (!dir.isDirectory() && !dir.mkdirs()) {
					throw new IOException("Cannot create " + dir);
				}
				session.download(magnet.withPublicTrackers().toUri(), dir, new torrent_flags_t());
				onAdded(p);
			} catch (IOException | RuntimeException | LinkageError e) {
				Logs.w(TAG, "Could not start torrent", e);
				fail(p, Error.FAILED, 0);
			}
		});
	}

	@MainThread
	public void onWifiLost() {
		Playback p = current;
		if (p != null && !p.wifiLost) {
			p.wifiLost = true;
			worker.execute(() -> {
				if (session != null) {
					session.pause();
				}
			});
			p.listener.onError(Error.NOT_ON_WIFI, 0);
		}
	}

	@MainThread
	public void preload(@NonNull Magnet magnet, @Nullable Integer fileIdx) {
		if (current != null) {
			return;
		}
		main.removeCallbacks(dropPreload);
		main.postDelayed(dropPreload, WARM_FOR_MS);
		Playback old = prefetch;
		if (old != null && old.infoHash.equalsIgnoreCase(magnet.infoHash) && Objects.equals(old.wantedFile, fileIdx)) {
			return;
		}
		dropPrefetch();
		Playback ahead = new Playback(magnet.infoHash, fileIdx, IGNORED);
		ahead.preload = true;
		prefetch = ahead;
		start(ahead, magnet);
	}

	@MainThread
	private void dropUnplayedPreload() {
		Playback p = prefetch;
		if (p != null && p.preload && current == null) {
			dropPrefetch();
		}
	}

	private void hold(Playback p) {
		run(() -> {
			TorrentHandle h = p.handle;
			if (p.stopped || !p.preload || h == null || !h.isValid()) {
				return;
			}
			try {
				h.unsetFlags(TorrentFlags.AUTO_MANAGED);
				h.pause();
				p.held = true;
			} catch (RuntimeException e) {
				Logs.w(TAG, "Could not pause a preload", e);
			}
		});
	}

	private void resumeHeld(Playback p) {
		TorrentHandle h = p.handle;
		if (h == null || !h.isValid()) {
			return;
		}
		try {
			h.resume();
			h.setFlags(TorrentFlags.AUTO_MANAGED);
		} catch (RuntimeException e) {
			Logs.w(TAG, "Could not resume a preload", e);
		}
	}

	@MainThread
	public void warmUp() {
		main.removeCallbacks(coolDown);
		main.postDelayed(coolDown, WARM_FOR_MS);
		run(() -> {
			ensureSession();
			if (session.isPaused() && idle()) {
				session.resume();
			}
		});
	}

	private void coolDown() {
		run(() -> {
			if (idle() && session != null && !session.isPaused()) {
				saveState();
				session.pause();
			}
		});
	}

	private void run(Runnable task) {
		try {
			worker.execute(task);
		} catch (RejectedExecutionException e) {
		}
	}

	@MainThread
	public void stop(boolean deleteFiles) {
		dropRival();
		Playback p = current;
		current = null;
		if (p != null) {
			release(p, deleteFiles);
		}
	}

	private void release(Playback p, boolean deleteFiles) {
		p.stopped = true;
		main.removeCallbacks(p.tick);
		synchronized (pieceLock) {
			pieceLock.notifyAll();
		}
		worker.execute(() -> {
			if (p.server != null) {
				p.server.stop();
			}
			Playback other = sharing(p);
			if (other != null) {
				if (other.fileIndex >= 0) {
					focus(other);
				}
				return;
			}
			if (session != null && p.handle != null && p.handle.isValid()) {
				if (deleteFiles) {
					session.remove(p.handle, SessionHandle.DELETE_FILES);
				} else {
					session.remove(p.handle);
				}
			}
			if (deleteFiles) {
				deleteRecursively(new File(cacheRoot, p.infoHash));
			}
			if (idle() && session != null) {
				saveState();
				session.pause();
			}
		});
	}

	@Nullable
	private Playback sharing(Playback p) {
		for (Playback other : new Playback[] {current, prefetch, rival}) {
			if (other != null && other != p && !other.stopped && other.infoHash.equalsIgnoreCase(p.infoHash)) {
				return other;
			}
		}
		return null;
	}

	@MainThread
	public void shutdown(boolean deleteCache, long timeoutMs) {
		main.removeCallbacks(coolDown);
		main.removeCallbacks(dropPreload);
		stop(false);
		dropPrefetch();
		worker.execute(() -> {
			if (deleteCache) {
				deleteRecursively(cacheRoot);
			}
			if (session != null) {
				saveState();
				try {
					session.stop();
				} catch (RuntimeException e) {
					Logs.w(TAG, "Could not stop libtorrent", e);
				}
				session = null;
			}
			if (deleteCache) {
				deleteRecursively(cacheRoot);
			}
		});
		worker.shutdown();
		try {
			if (!worker.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
				Logs.w(TAG, "libtorrent took too long to stop", null);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void clearCache() {
		worker.execute(() -> {
			if (idle()) {
				deleteRecursively(cacheRoot);
			}
		});
	}

	public long cacheSizeBytes() {
		return sizeOf(cacheRoot);
	}

	public void housekeep(boolean clearAll) {
		worker.execute(() -> {
			if (cacheRoot.isDirectory() && (clearAll || isLowOnSpace())) {
				trimCache(0);
			}
		});
	}

	private static boolean hasRoomFor(File dir, long size) {
		long free = dir.getUsableSpace();
		return free <= 0 || free >= size + KEEP_FREE_BYTES;
	}

	private boolean isLowOnSpace() {
		long free = cacheRoot.getUsableSpace();
		return free > 0 && free < LOW_SPACE_BYTES;
	}

	private void ensureSession() {
		if (session != null) {
			return;
		}
		SettingsPack settings = new SettingsPack()
				.listenInterfaces("0.0.0.0:0,[::]:0")
				.connectionsLimit(limits.connections)
				.activeDownloads(3)
				.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
				.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
				.setInteger(settings_pack.int_types.connection_speed.swigValue(), limits.connectSpeed)
				.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), limits.uploadBytesPerSecond)
				.setInteger(settings_pack.int_types.torrent_connect_boost.swigValue(), 60)
				.setInteger(settings_pack.int_types.peer_connect_timeout.swigValue(), 7)
				.setInteger(settings_pack.int_types.request_timeout.swigValue(), 10)
				.setInteger(settings_pack.int_types.piece_timeout.swigValue(), 10)
				.setBoolean(settings_pack.bool_types.strict_end_game_mode.swigValue(), false)
				.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), DHT_BOOTSTRAP);
		session = new SessionManager(false);
		session.addListener(new AlertListener() {
			@Override
			public int[] types() {
				return new int[] {
						AlertType.ADD_TORRENT.swig(),
						AlertType.METADATA_RECEIVED.swig(),
						AlertType.PIECE_FINISHED.swig(),
						AlertType.TORRENT_ERROR.swig()
				};
			}

			@Override
			public void alert(Alert<?> alert) {
				onAlert(alert);
			}
		});
		session.start(sessionParams(settings));
	}

	private SessionParams sessionParams(SettingsPack settings) {
		try {
			long size = stateFile.length();
			if (stateFile.isFile() && size > 0 && size <= MAX_STATE_BYTES) {
				SessionParams params = new SessionParams(Files.readAllBytes(stateFile.toPath()));
				params.setSettings(settings);
				return params;
			}
		} catch (IOException | RuntimeException e) {
			Logs.w(TAG, "The saved torrent session didn't read; starting fresh", e);
			stateFile.delete();
		}
		return new SessionParams(settings);
	}

	private void saveState() {
		try {
			byte[] state = session != null ? session.saveState() : null;
			if (state == null || state.length == 0 || state.length > MAX_STATE_BYTES) {
				return;
			}
			File temp = new File(stateFile.getPath() + ".tmp");
			Files.write(temp.toPath(), state);
			Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			Logs.w(TAG, "Could not save the torrent session", e);
		}
	}

	private void onAlert(Alert<?> alert) {
		if (alert instanceof PieceFinishedAlert) {
			synchronized (pieceLock) {
				pieceLock.notifyAll();
			}
			return;
		}
		if (!(alert instanceof TorrentAlert)) {
			return;
		}
		TorrentHandle alertHandle = ((TorrentAlert<?>) alert).handle();
		for (Playback p : new Playback[] {current, prefetch, rival}) {
			if (p == null || p.stopped || !isOurs(p, alertHandle)) {
				continue;
			}
			if (alert instanceof AddTorrentAlert) {
				worker.execute(() -> onAdded(p));
			} else if (alert instanceof MetadataReceivedAlert) {
				worker.execute(() -> prepareFile(p));
			} else if (alert instanceof TorrentErrorAlert) {
				Logs.w(TAG, "Torrent error: " + alert.message(), null);
				fail(p, Error.FAILED, 0);
			}
		}
	}

	private static boolean isOurs(Playback p, TorrentHandle handle) {
		return handle != null && handle.isValid() && p.infoHash.equalsIgnoreCase(handle.infoHash().toHex());
	}

	private void onAdded(Playback p) {
		if (p.stopped || p.handle != null) {
			return;
		}
		TorrentHandle handle = session.find(Sha1Hash.parseHex(p.infoHash));
		if (handle == null || !handle.isValid()) {
			return;
		}
		p.handle = handle;
		if (handle.torrentFile() != null) {
			prepareFile(p);
		}
	}

	private void prepareFile(Playback p) {
		if (p.stopped || p.fileIndex >= 0 || p.handle == null) {
			return;
		}
		TorrentInfo info = p.handle.torrentFile();
		if (info == null) {
			return;
		}
		FileStorage files = info.files();
		int index = pickFile(files, p.wantedFile);
		if (index < 0) {
			fail(p, Error.NO_VIDEO_FILE, 0);
			return;
		}
		long size = files.fileSize(index);
		File dir = new File(cacheRoot, p.infoHash);
		if (!hasRoomFor(dir, size)) {
			trimCache(0);
		}
		if (!hasRoomFor(dir, size)) {
			fail(p, Error.NO_SPACE, size);
			return;
		}
		p.fileIndex = index;
		p.fileSize = size;
		p.fileOffset = files.fileOffset(index);
		p.pieceLength = info.pieceLength();
		p.firstPiece = (int) (p.fileOffset / p.pieceLength);
		p.lastPiece = (int) ((p.fileOffset + size - 1) / p.pieceLength);
		int filePieces = p.lastPiece - p.firstPiece + 1;
		p.startPieces = (int) Math.max(1, Math.min(filePieces, START_BUFFER_BYTES / p.pieceLength));
		p.bytesPerSecond = Math.max(128L << 10, size / ASSUMED_RUNTIME_SECONDS);
		long aheadBytes = Math.max(MIN_AHEAD_BYTES, Math.min(MAX_AHEAD_BYTES, p.bytesPerSecond * AHEAD_SECONDS));
		p.aheadPieces = (int) Math.max(p.startPieces, Math.min(filePieces, aheadBytes / p.pieceLength));
		p.filePath = files.filePath(index, dir.getAbsolutePath());
		p.fileName = files.fileName(index);
		p.readPiece = p.firstPiece;

		synchronized (p) {
			focus(p);
			p.handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD);
			pushWindow(p, p.firstPiece);
			pushTail(p);
		}
		try {
			p.server = new LocalStreamServer(new PieceSource(p), p.fileName);
		} catch (IOException e) {
			Logs.w(TAG, "Local server failed", e);
			fail(p, Error.FAILED, 0);
			return;
		}
		main.post(() -> {
			if (!p.stopped) {
				p.phase = Phase.BUFFERING;
			}
		});
	}

	private void focus(Playback p) {
		FileStorage files = p.handle.torrentFile().files();
		Priority[] priorities = Priority.array(Priority.IGNORE, files.numFiles());
		priorities[p.fileIndex] = Priority.DEFAULT;
		Playback other = sharing(p);
		if (other != null && other.fileIndex >= 0) {
			priorities[other.fileIndex] = Priority.DEFAULT;
		}
		p.handle.prioritizeFiles(priorities);
	}

	private static void pushTail(Playback p) {
		for (int i = 0; i < TAIL_PIECES && p.lastPiece - i >= p.firstPiece; i++) {
			p.handle.setPieceDeadline(p.lastPiece - i, p.deadlineOffset + 300);
		}
	}

	private static void pushWindow(Playback p, int from) {
		for (int i = 0; i < p.aheadPieces && from + i <= p.lastPiece; i++) {
			p.handle.setPieceDeadline(from + i, deadline(p, i));
		}
	}

	private static int deadline(Playback p, int i) {
		if (i < p.startPieces) {
			return p.deadlineOffset + 200 + i * 50;
		}
		long seconds = (long) i * p.pieceLength / p.bytesPerSecond;
		return (int) Math.min(Integer.MAX_VALUE, p.deadlineOffset + 200 + p.startPieces * 50L + seconds * 1000);
	}

	static int pickFile(@NonNull FileStorage files, @Nullable Integer wanted) {
		int count = files.numFiles();
		if (wanted != null && wanted >= 0 && wanted < count && !files.padFileAt(wanted)
				&& isVideo(files.fileName(wanted))) {
			return wanted;
		}
		Integer[] order = new Integer[count];
		for (int i = 0; i < count; i++) {
			order[i] = i;
		}
		Arrays.sort(order, Comparator.comparingLong((Integer i) -> files.fileSize(i)).reversed());
		for (int i : order) {
			if (!files.padFileAt(i) && isVideo(files.fileName(i))) {
				return i;
			}
		}
		return -1;
	}

	static boolean isVideo(@NonNull String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("sample")) {
			return false;
		}
		for (String ext : VIDEO_EXTENSIONS) {
			if (lower.endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	private final class PieceSource implements LocalStreamServer.Source {
		private final Playback p;

		PieceSource(Playback p) {
			this.p = p;
		}

		private void moveWindow(int piece) {
			synchronized (p) {
				if (piece >= p.lastPiece - TAIL_PIECES + 1) {
					if (!p.handle.havePiece(piece)) {
						p.handle.setPieceDeadline(piece, 0);
					}
					return;
				}
				int previous = p.readPiece;
				if (piece == previous) {
					return;
				}
				p.readPiece = piece;
				if (piece == previous + 1) {
					int far = piece + p.aheadPieces - 1;
					if (far <= p.lastPiece) {
						p.handle.setPieceDeadline(far, deadline(p, p.aheadPieces - 1));
					}
				} else {
					p.handle.clearPieceDeadlines();
					pushWindow(p, piece);
				}
			}
		}

		@Override
		public long length() {
			return p.fileSize;
		}

		@Override
		public int read(long position, @NonNull byte[] buffer, int offset, int length) throws IOException {
			long absolute = p.fileOffset + position;
			int piece = (int) (absolute / p.pieceLength);
			long pieceEnd = (long) (piece + 1) * p.pieceLength;
			int chunk = (int) Math.min(length, pieceEnd - absolute);
			try {
				waitForPiece(piece);
			} catch (RuntimeException e) {
				throw new IOException("Torrent gone", e);
			}
			try (RandomAccessFile file = new RandomAccessFile(p.filePath, "r")) {
				file.seek(position);
				int n = file.read(buffer, offset, chunk);
				if (n <= 0) {
					throw new IOException("Unexpected end of file");
				}
				return n;
			}
		}

		private void waitForPiece(int piece) throws IOException {
			moveWindow(piece);
			synchronized (pieceLock) {
				while (!p.handle.havePiece(piece)) {
					if (p.stopped) {
						throw new IOException("Stopped");
					}
					try {
						pieceLock.wait(1000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IOException("Interrupted");
					}
				}
			}
		}
	}

	private void fail(Playback p, Error error, long size) {
		main.post(() -> {
			if (p.stopped) {
				return;
			}
			if (prefetch == p) {
				dropPrefetch();
			}
			if (rival == p) {
				dropRival();
				return;
			}
			Playback other = rival;
			if (current == p && other != null && !other.stopped) {
				takeOver(other, p);
				return;
			}
			p.listener.onError(error, size);
		});
	}

	private void trimCache(long limit) {
		File[] dirs = cacheRoot.listFiles();
		if (dirs == null) {
			return;
		}
		int count = dirs.length;
		long[] sizes = new long[count];
		long[] modified = new long[count];
		Integer[] order = new Integer[count];
		long total = 0;
		for (int i = 0; i < count; i++) {
			sizes[i] = sizeOf(dirs[i]);
			modified[i] = dirs[i].lastModified();
			order[i] = i;
			total += sizes[i];
		}
		Arrays.sort(order, Comparator.comparingLong((Integer i) -> modified[i]));
		for (int i : order) {
			if (total <= limit) {
				break;
			}
			if (!isActive(dirs[i].getName())) {
				total -= sizes[i];
				deleteRecursively(dirs[i]);
			}
		}
	}

	private boolean isActive(String infoHash) {
		for (Playback p : new Playback[] {current, prefetch, rival}) {
			if (p != null && p.infoHash.equalsIgnoreCase(infoHash)) {
				return true;
			}
		}
		return false;
	}

	private boolean idle() {
		return current == null && prefetch == null && rival == null;
	}

	private static long sizeOf(File f) {
		if (f.isFile()) {
			return f.length();
		}
		long total = 0;
		File[] children = f.listFiles();
		if (children != null) {
			for (File c : children) {
				total += sizeOf(c);
			}
		}
		return total;
	}

	private static void deleteRecursively(File f) {
		File[] children = Files.isSymbolicLink(f.toPath()) ? null : f.listFiles();
		if (children != null) {
			for (File c : children) {
				deleteRecursively(c);
			}
		}
		if (!f.delete() && f.exists()) {
			Logs.w(TAG, "Could not delete " + f.getName(), null);
		}
	}
}
