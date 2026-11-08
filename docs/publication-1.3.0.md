# Publication status — 1.3.0

Date: **8 November 2026**. This is a point-in-time publication record; moderation, galleries and credentials can change after the observations below.

| Platform | Status at verification |
|---|---|
| GitHub | Public stable release `v1.3.0`; 29 JARs plus the SHA-256 checksum file; all 30 asset server SHA-256 digests matched local files |
| CurseForge | 29 files uploaded, downloaded from their actual author-dashboard CDN links and verified against their own SHA-512 receipts; **29/29 Approved** in the final author snapshot |
| Modrinth | New description and summary saved, and both new gallery images confirmed after reload; the 29 version uploads remain pending a replacement personal access token because the supplied token returned HTTP 401 |

## GitHub

The public [v1.3.0 release](https://github.com/AlexFirst404/typing-animation/releases/tag/v1.3.0) points to source commit [`45b5a3d0d6629e2c83a2ae6017ed2a506aadca0c`](https://github.com/AlexFirst404/typing-animation/commit/45b5a3d0d6629e2c83a2ae6017ed2a506aadca0c). It is a stable release, not a draft or prerelease. All 29 supported native build artifacts and their checksum file were uploaded; each server-provided SHA-256 asset digest matched the final local artifact.

## CurseForge

Project: [Typing Animation](https://www.curseforge.com/minecraft/mc-mods/typing-animation), ID `1741028`.

- **29/29 uploaded files** have completed upload receipts, with no unresolved `pending` upload.
- **29/29 actual CDN downloads** matched their corresponding local JAR and their own receipt SHA-512, including exact sizes. File IDs, display names, local filenames and stored filenames were individually associated with the correct target.
- The author dashboard's final snapshot recorded **29 Approved** at **2026-11-08T20:28:07.146Z**.
- The earlier author snapshot recorded 29 Processing at 2026-11-08T20:22:06.620947+00:00. That earlier status is retained as historical evidence, not the latest moderation state.
- The two new gallery images were saved and confirmed after reload, bringing the author gallery to **14 images**.

CurseForge sanitized `+` to `-` in stored CDN filenames. The successful verification used the exact links read from the author dashboard. Earlier reconstructed `+` URLs returned 403 and were superseded as invalid-filename probes; those requests did not establish moderation or download availability.

Evidence is retained in `curseforge/releases/1.3.0.json`, with additive download/hash/status fields. Original upload timestamps, file IDs, upload SHA-512 and metadata SHA-512 remain unchanged. The detailed local reports are `.research/curseforge-1.3.0-download-verification.json` and `.research/curseforge-1.3.0-author-final.json`.

## Modrinth

The [project page](https://modrinth.com/mod/typing-animation) has the new description and summary. Both new gallery entries, **Basic settings · 1.3** and **Your own preset library · 1.3**, were confirmed once each after reloading the editor.

The 29 version files have **not** been uploaded to Modrinth. The supplied upload token returned HTTP 401; the user said they would provide a replacement token later. This record does not describe Modrinth version delivery as complete. No token or raw authentication header is stored here.

## Validation and metadata scope

[Release validation](validation-1.3.0.md) records 296 approved-preview core tests, 29 approved-preview native runtime passes, byte-for-byte implementation/resource equivalence of the stable rebuilds, 29 stable artifact audits and seven full production checks of exact stable JARs.

The upload metadata explicitly recommends Mod Menu as an optional dependency for Fabric files. Non-Fabric upload payloads omit `relations`; the author editor also showed the project's optional recommendation on a NeoForge file, consistent with inherited project defaults. It is **not required**. Those published recommendations were retained without expanding this release into remote metadata corrections. Receipt metadata hashes describe the original submitted payloads, not an assertion that server-inherited relations were absent. The local metadata/changelog/title/tag hashes were independently recomputed against each file's own upload receipt and matched 29/29.
