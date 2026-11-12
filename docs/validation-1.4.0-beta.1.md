# Validation — 1.4.0-beta.1

Validation date: **12 November 2026**. This record distinguishes source-level tests, native development clients, binary API inspection and execution of the packaged distribution JARs. Final acceptance checks below passed against the current source fingerprints and frozen distribution files.

| Check | Confirmed result |
|---|---|
| Shared core suite | **298 passed**, zero failures, errors or skipped tests |
| Final native client matrix | **29/29 PASS**, exit 0, full audio checks and successful Gradle completion; all before/after source hashes still match the current files |
| Scholar binary API inspection | **20/20 artifact API checks passed**; this is not 20 runtime tests |
| Scholar production client, Minecraft 26.3 | **PASS** on NeoForge **26.3.0.48-beta** with Scholar **1.2.12**, using the packaged JAR and an exact installed SHA-256 match |
| Scholar production clients, Minecraft 1.20.1 | **2/2 PASS** with Scholar **1.2.10** on Fabric **0.19.5** and Forge **47.4.23**, exact installed SHA-256 matches |
| Final distribution audit | **29/29 PASS**; metadata, optional mixins, book helpers, common resources, normalized core bytecode and file checksums verified |

## Actual book editors

The book checks exercise Minecraft's real `BookEditScreen`, its native input and page controls, and its signing interface. They are not limited to the generic multiline-widget fixture.

For Minecraft 1.20.1, 1.21.1, 1.21.4 and 1.21.5, the integration observes the legacy editor's native layout, text helper, page number, signing state and cursor positions. Tests cover typing, deletion ghosts, wrapping, caret movement, page navigation, accepted-input sounds, silent navigation, the multiline-field setting and settled pixels compared with the disabled-mod baseline. Signing retains the native black/grey underscore when no cursor customization is selected.

For the six newer API groups, the actual editor uses `MultiLineEditBox`, and `BookSignScreen` uses a centered `EditBox`. The new page-assignment hook resets the full previous-page state independently of text similarity. The real-screen probe covers short, empty and similar pages, PageUp/PageDown, retained edits, removal ghosts, caret movement, wrapping, the character limit, rejected-input silence, centered title input and Cancel returning to the editor.

The final legacy checks additionally include a wide-title pixel regression. Vanilla accepts fifteen wide Unicode characters even when the title is wider than its 114-pixel page column. The fixture chooses a genuinely wide glyph using the active font, types the final character with an appearance effect, checks its visible pixels beyond the former page-column boundary, and compares the settled title with native pixels. Signing effects are clipped to the full 192-pixel book; page text keeps its existing clipping area.

These title-screen fixtures do **not** save, finalize or send a book, create a world, or connect to a server. Test-only player fixtures supply the name needed by the native constructor. World/player persistence is outside their scope; rendering and editing remain the native implementation.

## Native runtime evidence

The final native development-client matrix completed **29/29 full self-tests**. Every accepted run recorded PASS, exit 0, completed audio checks, successful Gradle completion and identical source fingerprints before and after execution. Those hashes were checked again against the final source files. Earlier failed or stale attempts remain archived separately. Production-JAR verification is recorded in the following section.

| Native target | Result | Seconds |
|---|---|---:|
| 1.20.1-fabric | PASS | 89.2 |
| 1.20.1-forge | PASS | 94.9 |
| 1.21.1-fabric | PASS | 94.1 |
| 1.21.1-forge | PASS | 92.7 |
| 1.21.1-neoforge | PASS | 88.3 |
| 1.21.10-fabric | PASS | 55.7 |
| 1.21.10-forge | PASS | 61.7 |
| 1.21.10-neoforge | PASS | 58.2 |
| 1.21.11-fabric | PASS | 56.5 |
| 1.21.11-forge | PASS | 62.9 |
| 1.21.11-neoforge | PASS | 57.5 |
| 1.21.4-fabric | PASS | 86.7 |
| 1.21.4-forge | PASS | 89.2 |
| 1.21.4-neoforge | PASS | 88.6 |
| 1.21.5-fabric | PASS | 87.2 |
| 1.21.5-forge | PASS | 89.8 |
| 1.21.5-neoforge | PASS | 89.8 |
| 1.21.8-fabric | PASS | 60.7 |
| 1.21.8-forge | PASS | 67.4 |
| 1.21.8-neoforge | PASS | 63.8 |
| 26.1.2-fabric | PASS | 57.9 |
| 26.1.2-forge | PASS | 60.9 |
| 26.1.2-neoforge | PASS | 54.9 |
| 26.2-fabric | PASS | 56.0 |
| 26.2-forge | PASS | 58.7 |
| 26.2-neoforge | PASS | 56.7 |
| 26.3-fabric | PASS | 61.2 |
| 26.3-forge | PASS | 63.0 |
| 26.3-neoforge | PASS | 57.2 |

