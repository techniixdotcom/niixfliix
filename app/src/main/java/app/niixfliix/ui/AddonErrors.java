package app.niixfliix.ui;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import app.niixfliix.R;
import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.InstalledAddon;

public final class AddonErrors {

	private AddonErrors() {
	}

	@StringRes
	public static int message(@NonNull AddonRepository.AddError error) {
		switch (error) {
			case INVALID_URL:
				return R.string.addon_error_invalid_url;
			case ALREADY_INSTALLED:
				return R.string.addon_error_already_installed;
			case INVALID_MANIFEST:
				return R.string.addon_error_invalid_manifest;
			case STORAGE:
				return R.string.addon_error_storage;
			case UNREACHABLE:
			default:
				return R.string.addon_error_unreachable;
		}
	}

	/** toasts "installed" or the error */
	@NonNull
	public static AddonRepository.AddCallback toastCallback(@NonNull Context context) {
		return new AddonRepository.AddCallback() {
			@Override
			public void onAdded(@NonNull InstalledAddon addon) {
				Toast.makeText(context, context.getString(R.string.addon_installed, addon.displayName()),
						Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onFailed(@NonNull AddonRepository.AddError error) {
				Toast.makeText(context, message(error), Toast.LENGTH_LONG).show();
			}
		};
	}
}
