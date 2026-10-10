package app.niixfliix.addon.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Stream {

	private static final int MAX_HEADERS = 20;
	private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,64}");
	private static final Pattern HEADER_VALUE = Pattern.compile("[\\x20-\\x7e]{0,1024}");

	@Nullable public String name;
	@Nullable public String title;
	@Nullable public String description;
	@Nullable public String url;
	@Nullable public String infoHash;
	@Nullable public Integer fileIdx;
	@Nullable public String externalUrl;
	@Nullable public String ytId;
	@Nullable public List<String> sources;
	@Nullable public List<Subtitle> subtitles;
	@Nullable public StreamHints behaviorHints;

	public boolean isDirect() {
		return url != null && url.regionMatches(true, 0, "https://", 0, 8);
	}

	public boolean isTorrent() {
		return !isDirect() && infoHash != null && infoHash.matches("(?i)[0-9a-f]{40}");
	}

	@NonNull
	public String detailsText() {
		if (description != null && !description.isEmpty()) {
			return description;
		}
		return title != null ? title : "";
	}

	@Nullable
	public String bingeGroup() {
		return behaviorHints != null ? behaviorHints.bingeGroup : null;
	}

	/** proxyHeaders from the add-on, minus anything OkHttp would throw on. */
	@NonNull
	public Map<String, String> requestHeaders() {
		if (behaviorHints == null || behaviorHints.proxyHeaders == null
				|| behaviorHints.proxyHeaders.request == null) {
			return Collections.emptyMap();
		}
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : behaviorHints.proxyHeaders.request.entrySet()) {
			String name = e.getKey();
			String value = e.getValue();
			if (name != null && value != null && out.size() < MAX_HEADERS
					&& HEADER_NAME.matcher(name).matches() && HEADER_VALUE.matcher(value).matches()) {
				out.put(name, value);
			}
		}
		return out;
	}

	@NonNull
	public List<Subtitle> subtitlesOrEmpty() {
		return subtitles != null ? subtitles : Collections.emptyList();
	}
}
