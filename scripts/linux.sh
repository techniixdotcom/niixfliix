#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOME_DIR="${NIIXFLIIX_HOME:-$HOME/.niixfliix}"
JDK_DIR="$HOME_DIR/jdk-25"
LOG="$ROOT/build-linux.log"
GNOME=51
APP_ID=app.niixfliix.Niixfliix
FLATHUB=https://dl.flathub.org/repo/flathub.flatpakrepo

CLEAN=0
INSTALL=0
VERBOSE=0
# What to do instead of building, from ../BUILD.sh
ACTION=""
GRADLE_EXTRA=()
for arg in "$@"; do
	case "$arg" in
		--clean) CLEAN=1 ;;
		--install) INSTALL=1 ;;
		--verbose) VERBOSE=1 ;;
		--write-dependency-hashes) GRADLE_EXTRA+=(--write-verification-metadata sha256) ;;
		--new-key) ACTION=new-key ;;
		--create-key) ACTION=create-key ;;
		--show-key) ACTION=show-key ;;
		--test) ACTION="test" ;;
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
fail() { printf 'error: %s\n' "$*" | tee -a "$LOG" >&2; printf '       See %s for details.\n' "$LOG" >&2; exit 1; }
run() {
	if [ "$VERBOSE" = 1 ]; then
		"$@" 2>&1 | tee -a "$LOG"
		return "${PIPESTATUS[0]}"
	fi
	"$@" >> "$LOG" 2>&1
}
need() { command -v "$1" > /dev/null 2>&1 || fail "'$1' is needed but not installed. $2"; }

[ "$(uname -s)" = Linux ] || fail "The Linux app is built on Linux."
case "$(uname -m)" in
	x86_64|amd64) ;;
	*) fail "Only x86_64 is supported: libtorrent's Linux library is built for it only." ;;
esac

PACKAGES="Arch: sudo pacman -S flatpak · Debian/Ubuntu: sudo apt install flatpak · Fedora: sudo dnf install flatpak"
need curl ""
need tar ""
need python3 ""
need sha256sum ""
need flatpak "$PACKAGES"

prop() { sed -n "s/^$1=//p" "$ROOT/gradle.properties" | tr -d '\r'; }
APP_NAME="$(prop appName)"
VERSION="$(prop versionName)"
GITHUB_REPO="$(prop githubRepo)"
SLUG="$(printf '%s' "$APP_NAME" | tr '[:upper:]' '[:lower:]')"
case "$APP_NAME" in
	*[!A-Za-z0-9._-]*|'') fail "appName in gradle.properties may only use letters, digits, '.', '_' and '-'." ;;
esac

install_jdk() {
	if [ -x "$JDK_DIR/bin/java" ]; then
		return
	fi
	say "Installing JDK 25 into $JDK_DIR"
	local meta="$HOME_DIR/tmp/jdk25.json"
	run curl -fsSL -o "$meta" \
		"https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse" \
		|| fail "Could not reach api.adoptium.net"
	local link sum
	link="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[0]["binary"]["package"]["link"])' "$meta")"
	sum="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[0]["binary"]["package"]["checksum"])' "$meta")"
	local archive="$HOME_DIR/tmp/jdk25.tar.gz"
	run curl -fsSL -o "$archive" "$link" || fail "JDK download failed"
	[ "$(sha256sum "$archive" | cut -d' ' -f1)" = "$sum" ] || fail "JDK checksum mismatch, refusing to use it"
	rm -rf "$JDK_DIR" "$HOME_DIR/tmp/jdk25"
	mkdir -p "$HOME_DIR/tmp/jdk25"
	tar -xzf "$archive" -C "$HOME_DIR/tmp/jdk25"
	local home
	home="$(find "$HOME_DIR/tmp/jdk25" -maxdepth 4 -type f -name java -path '*/bin/java' | head -n1 | xargs dirname | xargs dirname)"
	mv "$home" "$JDK_DIR"
	rm -rf "$HOME_DIR/tmp/jdk25" "$archive" "$meta"
}

