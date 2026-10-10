package app.niixfliix.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import app.niixfliix.R;

/** http/https only, add-on text is untrusted (no intent:, file:, javascript: etc) */
public final class Links {

	private Links() {
	}

	public static boolean isWebLink(@Nullable String url) {
		if (url == null || url.length() > 4096) {
			return false;
		}
		Uri uri = Uri.parse(url.trim());
		String scheme = uri.getScheme();
		if (scheme == null) {
			return false;
		}
		String lower = scheme.toLowerCase(Locale.ROOT);
		return (lower.equals("https") || lower.equals("http")) && uri.getHost() != null && !uri.getHost().isEmpty();
	}

	public static void open(@NonNull Context context, @Nullable String url) {
		if (!isWebLink(url)) {
			Toast.makeText(context, R.string.error_link_not_allowed, Toast.LENGTH_SHORT).show();
			return;
		}
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
				.addCategory(Intent.CATEGORY_BROWSABLE)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			context.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(context, R.string.error_no_app_for_link, Toast.LENGTH_SHORT).show();
		}
	}

	@NonNull
	public static String youtube(@NonNull String videoId) {
		return "https://www.youtube.com/watch?v=" + Uri.encode(videoId);
	}
}
