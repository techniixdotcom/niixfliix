package app.niixfliix.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class CrashLog {

	private static final String DIR = "crash";
	private static final String FILE = "crash.log";
	private static final String SEPARATOR = "\n\n----------------------------------------\n\n";
	@VisibleForTesting
	static final int MAX_CHARS = 48 * 1024;
	private static final int MAX_CAUSES = 8;
	private static final int MAX_FRAMES = 80;

	private static volatile File file;
	private static volatile String version = "";
	private static volatile String system = "";
	private static volatile String device = "";

	private CrashLog() {
	}

	public static void install(@NonNull File privateDir, @NonNull String appVersion, @NonNull String system,
			@NonNull String device) {
		file = new File(new File(privateDir, DIR), FILE);
		CrashLog.version = appVersion;
		CrashLog.system = system;
		CrashLog.device = device;
		Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
			try {
				record(thread, error);
			} catch (Throwable ignored) {
			}
			if (previous != null) {
				previous.uncaughtException(thread, error);
			} else {
				ThreadGroup group = thread.getThreadGroup();
				if (group != null) {
					group.uncaughtException(thread, error);
				} else {
					error.printStackTrace();
				}
			}
		});
	}

	@WorkerThread
	@Nullable
	public static String read() {
		File f = file;
		if (f == null || !f.isFile()) {
			return null;
		}
		try (InputStream in = Files.newInputStream(f.toPath())) {
			String text = SizeCappedReader.readUtf8(in, MAX_CHARS * 4L);
			return text.trim().isEmpty() ? null : text;
		} catch (IOException e) {
			return null;
		}
	}

	@WorkerThread
	public static long lastCrashMillis() {
		File f = file;
		return f != null && f.isFile() ? f.lastModified() : 0;
	}

	@WorkerThread
	public static void clear() {
		File f = file;
		if (f != null && f.exists() && !f.delete()) {
			Logs.w("CrashLog", "Could not delete the crash log", null);
		}
	}

	@WorkerThread
	public static void recordNative(@NonNull String summary, long timeMillis) throws IOException {
		write(formatNative(summary, version, system, device, timeMillis));
	}

	private static void record(@NonNull Thread thread, @NonNull Throwable error) throws IOException {
		write(format(error, thread.getName(), version, system, device, System.currentTimeMillis()));
	}

	private static synchronized void write(@NonNull String entry) throws IOException {
		File f = file;
		if (f == null) {
			return;
		}
		File dir = f.getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
			return;
		}
		String old = null;
		if (f.isFile()) {
			try (InputStream in = Files.newInputStream(f.toPath())) {
				old = SizeCappedReader.readUtf8(in, MAX_CHARS * 4L);
			} catch (IOException ignored) {
			}
		}
		String text = combine(entry, old);
		File tmp = new File(dir, FILE + ".tmp");
		try (OutputStream out = new FileOutputStream(tmp)) {
			out.write(text.getBytes(StandardCharsets.UTF_8));
			out.flush();
		}
		if (!tmp.renameTo(f)) {
			tmp.delete();
		}
	}

	@VisibleForTesting
	@NonNull
	static String combine(@NonNull String entry, @Nullable String old) {
		String newest = entry.length() > MAX_CHARS ? entry.substring(0, MAX_CHARS) : entry;
		if (old == null || old.trim().isEmpty()) {
			return newest;
		}
		StringBuilder out = new StringBuilder(newest);
		for (String previous : old.split(SEPARATOR)) {
			if (previous.trim().isEmpty()) {
				continue;
			}
			if (out.length() + SEPARATOR.length() + previous.length() > MAX_CHARS) {
				break;
			}
			out.append(SEPARATOR).append(previous);
		}
		return out.toString();
	}

	@VisibleForTesting
	@NonNull
	static String formatNative(@NonNull String summary, @NonNull String version, @NonNull String system,
			@NonNull String device, long timeMillis) {
		return header(version, system, device, timeMillis) + "Thread: native\n\n" + Redactor.redact(summary).trim();
	}

	private static String header(String version, String system, String device, long timeMillis) {
		SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
		iso.setTimeZone(TimeZone.getTimeZone("UTC"));
		return "Time: " + iso.format(new Date(timeMillis)) + '\n'
				+ "App: " + version + '\n'
				+ "System: " + system + '\n'
				+ "Device: " + Redactor.redact(device) + '\n';
	}

	@VisibleForTesting
	@NonNull
	static String format(@NonNull Throwable error, @NonNull String threadName, @NonNull String version,
			@NonNull String system, @NonNull String device, long timeMillis) {
		StringBuilder out = new StringBuilder(header(version, system, device, timeMillis));
		out.append("Thread: ").append(Redactor.redact(threadName)).append("\n\n");
		Map<Throwable, Boolean> seen = new IdentityHashMap<>();
		Throwable t = error;
		for (int depth = 0; t != null && depth < MAX_CAUSES && seen.put(t, Boolean.TRUE) == null;
				depth++, t = t.getCause()) {
			if (depth > 0) {
				out.append("Caused by: ");
			}
			out.append(t.getClass().getName());
			String message = t.getMessage();
			if (message != null && !message.isEmpty()) {
				out.append(": ").append(Redactor.redact(message).replace('\n', ' '));
			}
			out.append('\n');
			StackTraceElement[] frames = t.getStackTrace();
			int shown = Math.min(frames.length, MAX_FRAMES);
			for (int i = 0; i < shown; i++) {
				out.append("\tat ").append(frames[i]).append('\n');
			}
			if (frames.length > shown) {
				out.append("\t… ").append(frames.length - shown).append(" more\n");
			}
		}
		return out.toString().trim();
	}
}
