package app.niixfliix.desktop.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AddonSubtitle {
	@NonNull public final String url;
	@Nullable public final String language;
	@NonNull public final String addonName;

	AddonSubtitle(@NonNull String url, @Nullable String language, @NonNull String addonName) {
		this.url = url;
		this.language = language;
		this.addonName = addonName;
	}
}
