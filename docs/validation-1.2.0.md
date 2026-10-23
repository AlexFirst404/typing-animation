# Release validation — 1.2.0

Validation date: 23 October 2026. This report records checks of the final stable release sources and artifacts.
Minecraft tests use the anchor version for each target in `targets/`.

| Check | Result |
| --- | --- |
| Shared core tests | **265 passed**, zero failures, errors or skipped tests, including the final bundled-resource checks |
| Publisher unit tests | **14 passed** (`node --test scripts/modrinth-publish.test.mjs`) |
| Release builds and artifact freshness | **29/29 passed**; final corrected JARs rebuilt and audited against packaged-source fingerprints |
| Native-loader runtime self-tests | **29/29 passed**, including audio checks, with clean Gradle exits |
| Modrinth and CurseForge upload dry runs | **29/29 passed on each platform** against the final corrected artifacts, exit 0 |
| Additional loader production checks | **5/5 full self-tests passed** on exact final JARs; installed-copy hashes verified |
| Primary 26.3 NeoForge production client | **Full self-test passed**, exact final JAR, exit 0 and zero rendering fallbacks |
| Local description and changelog parity | **Passed**: identical platform description files, summaries and changelogs; `sync-description.mjs --check` passed |
| Live project metadata | **Passed**: Modrinth body/summary match the canonical content; CurseForge saved and reloaded the same wording, links and media with editor formatting normalization |
| Gallery | **Passed**: seven existing in-game GIFs retained; two unscaled real preset/sound settings screenshots visually checked and added to the eight existing screenshots |

The sound and preset implementation passed 265 core tests and the full 26.3 NeoForge development and
production self-tests during the approved local `1.2.0-beta.1` preview. That evidence is recorded separately in
[`preview-1.2.0-beta.1.md`](preview-1.2.0-beta.1.md); it is not presented as a test of the stable release JARs.

CurseForge normalizes table spacing, converts bold Markdown inside tables to `<strong>`, changes the horizontal
rule marker and removes the final newline. Online descriptions therefore have matching content, not identical
raw Markdown bytes. The two new gallery images were uploaded to both platforms; archived images can make a
remote gallery count larger than the 17 current local assets.

Release coverage includes configuration migration and persistence, sound classification and rate limits,
36 bundled Ogg samples and 16 sound events, English/Russian UI and subtitles, six visual presets, accepted input,
mute/paste/field gates, both sign editors, preset Apply behaviour, sound audition and the four-page settings layout.
Existing animation, scrolling, selection, caret and vanilla-pixel-fidelity checks remain in the suite.

Native-loader tests run the final production sources in development clients. The six production checks below
instead launch the unmodified distribution JARs. The 26.3 suite additionally exercises all six visual-preset
Apply actions and the sound-audition scheduler. On the five legacy API groups from 1.20.1 through 1.21.8,
the clipboard sound diagnostic exercises the accepted bulk-paste callback directly: synthetic Ctrl+V cannot
change the native GLFW modifier state. This limitation does not skip the sound resource, typing, mute or sign checks.

## Resource fallback correction

The first stable production check on bare Quilt 0.30.1 / Minecraft 1.20.1 caught a real defect: the mod loaded
and rendered correctly, but its bundled sounds were not exposed as a client resource pack. The JAR contained
all 36 Ogg files and `sounds.json`; the game's resource manager only loaded vanilla assets. This is distinct
from the older diagnostic reflection limitations recorded for [1.1.0](validation-1.1.0.md).

A lowest-priority classpath sound-resource fallback was added for clients without loader-provided resource
packs. It skips registration when an existing pack already exposes all bundled audio files, and keeps custom
resource packs at higher priority. The corrected 1.20.1 Fabric anchor passed the full production test on bare
Quilt 0.30.1 at 15:22:36 +03:00, including all sound events and the mapping-safe diagnostics. Its exact SHA-256
is `ffb826a7c73e877281e83a70c51027eb7b517b92144185dbcb059b0fff1c638e`.
All corrected artifacts have now been rebuilt and passed the archive/freshness audit, both publisher dry runs,
all 29 native-loader runtime tests and all six production checks.

## Native-loader runtime matrix

All 29 development-client runs completed the full self-test and audio checks, with exit 0 and a successful
Gradle result. The final run completed at 15:40:40 +03:00. Each result records its source fingerprint and log.
The final audit matched all 29 recorded fingerprints to the current release sources and found zero mod,
audio or resource warnings and zero mixin failures.

