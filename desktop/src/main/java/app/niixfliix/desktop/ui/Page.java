package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;

import app.niixfliix.desktop.gtk.Widget;

public interface Page {

	@NonNull
	Widget view();

	default void onShow() {
	}

	default void onHide() {
	}

	/** Back (Escape, the mouse's back button) on a tab with nothing opened over it; true if it was used. */
	default boolean onBack() {
		return false;
	}
}
