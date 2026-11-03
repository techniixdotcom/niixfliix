package app.niixfliix.player;

import android.app.PictureInPictureParams;
import android.app.UiModeManager;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Rational;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.R;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamRanker;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.app.AppGraph;
import app.niixfliix.app.Constant;
import app.niixfliix.ui.Formats;

@OptIn(markerClass = UnstableApi.class)
public final class PlayerActivity extends AppCompatActivity implements PlaybackController.Screen, Player.Listener,
		PlayerGestures.Callback {

	private final AppGraph graph = AppGraph.get();
	private final PlaybackController playback = graph.playback();
	private static final long INDICATOR_MS = 700;
	private static final long ROTATE_SETTLE_MS = 300;
	private static final int COMPACT_BOTTOM_ROW_DP = 400;

	private final Runnable hideIndicator = this::hideIndicator;
	private final Runnable followRotation = this::followRotation;

	private Player player;
	private PlayerEngine engine;
	private PlayerView playerView;
	private View topBar;
	private View bottomActions;
	private TextView titleView;
	private TextView subtitleView;
	private TextView indicator;
	private View loading;
	private TextView loadingText;
	private View errorCard;
	private TextView errorText;
	private View errorNext;
	private ViewGroup videoFrame;
	private View info;
	private LinearLayout infoList;
	private TextView infoTitle;
	private TextView infoEpisode;
	private TextView infoFacts;
	private TextView infoDescription;
	private View upNextCard;
	private ImageButton fullscreenButton;
	private View lockButton;
	private View moreButton;
	private View[] overflowButtons;
	private UpNext upNext;
	private ScreenLock screenLock;
	private FullscreenLevels levels;
	@Nullable private OrientationEventListener rotation;
	private boolean inPip;
	private boolean fullscreen;
	private boolean tv;
	private boolean rotationArmed;
	@Nullable private Boolean turnedTo;

	private final ActivityResultLauncher<String[]> pickSubtitleFile = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(), this::onSubtitleFile);

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Player p = playback.player();
		PlayerEngine e = playback.engine();
		if (p == null || e == null) {
			finish();
			return;
		}
		player = p;
		engine = e;
		setContentView(R.layout.activity_player);
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		tv = isTv();
		fullscreen = tv || graph.playerPrefs.fullscreen();
		rotationArmed = !fullscreen;
		setRequestedOrientation(orientation());

		playerView = findViewById(R.id.player_view);
		topBar = findViewById(R.id.top_bar);
		bottomActions = findViewById(R.id.bottom_actions);
		titleView = findViewById(R.id.player_title);
		subtitleView = findViewById(R.id.player_subtitle);
		indicator = findViewById(R.id.gesture_indicator);
		loading = findViewById(R.id.loading);
		loadingText = findViewById(R.id.loading_text);
		errorCard = findViewById(R.id.error_card);
		errorText = findViewById(R.id.error_text);
		errorNext = findViewById(R.id.error_next);
		videoFrame = findViewById(R.id.video_frame);
		info = findViewById(R.id.player_info);
		infoList = findViewById(R.id.info_list);
		infoTitle = findViewById(R.id.info_title);
		infoEpisode = findViewById(R.id.info_episode);
		infoFacts = findViewById(R.id.info_facts);
		infoDescription = findViewById(R.id.info_description);
		upNextCard = findViewById(R.id.up_next);
		fullscreenButton = findViewById(R.id.player_fullscreen);
		lockButton = findViewById(R.id.player_lock);
		moreButton = findViewById(R.id.player_more);
		overflowButtons = new View[] {findViewById(R.id.player_audio), findViewById(R.id.player_streams)};
		View root = findViewById(android.R.id.content);
		upNext = new UpNext(root, playback::playNextNow, playback::cancelUpNext);
		screenLock = new ScreenLock(root, this::onUnlocked);
		levels = new FullscreenLevels(this, graph.main, root);
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.player_content), (v, insets) -> {
			Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(0, bars.top, 0, bars.bottom);
			return insets;
		});

		player.addListener(this);
		playerView.setPlayer(player);
		playerView.setShowNextButton(true);
		playerView.setShowPreviousButton(false);
		playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
			topBar.setVisibility(inPip ? View.GONE : visibility);
			bottomActions.setVisibility(inPip ? View.GONE : visibility);
			upNext.setControlsVisible(visibility == View.VISIBLE);
			levels.setControlsVisible(visibility == View.VISIBLE);
		});
		engine.applySubtitleStyle(playerView.getSubtitleView());
		new PlayerGestures(this, playerView, this).attach();

		findViewById(R.id.player_back).setOnClickListener(v -> onMinimize());
		lockButton.setOnClickListener(v -> lockScreen());
		findViewById(R.id.player_subtitles).setOnClickListener(v -> showSubtitles());
		findViewById(R.id.player_audio).setOnClickListener(v -> showAudio());
		findViewById(R.id.player_streams).setOnClickListener(v -> showStreamSwitcher());
		findViewById(R.id.player_minimize).setOnClickListener(v -> onMinimize());
		moreButton.setOnClickListener(this::showMore);
		fullscreenButton.setOnClickListener(v -> toggleFullscreen());
		lockButton.setVisibility(tv ? View.GONE : View.VISIBLE);
		fullscreenButton.setVisibility(tv ? View.GONE : View.VISIBLE);
		if (canFollowRotation()) {
			rotation = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
				@Override
				public void onOrientationChanged(int degrees) {
					onDeviceTurned(degrees);
				}
			};
		}
		View pip = findViewById(R.id.player_pip);
		pip.setVisibility(supportsPip() ? View.VISIBLE : View.GONE);
		pip.setOnClickListener(v -> enterPip());
		findViewById(R.id.error_close).setOnClickListener(v -> playback.stop());
		errorNext.setOnClickListener(v -> playback.tryNextStream());
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (screenLock.isLocked()) {
					screenLock.showPanel();
					return;
				}
				onMinimize();
			}
		});
		updatePipParams();
		applyLayout();
		if (tv) {
			playerView.requestFocus();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		playback.attach(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (rotation != null && rotation.canDetectOrientation()) {
			rotation.enable();
		}
	}

	@Override
	protected void onPause() {
		if (rotation != null) {
			rotation.disable();
		}
		turnedTo = null;
		graph.main.removeCallbacks(followRotation);
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
		playback.detach(this);
	}

	@Override
	protected void onDestroy() {
		graph.main.removeCallbacks(hideIndicator);
		graph.main.removeCallbacks(followRotation);
		if (player != null) {
			levels.release();
			screenLock.release();
			player.removeListener(this);
			playerView.setPlayer(null);
		}
		super.onDestroy();
	}

	private void onMinimize() {
		finish();
	}

	@Override
	public void onSwipeDown() {
		if (!fullscreen) {
			onMinimize();
		}
	}

	@Override
	public boolean onLevelSwipeStart(boolean leftHalf) {
		return levels.startSwipe(leftHalf);
	}

	@Override
	public void onLevelSwipe(boolean leftHalf, float delta) {
		levels.swipeBy(leftHalf, delta);
	}

	@Override
	public void onLevelSwipeEnd() {
		levels.endSwipe();
	}

	private void updateLevels() {
		levels.setEnabled(fullscreen && !tv && !inPip && !screenLock.isLocked());
	}

	@Override
	public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
		if (screenLock != null && screenLock.isLocked()) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				screenLock.showPanel();
			}
			return true;
		}
		if (playerView != null && seekKey(event)) {
			return true;
		}
		boolean handled = super.dispatchKeyEvent(event);
		int key = event.getKeyCode();
		if (levels != null && event.getAction() == KeyEvent.ACTION_UP && (key == KeyEvent.KEYCODE_VOLUME_UP
				|| key == KeyEvent.KEYCODE_VOLUME_DOWN || key == KeyEvent.KEYCODE_VOLUME_MUTE)) {
			levels.onVolumeChangedOutside();
		}
		return handled;
	}

	private boolean seekKey(@NonNull KeyEvent event) {
		int key = event.getKeyCode();
		if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) {
			return false;
		}
		if (inPip || playerView.isControllerFullyVisible() || errorCard.getVisibility() == View.VISIBLE) {
			return false;
		}
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			onSeekBy(key == KeyEvent.KEYCODE_DPAD_RIGHT);
		}
		return true;
	}

	private void lockScreen() {
		playerView.hideController();
		playerView.setUseController(false);
		screenLock.lock();
		updateLevels();
	}

	private void onUnlocked() {
		updateLevels();
		playerView.setUseController(!inPip);
		playerView.showController();
	}

	@Override
	public void onClosed() {
		finish();
	}

	@Override
	public void onTitle(@NonNull String title, @NonNull String subtitle) {
		titleView.setText(title);
		subtitleView.setText(subtitle);
		PlaySession s = playback.session();
		Meta meta = s != null ? s.meta : null;
		show(infoTitle, title);
		show(infoEpisode, subtitle);
		show(infoFacts, meta != null ? facts(meta) : null);
		show(infoDescription, meta != null ? meta.description : null);
	}

	private static void show(TextView view, @Nullable String text) {
		view.setText(text);
		view.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
	}

	private static String facts(Meta meta) {
		List<String> parts = new ArrayList<>();
		for (String part : new String[] {meta.releaseInfo, meta.runtime, meta.imdbRating}) {
			if (part != null && !part.isEmpty()) {
				parts.add(part);
			}
		}
		return TextUtils.join(" · ", parts);
	}

	@Override
	public void onLoading(@Nullable String text) {
		loading.setVisibility(text != null && !inPip ? View.VISIBLE : View.GONE);
		loadingText.setText(text);
	}

	@Override
	public void onError(@Nullable String message, boolean offerNext) {
		errorCard.setVisibility(message != null ? View.VISIBLE : View.GONE);
		if (message != null) {
			errorText.setText(message);
			errorNext.setVisibility(offerNext ? View.VISIBLE : View.GONE);
			playerView.hideController();
		}
	}

	@Override
	public void onUpNext(@Nullable String title, long remainingMs) {
		upNext.show(title, remainingMs);
	}

	@Override
	public void onPictureInPictureModeChanged(boolean isInPip, @NonNull Configuration newConfig) {
		super.onPictureInPictureModeChanged(isInPip, newConfig);
		inPip = isInPip;
		playerView.setUseController(!isInPip && !screenLock.isLocked());
		topBar.setVisibility(View.GONE);
		bottomActions.setVisibility(View.GONE);
		upNext.setHiddenForPip(isInPip);
		applyLayout();
		if (!isInPip && getLifecycle().getCurrentState() == Lifecycle.State.CREATED) {
			playback.stop();
		}
	}

	@Override
	public void onIsPlayingChanged(boolean isPlaying) {
		updatePipParams();
	}

	@Override
	public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
		updatePipParams();
	}

	private void showStreamSwitcher() {
		PlaySession session = playback.session();
		if (session == null) {
			return;
		}
		List<StreamInfo> choices = StreamRanker.bestPerQuality(session.streams);
		if (!choices.contains(session.stream)) {
			choices.add(0, session.stream);
		}
		List<StreamInfo> playable = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		int checked = -1;
		for (StreamInfo s : choices) {
			if (!StreamRanker.isPlayable(s)) {
				continue;
			}
			if (s.key().equals(session.stream.key())) {
				checked = playable.size();
			}
			playable.add(s);
			String quality = Formats.quality(this, s.quality);
			labels.add(s.sizeBytes > 0
					? getString(R.string.player_stream_label, quality, Formats.size(this, s.sizeBytes)) : quality);
		}
		if (playable.isEmpty()) {
			Toast.makeText(this, R.string.player_no_other_streams, Toast.LENGTH_SHORT).show();
			return;
		}
		new MaterialAlertDialogBuilder(this)
				.setTitle(R.string.player_switch_stream)
				.setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
					dialog.dismiss();
					StreamInfo picked = playable.get(which);
					if (!picked.key().equals(session.stream.key())) {
						playback.pick(picked);
					}
				})
				.show();
	}

	private void showAudio() {
		TrackPicker.audio(this, player.getCurrentTracks(), engine::selectTrack);
	}

	private void showMore(@NonNull View anchor) {
		PopupMenu menu = new PopupMenu(this, anchor);
		menu.getMenu().add(0, R.id.player_audio, 0, R.string.player_audio);
		menu.getMenu().add(0, R.id.player_streams, 1, R.string.player_switch_stream);
		menu.setOnMenuItemClickListener(item -> {
			int id = item.getItemId();
			if (id == R.id.player_audio) {
				showAudio();
			} else if (id == R.id.player_streams) {
				showStreamSwitcher();
			} else {
				return false;
			}
			return true;
		});
		menu.show();
	}

	private void showSubtitles() {
		TrackPicker.subtitles(this, player.getCurrentTracks(), engine.subtitlesEnabled(), playback.addonSubtitles(),
				new TrackPicker.SubtitleChoice() {
					@Override
					public void off() {
						engine.subtitlesOff();
					}

					@Override
					public void track(@NonNull Tracks.Group group, int index) {
						engine.selectTrack(group, index);
					}

					@Override
					public void addon(@NonNull TrackPicker.AddonSubtitle s) {
						engine.addSubtitle(s.toSide());
					}

					@Override
					public void localFile() {
						pickSubtitleFile.launch(new String[] {"*/*"});
					}
				});
	}

	private void onSubtitleFile(@Nullable Uri uri) {
		if (uri == null) {
			return;
		}
		String name = displayName(uri);
		engine.addSubtitle(new PlayerEngine.SideSubtitle("file-" + Integer.toHexString(uri.hashCode()), uri, null,
				name, PlayerEngine.subtitleMimeType(name)));
	}

	@NonNull
	private String displayName(Uri uri) {
		try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME},
				null, null, null)) {
			if (cursor != null && cursor.moveToFirst()) {
				String name = cursor.getString(0);
				if (name != null) {
					return name;
				}
			}
		} catch (SecurityException | IllegalArgumentException ignored) {
		}
		return getString(R.string.player_subtitle_file);
	}

	@Override
	public void onSingleTap() {
		if (playerView.isControllerFullyVisible()) {
			playerView.hideController();
		} else {
			playerView.showController();
		}
	}

	@Override
	public void onSeekBy(boolean forward) {
		if (forward) {
			player.seekForward();
		} else {
			player.seekBack();
		}
		showIndicator(getString(forward ? R.string.player_seek_forward : R.string.player_seek_back,
				Constant.SEEK_STEP_MS / 1000));
	}

	@Override
	public void onZoom(boolean zoomIn) {
		playerView.setResizeMode(zoomIn ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT);
		showIndicator(getString(zoomIn ? R.string.player_zoom_fill : R.string.player_zoom_fit));
	}

	private void hideIndicator() {
		indicator.setVisibility(View.GONE);
	}

	private void showIndicator(String text) {
		indicator.setText(text);
		indicator.setVisibility(View.VISIBLE);
		graph.main.removeCallbacks(hideIndicator);
		graph.main.postDelayed(hideIndicator, INDICATOR_MS);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		applyLayout();
	}

	private void toggleFullscreen() {
		rotationArmed = false;
		turnedTo = null;
		graph.main.removeCallbacks(followRotation);
		setFullscreen(!fullscreen);
		graph.playerPrefs.setFullscreen(fullscreen);
	}

	private void setFullscreen(boolean on) {
		fullscreen = on;
		setRequestedOrientation(orientation());
		applyLayout();
	}

	private int orientation() {
		if (tv) {
			return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
		}
		if (fullscreen) {
			return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
		}
		return isLargeScreen() ? ActivityInfo.SCREEN_ORIENTATION_FULL_USER : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
	}

	private boolean isLargeScreen() {
		return getResources().getConfiguration().smallestScreenWidthDp >= 600;
	}

	private boolean isTv() {
		UiModeManager uiMode = getSystemService(UiModeManager.class);
		return uiMode != null && uiMode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION
				|| !getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
	}

	private boolean canFollowRotation() {
		return !tv && !isLargeScreen();
	}

	private boolean autoRotateOn() {
		return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
	}

	private void onDeviceTurned(int degrees) {
		if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
			return;
		}
		Boolean wantFullscreen = degrees <= 20 || degrees >= 340 ? Boolean.FALSE
				: degrees >= 70 && degrees <= 110 || degrees >= 250 && degrees <= 290 ? Boolean.TRUE : null;
		if (wantFullscreen == null) {
			return;
		}
		boolean allowed = autoRotateOn() && !inPip && !isInMultiWindowMode() && !screenLock.isLocked();
		if (!rotationArmed) {
			if (wantFullscreen == fullscreen) {
				rotationArmed = true;
			}
			return;
		}
		if (!allowed || wantFullscreen == fullscreen) {
			turnedTo = null;
			graph.main.removeCallbacks(followRotation);
			return;
		}
		if (!wantFullscreen.equals(turnedTo)) {
			turnedTo = wantFullscreen;
			graph.main.removeCallbacks(followRotation);
			graph.main.postDelayed(followRotation, ROTATE_SETTLE_MS);
		}
	}

	private void followRotation() {
		Boolean target = turnedTo;
		turnedTo = null;
		if (target != null && target != fullscreen && !isFinishing()) {
			setFullscreen(target);
		}
	}

	private void applyLayout() {
		boolean portrait = !fullscreen && !inPip;
		WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
		if (portrait) {
			bars.show(WindowInsetsCompat.Type.systemBars());
		} else {
			bars.hide(WindowInsetsCompat.Type.systemBars());
			bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		}
		ViewGroup.LayoutParams frame = videoFrame.getLayoutParams();
		frame.height = portrait ? videoHeight() : ViewGroup.LayoutParams.MATCH_PARENT;
		videoFrame.setLayoutParams(frame);
		info.setVisibility(portrait ? View.VISIBLE : View.GONE);
		fullscreenButton.setImageResource(fullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
		fullscreenButton.setContentDescription(getString(fullscreen
				? R.string.player_exit_fullscreen : R.string.player_fullscreen));
		fitBottomRow();
		moveUpNext(portrait);
		updateLevels();
	}

	private int videoHeight() {
		Configuration config = getResources().getConfiguration();
		float density = getResources().getDisplayMetrics().density;
		float byWidth = config.screenWidthDp * density * 9 / 16;
		float byHeight = config.screenHeightDp * density * 0.6f;
		return Math.round(Math.min(byWidth, byHeight));
	}

	private void fitBottomRow() {
		boolean compact = getResources().getConfiguration().screenWidthDp < COMPACT_BOTTOM_ROW_DP;
		for (View button : overflowButtons) {
			button.setVisibility(compact ? View.GONE : View.VISIBLE);
		}
		moreButton.setVisibility(compact ? View.VISIBLE : View.GONE);
	}

	private void moveUpNext(boolean portrait) {
		ViewGroup target = portrait ? infoList : videoFrame;
		if (upNextCard.getParent() != target) {
			placeUpNext(portrait, target);
		}
		upNext.setOverlay(!portrait);
	}

	private void placeUpNext(boolean portrait, @NonNull ViewGroup target) {
		((ViewGroup) upNextCard.getParent()).removeView(upNextCard);
		float dp = getResources().getDisplayMetrics().density;
		if (portrait) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.bottomMargin = Math.round(16 * dp);
			target.addView(upNextCard, 0, lp);
		} else {
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
					getResources().getDimensionPixelSize(R.dimen.up_next_width),
					ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
			lp.setMarginEnd(Math.round(16 * dp));
			lp.bottomMargin = Math.round(16 * dp);
			target.addView(upNextCard, target.indexOfChild(errorCard), lp);
		}
	}

	private boolean supportsPip() {
		return getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
	}

	private void enterPip() {
		try {
			enterPictureInPictureMode(pipParams());
		} catch (IllegalStateException e) {
		}
	}

	private void updatePipParams() {
		if (supportsPip()) {
			setPictureInPictureParams(pipParams());
		}
	}

	private PictureInPictureParams pipParams() {
		VideoSize size = player.getVideoSize();
		Rational ratio = size.width > 0 && size.height > 0
				? new Rational(size.width, size.height)
				: new Rational(16, 9);
		float value = ratio.floatValue();
		if (value > 2.39f) {
			ratio = new Rational(239, 100);
		} else if (value < 1 / 2.39f) {
			ratio = new Rational(100, 239);
		}
		PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder().setAspectRatio(ratio);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			builder.setSeamlessResizeEnabled(false);
		}
		return builder.build();
	}
}
