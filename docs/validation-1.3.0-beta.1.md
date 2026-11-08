# Typing Animation 1.3.0-beta.1 validation

Date: 2026-11-08. Local preview; no release publication performed.

## Results

- **296 core tests passed**, no failures, errors or skipped tests. A fresh rerun included configuration migration, safe preset import/export, per-animation cadence snapshots, blink behavior, custom Ogg libraries and EN/RU translation consistency.
- **29/29 standalone targets built and audited**: correct version/author, client metadata, optional Mod Menu recommendation, Java release, all new classes, all shared resources and 36 original Ogg samples. Every JAR is newer than its packaged source inputs.
- **29/29 development-client self-tests passed** against unchanged source fingerprints. These exercise text/cursor rendering, vanilla fidelity, chat/commands, ordinary/hanging signs, audio gates, real custom-pack resource reload and audition, first-visit mode selection/defer/persistence, detailed-control reachability, HEX validation, entry-point guards and the actual preset-manager button callbacks.
- **2/2 final production JAR checks passed** in fresh offline launcher instances: 26.3 NeoForge, and 1.20.1 Fabric Loader 0.14.19 with Mod Menu 7.2.2 installed. Installed bytes match the final distribution checksums. The Mod Menu run exercises the button-hiding branch as well as the retained alternative settings integration.
- Basic settings and the preset library were inspected visually. Layout checks cover 320×240 scaled screens. The library test saves, duplicates, renames, exports, drops/imports with a name collision, applies and confirms deletion while preserving personal field/interface settings. Probe-created files and settings are restored.

## Final primary artifact

`typinganimation-1.3.0-beta.1+26.3-neoforge.jar`

SHA-256: `52f721667e43ff5cdf65a1b5ca9702c2b1fbb670835c24b42c602afa0d3fe4bc`

## Native target matrix

| Target | Build/audit | Runtime |
|---|---|---|
| 1.20.1-fabric | PASS | PASS |
| 1.20.1-forge | PASS | PASS |
| 1.21.1-fabric | PASS | PASS |
| 1.21.1-forge | PASS | PASS |
| 1.21.1-neoforge | PASS | PASS |
| 1.21.10-fabric | PASS | PASS |
| 1.21.10-forge | PASS | PASS |
| 1.21.10-neoforge | PASS | PASS |
| 1.21.11-fabric | PASS | PASS |
| 1.21.11-forge | PASS | PASS |
| 1.21.11-neoforge | PASS | PASS |
| 1.21.4-fabric | PASS | PASS |
| 1.21.4-forge | PASS | PASS |
| 1.21.4-neoforge | PASS | PASS |
| 1.21.5-fabric | PASS | PASS |
| 1.21.5-forge | PASS | PASS |
| 1.21.5-neoforge | PASS | PASS |
| 1.21.8-fabric | PASS | PASS |
| 1.21.8-forge | PASS | PASS |
| 1.21.8-neoforge | PASS | PASS |
| 26.1.2-fabric | PASS | PASS |
| 26.1.2-forge | PASS | PASS |
| 26.1.2-neoforge | PASS | PASS |
| 26.2-fabric | PASS | PASS |
| 26.2-forge | PASS | PASS |
| 26.2-neoforge | PASS | PASS |
| 26.3-fabric | PASS | PASS |
| 26.3-forge | PASS | PASS |
| 26.3-neoforge | PASS | PASS |

## Reproduction and scope

`./gradlew.bat test --no-daemon --rerun-tasks` from `core/` with JDK 21 as the Gradle JVM.
`scripts/build-all.ps1` builds and collects the native targets.
`bash scripts/run-target.sh <target> selftest` runs the client fixture. Production JAR checks use the local offline compatibility launcher under `.research/compat/launcher/`. Native per-target logs and source fingerprints are retained in `.research/release-1.3.0-beta.1-selftests.json`; final JAR receipts are in `.research/artifacts-1.3.0-beta.1.json`.

Native self-tests cover the anchor Minecraft versions; declared patch ranges follow the shared API groups in SPEC.md. Production execution was checked for the two combinations listed above, not for every JAR/patch/loader combination. Quilt retains the existing declared compatibility; this preview adds no new Quilt version claims.

Sounds remain off by default and adaptive typing is opt-in. Missing imported sound packs fall back to the selected built-in family. Presets refer to sound packs by content ID; sharing a preset does not embed the audio ZIP.
