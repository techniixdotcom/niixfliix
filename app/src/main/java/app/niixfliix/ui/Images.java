package app.niixfliix.ui;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;

import java.util.Map;
import java.util.WeakHashMap;

import app.niixfliix.R;
import app.niixfliix.net.ImageUrls;

public final class Images {

	public static final float WATCHED_ALPHA = 0.5f;
	private static final ColorMatrixColorFilter GREYSCALE = greyscale();
	private static final DrawableTransitionOptions FADE = DrawableTransitionOptions.with(
			new DrawableCrossFadeFactory.Builder(150).setCrossFadeEnabled(true).build());
	private static final Map<RequestManager, int[]> FLINGING = new WeakHashMap<>();

	private Images() {
	}

	public static void watched(@NonNull ImageView image, boolean watched) {
		image.setColorFilter(watched ? GREYSCALE : null);
		image.setAlpha(watched ? WATCHED_ALPHA : 1f);
	}

	private static ColorMatrixColorFilter greyscale() {
		ColorMatrix matrix = new ColorMatrix();
		matrix.setSaturation(0);
		return new ColorMatrixColorFilter(matrix);
	}

	public static void poster(@NonNull ImageView view, @Nullable String url) {
		RequestManager glide = loaderOf(view);
		if (glide == null) {
			return;
		}
		view.setScaleType(ImageView.ScaleType.CENTER_CROP);
		if (!ImageUrls.isHttps(url)) {
			glide.clear(view);
			view.setImageDrawable(null);
			return;
		}
		glide.load(ImageUrls.poster(url, posterWidthPx(view)))
				.placeholder(R.drawable.bg_poster_placeholder)
				.error(android.R.color.transparent)
				.centerCrop()
				.format(DecodeFormat.PREFER_RGB_565)
				.transition(FADE)
				.into(view);
	}

	public static void logo(@NonNull ImageView view, @Nullable String url) {
		RequestManager glide = loaderOf(view);
		if (glide == null) {
			return;
		}
		view.setScaleType(ImageView.ScaleType.FIT_CENTER);
		view.setBackgroundResource(R.drawable.bg_poster_placeholder);
		if (!ImageUrls.isHttps(url)) {
			glide.clear(view);
			view.setImageDrawable(null);
			return;
		}
		glide.load(url)
				.centerInside()
				.into(view);
	}

	public static void icon(@NonNull ImageView view, @Nullable String url) {
		RequestManager glide = loaderOf(view);
		if (glide == null) {
			return;
		}
		if (!ImageUrls.isHttps(url)) {
			glide.clear(view);
			view.setImageDrawable(null);
			return;
		}
		glide.load(url).centerInside().into(view);
	}

	public static void backdrop(@NonNull ImageView view, @Nullable String url) {
		RequestManager glide = loaderOf(view);
		if (glide == null) {
			return;
		}
		if (!ImageUrls.isHttps(url)) {
			glide.clear(view);
			view.setImageDrawable(null);
			return;
		}
		int width = view.getWidth() > 0 ? view.getWidth() : view.getResources().getDisplayMetrics().widthPixels;
		glide.load(ImageUrls.backdrop(url, width)).centerCrop().into(view);
	}

	public static void cancel(@NonNull ImageView view) {
		RequestManager glide = loaderOf(view);
		if (glide != null) {
			glide.clear(view);
		}
	}

	public static void logo(@NonNull ImageView view, @Nullable String url, @NonNull Runnable onMissing) {
		RequestManager glide = loaderOf(view);
		if (glide == null) {
			return;
		}
		if (!ImageUrls.isHttps(url)) {
			glide.clear(view);
			onMissing.run();
			return;
		}
		int height = view.getLayoutParams() != null ? view.getLayoutParams().height : 0;
		RequestBuilder<Drawable> request = glide.load(url).listener(new RequestListener<Drawable>() {
			@Override
			public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model,
					@NonNull Target<Drawable> target, boolean isFirstResource) {
				onMissing.run();
				return false;
			}

			@Override
			public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
					@NonNull Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
				return false;
			}
		});
		if (height > 0) {
			request = request.override(Target.SIZE_ORIGINAL, height).downsample(DownsampleStrategy.CENTER_INSIDE);
		}
		request.into(view);
	}

	@MainThread
	public static void pauseWhileFlinging(@NonNull RecyclerView list) {
		FlingPause pause = new FlingPause();
		list.addOnScrollListener(pause);
		list.addOnAttachStateChangeListener(pause);
	}

	private static final class FlingPause extends RecyclerView.OnScrollListener
			implements View.OnAttachStateChangeListener {
		@Nullable private RequestManager glide;
		private boolean counted;

		@Override
		public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
			boolean now = newState == RecyclerView.SCROLL_STATE_SETTLING;
			if (now && glide == null) {
				glide = loaderOf(rv);
			}
			set(now);
		}

		@Override
		public void onViewAttachedToWindow(@NonNull View v) {
		}

		@Override
		public void onViewDetachedFromWindow(@NonNull View v) {
			set(false);
		}

		@MainThread
		private void set(boolean now) {
			if (glide == null || counted == now) {
				return;
			}
			counted = now;
			int[] count = FLINGING.get(glide);
			if (count == null) {
				count = new int[1];
				FLINGING.put(glide, count);
			}
			count[0] = Math.max(0, count[0] + (now ? 1 : -1));
			if (now && count[0] == 1) {
				glide.pauseRequests();
			} else if (!now && count[0] == 0) {
				glide.resumeRequests();
			}
		}
	}

	@Nullable
	private static RequestManager loaderOf(@NonNull View view) {
		try {
			return Glide.with(view.getContext());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static int posterWidthPx(@NonNull ImageView view) {
		if (view.getWidth() > 0) {
			return view.getWidth();
		}
		return Math.round(view.getResources().getDimension(R.dimen.poster_width) * 1.5f);
	}
}
