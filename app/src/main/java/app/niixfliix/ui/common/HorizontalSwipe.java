package app.niixfliix.ui.common;

import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public final class HorizontalSwipe implements RecyclerView.OnItemTouchListener {

	private static final float HORIZONTAL_BIAS = 0.6f;

	private final int slop;
	private float startX;
	private float startY;
	private boolean decided;

	private HorizontalSwipe(int slop) {
		this.slop = slop;
	}

	public static void attach(@NonNull RecyclerView list) {
		list.addOnItemTouchListener(new HorizontalSwipe(ViewConfiguration.get(list.getContext()).getScaledTouchSlop()));
	}

	@Override
	public boolean onInterceptTouchEvent(@NonNull RecyclerView list, @NonNull MotionEvent e) {
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				startX = e.getX();
				startY = e.getY();
				decided = false;
				holdParent(list, true);
				break;
			case MotionEvent.ACTION_MOVE:
				if (decided) {
					break;
				}
				float dx = e.getX() - startX;
				float dy = e.getY() - startY;
				float ax = Math.abs(dx);
				float ay = Math.abs(dy);
				if (ax <= slop && ay <= slop) {
					break;
				}
				decided = true;
				boolean sideways = ax >= ay * HORIZONTAL_BIAS;
				boolean canMove = dx != 0 && list.canScrollHorizontally(dx < 0 ? 1 : -1);
				holdParent(list, sideways && canMove);
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				decided = false;
				holdParent(list, false);
				break;
			default:
				break;
		}
		return false;
	}

	private static void holdParent(@NonNull RecyclerView list, boolean hold) {
		ViewParent parent = list.getParent();
		if (parent != null) {
			parent.requestDisallowInterceptTouchEvent(hold);
		}
	}

	@Override
	public void onTouchEvent(@NonNull RecyclerView list, @NonNull MotionEvent e) {
	}

	@Override
	public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
	}
}
