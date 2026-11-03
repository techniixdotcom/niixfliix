package app.niixfliix.player;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import app.niixfliix.R;

final class ScreenLock {

	private static final long HOLD_MS = 5000;
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
		View hold = root.findViewById(R.id.unlock_hold);
		hold.setOnTouchListener(this::onHoldTouch);
		ViewCompat.replaceAccessibilityAction(hold, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
				root.getContext().getString(R.string.player_unlock), (view, arguments) -> {
					if (locked) {
						unlock();
					}
					return true;
				});
	}

	boolean isLocked() {
		return locked;
	}

	void lock() {
		locked = true;
		shield.setVisibility(View.VISIBLE);
		showPanel();
	}

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
