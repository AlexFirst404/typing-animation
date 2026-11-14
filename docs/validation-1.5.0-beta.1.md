# Validation — 1.5.0-beta.1

Validation date: **13 November 2026 (Europe/Moscow)**. Evidence snapshot: `2026-11-13T01:56:31+03:00`.

Validation status: **Complete**. All development-client and production-JAR results below match the final frozen inputs. Earlier stale or failed attempts are retained as history and do not count toward the accepted matrix. Archive assembly has separate gates and records its CRC/hash readback in the packaging receipt.

| Check | Accepted evidence at this snapshot |
|---|---|
| Shared core suite | **303 tests**, zero failures, errors or skipped tests |
| Distribution audit | **29/29 PASS**; exact version/loader/Java metadata, optional integrations, core bytecode normalization, 42 common resources per JAR and hashes |
| Binary API inspection | **15/15 PASS**: five official CommandBlockUIOverhaul artifacts and ten selected Better Command Block UI artifacts; this is not fifteen runtime tests |
| Final native development clients | **29/29 current PASS** |
| Final packaged-JAR production clients | **18/18 current PASS** |
| Bundle acceptance | Exactly 35 entries required; the packager verifies ZIP CRC and all 29 JAR hashes before writing its receipt |

## What is exercised

The baseline matrix runs the full self-test in each of the repository's 29 native development clients. It covers text appearance/removal/reflow, settled native pixels, cursor controls, vanilla books/signing, ordinary and hanging signs, accepted-input sound gates, all four built-in sound families, real custom sound-pack import/audition, Basic/Detailed settings and named preset controls. Optional foreign editors are absent in that baseline, so their absence cannot be mistaken for a positive compatibility test.

The separate production matrix installs unmodified distribution JARs into fresh isolated loader instances with the exact upstream mods and their required dependencies. It checks full outer self-test completion, audio/sign checks, the matching required-mod environment flag, actual optional-widget activity and valid screenshot PNGs. Installed Typing Animation and dependency hashes are checked against their frozen files, all 27 distinct third-party artifacts match their official SHA-512 receipts, and the executed source fingerprint matches the current build inputs. The final independent recheck reads the installed copies, logs and valid PNG evidence again for all 18 cases.

**CommandBlockUIOverhaul:** its real formatted multiline widget is tested for native wrapping/indentation, independent editor state, appearance/removal, both cursor axes, bulk supplementary-Unicode input, one sound per accepted edit, rejected/read-only input, selection, disabled multiline fields, scroll and programmatic assignment while a typed edit is pending. Six pixel comparisons cover bordered/unbordered native text and native append/insert carets. Its command parser, selection, history, suggestions and command submission remain upstream-owned.

**Better Command Block UI:** its real widget is tested for formatted native segments and virtual indentation, wrapped/horizontally scrolled text, cursor movement, typing/removal, independent fields, native selection and field gates. Saved native/per-character captures provide the probe's exercised-path fidelity check. Legacy `§`-formatted chunks retain their native rendering when that is needed to preserve text/index semantics; this is not a claim that every chunk is replaced by per-character animation.

**Regression combinations:** one exact primary NeoForge 26.3 client includes Scholar, and one Fabric 1.20.1 client includes Better Command Block UI and Scholar together. These exercise the existing real book/text-box compatibility paths alongside the new adapter.

## Native development-client matrix

Only runs with successful Gradle completion, full self-test/audio evidence, exit zero and matching **before/after/current** source fingerprints count as current PASS.

| Target | Current result | Seconds |
|---|---|---:|
| 1.20.1-fabric | PASS | 92.6 |
| 1.20.1-forge | PASS | 96.4 |
| 1.21.1-fabric | PASS | 90.0 |
| 1.21.1-forge | PASS | 99.6 |
| 1.21.1-neoforge | PASS | 100.0 |
| 1.21.10-fabric | PASS | 60.6 |
| 1.21.10-forge | PASS | 65.7 |
| 1.21.10-neoforge | PASS | 56.8 |
| 1.21.11-fabric | PASS | 68.1 |
| 1.21.11-forge | PASS | 65.5 |
| 1.21.11-neoforge | PASS | 81.0 |
| 1.21.4-fabric | PASS | 90.2 |
| 1.21.4-forge | PASS | 103.9 |
| 1.21.4-neoforge | PASS | 113.2 |
| 1.21.5-fabric | PASS | 92.4 |
| 1.21.5-forge | PASS | 109.5 |
| 1.21.5-neoforge | PASS | 100.4 |
| 1.21.8-fabric | PASS | 57.8 |
| 1.21.8-forge | PASS | 63.0 |
| 1.21.8-neoforge | PASS | 62.5 |
| 26.1.2-fabric | PASS | 58.4 |
| 26.1.2-forge | PASS | 58.6 |
| 26.1.2-neoforge | PASS | 75.1 |
| 26.2-fabric | PASS | 57.4 |
| 26.2-forge | PASS | 60.0 |
| 26.2-neoforge | PASS | 72.1 |
| 26.3-fabric | PASS | 62.7 |
| 26.3-forge | PASS | 66.7 |
| 26.3-neoforge | PASS | 65.2 |

