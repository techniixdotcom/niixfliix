package app.niixfliix.desktop.gtk;

import static app.niixfliix.desktop.platform.Native.LONG;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

import app.niixfliix.desktop.platform.GLib;
import app.niixfliix.desktop.platform.Native;

public final class FileChooser {

	private static final Fn NEW = Fn.of(Lib.GTK, "gtk_file_dialog_new", PTR);
	private static final Fn TITLE = Fn.of(Lib.GTK, "gtk_file_dialog_set_title", null, PTR, PTR);
	private static final Fn FILTERS = Fn.of(Lib.GTK, "gtk_file_dialog_set_filters", null, PTR, PTR);
	private static final Fn INITIAL_FOLDER = Fn.of(Lib.GTK, "gtk_file_dialog_set_initial_folder", null, PTR, PTR);
	private static final Fn OPEN = Fn.of(Lib.GTK, "gtk_file_dialog_open", null, PTR, PTR, PTR, PTR, PTR);
	private static final Fn OPEN_FINISH = Fn.of(Lib.GTK, "gtk_file_dialog_open_finish", PTR, PTR, PTR, PTR);
	private static final Fn SELECT_FOLDER = Fn.of(Lib.GTK, "gtk_file_dialog_select_folder", null, PTR, PTR, PTR, PTR,
			PTR);
	private static final Fn SELECT_FOLDER_FINISH = Fn.of(Lib.GTK, "gtk_file_dialog_select_folder_finish", PTR, PTR,
			PTR, PTR);
	private static final Fn FILTER_NEW = Fn.of(Lib.GTK, "gtk_file_filter_new", PTR);
	private static final Fn FILTER_NAME = Fn.of(Lib.GTK, "gtk_file_filter_set_name", null, PTR, PTR);
	private static final Fn FILTER_PATTERN = Fn.of(Lib.GTK, "gtk_file_filter_add_pattern", null, PTR, PTR);
	private static final Fn FILTER_TYPE = Fn.of(Lib.GTK, "gtk_file_filter_get_type", LONG);
	private static final Fn STORE_NEW = Fn.of(Lib.GIO, "g_list_store_new", PTR, LONG);
	private static final Fn STORE_APPEND = Fn.of(Lib.GIO, "g_list_store_append", null, PTR, PTR);
	private static final Fn FILE_FOR_PATH = Fn.of(Lib.GIO, "g_file_new_for_path", PTR, PTR);
	private static final Fn FILE_PATH = Fn.of(Lib.GIO, "g_file_get_path", PTR, PTR);
	private static final Fn UNREF = Fn.of(Lib.GOBJECT, "g_object_unref", null, PTR);
	private static final Fn ROOT = Fn.of(Lib.GTK, "gtk_widget_get_root", PTR, PTR);

	private FileChooser() {
	}

	public static final class Filter {
		final String name;
		final String[] patterns;

		public Filter(@NonNull String name, @NonNull String... patterns) {
			this.name = name;
			this.patterns = patterns;
		}
	}

	public static void open(@NonNull Widget parent, @NonNull String title, @Nullable File folder,
			@NonNull Consumer<File> onPicked, @NonNull Filter... filters) {
		MemorySegment dialog = NEW.p();
		TITLE.v(dialog, title);
		MemorySegment store = STORE_NEW.p(FILTER_TYPE.l());
		for (Filter f : filters) {
			MemorySegment filter = FILTER_NEW.p();
			FILTER_NAME.v(filter, f.name);
			for (String pattern : f.patterns) {
				FILTER_PATTERN.v(filter, pattern);
			}
			STORE_APPEND.v(store, filter);
			UNREF.v(filter);
		}
		FILTERS.v(dialog, store);
		UNREF.v(store);
		startIn(dialog, folder);
		OPEN.v(dialog, parentWindow(parent), MemorySegment.NULL, Signals.ASYNC, picked(OPEN_FINISH, onPicked));
	}

	public static void folder(@NonNull Widget parent, @NonNull String title, @Nullable File folder,
			@NonNull Consumer<File> onPicked) {
		MemorySegment dialog = NEW.p();
		TITLE.v(dialog, title);
		startIn(dialog, folder);
		SELECT_FOLDER.v(dialog, parentWindow(parent), MemorySegment.NULL, Signals.ASYNC,
				picked(SELECT_FOLDER_FINISH, onPicked));
	}

	private static void startIn(MemorySegment dialog, @Nullable File folder) {
		if (folder != null && folder.isDirectory()) {
			MemorySegment file = FILE_FOR_PATH.p(folder.getAbsolutePath());
			INITIAL_FOLDER.v(dialog, file);
			UNREF.v(file);
		}
	}

	private static MemorySegment picked(Fn finish, Consumer<File> onPicked) {
		return Signals.async((source, result) -> {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment error = arena.allocate(PTR);
				MemorySegment file = finish.p(source, result, error);
				GLib.takeError(error);
				if (!Native.isNull(file)) {
					MemorySegment path = FILE_PATH.p(file);
					String text = Native.string(path);
					GLib.free(path);
					UNREF.v(file);
					if (text != null) {
						onPicked.accept(new File(text));
					}
				}
			} finally {
				UNREF.v(source);
			}
		});
	}

	private static MemorySegment parentWindow(Widget parent) {
		if (!parent.alive()) {
			return MemorySegment.NULL;
		}
		return ROOT.p(parent);
	}
}
