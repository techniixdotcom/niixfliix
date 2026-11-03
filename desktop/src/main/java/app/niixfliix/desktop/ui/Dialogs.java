package app.niixfliix.desktop.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import app.niixfliix.desktop.gtk.Bars;
import app.niixfliix.desktop.gtk.Box;
import app.niixfliix.desktop.gtk.Button;
import app.niixfliix.desktop.gtk.Check;
import app.niixfliix.desktop.gtk.Dialog;
import app.niixfliix.desktop.gtk.Entry;
import app.niixfliix.desktop.gtk.Label;
import app.niixfliix.desktop.gtk.Scroller;
import app.niixfliix.desktop.gtk.Widget;
import app.niixfliix.desktop.i18n.I18n;

public final class Dialogs {

	private static final String OK = "ok";
	private static final String CANCEL = "cancel";
	private static final String OTHER = "other";
	private static final int LONG_MESSAGE = 600;
	private static final int MESSAGE_MAX_HEIGHT = 440;
	private static final int LIST_MAX_HEIGHT = 420;

	private Dialogs() {
	}

	public static boolean isOpen() {
		return Dialog.anyOpen();
	}

	@NonNull
	private static Widget parent() {
		return MainWindow.get().root();
	}

	public static void message(@Nullable String title, @NonNull String message) {
		Dialog d;
		if (message.length() > LONG_MESSAGE) {
			Label text = Label.wrapping(message).selectable();
			d = Dialog.alert(title, null).extra(Scroller.vertical(text).fitContent(MESSAGE_MAX_HEIGHT));
		} else {
			d = Dialog.alert(title, message);
		}
		d.response(OK, I18n.t("action_ok"), Dialog.APPEARANCE_DEFAULT).defaults(OK, OK).present(parent());
	}

	public static void confirm(@Nullable String title, @NonNull String message, @NonNull String action,
			@NonNull Runnable onConfirm) {
		Dialog.alert(title, message)
				.response(CANCEL, I18n.t("action_cancel"), Dialog.APPEARANCE_DEFAULT)
				.response(OK, action, Dialog.APPEARANCE_SUGGESTED)
				.defaults(OK, CANCEL)
				.onResponse(id -> {
					if (OK.equals(id)) {
						onConfirm.run();
					}
				})
				.present(parent());
	}

	public static void either(@Nullable String title, @NonNull String message, @NonNull String first,
			@NonNull Runnable onFirst, @NonNull String second, @NonNull Runnable onSecond) {
		Dialog.alert(title, message)
				.response(CANCEL, I18n.t("action_cancel"), Dialog.APPEARANCE_DEFAULT)
				.response(OTHER, first, Dialog.APPEARANCE_DEFAULT)
				.response(OK, second, Dialog.APPEARANCE_SUGGESTED)
				.defaults(OK, CANCEL)
				.onResponse(id -> {
					if (OK.equals(id)) {
						onSecond.run();
					} else if (OTHER.equals(id)) {
						onFirst.run();
					}
				})
				.present(parent());
	}

	public static void choose(@NonNull String title, @NonNull List<String> options, int selected,
			@NonNull IntConsumer onChosen) {
		Dialog d = Dialog.alert(title, null);
		Box list = Box.column(2);
		Check first = null;
		Check focus = null;
		for (int i = 0; i < options.size(); i++) {
			Check option = Check.of(options.get(i), i == selected);
			if (first == null) {
				first = option;
			} else {
				option.group(first);
			}
			if (i == selected || focus == null) {
				focus = option;
			}
			int index = i;
			option.onToggled(() -> {
				if (option.isActive() && index != selected) {
					d.close();
					onChosen.accept(index);
				}
			});
			option.addClass("choice");
			list.add(option);
		}
		d.extra(Scroller.vertical(list).fitContent(LIST_MAX_HEIGHT))
				.response(CANCEL, I18n.t("action_cancel"), Dialog.APPEARANCE_DEFAULT)
				.defaults(null, CANCEL);
		if (focus != null) {
			d.focus(focus);
		}
		d.present(parent());
	}

	public static void prompt(@NonNull String title, @Nullable String message, @NonNull String hint,
			@NonNull String action, @NonNull Consumer<String> onOk) {
		Entry field = Entry.plain(hint);
		Dialog d = Dialog.alert(title, message);
		d.extra(field)
				.response(CANCEL, I18n.t("action_cancel"), Dialog.APPEARANCE_DEFAULT)
				.response(OK, action, Dialog.APPEARANCE_SUGGESTED)
				.defaults(OK, CANCEL)
				.responseEnabled(OK, false)
				.focus(field);
		field.onChanged(() -> d.responseEnabled(OK, !field.text().trim().isEmpty()));
		field.onActivate(() -> {
			String value = field.text().trim();
			if (!value.isEmpty()) {
				d.close();
				onOk.accept(value);
			}
		});
		d.onResponse(id -> {
			String value = field.text().trim();
			if (OK.equals(id) && !value.isEmpty()) {
				onOk.accept(value);
			}
		});
		d.present(parent());
	}

	@NonNull
	public static Dialog custom(@NonNull String title, @NonNull Widget body, int width, @NonNull Button... actions) {
		return custom(title, body, width, true, actions);
	}

	@NonNull
	public static Dialog locked(@NonNull String title, @NonNull Widget body, int width, @NonNull Button... actions) {
		return custom(title, body, width, false, actions);
	}

	private static Dialog custom(String title, Widget body, int width, boolean closable, Button... actions) {
		body.margins(6, 18, 18, 18);
		Scroller scroll = Scroller.vertical(body).fitContent(-1);
		Box buttons = null;
		if (actions.length > 0) {
			buttons = Box.row(8);
			buttons.addClass("dialog-actions");
			buttons.halign(Widget.ALIGN_END);
			buttons.add(actions);
		}
		Dialog d = Dialog.create(title, Bars.of(Bars.header(null, closable), scroll, buttons), width);
		d.canClose(closable);
		d.present(parent());
		return d;
	}

	@NonNull
	public static Button button(@NonNull String label, boolean primary) {
		return primary ? Button.text(label, "suggested-action") : Button.text(label);
	}
}
