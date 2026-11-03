package app.niixfliix.desktop.platform;

import static app.niixfliix.desktop.platform.Native.INT;
import static app.niixfliix.desktop.platform.Native.PTR;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import app.niixfliix.data.SecretStore;
import app.niixfliix.util.Logs;

public final class KeyringKeys implements SecretStore.KeySource {

	private static final String TAG = "KeyringKeys";
	private static final String SCHEMA = Paths.APP_ID + ".Secrets";
	private static final String ATTRIBUTE = "purpose";
	private static final String VALUE = "secrets-key";
	private static final int KEY_BYTES = 32;
	private static final long SCHEMA_SIZE = 8 + 8 + 32 * 16 + 8 + 7 * 8;

	private final File fallback;
	private final File savedSecrets;
	@Nullable private SecretKey key;

	public KeyringKeys(@NonNull File dataDir) {
		fallback = new File(dataDir, "secrets.key");
		savedSecrets = new File(dataDir, "secrets.json");
	}

	@NonNull
	@Override
	public synchronized SecretKey key() throws GeneralSecurityException, IOException {
		if (key == null) {
			key = new SecretKeySpec(load(), "AES");
		}
		return key;
	}

	private byte[] load() throws IOException {
		if (fallback.isFile()) {
			return decode(Files.readString(fallback.toPath(), StandardCharsets.US_ASCII));
		}
		String stored;
		try {
			stored = Libsecret.lookup();
		} catch (IOException | RuntimeException | LinkageError e) {
			if (hasSavedSecrets()) {
				throw new IOException("Keyring not available", e);
			}
			return useFile(e);
		}
		if (stored != null) {
			return decode(stored);
		}
		byte[] fresh = fresh();
		try {
			Libsecret.store(Base64.getEncoder().encodeToString(fresh));
			return fresh;
		} catch (IOException | RuntimeException | LinkageError e) {
			return useFile(e);
		}
	}

	private byte[] useFile(Throwable why) throws IOException {
		Logs.w(TAG, "No keyring, keeping the key in a private file", why);
		byte[] fresh = fresh();
		writePrivate(fallback, Base64.getEncoder().encodeToString(fresh));
		return fresh;
	}

	private boolean hasSavedSecrets() {
		return savedSecrets.isFile() && savedSecrets.length() > 2;
	}

	private static byte[] fresh() {
		byte[] bytes = new byte[KEY_BYTES];
		new SecureRandom().nextBytes(bytes);
		return bytes;
	}

	private static byte[] decode(String text) throws IOException {
		try {
			byte[] bytes = Base64.getDecoder().decode(text.trim());
			if (bytes.length != KEY_BYTES) {
				throw new IOException("Wrong key size");
			}
			return bytes;
		} catch (IllegalArgumentException e) {
			throw new IOException("Unreadable key", e);
		}
	}

	private static void writePrivate(File file, String text) throws IOException {
		Files.createDirectories(file.getParentFile().toPath());
		Files.write(file.toPath(), text.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE);
		Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"));
	}

	private static final class Libsecret {
		private static final SymbolLookup LIB = Native.library("libsecret-1.so.0");
		private static final MethodHandle LOOKUP = Native.variadic(LIB, "secret_password_lookup_sync", 3, PTR,
				PTR, PTR, PTR, PTR, PTR, PTR);
		private static final MethodHandle STORE = Native.variadic(LIB, "secret_password_store_sync", 6, INT,
				PTR, PTR, PTR, PTR, PTR, PTR, PTR, PTR, PTR);
		private static final MethodHandle FREE = Native.function(LIB, "secret_password_free", null, PTR);

		@Nullable
		static String lookup() throws IOException {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment error = arena.allocate(PTR);
				MemorySegment found = (MemorySegment) LOOKUP.invokeExact(schema(arena), MemorySegment.NULL, error,
						arena.allocateFrom(ATTRIBUTE), arena.allocateFrom(VALUE), MemorySegment.NULL);
				String problem = GLib.takeError(error);
				if (problem != null) {
					throw new IOException(problem);
				}
				if (Native.isNull(found)) {
					return null;
				}
				String value = Native.string(found);
				wipeAndFree(found, value);
				return value;
			} catch (IOException | RuntimeException e) {
				throw e;
			} catch (Throwable t) {
				throw Native.rethrow(t);
			}
		}

		static void store(String secret) throws IOException {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment error = arena.allocate(PTR);
				MemorySegment value = arena.allocateFrom(secret);
				int ok = (int) STORE.invokeExact(schema(arena), MemorySegment.NULL,
						arena.allocateFrom(Paths.APP_ID + " secrets key"), value, MemorySegment.NULL, error,
						arena.allocateFrom(ATTRIBUTE), arena.allocateFrom(VALUE), MemorySegment.NULL);
				value.fill((byte) 0);
				String problem = GLib.takeError(error);
				if (ok == 0 || problem != null) {
					throw new IOException(problem != null ? problem : "Keyring refused the key");
				}
			} catch (IOException | RuntimeException e) {
				throw e;
			} catch (Throwable t) {
				throw Native.rethrow(t);
			}
		}

		private static void wipeAndFree(MemorySegment found, @Nullable String value) throws Throwable {
			if (value != null) {
				Native.at(found, value.length()).fill((byte) 0);
			}
			FREE.invokeExact(found);
		}

		private static MemorySegment schema(Arena arena) {
			MemorySegment schema = arena.allocate(SCHEMA_SIZE, 8);
			schema.fill((byte) 0);
			schema.set(PTR, 0, arena.allocateFrom(SCHEMA));
			schema.set(PTR, 16, arena.allocateFrom(ATTRIBUTE));
			return schema;
		}
	}
}
