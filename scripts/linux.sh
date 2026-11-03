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
for arg in "$@"; do
	case "$arg" in
		--clean) CLEAN=1 ;;
		--install) INSTALL=1 ;;
		--verbose) VERBOSE=1 ;;
		*) echo "Unknown option: $arg (see ../BUILD.sh --help)" >&2; exit 2 ;;
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
run ./gradlew --no-daemon -PlinuxOnly=true :core:test :desktop:test :desktop:linuxDist \
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

while IFS= read -r old; do
	rm -f -- "$old"
	say "Removed old $(basename "$old")"
done < <(find "$ROOT/dist" -maxdepth 1 -type f \( -name "$SLUG*.flatpak" -o -name "$SLUG*.flatpak.sha256" \) \
	! -name "$(basename "$OUT")" ! -name "$(basename "$OUT").sha256")

say "Done: $OUT"
say "      $OUT.sha256"

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
