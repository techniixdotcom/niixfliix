package app.niixfliix.ui.common;

import android.content.Context;

import androidx.annotation.NonNull;

public final class Grids {

	private static final int POSTER_MIN_WIDTH_DP = 120;

	private Grids() {
	}

	public static int posterSpan(@NonNull Context context) {
		int widthDp = context.getResources().getConfiguration().screenWidthDp;
		return Math.max(3, widthDp / POSTER_MIN_WIDTH_DP);
	}
}
