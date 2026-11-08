# Release validation — 1.3.0

Validation date: 8 November 2026. This report distinguishes the approved preview runtime evidence from checks of the final stable distribution files. Publication and moderation status are recorded separately.

| Check | Result |
|---|---|
| Approved shared core suite | **296 passed**, no failures, errors or skipped tests, from the approved `1.3.0-beta.1` code |
| Approved native-loader client matrix | **29/29 full self-tests passed** on the approved preview code, with clean Gradle exits and unchanged per-target source fingerprints |
| Stable release builds and JAR audit | **29/29 passed** for final `1.3.0` artifacts |
| Stable-to-preview implementation equivalence | **2,506/2,506 compiled class entries byte-identical**, plus all **42 shared resources per JAR** byte-identical, across the 29 artifacts |
| Exact stable-JAR production checks | **7/7 passed**: primary 26.3 NeoForge, legacy NeoForge 1.20.1, and five Quilt 0.30.2 combinations |
| Installed artifact identity | **7/7 SHA-256 matches** between the copied production-test mod and the frozen distribution JAR |

## Promotion of the approved preview

The user approved `1.3.0-beta.1` before requesting publication. Its tests cover configuration migration and persistence, safe named-preset operations, cadence snapshots that do not rewind existing characters, blink policy, custom sound-pack validation, EN/RU key parity, actual text/cursor rendering, vanilla-pixel fidelity, accepted-input audio gates, ordinary and hanging sign editors, imported audio resource reload/audition, Basic/Detailed onboarding and persistence, control reachability at 320×240, HEX validation and actual preset-library button actions.

The stable rebuild changes version metadata, not the approved implementation. Every compiled class and shared resource in every stable artifact was compared byte-for-byte with its approved preview counterpart. The per-JAR comparison records allow changes only in the release metadata entries, such as the manifest and loader version metadata. The 296 core tests and 29 native development-client runs are therefore retained as evidence for the unchanged implementation; they are **not described as newly executed stable-version development runs**.

The exact stable files additionally underwent the seven independent production launches below. These use ordinary loader installations with an unmodified distribution JAR in a fresh instance, rather than the Gradle development classpath.

The preview’s separate Fabric + Mod Menu positive-path production check is retained as preview evidence. The seven stable production cases listed here do not claim a new Mod Menu-installed run.

## Stable production matrix

| Minecraft anchor | Distribution | Installed loader | Result | SHA-256 |
|---|---|---|---|---|
| 26.3 | NeoForge | NeoForge 26.3.0.34-beta | Full PASS, exit 0 | `a60b1e175700ab05aca6038faf0d1dc1f7738d593756be1944509b3ae6cf17e2` |
| 1.20.1 | Forge | NeoForge 47.1.106 | Full PASS, exit 0 | `ce354e5638ea9f89252e4ade6cd756313aa36a9e4e93bc83a6cb0fb9aad18032` |
| 1.20.1 | Fabric | Quilt 0.30.2 | Full PASS, exit 0 | `016adcc11a110cd593c39ecfe3c13462bcd6699499d8a50c2698fdcd99ff909b` |
| 26.2 | Fabric | Quilt 0.30.2 | Full PASS, exit 0 | `f2a09e4d0512d0f62bcd225223b0b13ed26dbb53df556ecd1a2db77d0a025ef2` |
| 1.21.1 | Fabric | Quilt 0.30.2 | Full PASS, exit 0 | `386d08707ed05484b6ee08e19981f1a751ba77f61c066b00c28aed6dca0fe7b2` |
| 1.21.11 | Fabric | Quilt 0.30.2 | Full PASS, exit 0 | `c5744b980a9c5ad24e0cd239a88d20119ff296f41022ca88721c50dbfe819123` |
| 26.3 | Fabric | Quilt 0.30.2 | Full PASS, exit 0 | `a00ddb034a0032110f36fa6f2242b5e9c8a0db4617d20db75e8a896160badbde` |

Each stable production run completed the full self-test and its audio checks, including the real imported sound-pack resource path and preset-manager UI. Installed-copy hashes matched the frozen release files, and no mixin/injection failure was found. Offline authentication and platform diagnostics unrelated to the mod are outside the runtime verdict.

## Quilt compatibility update

The official Quilt loader metadata was checked on the validation date: [Quilt loader versions](https://meta.quiltmc.org/v3/versions/loader). Quilt 0.30.2 is a stable release. Its downloaded loader JAR has SHA-256 `273033b1e92793e87f4800b02c6f3d8a89e14cbb71a8087a1f008837f24ab945`, matching that metadata, and its `quilt.mod.json` provides Fabric Loader compatibility version 0.19.5.

The successful final-JAR test on **Minecraft 26.3 + Quilt 0.30.2** adds the Quilt tag to `26.3-fabric`. That combination requires **Quilt 0.30.2 or newer**. The four older Quilt targets also passed the final 1.3.0 JARs on 0.30.2. Their previous release 1.2.0 results on Quilt 0.30.1 remain historical evidence in [validation-1.2.0.md](validation-1.2.0.md); they are not presented as fresh 1.3.0 executions on 0.30.1. Other Fabric targets receive no new Quilt tag.

## Native target coverage

| Target | Approved preview native runtime | Stable build / byte audit |
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

- `./gradlew.bat test --no-daemon --rerun-tasks` from `core/`, with the configured JDK/toolchain, reproduces the core suite.
- `scripts/build-all.ps1` rebuilds and collects all 29 standalone targets.
- `bash scripts/run-target.sh <target> selftest` runs the per-target development-client fixture.
- Exact stable production argument arrays and validated workspace instance paths are recorded in `.research/release-1.3.0-compat-plan.json`; final logs and installed hashes are in `.research/release-1.3.0-production.json`.
- The stable archive, class/resource equality and freshness audit is in `.research/artifacts-1.3.0.json`; final distribution checksums are in `dist/SHA256SUMS-1.3.0.txt`.
- The approved native matrix is in `.research/release-1.3.0-beta.1-selftests.json`; its report is [validation-1.3.0-beta.1.md](validation-1.3.0-beta.1.md).

Minecraft runtime checks use the **anchor** version of each API group. Declared patch ranges follow the identical-API grouping in SPEC.md; this report does not claim a separate launch of every patch in each range, or production execution of every native JAR/loader permutation. The seven production combinations above are the explicitly tested final stable binaries.

Sounds remain off by default and adaptive typing is opt-in. Named presets preserve personal field/interface/navigation settings; audio ZIP data is separate from the preset JSON. Missing imported sound packs fall back to the selected built-in family.
