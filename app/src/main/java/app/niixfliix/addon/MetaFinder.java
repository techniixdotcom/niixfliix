package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Manifest;
import app.niixfliix.addon.model.Meta;

/** Asks the meta add-ons in order, first answer wins. */
public final class MetaFinder {

	private MetaFinder() {
	}

	@Nullable
	@WorkerThread
	public static Meta find(@NonNull AddonRepository addons, @NonNull AddonClient client, @NonNull String type,
			@NonNull String id) {
		for (InstalledAddon addon : addons.supporting(Manifest.RESOURCE_META, type, id)) {
			try {
				Meta found = client.meta(addon, type, id);
				if (found != null) {
					return found;
				}
			} catch (IOException | RuntimeException e) {
				// try the next one
			}
		}
		return null;
	}
}
