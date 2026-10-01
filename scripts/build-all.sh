#!/usr/bin/env bash
# Build every target (or the ones given as arguments) and collect release jars in dist/.
#   bash scripts/build-all.sh                 # all targets
#   bash scripts/build-all.sh 26.3-neoforge   # selected targets
#   SELFTEST=1 bash scripts/build-all.sh      # also run the in-game self-test for each target (opens game windows)
# JDK locations can be overridden with JDK17_HOME / JDK21_HOME / JDK25_HOME (see scripts/run-target.sh).
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 2

if [ "$#" -gt 0 ]; then
  TARGETS=("$@")
else
  TARGETS=()
  for d in targets/*/; do
    [ -f "$d/target.properties" ] && TARGETS+=("$(basename "$d")")
  done
fi

MOD_VERSION=$(sed -n "s/^def MOD_VERSION = '\([^']*\)'.*/\1/p" scripts/target-common.gradle | tr -d '\r' | head -1)
[ -n "$MOD_VERSION" ] || { echo "cannot read MOD_VERSION from scripts/target-common.gradle"; exit 2; }

mkdir -p dist
ok=(); failed=()
for t in "${TARGETS[@]}"; do
  echo "=== $t: build ==="
  if ! bash scripts/run-target.sh "$t" build > "dist/.build-$t.log" 2>&1; then
    echo "    BUILD FAILED (log: dist/.build-$t.log)"
    failed+=("$t (build)"); continue
  fi
  glob=$(grep '^jar.glob=' "targets/$t/target.properties" | cut -d= -f2- | tr -d '\r')
  # build/libs keeps jars of earlier mod versions, so pick the one for the current MOD_VERSION.
  jar=$(ls targets/$t/$glob 2>/dev/null | grep -v -- '-sources' | grep -F -- "/typinganimation-$MOD_VERSION+" | head -1)
  if [ -z "$jar" ]; then
    echo "    NO JAR matching $glob for version $MOD_VERSION"; failed+=("$t (no jar)"); continue
  fi
  cp -f "$jar" dist/
  echo "    -> dist/$(basename "$jar")"
  if [ "${SELFTEST:-0}" = "1" ]; then
    echo "=== $t: selftest ==="
    if bash scripts/run-target.sh "$t" selftest > "dist/.selftest-$t.log" 2>&1; then
      echo "    SELFTEST PASS"
    else
      echo "    SELFTEST FAILED (log: dist/.selftest-$t.log)"; failed+=("$t (selftest)"); continue
    fi
  fi
  ok+=("$t")
done

echo
echo "OK (${#ok[@]}): ${ok[*]:-}"
if [ "${#failed[@]}" -gt 0 ]; then
  echo "FAILED (${#failed[@]}): ${failed[*]}"
  exit 1
fi
