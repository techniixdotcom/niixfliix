#!/usr/bin/env bash
# BUILD.sh -- the one entry point for building, testing and publishing niixfliix: the Android app
# and the Linux app (Flatpak).
#
# Run it with no arguments for the menu, or use one of these directly. The menu and the options
# are the same in the BUILD.sh of TestTube, NiiX and niixfliix, wherever an option fits the app.
#
#   ./BUILD.sh --release                  Build the signed release APK (dist/niixfliix<version>.apk).
#   ./BUILD.sh --linux                    Build the Linux app (dist/niixfliix<version>.flatpak, signed).
#   ./BUILD.sh --all                      Build both, Android first.
#   ./BUILD.sh --publish                  Build both and publish them to GitHub Releases: the APK,
#                                         the Flatpak, their .sha256, the .sig and the key handover,
#                                         checked so the apps' updaters accept them. On Linux.
#   ./BUILD.sh --upload-only              Publish what is already in dist/, without building.
#   ./BUILD.sh --debug                    Build a debug APK (no signing key needed).
#   ./BUILD.sh --install                  Install the APK in dist/ on the phone connected over USB.
#   ./BUILD.sh --test                     Run the test suite (the Linux app's too, on Linux).
#   ./BUILD.sh --audit-zip                Pack the source into dist/, without keys, logs or build
#                                         output, for a reviewer.
#   ./BUILD.sh --write-dependency-hashes  Record the SHA-256 of every library (both apps, so on
#                                         Linux); Gradle then refuses any library that changes.
#                                         Run it again after changing a library version.
#   ./BUILD.sh --push-source              Put the current source on GitHub as a normal commit (no
#                                         release). README, screenshots and docs there are kept.
#   ./BUILD.sh --set-version              Set the version name and code for the next release.
#   ./BUILD.sh --create-keys              Create the signing keys (the first builds also do).
#   ./BUILD.sh --show-keys                Show the signing keys' fingerprints and where they are.
#   ./BUILD.sh --use-shared-key           Switch the Android app to the Android key all your apps
#                                         share (~/.keys/android, made the first time), for good;
#                                         installed copies update to it normally.
#   ./BUILD.sh --new-linux-key            Give the Linux app a new signing key; installed copies
#                                         accept it through the handover file.
#   ./BUILD.sh --backup-keys[=DIR]        Copy all signing keys (~/.keys) to DIR/.keys (a USB stick,
#                                         for example) and check the copy.
#   ./BUILD.sh --clean                    Delete all build output (dist/ included).
#
# Options that go with the builds:
#   --install    after building, install it: the APK on the connected phone, the Flatpak for you
#   --clean      delete earlier build output first (what's in dist/ stays)
#   --verbose    show Gradle's whole output, not only the logs (build.log, build-linux.log)
#   --android    the same as --release
#
# Everything ends up in dist/. The Android app builds on Linux and macOS, the Linux app on Linux
# (x86_64). Publishing needs the GitHub CLI (gh); it is installed and logged in on first use.
# The repo is githubRepo in gradle.properties, the one both apps' updaters read.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

missing=()
for need in gradle.properties settings.gradle.kts gradlew gradle/wrapper/gradle-wrapper.properties \
	gradle/libs.versions.toml app/build.gradle.kts core/build.gradle.kts desktop/build.gradle.kts linux/flatpak.yml \
	scripts/android.sh scripts/linux.sh scripts/android-keys.sh scripts/Ed25519Tool.java; do
	[ -e "$ROOT/$need" ] || missing+=("$need")
done
if [ "${#missing[@]}" -gt 0 ]; then
	echo "error: $ROOT isn't the whole project; it has no ${missing[*]}." >&2
	echo "       Unzip the full project zip into an empty folder and run BUILD.sh from there." >&2
	exit 1
fi
chmod +x "$ROOT/gradlew" "$ROOT/scripts/android.sh" "$ROOT/scripts/linux.sh" 2> /dev/null || true

KEYS_HOME="${KEYS_HOME:-$HOME/.keys}"
# shellcheck source=scripts/android-keys.sh
source "$ROOT/scripts/android-keys.sh"

