# Release validation — 1.4.0

Validation date: **12 November 2026**. This record distinguishes the approved `1.4.0-beta.1` evidence from checks of the final stable files. Publication and moderation status are separate from local validation. All final stable acceptance checks below passed against the frozen release files.

| Check | Confirmed result |
|---|---|
| Approved shared core suite | **298 passed**, zero failures, errors or skipped tests |
| Approved native development-client matrix | **29/29 PASS**, full audio checks, clean Gradle completion and unchanged source fingerprints |
| Approved Scholar binary API inspection | **20/20 artifact API checks passed**; this is not twenty runtime tests |
| Approved Scholar production matrix | **3/3 PASS**: Minecraft 26.3 NeoForge with Scholar 1.2.12, and Minecraft 1.20.1 Fabric/Forge with Scholar 1.2.10; exact installed beta-JAR hashes matched |
| Approved beta distribution | **29/29 JAR audits passed**; ZIP checksum, CRC and all 29 archived JAR hashes verified |
| Stable release builds and JAR audit | **29/29 PASS** for the final stable artifacts |
| Stable-to-approved implementation equivalence | **2,923 existing class entries byte-identical**, **15 revised self-test helper entries** and **6 added test accessor entries**, across **2,944 stable class entries**. All product implementation classes and all **42 shared resources per JAR** are byte-identical |
| Exact stable-JAR production checks | **11/11 PASS**: three Scholar cases, six advertised extra-loader combinations and two additional remapped Fabric clients; **11/11 installed-copy SHA-256 matches** |
| Stable archive and checksum verification | **PASS**: archive SHA-256 and CRC verified; all **29 JARs** read back with matching hashes |

## Promotion of the approved preview

The user tested the preview and approved publication. Its complete evidence is retained in [validation-1.4.0-beta.1.md](validation-1.4.0-beta.1.md). The beta's native source fingerprints, 29 distribution hashes, three Scholar installed-copy hashes and archive contents were independently checked again before stable promotion. The standalone JUnit reports contain 298 tests with no failures, errors or skips.

The stable promotion changes version metadata and two self-test helpers. The approved preview has **2,938 compiled class entries** across the 29 JARs; the stable release has **2,944**, including six added diagnostic accessor entries. **2,923 existing class entries are byte-identical**. Nine changed entries are `BookSelfTest.class` in the three loaders for Minecraft 1.21.8, 1.21.10 and 1.21.11; six are `SelfTest.class` in the three loaders for Minecraft 1.21.8 and 1.21.10. No product implementation class changed. All **42 shared resources per JAR** are byte-identical.

The production check found that the helper's named-field reflection could not resolve `textField` on a remapped Fabric/Quilt runtime. Its single `textField()` method now finds the field by its `MultilineTextField` runtime type and rejects missing or ambiguous matches. The two additional Fabric checks reached the book and audio phases, then found the same mapping issue in `SelfTest.suggestion()`. That method now uses the already proven Minecraft 1.21.11 `EditBoxTestAccessor` pattern, reading the same preview hint without altering widget state. The accessor is added and registered once in each of the six affected JARs.

Independent source checks accept only these exact helper method replacements, the copied reference accessor and its single mixin-client registration. Restoring the approved helper/configuration bytes and excluding the added accessor reproduces every approved native target's source fingerprint; the remaining source content is unchanged. All affected JARs were rebuilt with unchanged before/after build fingerprints. The only other archive differences are release metadata. Earlier failed diagnostics attempts remain recorded separately.

This carries forward the approved preview evidence for the unchanged product implementation; it is not a claim that the 29 development-client tests ran again under the stable version. Exact stable distribution files receive separate production checks, including the corrected helper on the remapped runtimes below.

## Book editor coverage

The approved tests exercise the real vanilla book editing and signing screens. Legacy editor checks on Minecraft 1.20.1, 1.21.1, 1.21.4 and 1.21.5 cover typing, deletion, wrapping, cursor movement, page changes, accepted-input sounds, silent navigation, the multiline-field setting and settled pixels compared with native rendering. The wide-title regression checks fifteen wide Unicode characters against the book's full 192-pixel bounds.

The six newer API groups already use the multiline widget for pages and a centered signing field. Real-screen checks cover short, empty and similar page changes, PageUp/PageDown, retained edits, cursor movement, wrapping, the character limit, rejected-input silence and returning from signing to editing.

Scholar checks use its real formatted `TextBox` widgets and page-assignment method. They cover independent page fields, a centered signing-style field, styled text, a cursor in a red formatted span, supplementary Unicode characters, deletion, cursor movement, identical and short page assignment, and disable/re-enable behavior. Binary inspection verifies the used adapter signatures in twenty official Scholar artifacts.

