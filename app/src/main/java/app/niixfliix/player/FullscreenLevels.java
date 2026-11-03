package app.niixfliix.player;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.niixfliix.R;
import app.niixfliix.util.MainQueue;

final class FullscreenLevels implements LevelSlider.Listener {

	private static final long LINGER_MS = 1000;
	private static final long FADE_MS = 150;
	private static final float MIN_BRIGHTNESS = 0.01f;
	private static final float SYSTEM_BRIGHTNESS_MAX = 255f;

	private final Activity activity;
	private final MainQueue main;
	private final View brightnessPanel;
	private final View volumePanel;
	private final LevelSlider brightness;
	private final LevelSlider volume;
	private final TextView brightnessText;
	private final TextView volumeText;
	@Nullable private final AudioManager audio;
	private final int maxVolume;
	private final Runnable hidePanels = this::hideUnlessControls;
	private boolean enabled;
	private boolean controlsVisible;
	private boolean tracking;
	@Nullable private Float chosenBrightness;
	private float swipeVolume;

	FullscreenLevels(@NonNull Activity activity, @NonNull MainQueue main, @NonNull View root) {
		this.activity = activity;
		this.main = main;
		brightnessPanel = root.findViewById(R.id.brightness_panel);
		volumePanel = root.findViewById(R.id.volume_panel);
		brightness = root.findViewById(R.id.brightness_slider);
		volume = root.findViewById(R.id.volume_slider);
		brightnessText = root.findViewById(R.id.brightness_value);
		volumeText = root.findViewById(R.id.volume_value);
		brightness.setListener(this);
		volume.setListener(this);
		audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
		maxVolume = audio != null ? audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 0;
	}

	void setEnabled(boolean on) {
		if (on == enabled) {
			return;
		}
		enabled = on;
		tracking = false;
		main.removeCallbacks(hidePanels);
		applyBrightness();
		if (on) {
			refresh();
			if (controlsVisible) {
				show(brightnessPanel, true);
				show(volumePanel, canChangeVolume());
			}
		} else {
			hideNow(brightnessPanel);
			hideNow(volumePanel);
		}
	}

	void setControlsVisible(boolean visible) {
		controlsVisible = visible;
		if (!enabled) {
			return;
		}
		if (visible) {
			main.removeCallbacks(hidePanels);
			refresh();
			show(brightnessPanel, true);
			show(volumePanel, canChangeVolume());
		} else if (!tracking) {
			hideUnlessControls();
		}
	}

	void onVolumeChangedOutside() {
		if (enabled) {
			refreshVolume();
		}
	}

	boolean startSwipe(boolean left) {
		if (!enabled || !left && !canChangeVolume()) {
			return false;
		}
		tracking = true;
		main.removeCallbacks(hidePanels);
		if (left) {
			show(brightnessPanel, true);
		} else {
			refreshVolume();
			swipeVolume = volume.level();
			show(volumePanel, true);
		}
		return true;
	}

	void swipeBy(boolean left, float delta) {
		if (!tracking) {
			return;
		}
		if (left) {
			setBrightness(brightness.level() + delta);
		} else {
			swipeVolume = Math.max(0f, Math.min(1f, swipeVolume + delta));
			setVolume(swipeVolume);
		}
	}

	void endSwipe() {
		tracking = false;
		lingerIfNoControls();
	}

	@Override
	public void onLevelChanged(@NonNull LevelSlider slider, float level, boolean fromUser) {
		if (slider == brightness) {
			setBrightness(level);
		} else {
			setVolume(level);
		}
	}

	@Override
	public void onTracking(boolean on) {
		tracking = on;
		if (on) {
			main.removeCallbacks(hidePanels);
		} else {
			lingerIfNoControls();
		}
	}

	private void setBrightness(float level) {
		float value = Math.max(MIN_BRIGHTNESS, Math.min(1f, level));
		chosenBrightness = value;
		brightness.setLevel(value);
		brightnessText.setText(percent(value));
		applyBrightness();
	}

	private void applyBrightness() {
		WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
		float target = enabled && chosenBrightness != null
				? chosenBrightness : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
		if (lp.screenBrightness != target) {
			lp.screenBrightness = target;
			activity.getWindow().setAttributes(lp);
		}
	}

	private void setVolume(float level) {
		if (!canChangeVolume()) {
			return;
		}
		int index = Math.round(level * maxVolume);
		try {
			audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
		} catch (SecurityException e) {
		}
		refreshVolume();
		if (tracking) {
			volume.setLevel(level);
		}
	}

	private boolean canChangeVolume() {
		return audio != null && maxVolume > 0 && !audio.isVolumeFixed();
	}

	private void refresh() {
		float b = chosenBrightness != null ? chosenBrightness : systemBrightness();
		brightness.setLevel(b);
		brightnessText.setText(percent(b));
		refreshVolume();
	}

	private void refreshVolume() {
		if (!canChangeVolume()) {
			return;
		}
		float level = (float) audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume;
		volume.setLevel(level);
		volumeText.setText(percent(level));
	}

	private float systemBrightness() {
		float window = activity.getWindow().getAttributes().screenBrightness;
		if (window >= 0f) {
			return window;
		}
		try {
			int value = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
			return Math.max(MIN_BRIGHTNESS, Math.min(1f, value / SYSTEM_BRIGHTNESS_MAX));
		} catch (Settings.SettingNotFoundException e) {
			return 0.5f;
		}
	}

	private String percent(float level) {
		return activity.getString(R.string.percent, Math.round(level * 100f));
	}

	private void lingerIfNoControls() {
		main.removeCallbacks(hidePanels);
		if (!controlsVisible) {
			main.postDelayed(hidePanels, LINGER_MS);
		}
	}

	private void hideUnlessControls() {
		if (controlsVisible && enabled || tracking) {
			return;
		}
		hide(brightnessPanel);
		hide(volumePanel);
	}

	private static void show(View panel, boolean show) {
		if (!show) {
			hideNow(panel);
			return;
		}
		if (panel.getVisibility() != View.VISIBLE) {
			panel.setAlpha(0f);
			panel.setVisibility(View.VISIBLE);
		}
		panel.animate().cancel();
		panel.animate().alpha(1f).setDuration(FADE_MS);
	}

	private static void hide(View panel) {
		if (panel.getVisibility() != View.VISIBLE) {
			return;
		}
		panel.animate().cancel();
		panel.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> panel.setVisibility(View.GONE));
	}

	private static void hideNow(View panel) {
		panel.animate().cancel();
		panel.setVisibility(View.GONE);
	}

	void release() {
		main.removeCallbacks(hidePanels);
		hideNow(brightnessPanel);
		hideNow(volumePanel);
	}
}
