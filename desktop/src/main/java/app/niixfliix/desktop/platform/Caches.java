package app.niixfliix.desktop.platform;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import app.niixfliix.util.Logs;

public final class Caches {

	private static final String TAG = "Caches";

	private Caches() {
	}

	@NonNull
	public static File images() {
		return new File(Paths.cache(), "images");
	}

	@NonNull
	public static File torrents() {
		return new File(Paths.cache(), "torrents");
	}

	public static void clear() {
		delete(torrents());
	}

	static void delete(@NonNull File dir) {
		Path root = dir.toPath();
		if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					remove(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e) {
					remove(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path d, IOException e) {
					remove(d);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			Logs.w(TAG, "Could not empty " + dir.getName(), e);
		}
	}

	private static void remove(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			Logs.w(TAG, "Could not delete " + path.getFileName(), e);
		}
	}
}
