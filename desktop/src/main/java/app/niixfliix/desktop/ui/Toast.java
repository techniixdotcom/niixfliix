package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

import app.niixfliix.util.Logs;

public final class Toast {

	private static Consumer<String> host = text -> Logs.w("Toast", text, null);

	private Toast() {
	}

	public static void setHost(@NonNull Consumer<String> show) {
		host = show;
	}

	public static void show(@NonNull String text) {
		host.accept(text);
	}
}
