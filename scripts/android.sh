#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOME_DIR="${NIIXFLIIX_HOME:-$HOME/.niixfliix}"
JDK_DIR="$HOME_DIR/jdk-17"
SDK_DIR="$HOME_DIR/android-sdk"
# Every signing key, for all apps, lives in ~/.keys (scripts/android-keys.sh); this app's own one
# in ~/.keys/<appName>. Versions up to 1.17.0 kept it in ~/.niixfliix/keys, moved on the first run.
KEYS_HOME="${KEYS_HOME:-$HOME/.keys}"
# shellcheck source=scripts/android-keys.sh
source "$ROOT/scripts/android-keys.sh"
LEGACY_KEY_DIR="$HOME_DIR/keys"
# The first version whose updater accepts a change of key
ROTATION_AWARE_VERSION=1.17.1
OLD_PRIVATE="$HOME_DIR/private.properties"
OLD_PERSONAL_DIR="$HOME_DIR/personal"
LOG="$ROOT/build.log"
COMPILE_SDK=37.0
BUILD_TOOLS=36.0.0

CLEAN=0
DEBUG=0
INSTALL=0
VERBOSE=0
# What to do instead of building, from ../BUILD.sh
ACTION=""
GRADLE_EXTRA=()
for arg in "$@"; do
	case "$arg" in
		--clean) CLEAN=1 ;;
		--debug) DEBUG=1 ;;
		--install) INSTALL=1 ;;
		--verbose) VERBOSE=1 ;;
		--write-dependency-hashes) GRADLE_EXTRA+=(--write-verification-metadata sha256) ;;
		--use-shared-key) ACTION=switch ;;
		--create-keys) ACTION=create-keys ;;
		--show-keys) ACTION=show-keys ;;
		--test) ACTION="test" ;;
		--install-only) ACTION=install-only ;;
		--verify-dist) ACTION=verify-dist ;;
		*) echo "Unknown option: $arg (see ../BUILD.sh --help)" >&2; exit 2 ;;
	esac
done

: > "$LOG"
# The same look as the BUILD.sh of every app: "==>" for steps, "error:" when it stops.
say() {
	case "$*" in
		' '*) printf '%s\n' "$*" | tee -a "$LOG" ;;
		*) printf '==> %s\n' "$*" | tee -a "$LOG" ;;
	esac
}
warn() { printf 'warning: %s\n' "$*" | tee -a "$LOG" >&2; }
fail() { printf 'error: %s\n' "$*" | tee -a "$LOG" >&2; printf '       See %s for details.\n' "$LOG" >&2; exit 1; }
run() {
	if [ "$VERBOSE" = 1 ]; then
		"$@" 2>&1 | tee -a "$LOG"
		return "${PIPESTATUS[0]}"
	fi
	"$@" >> "$LOG" 2>&1
}
need() { command -v "$1" > /dev/null 2>&1 || fail "'$1' is needed but not installed."; }

need curl
need unzip
need tar
need python3

prop() { sed -n "s/^$1=//p" "$ROOT/gradle.properties" | tr -d '\r'; }
APP_NAME="$(prop appName)"
VERSION="$(prop versionName)"
SLUG="$(printf '%s' "$APP_NAME" | tr '[:upper:]' '[:lower:]')"
GITHUB_REPO="$(prop githubRepo)"
KEY_DIR="$KEYS_HOME/$SLUG"
LINEAGE="$KEY_DIR/lineage.bin"
MIN_SDK="$(sed -n 's/^[[:space:]]*minSdk[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$ROOT/app/build.gradle.kts" | head -n1)"
case "$MIN_SDK" in
	''|*[!0-9]*) fail "Could not read minSdk from app/build.gradle.kts" ;;
esac

case "$(uname -s)" in
	Linux) OS=linux; SDK_OS=linux ;;
	Darwin) OS=mac; SDK_OS=macosx ;;
	*) fail "Only Linux and macOS are supported." ;;
