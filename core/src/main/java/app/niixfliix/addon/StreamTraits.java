package app.niixfliix.addon;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StreamTraits {

	public enum Codec {
		AVC,
		HEVC,
		AV1
	}

	private static final Pattern HEVC = Pattern.compile("(?<![a-z0-9])(x265|h\\.?265|hevc)(?![a-z0-9])");
	private static final Pattern AV1 = Pattern.compile("(?<![a-z0-9])av1(?![a-z0-9])");
	private static final Pattern TEN_BIT = Pattern.compile(
			"(?<![a-z0-9])(10[ .-]?bits?|hi10p?|hdr(10)?(\\+|plus)?|dv|dovi|dolby[ .]?vision)(?![a-z0-9])");
	private static final Pattern DOLBY_VISION = Pattern.compile("(?<![a-z0-9])(dv|dovi|dolby[ .]?vision)(?![a-z0-9])");
	private static final Pattern HDR_FALLBACK = Pattern.compile("(?<![a-z0-9])hdr(10)?(\\+|plus)?(?![a-z0-9])");

	public final Codec codec;
	public final boolean tenBit;
	public final boolean dolbyVisionOnly;

	StreamTraits(@NonNull Codec codec, boolean tenBit, boolean dolbyVisionOnly) {
		this.codec = codec;
		this.tenBit = tenBit;
		this.dolbyVisionOnly = dolbyVisionOnly;
	}

	@NonNull
	public static StreamTraits of(@NonNull StreamInfo stream) {
		StringBuilder text = new StringBuilder(stream.releaseName);
		if (stream.fileName != null) {
			text.append(' ').append(stream.fileName);
		}
		for (String tag : stream.tags) {
			text.append(' ').append(tag);
		}
		return of(text.toString());
	}

	@NonNull
	static StreamTraits of(@NonNull String releaseText) {
		String text = releaseText.toLowerCase(Locale.ROOT).replace('_', ' ');
		Codec codec = AV1.matcher(text).find() ? Codec.AV1 : HEVC.matcher(text).find() ? Codec.HEVC : Codec.AVC;
		boolean dolbyVision = DOLBY_VISION.matcher(text).find();
		boolean tenBit = TEN_BIT.matcher(text).find();
		return new StreamTraits(codec, tenBit, dolbyVision && !HDR_FALLBACK.matcher(text).find());
	}
}
