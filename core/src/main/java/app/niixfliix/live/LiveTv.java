package app.niixfliix.live;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.ui.common.CatalogSource;

public final class LiveTv {

	private static final int MAX_GENRE_ROWS = 12;

	public static final class Row {
		@NonNull public final CatalogSource source;
		@Nullable public final String genre;
		@NonNull public final String key;

		Row(@NonNull CatalogSource source, @Nullable String genre) {
			this.source = source;
			this.genre = genre;
			this.key = source.addon.transportUrl + "|" + source.catalog.type + "|" + source.catalog.id
					+ (genre != null ? "|" + genre : "");
		}

		@NonNull
		public String name() {
			String name = source.catalog.name != null ? source.catalog.name : source.catalog.id;
			return genre != null ? name + " · " + genre : name;
		}

		public boolean isFreeSports() {
			return FreeSports.isIt(source.addon.transportUrl);
		}

		@NonNull
		public Map<String, String> extras() {
			return genre != null ? Collections.singletonMap(Catalog.EXTRA_GENRE, genre) : Collections.emptyMap();
		}
	}

	private LiveTv() {
	}

	@NonNull
	public static List<Row> rows(@NonNull AddonRepository addons) {
		List<Row> rows = new ArrayList<>();
		for (InstalledAddon addon : addons.active()) {
			if (addon.manifest == null) {
				continue;
			}
			for (Catalog catalog : addon.manifest.validCatalogs()) {
				if (!catalog.worksForLive()) {
					continue;
				}
				CatalogSource source = new CatalogSource(addon, catalog);
				if (catalog.worksWithoutExtras()) {
					rows.add(new Row(source, null));
					continue;
				}
				List<String> genres = catalog.genres();
				for (int i = 0; i < genres.size() && i < MAX_GENRE_ROWS; i++) {
					String genre = genres.get(i);
					if (genre != null && !genre.trim().isEmpty()) {
						rows.add(new Row(source, genre));
					}
				}
			}
		}
		List<Row> sports = new ArrayList<>();
		for (Row row : rows) {
			if (row.isFreeSports()) {
				sports.add(row);
			}
		}
		rows.removeAll(sports);
		rows.addAll(0, sports);
		return rows;
	}

	public static boolean hasAny(@NonNull AddonRepository addons) {
		return !rows(addons).isEmpty();
	}
}