esac
case "$(uname -m)" in
	x86_64|amd64) ARCH=x64 ;;
	arm64|aarch64) ARCH=aarch64 ;;
	*) fail "Unsupported CPU: $(uname -m)" ;;
esac

sha256_of() {
	if command -v sha256sum > /dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}
sha1_of() {
	if command -v sha1sum > /dev/null 2>&1; then sha1sum "$1" | cut -d' ' -f1; else shasum -a 1 "$1" | cut -d' ' -f1; fi
}

install_jdk() {
	if [ -x "$JDK_DIR/bin/java" ]; then
		return
	fi
	say "Installing JDK 17 into $JDK_DIR"
	local meta="$HOME_DIR/tmp/jdk.json"
	run curl -fsSL -o "$meta" \
		"https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=$ARCH&image_type=jdk&os=$OS&vendor=eclipse" \
		|| fail "Could not reach api.adoptium.net"
	local link sum
	link="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[0]["binary"]["package"]["link"])' "$meta")"
	sum="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[0]["binary"]["package"]["checksum"])' "$meta")"
	local archive="$HOME_DIR/tmp/jdk.tar.gz"
	run curl -fsSL -o "$archive" "$link" || fail "JDK download failed"
	[ "$(sha256_of "$archive")" = "$sum" ] || fail "JDK checksum mismatch, refusing to use it"
	rm -rf "$JDK_DIR" "$HOME_DIR/tmp/jdk"
	mkdir -p "$HOME_DIR/tmp/jdk"
	tar -xzf "$archive" -C "$HOME_DIR/tmp/jdk"
	local home
	home="$(find "$HOME_DIR/tmp/jdk" -maxdepth 4 -type f -name java -path '*/bin/java' | head -n1 | xargs dirname | xargs dirname)"
	mv "$home" "$JDK_DIR"
	rm -rf "$HOME_DIR/tmp/jdk" "$archive"
}

install_sdk() {
	local sdkmanager="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
	if [ ! -x "$sdkmanager" ]; then
		say "Installing Android command-line tools into $SDK_DIR"
		local repo="$HOME_DIR/tmp/repository.xml"
		run curl -fsSL -o "$repo" "https://dl.google.com/android/repository/repository2-3.xml" \
			|| fail "Could not reach dl.google.com"
		local found
		found="$(python3 - "$repo" "$SDK_OS" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for pkg in root.iter():
    if pkg.tag.endswith('remotePackage') and pkg.get('path') == 'cmdline-tools;latest':
        for archive in pkg.iter():
            if not archive.tag.endswith('archive'):
                continue
            host = next((e.text for e in archive.iter() if e.tag.endswith('host-os')), None)
            if host != sys.argv[2]:
                continue
            sha = next(e.text for e in archive.iter() if e.tag.endswith('checksum'))
            url = next(e.text for e in archive.iter() if e.tag.endswith('url'))
            print(url, sha)
            sys.exit(0)
sys.exit(1)
PY
)" || fail "Could not find the command-line tools in Google's repository list"
		local url sha
		url="$(echo "$found" | cut -d' ' -f1)"
		sha="$(echo "$found" | cut -d' ' -f2)"
		local zip="$HOME_DIR/tmp/cmdline-tools.zip"
		run curl -fsSL -o "$zip" "https://dl.google.com/android/repository/$url" || fail "SDK tools download failed"
		[ "$(sha1_of "$zip")" = "$sha" ] || fail "SDK tools checksum mismatch, refusing to use them"
		rm -rf "$SDK_DIR/cmdline-tools" "$HOME_DIR/tmp/cmdline"
		mkdir -p "$SDK_DIR/cmdline-tools" "$HOME_DIR/tmp/cmdline"
		unzip -q "$zip" -d "$HOME_DIR/tmp/cmdline"
		mv "$HOME_DIR/tmp/cmdline/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
		rm -rf "$HOME_DIR/tmp/cmdline" "$zip"
	fi
	if [ ! -d "$SDK_DIR/platforms/android-$COMPILE_SDK" ] || [ ! -d "$SDK_DIR/build-tools/$BUILD_TOOLS" ]; then
		say "Installing Android platform $COMPILE_SDK and build tools $BUILD_TOOLS"
		yes 2> /dev/null | run "$sdkmanager" --sdk_root="$SDK_DIR" --licenses || true
		run "$sdkmanager" --sdk_root="$SDK_DIR" "platforms;android-$COMPILE_SDK" "build-tools;$BUILD_TOOLS" "platform-tools" \
			|| fail "sdkmanager failed"
	fi
}

