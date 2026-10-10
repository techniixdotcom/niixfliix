package app.niixfliix.account;

import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import app.niixfliix.addon.AddonRepository;
import app.niixfliix.addon.StremioUrl;
import app.niixfliix.addon.model.InstalledAddon;
import app.niixfliix.data.Library;
import app.niixfliix.data.SecretStore;
import app.niixfliix.data.model.LibraryItem;
import app.niixfliix.torrentio.TorrentioConfig;
import app.niixfliix.util.SizeCappedReader;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Optional Stremio sign-in. While signed in, add-ons and library sync both ways; signing out leaves local data alone.
 *
 * Only the auth key is kept (encrypted), never the password. Requests go to api.strem.io only and don't follow
 * redirects since the key is in the body. Field names are the ones stremio-core uses.
 */
public final class StremioAccount implements Library.Changes {

	public interface SignInCallback {
		void onSignedIn(int addons, int titles);

		/** @param wrongLogin the API said no, rather than the network failing */
		void onFailed(boolean wrongLogin);
	}

	private static final String API = "https://api.strem.io/api/";
	private static final String KEY_AUTH = "stremio_auth_key";
	private static final String KEY_EMAIL = "stremio_email";
	/** a big library is a few MB of JSON */
	private static final long MAX_RESPONSE_BYTES = 8L << 20;
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	private static final class ApiException extends IOException {
		ApiException() {
			super("Stremio API refused the request");
		}
	}

	/** what one sync brought down */
	private static final class Pulled {
		final List<String> addonUrls = new ArrayList<>();
		final List<LibraryItem> titles = new ArrayList<>();
		final Map<String, JsonObject> raw = new HashMap<>();
	}

	private final OkHttpClient http;
	private final SecretStore secrets;
	private final ExecutorService io;
	private final Handler main;
	private final AddonRepository addons;
	private final Library library;
	/** the account's own copy of each title, so a removal keeps its watch state intact */
	private Map<String, JsonObject> accountItems = new HashMap<>();

	public StremioAccount(@NonNull OkHttpClient http, @NonNull SecretStore secrets, @NonNull ExecutorService io,
			@NonNull Handler main, @NonNull AddonRepository addons, @NonNull Library library) {
		this.http = http.newBuilder().followRedirects(false).followSslRedirects(false).build();
		this.secrets = secrets;
		this.io = io;
		this.main = main;
		this.addons = addons;
		this.library = library;
	}

	public boolean isSignedIn() {
		return secrets.get(KEY_AUTH) != null;
	}

	@Nullable
	public String email() {
		return secrets.get(KEY_EMAIL);
	}

	@MainThread
	public void signIn(@NonNull String email, @NonNull String password, @NonNull SignInCallback callback) {
		io.execute(() -> {
			String key;
			try {
				key = login(email, password);
			} catch (ApiException e) {
				main.post(() -> callback.onFailed(true));
				return;
			} catch (IOException | RuntimeException e) {
				main.post(() -> callback.onFailed(false));
				return;
			}
			try {
				Pulled pulled = pull(key);
				// stored only after the first sync, a failure leaves us signed out
				if (!secrets.put(KEY_AUTH, key)) {
					throw new IOException("Auth key couldn't be stored");
				}
				secrets.put(KEY_EMAIL, email);
				main.post(() -> {
					apply(pulled);
					callback.onSignedIn(pulled.addonUrls.size(), pulled.titles.size());
				});
			} catch (IOException | RuntimeException e) {
				main.post(() -> callback.onFailed(false));
			}
		});
	}

