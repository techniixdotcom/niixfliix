package app.niixfliix.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SizeCappedReaderTest {

	@Test
	public void readsWithinTheLimit() throws IOException {
		byte[] data = "héllo wörld".getBytes(StandardCharsets.UTF_8);
		assertEquals("héllo wörld", SizeCappedReader.readUtf8(new ByteArrayInputStream(data), data.length));
	}

	@Test
	public void stopsOnceTheLimitIsPassed() throws IOException {
		InputStream endless = new InputStream() {
			private long served;

			@Override
			public int read() {
				served++;
				if (served > 50_000_000) {
					throw new AssertionError("Kept reading long after the limit");
				}
				return 'a';
			}
		};
		try {
			SizeCappedReader.read(endless, 1024);
			fail("Expected TooLargeException");
		} catch (SizeCappedReader.TooLargeException expected) {
		}
	}

	@Test
	public void exactlyTheLimitIsFine() throws IOException {
		byte[] data = new byte[4096];
		assertEquals(4096, SizeCappedReader.read(new ByteArrayInputStream(data), 4096).length);
	}

	@Test
	public void oneByteOverFails() {
		try {
			SizeCappedReader.read(new ByteArrayInputStream(new byte[4097]), 4096);
			fail("Expected TooLargeException");
		} catch (IOException expected) {
		}
	}
}
