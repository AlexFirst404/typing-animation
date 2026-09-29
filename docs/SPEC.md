# Typing Animation — engineering spec (source of truth for all contributors/agents)

Client-side Minecraft mod: characters typed into any text field (chat, commands, anvil, search,
world/server names, sign-less `EditBox` fields, and multi-line fields where feasible) appear with a
smooth animation; deleted characters animate out; text re-flows and the caret glide smoothly.
Inspired by the concept of "Animated Typing" (Modrinth, Fabric only, 1.21.4–1.21.11) but **this is an
original, clean-room implementation**. That mod is *All Rights Reserved*: never copy, paraphrase or
port its code, names of its classes, or its assets. Only the general idea ("animate newly typed chars")
is shared.

- Mod id: `typinganimation` · Name: `Typing Animation` · Version: `1.0.0` · License: MIT
- Java package root: `dev.typinganimation`
- Client only. Must never be required on servers; must never crash the game (see "Safety").
- No dependency on Fabric API. Mod Menu integration on Fabric is optional (compileOnly).

## 1. Target matrix

Each row is a *standalone* Gradle project in `targets/<anchor>-<loader>/` compiled against the anchor
Minecraft version and declaring the listed supported range in its metadata.
Loader versions below are "latest as of 2026-09-29"; bumping to a newer patch is fine.

| Anchor MC | Declared range     | Java | NeoForge            | Forge            | Fabric (loader 0.19.5) |
|-----------|--------------------|------|---------------------|------------------|------------------------|
| 1.20.1    | 1.20 – 1.20.1      | 17   | — (Forge jar also runs on NeoForge 1.20.1) | 47.4.x | yes |
| 1.21.1    | 1.21 – 1.21.1      | 21   | 21.1.252            | 52.1.x           | yes |
| 1.21.4    | 1.21.4             | 21   | 21.4.158            | 54.1.x           | yes |
| 1.21.5    | 1.21.5             | 21   | 21.5.98             | 55.1.x           | yes |
| 1.21.8    | 1.21.6 – 1.21.8    | 21   | 21.8.54             | 58.1.x           | yes |
| 1.21.10   | 1.21.9 – 1.21.10   | 21   | 21.10.64            | 60.1.x           | yes |
| 1.21.11   | 1.21.11            | 21   | 21.11.45            | 61.2.x           | yes |
| 26.1.2    | 26.1 – 26.1.2      | 25   | 26.1.2.112          | 64.1.x           | yes |
| 26.2      | 26.2               | 25   | 26.2.0.88           | 65.1.x           | yes |
| 26.3      | 26.3               | 25   | 26.3.0.34-beta (**primary target**) | 66.0.x | yes |

A declared range wider than the anchor is allowed only if the used APIs are identical across it;
if in doubt, narrow the range. (Forge ships separate builds per patch; its dependency range must
still admit every MC version in the declared range, or be narrowed.)

