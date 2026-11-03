package app.niixfliix.download;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.FileNotFoundException;

import app.niixfliix.R;
import app.niixfliix.data.Prefs;
import app.niixfliix.util.Logs;

public final class DownloadFolder {

	private static final String TAG = "DownloadFolder";
	private static final int ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

	private DownloadFolder() {
	}

	@Nullable
	public static Uri picked(@NonNull Context context, @NonNull Prefs prefs) {
		String saved = prefs.downloadsFolder();
		if (saved == null) {
			return null;
		}
		Uri tree = Uri.parse(saved);
		for (UriPermission p : context.getContentResolver().getPersistedUriPermissions()) {
			if (p.getUri().equals(tree) && p.isWritePermission()) {
				return tree;
			}
		}
		return null;
	}

	public static boolean pick(@NonNull Context context, @NonNull Prefs prefs, @NonNull Uri tree) {
		ContentResolver resolver = context.getContentResolver();
		try {
			resolver.takePersistableUriPermission(tree, ACCESS);
		} catch (SecurityException e) {
			Logs.w(TAG, "No lasting access to the picked folder", e);
			return false;
		}
		release(context, prefs.downloadsFolder(), tree);
		prefs.setDownloadsFolder(tree.toString());
		return true;
	}

	public static void useDefault(@NonNull Context context, @NonNull Prefs prefs) {
		release(context, prefs.downloadsFolder(), null);
		prefs.setDownloadsFolder(null);
	}

	private static void release(Context context, @Nullable String old, @Nullable Uri keep) {
		if (old == null) {
			return;
		}
		Uri oldTree = Uri.parse(old);
		if (oldTree.equals(keep)) {
			return;
		}
		try {
			context.getContentResolver().releasePersistableUriPermission(oldTree, ACCESS);
		} catch (SecurityException e) {
		}
	}

	@NonNull
	public static String label(@NonNull Context context, @NonNull Prefs prefs) {
		Uri tree = picked(context, prefs);
		if (tree == null) {
			return Environment.DIRECTORY_DOWNLOADS + "/" + context.getString(R.string.app_name);
		}
		try {
			String id = DocumentsContract.getTreeDocumentId(tree);
			int colon = id.indexOf(':');
			String path = colon >= 0 ? id.substring(colon + 1) : id;
			return path.isEmpty() ? context.getString(R.string.settings_downloads_folder_root) : path;
		} catch (IllegalArgumentException e) {
			return tree.toString();
		}
	}

	@Nullable
	public static Uri createFile(@NonNull Context context, @NonNull Uri tree, @NonNull String mimeType,
			@NonNull String name) {
		try {
			Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
			return DocumentsContract.createDocument(context.getContentResolver(), parent, mimeType, name);
		} catch (FileNotFoundException | SecurityException | IllegalArgumentException
				| UnsupportedOperationException e) {
			Logs.w(TAG, "Could not create a file in the picked folder", e);
			return null;
		}
	}

	public static boolean isDocument(@NonNull Context context, @NonNull Uri uri) {
		return DocumentsContract.isDocumentUri(context, uri);
	}

	public static boolean delete(@NonNull Context context, @NonNull Uri document) {
		try {
			return DocumentsContract.deleteDocument(context.getContentResolver(), document);
		} catch (FileNotFoundException e) {
			return true;
		} catch (SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
			Logs.w(TAG, "Could not delete a downloaded file", e);
			return false;
		}
	}

	public static boolean needsStoragePermission(@NonNull Context context, @NonNull Prefs prefs) {
		return Build.VERSION.SDK_INT < 29 && picked(context, prefs) == null
				&& ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED;
	}
}
