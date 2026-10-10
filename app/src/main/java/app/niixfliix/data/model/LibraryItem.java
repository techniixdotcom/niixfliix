package app.niixfliix.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class LibraryItem {

	@NonNull public String id = "";
	@NonNull public String type = "";
	@NonNull public String name = "";
	@Nullable public String poster;
	public long addedAt;
}
