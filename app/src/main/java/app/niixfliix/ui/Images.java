package app.niixfliix.ui;

import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import app.niixfliix.R;

/** https only */
public final class Images {

	private Images() {
	}

	public static void poster(@NonNull ImageView view, @Nullable String url) {
		Picasso.get().cancelRequest(view);
		if (!isHttps(url)) {
			view.setImageResource(R.drawable.bg_poster_placeholder);
			return;
		}
		Picasso.get().load(url)
				.placeholder(R.drawable.bg_poster_placeholder)
				.error(R.drawable.bg_poster_placeholder)
				.fit()
				.centerCrop()
				.into(view);
	}

	public static void icon(@NonNull ImageView view, @Nullable String url) {
		Picasso.get().cancelRequest(view);
		if (!isHttps(url)) {
			view.setImageDrawable(null);
			return;
		}
		Picasso.get().load(url).fit().centerInside().into(view);
	}

	public static void backdrop(@NonNull ImageView view, @Nullable String url) {
		Picasso.get().cancelRequest(view);
		if (!isHttps(url)) {
			view.setImageDrawable(null);
			return;
		}
		Picasso.get().load(url).fit().centerCrop().into(view);
	}

	/** Hides the view (title shows instead) if there's no logo. resize() not fit(), width is wrap_content. */
	public static void logo(@NonNull ImageView view, @Nullable String url, @NonNull Runnable onMissing) {
		Picasso.get().cancelRequest(view);
		if (!isHttps(url)) {
			onMissing.run();
			return;
		}
		int height = view.getLayoutParams() != null ? view.getLayoutParams().height : 0;
		RequestCreator request = Picasso.get().load(url);
		if (height > 0) {
			request.resize(0, height).onlyScaleDown();
		}
		request.into(view, new Callback() {
			@Override
			public void onSuccess() {
			}

			@Override
			public void onError(Exception e) {
				onMissing.run();
			}
		});
	}

	private static boolean isHttps(@Nullable String url) {
		return url != null && url.regionMatches(true, 0, "https://", 0, 8);
	}
}
