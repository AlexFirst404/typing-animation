#!/usr/bin/env bash
# Run Gradle for one target with the right Gradle JVM (from target.properties).
#   scripts/run-target.sh <target> build            -> ./gradlew build --no-daemon
#   scripts/run-target.sh <target> selftest [args]  -> dev client with the self-test, PASS/FAIL summary
#   scripts/run-target.sh <target> showcase [args]  -> dev client recording the gallery media (26.3 targets only)
#   scripts/run-target.sh <target> <any gradle args>
# <target> is a directory name under targets/, e.g. 26.3-neoforge.
#
# Gradle JVM: target.properties "gradle.jdk" names the major version N (17 | 21 | 25). JAVA_HOME is taken from
# JDK<N>_HOME when that is set, else from JAVA_HOME when it already is a JDK N, else from the first JDK N found in
# the usual install locations (Program Files vendors, Gradle-provisioned ~/.gradle/jdks, ~/.jdks, /usr/lib/jvm,
# macOS JavaVirtualMachines). A candidate counts only if its "release" file says JAVA_VERSION N.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
T="${1:?target name required}"; shift
DIR="$ROOT/targets/$T"
[ -f "$DIR/target.properties" ] || { echo "no such target: $T"; exit 2; }

GJ=$(grep '^gradle.jdk=' "$DIR/target.properties" | cut -d= -f2 | tr -d '\r ')
case "$GJ" in
  ''|*[!0-9]*) echo "$T: target.properties has no valid gradle.jdk (got '$GJ')"; exit 2 ;;
esac

# Major version of the JDK at $1 (from its release file), or nothing.
jdk_major() {
  [ -f "$1/release" ] || return 0
  sed -n 's/^JAVA_VERSION="\([0-9][0-9]*\).*/\1/p' "$1/release" | tr -d '\r' | head -1
}

has_java() {
  [ -x "$1/bin/java" ] || [ -x "$1/bin/java.exe" ]
}

# Prints the JDK home for major version $1, or fails.
find_jdk() {
  local v="$1" var="JDK${1}_HOME" c
  if [ -n "${!var:-}" ]; then
    if has_java "${!var}"; then printf '%s\n' "${!var}"; return 0; fi
    echo "$var=${!var} has no bin/java" >&2
    return 1
  fi
  if [ -n "${JAVA_HOME:-}" ] && has_java "$JAVA_HOME" && [ "$(jdk_major "$JAVA_HOME")" = "$v" ]; then
    printf '%s\n' "$JAVA_HOME"; return 0
  fi
  for c in \
      "/c/Program Files/Java/jdk-$v"* \
      "/c/Program Files/Eclipse Adoptium/jdk-$v"* \
      "/c/Program Files/Microsoft/jdk-$v"* \
      "/c/Program Files/Zulu/zulu-$v"* \
      "/c/Program Files/Amazon Corretto/jdk$v"* \
      "$HOME/.gradle/jdks/"*"-$v-"* \
      "$HOME/.jdks/"*"-$v"* \
      /usr/lib/jvm/*"-$v"* /usr/lib/jvm/*"-$v-"* \
      /Library/Java/JavaVirtualMachines/*"-$v"*/Contents/Home \
      /Library/Java/JavaVirtualMachines/*"-$v."*/Contents/Home; do
    [ -d "$c" ] || continue
    if has_java "$c" && [ "$(jdk_major "$c")" = "$v" ]; then
      printf '%s\n' "$c"; return 0
    fi
  done
  return 1
}

if ! JH=$(find_jdk "$GJ"); then
  echo "$T needs a JDK $GJ as the Gradle JVM (target.properties gradle.jdk=$GJ), but none was found."
  echo "Install one or point JDK${GJ}_HOME at it, e.g.  JDK${GJ}_HOME=/path/to/jdk-$GJ bash scripts/run-target.sh $T build"
  exit 2
fi
export JAVA_HOME="$JH"
cd "$DIR" || exit 2

if [ "${1:-}" = "selftest" ]; then
  shift
  OUT="$DIR/build/selftest"
  mkdir -p "$OUT"
  LOG="$OUT/selftest.log"
  export TYPINGANIMATION_SELFTEST=1
  export TYPINGANIMATION_SELFTEST_SCREENSHOTS="${TYPINGANIMATION_SELFTEST_SCREENSHOTS:-1}"
  timeout "${SELFTEST_TIMEOUT:-1200}" ./gradlew runClient --no-daemon "$@" > "$LOG" 2>&1
  rc=$?
  grep -h "\[typinganimation\] SELFTEST SCREENSHOT" "$LOG" | sed 's/.*SELFTEST SCREENSHOT/screenshot:/' | sort -u
  if grep -q "\[typinganimation\] SELFTEST PASS" "$LOG"; then
    echo "SELFTEST PASS ($T)"
    exit 0
  fi
  grep -n -A40 "\[typinganimation\] SELFTEST FAIL" "$LOG" | head -80
  grep -n -i -E "mixin.*(error|fail)|InvalidInjection|Exception in|Crash report|---- Minecraft Crash Report" "$LOG" | head -40
  echo "SELFTEST FAILED ($T) gradle-rc=$rc log=$LOG"
  exit 1
fi

if [ "${1:-}" = "showcase" ]; then
  # Dev showcase recorder (mc/26.3 only): TYPINGANIMATION_SHOWCASE=1, frames to TYPINGANIMATION_SHOWCASE_OUT
  # (default: the system temp dir). Used by scripts/make-gallery.sh.
  shift
  if ! grep -q '^group=26\.3\s*$' "$DIR/target.properties"; then
    echo "$T: the showcase exists only in mc/26.3 (targets 26.3-*)"
    exit 2
  fi
  OUT="$DIR/build/showcase"
  mkdir -p "$OUT"
  LOG="$OUT/showcase.log"
  unset TYPINGANIMATION_SELFTEST
  export TYPINGANIMATION_SHOWCASE=1
  timeout "${SHOWCASE_TIMEOUT:-1800}" ./gradlew runClient --no-daemon "$@" > "$LOG" 2>&1
  rc=$?
  grep -h "\[typinganimation\] SHOWCASE" "$LOG" | sed 's/.*\[typinganimation\] /  /'
  if grep -q "\[typinganimation\] SHOWCASE DONE" "$LOG"; then
    echo "SHOWCASE DONE ($T)"
    exit 0
  fi
  grep -n -A40 "\[typinganimation\] SHOWCASE FAIL" "$LOG" | head -80
  grep -n -i -E "mixin.*(error|fail)|InvalidInjection|Exception in|Crash report|---- Minecraft Crash Report" "$LOG" | head -40
  echo "SHOWCASE FAILED ($T) gradle-rc=$rc log=$LOG"
  exit 1
fi

exec ./gradlew "$@" --no-daemon
