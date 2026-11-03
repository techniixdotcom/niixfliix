package app.niixfliix.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import javax.crypto.spec.SecretKeySpec;

public class OldLiveSitesTest {

	private final MemoryStore settings = new MemoryStore();
	private final SecretStore secrets = new SecretStore(new MemoryStore(), () -> new SecretKeySpec(new byte[32], "AES"));
	private final Gson gson = new Gson();

	@Test
	public void signInsAndSitesGoOnce() {
		settings.putString("live_sites", "[{\"id\":\"blue\",\"name\":\"blue\",\"url\":\"https://tv.blue.ch\"},"
				+ "{\"id\":\"x1\",\"name\":\"Mine\",\"url\":\"https://tv.example.com\"}]");
		secrets.put("live_user_blue", "me@example.com");
		secrets.put("live_password_blue", "secret");
		secrets.put("live_user_x1", "me");
		secrets.put("live_password_x1", "pw");
		secrets.put("live_user_srf", "someone");
		secrets.put("installed_addons", "[]");
		assertTrue(OldLiveSites.removeOnce(settings, secrets, gson));
		assertNull(secrets.get("live_user_blue"));
		assertNull(secrets.get("live_password_blue"));
		assertNull(secrets.get("live_user_x1"));
		assertNull(secrets.get("live_password_x1"));
		assertNull(secrets.get("live_user_srf"));
		assertNull(settings.getString("live_sites", null));
		assertEquals("[]", secrets.get("installed_addons"));
		assertFalse(OldLiveSites.removeOnce(settings, secrets, gson));
	}

	@Test
	public void anUnreadableListIsRemovedToo() {
		settings.putString("live_sites", "{not json");
		secrets.put("live_user_blue", "me@example.com");
		assertTrue(OldLiveSites.removeOnce(settings, secrets, gson));
		assertNull(settings.getString("live_sites", null));
		assertNull(secrets.get("live_user_blue"));
	}
}
