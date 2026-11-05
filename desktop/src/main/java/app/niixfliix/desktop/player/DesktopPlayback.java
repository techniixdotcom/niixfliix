package app.niixfliix.desktop.player;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.Subtitle;
import app.niixfliix.addon.model.Video;
import app.niixfliix.app.Constant;
import app.niixfliix.app.Core;
import app.niixfliix.data.Prefs;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.QueueItem;
import app.niixfliix.desktop.DesktopGraph;
import app.niixfliix.desktop.i18n.I18n;
import app.niixfliix.desktop.platform.HardwareGuard;
import app.niixfliix.desktop.platform.Portal;
import app.niixfliix.desktop.ui.Fmt;
import app.niixfliix.desktop.ui.Toast;
import app.niixfliix.player.PlaySession;
import app.niixfliix.player.PlayerPreferences;
import app.niixfliix.player.QueueResolver;
import app.niixfliix.torrent.TorrentEngine;
import app.niixfliix.util.Logs;

@MainThread
public final class DesktopPlayback implements VideoPlayer.Listener {

	public interface Screen {
		void onTitle(@NonNull String title, @NonNull String subtitle);

		void onLoading(@Nullable String text);

		void onError(@Nullable String message, boolean offerNext);

		void onUpNext(@Nullable String title, long remainingMs);

		void onVideoChanged();

		void onClosed();
	}

	public interface Listener {
		void onNowPlayingChanged();
	}

	private static final String TAG = "Playback";
	private static final long TICK_MS = 1000;
	private static final long SAVE_EVERY_MS = 10_000;
	private static final long MIN_DURATION_FOR_UP_NEXT_MS = 10 * 60 * 1000;
	private static final int MAX_ADDON_SUBTITLES = 200;
	private static final long TORRENT_START_TIMEOUT_MS = 45_000;
	private static final long END_SLACK_MS = 1500;
	private static final int STUCK_AT_END_TICKS = 3;

	private final DesktopGraph graph;
	private final Runnable tick = this::tick;
	private final Runnable torrentStartCheck = this::checkTorrentStarted;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private final List<AddonSubtitle> addonSubtitles = new ArrayList<>();
	private final FrameRelay frames = new FrameRelay();

	@Nullable private VideoPlayer player;
	@Nullable private PlaySession session;
	@Nullable private Screen screen;
	@Nullable private String inhibitHandle;

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
	private int stuckAtEndTicks;
	private boolean ended;
	private int subtitleGeneration;
	private int subtitleRequests;
	private boolean subtitlesDecided;
	private boolean userPicked;
	@Nullable private String pickedAudioLanguage;
	@Nullable private String pickedTextLanguage;
	private boolean pickedTextOff;

	@Nullable private QueueItem upcoming;
	private boolean upcomingFromQueue;
	@Nullable private QueueResolver upcomingResolver;
	@Nullable private PlaySession upcomingSession;
	private boolean upcomingDone;
	private boolean waitingForUpcoming;

	public DesktopPlayback(@NonNull DesktopGraph graph) {
		this.graph = graph;
	}

	public void play(@NonNull PlaySession next) {
		if (player == null) {
			player = new VideoPlayer(graph.main, this, frames);
			player.setVolume(graph.desktopPrefs.volume() / 100.0);
			graph.main.postDelayed(tick, TICK_MS);
		} else if (session != null) {
			saveProgress();
			stopTorrent();
		}
		if (session == null || !next.metaId.equals(session.metaId)) {
			userPicked = false;
			pickedAudioLanguage = null;
			pickedTextLanguage = null;
			pickedTextOff = false;
		}
		session = next;
		autoTriedNext = false;
		applySubtitleStyle();
		startCurrent(next.meta != null ? graph.history.resumePosition(next.videoId) : 0);
		notifyListeners();
	}

