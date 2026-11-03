#!/usr/bin/env bash

set -euo pipefail

usage() {
	cat <<'USAGE'
./BUILD.sh              asks: Android app, Linux app (Flatpak) or both
./BUILD.sh --android    signed Android APK
./BUILD.sh --linux      Linux Flatpak bundle
./BUILD.sh --all        both, Android first

Options for either:
  --clean      wipe the build output first (what's in dist/ stays)
  --install    install what was built: the APK on the connected device (adb), the Flatpak for this user
  --verbose    show the full output, not just the log files (build.log, build-linux.log)
For the Android app:
  --debug      debug APK instead of the signed release one
USAGE
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

missing=()
for need in gradle.properties settings.gradle.kts gradlew gradle/wrapper/gradle-wrapper.properties \
	gradle/libs.versions.toml app/build.gradle.kts core/build.gradle.kts desktop/build.gradle.kts linux/flatpak.yml \
	scripts/android.sh scripts/linux.sh; do
	[ -e "$ROOT/$need" ] || missing+=("$need")
done
if [ "${#missing[@]}" -gt 0 ]; then
	echo "ERROR: $ROOT isn't the whole project; it has no ${missing[*]}." >&2
	echo "Unzip the full project zip into an empty folder and run BUILD.sh from there." >&2
	exit 1
fi
chmod +x "$ROOT/gradlew" "$ROOT/scripts/android.sh" "$ROOT/scripts/linux.sh" 2> /dev/null || true

TARGET=""
COMMON=()
DEBUG=0
pick() {
	if [ -n "$TARGET" ] && [ "$TARGET" != "$1" ]; then
		echo "Choose one of --android, --linux and --all." >&2
		exit 2
	fi
	TARGET="$1"
}
for arg in "$@"; do
	case "$arg" in
		--android) pick android ;;
		--linux) pick linux ;;
		--all) pick all ;;
		--clean|--install|--verbose) COMMON+=("$arg") ;;
		--debug) DEBUG=1 ;;
		-h|--help) usage; exit 0 ;;
		*) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
	esac
done

LINUX_OK=0
if [ "$(uname -s)" = Linux ]; then
	LINUX_OK=1
fi

if [ -z "$TARGET" ]; then
	if [ ! -t 0 ]; then
		echo "Say what to build: --android, --linux or --all (try --help)." >&2
		exit 2
	fi
	echo "What do you want to build?"
	echo "  1) Android app (APK)"
	if [ "$LINUX_OK" = 1 ]; then
		echo "  2) Linux app (Flatpak)"
		echo "  3) Both"
	fi
	while [ -z "$TARGET" ]; do
		read -r -p "Choice: " answer || exit 1
		case "$answer" in
			1) TARGET=android ;;
			2) [ "$LINUX_OK" = 1 ] && TARGET=linux ;;
			3) [ "$LINUX_OK" = 1 ] && TARGET=all ;;
		esac
		[ -n "$TARGET" ] || echo "Type the number of one of the choices above."
	done
fi

if [ "$TARGET" != android ] && [ "$LINUX_OK" != 1 ]; then
	echo "The Linux app is built on Linux; here only --android works." >&2
	exit 1
fi
if [ "$DEBUG" = 1 ] && [ "$TARGET" = linux ]; then
	echo "--debug is for the Android app; the Linux app has no debug build." >&2
	exit 2
fi

package_for() {
	case "$1:$2" in
		python3:pacman) echo python ;;
		sha256sum:*) echo coreutils ;;
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
	if [ ! -t 0 ]; then
		echo "Install them with:  $cmd" >&2
		echo "then run BUILD.sh again." >&2
		exit 1
	fi
	if [ "$(id -u)" != 0 ] && [ "$manager" != brew ] && ! command -v sudo > /dev/null 2>&1; then
		echo "Install them as root with:  ${cmd//sudo /}" >&2
		exit 1
	fi
	echo "This installs them:  $cmd"
	local answer
	read -r -p "Install now? [Y/n] " answer || exit 1
	case "$answer" in
		''|y|Y|yes|YES) ;;
		*) echo "Not installed. Install them yourself, then run BUILD.sh again." >&2; exit 1 ;;
	esac
	if ! bash -c "$cmd"; then
		echo "Installing failed. Check the messages above (on Arch, an out-of-date system may first need" >&2
		echo "sudo pacman -Syu), then run BUILD.sh again." >&2
		exit 1
	fi
	for tool in "${missing[@]}"; do
		command -v "$tool" > /dev/null 2>&1 || { echo "'$tool' still isn't there after installing." >&2; exit 1; }
	done
}

TOOLS=(curl tar python3)
if [ "$TARGET" = android ] || [ "$TARGET" = all ]; then
	TOOLS+=(unzip)
fi
if [ "$TARGET" = linux ] || [ "$TARGET" = all ]; then
	TOOLS+=(sha256sum flatpak)
fi
ensure_tools "${TOOLS[@]}"

ANDROID_ARGS=(${COMMON[@]+"${COMMON[@]}"})
LINUX_ARGS=(${COMMON[@]+"${COMMON[@]}"})
if { [ "$TARGET" = linux ] || [ "$TARGET" = all ]; } && [ -t 0 ] \
		&& [[ " ${COMMON[*]-} " != *" --install "* ]]; then
	read -r -p "Install the Linux app for your user once it's built? [Y/n] " answer || exit 1
	case "$answer" in
		''|y|Y|yes|YES) LINUX_ARGS+=(--install) ;;
	esac
fi
if [ "$DEBUG" = 1 ]; then
	ANDROID_ARGS+=(--debug)
fi

if [ "$TARGET" = android ] || [ "$TARGET" = all ]; then
	echo "== Android app"
	bash "$ROOT/scripts/android.sh" ${ANDROID_ARGS[@]+"${ANDROID_ARGS[@]}"}
fi
if [ "$TARGET" = linux ] || [ "$TARGET" = all ]; then
	if [ "$TARGET" = all ]; then
		echo
	fi
	echo "== Linux app"
	bash "$ROOT/scripts/linux.sh" ${LINUX_ARGS[@]+"${LINUX_ARGS[@]}"}
fi
