# Release validation — 1.1.0

Validation date: 18 October 2026. Tests use the Minecraft anchor version for each target in `targets/`.

| Check | Result |
| --- | --- |
| Shared core tests | **248 passed**, zero failures, errors or skipped tests |
| Release builds | **29/29 passed**; all 29 JARs are newer than their packaged source inputs |
| Native-loader runtime self-tests | **Pending**: the final 29-target matrix is still running |
| Modrinth and CurseForge upload dry runs | **29/29 passed on each platform** |
| Gallery capture | Real 26.3 NeoForge client; seven GIFs and eight screenshots generated and visually checked |

Core tests cover configuration migration, independent appearance/removal strength, all animation transforms,
two-dimensional caret movement, disabled/focus behavior, exact settling and allocation limits. Runtime checks
exercise typing, deletion, paste, scrolling, selection, formatted text, pixel fidelity, settings pages and both
normal and hanging sign editors. The native-loader matrix result above must be finalized before release.

## Additional loader compatibility

These checks launch the **unmodified final release JARs** in production-style clients. Every tested JAR's SHA-256
matches the corresponding file in `dist/`. All five combinations load the mod and its key mixin targets and
render animated text without an injection failure.

| Minecraft | Release JAR | Additional loader | Production result |
| --- | --- | --- | --- |
| 1.20.1 | Fabric | Quilt 0.30.1 | Animation and pixel-fidelity checks passed; diagnostic reflection stop A |
| 1.21.1 | Fabric | Quilt 0.30.1 | Animation and pixel-fidelity checks passed; diagnostic reflection stop A |
| 1.21.11 | Fabric | Quilt 0.30.1 | All 10 mixin targets loaded; typing, deletion, scrolling and style checks passed; diagnostic reflection stop B |
| 26.2 | Fabric | Quilt 0.30.1 | **Full runtime self-test passed**, zero rendering fallbacks |
| 1.20.1 | Forge | NeoForge 47.1.106 | Animation and pixel-fidelity checks passed; diagnostic reflection stop A |

- **A:** the development self-test reflects the Mojang-named `EditBox.suggestion` field. Production mappings use a
  different name, so the preview-hint diagnostic stops with `NoSuchFieldException: suggestion`.
- **B:** the development self-test reflects the Mojang-named `MultiLineEditBox.textField` field. The diagnostic
  stops at step 140, “default settings”, with `NoSuchFieldException: textField`.

These four stops are harness limitations, not full self-test passes. Later diagnostic steps were not executed in
those production runs. The 1.21.11 target-loading diagnostic uses remappable class literals and nested-class
discovery so the key mixins are checked before this limitation is reached.

Minecraft 26.3 is **not tagged for Quilt**. Quilt 0.30.1 provides Fabric Loader compatibility version 0.19.3,
below this target's required 0.19.5. Earlier success with Quilt 0.31.0-beta.4 is not used as a current stable-loader
compatibility claim. Other Fabric targets have no additional Quilt tag.

## Tested release hashes

| JAR (prefix `typinganimation-1.1.0+`) | SHA-256 |
| --- | --- |
| `1.20-1.20.1-fabric.jar` | `a425d70c92bf45c7450231024493e4b99116d8f4f9d63065df21b77c8bd52ca7` |
| `1.21-1.21.1-fabric.jar` | `908ef83e29a6a2219f4becb93f51d484402bdfcab38cbeb6c14f33f3f9bcc213` |
| `1.21.11-fabric.jar` | `e80e388a0015829a9a988d70e3c07330d37a0a8d56c31cc7fbdf54f057f7f4e3` |
| `26.2-fabric.jar` | `d7aa92f78ee1708c255205fd77cd03fffe825f82cdeafe16572c7f1c9d9f1708` |
| `1.20-1.20.1-forge.jar` | `b33907c90b92dbc3ec81adde302fa4dbfadb6f72d69b6127a8e194ddecf9ba9b` |

Local validation records are retained in `core/build/test-results/test/`, `.research/release-build-progress.json`,
`.research/release-artifact-freshness.json`, `.research/release-1.1.0-selftests.json` and
`.research/compat/release-1.1.0-results.json`. The last report includes the production logs, exact failure messages,
rendering evidence and the final-JAR hash comparisons.

For the standard checks, run `./core/gradlew -p core test --no-daemon` and `bash scripts/run-target.sh <target> selftest`.
Use `node scripts/modrinth-publish.mjs --dry-run` and `node scripts/curseforge-publish.mjs --dry-run` to validate
release payloads without uploading.
