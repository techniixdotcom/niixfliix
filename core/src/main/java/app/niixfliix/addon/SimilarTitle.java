package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class SimilarTitle {

	@NonNull public final String imdbId;
	@NonNull public final String name;
	@Nullable public final String released;
	public final int score;
	public final boolean styled;

	public SimilarTitle(@NonNull String imdbId, @NonNull String name, @Nullable String released, int score, boolean styled) {
		this.imdbId = imdbId;
		this.name = name;
		this.released = released;
		this.score = score;
		this.styled = styled;
	}
}
