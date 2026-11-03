package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

import app.niixfliix.util.CrashLog;
import app.niixfliix.util.Logs;

public final class NativeCrashes {

	private static final String TAG = "NativeCrashes";
	private static final int MAX_REPORT_BYTES = 4 << 20;
	private static final int MAX_FRAMES = 40;
	private static final int MAX_FILES = 5;
	private static final String HS_ERR = "hs_err_pid";
	private static final String EXIT = "exit-";
	private static final int MAX_EXIT_CHARS = 12_000;

	private NativeCrashes() {
	}

	public static int collect(@NonNull File dataDir) {
		File[] reports = new File(dataDir, "crash").listFiles((dir, name) -> name.endsWith(".log")
				&& (name.startsWith(HS_ERR) || name.startsWith(EXIT)));
		if (reports == null || reports.length == 0) {
			return 0;
		}
		Arrays.sort(reports, Comparator.comparingLong(File::lastModified));
		for (int i = 0; i < reports.length; i++) {
			File report = reports[i];
			if (i >= reports.length - MAX_FILES && report.isFile() && !Files.isSymbolicLink(report.toPath())) {
				try (InputStream in = Files.newInputStream(report.toPath())) {
					String text = new String(in.readNBytes(MAX_REPORT_BYTES), StandardCharsets.UTF_8);
					String summary = report.getName().startsWith(EXIT) ? lastPart(text) : summarize(text);
					if (!summary.isEmpty()) {
						CrashLog.recordNative(summary, report.lastModified());
					}
				} catch (IOException | RuntimeException e) {
					Logs.w(TAG, "Could not read " + report.getName(), e);
				}
			}
			if (!report.delete() && report.exists()) {
				Logs.w(TAG, "Could not delete " + report.getName(), null);
			}
		}
		return reports.length;
	}

	@VisibleForTesting
	@NonNull
	static String lastPart(@NonNull String report) {
		String text = report.trim();
		if (text.length() <= MAX_EXIT_CHARS) {
			return text;
		}
		int firstLineEnd = text.indexOf('\n');
		String cause = firstLineEnd > 0 ? text.substring(0, firstLineEnd) : "";
		return cause + "\n\n…\n" + text.substring(text.length() - Math.max(0, MAX_EXIT_CHARS - cause.length()));
	}

	@VisibleForTesting
	@NonNull
	static String summarize(@NonNull String report) {
		StringBuilder out = new StringBuilder();
		String[] lines = report.split("\r?\n");
		int i = 0;
		while (i < lines.length && (lines[i].startsWith("#") || lines[i].trim().isEmpty())) {
			String line = lines[i++];
			if (line.startsWith("# If you would like") || line.startsWith("#   https://")
					|| line.startsWith("# See problematic frame")) {
				continue;
			}
			if (!line.trim().isEmpty() && !line.trim().equals("#")) {
				out.append(line).append('\n');
			}
		}
		for (; i < lines.length; i++) {
			String line = lines[i];
			if (line.startsWith("Current thread")) {
				out.append('\n').append(line).append('\n');
			} else if (line.startsWith("Native frames:") || line.startsWith("Java frames:")) {
				out.append('\n').append(line).append('\n');
				int frames = 0;
				for (i++; i < lines.length && !lines[i].trim().isEmpty(); i++) {
					if (frames++ < MAX_FRAMES) {
						out.append(lines[i]).append('\n');
					}
				}
			} else if (line.startsWith("---------------  P R O C E S S")) {
				break;
			}
		}
		return out.toString().trim();
	}
}
