package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

import app.niixfliix.util.Logs;

final class DiskCache {

	private static final String TAG = "DiskCache";
	private static final String SUFFIX = ".json";

	static final class Entry {
		@NonNull final String body;
		final long ageMs;

		Entry(@NonNull String body, long ageMs) {
			this.body = body;
			this.ageMs = ageMs;
		}
	}

	private final File dir;
	private final long maxBytes;

	DiskCache(@NonNull File dir, long maxBytes) {
		this.dir = dir;
		this.maxBytes = maxBytes;
	}

	@WorkerThread
	@Nullable
	synchronized Entry read(@NonNull String key, long maxBodyBytes) {
		File file = file(key);
		try {
			if (!file.isFile() || file.length() > maxBodyBytes) {
				return null;
			}
			String body = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			long age = Math.max(0, System.currentTimeMillis() - file.lastModified());
			return new Entry(body, age);
		} catch (IOException | SecurityException e) {
			return null;
		}
	}

	@WorkerThread
	synchronized void write(@NonNull String key, @NonNull String body) {
		File file = file(key);
		File temp = new File(dir, file.getName() + ".tmp");
		try {
			Files.createDirectories(dir.toPath());
			Files.write(temp.toPath(), body.getBytes(StandardCharsets.UTF_8));
			try {
				Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			trim();
		} catch (IOException | SecurityException e) {
			Logs.w(TAG, "Could not keep an add-on answer", e);
			temp.delete();
		}
	}

	@WorkerThread
	synchronized void remove(@NonNull String key) {
		file(key).delete();
	}

	private void trim() {
		File[] files = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
		if (files == null) {
			return;
		}
		long total = 0;
		for (File f : files) {
			total += f.length();
		}
		if (total <= maxBytes) {
			return;
		}
		Arrays.sort(files, Comparator.comparingLong(File::lastModified));
		for (File f : files) {
			if (total <= maxBytes) {
				break;
			}
			long size = f.length();
			if (f.delete()) {
				total -= size;
			}
		}
	}

	private File file(String key) {
		return new File(dir, sha256(key) + SUFFIX);
	}

	private static String sha256(String text) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is part of every Java", e);
		}
	}
}
