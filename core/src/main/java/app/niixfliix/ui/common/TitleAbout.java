package app.niixfliix.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import app.niixfliix.addon.ReleaseOrder;
import app.niixfliix.addon.model.Meta;
import app.niixfliix.addon.model.MetaLink;

public final class TitleAbout {

	public enum Fact {
		DIRECTOR,
		WRITERS,
		RELEASED,
		COUNTRY,
		AWARDS
	}

	public static final class Entry {
		@NonNull public final Fact fact;
		@NonNull public final String value;

		Entry(@NonNull Fact fact, @NonNull String value) {
			this.fact = fact;
			this.value = value;
		}
	}

	private static final Pattern IMDB_ID = Pattern.compile("tt\\d{5,10}");
	private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
	private static final int MAX_PEOPLE = 4;
	private static final int MAX_CAST = 12;
	private static final int MAX_TEXT = 300;

	private TitleAbout() {
	}

	@NonNull
	public static List<Entry> facts(@NonNull Meta m, @NonNull Locale locale, @NonNull String separator) {
		List<Entry> out = new ArrayList<>();
		add(out, Fact.DIRECTOR, join(people(m.director, m.links, "Directors", MAX_PEOPLE), separator));
		add(out, Fact.WRITERS, join(people(m.writer, m.links, "Writers", MAX_PEOPLE), separator));
		add(out, Fact.RELEASED, releasedDate(m.released, locale));
		add(out, Fact.COUNTRY, text(m.country, separator));
		add(out, Fact.AWARDS, text(m.awards, separator));
		return out;
	}

	@NonNull
	public static List<String> cast(@NonNull Meta m) {
		return people(m.cast, m.links, "Cast", MAX_CAST);
	}

	@Nullable
	public static String imdbUrl(@NonNull Meta m) {
		String id = text(m.imdbId, "");
		if (id == null || !IMDB_ID.matcher(id).matches()) {
			id = m.id;
		}
		return id != null && IMDB_ID.matcher(id).matches() ? "https://www.imdb.com/title/" + id + "/" : null;
	}

	@Nullable
	public static String trailerImage(@Nullable String youtubeId) {
		return youtubeId != null && YOUTUBE_ID.matcher(youtubeId).matches()
				? "https://img.youtube.com/vi/" + youtubeId + "/hqdefault.jpg" : null;
	}

	@Nullable
	static String releasedDate(@Nullable String released, @NonNull Locale locale) {
		long at = ReleaseOrder.parseDate(released);
		if (at <= 0) {
			return null;
		}
		LocalDate day = Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate();
		return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(day);
	}

	@NonNull
	static List<String> people(@Nullable List<String> listed, @Nullable List<MetaLink> links, @NonNull String category,
			int max) {
		Set<String> out = new LinkedHashSet<>();
		if (listed != null) {
			for (String name : listed) {
				addName(out, name, max);
			}
		}
		if (out.isEmpty() && links != null) {
			for (MetaLink link : links) {
				if (link != null && category.equalsIgnoreCase(link.category)) {
					addName(out, link.name, max);
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static void addName(Set<String> out, @Nullable String name, int max) {
		if (name != null && !name.trim().isEmpty() && out.size() < max) {
			out.add(name.trim());
		}
	}

	@Nullable
	static String text(@Nullable JsonElement value, @NonNull String separator) {
		if (value == null || value.isJsonNull()) {
			return null;
		}
		if (value.isJsonPrimitive()) {
			return clean(value.getAsString());
		}
		if (value.isJsonArray()) {
			List<String> parts = new ArrayList<>();
			JsonArray array = value.getAsJsonArray();
			for (JsonElement e : array) {
				if (e != null && e.isJsonPrimitive()) {
					String part = clean(e.getAsString());
					if (part != null) {
						parts.add(part);
					}
				}
			}
			return parts.isEmpty() ? null : clean(String.join(separator, parts));
		}
		return null;
	}

	@Nullable
	private static String clean(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String t = value.trim().replaceAll("\\s+", " ");
		if (t.isEmpty() || t.equalsIgnoreCase("N/A")) {
			return null;
		}
		return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT).trim() + "…" : t;
	}

	@Nullable
	private static String join(@NonNull List<String> names, @NonNull String separator) {
		return names.isEmpty() ? null : String.join(separator, names);
	}

	private static void add(List<Entry> out, Fact fact, @Nullable String value) {
		if (value != null && !value.isEmpty()) {
			out.add(new Entry(fact, value));
		}
	}
}