mkdir -p "$HOME_DIR/tmp"
chmod 700 "$HOME_DIR"
install_jdk

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$ROOT"

# ---- The Linux signing key ---------------------------------------------------------------------
#
# Every bundle is signed with the Linux signing key in ~/.keys/linux, and the app only installs
# updates signed with a key it trusts: the one in linux/update-key.pub when it was built, or one
# that key handed over to. A new key (--new-key) is approved by the old one in handover.txt, which
# goes with every release from then on, so installed copies move to the new key by themselves.
KEYS_HOME="${KEYS_HOME:-$HOME/.keys}"
LKEY_DIR="$KEYS_HOME/linux"
LKEY="$LKEY_DIR/linux-signing-key.pem"
LPUB="$LKEY_DIR/linux-signing-key.pub"
HANDOVER="$LKEY_DIR/handover.txt"
PINNED="$ROOT/linux/update-key.pub"
HANDOVER_ASSET="$SLUG-linux-key-handover.txt"

# scripts/Ed25519Tool.java, with the app's own update classes once they're built
key_tool() {
	"$JDK_DIR/bin/java" -cp "$ROOT/core/build/classes/java/main" "$ROOT/scripts/Ed25519Tool.java" "$@"
}

pinned_key() {
	{ grep -m1 -E '^[0-9a-f]{64}$' "$PINNED" 2> /dev/null; } || true
}

write_pin() {
	{
		echo "# The keys the Linux app accepts updates from (Ed25519, hex). scripts/linux.sh writes it from"
		echo "# ~/.keys/linux: push it with the source, and don't edit it by hand."
		echo "$1"
	} > "$PINNED.tmp" && mv "$PINNED.tmp" "$PINNED"
	[ "$(pinned_key)" = "$1" ] || fail "Could not write $PINNED"
}

keys_private() {
	mkdir -p "$LKEY_DIR"
	find "$KEYS_HOME" -type d -exec chmod 700 {} + 2> /dev/null || true
	find "$KEYS_HOME" -type f -exec chmod 600 {} + 2> /dev/null || true
}

ensure_linux_key() {
	keys_private
	if [ ! -f "$LKEY" ] && [ ! -f "$LPUB" ]; then
		say "Creating the Linux signing key in $LKEY_DIR (back up $KEYS_HOME: ./BUILD.sh --backup-keys)"
		(umask 077; key_tool genkey "$LKEY" "$LPUB") >> "$LOG" 2>&1 || fail "Could not create the Linux signing key"
		keys_private
	elif [ ! -f "$LKEY" ] || [ ! -f "$LPUB" ]; then
		fail "$LKEY_DIR holds only half of the Linux signing key. Restore it from your backup."
	fi
	key_tool check "$LKEY" "$LPUB" >> "$LOG" 2>&1 || fail "$LKEY and $LPUB don't belong together. Restore them from your backup."
	local current pinned
	current="$(tr -d '[:space:]' < "$LPUB")"
	pinned="$(pinned_key)"
	if [ -z "$pinned" ]; then
		write_pin "$current"
		say "The Linux app now only accepts updates signed with your Linux key (linux/update-key.pub). Push it with the source."
	elif [ "$pinned" != "$current" ]; then
		if key_tool reaches "$PINNED" "$HANDOVER" "$current" >> "$LOG" 2>&1; then
			write_pin "$current"
			say "linux/update-key.pub named an earlier Linux key; it now names the current one (installed copies follow the handover)."
		else
			fail "linux/update-key.pub names a different Linux key ($pinned) than $LPUB ($current), and no handover leads from it to yours, so installed copies would refuse this build. Restore the matching key from your backup, or put back the linux/update-key.pub of your last release."
		fi
	fi
}

