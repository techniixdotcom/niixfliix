package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.niixfliix.addon.model.Catalog;
import app.niixfliix.addon.model.Meta;

public final class HomeHero {

	public static final String GENRE_SEPARATOR = " • ";
	private static final int MAX_GENRES = 3;

	@NonNull public final PosterItem item;
	@Nullable public final Meta meta;

	public HomeHero(@NonNull PosterItem item, @Nullable Meta meta) {
		this.item = item;
		this.meta = meta;
	}

	public static final int ROTATION = 5;
	public static final long ROTATE_EVERY_MS = 8000;

	@Nullable
	public static PosterItem pick(@NonNull List<PosterItem> items) {
		List<PosterItem> all = pickAll(items, 1);
		return all.isEmpty() ? null : all.get(0);
	}

	@NonNull
	public static List<PosterItem> pickAll(@NonNull List<PosterItem> items, int max) {
		List<PosterItem> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (PosterItem p : items) {
			if (out.size() >= max) {
				break;
			}
			if (!p.id.isEmpty() && !p.type.isEmpty() && !p.header && !Catalog.isLiveType(p.type) && seen.add(p.id)) {
				out.add(p);
			}
		}
		return out;
	}

	@NonNull
	public String name() {
		return meta != null && meta.name != null && !meta.name.isEmpty() ? meta.name : item.name;
	}

	@Nullable
	public String backdrop() {
		if (meta != null && meta.background != null && !meta.background.isEmpty()) {
			return meta.background;
		}
		return meta != null && meta.poster != null ? meta.poster : item.poster;
	}

	@Nullable
	public String logo() {
		return meta != null ? meta.logo : null;
	}

	@Nullable
	public String description() {
		return meta != null ? meta.description : null;
	}

	@NonNull
	public String genres() {
		List<String> out = new ArrayList<>();
		if (meta != null) {
			for (String g : meta.allGenres()) {
				if (g != null && !g.trim().isEmpty() && out.size() < MAX_GENRES) {
					out.add(g.trim());
				}
			}
		}
		return String.join(GENRE_SEPARATOR, out);
	}

	public boolean sameTitle(@Nullable HomeHero other) {
		return other != null && other.item.id.equals(item.id);
	}
}
