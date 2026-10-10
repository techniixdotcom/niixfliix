package app.niixfliix.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Tap = controls, double tap left/right third = seek, swipe down = minimize, pinch = zoom. */
final class PlayerGestures {

	interface Callback {
		void onSingleTap();

		void onSeekBy(boolean forward);

		void onMinimize();

		void onZoom(boolean zoomIn);
	}

	/** of the screen height, so a sloppy tap never minimizes */
	private static final float MINIMIZE_SWIPE = 0.15f;

	private final View view;
	private final GestureDetector taps;
	private final ScaleGestureDetector pinch;
	private boolean scaling;

	PlayerGestures(@NonNull Context context, @NonNull View view, @NonNull Callback callback) {
		this.view = view;
		taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDown(@NonNull MotionEvent e) {
				return true;
			}

			@Override
			public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
				callback.onSingleTap();
				return true;
			}

			@Override
			public boolean onDoubleTap(@NonNull MotionEvent e) {
				float x = e.getX() / view.getWidth();
				if (x < 1 / 3f) {
					callback.onSeekBy(false);
				} else if (x > 2 / 3f) {
					callback.onSeekBy(true);
				} else {
					callback.onSingleTap();
				}
				return true;
			}

			@Override
			public boolean onFling(@Nullable MotionEvent start, @NonNull MotionEvent end, float vx, float vy) {
				if (start == null || scaling) {
					return false;
				}
				float dy = end.getY() - start.getY();
				float dx = end.getX() - start.getX();
				if (dy > view.getHeight() * MINIMIZE_SWIPE && dy > Math.abs(dx) * 1.5f) {
					callback.onMinimize();
					return true;
				}
				return false;
			}
		});
		pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			private float total;

			@Override
			public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
				scaling = true;
				total = 1f;
				return true;
			}

			@Override
			public boolean onScale(@NonNull ScaleGestureDetector detector) {
				total *= detector.getScaleFactor();
				return true;
			}

			@Override
			public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
				if (total > 1.08f) {
					callback.onZoom(true);
				} else if (total < 0.92f) {
					callback.onZoom(false);
				}
			}
		});
	}

	@SuppressLint("ClickableViewAccessibility")
	void attach() {
		view.setOnTouchListener((v, event) -> {
			pinch.onTouchEvent(event);
			taps.onTouchEvent(event);
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				scaling = false;
			}
			return true;
		});
	}
}
