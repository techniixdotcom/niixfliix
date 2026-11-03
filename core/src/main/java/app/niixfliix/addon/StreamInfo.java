package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.niixfliix.addon.model.Stream;

public final class StreamInfo {

	public enum Kind {
		DIRECT,
		TORRENT,
		EXTERNAL,
		UNSUPPORTED
	}

	private static final String[] VIDEO_EXTENSIONS = {".mkv", ".mp4", ".m4v", ".webm", ".avi"};

	@NonNull public final Stream stream;
	@NonNull public final String addonName;
	@NonNull public final String addonUrl;
	@NonNull public Kind kind = Kind.UNSUPPORTED;
	@NonNull public Quality quality = Quality.OTHER;
	@NonNull public String source = "";
	@NonNull public String releaseName = "";
	@Nullable public String fileName;
	public long sizeBytes = -1;
	public int seeders = -1;
	@NonNull public final List<String> languages = new ArrayList<>();
	@NonNull public final List<String> tags = new ArrayList<>();
	@Nullable public Boolean cached;

	public StreamInfo(@NonNull Stream stream, @NonNull String addonName, @NonNull String addonUrl) {
		this.stream = stream;
		this.addonName = addonName;
		this.addonUrl = addonUrl;
	}

	public boolean isUncachedDebrid() {
		return Boolean.FALSE.equals(cached);
	}

	public boolean isDownloadable() {
		return kind == Kind.DIRECT && stream.url != null && !isUncachedDebrid();
	}

	@NonNull
	public String downloadFileName(@NonNull String title) {
		String name = fileName != null ? fileName : releaseName;
		if (name.isEmpty()) {
			name = title;
		}
		if (name.toLowerCase(Locale.ROOT).matches(".*\\.(mkv|mp4|m4v|webm|avi)$")) {
			return name;
		}
		return name + linkExtension();
	}

	@NonNull
	public String downloadExtension() {
		String name = (fileName != null ? fileName : releaseName).toLowerCase(Locale.ROOT);
		for (String ext : VIDEO_EXTENSIONS) {
			if (name.endsWith(ext)) {
				return ext;
			}
		}
		return linkExtension();
	}

	private String linkExtension() {
		String url = stream.url != null ? stream.url.toLowerCase(Locale.ROOT) : "";
		int query = url.indexOf('?');
		String path = query >= 0 ? url.substring(0, query) : url;
		for (String ext : new String[] {".mp4", ".webm", ".m4v", ".avi"}) {
			if (path.endsWith(ext)) {
				return ext;
			}
		}
		return ".mkv";
	}

	@NonNull
	public String key() {
		String id = stream.url != null ? stream.url : stream.infoHash + ":" + stream.fileIdx;
		return addonUrl + "\n" + id;
	}
}
