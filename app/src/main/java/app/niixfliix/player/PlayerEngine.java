package app.niixfliix.player;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.SubtitleView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.niixfliix.app.Constant;
import app.niixfliix.net.Http;
import okhttp3.OkHttpClient;

/** ExoPlayer setup. Add-on headers only go to the stream host. */
@OptIn(markerClass = UnstableApi.class)
public final class PlayerEngine {

	public static final class SideSubtitle {
		@NonNull public final String id;
		@NonNull public final Uri uri;
		@Nullable public final String language;
		@NonNull public final String label;
		@NonNull public final String mimeType;

		public SideSubtitle(@NonNull String id, @NonNull Uri uri, @Nullable String language, @NonNull String label,
				@NonNull String mimeType) {
			this.id = id;
			this.uri = uri;
			this.language = language;
			this.label = label;
			this.mimeType = mimeType;
		}
	}

	/**
	 * Most torrent/debrid releases are MKV with AC3, E-AC3, DTS or TrueHD sound, which most phones have no
	 * decoder for, so the video played silently. The phone's own decoders go first, FFmpeg takes whatever
	 * they can't. Added directly rather than through Media3's reflection lookup so R8 can't strip it.
	 */
	private static final class Renderers extends DefaultRenderersFactory {

		Renderers(Context context) {
			super(context);
			setEnableDecoderFallback(true);
		}

		@Override
		protected void buildAudioRenderers(@NonNull Context context, int extensionRendererMode,
				@NonNull MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback,
				@NonNull AudioSink audioSink, @NonNull Handler eventHandler,
				@NonNull AudioRendererEventListener eventListener, @NonNull ArrayList<Renderer> out) {
			super.buildAudioRenderers(context, EXTENSION_RENDERER_MODE_OFF, mediaCodecSelector,
					enableDecoderFallback, audioSink, eventHandler, eventListener, out);
			out.add(new FfmpegAudioRenderer(eventHandler, eventListener, audioSink));
		}
	}

	private static final int START_BUFFER_MS = 1000;
	private static final int REBUFFER_MS = 3000;

	private final ExoPlayer player;
	private final PlayerPreferences prefs;
	/** add-on headers, each only ever sent to the host of the stream it came with */
	private final Map<String, Map<String, String>> headersByHost = new ConcurrentHashMap<>();
	private final List<SideSubtitle> sideSubtitles = new ArrayList<>();
	@Nullable private MediaItem item;
	/** what plays next, already in the playlist so the player buffers it and moves on without a pause */
	@Nullable private MediaItem next;
	@Nullable private String pendingSubtitleId;
	private boolean userPicked;

	public PlayerEngine(@NonNull Context context, @NonNull OkHttpClient http, @NonNull PlayerPreferences prefs) {
		this.prefs = prefs;
		OkHttpDataSource.Factory network = new OkHttpDataSource.Factory(http).setUserAgent(Http.userAgent());
		ResolvingDataSource.Factory withHeaders = new ResolvingDataSource.Factory(network, this::addHeaders);
		DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(context, withHeaders);
		player = new ExoPlayer.Builder(context, new Renderers(context))
				.setLoadControl(new DefaultLoadControl.Builder()
						// start after 1 s of buffer instead of 2.5 s; the torrent side keeps far more coming
						.setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
								DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
						.build())
				.setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
				.setAudioAttributes(new AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
						.build(), true)
				.setHandleAudioBecomingNoisy(true)
				.setSeekBackIncrementMs(Constant.SEEK_STEP_MS)
				.setSeekForwardIncrementMs(Constant.SEEK_STEP_MS)
				// keep wifi + cpu awake with the screen off
				.setWakeMode(C.WAKE_MODE_NETWORK)
				.build();
		applyLanguagePreferences();
	}

	@NonNull
	public ExoPlayer player() {
		return player;
	}

	public void play(@NonNull String url, @NonNull Map<String, String> headers, long startMs,
			@NonNull MediaMetadata metadata) {
		headersByHost.clear();
		rememberHeaders(url, headers);
		sideSubtitles.clear();
		next = null;
		item = new MediaItem.Builder().setUri(url).setMediaMetadata(metadata).build();
		player.setMediaItem(item, startMs);
		player.prepare();
		player.play();
	}

	/** lines up what plays after this one; the player starts buffering it near the end */
	public void queueNext(@NonNull String url, @NonNull Map<String, String> headers, @NonNull MediaMetadata metadata) {
		clearNext();
		rememberHeaders(url, headers);
		next = new MediaItem.Builder().setUri(url).setMediaMetadata(metadata).build();
		player.addMediaItem(next);
	}

	public void clearNext() {
		next = null;
		int count = player.getMediaItemCount();
		int current = player.getCurrentMediaItemIndex();
		if (count > current + 1) {
			player.removeMediaItems(current + 1, count);
		}
	}

	/** after the player moved on to the queued item by itself: that one is now the current one */
	public void adoptNext() {
		item = next;
		next = null;
		sideSubtitles.clear();
		int current = player.getCurrentMediaItemIndex();
		if (current > 0) {
			player.removeMediaItems(0, current);
		}
	}

	private void rememberHeaders(String url, Map<String, String> headers) {
		String host = Uri.parse(url).getHost();
		if (host != null && !headers.isEmpty()) {
			headersByHost.put(host.toLowerCase(Locale.ROOT), headers);
		}
	}

	/** The user picked it. Rebuilds the media item, so there's a short rebuffer. */
	public void addSubtitle(@NonNull SideSubtitle subtitle) {
		userPicked = true;
		addSideSubtitle(subtitle);
	}

