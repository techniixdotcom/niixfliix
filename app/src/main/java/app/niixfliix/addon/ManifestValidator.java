package app.niixfliix.addon;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.List;

import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.Manifest;

public final class ManifestValidator {

	public static final class InvalidManifestException extends IOException {
		public InvalidManifestException(String reason) {
			super(reason);
		}
	}

	private static final int MAX_TEXT = 200;
	private static final int MAX_DESCRIPTION = 5000;
	private static final int MAX_LIST = 100;
	private static final int MAX_CATALOGS = 200;

	private ManifestValidator() {
	}

	public static void validate(@NonNull Manifest m) throws InvalidManifestException {
		requireText(m.id, "id", MAX_TEXT);
		requireText(m.name, "name", MAX_TEXT);
		if (m.version != null && m.version.length() > MAX_TEXT) {
			throw new InvalidManifestException("version is too long");
		}
		if (m.description != null && m.description.length() > MAX_DESCRIPTION) {
			throw new InvalidManifestException("description is too long");
		}
		if (m.resources == null || m.resources.isEmpty()) {
			throw new InvalidManifestException("no resources");
		}
		if (m.types == null || m.types.isEmpty()) {
			throw new InvalidManifestException("no types");
		}
		checkSize(m.resources, "resources", MAX_LIST);
		checkSize(m.types, "types", MAX_LIST);
		checkSize(m.idPrefixes, "idPrefixes", MAX_LIST);
		checkSize(m.catalogs, "catalogs", MAX_CATALOGS);
		if (m.catalogs != null) {
			for (Catalog c : m.catalogs) {
				if (c.name != null && c.name.length() > MAX_TEXT) {
					throw new InvalidManifestException("catalog name is too long");
				}
			}
		}
		// not fatal, just drop it
		if (m.logo != null && !m.logo.startsWith("https://")) {
			m.logo = null;
		}
	}

	private static void requireText(String value, String field, int max) throws InvalidManifestException {
		if (value == null || value.trim().isEmpty()) {
			throw new InvalidManifestException(field + " is missing");
		}
		if (value.length() > max) {
			throw new InvalidManifestException(field + " is too long");
		}
	}

	private static void checkSize(List<?> list, String field, int max) throws InvalidManifestException {
		if (list != null && list.size() > max) {
			throw new InvalidManifestException("too many " + field);
		}
	}
}
