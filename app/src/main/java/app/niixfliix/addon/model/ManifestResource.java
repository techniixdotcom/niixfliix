package app.niixfliix.addon.model;

import androidx.annotation.Nullable;

import java.util.List;

/** Either a plain string ("stream") or an object with its own types/idPrefixes. */
public final class ManifestResource {

	@Nullable public String name;
	@Nullable public List<String> types;
	@Nullable public List<String> idPrefixes;

	public ManifestResource(@Nullable String name) {
		this.name = name;
	}
}
