package app.niixfliix.debrid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DebridKeyCheckTest {

	@Test
	public void readsEachServiceAnswer() {
		assertEquals(DebridKeyCheck.Result.VALID,
				DebridKeyCheck.readBody(DebridService.ALL_DEBRID, "{\"status\": \"success\", \"data\": {}}"));
		assertEquals(DebridKeyCheck.Result.INVALID,
				DebridKeyCheck.readBody(DebridService.PREMIUMIZE, "{\"status\":\"error\",\"message\":\"bad key\"}"));
		assertEquals(DebridKeyCheck.Result.VALID,
				DebridKeyCheck.readBody(DebridService.TORBOX, "{\"success\":true,\"data\":{}}"));
		assertEquals(DebridKeyCheck.Result.INVALID,
				DebridKeyCheck.readBody(DebridService.DEBRID_LINK, "{\"success\":false}"));
		assertEquals(DebridKeyCheck.Result.VALID,
				DebridKeyCheck.readBody(DebridService.REAL_DEBRID, "{\"id\":1,\"username\":\"me\"}"));
		assertEquals(DebridKeyCheck.Result.UNKNOWN,
				DebridKeyCheck.readBody(DebridService.ALL_DEBRID, "<html>maintenance</html>"));
	}

	@Test
	public void keyShape() {
		assertTrue(DebridService.isPlausibleKey("ABCDEF1234567890"));
		assertFalse(DebridService.isPlausibleKey("short"));
		assertFalse(DebridService.isPlausibleKey("has space in it"));
		assertFalse(DebridService.isPlausibleKey("abc|sort=x"));
		assertFalse(DebridService.isPlausibleKey(null));
	}

	@Test
	public void keysAreMasked() {
		assertEquals("••••3456", DebridService.mask("ABCDEF123456"));
		assertEquals("", DebridService.mask(null));
	}
}
