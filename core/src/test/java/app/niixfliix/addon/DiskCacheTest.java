package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

public class DiskCacheTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void keepsWhatWasWrittenUnderAHashedName() throws Exception {
		File dir = new File(temp.getRoot(), "catalogs");
		DiskCache cache = new DiskCache(dir, 1 << 20);
		String url = "https://addon.example/key=secret/catalog/movie/top.json";
		assertNull(cache.read(url, 1 << 20));
		cache.write(url, "{\"metas\":[]}");
		DiskCache.Entry entry = cache.read(url, 1 << 20);
		assertNotNull(entry);
		assertEquals("{\"metas\":[]}", entry.body);
		assertTrue(entry.ageMs < 60_000);
		String[] names = dir.list();
		assertNotNull(names);
		assertEquals(1, names.length);
		assertFalse(names[0].contains("secret"));
		assertTrue(names[0].matches("[0-9a-f]{64}\\.json"));
		assertNull(cache.read(url, 4));
		cache.remove(url);
		assertNull(cache.read(url, 1 << 20));
	}

	@Test
	public void theOldestGoWhenItGrowsTooBig() throws Exception {
		File dir = new File(temp.getRoot(), "catalogs");
		DiskCache cache = new DiskCache(dir, 25);
		char[] ten = new char[10];
		Arrays.fill(ten, 'x');
		String body = new String(ten);
		cache.write("a", body);
		new File(dir.listFiles()[0].getPath()).setLastModified(System.currentTimeMillis() - 60_000);
		cache.write("b", body);
		cache.write("c", body);
		assertNull(cache.read("a", 100));
		assertNotNull(cache.read("b", 100));
		assertNotNull(cache.read("c", 100));
	}
}
