#!/bin/bash
APP=/app/lib/@SLUG@
if [ -f /app/jre/lib/libjsig.so ]; then
	export LD_PRELOAD="/app/jre/lib/libjsig.so${LD_PRELOAD:+:$LD_PRELOAD}"
fi
export GST_DEBUG="${GST_DEBUG:-2}"
export GST_DEBUG_NO_COLOR=1
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
CRASH_DIR="$DATA/crash"
LOG_DIR="$DATA/logs"
umask 077
mkdir -p "$CRASH_DIR" "$LOG_DIR"
LOG="$LOG_DIR/run-$(date +%Y%m%d-%H%M%S)-$$.log"
MAX_LOG_BYTES=20000000

mapfile -t OLD_LOGS < <(find "$LOG_DIR" -maxdepth 1 -name 'run-*.log' -printf '%T@ %p\n' | sort -rn | tail -n +5 \
	| cut -d' ' -f2-)
if [ "${#OLD_LOGS[@]}" -gt 0 ]; then
	rm -f -- "${OLD_LOGS[@]}"
fi

mapfile -t JARS < <(find "$APP/app" -maxdepth 1 -name '*.jar' ! -type d | LC_ALL=C sort)
CLASS_PATH="$(IFS=:; echo "${JARS[*]}")"

AOT_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/aot"
AOT=""
AOT_ARGS=()
if mkdir -p "$AOT_DIR" 2> /dev/null; then
	key="$(printf '%s\n' "@VERSION@" "$(cat /app/jre/release 2> /dev/null)" "${JARS[@]}" | cksum | cut -d' ' -f1)"
	AOT="$AOT_DIR/app-@VERSION@-$key.aot"
	find "$AOT_DIR" -maxdepth 1 -type f -name 'app-*.aot' ! -name "${AOT##*/}" -delete 2> /dev/null
	find "$AOT_DIR" -maxdepth 1 -type f -name 'app-*.aot.*.tmp' -mmin +1440 -delete 2> /dev/null
	if [ -s "$AOT" ]; then
		AOT_ARGS=(-XX:AOTCache="$AOT")
	else
		AOT_NEW="$AOT.$$.tmp"
		AOT_ARGS=(-XX:AOTCacheOutput="$AOT_NEW")
	fi
fi

JAVA=(/app/jre/bin/java
	--enable-native-access=ALL-UNNAMED
	-Djava.awt.headless=true
	-XX:ErrorFile="$CRASH_DIR/hs_err_pid%p.log"
	-XX:-CreateCoredumpOnCrash
	-XX:+UseCompactObjectHeaders
	-XX:G1PeriodicGCInterval=60000
	-Dlibtorrent4j.jni.path="$APP/native/libtorrent4j.so"
	${AOT_ARGS[@]+"${AOT_ARGS[@]}"}
	-cp "$CLASS_PATH"
	app.niixfliix.desktop.Main "$@")
STARTED=$SECONDS

if [ -t 2 ]; then
	"${JAVA[@]}" 2>&1 | tee -p >(head -c "$MAX_LOG_BYTES" > "$LOG")
	status=${PIPESTATUS[0]}
else
	"${JAVA[@]}" 2>&1 | head -c "$MAX_LOG_BYTES" > "$LOG"
	status=${PIPESTATUS[0]}
fi
wait

if [ -n "$AOT" ]; then
	if [ -n "${AOT_NEW:-}" ]; then
		if [ "$status" -eq 0 ] && [ $((SECONDS - STARTED)) -ge 20 ] && [ -s "$AOT_NEW" ]; then
			mv -f "$AOT_NEW" "$AOT"
		else
			rm -f "$AOT_NEW"
		fi
	elif grep -q '\]\[error *\]\[aot' "$LOG" 2> /dev/null; then
		rm -f "$AOT"
	fi
fi

case "$status" in
	0|129|130|143) exit "$status" ;;
esac

if [ "$status" -gt 128 ]; then
	signal=$((status - 128))
	name="$(kill -l "$signal" 2> /dev/null || echo "$signal")"
	cause="Ended by signal $signal (SIG$name)"
	case "$signal" in
		9) cause="$cause: the system killed it, most often because memory ran out" ;;
		6) cause="$cause: a library gave up (an assertion or an error it couldn't handle)" ;;
		11|7) cause="$cause: a library read memory it shouldn't have" ;;
	esac
else
	cause="Ended with exit code $status"
fi
{
	echo "$cause"
	echo
	echo "Last output:"
	tail -n 80 "$LOG"
} > "$CRASH_DIR/exit-$(date +%s)-$$.log"
exit "$status"
