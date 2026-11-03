package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.data.model.HistoryEntry;
import app.niixfliix.data.model.LibraryItem;

public final class PosterItem {

	@NonNull public final String id;
	@NonNull public final String type;
	@NonNull public final String name;
	@Nullable public final String poster;
	public final float progress;
	public final boolean isNew;
	public final boolean header;
	public final boolean wholePicture;
	public final long released;

	public PosterItem(@NonNull String id, @NonNull String type, @NonNull String name, @Nullable String poster,
			float progress) {
		this(id, type, name, poster, progress, false, false, false, 0);
	}

	private PosterItem(@NonNull String id, @NonNull String type, @NonNull String name, @Nullable String poster,
			float progress, boolean isNew, boolean header, boolean wholePicture, long released) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.poster = poster;
		this.progress = progress;
		this.isNew = isNew;
		this.header = header;
		this.wholePicture = wholePicture;
		this.released = released;
	}

	@NonNull
	public PosterItem withNew(boolean isNew) {
		return isNew == this.isNew ? this
				: new PosterItem(id, type, name, poster, progress, isNew, header, wholePicture, released);
	}

	@NonNull
	public static PosterItem header(@NonNull String key, @NonNull String text) {
		return new PosterItem(key, "\u0000header", text, null, -1, false, true, false, 0);
	}

	@NonNull
	public static PosterItem of(@NonNull Meta meta) {
		return new PosterItem(meta.id != null ? meta.id : "", meta.type != null ? meta.type : "",
				meta.name != null ? meta.name : "", meta.poster, -1, false, false, meta.hasWidePoster(),
				ReleaseOrder.of(meta));
	}

	@NonNull
	public static PosterItem of(@NonNull HistoryEntry entry, boolean withProgress) {
		return new PosterItem(entry.metaId, entry.type, entry.name, entry.poster,
				withProgress ? entry.progress() : -1);
	}

	@NonNull
	public static PosterItem of(@NonNull LibraryItem item) {
		return new PosterItem(item.id, item.type, item.name, item.poster, -1, false, false, false, item.released);
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
