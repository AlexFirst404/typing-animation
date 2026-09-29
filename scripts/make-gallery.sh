#!/usr/bin/env bash
# Regenerates the Modrinth gallery media in modrinth/gallery/ from the dev showcase of the 26.3 client.
#   bash scripts/make-gallery.sh           record (a Minecraft window opens for about 2 minutes), then build the media
#   bash scripts/make-gallery.sh --reuse   only rebuild the media from the frames of the last recording
# The showcase (mc/26.3 Showcase, TYPINGANIMATION_SHOWCASE=1) creates a superflat world, types into the real chat and
# saves every frame of its clips as PNG to $SHOWCASE_OUT (default: <temp>/typinganimation-showcase, never inside the
# repository); tools/MakeGif.java turns the clips into GIFs. The crops below assume the frame sizes the showcase
# checks (800x480 for chat/command, 1280x720 for the rest; a HiDPI desktop scale would change them).
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="${SHOWCASE_TARGET:-26.3-neoforge}"
TMP_ROOT="$(cd "${TMPDIR:-${TEMP:-/tmp}}" && pwd)"
OUT="${SHOWCASE_OUT:-$TMP_ROOT/typinganimation-showcase}"
GALLERY="$ROOT/modrinth/gallery"

# Windows paths for the JVM when running under Git Bash / MSYS
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s\n' "$1"; fi; }

if [ "${1:-}" != "--reuse" ]; then
  mkdir -p "$OUT"
  TYPINGANIMATION_SHOWCASE_OUT="$(native "$OUT")" bash "$ROOT/scripts/run-target.sh" "$TARGET" showcase
fi

MANIFEST="$OUT/manifest.properties"
[ -f "$MANIFEST" ] || { echo "no showcase recording in $OUT (run without --reuse)"; exit 1; }
prop() { sed -n "s/^$1=//p" "$MANIFEST" | tr -d '\r'; }
for clip in chat:800x480 command:800x480 world:1280x720 styles:1280x720; do
  name="${clip%%:*}"; size="${clip#*:}"
  if [ "$(prop "clip.$name.size")" != "$size" ]; then
    echo "clip $name was recorded at '$(prop "clip.$name.size")', the crops expect $size"; exit 1
  fi
done

mkdir -p "$GALLERY"
gif() { # <clip> <out.gif> <MakeGif args...>
  local clip="$1" file="$2"; shift 2
  java "$ROOT/tools/MakeGif.java" --in "$(native "$OUT/$clip")" --out "$(native "$GALLERY/$file")" "$@"
}
# chat input, recent chat lines and the hotbar (bottom of the 400x240 GUI at scale 2)
gif chat chat-typing.gif --crop 0,248,800,232 --hold-last 300
# the same area plus the room the command suggestions need
gif command command-highlighting.gif --crop 0,200,800,280 --hold-last 300
# close-up of the input line left of the hotbar (1280x720 frame, GUI scale 2), shown at 2x
gif styles styles.gif --crop 0,620,400,100 --scale 2 --hold-last 300

for still in chat-in-world options-button config-screen-en config-screen-ru; do
  cp "$OUT/stills/$still.png" "$GALLERY/$still.png"
done
ls -l "$GALLERY"
