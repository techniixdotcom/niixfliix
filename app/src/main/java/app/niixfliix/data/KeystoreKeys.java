package app.niixfliix.data;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class KeystoreKeys implements SecretStore.KeySource {

	private static final String KEYSTORE = "AndroidKeyStore";
	private static final String ALIAS = "niixfliix_secrets";

	@NonNull
	@Override
	public SecretKey key() throws GeneralSecurityException, IOException {
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

	@Override
	public boolean isGone(@NonNull GeneralSecurityException e) {
		return e instanceof KeyPermanentlyInvalidatedException || SecretStore.KeySource.super.isGone(e);
	}
}
