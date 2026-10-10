package app.niixfliix.torrentio;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.niixfliix.addon.Quality;
import app.niixfliix.addon.StreamInfo;
import app.niixfliix.addon.model.Stream;

/**
 * Torrentio packs everything into two text fields:
 * <pre>
 * name:  "[RD+] Torrentio\n4k DV | HDR"
 * title: "Movie.2019.2160p.UHD.BluRay.x265-GRP\n👤 125 💾 15.2 GB ⚙️ ThePirateBay\nMulti Audio / 🇬🇧 / 🇮🇹"
 * </pre>
 * Series add a second line with the file name inside a season pack. Other add-ons
 * (ThePirateBay+ and friends) often copy this layout, so the same parser is used for
 * every stream; whatever it can't find just stays empty.
 */
public final class TorrentioStreamParser {

	private static final Pattern DEBRID_TAG = Pattern.compile("\\[[A-Za-z]{2,3}(\\+| download)?\\]");
	// "👤 125" (Torrentio), "🌱 125", "Seeds: 125", "S: 125"
	private static final Pattern SEEDERS = Pattern.compile("(?:👤|🌱|(?i:\\bseed(?:er)?s?|\\bS)\\s*:)\\s*(\\d+)");
	private static final Pattern SIZE = Pattern.compile("💾\\s*([0-9]+(?:[.,][0-9]+)?)\\s*([KMGT]i?B)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PROVIDER = Pattern.compile("⚙️?\\s*\\S[^\\n]*$");
	private static final Pattern FLAG = Pattern.compile("[\\x{1F1E6}-\\x{1F1FF}]{2}");
	private static final Pattern LANGUAGE_WORDS = Pattern.compile("(?i)\\b(multi audio|dual audio|multi subs)\\b");
	private static final Pattern HEVC = Pattern.compile("(?i)(?<![a-z0-9])(x265|h\\.?265|hevc)(?![a-z0-9])");
	private static final Pattern REMUX = Pattern.compile("(?i)(?<![a-z0-9])remux(?![a-z0-9])");
	private static final Pattern CAM = Pattern.compile("(?i)(?<![a-z0-9])(cam|hdcam|camrip|telesync|hdts|scr|screener)(?![a-z0-9])");

	private TorrentioStreamParser() {
	}

	@NonNull
	public static StreamInfo parse(@NonNull Stream stream, @NonNull String addonName, @NonNull String addonUrl) {
		StreamInfo info = new StreamInfo(stream, addonName, addonUrl);
		info.kind = kindOf(stream);
		readName(stream.name != null ? stream.name : "", info);
		readDetails(stream.detailsText(), info);

		if (stream.behaviorHints != null) {
			if (info.fileName == null && stream.behaviorHints.filename != null) {
				info.fileName = stream.behaviorHints.filename;
			}
			if (info.sizeBytes < 0 && stream.behaviorHints.videoSize != null && stream.behaviorHints.videoSize > 0) {
				info.sizeBytes = stream.behaviorHints.videoSize;
			}
		}
		if (info.releaseName.isEmpty()) {
			info.releaseName = info.fileName != null ? info.fileName : info.source;
		}

		Quality quality = Quality.detect(stream.name);
		if (quality == null) {
			quality = Quality.detect(info.releaseName);
		}
		if (quality == null) {
			quality = Quality.detect(info.fileName);
		}
		info.quality = quality != null ? quality : Quality.OTHER;

		String release = info.releaseName + " " + (info.fileName != null ? info.fileName : "");
		addTagIf(info, REMUX.matcher(release).find(), "REMUX");
		addTagIf(info, HEVC.matcher(release).find(), "HEVC");
		addTagIf(info, CAM.matcher(release).find(), "CAM");
		return info;
	}

	@NonNull
	private static StreamInfo.Kind kindOf(Stream stream) {
		if (stream.isDirect()) {
			return StreamInfo.Kind.DIRECT;
		}
		if (stream.isTorrent()) {
			return StreamInfo.Kind.TORRENT;
		}
		if (stream.externalUrl != null || stream.ytId != null) {
			return StreamInfo.Kind.EXTERNAL;
		}
		return StreamInfo.Kind.UNSUPPORTED;
	}

	private static void readName(String name, StreamInfo info) {
		String[] lines = name.split("\n");
		String first = lines.length > 0 ? lines[0] : "";
		Matcher m = DEBRID_TAG.matcher(first);
		if (m.find()) {
			info.cached = "+".equals(m.group(1));
			first = first.substring(0, m.start()) + first.substring(m.end());
		}
		info.source = first.trim();
		for (int i = 1; i < lines.length; i++) {
			for (String piece : lines[i].split("\\|")) {
				String token = piece.trim();
				if (token.isEmpty()) {
					continue;
				}
				// quality is already the group header
				String rest = token.replaceFirst("(?i)^(4k|2160p|1440p|1080p|720p|480p|576p|360p)\\s*", "").trim();
				for (String word : rest.split("\\s+")) {
					addTagIf(info, !word.isEmpty(), word);
				}
			}
		}
	}

	private static void readDetails(String text, StreamInfo info) {
		int nameLines = 0;
		for (String raw : text.split("\n")) {
			String line = raw.trim();
			if (line.isEmpty()) {
				continue;
			}
			boolean stats = false;
			Matcher seeders = SEEDERS.matcher(line);
			if (seeders.find()) {
				info.seeders = parseCount(seeders.group(1));
				stats = true;
			}
			Matcher size = SIZE.matcher(line);
			if (size.find()) {
				info.sizeBytes = toBytes(size.group(1), size.group(2));
				stats = true;
			}
			if (PROVIDER.matcher(line).find()) {
				stats = true;
			}
			if (stats) {
				continue;
			}
			if (FLAG.matcher(line).find() || LANGUAGE_WORDS.matcher(line).find()) {
				for (String part : line.split("/")) {
					String language = part.trim();
					if (!language.isEmpty() && !info.languages.contains(language)) {
						info.languages.add(language);
					}
				}
				continue;
			}
			if (nameLines == 0) {
				info.releaseName = line;
			} else if (nameLines == 1) {
				info.fileName = line;
			}
			nameLines++;
		}
	}

	private static int parseCount(String digits) {
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	static long toBytes(String number, String unit) {
		double value;
		try {
			value = Double.parseDouble(number.replace(',', '.'));
		} catch (NumberFormatException e) {
			return -1;
		}
		int power;
		switch (Character.toUpperCase(unit.charAt(0))) {
			case 'K':
				power = 1;
				break;
			case 'M':
				power = 2;
				break;
			case 'G':
				power = 3;
				break;
			default:
				power = 4;
		}
		return (long) (value * Math.pow(1024, power));
	}

	private static void addTagIf(StreamInfo info, boolean condition, String tag) {
		if (condition && !info.tags.contains(tag)) {
			info.tags.add(tag);
		}
	}
}
