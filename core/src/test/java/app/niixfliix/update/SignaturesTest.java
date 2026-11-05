package app.niixfliix.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SignaturesTest {

	private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	private static final class Key {
		final PrivateKey secret;
		final String hex;

		Key() throws Exception {
			KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
			secret = pair.getPrivate();
			byte[] encoded = pair.getPublic().getEncoded();
			hex = Releases.hex(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
		}

		byte[] sign(byte[] message) throws Exception {
			Signature signer = Signature.getInstance("Ed25519");
			signer.initSign(secret);
			signer.update(message);
			return signer.sign();
		}

		String handover(Key next) throws Exception {
			return next.hex + " " + hex + " " + Releases.hex(sign(Signatures.handoverMessage(next.hex))) + "\n";
		}
	}

	@Test
	public void acceptsOnlyTheTrustedKeysSignature() throws Exception {
		Key trusted = new Key();
		Key other = new Key();
		List<String> keys = Collections.singletonList(trusted.hex);
		byte[] signature = trusted.sign(Signatures.releaseMessage(SHA));
		assertTrue(Signatures.verifyRelease(SHA, signature, keys));
		assertTrue(Signatures.verifyRelease(SHA.toUpperCase(), signature, keys));
		assertFalse(Signatures.verifyRelease(SHA.replace('0', '1'), signature, keys));
		assertFalse(Signatures.verifyRelease(SHA, other.sign(Signatures.releaseMessage(SHA)), keys));
		assertFalse(Signatures.verifyRelease(SHA, new byte[64], keys));
		assertFalse(Signatures.verifyRelease(SHA, new byte[3], keys));
		assertFalse(Signatures.verifyRelease("nothex", signature, keys));
	}

	@Test
	public void followsHandoversFromATrustedKeyOnly() throws Exception {
		Key first = new Key();
		Key second = new Key();
		Key third = new Key();
		Key stranger = new Key();
		List<String> pinned = Collections.singletonList(first.hex);
		String chain = first.handover(second) + second.handover(third);

		Set<String> keys = Signatures.trusted(pinned, chain);
		assertTrue(keys.contains(second.hex));
		assertTrue(keys.contains(third.hex));
		assertTrue(Signatures.verifyRelease(SHA, third.sign(Signatures.releaseMessage(SHA)), keys));

		// In any order
		assertEquals(keys, Signatures.trusted(pinned, second.handover(third) + first.handover(second)));
		// A stranger's approval adds nothing, and a handover never goes backwards
		assertEquals(1, Signatures.trusted(pinned, stranger.handover(third)).size());
		assertFalse(Signatures.trusted(Collections.singletonList(third.hex), chain).contains(first.hex));
		// A line whose signature doesn't match the key it names
		String forged = third.hex + " " + first.hex + " " + Releases.hex(stranger.sign(Signatures.handoverMessage(third.hex)));
		assertFalse(Signatures.trusted(pinned, forged).contains(third.hex));
		assertEquals(1, Signatures.trusted(pinned, "garbage\n\n" + first.hex).size());
	}

	@Test
	public void readsTheBuiltInKeys() throws Exception {
		Key one = new Key();
		Key two = new Key();
		assertEquals(Arrays.asList(one.hex, two.hex), Signatures.keys(one.hex + "," + two.hex + ",," + one.hex + ",x"));
		assertTrue(Signatures.keys("").isEmpty());
	}
}
