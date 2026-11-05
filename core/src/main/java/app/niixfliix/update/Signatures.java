package app.niixfliix.update;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ed25519 signatures on Linux releases, and handing the signing over to a new key.
 * <p>
 * Each Linux release has a .sig next to its bundle: a 64-byte Ed25519 signature, made with the
 * Linux signing key (BUILD.sh keeps it in ~/.keys/linux), of the bundle's SHA-256 (see
 * {@link #releaseMessage}). Signing the hash rather than the file keeps memory flat: Ed25519 holds
 * the whole message in memory, and a bundle is a few hundred MB. The app trusts the keys built
 * into it (linux/update-key.pub) and nothing else.
 * <p>
 * When the key changes, the old key signs one line approving the new one, and that line goes
 * with every release from then on (the handover file). An installed app that only knows the old
 * key follows those lines from the keys it trusts to the key the bundle is signed with, the same
 * way Android follows an APK's key rotation. Only whoever holds a trusted key can add a key, and a
 * line naming a key nobody trusted yet proves nothing.
 * <p>
 * Plain Java (Ed25519 is in Java 15 and newer), so the build script checks each bundle with this
 * same code before it's released (scripts/Ed25519Tool.java).
 */
public final class Signatures {

	/** What the old key signs to approve a new one, followed by the new key in hex and a newline. */
	static final String HANDOVER_CONTEXT = "niix linux key handover v1\n";
	/** What a release's signature covers, followed by the file's SHA-256 in hex and a newline. */
	static final String RELEASE_CONTEXT = "niix linux release v1\n";
	private static final int KEY_BYTES = 32;
	private static final int SIGNATURE_BYTES = 64;
	private static final int MAX_HANDOVERS = 64;
	/** SubjectPublicKeyInfo of an Ed25519 key, up to the key itself (RFC 8410). */
	private static final byte[] X509_PREFIX = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

	private Signatures() {
	}

	/** Whether {@code text} is one public key: 64 hex characters. */
	public static boolean isKey(String text) {
		return text != null && text.matches("[0-9a-f]{64}");
	}

	/** The keys built into the app, from its comma-separated list. Anything malformed is left out. */
	public static List<String> keys(String commaSeparated) {
		List<String> out = new ArrayList<>();
		if (commaSeparated == null) {
			return out;
		}
		for (String key : commaSeparated.split(",")) {
			String k = key.trim().toLowerCase(Locale.ROOT);
			if (isKey(k) && !out.contains(k)) {
				out.add(k);
			}
		}
		return out;
	}

	/** The message a release's signature covers: its file's SHA-256 ({@code sha256}, hex). */
	public static byte[] releaseMessage(String sha256) {
		return (RELEASE_CONTEXT + sha256.toLowerCase(Locale.ROOT) + "\n").getBytes(StandardCharsets.US_ASCII);
	}

	/** Whether one of {@code keys} signed the release whose file has this SHA-256. */
	public static boolean verifyRelease(String sha256, byte[] signature, Collection<String> keys) {
		if (sha256 == null || !sha256.toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}")) {
			return false;
		}
		byte[] message = releaseMessage(sha256);
		for (String key : keys) {
			if (verify(key, message, signature)) {
				return true;
			}
		}
		return false;
	}

	/** The message the old key signs to approve {@code newKey}. */
	public static byte[] handoverMessage(String newKey) {
		return (HANDOVER_CONTEXT + newKey + "\n").getBytes(StandardCharsets.US_ASCII);
	}

	/**
	 * The keys to accept: the trusted ones, plus every key a handover line approves with a key
	 * already accepted. Lines are "new-key old-key signature", in hex; anything else is ignored.
	 */
	public static Set<String> trusted(Collection<String> pinned, String handovers) {
		Set<String> keys = new LinkedHashSet<>();
		for (String key : pinned) {
			if (isKey(key)) {
				keys.add(key);
			}
		}
		if (handovers == null || handovers.isEmpty()) {
			return keys;
		}
		List<String[]> lines = new ArrayList<>();
		for (String line : handovers.split("\n")) {
			String[] parts = line.trim().toLowerCase(Locale.ROOT).split("\\s+");
			if (parts.length == 3 && isKey(parts[0]) && isKey(parts[1]) && parts[2].matches("[0-9a-f]{128}")) {
				lines.add(parts);
			}
			if (lines.size() >= MAX_HANDOVERS) {
				break;
			}
		}
		boolean added = true;
		while (added) {
			added = false;
			for (String[] line : lines) {
				if (!keys.contains(line[0]) && keys.contains(line[1])
						&& verify(line[1], handoverMessage(line[0]), unhex(line[2]))) {
					keys.add(line[0]);
					added = true;
				}
			}
		}
		return keys;
	}

	/** Whether {@code signature} is {@code key}'s signature of {@code message}. */
	public static boolean verify(String key, byte[] message, byte[] signature) {
		try {
			Signature verifier = verifier(key);
			verifier.update(message);
			return signature != null && signature.length == SIGNATURE_BYTES && verifier.verify(signature);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			return false;
		}
	}

	private static Signature verifier(String key) throws GeneralSecurityException {
		byte[] raw = unhex(key);
		if (raw.length != KEY_BYTES) {
			throw new IllegalArgumentException("not an Ed25519 key");
		}
		byte[] encoded = new byte[X509_PREFIX.length + KEY_BYTES];
		System.arraycopy(X509_PREFIX, 0, encoded, 0, X509_PREFIX.length);
		System.arraycopy(raw, 0, encoded, X509_PREFIX.length, KEY_BYTES);
		PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
		Signature verifier = Signature.getInstance("Ed25519");
		verifier.initVerify(publicKey);
		return verifier;
	}

	static byte[] unhex(String hex) {
		if (hex.length() % 2 != 0) {
			throw new IllegalArgumentException("odd length");
		}
		byte[] out = new byte[hex.length() / 2];
		for (int i = 0; i < out.length; i++) {
			int hi = Character.digit(hex.charAt(2 * i), 16);
			int lo = Character.digit(hex.charAt(2 * i + 1), 16);
			if (hi < 0 || lo < 0) {
				throw new IllegalArgumentException("not hex");
			}
			out[i] = (byte) ((hi << 4) | lo);
		}
		return out;
	}
}
