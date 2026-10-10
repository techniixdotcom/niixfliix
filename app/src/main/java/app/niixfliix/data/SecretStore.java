package app.niixfliix.data;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import app.niixfliix.util.Logs;

/** AES-GCM with the key in the Android Keystore. Anything that might hold a debrid key goes here. */
public final class SecretStore {

	private static final String TAG = "SecretStore";
	private static final String KEYSTORE = "AndroidKeyStore";
	private static final String ALIAS = "niixfliix_secrets";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final KeyValueStore store;

	public SecretStore(@NonNull KeyValueStore store) {
		this.store = store;
	}

	@Nullable
	public synchronized String get(@NonNull String name) {
		String stored = store.getString(name, null);
		if (stored == null) {
			return null;
		}
		try {
			byte[] all = Base64.decode(stored, Base64.NO_WRAP);
			if (all.length <= IV_BYTES) {
				throw new IllegalArgumentException("Too short");
			}
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
			byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		} catch (AEADBadTagException | KeyPermanentlyInvalidatedException | UnrecoverableKeyException
				| IllegalArgumentException e) {
			// key is gone (e.g. data restored on another phone), this can never be decrypted again
			Logs.w(TAG, "Dropping unreadable secret " + name, e);
			store.remove(name);
			return null;
		} catch (GeneralSecurityException | IOException | RuntimeException e) {
			// often a transient ProviderException, keep the value
			Logs.w(TAG, "Could not read secret " + name, e);
			return null;
		}
	}

	/** false if encryption failed, nothing gets written then */
	public synchronized boolean put(@NonNull String name, @Nullable String value) {
		if (value == null) {
			store.remove(name);
			return true;
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key());
			byte[] iv = cipher.getIV();
			byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			byte[] all = new byte[iv.length + sealed.length];
			System.arraycopy(iv, 0, all, 0, iv.length);
			System.arraycopy(sealed, 0, all, iv.length, sealed.length);
			store.putString(name, Base64.encodeToString(all, Base64.NO_WRAP));
			return true;
		} catch (GeneralSecurityException | IOException | RuntimeException e) {
			Logs.w(TAG, "Could not encrypt " + name, e);
			return false;
		}
	}

	private static SecretKey key() throws GeneralSecurityException, IOException {
		KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
		keyStore.load(null);
		Key existing = keyStore.getKey(ALIAS, null);
		if (existing instanceof SecretKey) {
			return (SecretKey) existing;
		}
		KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
		generator.init(new KeyGenParameterSpec.Builder(ALIAS,
				KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build());
		return generator.generateKey();
	}
}