Declared ranges in the metadata (all derived from `target.properties` `mc.range` by `scripts/target-common.gradle`):
Forge/NeoForge use closed Maven ranges (`[1.21.6,1.21.8]`, `[26.3]`), never half-open ones such as `[1.21.6,1.21.9)`,
which would admit the next version's pre-releases and release candidates; Fabric uses `>=a <=b` or the exact version.
Loader floors (in each target's `gradle.properties`):
- NeoForge `neo_version_range` = the first build line of the declared range, with its betas: `[21.0.0-beta,)`,
  `[21.4.0-beta,)`, `[21.5.0-beta,)`, `[21.6.0-beta,)`, `[21.9.0-beta,)`, `[21.11.0-beta,)`, `[26.1.0-beta,)`,
  `[26.2.0-beta,)`, `[26.3.0-beta,)` (never just the build the target compiles against).
- Forge `forge_version_range` (also the javafml `loaderVersion`) = the Forge major of the lowest declared MC version.
- Fabric `fabricloader_dependency` = the stable loader that Fabric's announcement of that Minecraft line told players
  to install: 1.20–1.20.1 `>=0.14.19`, 1.21–1.21.1 `>=0.15.11`, 1.21.4 `>=0.16.9`, 1.21.5 `>=0.16.10`,
  1.21.6–1.21.8 `>=0.16.14`, 1.21.9–1.21.10 `>=0.17.2`, 1.21.11 `>=0.18.1`, 26.1–26.1.2 `>=0.18.4`, 26.2 `>=0.19.3`,
  26.3 `>=0.19.5` (all of them bundle a Mixin that knows the `compatibilityLevel` we use).

## 2. Repository layout

```
docs/SPEC.md                    this file
core/                           pure Java (no Minecraft classes), Java 17, standalone Gradle project
  src/main/java/dev/typinganimation/core/...
  src/test/java/...             JUnit 5 tests (run with core's own gradle build)
common-resources/               resources shared by every target
  assets/typinganimation/lang/en_us.json, ru_ru.json
  assets/typinganimation/icon.png
mc/<group>/                     Minecraft-facing code shared by all loaders of one "API group"
  src/main/java/dev/typinganimation/mc/...        renderer, config screen, self-test, init
  src/main/java/dev/typinganimation/mixin/...     mixins (Mojang names)
  src/main/resources/typinganimation.mixins.json
targets/<anchor>-<loader>/      standalone Gradle build (own wrapper, settings.gradle, gradle.properties)
  target.properties             metadata used by scripts and by the build (see §7)
  src/main/java/dev/typinganimation/<loader>/...  thin loader glue only
  src/main/resources/...        loader metadata templates (fabric.mod.json / META-INF/mods.toml + pack.mcmeta;
  src/main/templates/...        NeoForge: META-INF/neoforge.mods.toml, expanded by generateModMetadata as in the MDK)
scripts/                        run-target.sh, build-all.sh / build-all.ps1, target-common.gradle
.research/                      local API notes (gitignored; may contain decompiled MC excerpts)
dist/                           collected release jars (gitignored)
.gitattributes                  LF for gradlew/*.sh, CRLF for *.bat, for every Gradle project in the repo
```

Every `targets/*/build.gradle` applies `scripts/target-common.gradle` right after its `plugins` block. That
script is the single source of what all targets share: it reads `target.properties` (`group`, `mc.range`, `loader`,
`java.release`), holds the mod id/name/version/license/author, takes the description from the English lang key
`typinganimation.description`, sets the release jar name, the Java toolchain/`--release`, UTF-8, the standard
manifest attributes and the LICENSE in the jar, and adds the shared source dirs:
```groovy
sourceSets.main.java.srcDirs += ['../../core/src/main/java', '../../mc/<group>/src/main/java']
sourceSets.main.resources.srcDirs += ['../../common-resources', '../../mc/<group>/src/main/resources']
```
Loader metadata templates are expanded from its `typing.expand` map (plus the target's own loader range), so no
metadata value is written twice. Never duplicate a resource path across source dirs (Gradle duplicate-entry errors).
If a loader needs a different mixin json (e.g. a `refmap` key), generate/patch it in that target's build.

"API group" = set of MC versions whose Mojang-mapped client API used by us is identical, so one
`mc/<group>` source tree compiles for all of them and for every loader. All shared code uses
**Mojang (official) names**: Fabric targets use `loom.officialMojangMappings()` (or no mappings on
unobfuscated 26.x), NeoForge/Forge use official names natively.

## 3. Build rules (Windows host)

- Run Gradle with a JDK of the major version named by the target's `target.properties` `gradle.jdk` as
  `JAVA_HOME`: `21` for the NeoForge (MDG) and Forge (FG6/FG7) targets, `25` for all Fabric targets (Loom 1.18.2
  refuses to run on an older Gradle JVM). `scripts/run-target.sh` (and `build-all.ps1`) pick it: `JDK<N>_HOME` if
  set, else `JAVA_HOME` if it already is a JDK N, else the first JDK N in the usual install locations (vendor folders
  under Program Files, Gradle-provisioned `~/.gradle/jdks`, `~/.jdks`, `/usr/lib/jvm`, macOS JavaVirtualMachines),
  checked through the JDK's `release` file. No machine-specific path is needed anywhere.
- Compilation and dev runs use the Gradle toolchain of `java.release` (17 / 21 / 25); every `settings.gradle`
  applies the foojay resolver so a missing toolchain is provisioned.
- Always pass `--no-daemon` (many builds run in parallel on this machine; lingering daemons exhaust RAM).
  Keep `org.gradle.jvmargs` ≤ `-Xmx3G`.
- Use the official template/MDK conventions of each loader+version (NeoForge: ModDevGradle; Forge: the
  plugin its official MDK uses; Fabric: Loom version the official template uses for that MC version), with every
  plugin pinned to an exact version (no `+`, ranges or `-SNAPSHOT`): Loom 1.18.2, ModDevGradle 2.0.147,
  ForgeGradle 7.0.40 (6.0.54 + MixinGradle 0.7.38 for 1.20.1). No MDK leftovers the mod does not use (event-bus
  validator, logging markers, IDE plugins, sources jars).
- Output jar name: `typinganimation-1.0.0+<rangeLabel>-<loader>.jar`, rangeLabel like `1.21.6-1.21.8` or `26.3`
  (the only jar in `build/libs`; Fabric's remapJar, Forge 1.20.1's reobfJar and the plain jar task all produce it).
- Mod metadata: id/name/version/license MIT/authors `alext`/description (see lang `typinganimation.description`
  in English)/icon `assets/typinganimation/icon.png`; client-only markers where the loader supports them
  (Fabric `"environment": "client"`; NeoForge `@Mod(dist = Dist.CLIENT)` + side `CLIENT` deps; Forge
  display-test IGNORE_SERVER_VERSION / client-dist guard). One template per loader:
  Fabric: `"mixins": [{"config": ..., "environment": "client"}]`, `"modmenu"` entrypoint
  `dev.typinganimation.fabric.ModMenuIntegration`, `"suggests": {"modmenu": "*"}`;
  NeoForge: `modLoader="javafml"` + `loaderVersion="[1,)"` everywhere (mandatory up to FML 6 = NeoForge 21.4,
  optional later), no `displayTest` (not a NeoForge key), `logoFile` up to 1.21.11 / `iconFile` on 26.x;
  Forge: `loaderVersion` = `forge_version_range`, `clientSideOnly=true`, `displayTest="IGNORE_SERVER_VERSION"`,
  mixins via the `MixinConfigs` manifest attribute, client glue class `ForgeClient`; `pack.mcmeta` description
  "Typing Animation resources". Jar manifests carry the same Specification-/Implementation-* attributes.

## 4. Core module API (pure Java 17; may use Gson and SLF4J which every MC version ships)

Package `dev.typinganimation.core`.

```java
public final class TypingAnimationMod {           // constants
  public static final String MOD_ID = "typinganimation", MOD_NAME = "Typing Animation";
  public static final org.slf4j.Logger LOGGER;
}

public enum Easing {                               // apply(0)==0 and apply(1)==1 exactly; input clamped to [0,1]
  AUTO, LINEAR, SINE_OUT, QUAD_OUT, CUBIC_OUT, QUART_OUT, EXPO_OUT, BACK_OUT, ELASTIC_OUT, BOUNCE_OUT, SINE_IN_OUT;
  public float apply(float t);                     // AUTO behaves like CUBIC_OUT when applied directly
  public Easing resolve(Easing styleDefault);      // AUTO -> styleDefault, else this
  public String translationKey();                  // "typinganimation.easing.<lower_name>"
}

public enum AppearStyle {                          // how a newly typed char enters
  NONE, FADE, SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, POP, GROW, STRETCH, DROP, SPIN, WAVE, SCRAMBLE;
  public Easing defaultEasing();
  /** t = linear progress (may be <0 for not-yet-revealed staggered chars -> fully hidden, alpha 0 unless NONE),
   *  e = eased progress, intensity = config multiplier, seed = per-char seed, nowMs for SCRAMBLE glyph cycling.
   *  Writes into out (after out.reset()). At t>=1 MUST leave out as identity. */
  public void apply(float t, float e, float intensity, int seed, long nowMs, CharTransform out);
  public String translationKey();                  // "typinganimation.appear.<lower_name>"
}

public enum RemoveStyle {                          // how a deleted char leaves (ghost)
  NONE, FADE, FALL, SHRINK, FLY_UP, SCATTER;
  public Easing defaultEasing();
  /** t = linear progress 0..1 of the exit; at t>=1 the ghost is gone (alpha 0). */
  public void apply(float t, float e, float intensity, int seed, CharTransform out);
  public String translationKey();                  // "typinganimation.remove.<lower_name>"
}

public final class CharTransform {                 // mutable, reused (no per-char allocation)
  public float dx, dy;                             // GUI px offset
  public float scaleX = 1, scaleY = 1;
  public float rotation;                           // radians, positive = clockwise on screen
  public float alpha = 1;                          // 0..1 multiplier of the text color alpha
  public float pivotX = 0.5f, pivotY = 0.5f;       // pivot as fraction of glyph cell (width x 9px line)
  public int glyph = -1;                           // codepoint override (SCRAMBLE), -1 = none
  public void reset();                             // identity
  public boolean isIdentity();
}
```
Style semantics (distances assume the 9 px MC line height, scaled by `intensity`):
FADE alpha=e · SLIDE_UP dy=(1-e)*7 · SLIDE_DOWN dy=-(1-e)*7 · SLIDE_LEFT dx=(1-e)*8 (enters from the right) ·
POP scale=e around centre (default BACK_OUT for overshoot) · GROW scaleY=e pivot bottom · STRETCH scaleX=e pivot left ·
DROP dy=-(1-e)*9 (default BOUNCE_OUT) · SPIN rotation=(1-e)*-π/2 & scale=e around centre ·
WAVE dy=sin(t*2.5π)*(1-e)*3 · SCRAMBLE random glyph (changes every ~45 ms, deterministic from seed+time)
until t≥0.65 then real glyph. All styles except NONE also fade alpha in (alpha ≈ min(1, e*1.6) or similar)
and are fully hidden (alpha 0) while t<0.
Remove: FADE alpha=1-e · FALL dy=e*9, slight rotation, fade · SHRINK scale=1-e centre, fade ·
FLY_UP dy=-e*9, fade · SCATTER dx/dy random direction from seed *6, rotation, fade.

```java
public final class TypingConfig {                  // plain mutable fields + defaults; clamp() enforces ranges
  public boolean enabled = true;
  public AppearStyle appearStyle = AppearStyle.SLIDE_UP;
  public RemoveStyle removeStyle = RemoveStyle.FADE;
  public Easing easing = Easing.AUTO;
  public int durationMs = 220;          // [40, 1000]
  public int removeDurationMs = 160;    // [40, 1000]
  public float intensity = 1.0f;        // [0.25, 3.0]
  public int staggerMs = 14;            // [0, 60]  per-char delay when several chars are inserted at once (paste, history)
  public int maxStaggerMs = 350;        // [0, 2000] cap on total stagger spread (json only)
  public boolean smoothReflow = true;   // chars glide to new x when text shifts / scrolls
  public boolean smoothCursor = true;   // caret glides
  public int glideMs = 55;              // [0, 300] time constant of glide smoothing
  public boolean animateChat = true;
  public boolean animateOtherFields = true;
  public boolean animateMultiline = true;
  public boolean onlyWhenFocused = true;   // unfocused fields change instantly (json only)
  public boolean optionsButton = true;     // show button in vanilla Options screen
  public TypingConfig copy(); public void clamp(); public static TypingConfig defaults();
}

public final class ConfigManager {                 // file: <configDir>/typinganimation.json, Gson, pretty printed
  public static void init(java.nio.file.Path configDir);   // idempotent; loads or writes defaults; never throws
  public static TypingConfig get();                // never null (defaults before init)
  public static void save();                       // never throws (logs)
  public static void reset();                      // defaults + save
  public static java.nio.file.Path path();
}
```
JSON: keys = field names, enums as lower-case names, missing/invalid values -> defaults, then clamp.

```java
public enum FieldKind { CHAT, OTHER, MULTILINE }
  // helper: public static boolean isAnimated(FieldKind, TypingConfig)  (enabled && per-kind toggle)

public final class FieldAnimationState {           // one per text widget instance
  public void beginFrame(long nowMs, float originX, float originY, int scrollKey);
      // dt tracking (clamped 0..100 ms); origin change => snap all smoothing; scrollKey = e.g. displayPos
  public void sync(String value, boolean animate, TypingConfig cfg);
      // diff vs previous value (common prefix/suffix): keeps per-char identity arrays aligned
      // (birth time, smoothed x, last layout); inserted chars get birth = now + i*step (stagger:
      // step = n>1 ? min(staggerMs, maxStaggerMs/(n-1)) : 0); removed chars that were laid out in the
      // previous frame become ghosts (at their last position/colour/style). animate=false or the very
      // first sync => no births/ghosts (text simply appears). Handles surrogate pairs (indices are UTF-16).
  public float layoutChar(int index, float targetX, float y, float width, int codepoint, Object style, int color);
      // records layout for this frame; returns smoothed x (== targetX when smoothing off/settled/new char)
  public boolean appearTransform(int index, TypingConfig cfg, CharTransform out);   // false => identity
  public int ghostCount(); public Ghost ghost(int i);
  public boolean ghostTransform(Ghost g, TypingConfig cfg, CharTransform out);       // false => expired/invisible
  public float caretX(float targetX, TypingConfig cfg);   // smoothed caret x
      // CALL ORDER per frame: beginFrame -> sync -> caretX (whenever the caret target is known, i.e. caret visible)
      // -> isIdle -> layoutChar... -> ghosts -> endFrame. The caret must go through caretX on EVERY frame it is
      // shown (idle or not); the idle fast path covers the text only.
  public boolean isIdle(TypingConfig cfg);
      // true when nothing animates: no active births, no ghosts, all laid-out chars settled, caret settled,
      // text/scrollKey/origin unchanged this frame. Renderers may then draw the vanilla way (layoutChar calls on
      // an idle frame snap to target).
  public void endFrame();                          // prune expired ghosts, roll "laid out last frame" flags
  public void reset();
  public static final class Ghost { public final int codepoint; public final Object style; public final int color;
      public final float x, y, width; public final long removedAt; public final int seed; }
}
public final class ScrambleGlyphs { public static int pick(int seed, long nowMs); }   // printable ASCII-ish set
```
Everything in core is allocation-free per frame in steady state (arrays grow geometrically and are reused).

Implemented behaviour details (core is the source of truth; read its javadoc):
- A ghost starts from the pose its char was last drawn with (mid-appear alpha/offset/scale/rotation, frozen SCRAMBLE
  glyph); chars that were invisible when last drawn leave no ghost. Renderers must honour `out.glyph` for ghosts too.
- An origin change in `beginFrame` snaps glide smoothing and SHIFTS existing ghosts by the origin delta.
- `animate=false` (or `enabled=false`) also snaps glide/caret for that frame. A char whose y changes snaps.
- `ghost(i)` returns null when out of range; at most 512 ghosts. Config file that fails to parse is backed up to
  `typinganimation.json.bak` and rewritten with defaults. `TypingConfig` exposes `*_MIN`/`*_MAX` range constants.

## 5. Renderer contract (per `mc/<group>`)

Hook the text drawing of `EditBox` (and `MultiLineEditBox`/`MultilineTextField` if feasible) so that:

1. Each frame: `state.beginFrame(Util.getMillis(), textX, textY, displayPos)`,
   `state.sync(value, FieldKind.isAnimated(kind,cfg) && (!cfg.onlyWhenFocused || focused), cfg)`.
2. Visible text is drawn **per character**: iterate the `FormattedCharSequence` produced by the widget's
   formatter(s) (keeps chat command syntax colouring); running absolute index = segment start + UTF-16 offset;
   target x = accumulated `font.width(FormattedCharSequence.codepoint(cp, style))`. For each char:
   `x = state.layoutChar(...)`, then if `state.appearTransform(...)` draw with the transform
   (translate to pivot, rotate, scale, translate back; alpha multiplied into ARGB colour), else draw plainly at x.
   If the formatter output does not match the substring length, fall back to vanilla drawing for that segment.
3. Return / preserve exactly the values vanilla code uses afterwards (e.g. end x of a segment used for caret
   and suggestion placement) so vanilla caret/selection/suggestion logic stays correct.
4. Draw ghosts (`ghostTransform`) at their recorded positions, clipped to the widget's inner area where
   the version makes that easy.
5. Caret: offset vanilla caret drawing by `state.caretX(target) - target` when `smoothCursor`.
6. When `state.isIdle(cfg)` the renderer may call the vanilla draw (still call `layoutChar` for every visible
   char so ghost positions are known). At rest the per-char output must be pixel-identical to vanilla.
7. Alpha: never pass an alpha that the version's `Font` would treat as "opaque" by mistake (pre-1.21.6 `Font`
   forces alpha<4/255 to opaque) — skip drawing chars whose alpha < ~0.02 instead.
8. Batch where cheap (e.g. 1.20.x `GuiGraphics#drawManaged`) — per-char `flush` storms must be avoided.
9. Field kind: chat input is `FieldKind.CHAT` (mark it from a `ChatScreen` mixin), multi-line = `MULTILINE`,
   everything else `OTHER`. State lives on the widget via a duck interface `dev.typinganimation.mc.TypingStateHolder`.

Mixins: **plain Mixin only** (`@Inject`, `@Redirect`, `@ModifyArg`, `@ModifyVariable`, `@Shadow`, `@Accessor`,
`@Invoker`, `@Unique`) in every group — Forge 1.20.1 and 1.21.1–1.21.9 do not bundle MixinExtras, and one uniform
style keeps groups portable. Keep mixin count minimal. `"injectors": {"defaultRequire": 1}`; Mojang names.
Where the same call has different descriptors per loader (NeoForge 1.21.1–1.21.5 patches EditBox to call the 6-arg
`drawString(..., boolean textShadow)`; vanilla/Fabric/Forge call the 5-arg one), target both with `require = 0`
handlers tied together by `@Group(name=..., min=1)` so a total miss still fails loudly.
`compatibilityLevel`: `JAVA_17` for 1.20.x, `JAVA_21` for everything else (Forge's upstream Mixin 0.8.7 stops at
JAVA_21, even on 26.x). Forge 1.20.1 needs a refmap (MixinGradle, SRG runtime); on obfuscated Fabric versions Loom's
remapJar remaps the mixin annotations statically to intermediary names (no refmap; the jar manifest says
`Fabric-Loom-Mixin-Remap-Type: static`); NeoForge/Forge ≥1.20.6/26.x run Mojang names (no refmap).
Mixin registration: Fabric `fabric.mod.json` `"mixins"`; NeoForge `[[mixins]]` in neoforge.mods.toml;
Forge jar-manifest `MixinConfigs: typinganimation.mixins.json` + dev run arg `--mixin.config=typinganimation.mixins.json`
(1.20.1: MixinGradle `mixin { config ... }`).

Cross-loader rules learned in research (all groups):
- Never call loader-only additions from shared code: Forge/NeoForge float `drawString` overloads,
  NeoForge `EditBox#getTextShadow()` (use `@Shadow` on the field if it exists in vanilla), `Button.Builder#build(Function)`.
- `Font#width` ceils; accumulate per-char advances as floats (`font.getSplitter().stringWidth(...)` or equivalent)
  and place glyphs with a pose translate for the fractional part so rest positions equal vanilla's.
- FormattedCharSink indices restart per piece in composite sequences — count UTF-16 offsets yourself.
- Render-state era (1.21.6+ and 26.x): drawing is deferred; every per-char draw must receive a fresh immutable
  `FormattedCharSequence` (never a reused mutable one). Each submit copies the pose. Keep per-char submits to
  frames that animate; merging runs of settled identity chars into one submit is a welcome optimisation.
- Pre-1.21.6: text colour alpha < 4/255 is forced opaque and the text shader discards alpha < 0.1 — OR in
  0xFF000000 before scaling alpha and skip glyphs whose alpha is too small.
- `Screen#onClose` differs per loader; config screen closes with an explicit `minecraft.setScreen(parent)`.
- MultiLineEditBox: `MultilineTextField.StringView` is a protected nested record; use accessor/invoker mixins
  with `@Coerce`/Object-typed handlers or recompute line starts — never access-wideners/ATs (they differ per loader).
  If multi-line support is not feasible cleanly in a version, omit it there and hide the `animateMultiline` option.
- 26.1+: `GuiGraphics` -> `GuiGraphicsExtractor`, `renderWidget` -> `extractWidgetRenderState`, `drawString` -> `text`,
  caret drawn via `TextCursorUtils`; 26.3 input uses SDL key codes (`KeyEvent(key, keycode, mods)`).

Scroll model: the two families of groups intentionally differ in how a scroll of the visible window is shown.
- 1.20.x–1.21.5 (immediate-mode `GuiGraphics`): the chars glide from their previous x like any reflow; a scroll jump
  larger than the edit that caused it (Home/End, clicks, word jumps) snaps instead of gliding. The grey suggestion
  and the selection highlight are drawn by vanilla at their target x, and the caret does not glide while text is
  selected (vanilla's highlight uses the unsmoothed caret x).
- 1.21.6+ and 26.x (render state): a scroll glides as one offset for the whole window (Home/End included), chars that
  enter the window during the glide are revealed at its edges, and the suggestion and the selection highlight move
  with the same offset (their x is shifted by the same hooks).
Both are exact at rest; the self-test of the render-state groups additionally traces a scroll-glide frame.

## 6. UI

- **Config screen** `dev.typinganimation.mc.ConfigScreen(Screen parent)`: title; a live preview `EditBox`
  (focused, hint text; vanilla draws a hint only in unfocused boxes, so the hint is the grey suggestion while the
  preview is empty, and clicking a button or releasing a slider gives the focus back to the preview) + "Demo"
  button that auto-types a localized sample phrase with human-like timing then erases part of it (loops until
  pressed again); two-column grid (150 px buttons, 22 px row step) with:
  `enabled`, `appearStyle`, `removeStyle`, `easing`, `durationMs` (slider), `intensity` (slider),
  `staggerMs` (slider), `glideMs` (slider), `smoothReflow`, `smoothCursor`, `animateChat`,
  `animateOtherFields`, `animateMultiline` (only if multi-line is supported in this group), `optionsButton`;
  tooltips for each; bottom row "Reset" + "Done". Changes apply live; saved on Done/close. While `enabled` is
  off every other option except `optionsButton` is greyed out. Slider labels: "220 ms", "14 ms/char", intensity
  "1x" / "1.5x" / "1.25x" (no trailing zeros); arrow keys move a slider by exactly one step.
  Must fit a 240 px tall scaled screen.
- **Options screen button**: when `optionsButton` is on, the vanilla Options screen shows a compact button
  (`typinganimation.options.button`) — in the options grid if the version makes that clean (1.21+: a 150 px button
  as its own row, centred under both columns), else top-left corner (x=6, y=6, height 20, width = text width + 16;
  1.20.x, whose grid already fills a 240 px screen, and newer groups when another mod replaced the grid). The hooks
  catch and log their own failures, so the Options screen never breaks.
- **Mods list**: NeoForge/Forge register the config screen factory; Fabric provides a Mod Menu entrypoint
  (compileOnly Mod Menu matching the MC version; skip if none exists for that version).

Lang keys (en_us + ru_ru, both complete, and nothing else: the json-only config fields have no translations;
core's LangFilesTest fails on missing and on unused keys):
```
typinganimation.title, typinganimation.description, typinganimation.options.button,
typinganimation.config.preview.hint, typinganimation.config.demo, typinganimation.config.demo.stop,
typinganimation.config.demo.text, typinganimation.config.reset, typinganimation.config.done,
typinganimation.config.<field> and typinganimation.config.<field>.tooltip for every UI field above
typinganimation.config.ms (format "%s ms"), typinganimation.config.ms_per_char ("%s ms/char"),
typinganimation.config.multiplier ("%sx"), typinganimation.appear.*, typinganimation.remove.*, typinganimation.easing.*
```

## 7. `target.properties`

```
mc.anchor=1.21.1
mc.range=1.21-1.21.1        (declared range: jar name label and every metadata range are derived from it)
loader=neoforge             (fabric | forge | neoforge)
group=1.21.1                (the mc/<group> source tree)
gradle.jdk=21               (major version of the Gradle JVM, see section 3)
java.release=21             (toolchain and --release)
jar.glob=build/libs/typinganimation-*+1.21-1.21.1-neoforge.jar   (matches exactly the release jar)
```

## 8. Safety

- Any exception in our render path: catch, log once per widget class, fall back to vanilla drawing
  (and remember `TypingRenderer.firstError` for the self-test). Never crash the game.
- No per-frame allocations beyond small unavoidable ones (e.g. `FormattedCharSequence.codepoint`).
- Client-only classes must not load on a dedicated server (loader glue guards by dist/environment).

## 9. Dev self-test (every target must pass)

Activated by env var `TYPINGANIMATION_SELFTEST=1` (or `-Dtypinganimation.selftest=true`); screenshots when
`TYPINGANIMATION_SELFTEST_SCREENSHOTS=1`. Implemented in `mc/<group>` (`dev.typinganimation.mc.SelfTest`),
driven from a client tick hook, starting once the first title/onboarding screen is shown and no overlay is active:
0. force-load (`Class.forName(name, true, loader)`) every class targeted by our mixins so a mixin that fails to
   apply fails the test (ChatScreen cannot be opened from the title screen: it dereferences `minecraft.player`);
1. open a test screen with a focused `EditBox` (and a multi-line box if supported); over ~60 ticks insert
   chars one by one, paste a long string, delete chars, move the caret, scroll; also give one EditBox a
   chat-like formatter (coloured styles, like command highlighting) and mark it `FieldKind.CHAT`;
   screenshots mid-animation and at rest when screenshots are enabled;
2. (removed — see 0 and 1)
3. open vanilla Options screen, assert our button exists, press it, assert `ConfigScreen` opens, render
   frames, cycle a button, change a slider, press Done;
   every group also checks: the preview is focused and shows the hint on open, clicks keep the preview focused,
   slider arrow keys move one step, `enabled` off greys the style options out, the demo stops (keeping the text)
   when the user types, an Options screen opened with `optionsButton` off has no button and the setting applies
   when it is switched back on, Reset restores and saves the defaults; the at-rest fidelity pairs (plain, chat,
   multi-line where supported, and a decorated pair with underline/strikethrough/italic/bold and obfuscated chars)
   are pixel-compared with vanilla on the idle path, the per-char path and with translucent text; no formatter
   mismatch fallback may happen;
4. assert `TypingRenderer.firstError == null`; log exactly `[typinganimation] SELFTEST PASS` or
   `[typinganimation] SELFTEST FAIL: <reason>` (with stack), then `Minecraft#stop()`.
Run: `TYPINGANIMATION_SELFTEST=1 ./gradlew runClient --no-daemon` in the target dir; pass = log line present
and no crash report.
