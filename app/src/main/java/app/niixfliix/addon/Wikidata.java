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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import app.niixfliix.BuildConfig;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Official titles in any language for a batch of IMDb ids, from Wikidata (IMDb id is property P345). Run by the
 * Wikimedia Foundation, so it's there when TMDB's community-hosted add-on isn't, and one request covers a whole row
 * of posters.
 */
final class Wikidata {

	private static final String SPARQL = "https://query.wikidata.org/sparql";
	/** Wikimedia blocks requests without a descriptive User-Agent */
	private static final String USER_AGENT = "niixfliix/" + BuildConfig.VERSION_NAME
			+ " (https://github.com/" + BuildConfig.GITHUB_REPO + ")";
	private static final long MAX_BYTES = 1L << 20;
	private static final long TIMEOUT_MS = 8000;
	private static final Pattern IMDB_ID = Pattern.compile("tt\\d{1,10}");
	private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(-[a-z]{2,4})?");

	private Wikidata() {
	}

	/** imdb id -> title, for the ids Wikidata has a title for in one of {@code languages} */
	@WorkerThread
	@NonNull
	static Map<String, String> titles(@NonNull OkHttpClient http, @NonNull Collection<String> imdbIds,
			@NonNull List<String> languages) throws IOException {
		String query = query(imdbIds, languages);
		if (query == null) {
			return new HashMap<>();
		}
		HttpUrl url = HttpUrl.get(SPARQL).newBuilder()
				.addQueryParameter("format", "json")
				.addQueryParameter("query", query)
				.build();
		Request request = new Request.Builder().url(url)
				.header("Accept", "application/sparql-results+json")
				.header("User-Agent", USER_AGENT)
				.build();
		Call call = http.newCall(request);
		call.timeout().timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS);
		try (Response response = call.execute()) {
			ResponseBody body = response.body();
			if (!response.isSuccessful() || body == null) {
				throw new IOException("Wikidata HTTP " + response.code());
			}
			return parse(SizeCappedReader.readUtf8(body.byteStream(), MAX_BYTES), languages);
		}
	}

	/** null if nothing valid is left to ask for. Ids and languages are checked, they end up inside the query. */
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

	/** picks, per id, the label in the earliest of {@code languages} */
	@VisibleForTesting
	@NonNull
	static Map<String, String> parse(@NonNull String json, @NonNull List<String> languages) throws IOException {
		Map<String, String> titles = new HashMap<>();
		Map<String, Integer> rank = new HashMap<>();
		try {
			JsonElement root = JsonParser.parseString(json);
			JsonObject results = root.isJsonObject() ? root.getAsJsonObject().getAsJsonObject("results") : null;
			JsonArray bindings = results != null ? results.getAsJsonArray("bindings") : null;
			if (bindings == null) {
				throw new IOException("Bad Wikidata answer");
			}
			for (JsonElement e : bindings) {
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

	/** Wikidata's language codes for a TMDB language, best first: "pt-BR" -> pt-br, pt */
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
