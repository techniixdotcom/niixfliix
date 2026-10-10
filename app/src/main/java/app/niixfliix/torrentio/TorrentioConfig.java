package app.niixfliix.torrentio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.niixfliix.addon.DefaultAddons;
import app.niixfliix.addon.StremioUrl;
import app.niixfliix.debrid.DebridService;

/**
 * Torrentio keeps its config in the URL path, name=value pairs joined by '|':
 * https://torrentio.strem.fun/sort=seeders|realdebrid=KEY/manifest.json
 * Unknown options are kept so links from their configure page round-trip.
 */
public final class TorrentioConfig {

	public enum Sort {
		QUALITY(null),
		QUALITY_SIZE("qualitysize"),
		SEEDERS("seeders"),
		SIZE("size");

		@Nullable public final String value;

		Sort(@Nullable String value) {
			this.value = value;
		}

		@NonNull
		static Sort fromValue(@Nullable String value) {
			for (Sort s : values()) {
				if (s.value != null && s.value.equals(value)) {
					return s;
				}
			}
			return QUALITY;
		}
	}

	public static final List<String> PROVIDERS = Collections.unmodifiableList(Arrays.asList(
			"yts", "eztv", "rarbg", "1337x", "thepiratebay", "kickasstorrents", "torrentgalaxy",
			"magnetdl", "horriblesubs", "nyaasi", "tokyotosho", "anidex", "rutor", "rutracker",
			"comando", "bludv", "torrent9", "ilcorsaronero", "mejortorrent", "wolfmax4k",
			"cinecalidad", "besttorrents"));

	public static final List<String> QUALITY_FILTERS = Collections.unmodifiableList(Arrays.asList(
			"brremux", "hdrall", "dolbyvision", "dolbyvisionwithhdr", "threed", "4k", "1080p",
			"720p", "480p", "other", "scr", "cam", "unknown"));

