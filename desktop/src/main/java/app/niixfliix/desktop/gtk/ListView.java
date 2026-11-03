package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ListView<W extends ListView.Row> extends Widget {

	public interface Row {
		@NonNull
		Widget widget();

		void bind(int position);
	}

	private static final int HORIZONTAL = 0;
	private static final int SCROLL_NONE = 0;

	private static final Fn STRING_LIST_NEW = Fn.of(Lib.GTK, "gtk_string_list_new", PTR, PTR);
	private static final Fn SPLICE = Fn.of(Lib.GTK, "gtk_string_list_splice", null, PTR, INT, INT, PTR);
	private static final Fn NO_SELECTION = Fn.of(Lib.GTK, "gtk_no_selection_new", PTR, PTR);
	private static final Fn FACTORY_NEW = Fn.of(Lib.GTK, "gtk_signal_list_item_factory_new", PTR);
	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_list_view_new", PTR, PTR, PTR);
	private static final Fn ORIENTATION = Fn.of(Lib.GTK, "gtk_orientable_set_orientation", null, PTR, INT);
	private static final Fn SCROLL_TO = Fn.of(Lib.GTK, "gtk_list_view_scroll_to", null, PTR, INT, INT, PTR);
	private static final Fn SET_CHILD = Fn.of(Lib.GTK, "gtk_list_item_set_child", null, PTR, PTR);
	private static final Fn POSITION = Fn.of(Lib.GTK, "gtk_list_item_get_position", INT, PTR);
	private static final Fn ITEM_ACTIVATABLE = Fn.of(Lib.GTK, "gtk_list_item_set_activatable", null, PTR, INT);
	private static final Fn ITEM_SELECTABLE = Fn.of(Lib.GTK, "gtk_list_item_set_selectable", null, PTR, INT);
	private static final Fn ITEM_FOCUSABLE = Fn.of(Lib.GTK, "gtk_list_item_set_focusable", null, PTR, INT);

	private final Model model;
	private final Map<Long, W> rows = new HashMap<>();
	private int count;

	private ListView(MemorySegment pointer, Model model) {
		super(pointer);
		this.model = model;
	}

	private static final class Model extends GObj {
		Model(MemorySegment pointer) {
			super(pointer);
		}
	}

	private static final class Factory extends GObj {
		Factory(MemorySegment pointer) {
			super(pointer);
		}
	}

	@NonNull
	public static <W extends Row> ListView<W> create(boolean horizontal, @NonNull Supplier<W> make) {
		Model model = new Model(STRING_LIST_NEW.p(MemorySegment.NULL));
		Factory factory = new Factory(FACTORY_NEW.p());
		MemorySegment selection = NO_SELECTION.p(model);
		ListView<W> view = new ListView<>(NEW.p(selection, factory), model);
		if (horizontal) {
			ORIENTATION.v(view, HORIZONTAL);
		}
		factory.onPtr("setup", item -> {
			W row = make.get();
			view.rows.put(item.address(), row);
			SET_CHILD.v(item, row.widget());
			ITEM_ACTIVATABLE.v(item, false);
			ITEM_SELECTABLE.v(item, false);
			if (ITEM_FOCUSABLE.exists()) {
				ITEM_FOCUSABLE.v(item, false);
			}
		});
		factory.onPtr("bind", item -> {
			W row = view.rows.get(item.address());
			if (row != null) {
				row.bind(POSITION.i(item));
			}
		});
		factory.onPtr("teardown", item -> view.rows.remove(item.address()));
		view.addClass("plain-list");
		return view;
	}

	public int count() {
		return count;
	}

	public void changed(int position, int removed, int added) {
		if (!model.alive()) {
			return;
		}
		String[] blanks = new String[added];
		Arrays.fill(blanks, "");
		count += added - removed;
		SPLICE.v(model, position, removed, blanks);
	}

	public void replaceAll(int newCount) {
		changed(0, count, newCount);
	}

	public void rebindAll() {
		for (Map.Entry<Long, W> e : rows.entrySet()) {
			int position = POSITION.i(MemorySegment.ofAddress(e.getKey()));
			if (position >= 0 && position < count) {
				e.getValue().bind(position);
			}
		}
	}

	public void scrollTo(int position) {
		if (position >= 0 && position < count && SCROLL_TO.exists()) {
			SCROLL_TO.v(this, position, SCROLL_NONE, MemorySegment.NULL);
		}
	}
}
