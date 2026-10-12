#!/usr/bin/env node
// CurseForge author upload API. Node >= 18; no dependencies. Importing this module has no side effects.
import { readFile, readdir, mkdir, writeFile, rename, open, unlink } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
import { inflateRawSync } from 'node:zlib';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const API_BASE = 'https://minecraft.curseforge.com/api';
const MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const MOD_ID = 'typinganimation';
const LOADER_NAMES = { fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge', quilt: 'Quilt' };
const LOADER_ORDER = ['fabric', 'forge', 'neoforge'];
const RELEASE_NAME = /^\d+(?:\.\d+)+$/;
const hash = (bytes) => createHash('sha512').update(bytes).digest('hex');
const readText = async (filename) => (await readFile(filename, 'utf8')).replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
const redact = (value, token) => token ? String(value).split(token).join('<CURSEFORGE_TOKEN>') : String(value);

function compareVersions(a, b) {
    const x = a.split('.').map(Number), y = b.split('.').map(Number);
    for (let i = 0; i < Math.max(x.length, y.length); i++) {
        const difference = (x[i] ?? 0) - (y[i] ?? 0);
        if (difference) return Math.sign(difference);
    }
    return 0;
}

function properties(text) {
    const result = {};
    for (const line of text.split('\n')) {
        const match = line.match(/^\s*([^#!=\s][^=]*?)\s*=\s*(.*?)\s*$/);
        if (match) result[match[1]] = match[2];
    }
    return result;
}

function projectId(value) {
    const id = String(value ?? '').trim();
    if (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) {
        throw new Error('A positive numeric CurseForge project ID is required; create the project in the author dashboard first.');
    }
    return id;
}

function apiBase(value = API_BASE) {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
        throw new Error('CurseForge API base must be an HTTPS URL without credentials, query or fragment.');
    }
    return value.replace(/\/+$/, '');
}

class RequestError extends Error {
    constructor(message, { status, unknownOutcome = false } = {}) {
        super(message);
        this.name = 'RequestError';
        this.status = status;
        this.unknownOutcome = unknownOutcome;
    }
}

// Deliberately one attempt, including for HTTP 429. A mutation never retries automatically.
async function request(url, { token, method = 'GET', body, timeoutMs = 30000, fetchImpl = globalThis.fetch } = {}) {
    if (typeof fetchImpl !== 'function') throw new Error('Use Node 18 or newer with fetch, FormData and Blob.');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    timer.unref?.();
    const headers = { Accept: 'application/json', 'User-Agent': 'typing-animation-curseforge-release-kit' };
    if (token) headers['X-Api-Token'] = token;
    let response;
    let text;
    try {
        response = await fetchImpl(url, { method, headers, body, signal: controller.signal });
        text = await response.text();
    } catch (error) {
        const reason = controller.signal.aborted ? `timed out after ${timeoutMs} ms` : redact(error.message, token);
        throw new RequestError(`${method} ${url}: ${reason}.${method === 'POST' ? ' Upload outcome is unknown; reconcile the pending receipt before rerunning.' : ''}`, {
            unknownOutcome: method === 'POST',
        });
    } finally {
        clearTimeout(timer);
    }
    if (!response.ok) {
        throw new RequestError(`${method} ${url}: HTTP ${response.status}: ${redact(text.slice(0, 2000), token)}`, {
            status: response.status, unknownOutcome: method === 'POST' && response.status >= 500,
        });
    }
    try {
        return JSON.parse(text);
    } catch {
        throw new RequestError(`${method} ${url}: response is not valid JSON.${method === 'POST' ? ' Upload outcome is unknown; reconcile the pending receipt before rerunning.' : ''}`, {
            unknownOutcome: method === 'POST',
        });
    }
}

function zipEntries(bytes) {
    let end = -1;
    for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
        if (bytes.readUInt32LE(i) === 0x06054b50 && i + 22 + bytes.readUInt16LE(i + 20) === bytes.length) { end = i; break; }
    }
    if (end < 0) throw new Error('JAR has no ZIP central directory.');
    const count = bytes.readUInt16LE(end + 10), offset = bytes.readUInt32LE(end + 16);
    if (count === 0xffff || offset === 0xffffffff) throw new Error('ZIP64 JARs are not supported.');
    const entries = new Map();
    for (let n = 0, cursor = offset; n < count; n++) {
        if (cursor + 46 > end || bytes.readUInt32LE(cursor) !== 0x02014b50) throw new Error('Corrupt ZIP central directory.');
        const nameLength = bytes.readUInt16LE(cursor + 28);
        const next = cursor + 46 + nameLength + bytes.readUInt16LE(cursor + 30) + bytes.readUInt16LE(cursor + 32);
        if (next > end) throw new Error('Truncated ZIP central directory.');
        const name = bytes.toString('utf8', cursor + 46, cursor + 46 + nameLength);
        if (entries.has(name)) throw new Error(`Duplicate JAR entry: ${name}`);
        entries.set(name, { method: bytes.readUInt16LE(cursor + 10), size: bytes.readUInt32LE(cursor + 20), offset: bytes.readUInt32LE(cursor + 42) });
        cursor = next;
    }
    return entries;
}

function zipRead(bytes, entry) {
    if (!entry) throw new Error('Required JAR entry is missing.');
    const position = entry.offset;
    if (position + 30 > bytes.length || bytes.readUInt32LE(position) !== 0x04034b50) throw new Error('Corrupt ZIP local header.');
    const start = position + 30 + bytes.readUInt16LE(position + 26) + bytes.readUInt16LE(position + 28);
    if (start + entry.size > bytes.length) throw new Error('Truncated JAR entry.');
    const compressed = bytes.subarray(start, start + entry.size);
    if (entry.method === 0) return compressed;
    if (entry.method === 8) return inflateRawSync(compressed);
    throw new Error(`Unsupported ZIP compression method: ${entry.method}`);
}

function inspectJar(bytes, target, modVersion) {
    const zip = zipEntries(bytes);
    let id, version, minecraft;
    const ends = target.range.split('-');
    const expectedRange = target.loader === 'fabric'
        ? (ends.length === 2 ? `>=${ends[0]} <=${ends[1]}` : target.range)
        : (ends.length === 2 ? `[${ends[0]},${ends[1]}]` : `[${target.range}]`);
    if (target.loader === 'fabric') {
        const metadata = JSON.parse(zipRead(bytes, zip.get('fabric.mod.json')).toString('utf8'));
        id = metadata.id;
        version = metadata.version;
        minecraft = metadata.depends?.minecraft;
    } else {
        const filename = target.loader === 'neoforge' ? 'META-INF/neoforge.mods.toml' : 'META-INF/mods.toml';
        const tables = zipRead(bytes, zip.get(filename)).toString('utf8').split(/^\s*(?=\[\[)/m);
        const field = (table, key) => table.match(new RegExp(`^\\s*${key}\\s*=\\s*"([^\"]*)"`, 'm'))?.[1];
        const mod = tables.find((table) => /^\[\[mods\]\]/.test(table)) ?? '';
        const dependency = tables.find((table) => /^\[\[dependencies\./.test(table) && field(table, 'modId') === 'minecraft') ?? '';
        id = field(mod, 'modId');
        version = field(mod, 'version');
        minecraft = field(dependency, 'versionRange');
    }
    if (id !== MOD_ID || version !== modVersion || minecraft !== expectedRange) {
        throw new Error(`${target.filename}: loader metadata must declare ${MOD_ID} ${modVersion} for ${expectedRange}; got ${id} ${version} for ${JSON.stringify(minecraft)}.`);
    }
    let classes = 0;
    for (const [name, entry] of zip) {
        if (!name.endsWith('.class')) continue;
        const data = zipRead(bytes, entry);
        if (data.length < 8 || data.readUInt32BE(0) !== 0xcafebabe) throw new Error(`${target.filename}: invalid class ${name}.`);
        if (data.readUInt16BE(6) > target.javaRelease + 44) throw new Error(`${target.filename}: ${name} requires newer Java than java.release=${target.javaRelease}.`);
        classes++;
    }
    if (!classes) throw new Error(`${target.filename}: JAR contains no compiled classes.`);
    if (!zip.has('LICENSE_typinganimation')) throw new Error(`${target.filename}: JAR is missing LICENSE_typinganimation.`);
    return { minecraft, classes };
}

/** Validate local release files and prepare author-API metadata. No writes or remote mutations.
 * Options: token?, projectId?, only? (target-name array), gameVersions? (author API response),
 * manifest? (Mojang manifest), root?, fetchImpl?, timeoutMs?, apiBase?. No token enters the returned plan.
 * Without a token or supplied gameVersions, the local dry run reports that CurseForge tags remain unverified.
 */
export async function prepareRelease(options = {}) {
    const root = path.resolve(options.root ?? ROOT);
    const base = apiBase(options.apiBase);
    const gradle = await readText(path.join(root, 'scripts', 'target-common.gradle'));
    const modVersion = gradle.match(/^\s*def\s+MOD_VERSION\s*=\s*['"]([^'"]+)['"]/m)?.[1];
    if (!modVersion || !/^[\w.+-]+$/.test(modVersion)) throw new Error('Cannot find a safe MOD_VERSION in scripts/target-common.gradle.');
    const project = JSON.parse(await readText(path.join(root, 'modrinth', 'project.json')));
    const changelog = (await readText(path.join(root, 'curseforge', 'changelog.md'))).trim();
    if (!changelog) throw new Error('curseforge/changelog.md must not be empty.');
    const releaseType = project.version_type ?? 'release';
    if (!['release', 'beta', 'alpha'].includes(releaseType)) throw new Error(`Unknown release type: ${releaseType}`);
    const extraLoaders = project.extra_loaders ?? {};
    if (!extraLoaders || Array.isArray(extraLoaders) || typeof extraLoaders !== 'object') throw new Error('modrinth/project.json extra_loaders must be an object.');
    const targets = [];
    for (const directory of await readdir(path.join(root, 'targets'), { withFileTypes: true })) {
        if (!directory.isDirectory()) continue;
        const props = properties(await readText(path.join(root, 'targets', directory.name, 'target.properties')));
        const range = props['mc.range'], loader = props.loader, javaRelease = Number(props['java.release']);
        const ends = (range ?? '').split('-');
        if (ends.length > 2 || ends.some((end) => !RELEASE_NAME.test(end)) || compareVersions(ends[0], ends.at(-1)) > 0) {
            throw new Error(`${directory.name}: invalid mc.range ${JSON.stringify(range)}.`);
        }
        if (!LOADER_ORDER.includes(loader) || !Number.isInteger(javaRelease) || javaRelease < 8) throw new Error(`${directory.name}: invalid loader or java.release.`);
        const extras = extraLoaders[directory.name] ?? [];
        if (!Array.isArray(extras) || extras.some((extra) => !Object.hasOwn(LOADER_NAMES, extra))) throw new Error(`${directory.name}: invalid extra_loaders.`);
        const filename = `${MOD_ID}-${modVersion}+${range}-${loader}.jar`;
        targets.push({ name: directory.name, loader, range, javaRelease, filename,
            filePath: path.join(root, 'dist', filename), loaders: [...new Set([loader, ...extras])] });
    }
    if (!targets.length) throw new Error('No targets/*/target.properties found.');
    for (const name of Object.keys(extraLoaders)) {
        if (!targets.some((target) => target.name === name)) throw new Error(`extra_loaders refers to unknown target ${name}.`);
    }
    if (new Set(targets.map((target) => target.filename)).size !== targets.length) throw new Error('Two targets produce the same release filename.');
    targets.sort((a, b) => compareVersions(a.range.split('-').at(-1), b.range.split('-').at(-1)) || LOADER_ORDER.indexOf(a.loader) - LOADER_ORDER.indexOf(b.loader));
    const selectedNames = options.only === undefined ? null : new Set(options.only);
    if (selectedNames) {
        if (!Array.isArray(options.only) || !selectedNames.size) throw new Error('only must be a nonempty array of target names.');
        for (const name of selectedNames) if (!targets.some((target) => target.name === name)) throw new Error(`Unknown target: ${name}`);
    }
    const network = { fetchImpl: options.fetchImpl, timeoutMs: options.timeoutMs };
    const manifest = options.manifest ?? await request(MANIFEST_URL, network);
    if (!Array.isArray(manifest?.versions)) throw new Error('Mojang manifest response has no versions array.');
    const minecraftReleases = [...new Set(manifest.versions.filter((entry) => entry.type === 'release' && RELEASE_NAME.test(entry.id)).map((entry) => entry.id))];
    let versionsResponse = options.gameVersions;
    if (versionsResponse === undefined && options.token) versionsResponse = await request(`${base}/game/versions`, { ...network, token: options.token });
    const gameVersions = Array.isArray(versionsResponse) ? versionsResponse : versionsResponse?.data;
    if (versionsResponse !== undefined && !Array.isArray(gameVersions)) throw new Error('CurseForge game versions response must be an array (or data array).');
    const tagNames = gameVersions ? new Set(gameVersions.map((entry) => entry.name)) : null;
    const errors = [], versions = [];
    // Inspect the complete release, even when --only selects a small upload batch.
    for (const target of targets) {
        try {
            const ends = target.range.split('-');
            for (const end of ends) if (!minecraftReleases.includes(end)) throw new Error(`${target.name}: ${end} is not a release in Mojang's manifest.`);
            const minecraftVersions = minecraftReleases.filter((version) => compareVersions(version, ends[0]) >= 0 && compareVersions(version, ends.at(-1)) <= 0).sort(compareVersions);
            const gameVersionNames = ['Client', ...minecraftVersions, ...target.loaders.map((loader) => LOADER_NAMES[loader]), `Java ${target.javaRelease}`];
            if (tagNames) for (const name of gameVersionNames) if (!tagNames.has(name)) throw new Error(`${target.name}: CurseForge has no game version/tag named ${name}.`);
            const bytes = await readFile(target.filePath);
            if (!bytes.length || bytes.length >= 2 * 1024 ** 3) throw new Error(`${target.filename}: JAR must be nonempty and smaller than 2 GiB.`);
            const inspected = inspectJar(bytes, target, modVersion);
            const metadata = { changelog, changelogType: 'markdown', displayName: `${project.title ?? 'Typing Animation'} ${modVersion} — ${target.range} (${LOADER_NAMES[target.loader]})`,
                gameVersionNames, releaseType, isMarkedForManualRelease: false };
            if (target.loader === 'fabric') metadata.relations = { projects: [{ slug: 'modmenu', type: 'optionalDependency' }] };
            const prepared = { ...target, minecraftVersions, size: bytes.length, sha512: hash(bytes), metadata, metadataSha512: hash(JSON.stringify(metadata)), ...inspected };
            if (!selectedNames || selectedNames.has(target.name)) versions.push(prepared);
        } catch (error) {
            errors.push(`${target.name}: ${error.message}`);
        }
    }
    if (errors.length) throw new Error(`Release validation failed:\n${errors.map((error) => `  ${error}`).join('\n')}`);
    return { modVersion, root, totalTargets: targets.length, versions, tagNamesValidated: Boolean(tagNames),
        projectId: options.projectId === undefined ? null : projectId(options.projectId),
        receiptPath: path.join(root, 'curseforge', 'releases', `${modVersion}.json`),
        warnings: tagNames ? [] : ['CurseForge tag names have not been checked. Supply CURSEFORGE_TOKEN or gameVersions for authenticated tag validation.'] };
}

async function readReceipt(filename, modVersion, id) {
    let text;
    try { text = await readText(filename); }
    catch (error) {
        if (error.code === 'ENOENT') return { version: modVersion, projectId: id, files: [], pending: null };
        throw error;
    }
    const receipt = JSON.parse(text);
    if (receipt.version !== modVersion || String(receipt.projectId) !== id || !Array.isArray(receipt.files)) throw new Error(`${filename}: receipt belongs to another project/release or has an invalid files array.`);
    const filenames = new Set();
    for (const file of receipt.files) {
        if (String(file.projectId) !== id || typeof file.filename !== 'string' || !/^[a-f0-9]{128}$/.test(file.sha512 ?? '') || !Number.isSafeInteger(file.fileId) || file.fileId <= 0 || filenames.has(file.filename)) {
            throw new Error(`${filename}: invalid or duplicate upload receipt.`);
        }
        filenames.add(file.filename);
    }
    return receipt;
}

async function saveReceipt(filename, receipt) {
    const temporary = `${filename}.${randomUUID()}.tmp`;
    await writeFile(temporary, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
    await rename(temporary, filename);
}

/** Revalidate and upload sequentially to an existing project, recording every success immediately.
 * Required: {token, projectId}. Supports prepareRelease options and onProgress(message).
 * Pending requests block later runs until the operator checks the author dashboard and reconciles the receipt.
 */
export async function publishRelease(options = {}) {
    const id = projectId(options.projectId);
    if (typeof options.token !== 'string' || !options.token.trim()) throw new Error('CurseForge author API token is required.');
    const release = await prepareRelease({ ...options, projectId: id });
    if (!release.tagNamesValidated) throw new Error('Publishing requires validated CurseForge tag names.');
    await mkdir(path.dirname(release.receiptPath), { recursive: true });
    const lockPath = `${release.receiptPath}.lock`;
    let lock;
    try { lock = await open(lockPath, 'wx'); }
    catch (error) {
        if (error.code === 'EEXIST') throw new Error(`Release is locked: ${lockPath}. Check for another publisher or an interrupted process before removing this lock.`);
        throw error;
    }
    const progress = (message) => options.onProgress?.(redact(message, options.token));
    try {
        const receipt = await readReceipt(release.receiptPath, release.modVersion, id);
        if (receipt.pending) throw new Error(`Unresolved upload for ${receipt.pending.filename}. Check the project's Files tab, then reconcile pending in ${release.receiptPath}; no uploads were attempted.`);
        // Check all receipts before attempting the first mutation.
        for (const file of release.versions) {
            const existing = receipt.files.find((entry) => entry.filename === file.filename);
            if (existing && (existing.sha512 !== file.sha512 || (existing.metadataSha512 && existing.metadataSha512 !== file.metadataSha512))) throw new Error(`${file.filename}: existing receipt differs from this artifact or metadata. Reconcile it; files are never reuploaded to update metadata.`);
        }
        let uploaded = 0, skipped = 0;
        for (const [index, file] of release.versions.entries()) {
            const existing = receipt.files.find((entry) => entry.filename === file.filename);
            if (existing) { skipped++; progress(`[${index + 1}/${release.versions.length}] Skip ${file.filename}: file ${existing.fileId} already recorded.`); continue; }
            const bytes = await readFile(file.filePath);
            if (hash(bytes) !== file.sha512) throw new Error(`${file.filename} changed after validation; stopped before uploading it.`);
            const form = new FormData();
            form.append('metadata', JSON.stringify(file.metadata));
            form.append('file', new Blob([bytes], { type: 'application/java-archive' }), file.filename);
            receipt.pending = { filename: file.filename, target: file.name, sha512: file.sha512, metadataSha512: file.metadataSha512, projectId: id, startedAt: new Date().toISOString() };
            await saveReceipt(release.receiptPath, receipt);
            progress(`[${index + 1}/${release.versions.length}] Upload ${file.filename}.`);
            let result;
            try {
                result = await request(`${apiBase(options.apiBase)}/projects/${id}/upload-file`, {
                    token: options.token, method: 'POST', body: form, timeoutMs: options.uploadTimeoutMs ?? 120000, fetchImpl: options.fetchImpl,
                });
            } catch (error) {
                if (error instanceof RequestError && !error.unknownOutcome) {
                    receipt.pending = null;
                    await saveReceipt(release.receiptPath, receipt);
                }
                throw error;
            }
            if (!Number.isSafeInteger(result?.id) || result.id <= 0) throw new Error(`${file.filename}: upload response has no valid file ID. Outcome is unknown; pending remains in ${release.receiptPath}.`);
            const completed = { ...receipt.pending, fileId: result.id, uploadedAt: new Date().toISOString() };
            receipt.files.push(completed);
            receipt.pending = null;
            try { await saveReceipt(release.receiptPath, receipt); }
            catch (error) { throw new Error(`${file.filename} uploaded as file ${result.id}, but saving its receipt failed: ${error.message}. Reconcile the pending receipt before rerunning.`); }
            uploaded++;
            progress(`Recorded CurseForge file ${result.id}.`);
        }
        return { projectId: id, modVersion: release.modVersion, uploaded, skipped, receiptPath: release.receiptPath, files: receipt.files };
    } finally {
        await lock.close();
        await unlink(lockPath);
    }
}

const HELP = `Usage: node scripts/curseforge-publish.mjs [--dry-run | --publish] [--only target,...] [--json] [--project ID]
Default: validate and preview without uploading. CURSEFORGE_TOKEN enables tag validation.
Publish requires CURSEFORGE_TOKEN and CURSEFORGE_PROJECT (or --project ID).
Project creation and page editing use the CurseForge author dashboard.
See curseforge/README.md for receipts and interrupted-upload recovery.`;

async function main(argv) {
    const options = { token: process.env.CURSEFORGE_TOKEN, projectId: process.env.CURSEFORGE_PROJECT, onProgress: console.log };
    let mode = 'dry-run', explicitMode = null, json = false;
    for (let i = 0; i < argv.length; i++) {
        const argument = argv[i];
        if (argument === '--help' || argument === '-h') { console.log(HELP); return; }
        if (argument === '--publish' || argument === '--dry-run') {
            if (explicitMode && explicitMode !== argument) throw new Error('Choose --dry-run or --publish.');
            explicitMode = argument;
            mode = argument.slice(2);
        } else if (argument === '--json') json = true;
        else if (argument === '--only' || argument === '--project') {
            const value = argv[++i];
            if (!value || value.startsWith('--')) throw new Error(`${argument} requires a value.`);
            if (argument === '--only') options.only = value.split(',').map((name) => name.trim()).filter(Boolean);
            else options.projectId = value;
        } else throw new Error(`Unknown argument: ${argument}`);
    }
    if (mode === 'publish' && json) throw new Error('--json is a dry-run option.');
    if (mode === 'publish') {
        const result = await publishRelease(options);
        console.log(`Uploaded ${result.uploaded}; skipped ${result.skipped}. Receipts: ${result.receiptPath}`);
        return;
    }
    const release = await prepareRelease(options);
    for (const warning of release.warnings) console.log(`WARNING: ${warning}`);
    console.log(`Dry run: ${release.versions.length} of ${release.totalTargets} JARs for ${release.modVersion}; CurseForge tags ${release.tagNamesValidated ? 'validated' : 'unverified'}.`);
    if (json) console.log(JSON.stringify(release, null, 2));
    else for (const file of release.versions) console.log(`${file.name}: ${file.metadata.gameVersionNames.join(', ')}; ${file.filename}`);
}

if (typeof process !== 'undefined' && process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main(process.argv.slice(2)).catch((error) => {
        console.error(redact(error.message, process.env.CURSEFORGE_TOKEN));
        process.exitCode = 1;
    });
}