# A new Linux signing key, approved by the old one, so installed copies take it as a normal update.
new_linux_key() {
	if [ ! -f "$LKEY" ]; then
		say "There's no Linux signing key yet; the next Linux build makes one (or option 13)."
		return 0
	fi
	ensure_linux_key
	say "This makes a new Linux signing key. The old one signs its approval of the new one, which goes"
	say "with every release from now on, so installed copies accept the new key. Keep the old key too."
	local answer=""
	if { true > /dev/tty; } 2> /dev/null; then
		printf 'Make a new Linux signing key? [y/N] ' > /dev/tty
		read -r answer < /dev/tty || answer=""
	fi
	case "$answer" in
		y|Y|yes|YES) ;;
		*) say "Nothing was changed."; return 0 ;;
	esac
	local stamp new_pem="$LKEY_DIR/new-key.pem" new_pub="$LKEY_DIR/new-key.pub" line
	stamp="$(date +%Y%m%d-%H%M%S)"
	rm -f "$new_pem" "$new_pub"
	(umask 077; key_tool genkey "$new_pem" "$new_pub") >> "$LOG" 2>&1 || fail "Could not create the new key"
	line="$(key_tool handover "$LKEY" "$LPUB" "$new_pub" 2>> "$LOG")" || { rm -f "$new_pem" "$new_pub"; fail "The old key could not approve the new one"; }
	(umask 077; printf '%s\n' "$line" >> "$HANDOVER")
	if ! key_tool reaches "$LPUB" "$HANDOVER" "$(tr -d '[:space:]' < "$new_pub")" >> "$LOG" 2>&1; then
		rm -f "$new_pem" "$new_pub"
		fail "The handover doesn't check out; the old key is still in use"
	fi
	mkdir -p "$LKEY_DIR/old"
	mv "$LKEY" "$LKEY_DIR/old/linux-signing-key-$stamp.pem"
	mv "$LPUB" "$LKEY_DIR/old/linux-signing-key-$stamp.pub"
	mv "$new_pem" "$LKEY"
	mv "$new_pub" "$LPUB"
	keys_private
	write_pin "$(tr -d '[:space:]' < "$LPUB")"
	say "Done. The next Linux build is signed with the new key; the old one is in $LKEY_DIR/old."
	say "Push the source (linux/update-key.pub changed), upload dist/$HANDOVER_ASSET with every release"
	say "from now on, and back up $KEYS_HOME now: ./BUILD.sh --backup-keys (option 17)"
}

show_linux_key() {
	say "Linux signing key (in $LKEY_DIR)"
	if [ -f "$LPUB" ]; then
		say "    Signs Linux releases:   $(tr -d '[:space:]' < "$LPUB")"
	else
		say "    None yet. The first Linux build makes one (or option 13)."
	fi
	say "    The app trusts:         $(pinned_key) (linux/update-key.pub)"
	if [ -s "$HANDOVER" ]; then
		say "    Key changes so far:     $(grep -c . "$HANDOVER") (handover.txt, uploaded with every release)"
	fi
}

# Before publishing: the bundle in dist/ is signed so installed copies accept it.
verify_dist() {
	local out="$ROOT/dist/$SLUG$VERSION.flatpak"
	[ -f "$out" ] && [ -f "$out.sha256" ] && [ -f "$out.sig" ] \
		|| fail "The Linux bundle, its .sha256 or its .sig for version $VERSION is missing from dist/. Build it first (option 2)."
	[ "$(sha256sum "$out" | cut -d' ' -f1)" = "$(cut -d' ' -f1 < "$out.sha256")" ] \
		|| fail "$(basename "$out") doesn't match its .sha256. Build it again."
	run ./gradlew --no-daemon -PlinuxOnly=true :core:classes || fail "Could not build the app's update check"
	key_tool verify "$out" "$out.sig" "$PINNED" "$HANDOVER" >> "$LOG" 2>&1 \
		|| fail "The app would refuse $(basename "$out")'s signature. Build it again (option 2)."
	if [ -s "$HANDOVER" ]; then
		[ -f "$ROOT/dist/$HANDOVER_ASSET" ] || cp "$HANDOVER" "$ROOT/dist/$HANDOVER_ASSET"
	fi
	say "$(basename "$out") is signed with your Linux key"
}

