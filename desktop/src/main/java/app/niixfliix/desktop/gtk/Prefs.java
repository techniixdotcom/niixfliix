package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class Prefs {

	private static final int NONE = 0xFFFFFFFF;

	private static final Fn PAGE_NEW = Fn.of(Lib.ADW, "adw_preferences_page_new", PTR);
	private static final Fn PAGE_ADD = Fn.of(Lib.ADW, "adw_preferences_page_add", null, PTR, PTR);
	private static final Fn GROUP_NEW = Fn.of(Lib.ADW, "adw_preferences_group_new", PTR);
	private static final Fn GROUP_TITLE = Fn.of(Lib.ADW, "adw_preferences_group_set_title", null, PTR, PTR);
	private static final Fn GROUP_DESCRIPTION = Fn.of(Lib.ADW, "adw_preferences_group_set_description", null,
			PTR, PTR);
	private static final Fn GROUP_ADD = Fn.of(Lib.ADW, "adw_preferences_group_add", null, PTR, PTR);
	private static final Fn GROUP_REMOVE = Fn.of(Lib.ADW, "adw_preferences_group_remove", null, PTR, PTR);
	private static final Fn GROUP_SUFFIX = Fn.of(Lib.ADW, "adw_preferences_group_set_header_suffix", null, PTR, PTR);
	private static final Fn ROW_TITLE = Fn.of(Lib.ADW, "adw_preferences_row_set_title", null, PTR, PTR);
	private static final Fn ROW_MARKUP = Fn.of(Lib.ADW, "adw_preferences_row_set_use_markup", null, PTR, INT);
	private static final Fn ACTION_ROW_NEW = Fn.of(Lib.ADW, "adw_action_row_new", PTR);
	private static final Fn SUBTITLE = Fn.of(Lib.ADW, "adw_action_row_set_subtitle", null, PTR, PTR);
	private static final Fn ADD_SUFFIX = Fn.of(Lib.ADW, "adw_action_row_add_suffix", null, PTR, PTR);
	private static final Fn ADD_PREFIX = Fn.of(Lib.ADW, "adw_action_row_add_prefix", null, PTR, PTR);
	private static final Fn ACTIVATABLE = Fn.of(Lib.GTK, "gtk_list_box_row_set_activatable", null, PTR, INT);
	private static final Fn SWITCH_ROW_NEW = Fn.of(Lib.ADW, "adw_switch_row_new", PTR);
	private static final Fn SWITCH_SET = Fn.of(Lib.ADW, "adw_switch_row_set_active", null, PTR, INT);
	private static final Fn SWITCH_GET = Fn.of(Lib.ADW, "adw_switch_row_get_active", INT, PTR);
	private static final Fn COMBO_ROW_NEW = Fn.of(Lib.ADW, "adw_combo_row_new", PTR);
	private static final Fn COMBO_MODEL = Fn.of(Lib.ADW, "adw_combo_row_set_model", null, PTR, PTR);
	private static final Fn COMBO_GET = Fn.of(Lib.ADW, "adw_combo_row_get_selected", INT, PTR);
	private static final Fn COMBO_SET = Fn.of(Lib.ADW, "adw_combo_row_set_selected", null, PTR, INT);
	private static final Fn STRING_LIST_NEW = Fn.of(Lib.GTK, "gtk_string_list_new", PTR, PTR);
	private static final Fn UNREF = Fn.of(Lib.GOBJECT, "g_object_unref", null, PTR);
	private static final Fn PASSWORD_ROW_NEW = Fn.of(Lib.ADW, "adw_password_entry_row_new", PTR);
	private static final Fn ENTRY_ROW_NEW = Fn.of(Lib.ADW, "adw_entry_row_new", PTR);
	private static final Fn GET_TEXT = Fn.of(Lib.GTK, "gtk_editable_get_text", PTR, PTR);
	private static final Fn SET_TEXT = Fn.of(Lib.GTK, "gtk_editable_set_text", null, PTR, PTR);

	private Prefs() {
	}

	@NonNull
	public static Page page() {
		return new Page(PAGE_NEW.p());
	}

	@NonNull
	public static Group group(@Nullable String title, @Nullable String description) {
		Group g = new Group(GROUP_NEW.p());
		if (title != null) {
			GROUP_TITLE.v(g, title);
		}
		if (description != null) {
			GROUP_DESCRIPTION.v(g, description);
		}
		return g;
	}

	@NonNull
	public static Row row(@NonNull String title, @Nullable String subtitle, @Nullable Runnable onClick) {
		Row r = new Row(ACTION_ROW_NEW.p());
		titled(r, title);
		r.subtitle(subtitle);
		if (onClick != null) {
			ACTIVATABLE.v(r, true);
			r.on("activated", onClick);
		}
		return r;
	}

	@NonNull
	public static Switch toggle(@NonNull String title, @Nullable String subtitle, boolean on,
			@NonNull Consumer<Boolean> onToggled) {
		Switch s = new Switch(SWITCH_ROW_NEW.p());
		titled(s, title);
		if (subtitle != null) {
			SUBTITLE.v(s, subtitle);
		}
		SWITCH_SET.v(s, on);
		s.onNotify("active", () -> {
			if (!s.updating) {
				onToggled.accept(SWITCH_GET.b(s));
			}
		});
		return s;
	}

	@NonNull
	public static Combo combo(@NonNull String title, @NonNull List<String> choices, int selected,
			@NonNull IntConsumer onPicked) {
		Combo c = new Combo(COMBO_ROW_NEW.p());
		titled(c, title);
		c.choices(choices, selected);
		c.onNotify("selected", () -> {
			if (!c.updating) {
				int picked = COMBO_GET.i(c);
				if (picked != NONE) {
					onPicked.accept(picked);
				}
			}
		});
		return c;
	}

	@NonNull
	public static Field field(@NonNull String title, boolean secret) {
		Field f = new Field(secret ? PASSWORD_ROW_NEW.p() : ENTRY_ROW_NEW.p());
		titled(f, title);
		return f;
	}

	private static void titled(Widget row, String title) {
		ROW_MARKUP.v(row, false);
		ROW_TITLE.v(row, title);
	}

	public static final class Page extends Widget {
		Page(MemorySegment pointer) {
			super(pointer);
		}

		public Page add(@NonNull Group group) {
			PAGE_ADD.v(this, group);
			return this;
		}
	}

	public static final class Group extends Widget {
		Group(MemorySegment pointer) {
			super(pointer);
		}

		public Group add(@NonNull Widget row) {
			GROUP_ADD.v(this, row);
			return this;
		}

		public void remove(@NonNull Widget row) {
			GROUP_REMOVE.v(this, row);
		}

		public Group suffix(@NonNull Widget widget) {
			GROUP_SUFFIX.v(this, widget);
			return this;
		}
	}

	public static final class Row extends Widget {
		Row(MemorySegment pointer) {
			super(pointer);
		}

		public Row title(@NonNull String title) {
			ROW_TITLE.v(this, title);
			return this;
		}

		public Row subtitle(@Nullable String subtitle) {
			SUBTITLE.v(this, subtitle != null ? subtitle : "");
			return this;
		}

		public Row suffix(@NonNull Widget widget) {
			ADD_SUFFIX.v(this, widget);
			return this;
		}

		public Row prefix(@NonNull Widget widget) {
			ADD_PREFIX.v(this, widget);
			return this;
		}
	}

	public static final class Switch extends Widget {
		boolean updating;

		Switch(MemorySegment pointer) {
			super(pointer);
		}

		public void active(boolean on) {
			updating = true;
			try {
				SWITCH_SET.v(this, on);
			} finally {
				updating = false;
			}
		}
	}

	public static final class Combo extends Widget {
		boolean updating;

		Combo(MemorySegment pointer) {
			super(pointer);
		}

		public void choices(@NonNull List<String> choices, int selected) {
			if (!alive()) {
				return;
			}
			updating = true;
			try {
				MemorySegment model = STRING_LIST_NEW.p((Object) choices.toArray(new String[0]));
				COMBO_MODEL.v(this, model);
				UNREF.v(model);
				COMBO_SET.v(this, selected >= 0 && selected < choices.size() ? selected : NONE);
			} finally {
				updating = false;
			}
		}

		public int selected() {
			int selected = COMBO_GET.i(this);
			return selected == NONE ? -1 : selected;
		}

		public void select(int index) {
			updating = true;
			try {
				COMBO_SET.v(this, index);
			} finally {
				updating = false;
			}
		}
	}

	public static final class Field extends Widget {
		Field(MemorySegment pointer) {
			super(pointer);
		}

		@NonNull
		public String text() {
			String text = GET_TEXT.s(this);
			return text != null ? text : "";
		}

		public Field text(@NonNull String text) {
			SET_TEXT.v(this, text);
			return this;
		}

		public Field onEnter(@NonNull Runnable action) {
			on("entry-activated", action);
			return this;
		}
	}
}
