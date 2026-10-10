package app.niixfliix.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class WindowPadding {

	public static final int LEFT = 1;
	public static final int TOP = 2;
	public static final int RIGHT = 4;
	public static final int BOTTOM = 8;

	private WindowPadding() {
	}

	public static void apply(@NonNull View view, int sides) {
		int left = view.getPaddingLeft();
		int top = view.getPaddingTop();
		int right = view.getPaddingRight();
		int bottom = view.getPaddingBottom();
		ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
			Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.displayCutout());
			v.setPadding(
					left + ((sides & LEFT) != 0 ? bars.left : 0),
					top + ((sides & TOP) != 0 ? bars.top : 0),
					right + ((sides & RIGHT) != 0 ? bars.right : 0),
					bottom + ((sides & BOTTOM) != 0 ? bars.bottom : 0));
			return insets;
		});
	}
}
