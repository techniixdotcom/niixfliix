package app.niixfliix.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import app.niixfliix.addon.model.ProxyHeaders;
import app.niixfliix.addon.model.Stream;
import app.niixfliix.addon.model.StreamHints;

public class StreamHeadersTest {

	private static Stream withHeaders(Map<String, String> headers) {
		Stream s = new Stream();
		s.behaviorHints = new StreamHints();
		s.behaviorHints.proxyHeaders = new ProxyHeaders();
		s.behaviorHints.proxyHeaders.request = headers;
		return s;
	}

	@Test
	public void keepsValidHeadersOnly() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Referer", "https://example.com/");
		headers.put("User-Agent", "Mozilla/5.0");
		headers.put("Bad Name", "x");
		headers.put("X-Unicode", "é");
		headers.put("X-Newline", "a\r\nInjected: yes");
		headers.put(null, "x");
		headers.put("X-Null", null);
		Map<String, String> out = withHeaders(headers).requestHeaders();
		assertEquals(2, out.size());
		assertEquals("https://example.com/", out.get("Referer"));
		assertEquals("Mozilla/5.0", out.get("User-Agent"));
	}

	@Test
	public void noHintsMeansNoHeaders() {
		assertTrue(new Stream().requestHeaders().isEmpty());
	}

	@Test
	public void tooManyHeadersAreCut() {
		Map<String, String> headers = new LinkedHashMap<>();
		for (int i = 0; i < 100; i++) {
			headers.put("X-H" + i, "v");
		}
		assertEquals(20, withHeaders(headers).requestHeaders().size());
	}
}
