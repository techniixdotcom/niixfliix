package app.niixfliix.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.UnrecoverableKeyException;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import app.niixfliix.util.Logs;

public final class SecretStore {

	public interface KeySource {
		@NonNull
		SecretKey key() throws GeneralSecurityException, IOException;

		default boolean isGone(@NonNull GeneralSecurityException e) {
			return e instanceof AEADBadTagException || e instanceof UnrecoverableKeyException;
		}
	}

	private static final String TAG = "SecretStore";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final KeyValueStore store;
	private final KeySource keys;

	public SecretStore(@NonNull KeyValueStore store, @NonNull KeySource keys) {
		this.store = store;
		this.keys = keys;
	}

	@Nullable
	public synchronized String get(@NonNull String name) {
		String stored = store.getString(name, null);
		if (stored == null) {
			return null;
		}
		try {
			byte[] all = Base64.getDecoder().decode(stored);
			if (all.length <= IV_BYTES) {
				throw new IllegalArgumentException("Too short");
			}
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, keys.key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
			byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			drop(name, e);
			return null;
		} catch (GeneralSecurityException e) {
			if (keys.isGone(e)) {
				drop(name, e);
			} else {
				Logs.w(TAG, "Could not read secret " + name, e);
			}
			return null;
		} catch (IOException | RuntimeException e) {
			Logs.w(TAG, "Could not read secret " + name, e);
			return null;
		}
	}

	public synchronized boolean put(@NonNull String name, @Nullable String value) {
		if (value == null) {
			store.remove(name);
			return true;
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, keys.key());
			byte[] iv = cipher.getIV();
			byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			byte[] all = new byte[iv.length + sealed.length];
			System.arraycopy(iv, 0, all, 0, iv.length);
			System.arraycopy(sealed, 0, all, iv.length, sealed.length);
			store.putString(name, Base64.getEncoder().encodeToString(all));
			return true;
		} catch (GeneralSecurityException | IOException | RuntimeException e) {
			Logs.w(TAG, "Could not encrypt " + name, e);
			return false;
		}
	}

	private void drop(String name, Exception e) {
		Logs.w(TAG, "Dropping unreadable secret " + name, e);
		store.remove(name);
	}
}