say() { printf '==> %s\n' "$*"; }
warn() { printf 'warning: %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

# Questions go to the terminal; without one (CI, piped input) the answer comes from stdin.
ask() {
	ak_ask "$@"
}

has_tty() {
	{ true > /dev/tty; } 2> /dev/null
}

confirm() {
	local answer default="${2:-n}" hint="[y/N]"
	[ "$default" = y ] && hint="[Y/n]"
	ask "$1 $hint " answer
	answer="${answer:-$default}"
	case "$answer" in
		y|Y|yes|YES) return 0 ;;
		*) return 1 ;;
	esac
}

usage() {
	awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"
}

prop() { sed -n "s/^$1=//p" "$ROOT/gradle.properties" | tr -d '\r' | head -n1; }
APP_NAME="$(prop appName)"
SLUG="$(printf '%s' "$APP_NAME" | tr '[:upper:]' '[:lower:]')"
GITHUB_REPO="$(prop githubRepo)"
CHANGELOG="$ROOT/CHANGELOG.md"
DIST="$ROOT/dist"

# ---- What to do --------------------------------------------------------------------------------

MODE=""
CLEAN=0
INSTALL=0
VERBOSE=0
DEBUG=0
BACKUP_DIR=""

set_mode() {
	if [ -n "$MODE" ] && [ "$MODE" != "$1" ]; then
		die "Pick one action at a time ($MODE and $1 were both given)"
	fi
	MODE="$1"
}

show_menu() {
	local choice
	ask "$(printf 'What would you like to do?\n  1) Build a signed release APK\n  2) Build the Linux app (Flatpak)\n  3) Build the signed release APK and the Linux app\n  4) Build both and publish them to GitHub Releases\n  5) Publish the already-built APK and Linux app to GitHub Releases\n  6) Build a debug APK (no signing key needed)\n  7) Install the built APK to a connected phone (shows the real error if it fails)\n  8) Run the test suite\n  9) Package a source archive for a reviewer (no keys)\n 10) Record dependency hashes (supply-chain verification)\n 11) Push source to GitHub (no release)\n 12) Set the version for the next release\n 13) Create signing keys (once, before your first signed build)\n 14) Show the signing keys\n 15) Switch to the shared Android key (one key for all apps)\n 16) Give the Linux app a new signing key (installed copies accept it)\n 17) Back up all signing keys (~/.keys)\n 18) Delete all build output\nEnter a number [1-18]: ')" choice
	case "$choice" in
		1) MODE=release ;;
		2) MODE=linux ;;
		3) MODE=all ;;
		4) MODE=publish ;;
		5) MODE=upload-only ;;
		6) MODE=debug ;;
		7) MODE=install ;;
		8) MODE="test" ;;
		9) MODE=audit-zip ;;
		10) MODE=dephashes ;;
		11) MODE=pushsource ;;
		12) MODE=setversion ;;
		13) MODE=createkeys ;;
		14) MODE=showkeys ;;
		15) MODE=sharedkey ;;
		16) MODE=newlinuxkey ;;
		17) MODE=backupkeys ;;
		18) MODE=clean ;;
		*) die "'$choice' isn't one of the options above." ;;
	esac
}

for arg in "$@"; do
	case "$arg" in
		--release|--android) set_mode release ;;
		--linux) set_mode linux ;;
		--all) set_mode all ;;
		--publish) set_mode publish ;;
		--upload-only) set_mode upload-only ;;
		--debug) DEBUG=1 ;;
		--test) set_mode "test" ;;
		--audit-zip) set_mode audit-zip ;;
		--write-dependency-hashes) set_mode dephashes ;;
		--push-source) set_mode pushsource ;;
		--set-version) set_mode setversion ;;
		--create-keys) set_mode createkeys ;;
		--show-keys) set_mode showkeys ;;
		--use-shared-key) set_mode sharedkey ;;
		--new-linux-key) set_mode newlinuxkey ;;
		--backup-keys) set_mode backupkeys ;;
		--backup-keys=*) set_mode backupkeys; BACKUP_DIR="${arg#--backup-keys=}" ;;
		--install) INSTALL=1 ;;
		--clean) CLEAN=1 ;;
		--verbose) VERBOSE=1 ;;
		-h|--help) usage; exit 0 ;;
		*) die "Unknown option: $arg (see ./BUILD.sh --help)" ;;
	esac
