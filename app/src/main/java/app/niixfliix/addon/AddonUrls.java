package app.niixfliix.addon;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class AddonUrls {

	private AddonUrls() {
	}

	/** Ids keep their colons (tt0944947:1:2), add-ons expect them that way. */
	@NonNull
	public static String resource(@NonNull String manifestUrl, @NonNull String resource, @NonNull String type,
			@NonNull String id, @NonNull Map<String, String> extras) {
		StringBuilder url = new StringBuilder(StremioUrl.baseOf(manifestUrl));
		url.append('/').append(encode(resource))
				.append('/').append(encode(type))
				.append('/').append(encode(id));
		if (!extras.isEmpty()) {
			url.append('/');
			boolean first = true;
			for (Map.Entry<String, String> e : extras.entrySet()) {
				if (!first) {
					url.append('&');
				}
				url.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
				first = false;
			}
		}
		return url.append(".json").toString();
	}

	/** like encodeURIComponent but leaves ':' alone */
	@NonNull
	static String encode(@NonNull String value) {
		StringBuilder out = new StringBuilder();
		for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xff;
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
					|| "-_.!~*'():".indexOf(c) >= 0) {
				out.append((char) c);
			} else {
				out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
						.append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
			}
		}
		return out.toString();
	}
}
