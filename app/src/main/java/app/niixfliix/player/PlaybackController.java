package app.niixfliix.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import app.niixfliix.R;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.addon.model.Subtitle;
import app.niixfliix.addon.model.Video;
import app.niixfliix.app.AppGraph;
import app.niixfliix.app.Constant;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.net.Network;
import app.niixfliix.torrent.Magnet;
import app.niixfliix.torrent.TorrentEngine;
import app.niixfliix.ui.Formats;

/**
 * Plays things for the whole app, independent of the player screen: the screen can close (minimize) and the
 * video keeps going with the mini bar and the notification, the screen can come back and pick up where it is.
 * Owns the player, the torrent, retries, progress saving and what plays next.
 */
@MainThread
@OptIn(markerClass = UnstableApi.class)
public final class PlaybackController implements Player.Listener {

	/** the player screen while it's showing */
	public interface Screen {
		void onTitle(@NonNull String title, @NonNull String subtitle);

		/** loading text over the video, null hides it */
		void onLoading(@Nullable String text);

		/** null hides it */
		void onError(@Nullable String message, boolean offerNext);

		/** null hides the card */
		void onUpNext(@Nullable String title, long remainingMs);

		void onClosed();
	}

	/** the mini bars */
	public interface Listener {
		void onNowPlayingChanged();
	}

	private static final long TICK_MS = 1000;
	private static final long SAVE_EVERY_MS = 10_000;
	/** below this the card would cover half the video or more */
	private static final long MIN_DURATION_FOR_UP_NEXT_MS = 10 * 60 * 1000;
	private static final int MAX_ADDON_SUBTITLES = 200;
	/** a torrent with no peers or no metadata by then is dead, the next stream gets a go */
	private static final long TORRENT_START_TIMEOUT_MS = 45_000;
	private static final long END_SLACK_MS = 1500;
	/** some files never report their end; this many seconds stuck right before it counts as the end */
	private static final int STUCK_AT_END_TICKS = 3;

	private final Context context;
	private final AppGraph graph = AppGraph.get();
	private final Runnable tick = this::tick;
	private final Runnable torrentStartCheck = this::checkTorrentStarted;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private final List<TrackPicker.AddonSubtitle> addonSubtitles = new ArrayList<>();

	@Nullable private PlayerEngine engine;
	@Nullable private NextPlayer nextPlayer;
	@Nullable private PlaySession session;
	@Nullable private ListenableFuture<MediaController> serviceConnection;
	@Nullable private Screen screen;

	private MediaMetadata metadata = MediaMetadata.EMPTY;
	private String title = "";
	private String subtitle = "";
	@Nullable private String loading;
	@Nullable private String error;
	private boolean errorOffersNext;
	@Nullable private String upNextTitle;
	private long upNextRemaining;
	private boolean upNextCancelled;

	private boolean usingTorrent;
	@Nullable private TorrentEngine.Status torrentStatus;
	private long pendingStartMs;
	private boolean autoTriedNext;
	private long lastSave;
	/** of the playing item, kept so its progress can be saved after the player has moved on */
	private long lastDurationMs;
	private long lastPositionMs;
	private int stuckAtEndTicks;
	private boolean ended;
	private int subtitleGeneration;
	/** add-ons still looking for subtitles for this video */
	private int subtitleRequests;
	private boolean subtitlesDecided;

	/** what plays after this: the head of the queue, else the next episode */
	@Nullable private QueueItem upcoming;
	private boolean upcomingFromQueue;
	@Nullable private QueueResolver upcomingResolver;
	@Nullable private PlaySession upcomingSession;
	/** already in the player's playlist (direct link, or torrent once its start is in), so the switch has no pause */
	private boolean upcomingQueuedInPlayer;
	private boolean upcomingDone;
	private boolean waitingForUpcoming;

	public PlaybackController(@NonNull Context context) {
		this.context = context.getApplicationContext();
	}

