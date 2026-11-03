package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import app.niixfliix.addon.model.Catalog;

public final class DiscoverGenres {

	private static final Pattern NOT_A_GENRE = Pattern.compile("[\\d\\s./–-]+");

	@NonNull private final List<CatalogSource> sources;
	@NonNull private final List<String> genres;

	public DiscoverGenres(@NonNull List<CatalogSource> sourcesOfOneType) {
		this.sources = new ArrayList<>(sourcesOfOneType);
		Map<String, String> found = new LinkedHashMap<>();
		for (CatalogSource s : sources) {
			if (!s.catalog.supportsExtra(Catalog.EXTRA_GENRE)) {
				continue;
			}
			for (String g : s.catalog.genres()) {
				if (isGenre(g)) {
					found.putIfAbsent(key(g), g.trim());
				}
			}
		}
		this.genres = Collections.unmodifiableList(new ArrayList<>(found.values()));
	}

	static boolean isGenre(@Nullable String value) {
		return value != null && !value.trim().isEmpty() && !NOT_A_GENRE.matcher(value.trim()).matches();
	}

	private static String key(String genre) {
		return genre.trim().toLowerCase(Locale.ROOT);
	}

	@NonNull
	public List<String> genres() {
		return genres;
	}

	public boolean hasAll() {
		return allSource() != null;
	}

	public boolean isEmpty() {
		return sources.isEmpty();
	}

	@Nullable
	public CatalogSource sourceFor(@Nullable String genre) {
		if (genre == null) {
			return allSource();
		}
		String wanted = key(genre);
		for (CatalogSource s : sources) {
			if (!s.catalog.supportsExtra(Catalog.EXTRA_GENRE)) {
				continue;
			}
			for (String g : s.catalog.genres()) {
				if (key(g).equals(wanted)) {
					return s;
				}
			}
		}
		return null;
	}

	@Nullable
	private CatalogSource allSource() {
		for (CatalogSource s : sources) {
			if (s.catalog.worksWithoutExtras()) {
				return s;
			}
		}
		return null;
	}

	@Nullable
	public String firstChoice() {
		if (hasAll()) {
			return null;
		}
		return genres.isEmpty() ? null : genres.get(0);
	}
}
