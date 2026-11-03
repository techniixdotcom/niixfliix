package app.niixfliix.desktop.player;

import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.niixfliix.desktop.platform.GLib;
import app.niixfliix.desktop.platform.Gst;
import app.niixfliix.desktop.platform.Native;
import app.niixfliix.net.Http;
import app.niixfliix.util.Logs;
import app.niixfliix.util.MainQueue;

public final class VideoPlayer {

	public enum State {
		IDLE,
		BUFFERING,
		PAUSED,
		PLAYING,
		ENDED
	}

	public enum ErrorKind {
		HTTP,
		NETWORK,
		FORMAT,
		OTHER
	}

	public interface Listener {
		void onState(@NonNull State state);

		void onError(@NonNull ErrorKind kind, int httpCode);

		void onTracksReady();

		void onVideoChanged();
	}

	public interface FrameSink {
		void frame(int width, int height, @NonNull MemorySegment bgra);
	}

	public static final class Track {
		public final int index;
		@Nullable public final String language;
		@Nullable public final String title;
		@Nullable public final String codec;

		Track(int index, @Nullable String language, @Nullable String title, @Nullable String codec) {
			this.index = index;
			this.language = language;
			this.title = title;
			this.codec = codec;
		}

		String key() {
			return language + "|" + title + "|" + codec;
		}
	}

	public static final class SubtitleStyle {
		final int sizePercent;
		final int color;
		final boolean background;

		public SubtitleStyle(int sizePercent, int color, boolean background) {
			this.sizePercent = sizePercent;
			this.color = color;
			this.background = background;
		}
	}

	private static final String TAG = "VideoPlayer";
	private static final int PLAY_FLAG_TEXT = 0x4;
	private static final int PLAY_FLAG_DEINTERLACE = 0x200;
	private static final int PLAY_FLAG_SOFT_COLORBALANCE = 0x400;
	private static final int PLAY_FLAG_FORCE_SW_DECODERS = 0x1000;
	private static final long BUS_WAIT_NS = 100_000_000L;
	private static final long FRAME_WAIT_NS = 100_000_000L;
	private static final long JOIN_MS = 3000;
	private static final int MAX_EXTERNAL_CHECKS = 100;
	private static final float SUBTITLE_POINTS = 14.4f;
	private static final Pattern HTTP_CODE = Pattern.compile("\\b([45]\\d\\d)\\b");
	private static final int LOCAL_READ_TIMEOUT_S = 300;
	private static final String LOCAL_PREFIX = "http://127.0.0.1:";
	private static final Map<Long, Pipeline> PIPELINES = new ConcurrentHashMap<>();
	private static MemorySegment sourceSetup;

	private final MainQueue main;
	private final Listener listener;
	private final FrameSink frames;
	private final boolean gtkSink;
	@Nullable private Pipeline current;
	private double volume = 1;
	private boolean muted;
	private boolean hardware;
	private SubtitleStyle style = new SubtitleStyle(100, 0xFFFFFFFF, false);

	public VideoPlayer(@NonNull MainQueue main, @NonNull Listener listener, @NonNull FrameSink frames) {
		Gst.init();
		callbacks();
		this.main = main;
		this.listener = listener;
		this.frames = frames;
		gtkSink = Gst.has("gtk4paintablesink");
		if (!gtkSink) {
			Logs.w(TAG, "GStreamer has no gtk4paintablesink: frames are copied to the screen by the CPU", null);
		}
	}

	public void setHardwareDecoding(boolean on) {
		hardware = on;
	}

	@Nullable
	public MemorySegment paintable() {
		Pipeline p = current;
		return p != null ? p.paintable : null;
	}

	public boolean isHardwareDecoding() {
		Pipeline p = current;
		return p != null && p.hardware;
	}

	public void open(@NonNull String uri, @NonNull Map<String, String> headers, long startMs, boolean play) {
		start(new Pipeline(uri, headers, null, startMs, play, hardware));
	}