## Exact production-JAR matrix

Every accepted row additionally requires the same frozen distribution hash in its installed `mods` copy. These are ordinary installed-loader clients, not Gradle's development classpath. The separate launcher verdict and Minecraft exit both have to pass.

| Case | Minecraft anchor | Installed loader | Upstream editor/book mod | Current result | Typing Animation SHA-256 |
|---|---|---|---|---|---|
| better-26.3-fabric | 26.3 | Fabric 0.19.5 | Better Command Block UI 0.5.4a | PASS | `60038f818c987cf64ff1d19837e6392483ed7009f6d337d7a852c866eabde054` |
| overhaul-1.20.1-forge | 1.20.1 | Forge 47.4.23 | CommandBlockUIOverhaul 1.1.0+mc1.20.1 | PASS | `8acf1bea28680a2b3eb922ebb1a5ca3687af4272f5b66300ae4ad5c428488245` |
| better-1.20.1-fabric | 1.20.1 | Fabric 0.19.5 | Better Command Block UI 0.5.2 | PASS | `21cbc2c6e953ff0e3903fc0138c68d8d7b7d5c8720bfc7e5d2720081b7d8509f` |
| better-1.21.1-fabric | 1.21.1 | Fabric 0.19.5 | Better Command Block UI 0.5.2a | PASS | `57faffa0ebfdd5984cd0a2dc872c94db76e3401051c98f33b1688efb2ada2cd5` |
| better-1.21.4-fabric | 1.21.4 | Fabric 0.19.5 | Better Command Block UI 0.5.3 | PASS | `6d324423dda77f447f8822997ee972a6a3dc78ff1bd348a31c5642b33b1b36b1` |
| better-1.21.5-fabric | 1.21.5 | Fabric 0.19.5 | Better Command Block UI 0.5.3 | PASS | `3120712f87532202044501607b5b03b475ba0d033b40d6ff739062a927583907` |
| better-1.21.8-fabric | 1.21.8 | Fabric 0.19.5 | Better Command Block UI 0.5.3 | PASS | `4ac747121ac9166eef61833354bf527bc6be070bae5507478e1957c7279658fc` |
| better-1.21.10-fabric | 1.21.10 | Fabric 0.19.5 | Better Command Block UI 0.5.3 | PASS | `90549693b22f12e91165a120e78975b2dcffbd80c791e9c7412bddc597ef116e` |
| better-1.21.11-fabric | 1.21.11 | Fabric 0.19.5 | Better Command Block UI 0.5.3 | PASS | `67278bcbea48fe4f78a3038077b5dc0ec5f393701764d00653e04966d652c41b` |
| better-26.1.2-fabric | 26.1.2 | Fabric 0.19.5 | Better Command Block UI 0.5.3 | PASS | `f7e041b9023a7b00e77983efe91f563e9385b724090d10a76122e1cda34ed44f` |
| better-26.2-fabric | 26.2 | Fabric 0.19.5 | Better Command Block UI 0.5.3 | PASS | `2c0bfd2f0bf8fd316355412a6d7ed7534e3ffdfc43f75a13d7675c766c9b0126` |
| overhaul-1.21.1-neoforge | 1.21.1 | NeoForge 21.1.252 | CommandBlockUIOverhaul 1.1.0+mc1.21.1 | PASS | `c4a2f8492a2431c62c0d189c6c861267fd476cf1690741b06cf709aedc9b4c0a` |
| overhaul-intermediate-1.20.1-forge | 1.20.1 | Forge 47.4.23 | CommandBlockUIOverhaul 1.0.2+mc1.20.1 | PASS | `8acf1bea28680a2b3eb922ebb1a5ca3687af4272f5b66300ae4ad5c428488245` |
| overhaul-oldest-1.20.1-forge | 1.20.1 | Forge 47.4.23 | CommandBlockUIOverhaul 1.0.1 | PASS | `8acf1bea28680a2b3eb922ebb1a5ca3687af4272f5b66300ae4ad5c428488245` |
| better-1.20.1-quilt | 1.20.1 | Quilt 0.30.2 | Better Command Block UI 0.5.2 | PASS | `21cbc2c6e953ff0e3903fc0138c68d8d7b7d5c8720bfc7e5d2720081b7d8509f` |
| better-26.3-quilt | 26.3 | Quilt 0.30.2 | Better Command Block UI 0.5.4a | PASS | `60038f818c987cf64ff1d19837e6392483ed7009f6d337d7a852c866eabde054` |
| scholar-26.3-neoforge | 26.3 | NeoForge 26.3.0.48-beta | Scholar 1.2.12 | PASS | `c3257cb0f9db29ae4e18a1963ff8d10068fa032d286f8a401e788ff54b79bc8f` |
| combined-better-scholar-1.20.1-fabric | 1.20.1 | Fabric 0.19.5 | Better Command Block UI 0.5.2, Scholar 1.2.10 | PASS | `21cbc2c6e953ff0e3903fc0138c68d8d7b7d5c8720bfc7e5d2720081b7d8509f` |