| Minecraft anchor | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| 1.20.1 | Passed | Passed | Additional-loader production check below |
| 1.21.1 | Passed | Passed | Passed |
| 1.21.4 | Passed | Passed | Passed |
| 1.21.5 | Passed | Passed | Passed |
| 1.21.8 | Passed | Passed | Passed |
| 1.21.10 | Passed | Passed | Passed |
| 1.21.11 | Passed | Passed | Passed |
| 26.1.2 | Passed | Passed | Passed |
| 26.2 | Passed | Passed | Passed |
| 26.3 | Passed | Passed | Passed |

## Additional loader compatibility

All checks use unmodified final stable JARs with installed-copy SHA-256 verification. Every combination completed
the full self-test, including all 16 sound events, field/mute/paste gates and both sign editors. There were zero
resource-path warnings and zero mixin failures. The earlier production reflection stops are resolved by
mapping-safe diagnostic accessors.

| Minecraft | Release JAR | Additional loader | Result |
| --- | --- | --- | --- |
| 1.20.1 | Fabric | Quilt 0.30.1 | **Full self-test passed** |
| 1.21.1 | Fabric | Quilt 0.30.1 | **Full self-test passed** |
| 1.21.11 | Fabric | Quilt 0.30.1 | **Full self-test passed** |
| 26.2 | Fabric | Quilt 0.30.1 | **Full self-test passed** |
| 1.20.1 | Forge | NeoForge 47.1.106 | **Full self-test passed** |

The primary **26.3 / NeoForge 26.3.0.34-beta** distribution also passed the full production self-test at
15:32:25 +03:00, with zero rendering fallbacks. The 1.20.1 Quilt anchor result was retained after the final build
audit confirmed its exact JAR hash was unchanged; the other five production cases ran after the rebuild completed.

Minecraft 26.3 remains **untagged for Quilt**: stable Quilt 0.30.1 provides Fabric Loader compatibility 0.19.3,
below the target's required 0.19.5. Other Fabric targets have no additional Quilt tag.

## Tested production hashes

| JAR (prefix `typinganimation-1.2.0+`) | SHA-256 |
| --- | --- |
| `1.20-1.20.1-fabric.jar` | `ffb826a7c73e877281e83a70c51027eb7b517b92144185dbcb059b0fff1c638e` |
| `1.21-1.21.1-fabric.jar` | `3e15ce3d1ed0101c37827544f9f23288dd84986565e80d72b858238f94cc0b66` |
| `1.21.11-fabric.jar` | `6c9421202fe2ce80513ea7c335d6001c973583521c7fa7b023fa2f311cff8421` |
| `26.2-fabric.jar` | `cbd52e5c78ceec482bcb4aead29855019b7dd2c139a74b72daeb46371decbad9` |
| `1.20-1.20.1-forge.jar` | `e8941fa60141eb687904c71f833d36b68263ca7c58ada70811f6e4a48eaaeba6` |
| `26.3-neoforge.jar` | `0661f41d0d73c59d6ac235bf7a934c615835ea426a150119f4610b9761fb1473` |

## Evidence

Local records are retained in `core/build/test-results/test/`, `.research/release-1.2.0-builds.json`,
`.research/release-1.2.0-artifact-audit.json`, `.research/release-1.2.0-selftests.json`,
`.research/release-1.2.0-selftests.md`, `.research/compat/release-1.2.0-results.json`,
`.research/release-1.2.0-modrinth-dry-run.log` and `.research/release-1.2.0-curseforge-dry-run.log`.
The production report contains all six exact hashes, full-pass evidence and log paths. All 29 release hashes
are recorded in `dist/SHA256SUMS-1.2.0.txt`.

The isolated offline clients log expected authentication/Realms errors. Their Windows performance-counter and
vanilla shader warnings are unrelated to the mod. Two nonfatal Windows clipboard warnings occurred in native
runs; both affected runs still passed their paste assertions. A Russian label diagnostic uses an unreachable
1000 ms cursor value even though that UI slider is capped at 500 ms; its 18 width warnings are known diagnostic
false positives.

## Delivery verification

On 23 October 2026, the public GitHub release `v1.2.0` contained all 29 final JARs, with matching names, sizes
and SHA-256 digests. The tag points to `9ef2f774cc032175817f7a012abdbb78dd070bc9`, containing the validated code
and release receipts.

Modrinth accepted all 29 release versions; their file sizes and SHA-512 hashes matched the final artifacts.
All ten Fabric versions carry the optional Mod Menu dependency, and the project description and source URL
were verified. The project status at this check was `processing` (moderation pending).

CurseForge accepted all 29 files, and every uploaded CDN download matched its final local JAR by SHA-512 and
size. The author dashboard showed all 29 as `Processing` at verification time. The new 26.3 Fabric file also
showed Mod Menu as an Optional Dependency. Upload IDs and hashes are recorded in
[`curseforge/releases/1.2.0.json`](../curseforge/releases/1.2.0.json).

The complete local verification record is `.research/release-1.2.0-publication-verification.json`.