done
# --debug on its own or with --release/--android (as in older versions): the debug APK.
if [ "$DEBUG" = 1 ]; then
	case "$MODE" in
		''|release) MODE=debug ;;
		debug) ;;
		*) die "--debug is for the Android app's build, not for --$MODE" ;;
	esac
fi
# --clean and --install on their own: delete the build output, install the APK already built.
if [ -z "$MODE" ] && [ "$CLEAN" = 1 ]; then
	MODE=clean
elif [ -z "$MODE" ] && [ "$INSTALL" = 1 ]; then
	MODE=install
fi
if [ -z "$MODE" ]; then
	if [ $# -eq 0 ] && has_tty; then
		show_menu
	else
		die "Say what to do, for example --release, --linux or --all (see ./BUILD.sh --help)."
	fi
fi

LINUX_OK=0
if [ "$(uname -s)" = Linux ]; then
	LINUX_OK=1
fi
needs_linux() {
	[ "$LINUX_OK" = 1 ] || die "$1 needs Linux: the Linux app is built and checked there. On this computer only the Android options work."
}

# ---- Tools the scripts can't install themselves --------------------------------------------------

package_for() {
	case "$1:$2" in
		python3:pacman) echo python ;;
		sha256sum:*) echo coreutils ;;
		gh:pacman) echo github-cli ;;
		*) echo "$1" ;;
	esac
}

install_command() {
	local manager="$1"
	shift
	local sudo=""
	if [ "$(id -u)" != 0 ]; then
		sudo="sudo "
	fi
	case "$manager" in
		pacman) echo "${sudo}pacman -S --needed --noconfirm $*" ;;
		apt-get) echo "${sudo}apt-get update && ${sudo}apt-get install -y $*" ;;
		dnf) echo "${sudo}dnf install -y $*" ;;
		zypper) echo "${sudo}zypper --non-interactive install $*" ;;
		xbps-install) echo "${sudo}xbps-install -Sy $*" ;;
		brew) echo "brew install $*" ;;
	esac
}

ensure_tools() {
	local missing=()
	local tool
	for tool in "$@"; do
		command -v "$tool" > /dev/null 2>&1 || missing+=("$tool")
	done
	if [ "${#missing[@]}" -eq 0 ]; then
		return
	fi
	local manager=""
	if [ -e /run/ostree-booted ]; then
		manager=""
	elif [ "$(uname -s)" = Darwin ]; then
		command -v brew > /dev/null 2>&1 && manager=brew
	else
		for tool in pacman apt-get dnf zypper xbps-install; do
			if command -v "$tool" > /dev/null 2>&1; then
				manager="$tool"
				break
			fi
		done
	fi
	echo "Needed but not installed: ${missing[*]}"
	if [ -z "$manager" ]; then
		echo "Install them with your system's package manager (on Fedora Silverblue and the like: rpm-ostree install," >&2
		echo "then restart), then run BUILD.sh again." >&2
		exit 1
	fi
	local packages=()
	for tool in "${missing[@]}"; do
		packages+=("$(package_for "$tool" "$manager")")
	done
	local cmd
	cmd="$(install_command "$manager" "${packages[@]}")"
	if ! has_tty; then
		echo "Install them with:  $cmd" >&2
		echo "then run BUILD.sh again." >&2
		exit 1
	fi
	if [ "$(id -u)" != 0 ] && [ "$manager" != brew ] && ! command -v sudo > /dev/null 2>&1; then
		echo "Install them as root with:  ${cmd//sudo /}" >&2
		exit 1
	fi
	echo "This installs them:  $cmd"
	confirm "Install now?" y || { echo "Not installed. Install them yourself, then run BUILD.sh again." >&2; exit 1; }
	if ! bash -c "$cmd"; then
		echo "Installing failed. Check the messages above (on Arch, an out-of-date system may first need" >&2
		echo "sudo pacman -Syu), then run BUILD.sh again." >&2
		exit 1
	fi
	for tool in "${missing[@]}"; do
		command -v "$tool" > /dev/null 2>&1 || { echo "'$tool' still isn't there after installing." >&2; exit 1; }
	done
}

