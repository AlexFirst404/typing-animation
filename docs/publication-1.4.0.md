# Publication status — 1.4.0

Date: **12 November 2026**. This is a point-in-time record of completed delivery and remaining platform access.

| Platform | Verified result |
|---|---|
| GitHub | Public stable `v1.4.0`; 29 JARs, the all-versions ZIP and SHA-256 checksums; **31/31 server asset digests** match local files |
| CurseForge | **29/29 uploads accepted** for project `1741028`; completed receipts, matching artifact and upload-metadata hashes, and no unresolved upload |
| Modrinth | Release kit validated; **version uploads and description sync remain pending** a replacement token. The supplied token returns HTTP 401 |

## GitHub

The [public v1.4.0 release](https://github.com/AlexFirst404/typing-animation/releases/tag/v1.4.0) targets source commit [`73c1b6a97058d5fc816a3f8e97adfb4ae79b01b9`](https://github.com/AlexFirst404/typing-animation/commit/73c1b6a97058d5fc816a3f8e97adfb4ae79b01b9). It is neither a draft nor a prerelease and is marked as the latest release. All **31 asset sizes and server-provided SHA-256 digests** match the frozen distribution files. Verification time: `2026-11-12T17:02:11.029026+00:00`.

The archive contains 29 alternative JARs, the checksum file, the original MIT license and EN/RU installation notes. Scholar is optional and is not bundled. Archive SHA-256: `d710fd3123a46f29862ae9072b098310eb2fa0df2c81593f6324640b3dfa1264`.

## CurseForge

[Typing Animation](https://www.curseforge.com/minecraft/mc-mods/typing-animation), project `1741028`, accepted all 29 files. The Minecraft 26.3 NeoForge upload is [file 9577563](https://www.curseforge.com/minecraft/mc-mods/typing-animation/files/9577563). Each completed receipt matches its local JAR SHA-512 and its submitted metadata hash. There is no unresolved `pending` entry. The original upload records are retained in [curseforge/releases/1.4.0.json](../curseforge/releases/1.4.0.json).

Each file includes the same EN/RU release changelog. Fabric upload payloads recommend **Mod Menu** as an optional dependency. Additional Quilt and NeoForge tags retain only the combinations explicitly verified in the stable production matrix.

Upload acceptance is confirmed; the author dashboard's moderation status and public CDN downloads were not inspected in this run. This record does not claim that all files are approved or publicly downloadable yet.

The shared description was subsequently **saved in the CurseForge author dashboard and verified after reloading** on 12 November 2026. The saved full text, all 23 image URLs and every link match the prepared HTML. All 23 images loaded; seven GIFs, four wide SVG dividers and two centered platform-link rows are retained. Both English and Russian book/Scholar sections are present, and the previous restriction to book support from 1.21.6 is removed. The initial native Windows-browser attempt was blocked by unavailable URL policy support; the successful follow-up used the supported in-app browser. The release changelog remains attached separately to every file.

## Modrinth

The local publisher dry run validates all 29 final artifacts, version/loader tags, optional Mod Menu dependencies and release metadata. No 1.4.0 versions or project-description changes were submitted to Modrinth. Authentication returned HTTP 401, and the user deferred providing a replacement token. The unauthenticated project lookup does not establish its moderation status.

The prepared `modrinth/` kit can resume description synchronization and version publication when working credentials are supplied. No credential is stored in this record or the repository.

## Validation

[Release validation](validation-1.4.0.md) records 298 approved-preview core tests, 29 approved-preview native runtime passes, 29 final artifact audits and **11/11 exact stable-JAR production passes** with installed-file hashes. All product classes and 42 shared resources per JAR match the approved preview. Documented stable-only differences are diagnostics helpers/accessors and their mixin registration.
