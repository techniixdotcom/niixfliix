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
}