	private void start(Pipeline p) {
		close();
		current = p;
		listener.onVideoChanged();
		p.start();
	}

	@Nullable
	public String uri() {
		return current != null ? current.uri : null;
	}

	public void play() {
		if (current != null) {
			current.setTarget(true);
		}
	}

	public void pause() {
		if (current != null) {
			current.setTarget(false);
		}
	}

	public boolean isPlaying() {
		return current != null && current.wantPlaying;
	}

	@NonNull
	public State state() {
		return current != null ? current.state : State.IDLE;
	}

	public void seekTo(long ms) {
		if (current != null) {
			current.seek(Math.max(0, ms));
		}
	}

	public long positionMs() {
		Pipeline p = current;
		if (p == null) {
			return 0;
		}
		if (p.pendingSeekMs >= 0) {
			return p.pendingSeekMs;
		}
		long ns = Gst.position(p.playbin);
		return ns >= 0 ? ns / 1_000_000 : p.lastPositionMs;
	}

	public long durationMs() {
		Pipeline p = current;
		if (p == null) {
			return 0;
		}
		long ns = Gst.duration(p.playbin);
		return ns > 0 ? ns / 1_000_000 : 0;
	}

	public void setVolume(double value) {
		volume = Math.max(0, Math.min(1, value));
		if (current != null) {
			GLib.set(current.playbin, "volume", volume);
		}
	}

	public double volume() {
		return volume;
	}

	public void setMuted(boolean mute) {
		muted = mute;
		if (current != null) {
			GLib.set(current.playbin, "mute", mute);
		}
	}

	public boolean isMuted() {
		return muted;
	}

	@NonNull
	public List<Track> audioTracks() {
		return tracks("n-audio", "get-audio-tags", "audio-codec");
	}

	@NonNull
	public List<Track> textTracks() {
		return tracks("n-text", "get-text-tags", "subtitle-codec");
	}

	public int currentAudio() {
		return current != null ? GLib.getInt(current.playbin, "current-audio") : -1;
	}

	public int currentText() {
		if (current == null || (GLib.getInt(current.playbin, "flags") & PLAY_FLAG_TEXT) == 0) {
			return -1;
		}
		return GLib.getInt(current.playbin, "current-text");
	}

	public void selectAudio(int index) {
		if (current != null) {
			GLib.set(current.playbin, "current-audio", index);
		}
	}

	public void selectText(int index) {
		Pipeline p = current;
		if (p == null) {
			return;
		}
		int flags = GLib.getInt(p.playbin, "flags");
		if (index < 0) {
			GLib.set(p.playbin, "flags", flags & ~PLAY_FLAG_TEXT);
			return;
		}
		GLib.set(p.playbin, "current-text", index);
		GLib.set(p.playbin, "flags", flags | PLAY_FLAG_TEXT);
	}

	@Nullable
	public String subtitleUri() {
		return current != null ? current.subtitleUri : null;
	}

	public void setSubtitleUri(@Nullable String uri) {
		Pipeline p = current;
		if (p == null) {
			return;
		}
		List<Track> own = new ArrayList<>();
		for (Track t : textTracks()) {
			if (t.index != p.externalText) {
				own.add(t);
			}
		}
		Pipeline next = new Pipeline(p.uri, p.headers, uri, positionMs(), p.wantPlaying, p.hardware);
		next.restoreAudio = currentAudio();
		next.ownTracks = own;
		start(next);
	}

	public int externalText() {
		return current != null ? current.externalText : -1;
	}

	public void setSubtitleStyle(@NonNull SubtitleStyle newStyle) {
		style = newStyle;
	}

	public void stop() {
		close();
	}

	private void close() {
		Pipeline p = current;
		current = null;
		if (p != null) {
			p.dispose();
			listener.onVideoChanged();
		}
	}

	private List<Track> tracks(String countProperty, String tagSignal, String codecTag) {
		Pipeline p = current;
		return p != null ? tracks(countProperty, tagSignal, codecTag, p) : Collections.emptyList();
	}

