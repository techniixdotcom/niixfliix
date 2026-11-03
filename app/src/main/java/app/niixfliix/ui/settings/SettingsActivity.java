package app.niixfliix.ui.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;

import app.niixfliix.R;
import app.niixfliix.ui.BaseActivity;
import app.niixfliix.ui.WindowPadding;

public final class SettingsActivity extends BaseActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		WindowPadding.apply(findViewById(R.id.root), WindowPadding.LEFT | WindowPadding.RIGHT | WindowPadding.BOTTOM);
		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(R.string.nav_settings);
		toolbar.setNavigationOnClickListener(v -> finish());
		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.settings_container, new SettingsFragment())
					.commit();
		}
	}
}