case "$ACTION" in
	new-key) new_linux_key; exit 0 ;;
	create-key) ensure_linux_key; say "The Linux signing key is in $LKEY_DIR. Back up $KEYS_HOME (option 17)."; exit 0 ;;
	show-key) show_linux_key; exit 0 ;;
	verify-dist) verify_dist; exit 0 ;;
	test)
		say "Running the tests"
		run ./gradlew --no-daemon -PlinuxOnly=true ${GRADLE_EXTRA[@]+"${GRADLE_EXTRA[@]}"} :core:test :desktop:test \
			|| fail "a test failed"
		say "All tests passed"
		exit 0
		;;
esac
ensure_linux_key

WORK="$ROOT/desktop/build/flatpak"
if [ "$CLEAN" = 1 ]; then
	say "Cleaning"
	run ./gradlew --no-daemon -PlinuxOnly=true clean || fail "clean failed"
	rm -rf "$WORK"
fi

mapfile -t STALE < <(grep -rlE '^import javafx\.' "$ROOT/desktop/src" --include='*.java' 2> /dev/null || true)
if [ "${#STALE[@]}" -gt 0 ]; then
	for file in "${STALE[@]}"; do
		say "  left over from an older version: ${file#"$ROOT"/}"
	done
	fail "Files from an older version are still in this folder. Delete the whole folder, unzip the new version into an empty one and build again (your settings and library are kept: they're not in this folder)."
fi

say "Testing and building the app"
run ./gradlew --no-daemon -PlinuxOnly=true ${GRADLE_EXTRA[@]+"${GRADLE_EXTRA[@]}"} :core:test :desktop:test :desktop:linuxDist \
	|| fail "the Gradle build failed"
DIST="$ROOT/desktop/build/linux/$SLUG"
[ -f "$DIST/lib/native/libtorrent4j.so" ] || fail "libtorrent's Linux library is missing from the build"

say "Preparing the Flatpak"
mkdir -p "$WORK"
fill() {
	APP_ID="$APP_ID" APP_NAME="$APP_NAME" SLUG="$SLUG" VERSION="$VERSION" DATE="$(date -u +%Y-%m-%d)" \
		GNOME="$GNOME" GITHUB_REPO="$GITHUB_REPO" DIST="$DIST" python3 - "$1" "$2" <<'PY'
import os, sys
from xml.sax.saxutils import escape
source, target = sys.argv[1], sys.argv[2]
text = open(source, encoding="utf-8").read()
for key in ("APP_ID", "APP_NAME", "SLUG", "VERSION", "DATE", "GNOME", "GITHUB_REPO", "DIST"):
    value = os.environ[key]
    text = text.replace("@" + key + "@", escape(value) if source.endswith(".xml") else value)
open(target, "w", encoding="utf-8").write(text)
PY
}
fill "$ROOT/linux/flatpak.yml" "$WORK/$APP_ID.yml"
fill "$ROOT/linux/launcher.sh" "$WORK/$SLUG.sh"
fill "$ROOT/linux/app.desktop" "$WORK/$APP_ID.desktop"
fill "$ROOT/linux/metainfo.xml" "$WORK/$APP_ID.metainfo.xml"

run flatpak remote-add --user --if-not-exists flathub "$FLATHUB" || fail "Could not add the Flathub remote"

if command -v flatpak-builder > /dev/null 2>&1; then
	BUILDER=(flatpak-builder)
else
	say "No flatpak-builder on this system, using Flathub's (org.flatpak.Builder)"
	run flatpak install --user -y --noninteractive flathub org.flatpak.Builder \
		|| fail "Could not install org.flatpak.Builder from Flathub"
	BUILDER=(flatpak run org.flatpak.Builder)
fi

say "Building the Flatpak (the first time downloads the GNOME $GNOME runtime)"
run "${BUILDER[@]}" --user --force-clean --disable-rofiles-fuse --install-deps-from=flathub \
	--repo="$WORK/repo" --state-dir="$WORK/state" "$WORK/build" "$WORK/$APP_ID.yml" \
	|| fail "flatpak-builder failed"

