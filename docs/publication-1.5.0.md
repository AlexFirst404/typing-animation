# Publication status — 1.5.0

Date: **14 November 2026 (Europe/Moscow)**. This is a point-in-time delivery record; upload acceptance and moderation are separate results.

| Platform | Verified result |
|---|---|
| GitHub | Public stable **v1.5.0**, 29 JARs, an all-versions archive and SHA-256 checksums; **31/31 server asset digests and sizes match** |
| CurseForge | **29/29 uploads accepted**, 29 unique file IDs, no unresolved upload; **29/29 exact CDN downloads match SHA-512 and size**; moderation remains in progress |
| Modrinth | **Deferred at the user's request**; no project edits or version uploads were submitted |

## GitHub

The [public v1.5.0 release](https://github.com/AlexFirst404/typing-animation/releases/tag/v1.5.0) uses source commit [`64f0af1acf75f7f0cee4391bad4571ed3f713cf7`](https://github.com/AlexFirst404/typing-animation/commit/64f0af1acf75f7f0cee4391bad4571ed3f713cf7). The release is neither a draft nor a prerelease. Its public API endpoint also returned HTTP 200 without authentication. All 31 server-provided SHA-256 digests and asset sizes match the frozen local files.

The archive contains exactly **35 entries**: 29 alternative Typing Animation JARs, their checksum file, the MIT license, EN/RU installation notes, changelog, validation report and compatibility survey. Install **one** matching JAR. No third-party editor, book mod or dependency is bundled. Archive SHA-256: `2d4eae8a5be259c7e7a71e1dcf5712dd102881ec7f960efe2b57ebf3f3a07b38`. CRC and all JAR readbacks passed.

## CurseForge

[Typing Animation](https://www.curseforge.com/minecraft/mc-mods/typing-animation), project `1741028`, accepted all 29 Release uploads. The 26.3 NeoForge upload is [file 9612010](https://www.curseforge.com/minecraft/mc-mods/typing-animation/files/9612010). The completed [upload receipts](../curseforge/releases/1.5.0.json) retain each file ID, local SHA-512 and submitted metadata hash; `pending` is null. No uncertain POST was retried.

The author dashboard supplied the actual CDN links and sanitized filenames. All **29 downloaded JARs** match their local/uploaded SHA-512 and size. This verifies remote bytes and does not imply moderator approval.

The latest complete row snapshot, captured at `2026-11-14T15:18:47.435Z`, showed **1 Approved, 26 Under Review, 2 Processing** and 0 Scanning For Malware. The earlier all-Processing snapshot is retained separately. Files awaiting review have not been represented as approved or available in the public file listing. Their successful payload downloads do not bypass or establish moderation status.

Every file has the same EN/RU changelog and verified Minecraft/Client/loader/Java tags. Fabric files recommend Mod Menu as an optional dependency. The extra Quilt and legacy NeoForge tags use only combinations exercised in the current stable production matrix.

The description was **saved and verified after reloading** the author dashboard. Its full normalized text matches the shared GitHub/Modrinth description source (SHA-256 `74ff62cebece09046c8cd8805e5b443df7e1f3dfb08d4f6ca68d7ea203e2e491`). All **23 images loaded**, retaining seven GIFs, four wide pixel SVG dividers and two centered platform-link rows. The new 16-preset table, command-editor support and in-game button option are present in English and Russian.

## Validation and evidence

[Release validation](validation-1.5.0.md) records **303 core tests**, **29/29 current development clients**, **29/29 JAR audits** and **11/11 current exact production configurations**. There were no failures or retries. All six advertised extra-loader combinations were exercised, and all test clients exited.

Local evidence: `.research/package-1.5.0.json`, `.research/github-1.5.0-public-verification.json`, `.research/curseforge-1.5.0-upload-verification.json`, `.research/curseforge-1.5.0-download-verification.json`, `.research/curseforge-description-1.5.0-verification.json` and `.research/command-editors/runtime-final-reconciled-1.5.0.json`. Screenshots retain the saved preset description and author file statuses. Credentials are not included in this record.