# Moves the key from where versions up to 1.17.0 kept it, checking every copy.
migrate_key() {
	[ -f "$LEGACY_KEY_DIR/$SLUG-release.jks" ] || return 0
	if [ -f "$KEY_DIR/$SLUG-release.jks" ]; then
		# The key moved here already. One in the old folder was made after that, by an older copy of
		# the build scripts that didn't know about ~/.keys: it isn't the key the releases are signed
		# with, so it's set aside (not deleted) where nothing picks it up again.
		local aside
		aside="$LEGACY_KEY_DIR-unused-$(date +%Y%m%d-%H%M%S)"
		mv "$LEGACY_KEY_DIR" "$aside" || fail "Could not set aside $LEGACY_KEY_DIR"
		warn "$LEGACY_KEY_DIR held a different key than $KEY_DIR, probably made by an older copy of"
		warn "the build scripts. Your releases use the one in $KEY_DIR; the other was moved to $aside."
		return 0
	fi
	ak_move_dir "$LEGACY_KEY_DIR" "$KEY_DIR" || fail "Could not move the signing key from $LEGACY_KEY_DIR to $KEY_DIR (it's still there)"
	say "Moved the signing key from $LEGACY_KEY_DIR to $KEY_DIR"
}

ensure_key() {
	local keystore="$KEY_DIR/$SLUG-release.jks"
	local env="$KEY_DIR/signing.env"
	migrate_key
	mkdir -p "$KEY_DIR"
	chmod 700 "$KEYS_HOME" "$KEY_DIR"
	if [ ! -f "$keystore" ]; then
		say "Creating a release signing key in $KEY_DIR (back this folder up)"
		local password
		password="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
		run "$JDK_DIR/bin/keytool" -genkeypair -noprompt -keystore "$keystore" -storetype PKCS12 \
			-alias "$SLUG" -keyalg RSA -keysize 4096 -validity 10000 \
			-storepass "$password" -keypass "$password" -dname "CN=$APP_NAME" \
			|| fail "keytool failed"
		umask 077
		printf 'NIIXFLIIX_KEYSTORE_PASSWORD=%s\nNIIXFLIIX_KEY_ALIAS=%s\n' "$password" "$SLUG" > "$env"
		chmod 600 "$keystore" "$env"
	fi
	NIIXFLIIX_KEYSTORE_PASSWORD="$(sed -n 's/^NIIXFLIIX_KEYSTORE_PASSWORD=//p' "$env")"
	NIIXFLIIX_KEY_ALIAS="$(sed -n 's/^NIIXFLIIX_KEY_ALIAS=//p' "$env")"
	if [ -z "$NIIXFLIIX_KEYSTORE_PASSWORD" ] || [ -z "$NIIXFLIIX_KEY_ALIAS" ]; then
		fail "$env is incomplete"
	fi
	export NIIXFLIIX_KEYSTORE="$keystore"
	export NIIXFLIIX_KEYSTORE_PASSWORD NIIXFLIIX_KEY_ALIAS
	ak_secure || fail "Could not set up $KEYS_HOME"
	if [ -f "$LINEAGE" ] && ! ak_shared_exists; then
		fail "$APP_NAME uses the shared Android key, but it isn't in $AK_SHARED_DIR. Restore $KEYS_HOME from your backup."
	fi
}