ANDROID_TOOLS=(curl tar python3 unzip)
LINUX_TOOLS=(curl tar python3 sha256sum flatpak)

ensure_gh() {
	ensure_tools gh git
	if ! gh auth status > /dev/null 2>&1; then
		has_tty || die "The GitHub CLI isn't logged in. Run: gh auth login"
		say "Logging in to GitHub (once)"
		gh auth login < /dev/tty > /dev/tty 2>&1 || die "GitHub login failed"
	fi
	gh auth status > /dev/null 2>&1 || die "The GitHub CLI isn't logged in. Run: gh auth login"
}

# ---- Builds ------------------------------------------------------------------------------------

build_args() {
	BUILD_ARGS=()
	[ "$CLEAN" = 1 ] && BUILD_ARGS+=(--clean)
	[ "$VERBOSE" = 1 ] && BUILD_ARGS+=(--verbose)
	return 0
}

build_android() {
	build_args
	[ "$INSTALL" = 1 ] && BUILD_ARGS+=(--install)
	say "Android app"
	bash "$ROOT/scripts/android.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"} "$@"
}

build_linux() {
	build_args
	if [ "$INSTALL" = 1 ]; then
		BUILD_ARGS+=(--install)
	elif [ "$MODE" != publish ] && has_tty && confirm "Install the Linux app for your user once it's built?" y; then
		BUILD_ARGS+=(--install)
	fi
	say "Linux app"
	bash "$ROOT/scripts/linux.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"} "$@"
}

clean_outputs() {
	say "Deleting build output"
	rm -rf "$DIST" "$ROOT/build" "$ROOT/app/build" "$ROOT/core/build" "$ROOT/desktop/build" "$ROOT/.gradle" "$ROOT/.kotlin"
	say "Done. The next build starts from scratch (the JDKs and the SDK in ~/.niixfliix and the keys in $KEYS_HOME stay)."
}

# ---- Versions ----------------------------------------------------------------------------------

version_name() { prop versionName; }
version_code() { prop versionCode; }

