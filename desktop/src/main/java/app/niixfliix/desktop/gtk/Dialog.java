package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

public final class Dialog extends Widget {

	public static final int APPEARANCE_DEFAULT = 0;
	public static final int APPEARANCE_SUGGESTED = 1;
	public static final int APPEARANCE_DESTRUCTIVE = 2;

	private static final Fn DIALOG_NEW = Fn.of(Lib.ADW, "adw_dialog_new", PTR);
	private static final Fn SET_CHILD = Fn.of(Lib.ADW, "adw_dialog_set_child", null, PTR, PTR);
	private static final Fn SET_TITLE = Fn.of(Lib.ADW, "adw_dialog_set_title", null, PTR, PTR);
	private static final Fn CONTENT_WIDTH = Fn.of(Lib.ADW, "adw_dialog_set_content_width", null, PTR, INT);
	private static final Fn CONTENT_HEIGHT = Fn.of(Lib.ADW, "adw_dialog_set_content_height", null, PTR, INT);
	private static final Fn FOLLOWS_CONTENT = Fn.of(Lib.ADW, "adw_dialog_set_follows_content_size", null, PTR, INT);
	private static final Fn CAN_CLOSE = Fn.of(Lib.ADW, "adw_dialog_set_can_close", null, PTR, INT);
	private static final Fn PRESENT = Fn.of(Lib.ADW, "adw_dialog_present", null, PTR, PTR);
	private static final Fn CLOSE = Fn.of(Lib.ADW, "adw_dialog_close", INT, PTR);
	private static final Fn FORCE_CLOSE = Fn.of(Lib.ADW, "adw_dialog_force_close", null, PTR);
	private static final Fn DEFAULT_WIDGET = Fn.of(Lib.ADW, "adw_dialog_set_default_widget", null, PTR, PTR);
	private static final Fn FOCUS = Fn.of(Lib.ADW, "adw_dialog_set_focus", null, PTR, PTR);
	private static final Fn ALERT_NEW = Fn.of(Lib.ADW, "adw_alert_dialog_new", PTR, PTR, PTR);
	private static final Fn ADD_RESPONSE = Fn.of(Lib.ADW, "adw_alert_dialog_add_response", null, PTR, PTR, PTR);
	private static final Fn APPEARANCE = Fn.of(Lib.ADW, "adw_alert_dialog_set_response_appearance", null,
			PTR, PTR, INT);
	private static final Fn DEFAULT_RESPONSE = Fn.of(Lib.ADW, "adw_alert_dialog_set_default_response", null,
			PTR, PTR);
	private static final Fn CLOSE_RESPONSE = Fn.of(Lib.ADW, "adw_alert_dialog_set_close_response", null, PTR, PTR);
	private static final Fn EXTRA_CHILD = Fn.of(Lib.ADW, "adw_alert_dialog_set_extra_child", null, PTR, PTR);
	private static final Fn RESPONSE_ENABLED = Fn.of(Lib.ADW, "adw_alert_dialog_set_response_enabled", null,
			PTR, PTR, INT);

	private static int open;

	private final boolean alert;
	private boolean closed;

	private Dialog(MemorySegment pointer, boolean alert) {
		super(pointer);
		this.alert = alert;
		on("closed", () -> {
			if (!closed) {
				closed = true;
				open--;
			}
		});
	}

	@NonNull
	public static Dialog create(@NonNull String title, @NonNull Widget content, int width) {
		Dialog d = new Dialog(DIALOG_NEW.p(), false);
		SET_TITLE.v(d, title);
		SET_CHILD.v(d, content);
		CONTENT_WIDTH.v(d, width);
		if (FOLLOWS_CONTENT.exists()) {
			FOLLOWS_CONTENT.v(d, false);
		}
		return d;
	}

	@NonNull
	public static Dialog alert(@Nullable String heading, @Nullable String body) {
		return new Dialog(ALERT_NEW.p(heading, body), true);
	}

	public static boolean anyOpen() {
		return open > 0;
	}

	public Dialog followsContent(boolean follows) {
		if (FOLLOWS_CONTENT.exists()) {
			FOLLOWS_CONTENT.v(this, follows);
		}
		return this;
	}

	public Dialog contentHeight(int height) {
		CONTENT_HEIGHT.v(this, height);
		return this;
	}

	public Dialog canClose(boolean canClose) {
		CAN_CLOSE.v(this, canClose);
		return this;
	}

	public Dialog defaultWidget(@NonNull Widget widget) {
		DEFAULT_WIDGET.v(this, widget);
		return this;
	}

	public Dialog focus(@NonNull Widget widget) {
		FOCUS.v(this, widget);
		return this;
	}

	public Dialog response(@NonNull String id, @NonNull String label, int appearance) {
		if (alert) {
			ADD_RESPONSE.v(this, id, label);
			APPEARANCE.v(this, id, appearance);
		}
		return this;
	}

	public Dialog responseEnabled(@NonNull String id, boolean enabled) {
		if (alert) {
			RESPONSE_ENABLED.v(this, id, enabled);
		}
		return this;
	}

	public Dialog defaults(@Nullable String enter, @NonNull String escape) {
		if (alert) {
			DEFAULT_RESPONSE.v(this, enter);
			CLOSE_RESPONSE.v(this, escape);
		}
		return this;
	}

	public Dialog extra(@NonNull Widget child) {
		if (alert) {
			EXTRA_CHILD.v(this, child);
		}
		return this;
	}

	public Dialog onResponse(@NonNull Consumer<String> action) {
		onPtr("response", id -> action.accept(id.reinterpret(Long.MAX_VALUE).getString(0)));
		return this;
	}

	public Dialog onClosed(@NonNull Runnable action) {
		on("closed", action);
		return this;
	}

	public Dialog onCloseAttempt(@NonNull Runnable action) {
		on("close-attempt", action);
		return this;
	}

	public Dialog present(@NonNull Widget parent) {
		if (alive()) {
			open++;
			PRESENT.v(this, parent);
		}
		return this;
	}

	public void close() {
		CLOSE.i(this);
	}

	public void forceClose() {
		FORCE_CLOSE.v(this);
	}

	public boolean isClosed() {
		return closed || !alive();
	}

	public Dialog title(@NonNull String title) {
		SET_TITLE.v(this, title);
		return this;
	}

	public Dialog child(@NonNull Widget content) {
		SET_CHILD.v(this, content);
		return this;
	}
}
