package app.niixfliix.ui.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;
import java.util.regex.Pattern;

import app.niixfliix.R;
import app.niixfliix.ui.BaseActivity;
import app.niixfliix.ui.Links;
import app.niixfliix.ui.WindowPadding;
import app.niixfliix.util.Logs;

public final class TrailerActivity extends BaseActivity {

	private static final String TAG = "Trailer";
	private static final String EXTRA_VIDEO = "video";
	private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

	@Nullable private WebView web;
	@Nullable private FrameLayout fullscreen;
	@Nullable private View content;
	@Nullable private View customView;
	@Nullable private WebChromeClient.CustomViewCallback customCallback;

	public static void start(@NonNull Context context, @NonNull String youtubeId) {
		if (!YOUTUBE_ID.matcher(youtubeId).matches()) {
			Links.open(context, Links.youtube(youtubeId));
			return;
		}
		Intent intent = new Intent(context, TrailerActivity.class).putExtra(EXTRA_VIDEO, youtubeId);
		if (!(context instanceof Activity)) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String video = getIntent().getStringExtra(EXTRA_VIDEO);
		if (video == null || !YOUTUBE_ID.matcher(video).matches()) {
			finish();
			return;
		}
		try {
			setContentView(R.layout.activity_trailer);
			web = findViewById(R.id.web);
			setUpWebView(web);
		} catch (RuntimeException e) {
			Logs.w(TAG, "WebView unavailable", e);
			releaseWebView();
			Links.open(this, Links.youtube(video));
			finish();
			return;
		}
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		content = findViewById(R.id.content);
		fullscreen = findViewById(R.id.fullscreen);
		WindowPadding.apply(content, WindowPadding.LEFT | WindowPadding.TOP | WindowPadding.RIGHT
				| WindowPadding.BOTTOM);
		View close = findViewById(R.id.close);
		close.setOnClickListener(v -> finish());
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (customView != null) {
					hideCustomView();
				} else {
					finish();
				}
			}
		});
		web.loadDataWithBaseURL(origin(), page(video), "text/html", "UTF-8", null);
	}

	@NonNull
	private String origin() {
		return "https://" + getPackageName().toLowerCase(Locale.ROOT) + "/";
	}

	@NonNull
	static String page(@NonNull String video) {
		return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
				+ "<meta name=\"referrer\" content=\"strict-origin-when-cross-origin\">"
				+ "<style>html,body{margin:0;height:100%;background:#000}"
				+ "iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style></head><body>"
				+ "<iframe src=\"https://www.youtube-nocookie.com/embed/" + video
				+ "?autoplay=1&playsinline=1&rel=0&modestbranding=1\" "
				+ "allow=\"autoplay; encrypted-media; fullscreen; picture-in-picture\" "
				+ "referrerpolicy=\"strict-origin-when-cross-origin\" allowfullscreen></iframe></body></html>";
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void setUpWebView(@NonNull WebView view) {
		WebSettings settings = view.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setMediaPlaybackRequiresUserGesture(false);
		settings.setAllowFileAccess(false);
		settings.setAllowContentAccess(false);
		settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
		settings.setSupportMultipleWindows(false);
		settings.setJavaScriptCanOpenWindowsAutomatically(false);
		settings.setGeolocationEnabled(false);
		settings.setSafeBrowsingEnabled(true);
		view.setBackgroundColor(0xFF000000);
		view.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(@NonNull WebView v, @NonNull WebResourceRequest request) {
				if (!request.isForMainFrame()) {
					return false;
				}
				Uri uri = request.getUrl();
				String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
				if (scheme.equals("https") || scheme.equals("http")) {
					Links.open(TrailerActivity.this, uri.toString());
				}
				return true;
			}
		});
		view.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onShowCustomView(@NonNull View v, @NonNull CustomViewCallback callback) {
				showCustomView(v, callback);
			}

			@Override
			public void onHideCustomView() {
				hideCustomView();
			}
		});
	}

	private void showCustomView(@NonNull View view, @NonNull WebChromeClient.CustomViewCallback callback) {
		if (customView != null || fullscreen == null || content == null) {
			callback.onCustomViewHidden();
			return;
		}
		customView = view;
		customCallback = callback;
		fullscreen.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		fullscreen.setVisibility(View.VISIBLE);
		content.setVisibility(View.INVISIBLE);
		WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
		bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		bars.hide(WindowInsetsCompat.Type.systemBars());
	}

	private void hideCustomView() {
		View view = customView;
		if (view == null) {
			return;
		}
		customView = null;
		if (fullscreen != null) {
			fullscreen.removeView(view);
			fullscreen.setVisibility(View.GONE);
		}
		if (content != null) {
			content.setVisibility(View.VISIBLE);
		}
		WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
				.show(WindowInsetsCompat.Type.systemBars());
		WebChromeClient.CustomViewCallback callback = customCallback;
		customCallback = null;
		if (callback != null) {
			callback.onCustomViewHidden();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (web != null) {
			web.onResume();
		}
	}

	@Override
	protected void onPause() {
		if (web != null) {
			web.onPause();
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		releaseWebView();
		super.onDestroy();
	}

	private void releaseWebView() {
		WebView view = web;
		web = null;
		if (view == null) {
			return;
		}
		hideCustomView();
		ViewGroup parent = (ViewGroup) view.getParent();
		if (parent != null) {
			parent.removeView(view);
		}
		view.stopLoading();
		view.setWebChromeClient(null);
		view.setWebViewClient(new WebViewClient());
		view.destroy();
	}
}