# The newest release on GitHub, pre-releases included (what the app's updater looks at).
published_version() {
	command -v gh > /dev/null 2>&1 && gh auth status > /dev/null 2>&1 || return 0
	gh api "repos/$GITHUB_REPO/releases?per_page=30" --jq '.[] | select(.draft | not) | .tag_name' 2> /dev/null \
		| sed 's/^v//' | grep -E '^[0-9]+(\.[0-9]+)+$' | sort -V | tail -n1 || true
}

# Switches the app to the Android key all apps share, for good (see scripts/android-keys.sh).
switch_key() {
	ensure_key
	if [ -f "$LINEAGE" ]; then
		say "$APP_NAME already uses the shared Android key"
		return
	fi
	local published
	published="$(published_version)"
	if [ -z "$published" ]; then
		warn "Couldn't check GitHub (install gh and run gh auth login to have it checked)."
		warn "Only switch once a release from $ROTATION_AWARE_VERSION on, signed with the old key, is out:"
		warn "older versions' in-app updater refuses the new key (Android itself accepts it)."
	elif [ "$(printf '%s\n%s\n' "$published" "$ROTATION_AWARE_VERSION" | sort -V | head -n1)" != "$ROTATION_AWARE_VERSION" ]; then
		warn "The newest release on GitHub is $published. Its in-app updater refuses a new key, so"
		warn "people would have to download the next APK by hand. Better: first publish $ROTATION_AWARE_VERSION"
		warn "or newer with the old key, give people time to update, then switch."
	else
		say "The newest release on GitHub ($published) already accepts the switch"
	fi
	ak_switch "$APP_NAME" "$NIIXFLIIX_KEYSTORE" "$NIIXFLIIX_KEYSTORE_PASSWORD" "$NIIXFLIIX_KEYSTORE_PASSWORD" \
		"$NIIXFLIIX_KEY_ALIAS" "$LINEAGE" || fail "$APP_NAME was not switched"
	say "Back up $KEYS_HOME now: ./BUILD.sh --backup-keys"
}

mkdir -p "$HOME_DIR/tmp"
chmod 700 "$HOME_DIR"
install_jdk
install_sdk

export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$SDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
AK_KEYTOOL="$JDK_DIR/bin/keytool"
AK_JAVA_HOME="$JDK_DIR"
AK_APKSIGNER="$SDK_DIR/build-tools/$BUILD_TOOLS/apksigner"
cd "$ROOT"

APKSIGNER="$SDK_DIR/build-tools/$BUILD_TOOLS/apksigner"
ADB="$SDK_DIR/platform-tools/adb"

# The signing key's digest as apksigner shows it: the shared key once switched, the app's own before.
release_digest() {
	if [ -f "$LINEAGE" ]; then
		ak_shared_digest
	else
		ak_fingerprint "$NIIXFLIIX_KEYSTORE" "$NIIXFLIIX_KEYSTORE_PASSWORD" "$NIIXFLIIX_KEY_ALIAS"
	fi
}

show_keys() {
	say "Signing keys (all in $KEYS_HOME)"
	migrate_key
	local keystore="$KEY_DIR/$SLUG-release.jks" env="$KEY_DIR/signing.env"
	if [ -f "$keystore" ] && [ -f "$env" ]; then
		local password alias
		password="$(ak_value "$env" NIIXFLIIX_KEYSTORE_PASSWORD || true)"
		alias="$(ak_value "$env" NIIXFLIIX_KEY_ALIAS || true)"
		say "    $APP_NAME's own Android key: $(ak_fingerprint "$keystore" "$password" "$alias")"
		say "                                $keystore"
		if [ -f "$LINEAGE" ]; then
			say "    $APP_NAME uses the shared Android key (its own key approved it: $LINEAGE)."
		elif ak_shared_exists; then
			say "    $APP_NAME doesn't use the shared Android key yet (option 15 switches it)."
		fi
	else
		say "    No Android key yet. The first release build makes one (or option 13)."
	fi
	if ak_shared_exists; then
		say "    Shared Android key:         $(ak_shared_digest)"
		say "                                $AK_SHARED_KS"
	fi
}

