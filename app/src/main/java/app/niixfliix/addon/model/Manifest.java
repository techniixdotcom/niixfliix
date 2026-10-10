package app.niixfliix.addon.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Manifest {

	public static final String RESOURCE_CATALOG = "catalog";
	public static final String RESOURCE_META = "meta";
	public static final String RESOURCE_STREAM = "stream";
	public static final String RESOURCE_SUBTITLES = "subtitles";

	@Nullable public String id;
	@Nullable public String name;
	@Nullable public String version;
	@Nullable public String description;
	@Nullable public String logo;
	@Nullable public List<ManifestResource> resources;
	@Nullable public List<String> types;
	@Nullable public List<String> idPrefixes;
	@Nullable public List<Catalog> catalogs;
	@Nullable public ManifestHints behaviorHints;

	/** Resource-level types/idPrefixes override the manifest-wide ones. No prefixes = any id. */
	public boolean supports(@NonNull String resource, @NonNull String type, @NonNull String id) {
		if (resources == null) {
			return false;
		}
		for (ManifestResource r : resources) {
			if (!resource.equals(r.name)) {
				continue;
			}
			List<String> allowedTypes = r.types != null ? r.types : types;
			List<String> prefixes = r.idPrefixes != null ? r.idPrefixes : idPrefixes;
			if (allowedTypes == null || !allowedTypes.contains(type)) {
				continue;
			}
			if (prefixes == null || prefixes.isEmpty()) {
				return true;
			}
			for (String prefix : prefixes) {
				if (prefix != null && id.startsWith(prefix)) {
					return true;
				}
			}
		}
		return false;
	}

	@NonNull
	public List<Catalog> validCatalogs() {
		if (catalogs == null) {
			return Collections.emptyList();
		}
		List<Catalog> out = new ArrayList<>(catalogs.size());
		for (Catalog c : catalogs) {
			if (c.isValid()) {
				out.add(c);
			}
		}
		return out;
	}

	public boolean isAdult() {
		return behaviorHints != null && Boolean.TRUE.equals(behaviorHints.adult);
	}

	public boolean needsConfiguration() {
		return behaviorHints != null && Boolean.TRUE.equals(behaviorHints.configurationRequired);
	}

	public boolean isConfigurable() {
		return behaviorHints != null && Boolean.TRUE.equals(behaviorHints.configurable);
	}

	@NonNull
	public String displayName() {
		return name != null && !name.isEmpty() ? name : id != null ? id : "";
	}
}
