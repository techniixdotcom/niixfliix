#!/usr/bin/env bash
# One command from a clean machine to a signed APK in dist/.
#
#   ./BUILD.sh              signed release APK
#   ./BUILD.sh --debug      debug APK instead
#   ./BUILD.sh --clean      wipe build output first
#   ./BUILD.sh --install    install on the connected device afterwards (adb)
#   ./BUILD.sh --verbose    show the full Gradle output, not just the log file
#
# JDK + SDK go in ~/.niixfliix. Back up ~/.niixfliix/keys, updates need the same key.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOME_DIR="${NIIXFLIIX_HOME:-$HOME/.niixfliix}"
JDK_DIR="$HOME_DIR/jdk-17"
SDK_DIR="$HOME_DIR/android-sdk"
KEY_DIR="$HOME_DIR/keys"
LOG="$ROOT/build.log"
COMPILE_SDK=36
BUILD_TOOLS=36.0.0

CLEAN=0
DEBUG=0
INSTALL=0
VERBOSE=0
for arg in "$@"; do
	case "$arg" in
		--clean) CLEAN=1 ;;
		--debug) DEBUG=1 ;;
		--install) INSTALL=1 ;;
		--verbose) VERBOSE=1 ;;
		-h|--help) sed -n '2,10p' "$0"; exit 0 ;;
		*) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
	esac
done

: > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }
fail() { say "ERROR: $*"; say "See $LOG for details."; exit 1; }
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

# checksums come from Adoptium's API / Google's repo xml
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

ensure_key() {
	local keystore="$KEY_DIR/$SLUG-release.jks"
	local env="$KEY_DIR/signing.env"
	mkdir -p "$KEY_DIR"
	chmod 700 "$KEY_DIR"
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
	# shellcheck disable=SC1090
	. "$env"
	export NIIXFLIIX_KEYSTORE="$keystore"
	export NIIXFLIIX_KEYSTORE_PASSWORD NIIXFLIIX_KEY_ALIAS
}

mkdir -p "$HOME_DIR/tmp"
chmod 700 "$HOME_DIR"
install_jdk
install_sdk

export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$SDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$ROOT"

if [ "$CLEAN" = 1 ]; then
	say "Cleaning"
	run ./gradlew --no-daemon clean || fail "clean failed"
	rm -rf "$ROOT/dist"
fi

mkdir -p "$ROOT/dist"
if [ "$DEBUG" = 1 ]; then
	say "Building debug APK"
	run ./gradlew --no-daemon testDebugUnitTest assembleDebug || fail "debug build failed"
	OUT="$ROOT/dist/$SLUG$VERSION-debug.apk"
	cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$OUT"
else
	ensure_key
	say "Building signed release APK"
	run ./gradlew --no-daemon testDebugUnitTest assembleRelease || fail "release build failed"
	OUT="$ROOT/dist/$SLUG$VERSION.apk"
	cp "$ROOT/app/build/outputs/apk/release/app-release.apk" "$OUT"
	run "$SDK_DIR/build-tools/$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" || fail "the APK signature does not verify"
fi

# the in-app updater checks this file
(cd "$ROOT/dist" && printf '%s  %s\n' "$(sha256_of "$(basename "$OUT")")" "$(basename "$OUT")" > "$(basename "$OUT").sha256")
say "Done: $OUT"
say "      $OUT.sha256"

if [ "$INSTALL" = 1 ]; then
	say "Installing on the connected device"
	run "$SDK_DIR/platform-tools/adb" install -r "$OUT" || fail "adb install failed"
fi
