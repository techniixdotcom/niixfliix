package app.niixfliix.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedactorTest {

	@Test
	public void addonUrlsLoseTheirConfiguration() {
		String line = Redactor.redact("Manifest refresh failed for https://torrentio.strem.fun/sort=seeders|realdebrid=SECRETKEY99/manifest.json: timeout");
		assertFalse(line.contains("SECRETKEY99"));
		assertTrue(line.contains("torrentio.strem.fun"));
	}

	@Test
	public void keyValuesAndBearerTokensAreHidden() {
		String line = Redactor.redact("premiumize=PMKEY123 apikey=abc Authorization: Bearer xyz.123");
		assertFalse(line.contains("PMKEY123"));
		assertFalse(line.contains("abc"));
		assertFalse(line.contains("xyz.123"));
	}

	@Test
	public void magnetLinksAreShortened() {
		assertEquals("open magnet:…", Redactor.redact("open magnet:?xt=urn:btih:abcdef&tr=udp://x"));
	}

	@Test
	public void nullIsEmpty() {
		assertEquals("", Redactor.redact(null));
	}

	@Test
	public void emailsPasswordsInUrlsAndJsonSecretsAreHidden() {
		String line = Redactor.redact("login someone.name+tag@mail.example.org failed");
		assertEquals("login ***@*** failed", line);
		line = Redactor.redact("GET https://user:hunter2@example.com/x");
		assertFalse(line.contains("hunter2"));
		assertTrue(line.contains("https://example.com/…"));
		line = Redactor.redact("{\"type\":\"Logout\",\"authKey\":\"abc123\",\"password\": \"pw\"}");
		assertFalse(line.contains("abc123"));
		assertFalse(line.contains("\"pw\""));
		assertTrue(line.contains("\"authKey\":\"***\""));
	}
}