	public static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList(
			"japanese", "russian", "italian", "portuguese", "spanish", "latino", "korean", "chinese",
			"taiwanese", "french", "german", "dutch", "hindi", "telugu", "tamil", "polish",
			"lithuanian", "latvian", "estonian", "czech", "slovakian", "slovenian", "hungarian",
			"romanian", "bulgarian", "serbian", "croatian", "ukrainian", "greek", "danish", "finnish",
			"swedish", "norwegian", "turkish", "arabic", "persian", "hebrew", "vietnamese",
			"indonesian", "malay", "thai"));

	private static final String KEY_PROVIDERS = "providers";
	private static final String KEY_SORT = "sort";
	private static final String KEY_LANGUAGE = "language";
	private static final String KEY_QUALITY_FILTER = "qualityfilter";
	private static final String KEY_LIMIT = "limit";
	private static final String KEY_DEBRID_OPTIONS = "debridoptions";
	private static final String NO_DOWNLOAD_LINKS = "nodownloadlinks";
	private static final String NO_CATALOG = "nocatalog";

	/** empty = all providers */
	public final Set<String> providers = new LinkedHashSet<>();
	public Sort sort = Sort.QUALITY;
	public final Set<String> qualityFilter = new LinkedHashSet<>();
	/** 0 = no limit */
	public int limitPerQuality;
	/** priority order */
	public final List<String> languages = new ArrayList<>();
	@Nullable public DebridService debrid;
	@Nullable public String debridKey;
	public boolean hideDownloadLinks = true;
	public boolean hideDebridCatalog = true;
	private final Map<String, String> unknown = new LinkedHashMap<>();
	private final Set<String> unknownDebridOptions = new LinkedHashSet<>();

	public boolean hasDebrid() {
		return debrid != null && DebridService.isPlausibleKey(debridKey);
	}

	@NonNull
	public String toManifestUrl() {
		List<String> parts = new ArrayList<>();
		if (!providers.isEmpty() && !providers.containsAll(PROVIDERS)) {
			parts.add(KEY_PROVIDERS + "=" + String.join(",", providers));
		}
		if (sort.value != null) {
			parts.add(KEY_SORT + "=" + sort.value);
		}
		if (!languages.isEmpty()) {
			parts.add(KEY_LANGUAGE + "=" + String.join(",", languages));
		}
		if (!qualityFilter.isEmpty()) {
			parts.add(KEY_QUALITY_FILTER + "=" + String.join(",", qualityFilter));
		}
		if (limitPerQuality > 0) {
			parts.add(KEY_LIMIT + "=" + limitPerQuality);
		}
		for (Map.Entry<String, String> e : unknown.entrySet()) {
			parts.add(e.getKey() + "=" + e.getValue());
		}
		if (hasDebrid()) {
			Set<String> options = new LinkedHashSet<>();
			if (hideDownloadLinks) {
				options.add(NO_DOWNLOAD_LINKS);
			}
			if (hideDebridCatalog) {
				options.add(NO_CATALOG);
			}
			options.addAll(unknownDebridOptions);
			if (!options.isEmpty()) {
				parts.add(KEY_DEBRID_OPTIONS + "=" + String.join(",", options));
			}
			parts.add(debrid.torrentioKey + "=" + debridKey);
		}
		if (parts.isEmpty()) {
			return DefaultAddons.TORRENTIO;
		}
		return DefaultAddons.TORRENTIO_ORIGIN + "/" + String.join("|", parts) + "/manifest.json";
	}

	/** defaults for anything that isn't a Torrentio URL */
	@NonNull
	public static TorrentioConfig parse(@Nullable String manifestUrl) {
		TorrentioConfig config = new TorrentioConfig();
		if (manifestUrl == null || !isTorrentio(manifestUrl)) {
			return config;
		}
		String path = StremioUrl.configurationOf(manifestUrl);
		if (path.isEmpty()) {
			return config;
		}
		// older links use %7C
		path = path.replace("%7C", "|").replace("%7c", "|").replace("%2C", ",").replace("%2c", ",");
		// only debridoptions turns these on; without debrid the defaults come back below
		config.hideDownloadLinks = false;
		config.hideDebridCatalog = false;
		for (String part : path.split("\\|")) {
			int eq = part.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = part.substring(0, eq);
			String value = part.substring(eq + 1);
			DebridService service = DebridService.fromTorrentioKey(key);
			if (service != null) {
				config.debrid = service;
				config.debridKey = value;
				continue;
			}
			switch (key) {
				case KEY_PROVIDERS:
					config.providers.addAll(splitList(value));
					break;
				case KEY_SORT:
					config.sort = Sort.fromValue(value);
					break;
				case KEY_LANGUAGE:
					config.languages.addAll(splitList(value));
					break;
				case KEY_QUALITY_FILTER:
					config.qualityFilter.addAll(splitList(value));
					break;
				case KEY_LIMIT:
					config.limitPerQuality = parsePositiveInt(value);
					break;
				case KEY_DEBRID_OPTIONS:
					for (String option : splitList(value)) {
						if (NO_DOWNLOAD_LINKS.equals(option)) {
							config.hideDownloadLinks = true;
						} else if (NO_CATALOG.equals(option)) {
							config.hideDebridCatalog = true;
						} else {
							config.unknownDebridOptions.add(option);
						}
					}
					break;
				default:
					config.unknown.put(key, value);
			}
		}
		if (config.debrid == null) {
			config.hideDownloadLinks = true;
			config.hideDebridCatalog = true;
		}
		return config;
	}

	public static boolean isTorrentio(@NonNull String manifestUrl) {
		return DefaultAddons.TORRENTIO_HOST.equals(StremioUrl.hostOf(manifestUrl));
	}

	private static List<String> splitList(String value) {
		List<String> out = new ArrayList<>();
		for (String item : value.split(",")) {
			String trimmed = item.trim();
			if (!trimmed.isEmpty()) {
				out.add(trimmed);
			}
		}
		return out;
	}

	private static int parsePositiveInt(String value) {
		try {
			return Math.max(0, Integer.parseInt(value));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
