# Validation — Typing Animation 1.5.0

Validation date: **14 November 2026 (Europe/Moscow)**.

Validation status: **Complete**. All current stable build, artifact, development-client and production-JAR checks passed against the final frozen sources and distribution hashes. No retries or failures occurred. Historical preview passes are recorded separately. Archive assembly and readback are recorded by the packaging receipt.

| Check | Current status |
|---|---|
| Shared core | **303 tests PASS**, zero failures, errors and skips |
| Stable distribution builds | **29/29 PASS**, unchanged before/after source inputs |
| Current JAR audit | **29/29 PASS**, exact build hashes and no normalized core differences |
| Development-classpath clients | **29/29 current PASS**, full audio/Gradle and unchanged source fingerprints |
| Exact installed-JAR configurations | **11/11 current PASS**, independently reread installed hashes, logs and PNGs |
| Official dependency receipts | **15/15** distinct upstream artifacts matched fresh official SHA-512 responses; final runtime evidence must retain this manifest |
| Historical binary API inspection | **15** published binaries inspected during preview work; this is not fifteen current runtime clients |
| Popular-mod survey | **11 other projects**, source inspection only; no executed clients from that survey |
| Bundle acceptance | Exactly **35 entries** required; the packaging receipt records CRC and all 29 JAR hash readbacks |

## Release behavior under test

**Sixteen visual presets.** The six original combinations and their order remain intact. Ten new choices are Terminal, Typewriter, Snappy, Floating, Elastic, Retro, Swing, Orbit, Paper and Cinematic. They combine appearance/removal effects, timing, easing and cursor presentation, including blink speed, soft fade and visibility during typing. They do not change sounds, field selection, adaptive typing, the master switch or navigation preferences. Typewriter is a visual preset; its sound is selected separately.

Core tests verify that all sixteen combinations are distinct, already within valid ranges, recognized after every other preset, preserved through JSON, isolated from shared-template mutation and marked Custom after manual visual edits. Protected settings include custom sound packs, adaptive typing, detail mode and both settings-button preferences. The actual 26.3 settings self-test cycles and applies every preset through its UI buttons, preserves the preview text and audio/field choices, and checks layout after each Apply.

**Settings access.** Fields → Show Button In Game / «Поля → Кнопка в игре» hides the entry in Options opened from the pause menu. It does not add a separate button directly to the pause screen. The control works in Basic and Detailed mode, even with animations disabled and without Mod Menu. Main-menu access remains independent. The separate global Options-button switch uses an available alternative entry: the native Forge/NeoForge Mods config factory, or optional Mod Menu on Fabric/Quilt. Old configs default to a visible in-game entry.

The rendered pause-visibility scenario is present in the 1.20.1 and 26.3 self-tests: operate the Basic control with the master disabled, return to the same pause context, reopen Options, reload the saved preference, retain the title-menu entry and restore the pause entry through that UI. The other API groups undergo their normal full self-test; they are not described as executing this additional scenario. The exact required marker for the implemented groups is:

```text
pause settings visibility: Basic control, disabled master, return/reopen, persistence and title recovery passed
```

**Optional command editors.** CommandBlockUIOverhaul is supported on its published Forge 1.20.1 and NeoForge 1.21.1 builds. Better Command Block UI uses Fabric/Quilt. Both use Fields → Multi-line Fields and retain their native command colours, indentation, wrapping, selection, scrolling and editing. Programmatic assignments, initial text, navigation and rejected input stay quiet. Legacy `§`-formatted chunks in Better retain native drawing. The upstream mods remain optional and keep their own dependencies; Better 26.3 requires its matching Fabric API, while Typing Animation itself does not.

## Current development-client scope

All **29** repository targets are selected for current stable development-classpath runs, with no optional upstream editor/book mod assumed present. Each accepted run must have full `SELFTEST PASS`, complete four-family audio/sign feedback checks, successful Gradle completion, exit zero, no fatal injection/runtime diagnostics and identical before/after/current source fingerprints.

These checks exercise ordinary input, deletion, selection, scrolling, settled native pixels, caret movement, ordinary/hanging signs, books, configuration layout and the sound/input gates. The 26.3 clients additionally execute all sixteen preset Apply actions and sound audition. They are development-classpath tests, not execution of the remapped distribution JAR. Exact production-JAR checks are counted separately below.

## Exact production-JAR configurations

The eleven current cases use isolated workspace instances and the exact distribution JAR hashes. Ten include optional upstream mods; the legacy NeoForge 1.20.1 case exercises the additional loader using the Forge JAR without an optional editor. The six additional loader combinations advertised for this release are included in this matrix.

| Case | Loader / Minecraft | Installed optional editor or book mod | Current result |
|---|---|---|---|
| better-26.3-fabric | Fabric 0.19.5 / 26.3 | Better Command Block UI 0.5.4a | **PASS** |
| overhaul-1.20.1-forge | Forge 47.4.23 / 1.20.1 | CommandBlockUIOverhaul 1.1.0 | **PASS** |
| overhaul-1.21.1-neoforge | NeoForge 21.1.252 / 1.21.1 | CommandBlockUIOverhaul 1.1.0 | **PASS** |
| better-26.3-quilt | Quilt 0.30.2 / 26.3 | Better Command Block UI | **PASS** |
| scholar-26.3-neoforge | NeoForge 26.3.0.48-beta / 26.3 | Scholar 1.2.12 | **PASS** |
| combined-better-scholar-1.20.1-fabric | Fabric 0.19.5 / 1.20.1 | Better Command Block UI 0.5.2 and Scholar 1.2.10 | **PASS** |
| better-1.20.1-quilt | Quilt 0.30.2 / 1.20.1 | Better Command Block UI | **PASS** |
| better-1.21.1-quilt | Quilt 0.30.2 / 1.21.1 | Better Command Block UI | **PASS** |
| better-1.21.11-quilt | Quilt 0.30.2 / 1.21.11 | Better Command Block UI | **PASS** |
| better-26.2-quilt | Quilt 0.30.2 / 26.2 | Better Command Block UI | **PASS** |
| legacy-1.20.1-neoforge | NeoForge 47.1.106 / 1.20.1 | None; Forge distribution JAR | **PASS** |

