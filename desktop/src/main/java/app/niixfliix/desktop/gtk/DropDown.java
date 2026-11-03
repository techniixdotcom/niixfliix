package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.function.IntConsumer;

public final class DropDown extends Widget {

	private static final int NONE = 0xFFFFFFFF;

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_drop_down_new_from_strings", PTR, PTR);
	private static final Fn SET_MODEL = Fn.of(Lib.GTK, "gtk_drop_down_set_model", null, PTR, PTR);
	private static final Fn GET_SELECTED = Fn.of(Lib.GTK, "gtk_drop_down_get_selected", INT, PTR);
	private static final Fn SET_SELECTED = Fn.of(Lib.GTK, "gtk_drop_down_set_selected", null, PTR, INT);
	private static final Fn STRING_LIST_NEW = Fn.of(Lib.GTK, "gtk_string_list_new", PTR, PTR);
	private static final Fn UNREF = Fn.of(Lib.GOBJECT, "g_object_unref", null, PTR);

	private boolean updating;
	private int count;

	private DropDown(MemorySegment pointer) {
		super(pointer);
	}

	@NonNull
	public static DropDown of(@NonNull List<String> choices) {
		DropDown d = new DropDown(NEW.p((Object) choices.toArray(new String[0])));
		d.count = choices.size();
		return d;
	}

	public void choices(@NonNull List<String> choices, int selected) {
		if (!alive()) {
			return;
		}
		updating = true;
		try {
			MemorySegment model = STRING_LIST_NEW.p((Object) choices.toArray(new String[0]));
			SET_MODEL.v(this, model);
			UNREF.v(model);
			count = choices.size();
			select(selected);
		} finally {
			updating = false;
		}
	}

	public int selected() {
		int selected = GET_SELECTED.i(this);
		return selected == NONE ? -1 : selected;
	}

	public void select(int index) {
		boolean was = updating;
		updating = true;
		try {
			SET_SELECTED.v(this, index >= 0 && index < count ? index : NONE);
		} finally {
			updating = was;
		}
	}

	public DropDown onPicked(@NonNull IntConsumer action) {
		onNotify("selected", () -> {
			if (!updating) {
				action.accept(selected());
			}
		});
		return this;
	}
}
