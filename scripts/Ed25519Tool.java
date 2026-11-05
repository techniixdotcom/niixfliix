import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Linux signing key's tool, run by scripts/linux.sh with the JDK it installs:
 *
 *   genkey PEM PUB                       new Ed25519 key: private key to PEM (PKCS#8), public to PUB (hex)
 *   check PEM PUB                        exit 0 if the two belong together
 *   sign PEM FILE SIG                    signs FILE's SHA-256 the way the app checks it, 64 bytes to SIG
 *   handover OLD_PEM OLD_PUB NEW_PUB     prints the line in which the old key approves the new one
 *   reaches PINNED HANDOVERS KEY         exit 0 if KEY is trusted, or approved by a trusted key
 *   verify FILE SIG PINNED HANDOVERS     the app's own check (app.niixfliix.update.Signatures, which
 *                                        has to be on the class path), exit 0 if it passes
 *
 * PINNED holds public keys, one per line; HANDOVERS is the handover file, or "-" for none.
 * The messages are the app's: see core/src/main/java/app/niixfliix/update/Signatures.java.
 */
public final class Ed25519Tool {
	private static final String HANDOVER_CONTEXT = "niix linux key handover v1\n";
	private static final String RELEASE_CONTEXT = "niix linux release v1\n";
	private static final byte[] X509_PREFIX = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			usage();
		}
		switch (args[0]) {
			case "genkey" -> {
				need(args, 3);
				genkey(Path.of(args[1]), Path.of(args[2]));
			}
			case "check" -> {
				need(args, 3);
				System.exit(matches(loadPrivate(Path.of(args[1])), readKey(Path.of(args[2]))) ? 0 : 1);
			}
			case "sign" -> {
				need(args, 4);
				byte[] message = releaseMessage(sha256(Path.of(args[2])));
				Files.write(Path.of(args[3]), sign(loadPrivate(Path.of(args[1])), message));
			}
			case "handover" -> {
				need(args, 4);
				PrivateKey old = loadPrivate(Path.of(args[1]));
				String oldKey = readKey(Path.of(args[2]));
				String newKey = readKey(Path.of(args[3]));
				if (!matches(old, oldKey)) {
					fail("the old private and public key don't belong together");
				}
				byte[] signature = sign(old, (HANDOVER_CONTEXT + newKey + "\n").getBytes(StandardCharsets.US_ASCII));
				System.out.println(newKey + " " + oldKey + " " + hex(signature));
			}
			case "reaches" -> {
				need(args, 4);
				Set<String> keys = trusted(readKeys(Path.of(args[1])), handovers(args[2]));
				System.exit(keys.contains(args[3].trim().toLowerCase(Locale.ROOT)) ? 0 : 1);
			}
			case "verify" -> {
				need(args, 5);
				System.exit(appVerifies(Path.of(args[1]), Path.of(args[2]), readKeys(Path.of(args[3])),
						handovers(args[4])) ? 0 : 1);
			}
			default -> usage();
		}
	}

	private static void usage() {
		System.err.println("usage: genkey PEM PUB | check PEM PUB | sign PEM FILE SIG | handover OLD_PEM OLD_PUB NEW_PUB"
				+ " | reaches PINNED HANDOVERS KEY | verify FILE SIG PINNED HANDOVERS");
		System.exit(2);
	}

	private static void need(String[] args, int count) {
		if (args.length != count) {
			usage();
		}
	}

	private static void fail(String message) {
		System.err.println("Ed25519Tool: " + message);
		System.exit(1);
	}

	private static void genkey(Path pem, Path pub) throws Exception {
		if (Files.exists(pem) || Files.exists(pub)) {
			fail(pem + " or " + pub + " exists already; a key is never overwritten");
		}
		KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
				.encodeToString(pair.getPrivate().getEncoded());
		Files.writeString(pem, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
		Files.writeString(pub, publicHex(pair.getPublic()) + "\n");
	}

	private static String publicHex(PublicKey key) {
		byte[] encoded = key.getEncoded();
		byte[] raw = new byte[32];
		System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
		return hex(raw);
	}

	private static PrivateKey loadPrivate(Path pem) throws Exception {
		String text = Files.readString(pem);
		int start = text.indexOf("-----BEGIN PRIVATE KEY-----");
		int end = text.indexOf("-----END PRIVATE KEY-----");
		if (start < 0 || end < start) {
			fail(pem + " is not a PEM private key");
		}
		String body = text.substring(start + "-----BEGIN PRIVATE KEY-----".length(), end).replaceAll("\\s", "");
		return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
	}

	private static PublicKey publicKey(String hex) throws Exception {
		byte[] raw = unhex(hex);
		byte[] encoded = new byte[X509_PREFIX.length + raw.length];
		System.arraycopy(X509_PREFIX, 0, encoded, 0, X509_PREFIX.length);
		System.arraycopy(raw, 0, encoded, X509_PREFIX.length, raw.length);
		return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
	}

	private static byte[] sign(PrivateKey key, byte[] message) throws Exception {
		Signature signer = Signature.getInstance("Ed25519");
		signer.initSign(key);
		signer.update(message);
		return signer.sign();
	}

	private static boolean verify(String key, byte[] message, byte[] signature) {
		try {
			Signature verifier = Signature.getInstance("Ed25519");
			verifier.initVerify(publicKey(key));
			verifier.update(message);
			return signature.length == 64 && verifier.verify(signature);
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean matches(PrivateKey key, String publicKey) throws Exception {
		byte[] probe = "niix key check\n".getBytes(StandardCharsets.US_ASCII);
		return isKey(publicKey) && verify(publicKey, probe, sign(key, probe));
	}

	private static byte[] releaseMessage(String sha256) {
		return (RELEASE_CONTEXT + sha256 + "\n").getBytes(StandardCharsets.US_ASCII);
	}

	private static String sha256(Path file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[64 * 1024];
			int n;
			while ((n = in.read(buffer)) != -1) {
				digest.update(buffer, 0, n);
			}
		}
		return hex(digest.digest());
	}

	private static boolean isKey(String text) {
		return text != null && text.matches("[0-9a-f]{64}");
	}

	private static String readKey(Path file) throws Exception {
		List<String> keys = readKeys(file);
		if (keys.size() != 1) {
			fail(file + " should hold exactly one public key");
		}
		return keys.get(0);
	}

	private static List<String> readKeys(Path file) throws Exception {
		List<String> keys = new ArrayList<>();
		if (!Files.exists(file)) {
			return keys;
		}
		for (String line : Files.readAllLines(file)) {
			String key = line.trim().toLowerCase(Locale.ROOT);
			if (key.isEmpty() || key.startsWith("#")) {
				continue;
			}
			if (!isKey(key)) {
				fail(file + " holds something that isn't a public key: " + key);
			}
			keys.add(key);
		}
		return keys;
	}

	private static String handovers(String path) throws Exception {
		return path.equals("-") || !Files.exists(Path.of(path)) ? "" : Files.readString(Path.of(path));
	}

	/** The same rules as Signatures.trusted, for checks made before the app is built. */
	private static Set<String> trusted(Collection<String> pinned, String handovers) {
		Set<String> keys = new LinkedHashSet<>(pinned);
		List<String[]> lines = new ArrayList<>();
		for (String line : handovers.split("\n")) {
			String[] parts = line.trim().toLowerCase(Locale.ROOT).split("\\s+");
			if (parts.length == 3 && isKey(parts[0]) && isKey(parts[1]) && parts[2].matches("[0-9a-f]{128}")) {
				lines.add(parts);
			}
		}
		boolean added = true;
		while (added) {
			added = false;
			for (String[] line : lines) {
				if (!keys.contains(line[0]) && keys.contains(line[1]) && verify(line[1],
						(HANDOVER_CONTEXT + line[0] + "\n").getBytes(StandardCharsets.US_ASCII), unhex(line[2]))) {
					keys.add(line[0]);
					added = true;
				}
			}
		}
		return keys;
	}

	/** Runs the app's own check on a built release, so what is published is what the app accepts. */
	private static boolean appVerifies(Path file, Path sig, List<String> pinned, String handovers) throws Exception {
		Class<?> signatures;
		try {
			signatures = Class.forName("app.niixfliix.update.Signatures");
		} catch (ClassNotFoundException e) {
			fail("the app's Signatures class isn't on the class path (build the app first)");
			return false;
		}
		Method trusted = signatures.getMethod("trusted", Collection.class, String.class);
		Method verify = signatures.getMethod("verifyRelease", String.class, byte[].class, Collection.class);
		Object keys = trusted.invoke(null, pinned, handovers);
		return (Boolean) verify.invoke(null, sha256(file), Files.readAllBytes(sig), keys);
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
		}
		return out.toString();
	}

	private static byte[] unhex(String hex) {
		byte[] out = new byte[hex.length() / 2];
		for (int i = 0; i < out.length; i++) {
			out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
		}
		return out;
	}
}
