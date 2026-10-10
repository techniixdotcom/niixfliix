package app.niixfliix.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** For movies videoId == metaId. */
public final class HistoryEntry {

	@NonNull public String metaId = "";
	@NonNull public String type = "";
	@NonNull public String name = "";
	@Nullable public String poster;
	@NonNull public String videoId = "";
	public long positionMs;
	public long durationMs;
	public long updatedAt;

	public float progress() {
		if (durationMs <= 0) {
			return 0f;
		}
		return Math.min(1f, Math.max(0f, positionMs / (float) durationMs));
	}
}
