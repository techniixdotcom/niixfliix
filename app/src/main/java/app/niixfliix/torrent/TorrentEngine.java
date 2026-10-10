package app.niixfliix.torrent;

import android.os.Handler;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.niixfliix.util.Logs;

/**
 * Plays one torrent, and downloads the next one in the background so it's ready when this one ends. Downloads
 * in order, start and end first since MP4/MKV keep their index there, and serves the file through LocalStreamServer.
 */
public final class TorrentEngine {

	public interface Listener {
		@MainThread
		void onStatus(@NonNull Status status);

		@MainThread
		void onReady(@NonNull String url, @NonNull String fileName);

		@MainThread
		void onError(@NonNull Error error, long fileSize);
	}

	public enum Error {
		NO_VIDEO_FILE,
		TOO_BIG_FOR_CACHE,
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
	/** what the player needs before it starts: the container header and the first seconds */
	private static final long START_BUFFER_BYTES = 4L << 20;
	/**
	 * Everything downloads in order, but the next five minutes after the playhead are time-critical: libtorrent
	 * asks the fastest peers for them first and re-asks others if they're slow. After that, normal downloading.
	 */
	private static final int AHEAD_SECONDS = 5 * 60;
	/** torrents don't say how long the video is; a two-hour guess turns file size into a bitrate */
	private static final int ASSUMED_RUNTIME_SECONDS = 2 * 60 * 60;
	private static final long MIN_AHEAD_BYTES = 16L << 20;
	private static final long MAX_AHEAD_BYTES = 512L << 20;
	/** the container index (MKV cues, MP4 moov) is often at the end; reads there don't move the window */
	private static final int TAIL_PIECES = 2;
	/** added to the next torrent's deadlines while it waits its turn, so the playing one always wins */
	private static final int PREFETCH_DEADLINE_MS = 20_000;
	private static final long STATUS_INTERVAL_MS = 1000;
	private static final String[] VIDEO_EXTENSIONS = {".mkv", ".mp4", ".m4v", ".webm", ".avi", ".mov", ".ts", ".wmv"};

	private final File cacheRoot;
	private final Handler main;
	private final ExecutorService worker = Executors.newSingleThreadExecutor();
	private final Object pieceLock = new Object();

	private SessionManager session;
	@Nullable private volatile Playback current;
	/** what plays next, downloading while the current one still plays */
	@Nullable private volatile Playback prefetch;

	private final class Playback {
		final String infoHash;
		final Integer wantedFile;
		final long cacheLimit;
		volatile Listener listener;
		/** PREFETCH_DEADLINE_MS while it waits for its turn */
		volatile int deadlineOffset;
		final Runnable tick = this::tick;
		TorrentHandle handle;
		int fileIndex = -1;
		long fileSize;
		long fileOffset;
		int pieceLength;
		int firstPiece;
		int lastPiece;
		int startPieces;
		int aheadPieces;
		/** estimated, from the file size */
		long bytesPerSecond;
		String filePath;
		String fileName;
		LocalStreamServer server;
		volatile boolean stopped;
		volatile int readPiece;
		Phase phase = Phase.METADATA;
		boolean wifiLost;

		Playback(String infoHash, @Nullable Integer wantedFile, long cacheLimit, Listener listener) {
			this.infoHash = infoHash;
			this.wantedFile = wantedFile;
			this.cacheLimit = cacheLimit;
			this.listener = listener;
		}

		void tick() {
			if (stopped || handle == null || !handle.isValid()) {
				if (!stopped) {
					main.postDelayed(tick, STATUS_INTERVAL_MS);
				}
				return;
			}
			TorrentStatus s = handle.status();
			int buffered = phase == Phase.METADATA ? 0 : startProgress();
			listener.onStatus(new Status(phase, s.numPeers(), s.downloadPayloadRate(), buffered));
			if (phase == Phase.BUFFERING && buffered >= 100) {
				phase = Phase.PLAYING;
				listener.onReady(server.url(), fileName);
			}
			main.postDelayed(tick, STATUS_INTERVAL_MS);
		}

		int startProgress() {
			int have = 0;
			for (int p = firstPiece; p < firstPiece + startPieces; p++) {
				if (handle.havePiece(p)) {
					have++;
				}
			}
			boolean tail = handle.havePiece(lastPiece);
			int total = startPieces + 1;
			return (have + (tail ? 1 : 0)) * 100 / total;
		}
	}

