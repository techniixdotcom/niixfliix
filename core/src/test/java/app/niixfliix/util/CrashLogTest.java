package app.niixfliix.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

public class CrashLogTest {

	private static String format(Throwable error) {
		return CrashLog.format(error, "main", "1.0.0 (10000)", "Android 15 (API 35)", "Google Pixel 9", 0L);
	}

	@Test
	public void crashHasTheStackTraceAndNoSecrets() {
		IOException cause = new IOException("refused https://torrentio.strem.fun/realdebrid=SECRETKEY99/manifest.json");
		IllegalStateException error = new IllegalStateException("sign-in failed for someone@example.com", cause);
		String log = format(error);
		assertTrue(log.startsWith("Time: 1970-01-01T00:00:00Z\nApp: 1.0.0 (10000)\nSystem: Android 15 (API 35)\nDevice: Google Pixel 9"));
		assertTrue(log.contains("java.lang.IllegalStateException: sign-in failed for ***@***"));
		assertTrue(log.contains("Caused by: java.io.IOException: refused https://torrentio.strem.fun/…"));
		assertTrue(log.contains("\tat app.niixfliix.util.CrashLogTest."));
		assertFalse(log.contains("SECRETKEY99"));
		assertFalse(log.contains("someone@example.com"));
	}

	@Test
	public void aCauseLoopDoesntHang() {
		RuntimeException a = new RuntimeException("a");
		RuntimeException b = new RuntimeException("b", a);
		a.initCause(b);
		String log = format(b);
		assertEquals(2, log.split("RuntimeException").length - 1);
	}

	@Test
	public void longStackTracesAreCut() {
		RuntimeException error = new RuntimeException("deep");
		StackTraceElement frame = new StackTraceElement("a.B", "c", "B.java", 1);
		StackTraceElement[] frames = new StackTraceElement[200];
		Arrays.fill(frames, frame);
		error.setStackTrace(frames);
		String log = format(error);
		assertTrue(log.contains("\t… 120 more"));
	}

	@Test
	public void newestCrashFirstAndTheLogStaysSmall() {
		String separator = "\n\n----------------------------------------\n\n";
		String first = CrashLog.combine("crash 1", null);
		String second = CrashLog.combine("crash 2", first);
		assertEquals("crash 2" + separator + "crash 1", second);

		StringBuilder big = new StringBuilder();
		for (int i = 0; i < CrashLog.MAX_CHARS / 2; i++) {
			big.append('x');
		}
		String log = CrashLog.combine("newest", CrashLog.combine(big.toString(), CrashLog.combine(big.toString(), null)));
		assertTrue(log.length() <= CrashLog.MAX_CHARS);
		assertTrue(log.startsWith("newest"));
		for (String entry : log.split(separator)) {
			assertTrue(entry.equals("newest") || entry.length() == CrashLog.MAX_CHARS / 2);
		}

		StringBuilder huge = new StringBuilder();
		for (int i = 0; i < CrashLog.MAX_CHARS * 2; i++) {
			huge.append('y');
		}
		assertEquals(CrashLog.MAX_CHARS, CrashLog.combine(huge.toString(), "old").length());
	}
}
