package app.niixfliix.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;

import app.niixfliix.R;
import app.niixfliix.addon.StremioUrl;
import app.niixfliix.app.Constant;
import app.niixfliix.ui.BaseActivity;
import app.niixfliix.ui.Links;
import app.niixfliix.ui.WindowPadding;
import app.niixfliix.ui.common.TextPrompt;

/** Add-on configure page in a locked down WebView. Grabs the stremio:// install link and returns it. */
public final class ConfigurePageActivity extends BaseActivity {

	public static final String EXTRA_RESULT_URL = "manifest_url";
	private static final String EXTRA_URL = "url";

	private WebView web;
	@Nullable private String host;

	@NonNull
	public static Intent intent(@NonNull Context context, @NonNull String url) {
		return new Intent(context, ConfigurePageActivity.class).putExtra(EXTRA_URL, url);
	}

	@Override
	@SuppressLint("SetJavaScriptEnabled")
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String url = getIntent().getStringExtra(EXTRA_URL);
		Uri start = url != null && url.length() <= Constant.MAX_INTENT_TEXT ? Uri.parse(url) : null;
		if (start == null || !"https".equals(start.getScheme()) || start.getHost() == null) {
			finish();
			return;
		}
		host = start.getHost().toLowerCase(Locale.ROOT);
		setContentView(R.layout.activity_configure);
		WindowPadding.apply(findViewById(R.id.root), WindowPadding.LEFT | WindowPadding.RIGHT | WindowPadding.BOTTOM);
		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(host);
		toolbar.setNavigationOnClickListener(v -> finish());
		toolbar.inflateMenu(R.menu.configure);
		toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.action_paste) {
				TextPrompt.show(this, R.string.configure_paste_link, R.string.configure_paste_message,
						R.string.action_ok, this::finishWith);
				return true;
			}
			return false;
		});
		LinearProgressIndicator progress = findViewById(R.id.progress);

		web = findViewById(R.id.web);
		WebSettings settings = web.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setAllowFileAccess(false);
		settings.setAllowContentAccess(false);
		settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
		settings.setSafeBrowsingEnabled(true);
		settings.setGeolocationEnabled(false);
		settings.setSupportMultipleWindows(false);
		settings.setJavaScriptCanOpenWindowsAutomatically(false);
		CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
		web.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int newProgress) {
				progress.setProgress(newProgress);
				progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
			}
		});
		web.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
				return handle(request.getUrl());
			}
		});
		web.loadUrl(start.toString());
	}

	@Override
	protected void onDestroy() {
		if (web != null) {
			// might have had a debrid key typed in
			web.clearCache(true);
			web.clearHistory();
			WebStorage.getInstance().deleteAllData();
			CookieManager.getInstance().removeAllCookies(null);
			web.destroy();
		}
		super.onDestroy();
	}

	private boolean handle(@NonNull Uri uri) {
		String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
		if (scheme.equals("stremio")) {
			finishWith(uri.toString());
			return true;
		}
		if (scheme.equals("https") && host != null && host.equalsIgnoreCase(uri.getHost())) {
			if (uri.getPath() != null && uri.getPath().endsWith("/manifest.json")) {
				finishWith(uri.toString());
				return true;
			}
			return false;
		}
		if (Links.isWebLink(uri.toString())) {
			Links.open(this, uri.toString());
		}
		return true;
	}

	private void finishWith(String link) {
		String manifest = StremioUrl.toManifestUrl(link);
		if (manifest == null) {
			Toast.makeText(this, R.string.addon_error_invalid_url, Toast.LENGTH_LONG).show();
			return;
		}
		setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT_URL, manifest));
		finish();
	}
}