	public void stop() {
		if (player == null) {
			return;
		}
		saveProgress();
		graph.main.removeCallbacks(tick);
		resetUpcoming();
		graph.torrentEngine().dropPrefetch();
		stopTorrent();
		player.stop();
		player = null;
		HardwareGuard.end();
		session = null;
		addonSubtitles.clear();
		keepScreenOn(false);
		Screen s = screen;
		screen = null;
		if (s != null) {
			s.onClosed();
		}
		notifyListeners();
	}

	@Nullable
	public VideoPlayer player() {
		return player;
	}

	@Nullable
	public PlaySession session() {
		return session;
	}

	@NonNull
	public List<AddonSubtitle> addonSubtitles() {
		return Collections.unmodifiableList(addonSubtitles);
	}

	public void setFrameSink(@Nullable VideoPlayer.FrameSink sink) {
		frames.target = sink;
	}

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

	public boolean inBackground() {
		return player != null && screen == null;
	}

	public void addListener(@NonNull Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(@NonNull Listener listener) {
		listeners.remove(listener);
	}

	@NonNull
	public String title() {
		return title;
	}

	@NonNull
	public String subtitle() {
		return subtitle;
	}

	public void togglePlay() {
		VideoPlayer p = player;
		if (p == null) {
			return;
		}
		if (p.isPlaying()) {
			p.pause();
			saveProgress();
		} else {
			p.play();
		}
		notifyListeners();
	}

	public void seekBy(long deltaMs) {
		VideoPlayer p = player;
		if (p != null) {
			long duration = p.durationMs();
			long target = p.positionMs() + deltaMs;
			p.seekTo(duration > 0 ? Math.min(target, duration - 1000) : target);
		}
	}

	public void setVolume(int percent) {
		int clamped = Math.max(0, Math.min(100, percent));
		graph.desktopPrefs.setVolume(clamped);
		if (player != null) {
			player.setVolume(clamped / 100.0);
		}
	}

	private void switchTo(@NonNull StreamInfo s) {
		VideoPlayer p = player;
		if (session == null || p == null) {
			return;
		}
		long position = p.positionMs();
		saveProgress();
		stopTorrent();
		session.stream = s;
		startCurrent(position);
	}

	public void pick(@NonNull StreamInfo s) {
		PlaySession current = session;
		if (current != null && Meta.TYPE_SERIES.equals(current.type)) {
			graph.playerPrefs.setSeriesQuality(current.metaId, s.quality);
		}
		autoTriedNext = false;
		switchTo(s);
	}

	public void tryNextStream() {
		StreamInfo next = nextCandidate();
		if (next != null) {
			switchTo(next);
		}
	}

	public void cancelUpNext() {
		upNextCancelled = true;
		showUpNext(null, 0);
		resetUpcoming();
		graph.torrentEngine().dropPrefetch();
	}

	public void playNextNow() {
		if (prefetchUpcoming() != null) {
			showLoading(I18n.t("queue_finding"));
			playUpcoming();
		}
	}

	public boolean hasNext() {
		return session != null && (upcoming != null || findUpcoming() != null);
	}

	public void queueChanged() {
		notifyListeners();
	}

	public void selectAudio(@NonNull VideoPlayer.Track track) {
		if (player == null) {
			return;
		}
		player.selectAudio(track.index);
		userPicked = true;
		pickedAudioLanguage = LanguageCodes.normalize(track.language);
	}

	public void selectText(@NonNull VideoPlayer.Track track) {
		if (player == null) {
			return;
		}
		userPicked = true;
		pickedTextOff = false;
		pickedTextLanguage = LanguageCodes.normalize(track.language);
		player.selectText(track.index);
	}

	public void subtitlesOff() {
		if (player == null) {
			return;
		}
		userPicked = true;
		pickedTextOff = true;
		pickedTextLanguage = null;
		player.selectText(-1);
	}

	public void selectAddonSubtitle(@NonNull AddonSubtitle s) {
		if (player == null) {
			return;
		}
		userPicked = true;
		pickedTextOff = false;
		pickedTextLanguage = LanguageCodes.normalize(s.language);
		player.setSubtitleUri(s.url);
	}

	public void selectLocalSubtitle(@NonNull File file) {
		if (player == null) {
			return;
		}
		userPicked = true;
		pickedTextOff = false;
		player.setSubtitleUri(file.toURI().toString());
	}

	public void applySubtitleStyle() {
		PlayerPreferences prefs = graph.playerPrefs;
		int color;
		switch (prefs.subtitleColor()) {
			case YELLOW:
				color = 0xFFFFFF00;
				break;
			case CYAN:
				color = 0xFF00FFFF;
				break;
			case WHITE:
			default:
				color = 0xFFFFFFFF;
				break;
		}
		boolean background = prefs.subtitleBackground() != PlayerPreferences.SubtitleBackground.NONE;
		if (player != null) {
			player.setSubtitleStyle(new VideoPlayer.SubtitleStyle(prefs.subtitleSize(), color, background));
		}
	}

	private void startCurrent(long startMs) {
		PlaySession s = session;
		VideoPlayer p = player;
		if (s == null || p == null) {
			return;
		}
		showSession(s);
		StreamInfo stream = s.stream;
		if (stream.kind == StreamInfo.Kind.TORRENT && stream.stream.infoHash != null) {
			startTorrent(stream, startMs);
		} else if (stream.stream.url != null) {
			graph.torrentEngine().dropPrefetch();
			showLoading(I18n.t("loading"));
			open(p, stream.stream.url, stream.stream.requestHeaders(), startMs);
		}
	}

	private void open(VideoPlayer p, String url, Map<String, String> headers, long startMs) {
		boolean hardware = graph.desktopPrefs.hardwareDecoding();
		p.setHardwareDecoding(hardware);
		if (hardware) {
			HardwareGuard.begin();
		} else {
			HardwareGuard.end();
		}
		p.open(url, headers, startMs, true);
	}

	private void showSession(PlaySession s) {
		subtitlesDecided = false;
		showError(null, false);
		upNextCancelled = false;
		ended = false;
		stuckAtEndTicks = 0;
		showUpNext(null, 0);
		resetUpcoming();
		lastSave = System.currentTimeMillis();
		Video video = s.video;
		title = s.title;
		subtitle = video != null ? Fmt.episode(video) : s.stream.quality.shortName();
		if (screen != null) {
			screen.onTitle(title, subtitle);
		}
		loadAddonSubtitles();
		notifyListeners();
	}

	private void startTorrent(StreamInfo s, long startMs) {
		VideoPlayer p = player;
		if (p == null) {
			return;
		}
		p.stop();
		usingTorrent = true;
		pendingStartMs = startMs;
		torrentStatus = null;
		showLoading(I18n.t("torrent_connecting"));
		graph.main.removeCallbacks(torrentStartCheck);
		graph.main.postDelayed(torrentStartCheck, TORRENT_START_TIMEOUT_MS);
		graph.torrentEngine().play(Core.magnetFor(s), s.stream.fileIdx,
				Prefs.CLEAR_TORRENTS_AFTER_PLAYBACK, torrentListener());
		PlaySession playing = session;
		StreamInfo rival = playing != null ? StreamRanker.raceCandidate(playing.streams, s) : null;
		if (rival != null) {
			graph.torrentEngine().race(Core.magnetFor(rival), rival.stream.fileIdx);
		}
	}

	private TorrentEngine.Listener torrentListener() {
		return new TorrentEngine.Listener() {
			@Override
			public void onStatus(@NonNull TorrentEngine.Status status) {
				onTorrentStatus(status);
			}

			@Override
			public void onReady(@NonNull String url, @NonNull String fileName) {
				if (player != null) {
					open(player, url, new HashMap<>(), pendingStartMs);
				}
			}

			@Override
			public void onSwitched(@NonNull String infoHash) {
				PlaySession playing = session;
				if (playing != null) {
					playing.adoptTorrent(infoHash);
				}
			}

			@Override
			public void onError(@NonNull TorrentEngine.Error error, long fileSize) {
				Logs.w(TAG, "Torrent stopped: " + error, null);
				showLoading(null);
				boolean streamProblem = error == TorrentEngine.Error.FAILED
						|| error == TorrentEngine.Error.NO_VIDEO_FILE;
				if (!streamProblem || !skipToNextStream()) {
					showError(torrentMessage(error, fileSize), true);
				}
			}
		};
	}

	private static TorrentEngine.Listener prefetchListener() {
		return new TorrentEngine.Listener() {
			@Override
			public void onStatus(@NonNull TorrentEngine.Status status) {
			}

			@Override
			public void onReady(@NonNull String url, @NonNull String fileName) {
			}

			@Override
			public void onError(@NonNull TorrentEngine.Error error, long fileSize) {
			}
		};
	}

	private void stopTorrent() {
		graph.main.removeCallbacks(torrentStartCheck);
		torrentStatus = null;
		if (usingTorrent) {
			usingTorrent = false;
			graph.torrentEngine().stop(Prefs.CLEAR_TORRENTS_AFTER_PLAYBACK);
		}
	}

	private void onTorrentStatus(TorrentEngine.Status status) {
		torrentStatus = status;
		VideoPlayer p = player;
		boolean buffering = status.phase != TorrentEngine.Phase.PLAYING
				|| p != null && p.state() == VideoPlayer.State.BUFFERING;
		if (!buffering) {
			showLoading(null);
			return;
		}
		String speed = Fmt.speed(status.bytesPerSecond);
		String peers = I18n.plural("torrent_peers", status.peers, status.peers);
		switch (status.phase) {
			case METADATA:
				showLoading(I18n.t("torrent_finding", peers, speed));
				break;
			case BUFFERING:
				showLoading(I18n.t("torrent_buffering", status.bufferedPercent, peers, speed));
				break;
			case PLAYING:
			default:
				showLoading(I18n.t("torrent_playing", peers, speed));
				break;
		}
	}

	private void checkTorrentStarted() {
		VideoPlayer p = player;
		if (!usingTorrent || p == null || p.state() == VideoPlayer.State.PLAYING
				|| p.state() == VideoPlayer.State.PAUSED) {
			return;
		}
		TorrentEngine.Status status = torrentStatus;
		boolean moving = status != null && status.peers > 0 && status.phase != TorrentEngine.Phase.METADATA
				|| graph.torrentEngine().isRivalMoving();
		if (moving) {
			graph.main.postDelayed(torrentStartCheck, TORRENT_START_TIMEOUT_MS);
			return;
		}
		Logs.w(TAG, "Torrent didn't start in time: " + (status == null ? "no status"
				: status.peers + " peers, " + status.phase), null);
		if (!skipToNextStream()) {
			showLoading(null);
			showError(I18n.t("torrent_error_failed"), false);
		}
	}

	private String torrentMessage(TorrentEngine.Error error, long fileSize) {
		switch (error) {
			case NO_VIDEO_FILE:
				return I18n.t("torrent_error_no_video");
			case NO_SPACE:
				return I18n.t("torrent_error_no_space", Fmt.size(fileSize));
			case NOT_ON_WIFI:
				return I18n.t("torrent_error_wifi");
			case FAILED:
			default:
				return I18n.t("torrent_error_failed");
		}
	}

	@Override
	public void onState(@NonNull VideoPlayer.State state) {
		switch (state) {
			case ENDED:
				onEnded();
				break;
			case PLAYING:
				keepScreenOn(true);
				if (usingTorrent) {
					graph.main.removeCallbacks(torrentStartCheck);
				}
				showLoading(null);
				break;
			case PAUSED:
				keepScreenOn(false);
				showLoading(null);
				break;
			case BUFFERING:
				if (!usingTorrent) {
					showLoading(I18n.t("loading"));
				}
				break;
			default:
				break;
		}
		notifyListeners();
	}

	@Override
	public void onError(@NonNull VideoPlayer.ErrorKind kind, int httpCode) {
		PlaySession s = session;
		if (s == null) {
			return;
		}
		saveProgress();
		showLoading(null);
		StreamInfo next = StreamRanker.nextSameQuality(s.streams, s.stream);
		if (!autoTriedNext && next != null) {
			autoTriedNext = true;
			Toast.show(I18n.t("player_trying_next"));
			switchTo(next);
			return;
		}
		showError(errorMessage(kind, httpCode), true);
	}

	@Override
	public void onVideoChanged() {
		if (screen != null) {
			screen.onVideoChanged();
		}
	}

	@Override
	public void onTracksReady() {
		VideoPlayer p = player;
		if (p == null) {
			return;
		}
		String wanted = pickedAudioLanguage != null ? pickedAudioLanguage : graph.playerPrefs.language();
		for (VideoPlayer.Track t : p.audioTracks()) {
			if (LanguageCodes.matches(t.language, wanted)) {
				if (t.index != p.currentAudio()) {
					p.selectAudio(t.index);
				}
				break;
			}
		}
		decideSubtitles();
	}

	private static String errorMessage(VideoPlayer.ErrorKind kind, int httpCode) {
		switch (kind) {
			case HTTP:
				return httpCode > 0 ? I18n.t("player_error_http", httpCode) : I18n.t("player_error_generic");
			case NETWORK:
				return I18n.t("player_error_network");
			case FORMAT:
				return I18n.t("player_error_format");
			case OTHER:
			default:
				return I18n.t("player_error_generic");
		}
	}

	private void decideSubtitles() {
		VideoPlayer p = player;
		if (subtitlesDecided || p == null || p.externalText() >= 0) {
			subtitlesDecided = true;
			return;
		}
		if (userPicked) {
			subtitlesDecided = true;
			if (!pickedTextOff && pickedTextLanguage != null) {
				useSubtitles(pickedTextLanguage);
			}
			return;
		}
		String wanted = graph.playerPrefs.language();
		List<VideoPlayer.Track> audio = p.audioTracks();
		boolean audioFits = false;
		boolean untagged = true;
		for (VideoPlayer.Track t : audio) {
			audioFits |= LanguageCodes.matches(t.language, wanted);
			untagged &= LanguageCodes.normalize(t.language) == null;
		}
		if (audioFits || untagged && "en".equals(wanted) || useSubtitles(wanted)) {
			subtitlesDecided = true;
			return;
		}
		if (subtitleRequests > 0) {
			return;
		}
		subtitlesDecided = true;
		if (!"en".equals(wanted)) {
			useSubtitles("en");
		}
	}

	private boolean useSubtitles(String language) {
		VideoPlayer p = player;
		if (p == null) {
			return false;
		}
		for (VideoPlayer.Track t : p.textTracks()) {
			if (LanguageCodes.matches(t.language, language)) {
				p.selectText(t.index);
				return true;
			}
		}
		for (AddonSubtitle s : addonSubtitles) {
			if (LanguageCodes.matches(s.language, language)) {
				p.setSubtitleUri(s.url);
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

	private boolean skipToNextStream() {
		StreamInfo next = nextCandidate();
		if (next == null) {
			return false;
		}
		Toast.show(I18n.t("player_trying_next"));
		switchTo(next);
		return true;
	}

	private void tick() {
		graph.main.postDelayed(tick, TICK_MS);
		VideoPlayer p = player;
		if (p == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (p.state() == VideoPlayer.State.PLAYING && now - lastSave >= SAVE_EVERY_MS) {
			lastSave = now;
			saveProgress();
		}
		long duration = p.durationMs();
		if (duration > 0) {
			boolean stuckAtEnd = p.state() == VideoPlayer.State.BUFFERING
					&& duration - p.positionMs() <= END_SLACK_MS;
			stuckAtEndTicks = stuckAtEnd ? stuckAtEndTicks + 1 : 0;
			if (stuckAtEndTicks >= STUCK_AT_END_TICKS) {
				onEnded();
			}
		}
		checkUpNext(p);
	}

	private void saveProgress() {
		PlaySession s = session;
		VideoPlayer p = player;
		if (s == null || s.meta == null || p == null) {
			return;
		}
		long duration = p.durationMs();
		if (duration <= 0) {
			return;
		}
		long position = ended || p.state() == VideoPlayer.State.ENDED ? duration : p.positionMs();
		HistoryEntry entry = new HistoryEntry();
		entry.metaId = s.metaId;
		entry.type = s.type;
		entry.name = s.title;
		entry.poster = s.poster;
		entry.videoId = s.videoId;
		entry.positionMs = position;
		entry.durationMs = duration;
		graph.history.save(entry);
	}

	private void checkUpNext(VideoPlayer p) {
		long duration = p.durationMs();
		if (upNextCancelled || ended || duration < MIN_DURATION_FOR_UP_NEXT_MS) {
			return;
		}
		long remaining = Math.max(0, duration - p.positionMs());
		QueueItem coming = upcoming != null ? upcoming : findUpcoming();
		boolean fromQueue = upcoming != null ? upcomingFromQueue : coming != null && coming == graph.queue.peek();
		long lead = (fromQueue ? Constant.UP_NEXT_QUEUE_SECONDS : Constant.UP_NEXT_EPISODE_SECONDS) * 1000L;
		if (coming == null || remaining > lead) {
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
		episode.episode = Fmt.episode(next);
		return episode;
	}

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
		upcomingResolver = QueueResolver.start(true, next, upcomingFromQueue ? null : s.meta,
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
		keepScreenOn(false);
		if (upcoming == null && findUpcoming() == null) {
			graph.main.post(this::stop);
			return;
		}
		if (upNextCancelled) {
			return;
		}
		showUpNext(upNextLabel(prefetchUpcoming()), 0);
		playUpcoming();
	}

	private void preload(PlaySession next) {
		StreamInfo s = next.stream;
		if (upNextCancelled || waitingForUpcoming || s.kind != StreamInfo.Kind.TORRENT
				|| s.stream.infoHash == null) {
			return;
		}
		graph.torrentEngine().prefetch(Core.magnetFor(s), s.stream.fileIdx, prefetchListener());
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
			graph.queue.remove(item.key());
		}
		if (next == null) {
			showLoading(null);
			showUpNext(null, 0);
			showError(I18n.t("player_no_next_stream"), false);
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
				addonSubtitles.add(new AddonSubtitle(sub.url, sub.lang, stream.addonName));
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
						if (sub.url != null && sub.url.startsWith("https://")) {
							addonSubtitles.add(new AddonSubtitle(sub.url, sub.lang, addon.displayName()));
						}
					}
					subtitleRequests--;
					if (player != null && player.state() != VideoPlayer.State.BUFFERING) {
						decideSubtitles();
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

	private void keepScreenOn(boolean on) {
		if (on && inhibitHandle == null) {
			String reason = I18n.t("player_keeps_screen_on");
			graph.io.execute(() -> {
				String handle = Portal.inhibitIdle(reason);
				graph.main.post(() -> {
					if (handle != null && inhibitHandle == null && player != null && player.isPlaying()) {
						inhibitHandle = handle;
					} else if (handle != null) {
						graph.io.execute(() -> Portal.release(handle));
					}
				});
			});
		} else if (!on && inhibitHandle != null) {
			String handle = inhibitHandle;
			inhibitHandle = null;
			graph.io.execute(() -> Portal.release(handle));
		}
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

	private static final class FrameRelay implements VideoPlayer.FrameSink {
		@Nullable volatile VideoPlayer.FrameSink target;

		@Override
		public void frame(int width, int height, @NonNull MemorySegment bgra) {
			VideoPlayer.FrameSink t = target;
			if (t != null) {
				t.frame(width, height, bgra);
			}
		}
	}
}
