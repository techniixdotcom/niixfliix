package app.niixfliix.ui.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.niixfliix.R;
import app.niixfliix.live.SportSites;
import app.niixfliix.ui.BaseActivity;
import app.niixfliix.ui.Links;
import app.niixfliix.ui.SecureScreens;
import app.niixfliix.ui.WindowPadding;
import app.niixfliix.util.Logs;

public final class WebPlayerActivity extends BaseActivity {

	private static final String TAG = "WebPlayer";
	private static final String EXTRA_SITE = "site";
	private static final String PREFS = "web_player";
	private static final String KEY_DESKTOP = "desktop_";
	private static final Pattern WEBVIEW_TOKEN = Pattern.compile(";\\s*wv(?=\\))");
	private static final Pattern VERSION_TOKEN = Pattern.compile("\\s*Version/\\d+(?:\\.\\d+)*");
	private static final Pattern CHROME_MAJOR = Pattern.compile("Chrome/(\\d+)");
	private static final String FALLBACK_CHROME_MAJOR = "141";

	@Nullable private SportSites.Site site;
	@Nullable private WebView web;
	@Nullable private LinearProgressIndicator progress;
	@Nullable private View content;
	@Nullable private FrameLayout fullscreen;
	@Nullable private View customView;
	@Nullable private WebChromeClient.CustomViewCallback customCallback;
	@Nullable private MenuItem desktopItem;
	@NonNull private String mobileAgent = "";
	@NonNull private String desktopAgent = "";
	private boolean desktop;

