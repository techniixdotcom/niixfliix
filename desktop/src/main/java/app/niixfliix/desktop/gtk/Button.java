package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;

public final class Button extends Widget {

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_button_new", PTR);
	private static final Fn TOGGLE_NEW = Fn.of(Lib.GTK, "gtk_toggle_button_new", PTR);
	private static final Fn SET_LABEL = Fn.of(Lib.GTK, "gtk_button_set_label", null, PTR, PTR);
	private static final Fn SET_ICON = Fn.of(Lib.GTK, "gtk_button_set_icon_name", null, PTR, PTR);
	private static final Fn SET_CHILD = Fn.of(Lib.GTK, "gtk_button_set_child", null, PTR, PTR);
	private static final Fn HAS_FRAME = Fn.of(Lib.GTK, "gtk_button_set_has_frame", null, PTR, INT);
	private static final Fn SET_ACTIVE = Fn.of(Lib.GTK, "gtk_toggle_button_set_active", null, PTR, INT);
	private static final Fn GET_ACTIVE = Fn.of(Lib.GTK, "gtk_toggle_button_get_active", INT, PTR);
	private static final Fn SET_GROUP = Fn.of(Lib.GTK, "gtk_toggle_button_set_group", null, PTR, PTR);

	private static final int STACKED_ICON_SIZE = 24;
	private static final int SIDE_ICON_SIZE = 20;

	private final boolean toggle;
	@Nullable private Box content;
	@Nullable private Image icon;
	@Nullable private Label label;

	private Button(MemorySegment pointer, boolean toggle) {
		super(pointer);
		this.toggle = toggle;
	}

	@NonNull
	public static Button text(@NonNull String text, @NonNull String... classes) {
		Button b = new Button(NEW.p(), false);
		SET_LABEL.v(b, text);
		b.addClass(classes);
		return b;
	}

	@NonNull
	public static Button icon(@NonNull String iconName, @NonNull String tooltip) {
		Button b = new Button(NEW.p(), false);
		SET_ICON.v(b, iconName);
		b.tooltip(tooltip);
		b.accessibleLabel(tooltip);
		b.addClass("flat");
		return b;
	}

	@NonNull
	public static Button iconText(@NonNull String iconName, @NonNull String text, @NonNull String... classes) {
		Button b = new Button(NEW.p(), false);
		b.content(iconName, text, false);
		b.addClass(classes);
		return b;
	}

	@NonNull
	public static Button wrapping(@NonNull Widget child, @NonNull String... classes) {
		Button b = new Button(NEW.p(), false);
		SET_CHILD.v(b, child);
		b.addClass(classes);
		return b;
	}

	@NonNull
	public static Button toggle(@NonNull String text, @NonNull String... classes) {
		Button b = new Button(TOGGLE_NEW.p(), true);
		SET_LABEL.v(b, text);
		b.addClass(classes);
		return b;
	}

	@NonNull
	public static Button toggleStacked(@NonNull String iconName, @NonNull String text, @NonNull String... classes) {
		Button b = new Button(TOGGLE_NEW.p(), true);
		b.content(iconName, text, true);
		b.addClass(classes);
		return b;
	}

	private void content(String iconName, String text, boolean stacked) {
		icon = Image.icon(iconName);
		label = Label.of(text);
		if (stacked) {
			label.center();
			icon.pixels(STACKED_ICON_SIZE);
			content = Box.column(4, icon, label);
		} else {
			content = Box.row(8, icon, label);
			content.halign(ALIGN_CENTER);
		}
		SET_CHILD.v(this, content);
	}

	public Button stacked(boolean stacked) {
		if (content == null || icon == null || label == null) {
			return this;
		}
		content.vertical(stacked, stacked ? 4 : 14);
		icon.pixels(stacked ? STACKED_ICON_SIZE : SIDE_ICON_SIZE);
		if (stacked) {
			label.center();
			content.halign(ALIGN_FILL);
		} else {
			label.start();
			content.halign(ALIGN_START);
		}
		return this;
	}

	public Button label(@NonNull String text) {
		if (label != null) {
			label.text(text);
		} else {
			SET_LABEL.v(this, text);
		}
		return this;
	}

	public Button iconName(@NonNull String iconName) {
		if (icon != null) {
			icon.name(iconName);
		} else {
			SET_ICON.v(this, iconName);
		}
		return this;
	}

	public Button flat() {
		HAS_FRAME.v(this, false);
		return this;
	}

	public Button onClick(@NonNull Runnable action) {
		on("clicked", action);
		return this;
	}

	public Button active(boolean active) {
		if (toggle) {
			SET_ACTIVE.v(this, active);
		}
		return this;
	}

	public boolean isActive() {
		return toggle && GET_ACTIVE.b(this);
	}

	public Button group(@NonNull Button first) {
		if (toggle && first != this) {
			SET_GROUP.v(this, first);
		}
		return this;
	}

	public Button onToggled(@NonNull Runnable action) {
		on("toggled", action);
		return this;
	}
}
