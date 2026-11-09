package app.niixfliix.addon;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import app.niixfliix.app.AppInfo;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class Wikidata {

	private static final String SPARQL = "https://query.wikidata.org/sparql";
	private static final String USER_AGENT = AppInfo.APP_SLUG + "/" + AppInfo.VERSION_NAME
			+ " (https://github.com/" + AppInfo.GITHUB_REPO + ")";
	private static final long MAX_BYTES = 1L << 20;
	private static final long TIMEOUT_MS = 8000;
	private static final Pattern IMDB_ID = Pattern.compile("tt\\d{1,10}");
	private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(-[a-z]{2,4})?");
	private static final int MAX_SEARCH_CHARS = 100;
	@VisibleForTesting
	static final int MAX_HITS = 12;

	static final class Hit {
		@NonNull final String imdbId;
		@NonNull final String title;
		final boolean series;

		Hit(@NonNull String imdbId, @NonNull String title, boolean series) {
			this.imdbId = imdbId;
			this.title = title;
			this.series = series;
		}
	}

	private Wikidata() {
	}

	@WorkerThread
	@NonNull
	static Map<String, String> titles(@NonNull OkHttpClient http, @NonNull Collection<String> imdbIds,
			@NonNull List<String> languages) throws IOException {
		String query = query(imdbIds, languages);
		if (query == null) {
			return new HashMap<>();
		}
		return parse(run(http, query), languages);
	}

	@WorkerThread
	@NonNull
	static List<Hit> search(@NonNull OkHttpClient http, @NonNull String text, @NonNull String language)
			throws IOException {
		String query = searchQuery(text, language);
		if (query == null) {
			return new ArrayList<>();
		}
		return parseSearch(run(http, query));
	}

	/**
	 * The similar-titles query is heavy and can take Wikidata most of its own 60-second limit. It runs
	 * in the background (see MoreLikeThis), so it's given that long: a query cut off sooner is never
	 * kept, and every later visit would wait for it again.
	 */
	private static final long SIMILAR_TIMEOUT_MS = 55_000;
	private static final int SIMILAR_LIMIT = 120;
	private static final String BROAD_GENRES = "wd:Q130232, wd:Q157443, wd:Q188473, wd:Q2484376, wd:Q200092, "
			+ "wd:Q1054574, wd:Q471839, wd:Q157394, wd:Q959790, wd:Q319221, wd:Q93204, wd:Q202866, wd:Q1361932, "
			+ "wd:Q1200678, wd:Q369747, wd:Q842256, wd:Q645928, wd:Q17013749, wd:Q1366112, wd:Q21010853, "
			+ "wd:Q20442589, wd:Q459290, wd:Q24862, wd:Q1146335, wd:Q1107, wd:Q581714, wd:Q226730, wd:Q838368";
	private static final String FILM_KINDS = "wd:Q11424 wd:Q24869 wd:Q202866 wd:Q93204 wd:Q506240";
	private static final String SERIES_KINDS = "wd:Q5398426 wd:Q1259759 wd:Q581714 wd:Q63952888 wd:Q526877";
	private static final String STYLE_KINDS = "wd:Q202866 wd:Q581714 wd:Q1107 wd:Q63952888 wd:Q93204 "
			+ "wd:Q2143665 wd:Q1361932";

	@WorkerThread
	@NonNull
	static String similarBody(@NonNull OkHttpClient http, @NonNull String imdbId, boolean series,
			@NonNull List<String> languages) throws IOException {
		String query = similarQuery(imdbId, series, languages);
		if (query == null) {
			throw new IOException("Not an IMDb id");
		}
		// Wikidata sends nothing until the query is done, so the usual read timeout would cut it off
		OkHttpClient patient = http.newBuilder().readTimeout(SIMILAR_TIMEOUT_MS, TimeUnit.MILLISECONDS).build();
		return run(patient, query, SIMILAR_TIMEOUT_MS);
	}

	@VisibleForTesting
	static String similarQuery(@NonNull String imdbId, boolean series, @NonNull List<String> languages) {
		if (!IMDB_ID.matcher(imdbId).matches()) {
			return null;
		}
		StringBuilder langs = new StringBuilder();
		for (String language : languages) {
			if (LANGUAGE.matcher(language).matches() && !"en".equals(language)) {
				langs.append(langs.length() == 0 ? "" : ", ").append('"').append(language).append('"');
			}
		}
		String local = langs.length() == 0 ? ""
				: "OPTIONAL { ?other rdfs:label ?local . FILTER(LANG(?local) IN (" + langs + ")) } ";
		String kinds = series ? SERIES_KINDS : FILM_KINDS;
		return "SELECT ?imdb ?score (SAMPLE(?label) AS ?name) (MIN(?date) AS ?released) (MAX(?style) AS ?styled) "
				+ "WHERE { { SELECT ?other (SUM(?w) AS ?score) WHERE { "
				+ "?self wdt:P345 \"" + imdbId + "\" . "
				+ "{ ?self wdt:P179 ?f . ?other wdt:P179 ?f . BIND(10 AS ?w) } "
				+ "UNION { ?self wdt:P144 ?f . ?other wdt:P144 ?f . BIND(8 AS ?w) } "
				+ "UNION { ?self wdt:P57 ?f . ?other wdt:P57 ?f . BIND(3 AS ?w) } "
				+ "UNION { ?self wdt:P136 ?f . FILTER(?f NOT IN (" + BROAD_GENRES + ")) "
				+ "?other wdt:P136 ?f . BIND(6 AS ?w) } "
				+ "UNION { ?self wdt:P58 ?f . ?other wdt:P58 ?f . BIND(3 AS ?w) } "
				+ "UNION { ?self wdt:P161 ?f . ?other wdt:P161 ?f . BIND(2 AS ?w) } "
				+ "FILTER(?other != ?self) "
				+ "FILTER EXISTS { VALUES ?kind { " + kinds + " } ?other wdt:P31 ?kind } "
				+ "} GROUP BY ?other ORDER BY DESC(?score) LIMIT " + SIMILAR_LIMIT + " } "
				+ "?other wdt:P345 ?imdb . FILTER(STRSTARTS(?imdb, \"tt\")) "
				+ local
				+ "OPTIONAL { ?other rdfs:label ?english . FILTER(LANG(?english) = \"en\") } "
				+ (local.isEmpty() ? "BIND(?english AS ?label) " : "BIND(COALESCE(?local, ?english) AS ?label) ")
				+ "OPTIONAL { ?other wdt:P577 ?date } "
				+ "BIND(IF(EXISTS { VALUES ?sk { " + STYLE_KINDS + " } ?other wdt:P31|wdt:P136 ?sk }, 1, 0) AS ?style) "
				+ "} GROUP BY ?imdb ?score ORDER BY DESC(?score)";
	}

	/** Other films of the same film series, films after or before it, and other versions (remakes, adaptations). */
	private static final long RELATED_TIMEOUT_MS = 25_000;
	private static final int RELATED_LIMIT = 40;

	@WorkerThread
	@NonNull
	static String relatedBody(@NonNull OkHttpClient http, @NonNull String imdbId, @NonNull List<String> languages)
			throws IOException {
		String query = relatedQuery(imdbId, languages);
		if (query == null) {
			throw new IOException("Not an IMDb id");
		}
		OkHttpClient patient = http.newBuilder().readTimeout(RELATED_TIMEOUT_MS, TimeUnit.MILLISECONDS).build();
		return run(patient, query, RELATED_TIMEOUT_MS);
	}

	@VisibleForTesting
	static String relatedQuery(@NonNull String imdbId, @NonNull List<String> languages) {
		if (!IMDB_ID.matcher(imdbId).matches()) {
			return null;
		}
		StringBuilder langs = new StringBuilder();
		for (String language : languages) {
			if (LANGUAGE.matcher(language).matches() && !"en".equals(language)) {
				langs.append(langs.length() == 0 ? "" : ", ").append('"').append(language).append('"');
			}
		}
		String local = langs.length() == 0 ? ""
				: "OPTIONAL { ?other rdfs:label ?local . FILTER(LANG(?local) IN (" + langs + ")) } ";
		return "SELECT ?imdb (MAX(?w) AS ?score) (SAMPLE(?label) AS ?name) (MIN(?date) AS ?released) WHERE { "
				+ "?self wdt:P345 \"" + imdbId + "\" . "
				+ "{ ?self wdt:P179 ?f . ?other wdt:P179 ?f . BIND(3 AS ?w) } "
				+ "UNION { ?self wdt:P144 ?f . ?other wdt:P144 ?f . BIND(2 AS ?w) } "
				+ "UNION { ?self wdt:P144|wdt:P4969|wdt:P155|wdt:P156 ?other . BIND(3 AS ?w) } "
				+ "UNION { ?other wdt:P144|wdt:P4969|wdt:P155|wdt:P156 ?self . BIND(3 AS ?w) } "
				+ "FILTER(?other != ?self) "
				+ "FILTER EXISTS { VALUES ?kind { " + FILM_KINDS + " } ?other wdt:P31 ?kind } "
				+ "?other wdt:P345 ?imdb . FILTER(STRSTARTS(?imdb, \"tt\")) "
				+ local
				+ "OPTIONAL { ?other rdfs:label ?english . FILTER(LANG(?english) = \"en\") } "
				+ (local.isEmpty() ? "BIND(?english AS ?label) " : "BIND(COALESCE(?local, ?english) AS ?label) ")
				+ "OPTIONAL { ?other wdt:P577 ?date } "
				+ "} GROUP BY ?imdb LIMIT " + RELATED_LIMIT;
	}

	@VisibleForTesting
	@NonNull
	static List<SimilarTitle> parseSimilar(@NonNull String json) throws IOException {
		List<SimilarTitle> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (JsonElement element : bindings(json)) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject row = element.getAsJsonObject();
			String imdb = value(row, "imdb");
			String name = value(row, "name");
			String score = value(row, "score");
			if (imdb == null || !IMDB_ID.matcher(imdb).matches() || name == null || name.trim().isEmpty()
					|| score == null || !seen.add(imdb)) {
				continue;
			}
			int points;
			try {
				points = (int) Math.round(Double.parseDouble(score));
			} catch (NumberFormatException e) {
				continue;
			}
			String released = value(row, "released");
			String styled = value(row, "styled");
			out.add(new SimilarTitle(imdb, name.trim(), released, points, "1".equals(styled)));
		}
		return out;
	}

	@NonNull
	private static String run(@NonNull OkHttpClient http, @NonNull String query) throws IOException {
		return run(http, query, TIMEOUT_MS);
	}

	@NonNull
	private static String run(@NonNull OkHttpClient http, @NonNull String query, long timeoutMs) throws IOException {
		HttpUrl url = HttpUrl.get(SPARQL).newBuilder()
				.addQueryParameter("format", "json")
				.addQueryParameter("query", query)
				.build();
		Request request = new Request.Builder().url(url)
				.header("Accept", "application/sparql-results+json")
				.header("User-Agent", USER_AGENT)
				.build();
		Call call = http.newCall(request);
		call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS);
		try (Response response = call.execute()) {
			ResponseBody body = response.body();
			if (!response.isSuccessful() || body == null) {
				throw new IOException("Wikidata HTTP " + response.code());
			}
			return SizeCappedReader.readUtf8(body.byteStream(), MAX_BYTES);
		}
	}

	@VisibleForTesting
	static String searchQuery(@NonNull String text, @NonNull String language) {
		if (!LANGUAGE.matcher(language).matches()) {
			return null;
		}
		String literal = sparqlString(text);
		if (literal == null) {
			return null;
		}
		return "SELECT ?imdb ?label ?series WHERE { "
				+ "SERVICE wikibase:mwapi { "
				+ "bd:serviceParam wikibase:endpoint \"www.wikidata.org\" ; wikibase:api \"EntitySearch\" ; "
				+ "wikibase:limit 50 ; mwapi:search " + literal + " ; mwapi:language \"" + language + "\" . "
				+ "?item wikibase:apiOutputItem mwapi:item . ?rank wikibase:apiOrdinal true . } "
				+ "?item wdt:P345 ?imdb . FILTER(STRSTARTS(?imdb, \"tt\")) "
				+ "FILTER NOT EXISTS { ?item wdt:P31 wd:Q21191270 } "
				+ "OPTIONAL { ?item rdfs:label ?label . FILTER(LANG(?label) = \"" + language + "\") } "
				+ "BIND(EXISTS { ?item wdt:P31/wdt:P279* wd:Q5398426 } AS ?series) "
				+ "} ORDER BY ?rank LIMIT " + MAX_HITS;
	}

	@VisibleForTesting
	static String sparqlString(@NonNull String text) {
		StringBuilder out = new StringBuilder("\"");
		int chars = 0;
		boolean any = false;
		for (int i = 0; i < text.length() && chars < MAX_SEARCH_CHARS; ) {
			int c = text.codePointAt(i);
			i += Character.charCount(c);
			chars++;
			if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
					|| Character.getType(c) == Character.SURROGATE) {
				out.append(' ');
				continue;
			}
			if (c == '\\' || c == '"') {
				out.append('\\');
			}
			out.appendCodePoint(c);
			if (!Character.isWhitespace(c)) {
				any = true;
			}
		}
		return any ? out.append('"').toString() : null;
	}

	@VisibleForTesting
	@NonNull
	static List<Hit> parseSearch(@NonNull String json) throws IOException {
		List<Hit> hits = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		try {
			for (JsonElement e : bindings(json)) {
				JsonObject row = e.getAsJsonObject();
				String imdb = value(row, "imdb");
				if (imdb == null || !IMDB_ID.matcher(imdb).matches() || !seen.add(imdb)) {
					continue;
				}
				String label = value(row, "label");
				hits.add(new Hit(imdb, label != null ? label.trim() : "", "true".equals(value(row, "series"))));
			}
		} catch (JsonParseException | IllegalStateException | ClassCastException e) {
			throw new IOException("Bad Wikidata answer", e);
		}
		return hits;
	}

	@NonNull
	private static JsonArray bindings(@NonNull String json) throws IOException {
		JsonElement root = JsonParser.parseString(json);
		JsonObject results = root.isJsonObject() ? root.getAsJsonObject().getAsJsonObject("results") : null;
		JsonArray bindings = results != null ? results.getAsJsonArray("bindings") : null;
		if (bindings == null) {
			throw new IOException("Bad Wikidata answer");
		}
		return bindings;
	}

	private static String value(@NonNull JsonObject row, @NonNull String name) {
		JsonElement cell = row.get(name);
		if (cell == null || !cell.isJsonObject()) {
			return null;
		}
		JsonElement value = cell.getAsJsonObject().get("value");
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	@VisibleForTesting
	static String query(@NonNull Collection<String> imdbIds, @NonNull List<String> languages) {
		StringBuilder ids = new StringBuilder();
		for (String id : imdbIds) {
			if (IMDB_ID.matcher(id).matches()) {
				ids.append(" \"").append(id).append('"');
			}
		}
		StringBuilder langs = new StringBuilder();
		for (String language : languages) {
			if (LANGUAGE.matcher(language).matches()) {
				langs.append(langs.length() == 0 ? "" : ", ").append('"').append(language).append('"');
			}
		}
		if (ids.length() == 0 || langs.length() == 0) {
			return null;
		}
		return "SELECT ?imdb ?label WHERE { VALUES ?imdb {" + ids + " } ?item wdt:P345 ?imdb . "
				+ "?item rdfs:label ?label . FILTER(LANG(?label) IN (" + langs + ")) }";
	}

	@VisibleForTesting
	@NonNull
	static Map<String, String> parse(@NonNull String json, @NonNull List<String> languages) throws IOException {
		Map<String, String> titles = new HashMap<>();
		Map<String, Integer> rank = new HashMap<>();
		try {
			for (JsonElement e : bindings(json)) {
				JsonObject row = e.getAsJsonObject();
				JsonObject imdb = row.getAsJsonObject("imdb");
				JsonObject label = row.getAsJsonObject("label");
				if (imdb == null || label == null || !imdb.has("value") || !label.has("value")
						|| !label.has("xml:lang")) {
					continue;
				}
				String id = imdb.get("value").getAsString();
				String title = label.get("value").getAsString().trim();
				int r = languages.indexOf(label.get("xml:lang").getAsString().toLowerCase(Locale.ROOT));
				Integer best = rank.get(id);
				if (r >= 0 && !title.isEmpty() && (best == null || r < best)) {
					titles.put(id, title);
					rank.put(id, r);
				}
			}
		} catch (JsonParseException | IllegalStateException | ClassCastException e) {
			throw new IOException("Bad Wikidata answer", e);
		}
		return titles;
	}

	@NonNull
	static List<String> languages(@NonNull String tmdbLanguage) {
		String[] parts = tmdbLanguage.toLowerCase(Locale.ROOT).split("-");
		String language = parts[0];
		String region = parts.length > 1 ? parts[1] : "";
		if ("zh".equals(language)) {
			return "tw".equals(region)
					? Arrays.asList("zh-tw", "zh-hant", "zh-hk", "zh")
					: Arrays.asList("zh-cn", "zh-hans", "zh");
		}
		List<String> out = new ArrayList<>();
		if (!region.isEmpty()) {
			out.add(language + "-" + region);
		}
		out.add(language);
		return out;
	}
}
