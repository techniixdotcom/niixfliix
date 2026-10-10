package app.niixfliix.addon.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

public final class Catalog {

	public static final String EXTRA_SEARCH = "search";
	public static final String EXTRA_GENRE = "genre";
	public static final String EXTRA_SKIP = "skip";

	@Nullable public String type;
	@Nullable public String id;
	@Nullable public String name;
	@Nullable public List<CatalogExtra> extra;
	// older add-ons use these instead of extra[]
	@Nullable public List<String> extraSupported;
	@Nullable public List<String> extraRequired;

	public boolean supportsExtra(@NonNull String extraName) {
		if (extra != null) {
			for (CatalogExtra e : extra) {
				if (extraName.equals(e.name)) {
					return true;
				}
			}
		}
		return extraSupported != null && extraSupported.contains(extraName);
	}

	public boolean isExtraRequired(@NonNull String extraName) {
		if (extra != null) {
			for (CatalogExtra e : extra) {
				if (extraName.equals(e.name) && Boolean.TRUE.equals(e.isRequired)) {
					return true;
				}
			}
		}
		return extraRequired != null && extraRequired.contains(extraName);
	}

	public boolean worksWithoutExtras() {
		if (extra != null) {
			for (CatalogExtra e : extra) {
				if (Boolean.TRUE.equals(e.isRequired)) {
					return false;
				}
			}
		}
		return extraRequired == null || extraRequired.isEmpty();
	}

	/** Discover always sends a genre, so a required genre is fine */
	public boolean worksForDiscover() {
		return onlyRequires(EXTRA_GENRE);
	}

	public boolean worksForSearch() {
		return supportsExtra(EXTRA_SEARCH) && onlyRequires(EXTRA_SEARCH);
	}

	private boolean onlyRequires(String allowed) {
		if (extra != null) {
			for (CatalogExtra e : extra) {
				if (Boolean.TRUE.equals(e.isRequired) && !allowed.equals(e.name)) {
					return false;
				}
			}
		}
		if (extraRequired != null) {
			for (String name : extraRequired) {
				if (!allowed.equals(name)) {
					return false;
				}
			}
		}
		return true;
	}

	@NonNull
	public List<String> genres() {
		if (extra != null) {
			for (CatalogExtra e : extra) {
				if (EXTRA_GENRE.equals(e.name) && e.options != null) {
					return e.options;
				}
			}
		}
		return Collections.emptyList();
	}

	public boolean isValid() {
		return type != null && !type.isEmpty() && id != null && !id.isEmpty();
	}
}
