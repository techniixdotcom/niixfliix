package app.niixfliix.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import app.niixfliix.R;

public final class LevelSlider extends View {

	interface Listener {
		void onLevelChanged(@NonNull LevelSlider slider, float level, boolean fromUser);

		void onTracking(boolean tracking);
	}

	private static final float TRACK_DP = 4f;
	private static final float THUMB_DP = 14f;
	private static final float STEP = 0.1f;
	private static final int TRACK_ALPHA = 0x4D;

	private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rect = new RectF();
	private final float trackWidth;
	private final float thumbRadius;
	private float level;
	@Nullable private Listener listener;

	public LevelSlider(@NonNull Context context) {
		this(context, null);
	}

	public LevelSlider(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		float density = getResources().getDisplayMetrics().density;
		trackWidth = TRACK_DP * density;
		thumbRadius = THUMB_DP * density / 2;
		int white = ContextCompat.getColor(context, android.R.color.white);
		track.setColor(white);
		track.setAlpha(TRACK_ALPHA);
		fill.setColor(ContextCompat.getColor(context, R.color.red));
		thumb.setColor(white);
		setFocusable(true);
		setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
	}

	void setListener(@Nullable Listener listener) {
		this.listener = listener;
	}

	float level() {
		return level;
	}

	void setLevel(float value) {
		float clamped = clamp(value);
		if (clamped != level) {
			level = clamped;
			invalidate();
		}
	}

	private void setLevelFromUser(float value) {
		float clamped = clamp(value);
		if (clamped == level) {
			return;
		}
		level = clamped;
		invalidate();
		if (listener != null) {
			listener.onLevelChanged(this, level, true);
		}
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	private float top() {
		return getPaddingTop() + thumbRadius;
	}

	private float bottom() {
		return getHeight() - getPaddingBottom() - thumbRadius;
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);
		float cx = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
		float top = top();
		float bottom = bottom();
		if (bottom <= top) {
			return;
		}
		float half = trackWidth / 2;
		float y = bottom - (bottom - top) * level;
		rect.set(cx - half, top, cx + half, bottom);
		canvas.drawRoundRect(rect, half, half, track);
		rect.set(cx - half, y, cx + half, bottom);
		canvas.drawRoundRect(rect, half, half, fill);
		canvas.drawCircle(cx, y, thumbRadius, thumb);
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		if (!isEnabled()) {
			return false;
		}
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				if (getParent() != null) {
					getParent().requestDisallowInterceptTouchEvent(true);
				}
				setPressed(true);
				if (listener != null) {
					listener.onTracking(true);
				}
				setLevelFromUser(levelAt(event.getY()));
				return true;
			case MotionEvent.ACTION_MOVE:
				setLevelFromUser(levelAt(event.getY()));
				return true;
			case MotionEvent.ACTION_UP:
				setPressed(false);
				if (listener != null) {
					listener.onTracking(false);
				}
				performClick();
				return true;
			case MotionEvent.ACTION_CANCEL:
				setPressed(false);
				if (listener != null) {
					listener.onTracking(false);
				}
				return true;
			default:
				return super.onTouchEvent(event);
		}
	}

	@Override
	public boolean performClick() {
		return super.performClick();
	}

	private float levelAt(float y) {
		float top = top();
		float bottom = bottom();
		return bottom <= top ? level : (bottom - y) / (bottom - top);
	}

	@Override
	public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
		super.onInitializeAccessibilityNodeInfo(info);
		info.setClassName("android.widget.SeekBar");
		info.setRangeInfo(rangeInfo(level * 100f));
		if (isEnabled()) {
			if (level > 0f) {
				info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
			}
			if (level < 1f) {
				info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
			}
			info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
		}
	}

	@SuppressWarnings("deprecation")
	private static AccessibilityNodeInfo.RangeInfo rangeInfo(float percent) {
		int type = AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return new AccessibilityNodeInfo.RangeInfo(type, 0f, 100f, percent);
		}
		return AccessibilityNodeInfo.RangeInfo.obtain(type, 0f, 100f, percent);
	}

	@Override
	public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
		if (isEnabled()) {
			if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
				setLevelFromUser(level + STEP);
				return true;
			}
			if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
				setLevelFromUser(level - STEP);
				return true;
			}
			if (action == android.R.id.accessibilityActionSetProgress && arguments != null) {
				setLevelFromUser(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) / 100f);
				return true;
			}
		}
		return super.performAccessibilityAction(action, arguments);
	}
}