CommandBlockUIOverhaul is published for Forge 1.20.1 and NeoForge 1.21.1. The final production plan includes latest 1.1.0 on both loaders, the original 1.0.1 Forge artifact and the 1.0.2 Forge clipping-transition artifact. Better Command Block UI is exercised on ten Fabric anchors and two selected Quilt combinations. This is not a claim of a published upstream editor build on every loader supported by Typing Animation.

## Diagnostics and fixes retained in the history

Earlier development/production attempts remain in their JSON histories and archived logs. They are not combined with later successful results to manufacture a final PASS.

The first command-editor fixtures exposed that directly setting a cursor position can retain the native selection anchor, and that a per-render activity flag cannot be asserted between frames. Those fixture assertions were corrected to observe the native cursor/anchor state. The real legacy 1.0.1 editor also exposed a clipping difference: its unbordered insert caret extends one pixel above the widget and has no native scissor. The adapter preserves that behavior; native clipping introduced with 1.0.2 remains separate. A source change or new distribution hash invalidates earlier evidence for that target until the accepted run is repeated.

One existing native sound-timing assertion failed during an earlier attempt. That failure remains in the history. A fresh isolated run of the unchanged final sources passed; the failed attempt was not waived or combined with the successful run. Final acceptance uses only the final current result for every target.

## Artifact and packaging scope

The 29 JAR audit checks optional mixin registration, expected adapter/helpers, absence of bundled upstream classes, Minecraft/loader/Java metadata, AlexFirst attribution, the original license, all 42 common resources and all 36 built-in sound samples. Shared core implementation is compared across loader builds within each Java-release group after ASM normalization of debug/parameter metadata and constant-pool layout. Raw build metadata differences are recorded, and no JAR is rewritten to normalize it.

The final bundle is gated on all current audits and runtime checks. It contains exactly **35 entries**: 29 alternative Typing Animation JARs, SHA-256 checksums, the original MIT license, EN/RU README, changelog, this validation report and the separate compatibility survey. Install **one** matching JAR. No upstream mod or dependency JAR is bundled. Packaging verifies ZIP CRC and reads back every JAR against its frozen SHA-256; archive-specific cross-links in copied Markdown point to the bundled document names.

## Survey is separate from execution

[The eleven-mod compatibility survey](compatibility-audit-2026-11-13.md) inspects current published candidates and pinned source revisions. It labels custom-renderer bypass risk separately from expected vanilla-widget coverage. **None of those eleven surveyed mods was launched for that survey.** Those source-only judgments are not additional successful clients or reproduced bug reports.

## Evidence and limitations

- Core JUnit XML: `core/build/test-results/test/TEST-*.xml`.
- Final build fingerprints: `.research/build-1.5.0-beta.1-progress.json`.
- Artifact audit and checksums: `.research/artifacts-1.5.0-beta.1.json`, `dist/SHA256SUMS-1.5.0-beta.1.txt`.
- Native client results/history/logs: `.research/release-1.5.0-beta.1-selftests.json`, `.research/runtime-1.5.0-beta.1/`.
- Production plan, required-mod contract, current/previous results and final independent recheck: `.research/command-editors/production-1.5.0-beta.1-plan.json`, `production-contract.json`, `production-1.5.0-beta.1.json`.
- Official binary API inspections: `.research/commandblock-overhaul/adapter-api-audit.json`, `.research/better-commandblock/adapter-api-audit.json`.
- Final archive readback and secret-scan receipt: `.research/package-1.5.0-beta.1.json`.

These fixtures exercise the real upstream widgets and rendering/editing methods without creating a world, executing a command, sending a server packet, or saving/signing a book. Full in-world command execution, command-chain navigation across a live world, all editor add-ons, IME/bidirectional rendering, every resource pack and every Minecraft patch in a declared range are outside this exercised scope. Existing bidirectional/future-API fallback retains the native editor rather than forcing an invalid layout. Runtime checks use repository anchor versions; declared compatible patch ranges are not separate launches. This document records local beta validation, not publication or moderation status.