	public static void start(@NonNull Context context, @NonNull SportSites.Site site) {
		Intent intent = new Intent(context, WebPlayerActivity.class).putExtra(EXTRA_SITE, site.id);
		if (!(context instanceof Activity)) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		site = SportSites.find(getIntent().getStringExtra(EXTRA_SITE));
		if (site == null) {
			finish();
			return;
		}
		try {
			setContentView(R.layout.activity_web_player);
			web = findViewById(R.id.web);
			setUpWebView(web);
		} catch (RuntimeException e) {
			Logs.w(TAG, "WebView unavailable", e);
			releaseWebView();
			Toast.makeText(this, R.string.web_unavailable, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		SecureScreens.protect(this);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		content = findViewById(R.id.content);
		fullscreen = findViewById(R.id.fullscreen);
		progress = findViewById(R.id.progress);
		WindowPadding.apply(content, WindowPadding.LEFT | WindowPadding.RIGHT | WindowPadding.BOTTOM);
		setUpToolbar(site);
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				goBack();
			}
		});
		web.loadUrl(site.url);
	}

	private void setUpToolbar(@NonNull SportSites.Site site) {
		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(site.name);
		toolbar.setNavigationIcon(R.drawable.ic_close);
		toolbar.setNavigationContentDescription(R.string.action_close);
		toolbar.setNavigationOnClickListener(v -> finish());
		toolbar.inflateMenu(R.menu.web_player);
		desktopItem = toolbar.getMenu().findItem(R.id.action_desktop_site);
		desktopItem.setChecked(desktop);
		toolbar.setOnMenuItemClickListener(item -> {
			if (web == null) {
				return false;
			}
			int id = item.getItemId();
			if (id == R.id.action_reload) {
				web.reload();
				return true;
			}
			if (id == R.id.action_desktop_site) {
				setDesktop(!desktop);
				return true;
			}
			return false;
		});
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
		settings.setLoadWithOverviewMode(true);
		settings.setUseWideViewPort(true);
		settings.setSupportZoom(true);
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);
		String original = settings.getUserAgentString();
		mobileAgent = mobileAgent(original);
		desktopAgent = desktopAgent(original);
		SportSites.Site current = site;
		desktop = current != null && prefs().getBoolean(KEY_DESKTOP + current.id, current.desktopByDefault);
		applyAgent(settings);
		CookieManager cookies = CookieManager.getInstance();
		cookies.setAcceptCookie(true);
		cookies.setAcceptThirdPartyCookies(view, true);
		view.setWebViewClient(new Client());
		view.setWebChromeClient(new Chrome());
	}

	private SharedPreferences prefs() {
		return getSharedPreferences(PREFS, MODE_PRIVATE);
	}

	private void applyAgent(@NonNull WebSettings settings) {
		settings.setUserAgentString(desktop ? desktopAgent : mobileAgent);
	}

	private void setDesktop(boolean on) {
		WebView view = web;
		SportSites.Site current = site;
		if (view == null || current == null) {
			return;
		}
		desktop = on;
		if (desktopItem != null) {
			desktopItem.setChecked(on);
		}
		prefs().edit().putBoolean(KEY_DESKTOP + current.id, on).apply();
		applyAgent(view.getSettings());
		view.reload();
	}

	@NonNull
	static String mobileAgent(@Nullable String original) {
		if (original == null || original.isEmpty()) {
			return "";
		}
		String agent = WEBVIEW_TOKEN.matcher(original).replaceFirst("");
		return VERSION_TOKEN.matcher(agent).replaceFirst("");
	}

	@NonNull
	static String desktopAgent(@Nullable String original) {
		String major = FALLBACK_CHROME_MAJOR;
		if (original != null) {
			Matcher m = CHROME_MAJOR.matcher(original);
			if (m.find()) {
				major = m.group(1);
			}
		}
		return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + major
				+ ".0.0.0 Safari/537.36";
	}

	private void goBack() {
		if (customView != null) {
			hideCustomView();
		} else if (web != null && web.canGoBack()) {
			web.goBack();
		} else {
			finish();
		}
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
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
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
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		WebChromeClient.CustomViewCallback callback = customCallback;
		customCallback = null;
		if (callback != null) {
			callback.onCustomViewHidden();
		}
	}

	private void openOutside(@NonNull Uri uri) {
		String scheme = uri.getScheme();
		if (scheme == null) {
			return;
		}
		String lower = scheme.toLowerCase(Locale.ROOT);
		if (lower.equals("https") || lower.equals("http")) {
			Links.open(this, uri.toString());
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
			try {
				CookieManager.getInstance().flush();
			} catch (RuntimeException e) {
				Logs.w(TAG, "Cookie flush failed", e);
			}
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

	private final class Client extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
			if (!request.isForMainFrame()) {
				return false;
			}
			Uri uri = request.getUrl();
			if (SportSites.isTrusted(uri.toString())) {
				return false;
			}
			openOutside(uri);
			return true;
		}

		@Override
		public void onPageStarted(@NonNull WebView view, @NonNull String url, @Nullable Bitmap icon) {
			String lower = url.toLowerCase(Locale.ROOT);
			if (!SportSites.isTrusted(url) && !lower.startsWith("about:")) {
				view.stopLoading();
				openOutside(Uri.parse(url));
			}
		}

		@Override
		public boolean onRenderProcessGone(@NonNull WebView view, @NonNull RenderProcessGoneDetail detail) {
			if (view == web) {
				web = null;
				hideCustomView();
				ViewGroup parent = (ViewGroup) view.getParent();
				if (parent != null) {
					parent.removeView(view);
				}
				view.destroy();
				if (!isFinishing()) {
					Toast.makeText(WebPlayerActivity.this, R.string.web_crashed, Toast.LENGTH_LONG).show();
					finish();
				}
			}
			return true;
		}
	}

	private final class Chrome extends WebChromeClient {

		@Override
		public void onPermissionRequest(@NonNull PermissionRequest request) {
			if (SportSites.isTrusted(request.getOrigin().toString())) {
				for (String resource : request.getResources()) {
					if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) {
						request.grant(new String[] {PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
						return;
					}
				}
			}
			request.deny();
		}

		@Override
		public void onProgressChanged(@NonNull WebView view, int newProgress) {
			if (progress == null) {
				return;
			}
			if (newProgress >= 100) {
				progress.setVisibility(View.GONE);
			} else {
				progress.setVisibility(View.VISIBLE);
				progress.setProgressCompat(newProgress, true);
			}
		}

		@Override
		public void onShowCustomView(@NonNull View view, @NonNull CustomViewCallback callback) {
			showCustomView(view, callback);
		}

		@Override
		public void onHideCustomView() {
			hideCustomView();
		}
	}
}
