# Release validation — 1.2.0

Validation date: 23 October 2026. This report is being completed against the stable release artifacts.
Minecraft tests use the anchor version for each target in `targets/`.

| Check | Result |
| --- | --- |
| Shared core tests | **265 passed**, zero failures, errors or skipped tests |
| Publisher unit tests | **14 passed** (`node --test scripts/modrinth-publish.test.mjs`) |
| Release builds and artifact freshness | **Pending: 29 targets** |
| Native-loader runtime self-tests | **Pending: 29 targets** |
| Modrinth and CurseForge upload dry runs | **Pending: 29 targets per platform** |
| Additional loader production checks | **Pending: five exact stable-release JARs** |
| Description and changelog parity | **Passed**: identical platform descriptions, summaries and changelogs; `sync-description.mjs --check` passed |
| Gallery | **Passed**: seven existing in-game GIFs retained; two unscaled real preset/sound settings screenshots visually checked and added to the eight existing screenshots |

The sound and preset implementation passed 265 core tests and the full 26.3 NeoForge development and
production self-tests during the approved local `1.2.0-beta.1` preview. That evidence is recorded separately in
[`preview-1.2.0-beta.1.md`](preview-1.2.0-beta.1.md); it is not presented as a test of the stable release JARs.

Release coverage includes configuration migration and persistence, sound classification and rate limits,
36 bundled Ogg samples and 16 sound events, English/Russian UI and subtitles, six visual presets, accepted input,
mute/paste/field gates, both sign editors, preset Apply behaviour, sound audition and the four-page settings layout.
Existing animation, scrolling, selection, caret and vanilla-pixel-fidelity checks remain in the suite.

## Additional loader compatibility

The following production checks must use unmodified final stable JARs with installed-copy SHA-256 verification.
Any diagnostic reflection limitation will be recorded separately from a full runtime pass.

| Minecraft | Release JAR | Additional loader | Result |
| --- | --- | --- | --- |
| 1.20.1 | Fabric | Quilt 0.30.1 | Pending |
| 1.21.1 | Fabric | Quilt 0.30.1 | Pending |
| 1.21.11 | Fabric | Quilt 0.30.1 | Pending |
| 26.2 | Fabric | Quilt 0.30.1 | Pending |
| 1.20.1 | Forge | NeoForge 47.1.106 | Pending |

Minecraft 26.3 remains **untagged for Quilt**: stable Quilt 0.30.1 provides Fabric Loader compatibility 0.19.3,
below the target's required 0.19.5. Other Fabric targets have no additional Quilt tag.

## Evidence

Stable build, runtime and production records will be linked here after completion. No pending check above
should be read as a release pass.
