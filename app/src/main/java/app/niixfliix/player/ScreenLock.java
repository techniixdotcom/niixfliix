package app.niixfliix.player;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import app.niixfliix.R;

/** The player's lock: a see-through layer eats every touch until the lock on it is held for 5 s. */
final class ScreenLock {

	private static final long HOLD_MS = 5000;
	/** how long the lock stays on screen after a tap */
	private static final long PANEL_MS = 3000;

	private final View shield;
	private final View panel;
	private final CircularProgressIndicator ring;
	private final Runnable onUnlocked;
	private final Runnable hidePanel;
	private final Runnable holdTick = this::holdTick;
	private boolean locked;
	private long holdStart;

	ScreenLock(@NonNull View root, @NonNull Runnable onUnlocked) {
		shield = root.findViewById(R.id.lock_shield);
		panel = root.findViewById(R.id.unlock_panel);
		ring = root.findViewById(R.id.unlock_progress);
		this.onUnlocked = onUnlocked;
		hidePanel = () -> panel.setVisibility(View.GONE);
		shield.setOnClickListener(v -> showPanel());
		root.findViewById(R.id.unlock_hold).setOnTouchListener(this::onHoldTouch);
	}

	boolean isLocked() {
		return locked;
	}

	void lock() {
		locked = true;
		shield.setVisibility(View.VISIBLE);
		// show once how to get out again
		showPanel();
	}

	/** a tap anywhere, back or a key while locked: show where the lock is */
	void showPanel() {
		panel.setVisibility(View.VISIBLE);
		shield.removeCallbacks(hidePanel);
		if (holdStart == 0) {
			shield.postDelayed(hidePanel, PANEL_MS);
		}
	}

	private boolean onHoldTouch(View v, MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			holdStart = SystemClock.uptimeMillis();
			shield.removeCallbacks(hidePanel);
			ring.postOnAnimation(holdTick);
		} else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			if (action == MotionEvent.ACTION_UP) {
				v.performClick();
			}
			// let go too early
			stopHold();
			if (locked) {
				showPanel();
			}
		}
		return true;
	}

	private void holdTick() {
		long held = SystemClock.uptimeMillis() - holdStart;
		if (held >= HOLD_MS) {
			unlock();
			return;
		}
		ring.setProgress((int) (held * ring.getMax() / HOLD_MS));
		ring.postOnAnimation(holdTick);
	}

	private void stopHold() {
		holdStart = 0;
		ring.removeCallbacks(holdTick);
		ring.setProgress(0);
	}

	private void unlock() {
		locked = false;
		stopHold();
		shield.removeCallbacks(hidePanel);
		panel.setVisibility(View.GONE);
		shield.setVisibility(View.GONE);
		onUnlocked.run();
	}

	void release() {
		stopHold();
		shield.removeCallbacks(hidePanel);
	}
}
