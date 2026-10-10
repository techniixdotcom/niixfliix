package app.niixfliix.addon.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.niixfliix.addon.StremioUrl;

public final class InstalledAddon {

	@NonNull public String transportUrl;
	@Nullable public Manifest manifest;

	public InstalledAddon(@NonNull String transportUrl) {
		this.transportUrl = transportUrl;
	}

	@NonNull
	public String displayName() {
		if (manifest != null && !manifest.displayName().isEmpty()) {
			return manifest.displayName();
		}
		String host = StremioUrl.hostOf(transportUrl);
		return host != null ? host : "";
	}

	public boolean supports(@NonNull String resource, @NonNull String type, @NonNull String id) {
		return manifest != null && manifest.supports(resource, type, id);
	}
}
