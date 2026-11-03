package app.niixfliix.desktop.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NativeCrashesTest {

	private static final String REPORT = String.join("\n",
			"#",
			"# A fatal error has been detected by the Java Runtime Environment:",
			"#",
			"#  SIGSEGV (0xb) at pc=0x00007f3c2a1b2c3d, pid=4242, tid=4300",
			"#",
			"# JRE version: OpenJDK Runtime Environment (25.0.1+8) (build 25.0.1+8)",
			"# Problematic frame:",
			"# C  [libtorrent4j.so+0x1b2c3d]  libtorrent::torrent_handle::status(unsigned int) const+0x1d",
			"#",
			"# If you would like to submit a bug report, please visit:",
			"#   https://bugreport.java.com/bugreport/crash.jsp",
			"#",
			"",
			"---------------  S U M M A R Y ------------",
			"",
			"Command Line: --enable-native-access=ALL-UNNAMED app.niixfliix.desktop.Main",
			"",
			"---------------  T H R E A D  ---------------",
			"",
			"Current thread (0x00007f3c24001234):  JavaThread \"gtk\" [_thread_in_native, id=4300]",
			"",
			"Stack: [0x00007f3c1f000000,0x00007f3c1f100000],  sp=0x00007f3c1f0fe000",
			"Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)",
			"C  [libtorrent4j.so+0x1b2c3d]  libtorrent::torrent_handle::status(unsigned int) const+0x1d",
			"j  org.libtorrent4j.swig.libtorrent_jni.torrent_handle_status__SWIG_1(JLorg/libtorrent4j/swig/torrent_handle;)J+0",
			"",
			"Java frames: (J=compiled Java code, j=interpreted, Vv=VM code)",
			"j  org.libtorrent4j.TorrentHandle.status()Lorg/libtorrent4j/TorrentStatus;+4",
			"j  app.niixfliix.torrent.TorrentEngine$Playback.tick()V+50",
			"",
			"siginfo: si_signo: 11 (SIGSEGV), si_code: 1 (SEGV_MAPERR), si_addr: 0x0000000000000010",
			"",
			"Registers:",
			"RAX=0x0000000000000000, RBX=0x00007f3c24001234",
			"",
			"---------------  P R O C E S S  ---------------",
			"",
			"Environment Variables:",
			"HOME=/home/someone",
			"DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus");

	@Test
	public void keepsWhatFailedAndWhere() {
		String summary = NativeCrashes.summarize(REPORT);
		assertTrue(summary.contains("SIGSEGV (0xb)"));
		assertTrue(summary.contains("# C  [libtorrent4j.so+0x1b2c3d]"));
		assertTrue(summary.contains("JRE version: OpenJDK Runtime Environment (25.0.1+8)"));
		assertTrue(summary.contains("Current thread"));
		assertTrue(summary.contains("Native frames:"));
		assertTrue(summary.contains("TorrentEngine$Playback.tick()"));
	}

	@Test
	public void leavesOutRegistersEnvironmentAndBugReportAdvice() {
		String summary = NativeCrashes.summarize(REPORT);
		assertFalse(summary.contains("Registers"));
		assertFalse(summary.contains("RAX="));
		assertFalse(summary.contains("HOME="));
		assertFalse(summary.contains("DBUS_SESSION_BUS_ADDRESS"));
		assertFalse(summary.contains("Command Line"));
		assertFalse(summary.contains("bugreport.java.com"));
		assertFalse(summary.contains("If you would like"));
	}

	@Test
	public void emptyReportGivesNothing() {
		assertTrue(NativeCrashes.summarize("").isEmpty());
	}

	@Test
	public void launcherReportKeepsItsCauseAndTheEndOfTheOutput() {
		StringBuilder report = new StringBuilder("Ended by signal 6 (SIGABRT)\n\nLast output:\n");
		for (int i = 0; i < 5000; i++) {
			report.append("line ").append(i).append('\n');
		}
		String part = NativeCrashes.lastPart(report.toString());
		assertTrue(part.startsWith("Ended by signal 6 (SIGABRT)"));
		assertTrue(part.endsWith("line 4999"));
		assertFalse(part.contains("line 10\n"));
		assertTrue(part.length() < 12_100);
	}

	@Test
	public void shortLauncherReportIsKeptWhole() {
		String report = "Ended by signal 9 (SIGKILL)\n\nLast output:\nGStreamer-CRITICAL: x\n";
		assertEquals(report.trim(), NativeCrashes.lastPart(report));
	}
}