	/** starts playing, or replaces what's playing */
	public void play(@NonNull PlaySession next) {
		if (engine == null) {
			engine = new PlayerEngine(context, graph.http, graph.playerPrefs);
			engine.player().addListener(this);
			nextPlayer = new NextPlayer(engine.player(), this::playNextNow);
			PlaybackService.attach(nextPlayer);
			// binding keeps the media session service (notification, lock screen) running while it plays
			serviceConnection = new MediaController.Builder(context,
					new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync();
			graph.main.postDelayed(tick, TICK_MS);
		} else if (session != null) {
			saveProgress();
			stopTorrent();
			if (!next.metaId.equals(session.metaId)) {
				engine.resetTracks();
			}
		}
		session = next;
		autoTriedNext = false;
		startCurrent(next.meta != null ? graph.history.resumePosition(next.videoId) : 0);
		notifyListeners();
	}

	/** the mini bar's X, the error card's X, or nothing left to play */
	public void stop() {
		if (engine == null) {
			return;
		}
		saveProgress();
		graph.main.removeCallbacks(tick);
		resetUpcoming();
		graph.torrentEngine().dropPrefetch();
		stopTorrent();
		if (serviceConnection != null) {
			MediaController.releaseFuture(serviceConnection);
			serviceConnection = null;
		}
		PlaybackService.attach(null);
		context.stopService(new Intent(context, PlaybackService.class));
		engine.player().removeListener(this);
		engine.release();
		engine = null;
		nextPlayer = null;
		session = null;
		addonSubtitles.clear();
		Screen s = screen;
		screen = null;
		if (s != null) {
			s.onClosed();
		}
		notifyListeners();
	}

	/** what the screen, the mini bar and the media session use; its "next" is the next episode */
	@Nullable
	public Player player() {
		return nextPlayer;
	}

	@Nullable
	private ExoPlayer exo() {
		return engine != null ? engine.player() : null;
	}

	@Nullable
	public PlayerEngine engine() {
		return engine;
	}

	@Nullable
	public PlaySession session() {
		return session;
	}

	@NonNull
	public List<TrackPicker.AddonSubtitle> addonSubtitles() {
		return addonSubtitles;
	}

	/** the screen shows the current state right away */
	public void attach(@NonNull Screen s) {
		screen = s;
		s.onTitle(title, subtitle);
		s.onLoading(loading);
		s.onError(error, errorOffersNext);
		s.onUpNext(upNextTitle, upNextRemaining);
		notifyListeners();
	}

	public void detach(@NonNull Screen s) {
		if (screen == s) {
			screen = null;
			notifyListeners();
		}
	}

	/** the playing player when the player screen isn't showing, for the mini bar */
	@Nullable
	public Player inBackground() {
		return screen == null ? nextPlayer : null;
	}

	public void addListener(@NonNull Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(@NonNull Listener listener) {
		listeners.remove(listener);
	}

	private void switchTo(@NonNull StreamInfo s) {
		ExoPlayer p = exo();
		if (session == null || p == null) {
			return;
		}
		long position = p.getCurrentPosition();
		saveProgress();
		stopTorrent();
		session.stream = s;
		startCurrent(position);
	}

	/** the user picked one in the stream switcher */
	public void pick(@NonNull StreamInfo s) {
		autoTriedNext = false;
		switchTo(s);
	}

	public void tryNextStream() {
		StreamInfo next = nextCandidate();
		if (next != null) {
			switchTo(next);
		}
	}

	/** the X on the "Up next" card, only for this video */
	public void cancelUpNext() {
		upNextCancelled = true;
		showUpNext(null, 0);
		resetUpcoming();
		graph.torrentEngine().dropPrefetch();
	}

	/** tap on the "Up next" card */
	public void playNextNow() {
		ExoPlayer p = exo();
		if (upcomingQueuedInPlayer && p != null) {
			// already buffered in the player, just move to it
			p.seekToNextMediaItem();
			return;
		}
		if (prefetchUpcoming() != null) {
			showLoading(context.getString(R.string.queue_finding));
			playUpcoming();
		}
	}

	/** something was queued or removed: the next button may have something to do now */
	public void queueChanged() {
		if (nextPlayer != null) {
			nextPlayer.setHasNext(upcoming != null || findUpcoming() != null);
		}
	}

	private void startCurrent(long startMs) {
		PlaySession s = session;
		PlayerEngine e = engine;
		if (s == null || e == null) {
			return;
		}
		showSession(s);
		StreamInfo stream = s.stream;
		if (stream.kind == StreamInfo.Kind.TORRENT && stream.stream.infoHash != null) {
			startTorrent(stream, startMs);
		} else if (stream.stream.url != null) {
			// TorrentEngine.play drops it for a torrent
			graph.torrentEngine().dropPrefetch();
			showLoading(null);
			e.play(stream.stream.url, stream.stream.requestHeaders(), startMs, metadata);
		}
	}

	/** everything about a new current item except loading it */
	private void showSession(PlaySession s) {
		subtitlesDecided = false;
		if (engine != null && !engine.userPicked()) {
			engine.resetAutoSubtitles();
		}
		showError(null, false);
		upNextCancelled = false;
		ended = false;
		stuckAtEndTicks = 0;
		lastDurationMs = 0;
		lastPositionMs = 0;
		showUpNext(null, 0);
		resetUpcoming();
		lastSave = System.currentTimeMillis();
		Video video = s.video;
		String episode = video != null ? Formats.episode(context, video) : null;
		title = s.title;
		subtitle = episode != null ? episode : s.stream.quality.title;
		if (screen != null) {
			screen.onTitle(title, subtitle);
		}
		metadata = metadataFor(s);
		loadAddonSubtitles();
		queueChanged();
	}

	/** what the notification, lock screen and mini bar show */
	private MediaMetadata metadataFor(PlaySession s) {
		return new MediaMetadata.Builder()
				.setTitle(s.title)
				.setArtist(s.video != null ? Formats.episode(context, s.video) : null)
				.setArtworkUri(s.poster != null ? Uri.parse(s.poster) : null)
				.build();
	}

	private void startTorrent(StreamInfo s, long startMs) {
		PlayerEngine e = engine;
		if (e == null) {
			return;
		}
		e.player().stop();
		usingTorrent = true;
		pendingStartMs = startMs;
		torrentStatus = null;
		showLoading(context.getString(R.string.torrent_connecting));
		graph.main.removeCallbacks(torrentStartCheck);
		graph.main.postDelayed(torrentStartCheck, TORRENT_START_TIMEOUT_MS);
		graph.torrentEngine().play(magnetFor(s), s.stream.fileIdx, torrentCacheBytes(),
				graph.prefs.torrentClearOnExit(), torrentListener());
	}

	private static Magnet magnetFor(StreamInfo s) {
		return new Magnet(s.stream.infoHash, s.fileName != null ? s.fileName : s.releaseName,
				Magnet.trackersFromSources(s.stream.sources));
	}

	private long torrentCacheBytes() {
		return (long) graph.prefs.torrentCacheMb() << 20;
	}

	/** for the torrent that's playing */
	private TorrentEngine.Listener torrentListener() {
		return new TorrentEngine.Listener() {
			@Override
			public void onStatus(@NonNull TorrentEngine.Status status) {
				onTorrentStatus(status);
			}

			@Override
			public void onReady(@NonNull String url, @NonNull String fileName) {
				if (engine != null) {
					engine.play(url, new HashMap<>(), pendingStartMs, metadata);
				}
			}

			@Override
			public void onError(@NonNull TorrentEngine.Error error, long fileSize) {
				showLoading(null);
				// settings problems (cache, space, Wi-Fi) would fail the next stream too
				boolean streamProblem = error == TorrentEngine.Error.FAILED
						|| error == TorrentEngine.Error.NO_VIDEO_FILE;
				if (!streamProblem || !skipToNextStream()) {
					showError(torrentMessage(error, fileSize), true);
				}
			}
		};
	}

	/** for the one downloading behind it: once its start is in, it goes into the playlist like a direct link */
	private TorrentEngine.Listener prefetchListener(PlaySession next) {
		return new TorrentEngine.Listener() {
			@Override
			public void onStatus(@NonNull TorrentEngine.Status status) {
				// nobody's watching this one yet
			}

			@Override
			public void onReady(@NonNull String url, @NonNull String fileName) {
				PlayerEngine e = engine;
				if (e != null && upcomingSession == next && !upcomingQueuedInPlayer && !upNextCancelled
						&& !waitingForUpcoming) {
					e.queueNext(url, new HashMap<>(), metadataFor(next));
					upcomingQueuedInPlayer = true;
				}
			}

			@Override
			public void onError(@NonNull TorrentEngine.Error error, long fileSize) {
				// it gets a fresh start when its turn comes, and reports the problem then
			}
		};
	}

	private void stopTorrent() {
		graph.main.removeCallbacks(torrentStartCheck);
		torrentStatus = null;
		if (usingTorrent) {
			usingTorrent = false;
			graph.torrentEngine().stop(graph.prefs.torrentClearOnExit());
		}
	}

	private void onTorrentStatus(TorrentEngine.Status status) {
		torrentStatus = status;
		ExoPlayer p = exo();
		boolean buffering = status.phase != TorrentEngine.Phase.PLAYING
				|| p != null && p.getPlaybackState() == Player.STATE_BUFFERING;
		if (!buffering) {
			showLoading(null);
			return;
		}
		String speed = Formats.speed(context, status.bytesPerSecond);
		String peers = context.getResources().getQuantityString(R.plurals.torrent_peers, status.peers, status.peers);
		switch (status.phase) {
			case METADATA:
				showLoading(context.getString(R.string.torrent_finding, peers, speed));
				break;
			case BUFFERING:
				showLoading(context.getString(R.string.torrent_buffering, status.bufferedPercent, peers, speed));
				break;
			case PLAYING:
			default:
				showLoading(context.getString(R.string.torrent_playing, peers, speed));
				break;
		}
	}

	/** still nothing to play: no peers, or no metadata yet. Slow but moving torrents get more time. */
	private void checkTorrentStarted() {
		ExoPlayer p = exo();
		if (!usingTorrent || p == null || p.getPlaybackState() == Player.STATE_READY) {
			return;
		}
		TorrentEngine.Status status = torrentStatus;
		boolean moving = status != null && status.peers > 0 && status.phase != TorrentEngine.Phase.METADATA;
		if (moving) {
			graph.main.postDelayed(torrentStartCheck, TORRENT_START_TIMEOUT_MS);
		} else if (!skipToNextStream()) {
			showLoading(null);
			showError(context.getString(R.string.torrent_error_failed), false);
		}
	}

	private String torrentMessage(TorrentEngine.Error error, long fileSize) {
		switch (error) {
			case NO_VIDEO_FILE:
				return context.getString(R.string.torrent_error_no_video);
			case TOO_BIG_FOR_CACHE:
				return context.getString(R.string.torrent_error_too_big, Formats.size(context, fileSize),
						Formats.size(context, torrentCacheBytes()));
			case NO_SPACE:
				return context.getString(R.string.torrent_error_no_space, Formats.size(context, fileSize));
			case NOT_ON_WIFI:
				return context.getString(R.string.torrent_error_wifi);
			case FAILED:
			default:
				return context.getString(R.string.torrent_error_failed);
		}
	}

	@Override
	public void onPlayerError(@NonNull PlaybackException e) {
		PlaySession s = session;
		if (s == null) {
			return;
		}
		saveProgress();
		StreamInfo next = StreamRanker.nextSameQuality(s.streams, s.stream);
		if (!autoTriedNext && next != null) {
			// one silent retry at the same quality, after that it's up to the user
			autoTriedNext = true;
			Toast.makeText(context, R.string.player_trying_next, Toast.LENGTH_SHORT).show();
			switchTo(next);
			return;
		}
		showError(errorMessage(e), true);
	}

	@Override
	public void onPlaybackStateChanged(int state) {
		if (state == Player.STATE_ENDED) {
			onEnded();
		} else if (state == Player.STATE_READY && usingTorrent) {
			showLoading(null);
			graph.main.removeCallbacks(torrentStartCheck);
		}
	}

	@Override
	public void onTracksChanged(@NonNull Tracks tracks) {
		if (engine != null) {
			engine.onTracksChanged(tracks);
			decideSubtitles(tracks);
		}
	}

	/**
	 * Once per video, from the language chosen at first start: audio in it if there is some (the track
	 * selector already took it), else subtitles in it, else English subtitles over the original audio.
	 * Add-on subtitles count, so it waits for them before falling back to English.
	 */
	private void decideSubtitles(Tracks tracks) {
		PlayerEngine e = engine;
		if (subtitlesDecided || e == null || tracks.isEmpty()) {
			return;
		}
		String wanted = graph.playerPrefs.language();
		boolean audioFits = Languages.hasAudio(tracks, wanted)
				// an untagged track is most likely English, so leave English speakers alone
				|| Languages.audioUntagged(tracks) && wanted.equals("en");
		if (e.userPicked() || audioFits || useSubtitles(tracks, wanted)) {
			subtitlesDecided = true;
			return;
		}
		if (subtitleRequests > 0) {
			// an add-on may still come back with some in that language
			return;
		}
		subtitlesDecided = true;
		if (!wanted.equals("en")) {
			useSubtitles(tracks, "en");
		}
	}

	private boolean useSubtitles(Tracks tracks, String language) {
		PlayerEngine e = engine;
		if (e == null) {
			return false;
		}
		if (Languages.hasText(tracks, language)) {
			e.showSubtitlesIn(language);
			return true;
		}
		for (TrackPicker.AddonSubtitle s : addonSubtitles) {
			if (Languages.matches(s.language, language)) {
				e.addAutoSubtitle(s.toSide());
				return true;
			}
		}
		return false;
	}

	@Nullable
	private StreamInfo nextCandidate() {
		PlaySession s = session;
		if (s == null) {
			return null;
		}
		StreamInfo same = StreamRanker.nextSameQuality(s.streams, s.stream);
		if (same != null) {
			return same;
		}
		boolean seen = false;
		for (StreamInfo candidate : s.streams) {
			if (candidate.key().equals(s.stream.key())) {
				seen = true;
			} else if (seen && StreamRanker.isPlayable(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	/** quietly moves on to the next candidate; false if there's none left */
	private boolean skipToNextStream() {
		StreamInfo next = nextCandidate();
		if (next == null) {
			return false;
		}
		Toast.makeText(context, R.string.player_trying_next, Toast.LENGTH_SHORT).show();
		switchTo(next);
		return true;
	}

	private String errorMessage(PlaybackException e) {
		switch (e.errorCode) {
			case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
				if (e.getCause() instanceof HttpDataSource.InvalidResponseCodeException) {
					int code = ((HttpDataSource.InvalidResponseCodeException) e.getCause()).responseCode;
					return context.getString(R.string.player_error_http, code);
				}
				return context.getString(R.string.player_error_generic);
			case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
			case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
				return context.getString(R.string.player_error_network);
			case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
			case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
			case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
				return context.getString(R.string.player_error_format);
			default:
				return context.getString(R.string.player_error_generic);
		}
	}

	private void tick() {
		graph.main.postDelayed(tick, TICK_MS);
		ExoPlayer p = exo();
		if (p == null) {
			return;
		}
		if (usingTorrent && graph.prefs.torrentWifiOnly() && !Network.isUnmetered(context)) {
			graph.torrentEngine().onWifiLost();
		}
		long now = System.currentTimeMillis();
		if (p.isPlaying() && now - lastSave >= SAVE_EVERY_MS) {
			lastSave = now;
			saveProgress();
		}
		long duration = p.getDuration();
		if (duration > 0) {
			lastDurationMs = duration;
			lastPositionMs = p.getCurrentPosition();
			boolean stuckAtEnd = p.getPlaybackState() == Player.STATE_BUFFERING
					&& duration - p.getCurrentPosition() <= END_SLACK_MS;
			stuckAtEndTicks = stuckAtEnd ? stuckAtEndTicks + 1 : 0;
			if (stuckAtEndTicks >= STUCK_AT_END_TICKS) {
				onEnded();
			}
		}
		checkUpNext(p);
	}

	private void saveProgress() {
		PlaySession s = session;
		ExoPlayer p = exo();
		if (s == null || s.meta == null || p == null) {
			return;
		}
		long duration = p.getDuration();
		if (duration <= 0) {
			return;
		}
		long position = p.getPlaybackState() == Player.STATE_ENDED || ended ? duration : p.getCurrentPosition();
		HistoryEntry entry = entryFor(s);
		entry.positionMs = position;
		entry.durationMs = duration;
		graph.history.save(entry);
	}

	private static HistoryEntry entryFor(PlaySession s) {
		HistoryEntry entry = new HistoryEntry();
		entry.metaId = s.metaId;
		entry.type = s.type;
		entry.name = s.title;
		entry.poster = s.poster;
		entry.videoId = s.videoId;
		return entry;
	}

	private void checkUpNext(ExoPlayer p) {
		long duration = p.getDuration();
		if (upNextCancelled || duration < MIN_DURATION_FOR_UP_NEXT_MS) {
			return;
		}
		long remaining = Math.max(0, duration - p.getCurrentPosition());
		if (remaining > Constant.UP_NEXT_SECONDS * 1000L) {
			return;
		}
		QueueItem next = prefetchUpcoming();
		if (next != null) {
			showUpNext(upNextLabel(next), remaining);
		}
	}

	@Nullable
	private QueueItem findUpcoming() {
		QueueItem queued = graph.queue.peek();
		if (queued != null) {
			return queued;
		}
		PlaySession s = session;
		Video next = s != null ? s.nextVideo() : null;
		if (s == null || next == null || next.id == null) {
			return null;
		}
		QueueItem episode = QueueItem.of(s.metaId, s.type, s.title, s.poster);
		episode.videoId = next.id;
		episode.episode = Formats.episode(context, next);
		return episode;
	}

	/** starts looking for streams once, returns what will play next */
	@Nullable
	private QueueItem prefetchUpcoming() {
		if (upcoming != null) {
			return upcoming;
		}
		QueueItem next = findUpcoming();
		PlaySession s = session;
		if (next == null || s == null) {
			return null;
		}
		upcoming = next;
		upcomingFromQueue = next == graph.queue.peek();
		upcomingResolver = QueueResolver.start(context, next, upcomingFromQueue ? null : s.meta,
				upcomingFromQueue ? null : s.stream.stream.bingeGroup(), new QueueResolver.Callback() {
					@Override
					public void onReady(@NonNull PlaySession ready) {
						upcomingSession = ready;
						preload(ready);
						upcomingDone();
					}

					@Override
					public void onFailed() {
						upcomingDone();
					}
				});
		return next;
	}

	private void upcomingDone() {
		upcomingDone = true;
		if (waitingForUpcoming) {
			playUpcoming();
		}
	}

	private String upNextLabel(QueueItem next) {
		PlaySession s = session;
		return next.episode != null && s != null && next.metaId.equals(s.metaId) ? next.episode : next.label();
	}

	private void resetUpcoming() {
		if (upcomingResolver != null) {
			upcomingResolver.cancel();
		}
		if (upcomingQueuedInPlayer && engine != null) {
			engine.clearNext();
		}
		upcomingQueuedInPlayer = false;
		upcomingResolver = null;
		upcoming = null;
		upcomingFromQueue = false;
		upcomingSession = null;
		upcomingDone = false;
		waitingForUpcoming = false;
	}

	private void onEnded() {
		if (ended) {
			return;
		}
		ended = true;
		saveProgress();
		if (upcoming == null && findUpcoming() == null) {
			// not from inside the player's own callback
			graph.main.post(this::stop);
			return;
		}
		if (upNextCancelled) {
			return;
		}
		showUpNext(upNextLabel(prefetchUpcoming()), 0);
		playUpcoming();
	}

	/**
	 * Starts on the next one while this one is still playing. A direct link goes straight into the player's
	 * playlist; a torrent downloads in the background and joins the playlist once its start is in. Either way the
	 * player moves on without a pause.
	 */
	private void preload(PlaySession next) {
		PlayerEngine e = engine;
		if (e == null || upNextCancelled || waitingForUpcoming) {
			// if it's already waiting, playUpcoming loads it right away anyway
			return;
		}
		StreamInfo s = next.stream;
		if (s.kind == StreamInfo.Kind.TORRENT && s.stream.infoHash != null) {
			if (graph.prefs.torrentWifiOnly() && !Network.isUnmetered(context)) {
				return;
			}
			graph.torrentEngine().prefetch(magnetFor(s), s.stream.fileIdx, torrentCacheBytes(), prefetchListener(next));
		} else if (s.kind == StreamInfo.Kind.DIRECT && s.stream.url != null) {
			e.queueNext(s.stream.url, s.stream.requestHeaders(), metadataFor(next));
			upcomingQueuedInPlayer = true;
		}
	}

	@Override
	public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
		boolean moved = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
				|| reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK;
		if (moved && upcomingQueuedInPlayer) {
			adoptUpcoming(reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
		}
	}

	/** the player already moved on to the queued item: catch up with it. finished is false when the user skipped */
	private void adoptUpcoming(boolean finished) {
		PlaySession next = upcomingSession;
		PlaySession previous = session;
		PlayerEngine e = engine;
		if (next == null || previous == null || e == null) {
			return;
		}
		if (finished) {
			markFinished(previous);
		} else {
			saveSkipped(previous);
		}
		QueueItem item = upcoming;
		if (upcomingFromQueue && item != null) {
			graph.queue.remove(item.key());
		}
		upcomingQueuedInPlayer = false;
		StreamInfo stream = next.stream;
		if (stream.kind == StreamInfo.Kind.TORRENT && stream.stream.infoHash != null && graph.torrentEngine()
				.promote(stream.stream.infoHash, graph.prefs.torrentClearOnExit(), torrentListener())) {
			// the old torrent is stopped, the new one carries on where the prefetch got to
			graph.main.removeCallbacks(torrentStartCheck);
			torrentStatus = null;
			usingTorrent = true;
		} else {
			stopTorrent();
		}
		e.adoptNext();
		if (!next.metaId.equals(previous.metaId)) {
			e.resetTracks();
		}
		session = next;
		autoTriedNext = false;
		showSession(next);
		long resume = next.meta != null ? graph.history.resumePosition(next.videoId) : 0;
		if (resume > 0) {
			e.player().seekTo(resume);
		}
		notifyListeners();
	}

	/** saveProgress can't, the player is already on the next item */
	private void markFinished(PlaySession s) {
		if (s.meta == null) {
			return;
		}
		if (lastDurationMs > 0) {
			HistoryEntry entry = entryFor(s);
			entry.positionMs = lastDurationMs;
			entry.durationMs = lastDurationMs;
			graph.history.save(entry);
		}
		graph.history.setWatched(s.videoId, true);
	}

	/** where it was left at the last tick, the player is already on the next item */
	private void saveSkipped(PlaySession s) {
		if (s.meta == null || lastDurationMs <= 0) {
			return;
		}
		HistoryEntry entry = entryFor(s);
		entry.positionMs = lastPositionMs;
		entry.durationMs = lastDurationMs;
		graph.history.save(entry);
	}

	private void playUpcoming() {
		if (!upcomingDone) {
			waitingForUpcoming = true;
			return;
		}
		waitingForUpcoming = false;
		PlaySession next = upcomingSession;
		QueueItem item = upcoming;
		if (upcomingFromQueue && item != null) {
			// tried once, a title nothing can play shouldn't block the rest of the queue
			graph.queue.remove(item.key());
		}
		if (next == null) {
			showLoading(null);
			showUpNext(null, 0);
			showError(context.getString(R.string.player_no_next_stream), false);
			return;
		}
		play(next);
	}

	private void loadAddonSubtitles() {
		addonSubtitles.clear();
		subtitleRequests = 0;
		int generation = ++subtitleGeneration;
		PlaySession s = session;
		if (s == null || s.meta == null) {
			return;
		}
		String type = s.type;
		String videoId = s.videoId;
		Map<String, String> extras = new HashMap<>();
		StreamInfo stream = s.stream;
		if (stream.fileName != null) {
			extras.put("filename", stream.fileName);
		}
		if (stream.sizeBytes > 0) {
			extras.put("videoSize", String.valueOf(stream.sizeBytes));
		}
		for (Subtitle sub : stream.stream.subtitlesOrEmpty()) {
			if (sub.url != null && sub.url.startsWith("https://")) {
				addonSubtitles.add(addonSubtitle(sub, stream.addonName));
			}
		}
		List<InstalledAddon> sources = graph.addons.supporting(Manifest.RESOURCE_SUBTITLES, type, videoId);
		subtitleRequests = sources.size();
		for (InstalledAddon addon : sources) {
			graph.io.execute(() -> {
				List<Subtitle> result = fetchSubtitles(addon, type, videoId, extras);
				graph.main.post(() -> {
					if (generation != subtitleGeneration) {
						return;
					}
					for (Subtitle sub : result) {
						if (addonSubtitles.size() >= MAX_ADDON_SUBTITLES) {
							break;
						}
						addonSubtitles.add(addonSubtitle(sub, addon.displayName()));
					}
					subtitleRequests--;
					ExoPlayer p = exo();
					if (p != null) {
						decideSubtitles(p.getCurrentTracks());
					}
				});
			});
		}
	}

	private List<Subtitle> fetchSubtitles(InstalledAddon addon, String type, String videoId,
			Map<String, String> extras) {
		try {
			return graph.addonClient.subtitles(addon, type, videoId, extras);
		} catch (IOException | IllegalArgumentException e) {
			return Collections.emptyList();
		}
	}

	private static TrackPicker.AddonSubtitle addonSubtitle(Subtitle sub, String addonName) {
		return new TrackPicker.AddonSubtitle(sub.id != null ? sub.id : sub.url, sub.url, sub.lang, addonName);
	}

	private void showLoading(@Nullable String text) {
		loading = text;
		if (screen != null) {
			screen.onLoading(text);
		}
	}

	private void showError(@Nullable String message, boolean offerNext) {
		error = message;
		errorOffersNext = message != null && offerNext && nextCandidate() != null;
		if (screen != null) {
			screen.onError(message, errorOffersNext);
		}
	}

	private void showUpNext(@Nullable String label, long remainingMs) {
		upNextTitle = label;
		upNextRemaining = remainingMs;
		if (screen != null) {
			screen.onUpNext(label, remainingMs);
		}
	}

	private void notifyListeners() {
		for (Listener l : listeners) {
			l.onNowPlayingChanged();
		}
	}
}
