package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.model.Meta;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.LibraryItem;

public final class PosterItem {

	@NonNull public final String id;
	@NonNull public final String type;
	@NonNull public final String name;
	@Nullable public final String poster;
	/** 0..1, negative = no bar */
	public final float progress;

	public PosterItem(@NonNull String id, @NonNull String type, @NonNull String name, @Nullable String poster,
			float progress) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.poster = poster;
		this.progress = progress;
	}

	@NonNull
	public static PosterItem of(@NonNull Meta meta) {
		return new PosterItem(meta.id != null ? meta.id : "", meta.type != null ? meta.type : "",
				meta.name != null ? meta.name : "", meta.poster, -1);
	}

	@NonNull
	public static PosterItem of(@NonNull HistoryEntry entry, boolean withProgress) {
		return new PosterItem(entry.metaId, entry.type, entry.name, entry.poster,
				withProgress ? entry.progress() : -1);
	}

	@NonNull
	public static PosterItem of(@NonNull LibraryItem item) {
		return new PosterItem(item.id, item.type, item.name, item.poster, -1);
	}

	@NonNull
	public static List<PosterItem> ofMetas(@NonNull List<Meta> metas) {
		List<PosterItem> out = new ArrayList<>(metas.size());
		for (Meta m : metas) {
			out.add(of(m));
		}
		return out;
	}
}
