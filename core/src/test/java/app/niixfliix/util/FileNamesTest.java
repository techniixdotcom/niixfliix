package app.niixfliix.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import app.niixfliix.app.Constant;

public class FileNamesTest {

	private static int bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8).length;
	}

	@Test
	public void replacesCharactersFileSystemsReject() {
		assertEquals("a_b_c_d_e_f_g_h_i.mkv", FileNames.sanitize("a/b\\c:d*e?f\"g<h>i.mkv", 180));
		assertEquals("tab and newline.mp4", FileNames.sanitize("tab\tand\n\nnewline.mp4", 180));
	}

	@Test
	public void trimsDotsAndSpacesAndFallsBack() {
		assertEquals("name", FileNames.sanitize("  ..name.. ", 180));
		assertEquals("video", FileNames.sanitize("...", 180));
		assertEquals("video", FileNames.sanitize(null, 180));
	}

	@Test
	public void cutsToUtf8BytesAndKeepsExtension() {
		StringBuilder long1 = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			long1.append("日本");
		}
		String cut = FileNames.sanitize(long1 + ".mkv", Constant.FILE_NAME_MAX_BYTES);
		assertTrue(bytes(cut) <= Constant.FILE_NAME_MAX_BYTES);
		assertTrue(bytes(cut) < 180);
		assertTrue(cut.endsWith(".mkv"));
		assertTrue(cut.startsWith("日本"));
	}

	@Test
	public void neverSplitsEmojiSurrogates() {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < 80; i++) {
			s.append("🎬");
		}
		String cut = FileNames.sanitize(s.toString(), 50);
		assertTrue(bytes(cut) <= 50);
		assertEquals(0, cut.length() % 2);
		assertEquals(cut.codePointCount(0, cut.length()) * 2, cut.length());
	}

	@Test
	public void longDottedReleaseNameIsNotMistakenForAnExtension() {
		String name = "Some.Very.Long.Release.Name.2026.1080p.WEB-DL.DDP5.1.Atmos.H.264-GROUPNAME";
		String cut = FileNames.sanitize(name, 30);
		assertTrue(bytes(cut) <= 30);
		assertTrue(cut.startsWith("Some.Very.Long"));
	}
}
