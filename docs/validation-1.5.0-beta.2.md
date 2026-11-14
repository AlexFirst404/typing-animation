# Validation — 1.5.0-beta.2

Validation date: **14 November 2026 (Europe/Moscow)**.

Validation status: **Complete**. The shared core suite, all 29 builds and artifact checks, and the three selected development clients passed against the final frozen inputs. Archive assembly has separate gates and records its CRC/hash readback in the packaging receipt.

| Check | Result |
|---|---|
| Shared core suite | **303 tests PASS**, zero failures, errors or skipped tests |
| Distribution builds | **29/29 PASS**, frozen before/after source inputs and exact distribution hashes |
| Distribution audit | **29/29 PASS**, no normalized shared-core differences |
| Native development clients | **3/3 current PASS**, full visibility, ordinary field/audio and Gradle checks |
| Third-party production clients | **Not run for beta.2**; no current production matrix is claimed |
| Bundle acceptance | Exactly **35 entries** required; the packaging receipt records CRC and all 29 JAR hash readbacks |

## Change and acceptance behavior

The new `pauseOptionsButton=true` preference controls the Typing Animation entry in the vanilla Options screen when that screen was opened from the in-game pause menu. It does not add a new button directly to the pause screen. The **Show Button In Game / «Кнопка в игре»** control is available on the Fields page in both Basic and Detailed mode and remains usable while the master animation switch is disabled.

This separate in-game preference is editable on every loader without requiring Mod Menu. Turning it off preserves the main-menu Options entry as a recovery path when no alternative mod configuration entry exists. The existing `optionsButton` preference keeps its global meaning; it can hide the button globally only when an alternative settings entry is available. Forge/NeoForge report that alternative after registering their native Mods configuration factory. Fabric/Quilt use the installed Mod Menu integration. Moving a configuration to an environment without an alternative cannot hide its only main-menu settings entry.

Old configurations default to a visible in-game entry. The new preference participates in explicit JSON persistence, config copy/equality/hash behavior and the existing navigation-preference protection for user presets. Applying a built-in visual preset, importing a user preset or choosing a sound pack must not overwrite the user's navigation choices.

The core tests exercise the default and missing-key behavior, independent persistence of the global and in-game switches, config copying, built-in visual preset preservation and user-preset import protection. These core results do not establish the behavior of a rendered Minecraft screen.

## Development-client matrix

Exactly these three repository anchors were executed for beta.2 runtime validation. The other 26 distribution targets were built and audited, not separately launched for this change. All three clients exited successfully; no retries were required.

| Target | Purpose | Current result |
|---|---|---|
| 1.20.1-forge | Legacy Options-screen implementation and native Mods entry registration | **PASS**, 108.7 s |
| 26.3-neoforge | Current Options-screen implementation and native Mods entry registration | **PASS**, 68.1 s |
| 26.3-fabric | Current implementation without Mod Menu: loader log lists only Fabric Loader, Java, Minecraft, MixinExtras and Typing Animation | **PASS**, 64.9 s |

The existing full self-test is used, including its ordinary field/audio checks and the added rendered-screen visibility scenario. Acceptance requires the exact log marker:

```text
pause settings visibility: Basic control, disabled master, return/reopen, persistence and title recovery passed
```

That scenario must operate the Basic-mode control while animations are disabled, verify that returning to and reopening the in-game Options screen hides the entry, verify persistence, open the title-menu Options path and use its settings controls to restore the in-game entry. A policy assertion alone is not sufficient. Each accepted client also requires full `SELFTEST PASS`, the existing complete audio marker, successful Gradle completion, exit zero and no fatal diagnostics.

The before-run, after-run and current source fingerprints must match the accepted build inputs for each target. A source change invalidates a previous current result until that target's accepted run is repeated. Earlier failures or stale runs, if any, remain in the result history; they cannot be combined with later runs to manufacture acceptance.

## Artifact and archive gates

All 29 JARs must be built from the final frozen inputs with version `1.5.0-beta.2`, the intended Minecraft range, loader and Java release, AlexFirst attribution and the original license. The audit checks current source/build fingerprints, exact distribution hashes and sizes, optional compatibility mixin/helper registration, absence of bundled upstream classes, all 42 common resources and all 36 built-in sound samples. Shared core comparisons remain scoped to equivalent Java-release groups and the recorded bytecode normalization; the archive process does not rewrite JAR bytecode.

The bundle must contain exactly **35 entries**: 29 alternative Typing Animation JARs, SHA-256 checksums, the original MIT license, an EN/RU README, the beta.2 changelog, this validation report and the compatibility survey. Install **one** JAR matching the Minecraft range and loader. No upstream editor, book mod or dependency JAR is bundled.

Packaging is gated on the completed current audit and the three accepted native clients. It checks that sources, evidence inputs and distribution hashes have not changed, verifies ZIP CRC, reads back every entry and compares all 29 JARs to their frozen SHA-256 values. The separate packaging receipt records the final archive hash and archive-specific Markdown link changes; the archive's own hash is not embedded in this bundled document.

## Historical compatibility evidence and limits

The optional CommandBlockUIOverhaul and Better Command Block UI implementations from beta.1 remain in this preview. [The beta.1 validation report](validation-1.5.0-beta.1.md) records **18 third-party production combinations, 29 development clients and 15 binary API inspections for beta.1**. Those results belong to its exact old version, inputs and JAR hashes. They are historical regression context, **not current beta.2 runtime evidence**. No beta.2 third-party production reruns are included in the three-client scope above.

[The eleven-mod compatibility survey](compatibility-audit-2026-11-13.md) remains a source-only analysis. None of those eleven projects was launched for that survey. It does not add successful clients, certify all screens of those projects or report reproduced missing animation.

The three runtime anchors do not constitute launches of every declared Minecraft patch range, all 29 loader targets or every third-party combination. Full in-world workflows, every UI replacement, IME/bidirectional input and every resource pack are outside this change's exercised scope. This document records local preview validation, not publication or moderation status.

## Evidence locations

- Core JUnit XML: `core/build/test-results/test/TEST-*.xml`.
- Build results, logs and source fingerprints: `.research/build-1.5.0-beta.2-progress.json`.
- Current artifact audit: `.research/artifacts-1.5.0-beta.2.json`.
- Distribution checksums: `dist/SHA256SUMS-1.5.0-beta.2.txt`.
- Three-client results, retained history and log references: `.research/release-1.5.0-beta.2-selftests.json`.
- Archive readback and final packaging receipt: `.research/package-1.5.0-beta.2.json`.

The packaging gate rereads the current build, audit, client and JUnit evidence before writing an archive. The paired 26.3 NeoForge proof images are retained as `.research/runtime-1.5.0-beta.2/38-pause-options-hidden.png` and `39-title-settings-entry.png`; they show the absent in-game button and the retained main-menu entry.