The test matrix uses the repository's Minecraft anchors. Declared patch ranges are not a claim that every patch inside each range received a separate client run.

## Scholar compatibility

Scholar renders its own formatted text boxes rather than the vanilla multiline widget. The optional adapter keeps its native line layout, formatting, selection and editing; Unicode code-point positions are converted to the UTF-16 indices used by the animation core. A page assignment resets both page states even when the destination text is identical. Disabling the setting restores native text/cursor behavior and suppresses typing feedback.

The cursor adapter receives the color argument passed to Scholar's real `renderCursor` call. It does not depend on Scholar's version-specific `Formatting.Color` accessors. The final 26.3 production run exercised the cursor within a **red formatted span**, alongside styled text, a supplementary Unicode character, independent left/right pages, a centered signing-style field, deletion, horizontal/vertical caret motion, identical/short page assignment, and disable/re-enable behavior. This is exercised-path coverage, not a claim of pixel equality for every possible Scholar style.

The Scholar probe instantiates the **real Scholar `TextBox` widgets** in a controlled screen and invokes the real `SpreadBookEditScreen.setTextBoxes` page setter on a test fixture. It does not claim a full in-world Scholar interaction, inventory synchronization, lectern editing, import/export or network-persistence test.

| Packaged-JAR production case | Result | Typing Animation SHA-256 |
|---|---|---|
| Minecraft 26.3, NeoForge 26.3.0.48-beta, Scholar 1.2.12 | **PASS**, exit 0, installed file matched | `cbe124e42ed9ef406df76f179d2cfde92d56b727dc985a125aac6645d4971582` |
| Minecraft 1.20.1, Fabric 0.19.5, Scholar 1.2.10 | **PASS**, exit 0, installed file matched | `36cc76d592c60ccc1a719e49d6a24b080b9c18db418107ce2c1dc70210296544` |
| Minecraft 1.20.1, Forge 47.4.23, Scholar 1.2.10 | **PASS**, exit 0, installed file matched | `8f29a22a6a2855294163edd5ab6fa3ee9325237325cec1486e6d689df66f3538` |

The consolidated production receipts are `.research/scholar/production-final-results.json`. All three runs explicitly required Scholar, exercised the red-span caret and all twelve compatibility phases, and matched the exact installed JAR against its final distribution file. The two 1.20.1 runs also included the final wide-title pixel regression.

The separate binary audit inspected **20 official Scholar artifacts** across the supported Minecraft anchors and available Fabric/Forge/NeoForge builds. It verified the adapter's used class/method/field signatures and stored each artifact's hash in `.research/scholar/adapter-api-audit.json`. It establishes an API match for those binaries, not successful execution of all twenty combinations.

## Final artifact acceptance

All **29 distribution JARs** passed the final audit after the last packaged-source change. The audit checked version and loader metadata, AlexFirst attribution, client-only metadata, native book helpers and optional Scholar mixins. All **42 common resources per JAR** matched their source bytes.

The core implementation matched across loaders within each Java-release group after ASM normalization of debug/parameter metadata and constant-pool layout. Raw class bytes have loader-build metadata differences; no distribution JAR was modified to normalize it.

The all-versions ZIP contains exactly **29 JARs**, their SHA-256 checksum file, the original MIT license and a short EN/RU README. CRC verification passed, and every JAR read back from the archive matched its distribution file and audit receipt. Scholar and third-party dependencies are not bundled.

Final evidence: `.research/artifacts-1.4.0-beta.1.json`, `.research/package-1.4.0-beta.1.json`, `.research/release-1.4.0-beta.1-selftests.json`, and `dist/SHA256SUMS-1.4.0-beta.1.txt`. These are local validation records; public publication is a separate step.