# The APK in dist/: the release one unless only a debug one is there.
dist_apk() {
	if [ -f "$ROOT/dist/$SLUG$VERSION.apk" ]; then
		printf '%s' "$ROOT/dist/$SLUG$VERSION.apk"
	elif [ -f "$ROOT/dist/$SLUG$VERSION-debug.apk" ]; then
		printf '%s' "$ROOT/dist/$SLUG$VERSION-debug.apk"
	fi
}

# Installs the built APK and shows Android's own reason when it refuses.
install_only() {
	local apk output
	apk="$(dist_apk)"
	[ -n "$apk" ] || fail "No APK for version $VERSION in dist/. Build it first (option 1)."
	[ "$("$ADB" get-state 2> /dev/null | tr -d '\r')" = device ] \
		|| fail "No phone found. Turn on USB debugging, connect the phone, accept the prompt on it and try again."
	say "Installing $(basename "$apk") on the connected phone"
	output="$("$ADB" install -r "$apk" 2>&1 || true)"
	printf '%s\n' "$output" >> "$LOG"
	if printf '%s' "$output" | grep -q '^Success'; then
		say "Installed."
		return
	fi
	printf '%s\n' "$output" >&2
	case "$output" in
		*INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
			fail "The phone's $APP_NAME is signed with a different key. Uninstall it first (its data goes with it)." ;;
		*INSTALL_FAILED_VERSION_DOWNGRADE*)
			fail "The phone has a newer $APP_NAME than this APK. Set a higher version first (option 12)." ;;
	esac
	fail "Android refused the APK (its message is above)."
}

# Before publishing: the release APK in dist/ is signed with this app's key the way updates need.
verify_dist() {
	local apk="$ROOT/dist/$SLUG$VERSION.apk" signed expected
	[ -f "$apk" ] || fail "No release APK for version $VERSION in dist/. Build it first (option 1)."
	[ -f "$apk.sha256" ] || fail "$(basename "$apk").sha256 is missing. Build again (option 1)."
	ensure_key
	run "$APKSIGNER" verify "$apk" || fail "$(basename "$apk")'s signature doesn't verify"
	signed="$("$APKSIGNER" verify --print-certs "$apk" 2> /dev/null \
		| awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2; exit}' | tr 'A-F' 'a-f')"
	expected="$(release_digest)"
	[ -n "$signed" ] && [ "$signed" = "$expected" ] \
		|| fail "$(basename "$apk") isn't signed with your signing key, so the app would refuse it as an update."
	if [ -f "$LINEAGE" ]; then
		run ak_check "$apk" "$(ak_fingerprint "$NIIXFLIIX_KEYSTORE" "$NIIXFLIIX_KEYSTORE_PASSWORD" "$NIIXFLIIX_KEY_ALIAS")" \
			"$MIN_SDK" || fail "$(basename "$apk") isn't signed the way the switch to the shared key needs"
	fi
	[ "$(sha256_of "$apk")" = "$(cut -d' ' -f1 < "$apk.sha256")" ] || fail "$(basename "$apk") doesn't match its .sha256. Build again."
	say "$(basename "$apk") is signed with your key"
}

