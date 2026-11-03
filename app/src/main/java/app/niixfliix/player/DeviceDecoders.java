package app.niixfliix.player;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.StreamTraits;
import app.niixfliix.util.Logs;

public final class DeviceDecoders {

	private static final String TAG = "DeviceDecoders";
	private static final String AVC = "video/avc";
	private static final String HEVC = "video/hevc";
	private static final String AV1 = "video/av01";
	private static final String DOLBY_VISION = "video/dolby-vision";
	private static final int FRAME_RATE = 24;

	private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();
	private static volatile MediaCodecInfo[] codecs;

	private DeviceDecoders() {
	}

	public static void warmUp() {
		isSmooth(StreamTraits.Codec.HEVC, false, Quality.P1080);
		isSmooth(StreamTraits.Codec.HEVC, true, Quality.P1080);
		isSmooth(StreamTraits.Codec.AV1, false, Quality.P1080);
		isSmooth(StreamTraits.Codec.AVC, false, Quality.P1080);
	}

	public static boolean isSmooth(@NonNull StreamInfo stream) {
		StreamTraits traits = StreamTraits.of(stream);
		if (traits.dolbyVisionOnly && !hasHardware(DOLBY_VISION, false, 1280, 720)) {
			return false;
		}
		return isSmooth(traits.codec, traits.tenBit, stream.quality);
	}

	private static boolean isSmooth(StreamTraits.Codec codec, boolean tenBit, Quality quality) {
		int[] size = size(quality);
		String mime = codec == StreamTraits.Codec.AV1 ? AV1 : codec == StreamTraits.Codec.HEVC ? HEVC : AVC;
		return hasHardware(mime, tenBit, size[0], size[1]);
	}

	private static int[] size(Quality quality) {
		switch (quality) {
			case P2160:
				return new int[] {3840, 2160};
			case P1440:
				return new int[] {2560, 1440};
			case P720:
				return new int[] {1280, 720};
			case P480:
				return new int[] {854, 480};
			case P1080:
			default:
				return new int[] {1920, 1080};
		}
	}

	private static boolean hasHardware(String mime, boolean tenBit, int width, int height) {
		String key = mime + '|' + tenBit + '|' + width + 'x' + height;
		Boolean known = CACHE.get(key);
		if (known != null) {
			return known;
		}
		boolean found;
		try {
			found = findHardware(mime, tenBit, width, height);
		} catch (RuntimeException e) {
			Logs.w(TAG, "Could not read the decoders", e);
			found = true;
		}
		CACHE.put(key, found);
		return found;
	}

	private static boolean findHardware(String mime, boolean tenBit, int width, int height) {
		MediaCodecInfo[] all = codecs;
		if (all == null) {
			all = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
			codecs = all;
		}
		for (MediaCodecInfo info : all) {
			if (info.isEncoder() || !isHardware(info)) {
				continue;
			}
			for (String type : info.getSupportedTypes()) {
				if (!type.equalsIgnoreCase(mime)) {
					continue;
				}
				MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
				if (fits(caps, mime, tenBit, width, height)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean fits(MediaCodecInfo.CodecCapabilities caps, String mime, boolean tenBit, int width,
			int height) {
		if (DOLBY_VISION.equals(mime)) {
			return true;
		}
		MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
		if (video == null) {
			return false;
		}
		boolean size = video.areSizeAndRateSupported(width, height, FRAME_RATE)
				|| video.areSizeAndRateSupported(height, width, FRAME_RATE);
		if (!size) {
			return false;
		}
		if (!tenBit) {
			return true;
		}
		for (MediaCodecInfo.CodecProfileLevel level : caps.profileLevels) {
			if (isTenBitProfile(mime, level.profile)) {
				return true;
			}
		}
		return false;
	}

	@SuppressLint("InlinedApi")
	private static boolean isTenBitProfile(String mime, int profile) {
		switch (mime) {
			case HEVC:
				return profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
						|| profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
						|| profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus;
			case AV1:
				return profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
						|| profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10
						|| profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus;
			case AVC:
				return profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10;
			default:
				return false;
		}
	}

	private static boolean isHardware(MediaCodecInfo info) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			return info.isHardwareAccelerated() && !info.isSoftwareOnly();
		}
		String name = info.getName().toLowerCase(Locale.ROOT);
		return !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.startsWith("omx.ffmpeg.")
				&& !name.contains(".sw.") && !name.endsWith(".sw");
	}
}
