package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import app.niixfliix.addon.AddonClient;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.addon.model.Meta;

/** one catalog of one add-on */
public final class CatalogSource {

	@NonNull public final InstalledAddon addon;
	@NonNull public final Catalog catalog;

	public CatalogSource(@NonNull InstalledAddon addon, @NonNull Catalog catalog) {
		this.addon = addon;
		this.catalog = catalog;
	}

	@NonNull
	public String label() {
		String name = catalog.name != null ? catalog.name : catalog.id;
		return name + " · " + addon.displayName();
	}

	/** null if the add-on failed */
	@Nullable
	@WorkerThread
	public List<Meta> fetch(@NonNull AddonClient client, @NonNull Map<String, String> extras) {
		return fetch(client, addon, catalog, extras);
	}

	@Nullable
	@WorkerThread
	public static List<Meta> fetch(@NonNull AddonClient client, @NonNull InstalledAddon addon,
			@NonNull Catalog catalog, @NonNull Map<String, String> extras) {
		try {
			return client.catalog(addon, catalog.type, catalog.id, extras);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