	@WorkerThread
	@NonNull
	private String login(String email, String password) throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("type", "Login");
		body.addProperty("email", email);
		body.addProperty("password", password);
		body.addProperty("facebook", false);
		String key = string(call("login", body), "authKey");
		if (key == null) {
			throw new IOException("No auth key");
		}
		return key;
	}

	/** on app start: picks up add-ons and titles added in Stremio since last time */
	@MainThread
	public void sync() {
		String key = secrets.get(KEY_AUTH);
		if (key == null) {
			return;
		}
		io.execute(() -> {
			try {
				Pulled pulled = pull(key);
				main.post(() -> apply(pulled));
			} catch (IOException | RuntimeException e) {
				// offline or Stremio down, try again next start
			}
		});
	}

	@MainThread
	public void signOut() {
		String key = secrets.get(KEY_AUTH);
		secrets.put(KEY_AUTH, null);
		secrets.put(KEY_EMAIL, null);
		accountItems = new HashMap<>();
		if (key != null) {
			io.execute(() -> {
				JsonObject body = new JsonObject();
				body.addProperty("type", "Logout");
				body.addProperty("authKey", key);
				try {
					call("logout", body);
				} catch (IOException | RuntimeException e) {
					// the key is gone from the phone either way
				}
			});
		}
	}

	@Override
	public void onSaved(@NonNull LibraryItem item) {
		push(fresh(item, false));
	}

	@Override
	public void onRemoved(@NonNull LibraryItem item) {
		JsonObject known = accountItems.get(item.id);
		JsonObject change = known != null ? known.deepCopy() : fresh(item, true);
		change.addProperty("removed", true);
		change.addProperty("_mtime", Instant.now().toString());
		push(change);
	}

	@WorkerThread
	private Pulled pull(String key) throws IOException {
		Pulled pulled = new Pulled();
		JsonObject collection = new JsonObject();
		collection.addProperty("type", "AddonCollectionGet");
		collection.addProperty("authKey", key);
		collection.addProperty("update", true);
		JsonElement addonsResult = call("addonCollectionGet", collection);
		JsonArray descriptors = addonsResult.isJsonObject() ? array(addonsResult.getAsJsonObject(), "addons") : null;
		if (descriptors != null) {
			for (JsonElement d : descriptors) {
				String url = d.isJsonObject() ? string(d, "transportUrl") : null;
				if (url != null) {
					pulled.addonUrls.add(url);
				}
			}
		}

		JsonObject get = new JsonObject();
		get.addProperty("authKey", key);
		get.addProperty("collection", "libraryItem");
		get.add("ids", new JsonArray());
		get.addProperty("all", true);
		JsonElement items = call("datastoreGet", get);
		if (items.isJsonArray()) {
			for (JsonElement e : items.getAsJsonArray()) {
				LibraryItem item = e.isJsonObject() ? title(e.getAsJsonObject()) : null;
				if (item != null) {
					pulled.titles.add(item);
					pulled.raw.put(item.id, e.getAsJsonObject());
				}
			}
		}
		return pulled;
	}

	/** saved titles only: not removed, not the "temp" entries Stremio keeps for things merely watched */
	@Nullable
	static LibraryItem title(JsonObject o) {
		String id = string(o, "_id");
		String type = string(o, "type");
		String name = string(o, "name");
		if (id == null || type == null || name == null || bool(o, "removed") || bool(o, "temp")) {
			return null;
		}
		LibraryItem item = new LibraryItem();
		item.id = id;
		item.type = type;
		item.name = name;
		String poster = string(o, "poster");
		item.poster = poster != null && poster.startsWith("https://") ? poster : null;
		item.addedAt = millis(string(o, "_ctime"));
		return item;
	}

	@MainThread
	private void apply(Pulled pulled) {
		if (!isSignedIn()) {
			// signed out while this was on its way
			return;
		}
		accountItems = pulled.raw;
		importAddons(pulled.addonUrls);
		library.merge(pulled.titles);
		// and the other way: titles saved here before signing in
		JsonArray missing = new JsonArray();
		for (LibraryItem item : library.all()) {
			if (!accountItems.containsKey(item.id)) {
				missing.add(fresh(item, false));
			}
		}
		if (!missing.isEmpty()) {
			push(missing);
		}
	}

	/** adds what isn't installed yet; never removes anything */
	@MainThread
	private void importAddons(List<String> urls) {
		for (String url : urls) {
			String manifest = StremioUrl.toManifestUrl(url);
			if (manifest == null) {
				// Stremio's own local add-ons are plain http on 127.0.0.1, not for us
				continue;
			}
			if (TorrentioConfig.isTorrentio(manifest)) {
				InstalledAddon local = addons.torrentio();
				// the account's Torrentio wins unless it would drop a debrid key set up here
				boolean better = local == null || (!local.transportUrl.equals(manifest)
						&& (TorrentioConfig.parse(manifest).hasDebrid()
								|| !TorrentioConfig.parse(local.transportUrl).hasDebrid()));
				if (better) {
					addons.setTorrentioUrl(manifest);
				}
				continue;
			}
			addons.add(manifest, new AddonRepository.AddCallback() {
				@Override
				public void onAdded(@NonNull InstalledAddon addon) {
				}

				@Override
				public void onFailed(@NonNull AddonRepository.AddError error) {
					// already installed, or unreachable right now
				}
			});
		}
	}

	private void push(JsonElement changes) {
		String key = secrets.get(KEY_AUTH);
		if (key == null) {
			return;
		}
		JsonArray list;
		if (changes.isJsonArray()) {
			list = changes.getAsJsonArray();
		} else {
			list = new JsonArray();
			list.add(changes);
		}
		JsonObject put = new JsonObject();
		put.addProperty("authKey", key);
		put.addProperty("collection", "libraryItem");
		put.add("changes", list);
		io.execute(() -> {
			try {
				call("datastorePut", put);
			} catch (IOException | RuntimeException e) {
				// the phone keeps the change; it goes up with the next sync
			}
		});
	}

	/** a library item the way stremio-core expects it, with an empty watch state */
	@NonNull
	static JsonObject fresh(LibraryItem item, boolean removed) {
		String now = Instant.now().toString();
		JsonObject state = new JsonObject();
		state.add("lastWatched", JsonNull.INSTANCE);
		state.addProperty("timeWatched", 0);
		state.addProperty("timeOffset", 0);
		state.addProperty("overallTimeWatched", 0);
		state.addProperty("timesWatched", 0);
		state.addProperty("flaggedWatched", 0);
		state.addProperty("duration", 0);
		state.add("video_id", JsonNull.INSTANCE);
		state.add("watched", JsonNull.INSTANCE);
		state.addProperty("noNotif", false);
		JsonObject o = new JsonObject();
		o.addProperty("_id", item.id);
		o.addProperty("name", item.name);
		o.addProperty("type", item.type);
		o.addProperty("poster", item.poster != null ? item.poster : "");
		o.addProperty("posterShape", "poster");
		o.addProperty("removed", removed);
		o.addProperty("temp", false);
		o.addProperty("_ctime", item.addedAt > 0 ? Instant.ofEpochMilli(item.addedAt).toString() : now);
		o.addProperty("_mtime", now);
		o.add("state", state);
		return o;
	}

	@WorkerThread
	@NonNull
	private JsonElement call(String path, JsonObject body) throws IOException {
		Request request = new Request.Builder()
				.url(API + path)
				.post(RequestBody.create(body.toString(), JSON))
				.build();
		try (Response response = http.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("HTTP " + response.code());
			}
			ResponseBody responseBody = response.body();
			if (responseBody == null) {
				throw new IOException("Empty body");
			}
			JsonElement parsed;
			try {
				parsed = JsonParser.parseString(SizeCappedReader.readUtf8(responseBody.byteStream(), MAX_RESPONSE_BYTES));
			} catch (JsonParseException e) {
				throw new IOException("Not JSON", e);
			}
			if (!parsed.isJsonObject()) {
				throw new IOException("Unexpected answer");
			}
			JsonObject o = parsed.getAsJsonObject();
			if (o.has("error") && !o.get("error").isJsonNull()) {
				throw new ApiException();
			}
			JsonElement result = o.get("result");
			if (result == null || result.isJsonNull()) {
				throw new IOException("No result");
			}
			return result;
		}
	}

	@Nullable
	private static String string(JsonElement e, String name) {
		if (!e.isJsonObject()) {
			return null;
		}
		JsonElement v = e.getAsJsonObject().get(name);
		return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && !v.getAsString().isEmpty()
				? v.getAsString() : null;
	}

	@Nullable
	private static JsonArray array(JsonObject o, String name) {
		JsonElement v = o.get(name);
		return v != null && v.isJsonArray() ? v.getAsJsonArray() : null;
	}

	private static boolean bool(JsonObject o, String name) {
		JsonElement v = o.get(name);
		return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && v.getAsBoolean();
	}

	private static long millis(@Nullable String iso) {
		if (iso == null) {
			return 0;
		}
		try {
			return Instant.parse(iso).toEpochMilli();
		} catch (DateTimeParseException e) {
			return 0;
		}
	}
}