	public TorrentEngine(@NonNull File cacheRoot, @NonNull Handler main) {
		this.cacheRoot = cacheRoot;
		this.main = main;
	}

	/**
	 * Downloads what plays next while this one plays, the same way as {@link #play} but behind it. Once its start
	 * is in, {@code listener} gets {@link Listener#onReady}; at the switch {@link #promote} or {@link #play} carry
	 * on with it instead of starting over.
	 */
	@MainThread
	public void prefetch(@NonNull Magnet magnet, @Nullable Integer fileIdx, long cacheLimitBytes,
			@NonNull Listener listener) {
		Playback old = prefetch;
		if (old != null && old.infoHash.equalsIgnoreCase(magnet.infoHash) && Objects.equals(old.wantedFile, fileIdx)) {
			old.listener = listener;
			return;
		}
		dropPrefetch();
		Playback next = new Playback(magnet.infoHash, fileIdx, cacheLimitBytes, listener);
		next.deadlineOffset = PREFETCH_DEADLINE_MS;
		prefetch = next;
		start(next, magnet);
	}

	/** what was coming up won't play after all */
	@MainThread
	public void dropPrefetch() {
		Playback old = prefetch;
		prefetch = null;
		if (old != null) {
			release(old, true);
		}
	}

	/**
	 * The prefetched torrent becomes the playing one and keeps going where it got to, for when the player already
	 * moved on to it by itself. false if nothing for {@code infoHash} was prefetched.
	 */
	@MainThread
	public boolean promote(@NonNull String infoHash, boolean deleteOld, @NonNull Listener listener) {
		Playback next = prefetch;
		if (next == null || !next.infoHash.equalsIgnoreCase(infoHash)) {
			return false;
		}
		prefetch = null;
		stop(deleteOld);
		next.listener = listener;
		current = next;
		worker.execute(() -> {
			synchronized (next) {
				next.deadlineOffset = 0;
				if (next.stopped || next.fileIndex < 0) {
					// prepareFile does all this itself when the file info comes in
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
	public void play(@NonNull Magnet magnet, @Nullable Integer fileIdx, long cacheLimitBytes, boolean deleteOld,
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
		Playback playback = new Playback(magnet.infoHash, fileIdx, cacheLimitBytes, listener);
		current = playback;
		start(playback, magnet);
	}

	private void start(Playback p, Magnet magnet) {
		main.post(p.tick);
		worker.execute(() -> {
			try {
				ensureSession();
				if (session.isPaused()) {
					session.resume();
				}
				trimCache(p.cacheLimit);
				File dir = new File(cacheRoot, p.infoHash);
				if (!dir.isDirectory() && !dir.mkdirs()) {
					throw new IOException("Cannot create " + dir);
				}
				session.download(magnet.withPublicTrackers().toUri(), dir, new torrent_flags_t());
				TorrentHandle existing = session.find(Sha1Hash.parseHex(p.infoHash));
				if (existing != null && existing.isValid()) {
					onAdded(p, existing);
				}
			} catch (IOException | RuntimeException e) {
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
			// pause the whole session, DHT too
			worker.execute(() -> {
				if (session != null) {
					session.pause();
				}
			});
			p.listener.onError(Error.NOT_ON_WIFI, 0);
		}
	}

	@MainThread
	public void stop(boolean deleteFiles) {
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
			// a season pack: the next episode is in the same torrent
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
			// nothing playing or coming up, stop announcing to peers/DHT
			if (current == null && prefetch == null && session != null) {
				session.pause();
			}
		});
	}

	/** the other playback on the same torrent, if there is one */
	@Nullable
	private Playback sharing(Playback p) {
		for (Playback other : new Playback[] {current, prefetch}) {
			if (other != null && other != p && !other.stopped && other.infoHash.equalsIgnoreCase(p.infoHash)) {
				return other;
			}
		}
		return null;
	}

	/** only while nothing plays */
	public void clearCache() {
		worker.execute(() -> {
			if (current == null && prefetch == null) {
				deleteRecursively(cacheRoot);
			}
		});
	}

	public long cacheSizeBytes() {
		return sizeOf(cacheRoot);
	}

	private void ensureSession() {
		if (session != null) {
			return;
		}
		SettingsPack settings = new SettingsPack()
				.listenInterfaces("0.0.0.0:0,[::]:0")
				.connectionsLimit(200)
				.activeDownloads(2)
				// find peers fast: every tracker at once, more new connections per second, a bigger first burst
				.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
				.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
				.setInteger(settings_pack.int_types.connection_speed.swigValue(), 100)
				.setInteger(settings_pack.int_types.torrent_connect_boost.swigValue(), 60)
				// give up on dead or slow peers quickly so their pieces go to someone faster
				.setInteger(settings_pack.int_types.peer_connect_timeout.swigValue(), 7)
				.setInteger(settings_pack.int_types.request_timeout.swigValue(), 10)
				.setInteger(settings_pack.int_types.piece_timeout.swigValue(), 10)
				.setBoolean(settings_pack.bool_types.strict_end_game_mode.swigValue(), false);
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
		session.start(new SessionParams(settings));
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
		TorrentHandle handle = ((TorrentAlert<?>) alert).handle();
		for (Playback p : new Playback[] {current, prefetch}) {
			if (p == null || p.stopped || !isOurs(p, handle)) {
				continue;
			}
			if (alert instanceof AddTorrentAlert) {
				worker.execute(() -> onAdded(p, handle));
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

	private void onAdded(Playback p, TorrentHandle handle) {
		if (p.stopped || p.handle != null) {
			return;
		}
		p.handle = handle;
		if (handle.torrentFile() != null) {
			prepareFile(p);
		}
	}

	/** worker thread, once metadata is in */
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
		if (size > p.cacheLimit) {
			fail(p, Error.TOO_BIG_FOR_CACHE, size);
			return;
		}
		File dir = new File(cacheRoot, p.infoHash);
		if (dir.getUsableSpace() < size) {
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

	/** only this playback's file downloads, plus the other one's when both are in the same torrent */
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

	/** the player can't start without the index at the end either */
	private static void pushTail(Playback p) {
		for (int i = 0; i < TAIL_PIECES && p.lastPiece - i >= p.firstPiece; i++) {
			p.handle.setPieceDeadline(p.lastPiece - i, p.deadlineOffset + 300);
		}
	}

	/** time-critical deadlines for the look-ahead window from {@code from}, sooner pieces first */
	private static void pushWindow(Playback p, int from) {
		for (int i = 0; i < p.aheadPieces && from + i <= p.lastPiece; i++) {
			p.handle.setPieceDeadline(from + i, deadline(p, i));
		}
	}

	/** ms until the player reaches the i-th piece ahead; the start buffer is wanted right away */
	private static int deadline(Playback p, int i) {
		if (i < p.startPieces) {
			return p.deadlineOffset + 200 + i * 50;
		}
		long seconds = (long) i * p.pieceLength / p.bytesPerSecond;
		return (int) Math.min(Integer.MAX_VALUE, p.deadlineOffset + 200 + p.startPieces * 50L + seconds * 1000);
	}

	/** add-on's fileIdx if it's a video, else the largest video file */
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

		/** playing on: the window slides one piece. A seek: it starts over from there. */
		private void moveWindow(int piece) {
			// the local server reads on several threads
			synchronized (p) {
				if (piece >= p.lastPiece - TAIL_PIECES + 1) {
					// the player peeking at the index at the end, not really moving
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
			waitForPiece(piece);
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
				// play() starts it fresh later and reports the problem then
				dropPrefetch();
			}
			p.listener.onError(error, size);
		});
	}

	/** oldest first until it fits; never what's playing or coming up */
	private void trimCache(long limit) {
		File[] dirs = cacheRoot.listFiles();
		if (dirs == null) {
			return;
		}
		Arrays.sort(dirs, Comparator.comparingLong(File::lastModified));
		long total = 0;
		for (File d : dirs) {
			total += sizeOf(d);
		}
		for (File d : dirs) {
			if (total <= limit) {
				break;
			}
			if (!isActive(d.getName())) {
				total -= sizeOf(d);
				deleteRecursively(d);
			}
		}
	}

	private boolean isActive(String infoHash) {
		Playback p = current;
		Playback next = prefetch;
		return (p != null && p.infoHash.equalsIgnoreCase(infoHash))
				|| (next != null && next.infoHash.equalsIgnoreCase(infoHash));
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
		File[] children = f.listFiles();
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
