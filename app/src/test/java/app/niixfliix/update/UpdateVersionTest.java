package app.niixfliix.update;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateVersionTest {

	@Test
	public void comparesDottedVersions() {
		assertTrue(UpdateManager.isNewer("1.0.1", "1.0.0"));
		assertTrue(UpdateManager.isNewer("1.10.0", "1.9.9"));
		assertTrue(UpdateManager.isNewer("v2.0", "1.99.99"));
		assertFalse(UpdateManager.isNewer("1.0.0", "1.0.0"));
		assertFalse(UpdateManager.isNewer("1.0", "1.0.0"));
		assertFalse(UpdateManager.isNewer("0.9.9", "1.0.0"));
		assertFalse(UpdateManager.isNewer("1.0.0-beta", "1.0.0"));
		assertFalse(UpdateManager.isNewer("garbage", "1.0.0"));
	}
}
