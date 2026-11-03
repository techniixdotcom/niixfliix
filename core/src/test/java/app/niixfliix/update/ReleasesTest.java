package app.niixfliix.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReleasesTest {

	@Test
	public void comparesDottedVersions() {
		assertTrue(Releases.isNewer("1.0.1", "1.0.0"));
		assertTrue(Releases.isNewer("1.10.0", "1.9.9"));
		assertTrue(Releases.isNewer("v2.0", "1.99.99"));
		assertFalse(Releases.isNewer("1.0.0", "1.0.0"));
		assertFalse(Releases.isNewer("1.0", "1.0.0"));
		assertFalse(Releases.isNewer("0.9.9", "1.0.0"));
		assertFalse(Releases.isNewer("1.0.0-beta", "1.0.0"));
		assertFalse(Releases.isNewer("garbage", "1.0.0"));
	}

	private static String asset(String name) {
		return "{\"name\":\"" + name + "\",\"size\":123,"
				+ "\"browser_download_url\":\"https://github.com/o/r/releases/download/x/" + name + "\"}";
	}

	private static String release(String tag, boolean draft, String... assets) {
		return "{\"tag_name\":\"" + tag + "\",\"draft\":" + draft + ",\"prerelease\":false,\"body\":\"notes\","
				+ "\"assets\":[" + String.join(",", assets) + "]}";
	}

	@Test
	public void picksTheNewestReleaseNotJustTheOneMarkedLatest() {
		String json = "[" + release("v1.1.0", false, asset("niixfliix1.1.0.apk"), asset("niixfliix1.1.0.apk.sha256"))
				+ "," + release("v1.2.0", false, asset("niixfliix1.2.0.apk"), asset("niixfliix1.2.0.apk.sha256"),
				asset("niixfliix1.2.0-mapping.txt"))
				+ "," + release("v9.0.0", true, asset("niixfliix9.0.0.apk"), asset("niixfliix9.0.0.apk.sha256")) + "]";
		Releases.CheckResult result = Releases.choose(json, "1.1.0", "niixfliix", ".apk");
		assertEquals(Releases.Status.UPDATE, result.status);
		assertEquals("1.2.0", result.release.version);
		assertTrue(result.release.fileUrl.endsWith("/niixfliix1.2.0.apk"));
		assertTrue(result.release.checksumUrl.endsWith("/niixfliix1.2.0.apk.sha256"));
		assertEquals(123, result.release.size);
		assertEquals("niixfliix1.2.0.apk", result.release.fileName);
	}

	@Test
	public void eachPlatformTakesItsOwnFile() {
		String json = "[" + release("v1.4.0", false, asset("niixfliix1.4.0.apk"), asset("niixfliix1.4.0.apk.sha256"),
				asset("niixfliix-1.4.0-x86_64.flatpak"), asset("niixfliix-1.4.0-x86_64.flatpak.sha256")) + "]";
		Releases.CheckResult linux = Releases.choose(json, "1.3.3", "niixfliix", ".flatpak");
		assertEquals(Releases.Status.UPDATE, linux.status);
		assertTrue(linux.release.fileUrl.endsWith("/niixfliix-1.4.0-x86_64.flatpak"));
		assertTrue(linux.release.checksumUrl.endsWith("/niixfliix-1.4.0-x86_64.flatpak.sha256"));
		Releases.CheckResult android = Releases.choose(json, "1.3.3", "niixfliix", ".apk");
		assertTrue(android.release.checksumUrl.endsWith("/niixfliix1.4.0.apk.sha256"));
		String apkOnly = "[" + release("v1.4.0", false, asset("niixfliix1.4.0.apk"), asset("niixfliix1.4.0.apk.sha256")) + "]";
		assertEquals(Releases.Status.NO_FILE, Releases.choose(apkOnly, "1.3.3", "niixfliix", ".flatpak").status);
	}

	@Test
	public void saysWhatTheReleaseIsMissing() {
		String noChecksum = "[" + release("v1.2.0", false, asset("niixfliix1.2.0.apk")) + "]";
		Releases.CheckResult result = Releases.choose(noChecksum, "1.1.0", "niixfliix", ".apk");
		assertEquals(Releases.Status.NO_CHECKSUM, result.status);
		assertEquals("1.2.0", result.version);

		String noApk = "[" + release("v1.2.0", false) + "]";
		assertEquals(Releases.Status.NO_FILE, Releases.choose(noApk, "1.1.0", "niixfliix", ".apk").status);

		assertEquals(Releases.Status.NO_RELEASES, Releases.choose("[]", "1.1.0", "niixfliix", ".apk").status);
		String debugOnly = "[" + release("v1.2.0", false, asset("niixfliix1.2.0-debug.apk")) + "]";
		assertEquals(Releases.Status.NO_FILE, Releases.choose(debugOnly, "1.1.0", "niixfliix", ".apk").status);
	}

	@Test
	public void upToDateAndVersionsFromNamesWhenTheTagHasNone() {
		String same = "[" + release("v1.1.2", false, asset("niixfliix1.1.2.apk"), asset("niixfliix1.1.2.apk.sha256")) + "]";
		assertEquals(Releases.Status.UP_TO_DATE, Releases.choose(same, "1.1.2", "niixfliix", ".apk").status);
		String oddTag = "[" + release("latest-build", false, asset("niixfliix1.3.0.apk"),
				asset("niixfliix1.3.0.apk.sha256")) + "]";
		Releases.CheckResult result = Releases.choose(oddTag, "1.1.2", "niixfliix", ".apk");
		assertEquals(Releases.Status.UPDATE, result.status);
		assertEquals("1.3.0", result.release.version);
		assertEquals(Releases.Status.NETWORK, Releases.choose("{\"message\":\"x\"}", "1.0", "niixfliix", ".apk").status);
		assertEquals(Releases.Status.NETWORK, Releases.choose("<html>", "1.0", "niixfliix", ".apk").status);
	}

	@Test
	public void versionsInText() {
		assertEquals("1.2.0", Releases.versionIn("v1.2.0"));
		assertEquals("1.2.0", Releases.versionIn("niixfliix-1.2.0"));
		assertEquals("1.10.3", Releases.versionIn("niixfliix1.10.3.apk"));
		assertNull(Releases.versionIn("latest"));
		assertNull(Releases.versionIn(null));
	}
}