The automated fixtures do not claim full in-world book persistence, inventory synchronization, lectern editing, import/export or network tests. All declared Minecraft patch ranges are represented by their repository anchors; every patch is not a separate client run. Scholar remains optional and is not bundled.

## Stable production checks

All eleven accepted production cases ran an ordinary installed loader with the final distribution JAR, completed the full self-test and audio checks, and exited successfully. Every installed-copy SHA-256 matched the frozen distribution file. The three Scholar cases explicitly required Scholar and completed its compatibility probe; absence could not silently skip the checks. The final independent recheck read every log, installed file and recorded dependency hash again.

| Minecraft | Distribution | Installed loader / optional Scholar | Result | SHA-256 |
|---|---|---|---|---|
| 26.3 | neoforge | NeoForge 26.3.0.48-beta, Scholar 1.2.12 | **PASS**, exit 0 | `36776b6c0f006d9e08743bb8ab207964f890fe9885bcb033aa25c0f53172bd53` |
| 1.20.1 | fabric | Fabric 0.19.5, Scholar 1.2.10 | **PASS**, exit 0 | `620a1da790e22373754005a3c30f6fe53f27645b934fb753fa1d62ced93b2a07` |
| 1.20.1 | forge | Forge 47.4.23, Scholar 1.2.10 | **PASS**, exit 0 | `684fb3efce72683dfd12427ca90052c076be4d9ce551ea4c7889973cea700b66` |
| 1.20.1 | forge | NeoForge 47.1.106 | **PASS**, exit 0 | `684fb3efce72683dfd12427ca90052c076be4d9ce551ea4c7889973cea700b66` |
| 1.20.1 | fabric | Quilt 0.30.2 | **PASS**, exit 0 | `620a1da790e22373754005a3c30f6fe53f27645b934fb753fa1d62ced93b2a07` |
| 1.21.1 | fabric | Quilt 0.30.2 | **PASS**, exit 0 | `2ce7183b7cef3ec0b7545f0d9232b0729134e3f1772c0394aca241a960e0ef48` |
| 1.21.11 | fabric | Quilt 0.30.2 | **PASS**, exit 0 | `1d351d37ad741f138ac9b17734d3284c0dc35a06369494447e1046dacece5c57` |
| 26.2 | fabric | Quilt 0.30.2 | **PASS**, exit 0 | `1f6af1695fa81a5291fde05827b400312edf983afe044456f0542d6d1de71073` |
| 26.3 | fabric | Quilt 0.30.2 | **PASS**, exit 0 | `f23558cbfe64c60b92758c56cd4554e01c83d94a3a0f92373cf947004d7c1a32` |
| 1.21.8 | fabric | Fabric 0.19.5 | **PASS**, exit 0 | `01e55a5e414f690e4a1e119fc3cd6abc04c2c53467f92b166ffdd3e07762dd46` |
| 1.21.10 | fabric | Fabric 0.19.5 | **PASS**, exit 0 | `5d1dbaf2cecb0a43457adb5c36e0bd99c7ec47b8599bb380288d457d28d72c5a` |

Scholar 1.2.12 itself needs a newer NeoForge than the project's older 26.3 development baseline: its compatibility run uses **26.3.0.48-beta**. This does not change Typing Animation's loader metadata floor.

## Final artifact acceptance

All **29 stable JARs** passed the final audit, including version and loader metadata, author attribution, optional Scholar mixins, book helpers, unchanged product implementation and the narrowly allowed self-test helper change. Their original bytes were retained; no JAR was rewritten during comparison. All 42 common resources per artifact match both the approved preview and their current source files.

The all-versions archive contains exactly **29 alternative Typing Animation JARs**, their checksum file, the MIT license and an EN/RU README. It contains no Scholar or other third-party mod. Its checksum is `d710fd3123a46f29862ae9072b098310eb2fa0df2c81593f6324640b3dfa1264`; CRC and every archived JAR's SHA-256 passed readback verification. The tracked/untracked public-source scan inspected 874 text files and found zero credential-pattern matches.

Stable artifact and packaging receipts are `.research/artifacts-1.4.0.json`, `.research/package-1.4.0.json` and `dist/SHA256SUMS-1.4.0.txt`. The accepted eleven-case production receipt is `.research/release-1.4.0-production.json`, including the final independent evidence recheck. Failed earlier helper attempts remain archived and are not counted as successful checks.