	/** picked for the user from their language */
	void addAutoSubtitle(@NonNull SideSubtitle subtitle) {
		addSideSubtitle(subtitle);
	}

	private void addSideSubtitle(SideSubtitle subtitle) {
		MediaItem current = item;
		if (current == null || current.localConfiguration == null) {
			return;
		}
		sideSubtitles.add(subtitle);
		List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
		for (SideSubtitle s : sideSubtitles) {
			configs.add(new MediaItem.SubtitleConfiguration.Builder(s.uri)
					.setId(s.id)
					.setMimeType(s.mimeType)
					.setLanguage(s.language)
					.setLabel(s.label)
					.build());
		}
		item = current.buildUpon().setSubtitleConfigurations(configs).build();
		long position = player.getCurrentPosition();
		boolean playing = player.getPlayWhenReady();
		pendingSubtitleId = subtitle.id;
		player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
				.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
				.build());
		player.setMediaItem(item, position);
		if (next != null) {
			// setMediaItem replaced the whole playlist
			player.addMediaItem(next);
		}
		player.prepare();
		player.setPlayWhenReady(playing);
	}

	/** selects a just-added subtitle once its track shows up */
	public void onTracksChanged(@NonNull Tracks tracks) {
		String wanted = pendingSubtitleId;
		if (wanted == null) {
			return;
		}
		for (Tracks.Group group : tracks.getGroups()) {
			if (group.getType() != C.TRACK_TYPE_TEXT) {
				continue;
			}
			for (int i = 0; i < group.length; i++) {
				Format format = group.getTrackFormat(i);
				if (format.id != null && format.id.endsWith(wanted)) {
					pendingSubtitleId = null;
					selectTrack(group, i);
					return;
				}
			}
		}
	}

	public void selectTrack(@NonNull Tracks.Group group, int index) {
		userPicked = true;
		TrackSelectionParameters.Builder params = player.getTrackSelectionParameters().buildUpon()
				.setTrackTypeDisabled(group.getType(), false)
				.setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), index));
		// remember the language too so the next episode keeps it
		String language = group.getTrackFormat(index).language;
		if (language != null && group.getType() == C.TRACK_TYPE_AUDIO) {
			params.setPreferredAudioLanguage(language);
		} else if (language != null && group.getType() == C.TRACK_TYPE_TEXT) {
			params.setPreferredTextLanguage(language);
		}
		player.setTrackSelectionParameters(params.build());
	}

	public void subtitlesOff() {
		userPicked = true;
		pendingSubtitleId = null;
		player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
				.clearOverridesOfType(C.TRACK_TYPE_TEXT)
				.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
				.build());
	}

	public boolean subtitlesEnabled() {
		return !player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
	}

	public void applySubtitleStyle(@NonNull SubtitleView view) {
		int foreground;
		switch (prefs.subtitleColor()) {
			case YELLOW:
				foreground = Color.YELLOW;
				break;
			case CYAN:
				foreground = Color.CYAN;
				break;
			case WHITE:
			default:
				foreground = Color.WHITE;
				break;
		}
		int background;
		switch (prefs.subtitleBackground()) {
			case BLACK:
				background = Color.BLACK;
				break;
			case TRANSLUCENT:
				background = 0x99000000;
				break;
			case NONE:
			default:
				background = Color.TRANSPARENT;
				break;
		}
		view.setStyle(new CaptionStyleCompat(foreground, background, Color.TRANSPARENT,
				CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null));
		view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * prefs.subtitleSize() / 100f);
	}

	/** another title: whatever the user picked for the last one doesn't carry over */
	public void resetTracks() {
		userPicked = false;
		pendingSubtitleId = null;
		player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
				.clearOverrides()
				.setPreferredAudioLanguages()
				.setPreferredTextLanguages()
				.build());
		applyLanguagePreferences();
	}

	/** the next episode of the same title, nothing picked by hand: subtitles are decided again for it */
	void resetAutoSubtitles() {
		pendingSubtitleId = null;
		player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
				.clearOverridesOfType(C.TRACK_TYPE_TEXT)
				.setPreferredTextLanguages()
				.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
				.build());
	}

	void showSubtitlesIn(@NonNull String language) {
		player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
				.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
				.setPreferredTextLanguage(language)
				.build());
	}

	/** audio or subtitles chosen by hand for this title */
	boolean userPicked() {
		return userPicked;
	}

	public void release() {
		player.release();
	}

	/** audio in the chosen language when the file has it, otherwise the original; subtitles start off */
	private void applyLanguagePreferences() {
		player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
				.setPreferredAudioLanguage(prefs.language())
				.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
				.build());
	}

	private DataSpec addHeaders(DataSpec spec) {
		String host = spec.uri.getHost();
		Map<String, String> headers = host != null ? headersByHost.get(host.toLowerCase(Locale.ROOT)) : null;
		return headers != null ? spec.withAdditionalHeaders(headers) : spec;
	}

	/** guess from name/url, default SRT */
	@NonNull
	public static String subtitleMimeType(@NonNull String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		int query = lower.indexOf('?');
		if (query >= 0) {
			lower = lower.substring(0, query);
		}
		if (lower.endsWith(".vtt")) {
			return MimeTypes.TEXT_VTT;
		}
		if (lower.endsWith(".ass") || lower.endsWith(".ssa")) {
			return MimeTypes.TEXT_SSA;
		}
		if (lower.endsWith(".ttml") || lower.endsWith(".xml")) {
			return MimeTypes.APPLICATION_TTML;
		}
		return MimeTypes.APPLICATION_SUBRIP;
	}
}
