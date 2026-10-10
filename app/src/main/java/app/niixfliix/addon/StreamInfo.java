package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.niixfliix.addon.model.Stream;

public final class StreamInfo {

	public enum Kind {
		DIRECT,
		TORRENT,
		EXTERNAL,
		UNSUPPORTED
	}

	@NonNull public final Stream stream;
	@NonNull public final String addonName;
	@NonNull public final String addonUrl;
	@NonNull public Kind kind = Kind.UNSUPPORTED;
	@NonNull public Quality quality = Quality.OTHER;
	/** e.g. "Torrentio", without the [RD+] tag */
	@NonNull public String source = "";
	@NonNull public String releaseName = "";
	@Nullable public String fileName;
	public long sizeBytes = -1;
	public int seeders = -1;
	@NonNull public final List<String> languages = new ArrayList<>();
	@NonNull public final List<String> tags = new ArrayList<>();
	/** debrid only: true = cached, false = has to be downloaded first */
	@Nullable public Boolean cached;

	public StreamInfo(@NonNull Stream stream, @NonNull String addonName, @NonNull String addonUrl) {
		this.stream = stream;
		this.addonName = addonName;
		this.addonUrl = addonUrl;
	}

	public boolean isUncachedDebrid() {
		return Boolean.FALSE.equals(cached);
	}

	@NonNull
	public String key() {
		String id = stream.url != null ? stream.url : stream.infoHash + ":" + stream.fileIdx;
		return addonUrl + "\n" + id;
	}
}
