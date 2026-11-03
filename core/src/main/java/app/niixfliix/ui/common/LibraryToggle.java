package app.niixfliix.ui.common;

import androidx.annotation.NonNull;

import app.niixfliix.data.Library;
import app.niixfliix.data.model.LibraryItem;

public final class LibraryToggle {

	private LibraryToggle() {
	}

	public static boolean toggle(@NonNull Library library, @NonNull PosterItem poster) {
		if (library.contains(poster.id)) {
			library.remove(poster.id);
			return false;
		}
		LibraryItem item = new LibraryItem();
		item.id = poster.id;
		item.type = poster.type;
		item.name = poster.name;
		item.poster = poster.poster;
		item.released = poster.released;
		library.add(item);
		return true;
	}
}
