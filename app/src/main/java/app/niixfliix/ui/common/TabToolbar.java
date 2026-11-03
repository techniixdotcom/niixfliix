package app.niixfliix.ui.common;

import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;

import app.niixfliix.R;
import app.niixfliix.ui.settings.SettingsActivity;

public final class TabToolbar {

	private TabToolbar() {
	}

	public static void setUp(@NonNull Fragment tab, @NonNull View root, @StringRes int title) {
		MaterialToolbar toolbar = root.findViewById(R.id.tab_toolbar);
		toolbar.setTitle(title);
		toolbar.setTitleTextAppearance(tab.requireContext(), title == R.string.app_name
				? R.style.TextAppearance_Niixfliix_Wordmark : R.style.TextAppearance_Niixfliix_ToolbarTitle);
		toolbar.getMenu().clear();
		toolbar.inflateMenu(R.menu.home);
		toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.action_settings) {
				tab.startActivity(new Intent(tab.requireContext(), SettingsActivity.class));
				return true;
			}
			return false;
		});
	}
}