# 1.17.1 -> 11701, the scheme every release so far used. Empty for names it can't read.
version_code_for() {
	local major minor patch
	case "$1" in
		*[!0-9.]*|*..*|.*|*.) return 0 ;;
	esac
	IFS=. read -r major minor patch extra <<< "$1"
	[ -n "$major" ] && [ -n "$minor" ] && [ -n "$patch" ] && [ -z "${extra:-}" ] || return 0
	major=$((10#$major)); minor=$((10#$minor)); patch=$((10#$patch))
	[ "$minor" -lt 100 ] && [ "$patch" -lt 100 ] || return 0
	printf '%s' $((major * 10000 + minor * 100 + patch))
}

# Whether version a is newer than b, by number (1.17.10 is newer than 1.17.9).
version_newer() {
	[ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n1)" = "$1" ]
}

# The newest release on GitHub, pre-releases included (what the updaters look at). Empty if unknown.
published_version() {
	command -v gh > /dev/null 2>&1 && gh auth status > /dev/null 2>&1 || return 0
	gh api "repos/$GITHUB_REPO/releases?per_page=30" --jq '.[] | select(.draft | not) | .tag_name' 2> /dev/null \
		| sed 's/^v//' | grep -E '^[0-9]+(\.[0-9]+)+$' | sort -V | tail -n1 || true
}

set_version() {
	local current current_code published base suggested name code
	current="$(version_name)"
	current_code="$(version_code)"
	[ -n "$current" ] && [ -n "$current_code" ] || die "Could not read versionName and versionCode from gradle.properties"
	published="$(published_version)"
	[ -n "$published" ] && say "Newest release on GitHub: $published"
	base="$current"
	if [ -n "$published" ] && version_newer "$published" "$base"; then
		base="$published"
	fi
	IFS=. read -r a b c <<< "$base"
	suggested="$base"
	if [ -n "${c:-}" ]; then
		suggested="$a.$b.$((10#$c + 1))"
	fi
	say "Current version: $current (code $current_code)"
	ask "New version [$suggested]: " name
	name="${name:-$suggested}"
	name="${name#v}"
	code="$(version_code_for "$name")"
	[ -n "$code" ] || die "A version looks like 1.17.2: three numbers with dots, the last two below 100"
	[ "$code" -gt "$current_code" ] || die "Version $name (code $code) isn't newer than $current (code $current_code). Android won't install it as an update."
	if [ -n "$published" ] && ! version_newer "$name" "$published"; then
		die "Version $name isn't newer than $published, which is already on GitHub. The apps wouldn't offer it."
	fi
	sed -e "s/^versionName=.*/versionName=$name/" -e "s/^versionCode=.*/versionCode=$code/" \
		"$ROOT/gradle.properties" > "$ROOT/gradle.properties.tmp"
	mv "$ROOT/gradle.properties.tmp" "$ROOT/gradle.properties"
	[ "$(version_name)" = "$name" ] && [ "$(version_code)" = "$code" ] || die "Could not write the version into gradle.properties"
	say "Version set: $current ($current_code) -> $name ($code)"
	if [ -f "$CHANGELOG" ] && ! grep -qxF "## $name" "$CHANGELOG"; then
		awk -v v="$name" 'done == 0 && /^## / { print "## " v "\n"; done = 1 } { print }' "$CHANGELOG" > "$CHANGELOG.tmp"
		mv "$CHANGELOG.tmp" "$CHANGELOG"
		say "Added an empty '## $name' section to CHANGELOG.md. Write what changed there before publishing."
	fi
}

# The notes under "## <version>" in CHANGELOG.md, up to the next version.
changelog_notes() {
	[ -f "$CHANGELOG" ] || return 0
	awk -v v="$1" '
		$0 ~ "^## " { if (found) exit; if ($0 == "## " v || $0 == "## v" v) { found = 1; next } }
		found { print }
	' "$CHANGELOG" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}'
}

# ---- Source ------------------------------------------------------------------------------------

# Files that must never leave this computer.
SECRET_PATTERNS=('*.p12' '*.jks' '*.keystore' '*.pem' '*.key' 'signing.env' 'lineage.bin' 'keystore.properties' 'local.properties')

# Copies the source tree to $1, leaving out build output, logs and anything secret.
copy_source_tree() {
	local target="$1" pattern found=""
	mkdir -p "$target"
	local excludes=(--exclude=./.git --exclude=./.gradle --exclude=./.kotlin --exclude=./.idea
		--exclude=./build --exclude=./app/build --exclude=./core/build --exclude=./desktop/build
		--exclude=./dist --exclude='./*.log' --exclude='*.iml' --exclude=.DS_Store --exclude=./captures)
	for pattern in "${SECRET_PATTERNS[@]}"; do
		excludes+=(--exclude="$pattern")
	done
	(cd "$ROOT" && tar -cf - "${excludes[@]}" .) | (cd "$target" && tar -xf -)
	for pattern in "${SECRET_PATTERNS[@]}"; do
		found+="$(find "$target" -name "$pattern" -not -path '*/.git/*' 2> /dev/null)"
	done
	[ -z "$found" ] || die "Secret files ended up in the copy, stopping: $found"
}

audit_zip() {
	local version stage out
	version="$(version_name)"
	TMP="$(mktemp -d)"
	stage="$TMP/$SLUG"
	copy_source_tree "$stage"
	mkdir -p "$DIST"
	ensure_tools zip
	out="$DIST/$SLUG$version-source.zip"
	rm -f "$out"
	(cd "$TMP" && zip -qr -X "$out" "$SLUG")
	rm -rf "$TMP"
	say "Source (no keys, logs or build output): $out"
	say "SHA-256: $(ak_sha256 "$out")"
}

# Folders whose files on GitHub are made to match this source exactly (old files deleted).
MIRRORED_DIRS=(app core desktop gradle linux scripts)
# Never deleted by --push-source, and left as GitHub has them when this folder has no copy.
KEEP_ON_GITHUB=(README.md screens docs .github)

is_mirrored() {
	local dir
	for dir in "${MIRRORED_DIRS[@]}"; do
		case "$1" in
			"$dir"/*) return 0 ;;
		esac
	done
	return 1
}

push_source() {
	ensure_gh
	local version clone dir path deleted bad="" kept message answer name email login
	version="$(version_name)"
	TMP="$(mktemp -d)"
	clone="$TMP/repo"
	say "Fetching $GITHUB_REPO"
	gh auth setup-git > /dev/null 2>&1 || true
	git clone --quiet "https://github.com/$GITHUB_REPO.git" "$clone" || die "Could not clone $GITHUB_REPO"
	# One normal commit on top of the history, nothing is force-pushed. Only the code folders are
	# mirrored exactly (a file deleted here is deleted there); everything else is added or updated,
	# never deleted, and what's in KEEP_ON_GITHUB stays as GitHub has it unless this folder has it.
	for dir in "${MIRRORED_DIRS[@]}"; do
		rm -rf "${clone:?}/$dir"
	done
	copy_source_tree "$clone"
	for path in "${KEEP_ON_GITHUB[@]}"; do
		if [ ! -e "$ROOT/$path" ] && (cd "$clone" && git cat-file -e "HEAD:$path" 2> /dev/null); then
			rm -rf "${clone:?}/$path"
			(cd "$clone" && git checkout --quiet HEAD -- "$path")
		fi
	done
	(cd "$clone" && git add -A)
	deleted="$(cd "$clone" && git diff --cached --name-only --diff-filter=D)"
	while IFS= read -r path; do
		[ -n "$path" ] || continue
		is_mirrored "$path" || bad+="$path"$'\n'
	done <<< "$deleted"
	[ -z "$bad" ] || die "Stopped, nothing was pushed: this would delete on GitHub:"$'\n'"$bad"
	if (cd "$clone" && git diff --cached --quiet); then
		say "GitHub already has exactly this source; nothing to push"
		rm -rf "$TMP"
		return
	fi
	say "Changes compared with GitHub:"
	(cd "$clone" && git diff --cached --stat | tail -n 25)
	if [ -n "$deleted" ]; then
		say "Deleted on GitHub because they're gone from the code here:"
		printf '    %s\n' "${deleted//$'\n'/$'\n'    }"
	fi
	kept="$(cd "$clone" && git ls-files | while IFS= read -r f; do if [ ! -e "$ROOT/$f" ]; then printf '%s\n' "$f"; fi; done)"
	if [ -n "$kept" ]; then
		say "Kept as they are, only on GitHub: $(printf '%s' "$kept" | awk -F/ '{print $1}' | sort -u | tr '\n' ' ')"
	fi
	message="$APP_NAME $version"
	if has_tty; then
		ask "Commit message [$message]: " answer
		message="${answer:-$message}"
		confirm "Push this to $GITHUB_REPO?" y || die "Nothing was pushed"
	fi
	name="$(git config --global user.name 2> /dev/null || true)"
	email="$(git config --global user.email 2> /dev/null || true)"
	if [ -z "$name" ] || [ -z "$email" ]; then
		login="$(gh api user --jq .login 2> /dev/null || true)"
		login="${login:-$SLUG}"
		name="${name:-$login}"
		email="${email:-$login@users.noreply.github.com}"
	fi
	(cd "$clone" && git -c user.name="$name" -c user.email="$email" commit --quiet -m "$message")
	(cd "$clone" && git push --quiet origin HEAD) \
		|| die "GitHub refused the push (did something change there in the meantime?). Nothing was overwritten."
	rm -rf "$TMP"
	say "Source pushed to https://github.com/$GITHUB_REPO"
}

# ---- Publishing --------------------------------------------------------------------------------

# Publishes what's in dist/ as release v<version>: the APK, the Flatpak, their .sha256, the .sig
# and the key handover. Then checks it the way the updaters will: every file is all there, GitHub's
# SHA-256 matches, and it's the newest release.
publish_release() {
	ensure_gh
	local version tag file name size sha remote_size remote_digest attempt notes exists=0 prerelease=0
	version="$(version_name)"
	tag="v$version"
	local files=("$DIST/$SLUG$version.apk" "$DIST/$SLUG$version.apk.sha256"
		"$DIST/$SLUG$version.flatpak" "$DIST/$SLUG$version.flatpak.sha256" "$DIST/$SLUG$version.flatpak.sig")
	[ -f "$DIST/$SLUG-linux-key-handover.txt" ] && files+=("$DIST/$SLUG-linux-key-handover.txt")
	for file in "${files[@]}"; do
		[ -f "$file" ] || die "$(basename "$file") is missing from dist/. Build both first (option 3)."
	done
	# The checks the apps make, with their own rules: the APK's signing key, the bundle's signature
	bash "$ROOT/scripts/android.sh" --verify-dist || die "The APK can't be published"
	bash "$ROOT/scripts/linux.sh" --verify-dist || die "The Linux app can't be published"

	case "$tag" in
		*alpha*|*beta*|*rc*) prerelease=1 ;;
	esac
	local published
	published="$(published_version)"
	if [ -n "$published" ] && [ "$published" != "$version" ] && ! version_newer "$version" "$published"; then
		die "$version isn't newer than $published, the newest release on GitHub. Set a new version first (option 12)."
	fi
	notes="$(changelog_notes "$version")"
	[ -n "$(printf '%s' "$notes" | tr -d '[:space:]')" ] \
		|| die "CHANGELOG.md has nothing under '## $version'. Write what changed there: it becomes the release notes and the apps' update dialog."
	TMP="$(mktemp -d)"
	printf '%s\n' "$notes" > "$TMP/notes.md"
	say "Release notes for $tag:"
	sed 's/^/    /' "$TMP/notes.md"
	gh release view "$tag" --repo "$GITHUB_REPO" > /dev/null 2>&1 && exists=1
	if has_tty; then
		if [ "$exists" = 1 ]; then
			confirm "$tag already exists on $GITHUB_REPO. Replace its files and notes?" y || die "Nothing was published"
		else
			confirm "Publish $tag to $GITHUB_REPO?" y || die "Nothing was published"
		fi
	fi
	if [ "$exists" = 1 ]; then
		gh release upload "$tag" "${files[@]}" --repo "$GITHUB_REPO" --clobber
		gh release edit "$tag" --repo "$GITHUB_REPO" --notes-file "$TMP/notes.md" > /dev/null
	elif [ "$prerelease" = 1 ]; then
		gh release create "$tag" "${files[@]}" --repo "$GITHUB_REPO" --title "$tag" --notes-file "$TMP/notes.md" --prerelease
	else
		gh release create "$tag" "${files[@]}" --repo "$GITHUB_REPO" --title "$tag" --notes-file "$TMP/notes.md" --latest
	fi

	# A failed upload can leave a broken file behind under the right name, so check GitHub's own size
	# and SHA-256 of every file, and upload again if one is off.
	for file in "${files[@]}"; do
		name="$(basename "$file")"
		size="$(wc -c < "$file" | tr -d ' ')"
		sha="$(ak_sha256 "$file")"
		for attempt in 1 2 3; do
			remote_size="$(gh api "repos/$GITHUB_REPO/releases/tags/$tag" --jq ".assets[] | select(.name == \"$name\") | .size" 2> /dev/null || true)"
			remote_digest="$(gh api "repos/$GITHUB_REPO/releases/tags/$tag" --jq ".assets[] | select(.name == \"$name\") | .digest // \"\"" 2> /dev/null || true)"
			if [ "$remote_size" = "$size" ] && { [ "$remote_digest" = "sha256:$sha" ] || [ -z "$remote_digest" ]; }; then
				[ -n "$remote_digest" ] && break
				# Complete, but GitHub hasn't shown its SHA-256 yet
				sleep 10
				continue
			fi
			if [ "$attempt" -lt 3 ]; then
				warn "$name on GitHub doesn't match (size ${remote_size:-none}); uploading it again"
				sleep 5
				gh release upload "$tag" "$file" --repo "$GITHUB_REPO" --clobber > /dev/null 2>&1 || true
			fi
		done
		[ "$remote_size" = "$size" ] || die "$name on GitHub is ${remote_size:-missing} bytes, the file here is $size. Run option 5 to try again."
		[ -z "$remote_digest" ] || [ "$remote_digest" = "sha256:$sha" ] \
			|| die "GitHub reports SHA-256 '$remote_digest' for $name, expected $sha. Run option 5 to try again."
		say "On GitHub: $name ($size bytes)"
	done
	rm -rf "$TMP"
	if [ "$prerelease" = 0 ]; then
		local latest
		latest="$(gh api "repos/$GITHUB_REPO/releases/latest" --jq .tag_name 2> /dev/null || true)"
		[ "$latest" = "$tag" ] || gh release edit "$tag" --repo "$GITHUB_REPO" --latest > /dev/null 2>&1 || true
	fi
	say "Published: https://github.com/$GITHUB_REPO/releases/tag/$tag"
}

# ---- Keys --------------------------------------------------------------------------------------

backup_keys() {
	if [ -z "$BACKUP_DIR" ]; then
		ask "Folder to copy all signing keys to (a USB stick, for example): " BACKUP_DIR
	fi
	[ -n "$BACKUP_DIR" ] || die "No folder given"
	BACKUP_DIR="${BACKUP_DIR%/}"
	case "$BACKUP_DIR" in
		"$ROOT"|"$ROOT"/*) die "Not inside the project folder: the keys would go wherever the project goes." ;;
	esac
	ak_backup "$BACKUP_DIR" || exit 1
}

# ---- Run ---------------------------------------------------------------------------------------

TMP=""
trap '[ -n "$TMP" ] && rm -rf "$TMP"' EXIT

case "$MODE" in
	release)
		ensure_tools "${ANDROID_TOOLS[@]}"
		build_android
		;;
	linux)
		needs_linux "The Linux app"
		ensure_tools "${LINUX_TOOLS[@]}"
		build_linux
		;;
	all)
		needs_linux "Building both"
		ensure_tools "${ANDROID_TOOLS[@]}" "${LINUX_TOOLS[@]}"
		build_android
		echo
		build_linux
		;;
	publish)
		needs_linux "Publishing"
		ensure_tools "${ANDROID_TOOLS[@]}" "${LINUX_TOOLS[@]}"
		ensure_gh
		build_android
		echo
		build_linux
		echo
		publish_release
		;;
	upload-only)
		needs_linux "Publishing"
		ensure_tools "${ANDROID_TOOLS[@]}" "${LINUX_TOOLS[@]}"
		publish_release
		;;
	debug)
		ensure_tools "${ANDROID_TOOLS[@]}"
		build_android --debug
		;;
	install)
		ensure_tools "${ANDROID_TOOLS[@]}"
		bash "$ROOT/scripts/android.sh" --install-only
		;;
	test)
		ensure_tools "${ANDROID_TOOLS[@]}"
		bash "$ROOT/scripts/android.sh" --test
		if [ "$LINUX_OK" = 1 ]; then
			ensure_tools "${LINUX_TOOLS[@]}"
			bash "$ROOT/scripts/linux.sh" --test
		else
			warn "The Linux app's tests run on Linux only; they were skipped."
		fi
		;;
	audit-zip)
		audit_zip
		;;
	dephashes)
		needs_linux "Recording the libraries of both apps"
		ensure_tools "${ANDROID_TOOLS[@]}" "${LINUX_TOOLS[@]}"
		build_android --write-dependency-hashes
		echo
		build_linux --write-dependency-hashes
		say "Recorded in gradle/verification-metadata.xml. Push it with the source; run this again after changing a library version."
		;;
	pushsource)
		push_source
		;;
	setversion)
		set_version
		;;
	createkeys)
		ensure_tools "${ANDROID_TOOLS[@]}"
		bash "$ROOT/scripts/android.sh" --create-keys
		if [ "$LINUX_OK" = 1 ]; then
			ensure_tools "${LINUX_TOOLS[@]}"
			bash "$ROOT/scripts/linux.sh" --create-key
		fi
		;;
	showkeys)
		ensure_tools "${ANDROID_TOOLS[@]}"
		bash "$ROOT/scripts/android.sh" --show-keys
		if [ "$LINUX_OK" = 1 ]; then
			ensure_tools "${LINUX_TOOLS[@]}"
			bash "$ROOT/scripts/linux.sh" --show-key
		fi
		;;
	sharedkey)
		ensure_tools "${ANDROID_TOOLS[@]}"
		bash "$ROOT/scripts/android.sh" --use-shared-key
		;;
	newlinuxkey)
		needs_linux "The Linux signing key"
		ensure_tools "${LINUX_TOOLS[@]}"
		bash "$ROOT/scripts/linux.sh" --new-key
		;;
	backupkeys)
		backup_keys
		;;
	clean)
		clean_outputs
		;;
	*)
		die "Unknown action $MODE"
		;;
esac
