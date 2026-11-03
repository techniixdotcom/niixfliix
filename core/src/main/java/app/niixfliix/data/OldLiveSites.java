package app.niixfliix.data;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.LinkedHashSet;
import java.util.Set;

import app.niixfliix.util.Logs;

public final class OldLiveSites {

	private static final String TAG = "OldLiveSites";
	private static final String KEY_SITES = "live_sites";
	private static final String SECRET_USER = "live_user_";
	private static final String SECRET_PASSWORD = "live_password_";
	private static final String KEY_DONE = "old_live_sites_removed";
	private static final String[] SUGGESTED = {"blue", "srf"};

	private OldLiveSites() {
	}

	public static boolean removeOnce(@NonNull KeyValueStore settings, @NonNull SecretStore secrets,
			@NonNull Gson gson) {
		if (settings.getBoolean(KEY_DONE, false)) {
			return false;
		}
		Set<String> ids = new LinkedHashSet<>();
		for (String id : SUGGESTED) {
			ids.add(id);
		}
		String json = settings.getString(KEY_SITES, null);
		if (json != null) {
			try {
				JsonElement root = gson.fromJson(json, JsonElement.class);
				if (root != null && root.isJsonArray()) {
					JsonArray sites = root.getAsJsonArray();
					for (JsonElement e : sites) {
						if (e.isJsonObject()) {
							JsonObject site = e.getAsJsonObject();
							JsonElement id = site.get("id");
							if (id != null && id.isJsonPrimitive()) {
								ids.add(id.getAsString());
							}
						}
					}
				}
			} catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
				Logs.w(TAG, "The old Live list was unreadable; removing it anyway", e);
			}
		}
		for (String id : ids) {
			secrets.put(SECRET_USER + id, null);
			secrets.put(SECRET_PASSWORD + id, null);
		}
		settings.remove(KEY_SITES);
		settings.putBoolean(KEY_DONE, true);
		return true;
	}
}