The frozen plan records exact dependency files, versions, SHA-256/SHA-512 values, Minecraft/loader profiles and Java releases; the table does not replace those receipts. Acceptance rechecks the copied Typing Animation JAR, complete installed dependency set, original artifact hashes, loader-profile hash, required-mod environment flags, actual full self-test/audio markers and zero launcher/game exits. The 1.20.1 and 26.3 cases also require pause-visibility markers and both proof PNGs.

Better requires valid PNGs from its real editor and native/per-character captures. CommandBlockUIOverhaul requires its completed native-pixel probe and at least six identical-pixel comparisons. Scholar requires the real editor/book probe. PNG signatures, chunk CRCs and dimensions are rechecked. Each final accepted screenshot hash must agree with the accepted result and independent final recheck. No earlier stale/failed attempt may be combined with a later pass to manufacture acceptance.

The **15** distinct third-party dependency JARs are checked against fresh official Modrinth version-file responses. Raw JSON responses and their SHA-256 values are retained; each official JSON must contain the matching SHA-512 and version ID. This proves artifact identity, not runtime success by itself.

## Artifact and archive scope

All **29** stable JARs must be built from final frozen sources with the intended version, Minecraft range, loader/Java release, AlexFirst attribution and original MIT license. The audit checks exact build/JAR hashes, client-only metadata, optional adapter mixins/helpers, absence of bundled upstream classes, all 42 common resources, 36 sound samples and 16 sound events. Shared core comparison is scoped to equivalent Java releases after recorded normalization of debug/parameter metadata and constant-pool layout; the JAR bytes themselves are not rewritten.

The package additionally checks the exact ordered list of sixteen enum constants in every JAR, pause-setting bytecode and native config-entry registration. A fixed shared-core class count is not assumed: Java 17 emits three enum-switch helper classes that Java 21/25 no longer need. Actual class sets are checked against each target's compiled output and the accepted audit.

The archive contains exactly **35 entries**: 29 alternative Typing Animation JARs, SHA-256 sums, original MIT license, bilingual README, release changelog, this validation report and the popular-mod compatibility survey. Install **one** matching JAR, not all 29. No upstream mod or dependency is bundled.

Packaging requires current successful unit/build/audit/native/production receipts and a finalized version of this document. It freezes sources, logs, screenshots, installed dependencies, profiles and evidence inputs; verifies archive CRC and every readback entry; and matches all 29 JARs to their accepted SHA-256 values. The separate package receipt stores the final archive hash. That hash is not embedded in this bundled document, avoiding a circular hash dependency. Packaging does not itself upload or publish anything.

## Historical evidence and limits

[Beta.1 validation](validation-1.5.0-beta.1.md) records 18 exact preview production configurations and 29 preview development clients. [Beta.2 validation](validation-1.5.0-beta.2.md) records three preview development clients. These belong to their old sources, version metadata and JAR hashes, and are not current stable runtime passes.

The **15 binary API inspections** from preview research cover five published CommandBlockUIOverhaul binaries and ten Better Command Block UI anchors. The packager rereads their artifact hashes, preserving this historical API evidence without counting it as fifteen stable clients. Current stable runtime claims come only from the eleven production cases above.

[The eleven-mod survey](compatibility-audit-2026-11-13.md) is source-only analysis of **other** projects: YACL, FTB Library, REI, Modern UI, JEI, EMI, AE2, Refined Storage, Create, Cloth Config and Chat Patches. None was launched for that survey. It neither certifies their screens nor reports a reproduced animation failure. Its eleven entries are unrelated to the eleven production configurations in this release.

Current checks do not launch every declared Minecraft patch, every optional-mod release or every possible pair of UI replacements. Full in-world workflows, IME/bidirectional input, every resource pack and every font engine remain outside the exercised scope. Download/upload, publication and moderation status are recorded separately from build/runtime acceptance.

## Evidence locations

- Core XML: `core/build/test-results/test/TEST-*.xml`; full log: `.research/command-editors/core-1.5.0-tests.log`.
- Current builds: `.research/build-1.5.0-progress.json`.
- Current JAR audit: `.research/artifacts-1.5.0.json`.
- Distribution sums: `dist/SHA256SUMS-1.5.0.txt`.
- Native clients/history: `.research/release-1.5.0-selftests.json`.
- Production plan and contract: `.research/command-editors/production-1.5.0-plan.json`, `production-contract-1.5.0.json`.
- Production clients/history/final recheck: `.research/command-editors/production-1.5.0.json`.
- Official dependency manifest/raw responses: `.research/command-editors/dependencies-official-1.5.0.json`, `dependency-receipts-1.5.0/`.
- Archive/hash/readback receipt: `.research/package-1.5.0.json`.

The independent final readback is `.research/command-editors/runtime-final-reconciled-1.5.0.json`: 29 native clients, 11 exact production configurations and all 15 fresh official dependency receipts passed. There are 29 native attempts and 11 production attempts, with no retries. All test clients exited. Packaging rereads these current receipts and all final artifact hashes before producing the archive.