case "$ACTION" in
	switch) switch_key; exit 0 ;;
	create-keys) ensure_key; say "$APP_NAME's Android key is in $KEY_DIR. Back up $KEYS_HOME (option 17)."; exit 0 ;;
	show-keys) show_keys; exit 0 ;;
	install-only) install_only; exit 0 ;;
	verify-dist) verify_dist; exit 0 ;;
	test)
		say "Running the tests"
		run ./gradlew --no-daemon ${GRADLE_EXTRA[@]+"${GRADLE_EXTRA[@]}"} :core:test testDebugUnitTest || fail "a test failed"
		say "All tests passed"
		exit 0
		;;
esac

if [ "$CLEAN" = 1 ]; then
	say "Cleaning"
	run ./gradlew --no-daemon clean || fail "clean failed"
fi

mkdir -p "$ROOT/dist"
if [ "$DEBUG" = 1 ]; then
	say "Building debug APK"
	run ./gradlew --no-daemon ${GRADLE_EXTRA[@]+"${GRADLE_EXTRA[@]}"} :core:test testDebugUnitTest assembleDebug || fail "debug build failed"
	OUT="$ROOT/dist/$SLUG$VERSION-debug.apk"
	cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$OUT"
else
	ensure_key
	say "Building signed release APK"
	run ./gradlew --no-daemon ${GRADLE_EXTRA[@]+"${GRADLE_EXTRA[@]}"} :core:test testDebugUnitTest assembleRelease || fail "release build failed"
	OUT="$ROOT/dist/$SLUG$VERSION.apk"
	cp "$ROOT/app/build/outputs/apk/release/app-release.apk" "$OUT"
	if [ -f "$LINEAGE" ]; then
		# Switched to the shared key: signed with both, carrying the old key's approval of the new one
		run ak_sign "$OUT" "$NIIXFLIIX_KEYSTORE" "$NIIXFLIIX_KEYSTORE_PASSWORD" "$NIIXFLIIX_KEYSTORE_PASSWORD" \
			"$NIIXFLIIX_KEY_ALIAS" "$LINEAGE" || fail "could not sign the APK with the shared Android key"
		run ak_check "$OUT" "$(ak_fingerprint "$NIIXFLIIX_KEYSTORE" "$NIIXFLIIX_KEYSTORE_PASSWORD" "$NIIXFLIIX_KEY_ALIAS")" \
			"$MIN_SDK" || fail "the APK isn't signed the way the switch to the shared key needs"
		say "Signed with the shared Android key, and $APP_NAME's own key for Android 8"
	fi
	run "$APKSIGNER" verify --print-certs "$OUT" || fail "the APK signature does not verify"
	MAPPING="$ROOT/app/build/outputs/mapping/release/mapping.txt"
	if [ -f "$MAPPING" ]; then
		cp "$MAPPING" "$ROOT/dist/$SLUG$VERSION-mapping.txt"
		say "      $ROOT/dist/$SLUG$VERSION-mapping.txt (decodes crash logs from this build)"
	fi
fi

(cd "$ROOT/dist" && printf '%s  %s\n' "$(sha256_of "$(basename "$OUT")")" "$(basename "$OUT")" > "$(basename "$OUT").sha256")

while IFS= read -r old; do
	rm -f -- "$old"
	say "Removed old $(basename "$old")"
done < <(find "$ROOT/dist" -maxdepth 1 -type f \( -name "$SLUG*.apk" -o -name "$SLUG*.apk.sha256" \) \
	! -name "$(basename "$OUT")" ! -name "$(basename "$OUT").sha256")

say "Done: $OUT"
say "      $OUT.sha256"
if [ -e "$OLD_PRIVATE" ] || [ -e "$OLD_PERSONAL_DIR" ]; then
	say "Note: $OLD_PRIVATE and $OLD_PERSONAL_DIR are no longer used (the personal build is gone)."
	say "      They hold your old sign-in details: delete them, and uninstall \"$SLUG (personal)\" from your phone."
fi

if [ "$INSTALL" = 1 ]; then
	say "Installing $(basename "$OUT") on the connected device"
	run "$ADB" install -r "$OUT" || fail "adb install failed"
fi