mkdir -p "$ROOT/dist"
OUT="$ROOT/dist/$SLUG$VERSION.flatpak"
say "Bundling"
run flatpak build-bundle --runtime-repo="$FLATHUB" "$WORK/repo" "$OUT" "$APP_ID" \
	|| fail "flatpak build-bundle failed"

(cd "$ROOT/dist" && printf '%s  %s\n' "$(sha256sum "$(basename "$OUT")" | cut -d' ' -f1)" "$(basename "$OUT")" \
	> "$(basename "$OUT").sha256")

say "Signing the bundle with the Linux signing key"
key_tool sign "$LKEY" "$OUT" "$OUT.sig" >> "$LOG" 2>&1 || fail "Could not sign the bundle"
# The app's own check, with its own code: as this build will make it, and as copies that still
# trust an earlier key will (through the handovers).
key_tool verify "$OUT" "$OUT.sig" "$PINNED" "$HANDOVER" >> "$LOG" 2>&1 || fail "The app would refuse this bundle's signature"
if [ -s "$HANDOVER" ]; then
	cp "$HANDOVER" "$ROOT/dist/$HANDOVER_ASSET"
	first_key="$WORK/first-key.pub"
	awk 'NF == 3 { print $2; exit }' "$HANDOVER" > "$first_key"
	key_tool verify "$OUT" "$OUT.sig" "$first_key" "$HANDOVER" >> "$LOG" 2>&1 \
		|| fail "Copies that trust the first Linux key would refuse this bundle; check $HANDOVER"
else
	rm -f "$ROOT/dist/$HANDOVER_ASSET"
fi

while IFS= read -r old; do
	rm -f -- "$old"
	say "Removed old $(basename "$old")"
done < <(find "$ROOT/dist" -maxdepth 1 -type f \( -name "$SLUG*.flatpak" -o -name "$SLUG*.flatpak.sha256" \
	-o -name "$SLUG*.flatpak.sig" \) ! -name "$(basename "$OUT")" ! -name "$(basename "$OUT").sha256" \
	! -name "$(basename "$OUT").sig")

say "Done: $OUT"
say "      $OUT.sha256"
say "      $OUT.sig"
if [ -s "$HANDOVER" ]; then
	say "      $ROOT/dist/$HANDOVER_ASSET"
fi
say "Publish them with option 4 or 5 (or attach all of them to the GitHub release yourself):"
say "      the app only installs updates that have them."

ask() {
	[ -t 0 ] || return 1
	local answer
	read -r -p "$1 [Y/n] " answer || return 1
	case "$answer" in
		''|y|Y|yes|YES) return 0 ;;
		*) return 1 ;;
	esac
}

if [ "$INSTALL" = 1 ]; then
	if LC_ALL=C flatpak info --system "$APP_ID" > /dev/null 2>&1; then
		say "An older copy is installed for all users; the app menu may start that one instead."
		if ask "Remove it (needs sudo)?"; then
			sudo flatpak uninstall --system -y "$APP_ID" || say "Not removed. Later: sudo flatpak uninstall --system $APP_ID"
		else
			say "Remove it with: sudo flatpak uninstall --system $APP_ID"
		fi
	fi
	say "Installing for this user"
	run flatpak install --user -y --reinstall "$OUT" || fail "flatpak install failed"
	installed="$(LC_ALL=C flatpak info --user "$APP_ID" 2> /dev/null | sed -n 's/^ *Version: *//p' | head -n1)"
	if [ -n "$installed" ] && [ "$installed" != "$VERSION" ]; then
		fail "Installed, but flatpak reports version $installed instead of $VERSION"
	fi
	say "Installed version $VERSION"
	if LC_ALL=C flatpak ps --columns=application 2> /dev/null | grep -qx "$APP_ID"; then
		say "The app is still running the old version."
		if ask "Close it now (your library and settings are saved)?"; then
			flatpak kill "$APP_ID" 2> /dev/null || say "Couldn't close it; close its window yourself."
		else
			say "Close it and start it again to get version $VERSION."
		fi
	fi
	say "Start it from the app menu, or: flatpak run $APP_ID"
fi
