package app.niixfliix.download.model;

import androidx.annotation.Nullable;

import java.util.Map;

/** stored encrypted */
public final class DownloadSource {

	@Nullable public String url;
	@Nullable public Map<String, String> headers;
}