	private static List<Track> tracks(String countProperty, String tagSignal, String codecTag, Pipeline p) {
		int count = GLib.getInt(p.playbin, countProperty);
		List<Track> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			MemorySegment tags = GLib.emit(p.playbin, tagSignal, i);
			try {
				out.add(new Track(i, Gst.tag(tags, "language-code"), Gst.tag(tags, "title"), Gst.tag(tags, codecTag)));
			} finally {
				Gst.unref(tags);
			}
		}
		return out;
	}

	private void report(Pipeline p, State state) {
		main.post(() -> {
			if (p == current) {
				listener.onState(state);
			}
		});
	}

	private void reportError(Pipeline p, String text) {
		ErrorKind kind = kindOf(text);
		Matcher m = HTTP_CODE.matcher(text);
		int code = kind == ErrorKind.HTTP && m.find() ? Integer.parseInt(m.group(1)) : 0;
		Logs.w(TAG, "Playback failed: " + text, null);
		main.post(() -> {
			if (p != current) {
				return;
			}
			if (p.hardware && kind != ErrorKind.HTTP && kind != ErrorKind.NETWORK) {
				Logs.w(TAG, "Trying again without hardware decoding", null);
				Pipeline again = new Pipeline(p.uri, p.headers, p.subtitleUri, p.lastPositionMs, p.wantPlaying, false);
				again.restoreAudio = p.restoreAudio;
				again.ownTracks = p.ownTracks;
				start(again);
				return;
			}
			listener.onError(kind, code);
		});
	}

	static int extraTrack(@NonNull List<Track> before, @NonNull List<Track> now) {
		if (now.size() != before.size() + 1) {
			return now.isEmpty() ? -1 : now.size() - 1;
		}
		for (int skip = 0; skip < now.size(); skip++) {
			boolean same = true;
			for (int i = 0, j = 0; i < now.size() && same; i++) {
				if (i == skip) {
					continue;
				}
				same = now.get(i).key().equals(before.get(j++).key());
			}
			if (same) {
				return skip;
			}
		}
		return now.size() - 1;
	}

	@NonNull
	static ErrorKind kindOf(@NonNull String text) {
		String t = text.toLowerCase(Locale.ROOT);
		boolean fromHttp = t.contains("souphttpsrc");
		if (fromHttp && HTTP_CODE.matcher(t).find()) {
			return ErrorKind.HTTP;
		}
		if (fromHttp || t.contains("resolve") || t.contains("connection") || t.contains("timed out")
				|| t.contains("could not read from resource")) {
			return ErrorKind.NETWORK;
		}
		if (t.contains("decoder") || t.contains("missing plugin") || t.contains("not-negotiated")
				|| t.contains("no suitable plugins") || t.contains("codec") || t.contains("demux")
				|| t.contains("type was found") || t.contains("not supported")) {
			return ErrorKind.FORMAT;
		}
		return ErrorKind.OTHER;
	}

	private static synchronized void callbacks() {
		if (sourceSetup != null) {
			return;
		}
		try {
			MethodType type = MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class,
					MemorySegment.class);
			sourceSetup = Native.callback(MethodHandles.lookup().findStatic(VideoPlayer.class, "onSourceSetup", type),
					null, Arena.global(), PTR, PTR, PTR);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unused")
	private static void onSourceSetup(MemorySegment playbin, MemorySegment source, MemorySegment data) {
		try {
			Pipeline p = PIPELINES.get(playbin.address());
			if (p == null) {
				return;
			}
			if (GLib.hasProperty(source, "user-agent")) {
				GLib.set(source, "user-agent", Http.userAgent());
			}
			if (!p.headers.isEmpty() && GLib.hasProperty(source, "extra-headers")) {
				Gst.setHeaders(source, p.headers);
			}
			if (p.uri.startsWith(LOCAL_PREFIX) && GLib.hasProperty(source, "timeout")) {
				GLib.set(source, "timeout", LOCAL_READ_TIMEOUT_S);
			}
		} catch (Throwable t) {
			Logs.w(TAG, "Source setup failed", t);
		}
	}

	private static void styleTextOverlay(MemorySegment overlay, SubtitleStyle style) {
		GLib.set(overlay, "color", style.color);
		GLib.set(overlay, "shaded-background", style.background);
		GLib.set(overlay, "draw-outline", !style.background);
	}

	private final class Pipeline {
		final String uri;
		final Map<String, String> headers;
		@Nullable final String subtitleUri;
		final SubtitleStyle style;
		final boolean hardware;
		final MemorySegment playbin;
		final MemorySegment videoSink;
		@Nullable final MemorySegment paintable;
		@Nullable final MemorySegment appsink;
		final MemorySegment bus;
		volatile boolean running = true;
		volatile boolean wantPlaying;
		volatile State state = State.BUFFERING;
		volatile long pendingSeekMs;
		volatile long lastPositionMs;
		volatile int restoreAudio = -1;
		volatile List<Track> ownTracks = Collections.emptyList();
		volatile int externalText = -1;
		private boolean externalPending;
		private int externalChecks;
		volatile boolean prerolled;
		volatile boolean buffering;
		private Thread busThread;
		private Thread frameThread;

		Pipeline(String uri, Map<String, String> headers, @Nullable String subtitleUri, long startMs, boolean play,
				boolean hardware) {
			this.uri = uri;
			this.headers = headers;
			this.subtitleUri = subtitleUri;
			this.style = VideoPlayer.this.style;
			this.hardware = hardware;
			this.pendingSeekMs = startMs > 0 ? startMs : -1;
			this.lastPositionMs = Math.max(0, startMs);
			this.wantPlaying = play;
			Gst.hardwareDecoding(hardware);
			playbin = GLib.own(Gst.make("playbin", "player"));
			int flags = GLib.getInt(playbin, "flags");
			if (!hardware) {
				flags |= PLAY_FLAG_FORCE_SW_DECODERS;
			}
			if (gtkSink) {
				MemorySegment sink = GLib.own(Gst.make("gtk4paintablesink", "video"));
				paintable = GLib.getPointer(sink, "paintable");
				MemorySegment glContext = GLib.getPointer(paintable, "gl-context");
				if (!Native.isNull(glContext) && Gst.has("glsinkbin")) {
					GLib.unref(glContext);
					videoSink = GLib.own(Gst.make("glsinkbin", "video-gl"));
					GLib.setPointer(videoSink, "sink", sink);
					GLib.unref(sink);
				} else {
					GLib.unref(glContext);
					videoSink = sink;
				}
				flags &= ~(PLAY_FLAG_DEINTERLACE | PLAY_FLAG_SOFT_COLORBALANCE);
				appsink = null;
			} else {
				paintable = null;
				videoSink = GLib.own(Gst.make("appsink", "frames"));
				MemorySegment caps = Gst.caps("video/x-raw,format=BGRA");
				GLib.setPointer(videoSink, "caps", caps);
				Gst.unref(caps);
				GLib.set(videoSink, "max-buffers", 2);
				GLib.set(videoSink, "drop", true);
				GLib.set(videoSink, "enable-last-sample", false);
				appsink = videoSink;
			}
			GLib.set(playbin, "flags", flags);
			GLib.setPointer(playbin, "video-sink", videoSink);
			GLib.set(playbin, "uri", uri);
			if (subtitleUri != null) {
				GLib.set(playbin, "suburi", subtitleUri);
			}
			GLib.set(playbin, "subtitle-font-desc",
					String.format(Locale.ROOT, "Sans Bold %.1f", SUBTITLE_POINTS * style.sizePercent / 100f));
			GLib.set(playbin, "volume", volume);
			GLib.set(playbin, "mute", muted);
			if (subtitleUri == null) {
				GLib.set(playbin, "flags", GLib.getInt(playbin, "flags") & ~PLAY_FLAG_TEXT);
			}
			bus = Gst.bus(playbin);
			PIPELINES.put(playbin.address(), this);
			GLib.connect(playbin, "source-setup", sourceSetup);
		}

		void start() {
			busThread = new Thread(this::watchBus, "gst-bus");
			busThread.setDaemon(true);
			busThread.start();
			if (appsink != null) {
				frameThread = new Thread(this::pullFrames, "gst-frames");
				frameThread.setDaemon(true);
				frameThread.start();
			}
			report(this, State.BUFFERING);
			Gst.setState(playbin, Gst.STATE_PAUSED);
		}

		void setTarget(boolean play) {
			wantPlaying = play;
			if (state == State.ENDED) {
				return;
			}
			if (prerolled && !buffering) {
				Gst.setState(playbin, play ? Gst.STATE_PLAYING : Gst.STATE_PAUSED);
			}
			if (!play && state == State.PLAYING) {
				state = State.PAUSED;
				report(this, State.PAUSED);
			}
		}

		void seek(long ms) {
			lastPositionMs = ms;
			if (!prerolled) {
				pendingSeekMs = ms;
				return;
			}
			if (state == State.ENDED) {
				state = State.BUFFERING;
				report(this, State.BUFFERING);
				Gst.setState(playbin, Gst.STATE_PAUSED);
			}
			Gst.seek(playbin, ms * 1_000_000L, true);
		}

		private void watchBus() {
			int types = Gst.MESSAGE_EOS | Gst.MESSAGE_ERROR | Gst.MESSAGE_BUFFERING | Gst.MESSAGE_STATE_CHANGED
					| Gst.MESSAGE_ASYNC_DONE;
			while (running) {
				MemorySegment message = Gst.pop(bus, BUS_WAIT_NS, types);
				try {
					if (!Native.isNull(message)) {
						handle(message);
					}
					if (externalPending && running) {
						findExternalText();
					}
				} catch (RuntimeException e) {
					Logs.w(TAG, "Bus message failed", e);
				} finally {
					Gst.unref(message);
				}
			}
		}

		private void handle(MemorySegment message) {
			switch (Gst.messageType(message)) {
				case Gst.MESSAGE_ERROR:
					running = false;
					reportError(this, Gst.errorText(message));
					break;
				case Gst.MESSAGE_EOS:
					state = State.ENDED;
					report(this, State.ENDED);
					break;
				case Gst.MESSAGE_BUFFERING:
					onBuffering(Gst.bufferingPercent(message));
					break;
				case Gst.MESSAGE_ASYNC_DONE:
					onAsyncDone();
					break;
				case Gst.MESSAGE_STATE_CHANGED:
					MemorySegment source = Gst.messageSource(message);
					if (source.address() == playbin.address()) {
						onPipelineState(Gst.newState(message));
					} else if (Gst.newState(message) == Gst.STATE_READY) {
						setUp(source);
					}
					break;
				default:
					break;
			}
		}

		private void onAsyncDone() {
			if (!prerolled) {
				prerolled = true;
				styleSubtitles();
				if (restoreAudio >= 0) {
					GLib.set(playbin, "current-audio", restoreAudio);
				}
				if (subtitleUri != null) {
					externalPending = true;
					findExternalText();
				}
				main.post(() -> {
					if (this == current) {
						listener.onTracksReady();
					}
				});
			}
			long seekTo = pendingSeekMs;
			if (seekTo >= 0) {
				pendingSeekMs = -1;
				Gst.seek(playbin, seekTo * 1_000_000L, true);
				return;
			}
			if (!buffering) {
				Gst.setState(playbin, wantPlaying ? Gst.STATE_PLAYING : Gst.STATE_PAUSED);
			}
		}

		private void onBuffering(int percent) {
			boolean now = percent < 100;
			if (now == buffering) {
				return;
			}
			buffering = now;
			if (now) {
				Gst.setState(playbin, Gst.STATE_PAUSED);
				state = State.BUFFERING;
				report(this, State.BUFFERING);
			} else if (prerolled && pendingSeekMs < 0) {
				Gst.setState(playbin, wantPlaying ? Gst.STATE_PLAYING : Gst.STATE_PAUSED);
			}
		}

		private void onPipelineState(int newState) {
			if (state == State.ENDED || buffering) {
				return;
			}
			State next = newState == Gst.STATE_PLAYING ? State.PLAYING
					: newState == Gst.STATE_PAUSED && prerolled && pendingSeekMs < 0 ? State.PAUSED : null;
			if (next != null && next != state) {
				state = next;
				report(this, next);
			}
		}

		private void pullFrames() {
			MemorySegment sink = appsink;
			if (sink == null) {
				return;
			}
			while (running) {
				long started = System.nanoTime();
				MemorySegment sample = Gst.pullSample(sink, FRAME_WAIT_NS);
				if (Native.isNull(sample) && state != State.PLAYING) {
					sample = Gst.pullPreroll(sink, 0);
				}
				if (Native.isNull(sample)) {
					if (System.nanoTime() - started < 5_000_000L) {
						sleep();
					}
					continue;
				}
				try {
					int[] size = Gst.sampleSize(sample);
					if (size[0] > 0 && size[1] > 0) {
						Gst.readSample(sample, bytes -> {
							if (bytes.byteSize() >= (long) size[0] * size[1] * 4) {
								frames.frame(size[0], size[1], bytes);
							}
						});
					}
				} catch (RuntimeException e) {
					Logs.w(TAG, "Frame failed", e);
				} finally {
					Gst.unref(sample);
				}
			}
		}

		private void sleep() {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				running = false;
			}
		}

		private void findExternalText() {
			List<Track> now = tracks("n-text", "get-text-tags", "subtitle-codec", this);
			boolean arrived = now.size() > ownTracks.size();
			if (!arrived && ++externalChecks < MAX_EXTERNAL_CHECKS) {
				return;
			}
			externalPending = false;
			externalText = arrived ? extraTrack(ownTracks, now) : -1;
			if (externalText < 0) {
				Logs.w(TAG, "The subtitle file gave no subtitles", null);
				return;
			}
			GLib.set(playbin, "current-text", externalText);
			GLib.set(playbin, "flags", GLib.getInt(playbin, "flags") | PLAY_FLAG_TEXT);
			main.post(() -> {
				if (this == current) {
					listener.onTracksReady();
				}
			});
		}

		private void setUp(MemorySegment element) {
			String factory = Gst.factoryName(element);
			if ("textoverlay".equals(factory)) {
				styleTextOverlay(element, style);
			} else if (("videoconvert".equals(factory) || "videoconvertscale".equals(factory))
					&& GLib.hasProperty(element, "n-threads")) {
				GLib.set(element, "n-threads", 0);
			} else if (factory != null && factory.contains("dec") && !factory.contains("decodebin")) {
				Logs.w(TAG, "Decoder: " + factory, null);
			}
		}

		void styleSubtitles() {
			Gst.forEachByFactory(playbin, "textoverlay", overlay -> styleTextOverlay(overlay, style));
		}

		void dispose() {
			running = false;
			boolean busDone = join(busThread);
			Gst.setState(playbin, Gst.STATE_NULL);
			boolean framesDone = join(frameThread);
			PIPELINES.remove(playbin.address());
			if (!busDone || !framesDone) {
				Logs.w(TAG, "GStreamer didn't stop in time; its pipeline is left as it is", null);
				return;
			}
			GLib.unref(bus);
			GLib.unref(videoSink);
			GLib.unref(playbin);
			if (paintable != null) {
				GLib.unref(paintable);
			}
		}

		private boolean join(@Nullable Thread t) {
			if (t == null) {
				return true;
			}
			try {
				t.join(JOIN_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return !t.isAlive();
		}
	}
}
