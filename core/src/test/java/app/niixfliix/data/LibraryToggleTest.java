package app.niixfliix.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.ui.common.LibraryToggle;
import app.niixfliix.ui.common.PosterItem;

public class LibraryToggleTest {

	@Test
	public void aPosterIsSavedThenRemoved() {
		Library library = new Library(new MemoryStore(), new Gson());
		PosterItem poster = new PosterItem("tt1", "movie", "Tenzing", "https://img.example/p.jpg", -1);
		assertTrue(LibraryToggle.toggle(library, poster));
		assertTrue(library.contains("tt1"));
		LibraryItem saved = library.all().get(0);
		assertEquals("Tenzing", saved.name);
		assertEquals("movie", saved.type);
		assertEquals("https://img.example/p.jpg", saved.poster);
		assertTrue(saved.addedAt > 0);
		assertFalse(LibraryToggle.toggle(library, poster));
		assertFalse(library.contains("tt1"));
		assertTrue(library.all().isEmpty());
	}
}
