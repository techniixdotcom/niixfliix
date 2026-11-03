package app.niixfliix.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class PlayerGestures {

	interface Callback {
		void onSingleTap();

		void onSeekBy(boolean forward);

		void onSwipeDown();

		void onZoom(boolean zoomIn);

		boolean onLevelSwipeStart(boolean leftHalf);

		void onLevelSwipe(boolean leftHalf, float delta);

		void onLevelSwipeEnd();
	}

	private static final float MINIMIZE_SWIPE = 0.15f;
	private static final float LEVEL_SWIPE_RANGE = 0.6f;
	private static final float EDGE_DP = 40f;

	private final View view;
	private final Callback callback;
	private final GestureDetector taps;
	private final ScaleGestureDetector pinch;
	private final float edge;
	private boolean scaling;
	private boolean decided;
	private boolean levelSwipe;
	private boolean levelLeft;

	PlayerGestures(@NonNull Context context, @NonNull View view, @NonNull Callback callback) {
		this.view = view;
		this.callback = callback;
		edge = EDGE_DP * context.getResources().getDisplayMetrics().density;
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
			public boolean onScroll(@Nullable MotionEvent start, @NonNull MotionEvent now, float distanceX,
					float distanceY) {
				if (start == null || scaling || view.getHeight() == 0) {
					return false;
				}
				if (!decided) {
					decided = true;
					float dx = now.getX() - start.getX();
					float dy = now.getY() - start.getY();
					boolean vertical = Math.abs(dy) > Math.abs(dx) * 1.2f;
					boolean awayFromEdges = start.getY() > edge && start.getY() < view.getHeight() - edge;
					if (vertical && awayFromEdges) {
						levelLeft = start.getX() < view.getWidth() / 2f;
						levelSwipe = callback.onLevelSwipeStart(levelLeft);
					}
				}
				if (levelSwipe) {
					callback.onLevelSwipe(levelLeft, distanceY / (view.getHeight() * LEVEL_SWIPE_RANGE));
					return true;
				}
				return false;
			}

			@Override
			public boolean onFling(@Nullable MotionEvent start, @NonNull MotionEvent end, float vx, float vy) {
				if (start == null || scaling || levelSwipe) {
					return false;
				}
				float dy = end.getY() - start.getY();
				float dx = end.getX() - start.getX();
				if (dy > view.getHeight() * MINIMIZE_SWIPE && dy > Math.abs(dx) * 1.5f) {
					callback.onSwipeDown();
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
				endLevelSwipe();
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
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN) {
				decided = false;
			}
			pinch.onTouchEvent(event);
			taps.onTouchEvent(event);
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				scaling = false;
				endLevelSwipe();
			}
			return true;
		});
	}

	private void endLevelSwipe() {
		if (levelSwipe) {
			levelSwipe = false;
			callback.onLevelSwipeEnd();
		}
	}
}
