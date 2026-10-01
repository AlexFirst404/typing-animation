#!/usr/bin/env node
// Modrinth release helper for Typing Animation. Node >= 18, no dependencies (global fetch / FormData / Blob).
//
//   node scripts/modrinth-publish.mjs                     dry run (default): validate everything and print the
//                                                         version table; needs no token and changes nothing
//   node scripts/modrinth-publish.mjs --json              dry run, plus every version payload as JSON
//   node scripts/modrinth-publish.mjs --create-project    create a draft with icon, gallery and description (MODRINTH_TOKEN)
//   node scripts/modrinth-publish.mjs --sync-project      update title/summary/body/categories/license/links, the icon,
//                                                         and upload new gallery images (MODRINTH_TOKEN, MODRINTH_PROJECT)
//   node scripts/modrinth-publish.mjs --publish           upload every version that is not on Modrinth yet
//                                                         (MODRINTH_TOKEN, MODRINTH_PROJECT)
//
// Options:
//   --only <target,...>   limit the dry run / --publish to these targets (folder names under targets/, e.g. 26.3-neoforge)
//   --staging             use https://staging-api.modrinth.com/v2 (MODRINTH_API_URL overrides the API base URL)
//
// Inputs: modrinth/project.json (+ description.md, changelog.md, icon.png, gallery/gallery.json), targets/*/target.properties,
// the release jars in dist/ (bash scripts/build-all.sh) and the mod version in scripts/target-common.gradle.
// The token is read from the MODRINTH_TOKEN environment variable only and is never printed.
// Field names and limits follow Modrinth API v2 (https://docs.modrinth.com/api/) and its server-side validation.

import { readFile, readdir, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { inflateRawSync } from 'node:zlib';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const KIT_DIR = path.join(ROOT, 'modrinth');
const DIST_DIR = path.join(ROOT, 'dist');
const TARGETS_DIR = path.join(ROOT, 'targets');
const COMMON_GRADLE = path.join(ROOT, 'scripts', 'target-common.gradle');

const API_PRODUCTION = 'https://api.modrinth.com/v2';
const API_STAGING = 'https://staging-api.modrinth.com/v2';
const MOJANG_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const MOD_ID = 'typinganimation';

const LOADER_NAMES = { neoforge: 'NeoForge', forge: 'Forge', fabric: 'Fabric', quilt: 'Quilt' };
// Upload order inside one Minecraft range. Versions are uploaded oldest Minecraft first, so on Modrinth (newest upload
// on top) the newest Minecraft versions come first.
const LOADER_ORDER = ['fabric', 'forge', 'neoforge'];

// Server-side limits (labrinth routes/v2/project_creation.rs, version_creation.rs, projects.rs, util/validate.rs).
const RE_SLUG = /^[a-zA-Z0-9._-]{3,64}$/;
const RE_VERSION_NUMBER = /^[a-zA-Z0-9!@$()`.+,_"-]{1,32}$/;
const TITLE_LEN = [3, 64];
const SUMMARY_LEN = [3, 255];            // 255 on creation (256 when editing): keep to the stricter one
const BODY_MAX = 65536;
const VERSION_NAME_LEN = [1, 64];
const CHANGELOG_MAX = 65536;
const CATEGORIES_MAX = 3;
const ADDITIONAL_CATEGORIES_MAX = 256;
const ICON_MAX = 256 * 1024;             // PATCH /project/{id}/icon: "up to 256KiB" (creation allows 512 KiB)
const GALLERY_MAX = 5 * 1024 * 1024;     // 5 MiB per gallery image
const GALLERY_TITLE_LEN = [1, 255];
const GALLERY_DESCRIPTION_LEN = [1, 2048];
const URL_MAX = 2048;
const SIDE_TYPES = ['required', 'optional', 'unsupported'];
const ENVIRONMENTS = ['client_and_server', 'client_only', 'client_only_server_optional', 'singleplayer_only',
    'server_only', 'server_only_client_optional', 'dedicated_server_only', 'client_or_server',
    'client_or_server_prefers_both'];
const DEPENDENCY_TYPES = ['required', 'optional', 'incompatible', 'embedded'];
const VERSION_TYPES = ['release', 'beta', 'alpha'];
const IMAGE_TYPES = { png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp', bmp: 'image/bmp' };
const LINK_FIELDS = ['source_url', 'issues_url', 'wiki_url', 'discord_url', 'license_url'];

// Which files Modrinth's upload validators look for (labrinth src/validate/*.rs). The primary loader of a target must
// have its own metadata file; an extra loader only needs to pass Modrinth's check.
const LOADER_METADATA = {
    fabric: ['fabric.mod.json'],
    quilt: ['quilt.mod.json', 'fabric.mod.json'],
    forge: ['META-INF/mods.toml'],
    neoforge: ['META-INF/neoforge.mods.toml', 'META-INF/mods.toml'],
};
const MODRINTH_ACCEPTS = {
    fabric: (z) => z.has('fabric.mod.json'),
    quilt: (z) => z.has('quilt.mod.json') || z.has('fabric.mod.json'),
    forge: (z) => z.has('META-INF/mods.toml') || z.has('META-INF/MANIFEST.MF') || [...z.keys()].some((n) => n.endsWith('.class')),
    neoforge: (z) => z.has('META-INF/mods.toml') || z.has('META-INF/neoforge.mods.toml') || z.has('META-INF/MANIFEST.MF')
        || [...z.keys()].some((n) => n.endsWith('.class')),
};

// ---------------------------------------------------------------------------------------------------------------- misc

class Fail extends Error {}

let TOKEN = null;       // set only for the modes that need it; used for redaction of anything we print
let API_BASE = API_PRODUCTION;
let USER_AGENT = 'typing-animation-release-kit';

const log = (...a) => console.log(...a.map((x) => redact(x)));
const redact = (x) => (TOKEN && typeof x === 'string' ? x.split(TOKEN).join('<MODRINTH_TOKEN>') : x);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const rel = (p) => path.relative(ROOT, p).split(path.sep).join('/');
const kib = (n) => `${(n / 1024).toFixed(n < 10240 ? 1 : 0)} KiB`;
const isHttpsUrl = (s) => { try { return new URL(s).protocol === 'https:'; } catch { return false; } };
const lenIn = (s, [min, max]) => typeof s === 'string' && [...s].length >= min && [...s].length <= max;
const stripBom = (s) => (s.charCodeAt(0) === 0xfeff ? s.slice(1) : s);
const readText = async (p) => stripBom(await readFile(p, 'utf8')).replace(/\r\n?/g, '\n');

function compareVersions(a, b) {
    const x = a.split('.').map(Number), y = b.split('.').map(Number);
    for (let i = 0; i < Math.max(x.length, y.length); i++) {
        const d = (x[i] ?? 0) - (y[i] ?? 0);
        if (d !== 0) return Math.sign(d);
    }
    return 0;
}
const RE_RELEASE_ID = /^\d+(\.\d+)+$/;

class Problems {
    errors = [];
    warnings = [];
    error(msg) { this.errors.push(msg); }
    warn(msg) { this.warnings.push(msg); }
    print() {
        for (const w of this.warnings) log(`WARNING: ${w}`);
        for (const e of this.errors) log(`ERROR:   ${e}`);
    }
}

// ---------------------------------------------------------------------------------------------------------------- args

const USAGE = `Usage: node scripts/modrinth-publish.mjs [mode] [options]

Modes (default: dry run, no token needed, nothing is changed):
  --create-project   create the project on Modrinth as a draft (MODRINTH_TOKEN)
  --sync-project     update title, summary, body, categories, license, links, icon; add new gallery images
                     (MODRINTH_TOKEN, MODRINTH_PROJECT = project id or slug)
  --publish          upload every version not yet on the project (MODRINTH_TOKEN, MODRINTH_PROJECT)

Options:
  --only <t1,t2>     only these targets (folder names under targets/, e.g. 26.3-neoforge); dry run and --publish
  --json             dry run: also print every version payload as JSON
  --staging          use ${API_STAGING} (MODRINTH_API_URL overrides the API base URL)
  -h, --help         this help

See modrinth/README.md for the whole release procedure.`;

function parseArgs(argv) {
    const modes = { '--dry-run': 'dry-run', '--create-project': 'create-project', '--sync-project': 'sync-project', '--publish': 'publish' };
    const o = { mode: 'dry-run', only: null, json: false, staging: false, help: false };
    let modeGiven = null;
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        if (Object.hasOwn(modes, a)) {
            if (modeGiven && modeGiven !== a) throw new Fail(`choose one mode (${modeGiven} and ${a} given)`);
            modeGiven = a;
            o.mode = modes[a];
        } else if (a === '--only' || a.startsWith('--only=')) {
            const v = a === '--only' ? argv[++i] : a.slice('--only='.length);
            if (!v || v.startsWith('--')) throw new Fail('--only needs a comma-separated list of targets, e.g. --only 26.3-neoforge,26.3-fabric');
            o.only = v.split(',').map((s) => s.trim()).filter(Boolean);
        } else if (a === '--json') {
            o.json = true;
        } else if (a === '--staging') {
            o.staging = true;
        } else if (a === '-h' || a === '--help') {
            o.help = true;
        } else {
            throw new Fail(`unknown argument "${a}" (see --help)`);
        }
    }
    if (o.only && !['dry-run', 'publish'].includes(o.mode)) throw new Fail('--only works with the dry run and --publish only');
    if (o.json && o.mode !== 'dry-run') throw new Fail('--json works with the dry run only');
    return o;
}

// ---------------------------------------------------------------------------------------------------------------- http

class ApiError extends Error {
    constructor(method, url, status, body) {
        super(`${method} ${url} failed: HTTP ${status}`);
        this.status = status;
        this.body = body;
    }
}

function rateLimitWait(res) {
    const reset = Number(res.headers.get('x-ratelimit-reset'));
    const seconds = Number.isFinite(reset) && reset >= 0 ? reset + 1 : 15;
    return Math.min(Math.max(seconds, 1), 120) * 1000;
}

/** One HTTP request with retries: 429 (always, it was not processed), network errors and 5xx (GET only). */
async function http(method, url, { token = null, json, body, contentType, maxAttempts = 6 } = {}) {
    const headers = { 'User-Agent': USER_AGENT, Accept: 'application/json' };
    if (token) headers.Authorization = token;
    let payload = body;
    if (json !== undefined) {
        headers['Content-Type'] = 'application/json';
        payload = JSON.stringify(json);
    } else if (contentType) {
        headers['Content-Type'] = contentType;
    }
    const shown = url.replace(API_BASE, '');
    for (let attempt = 1; ; attempt++) {
        let res;
        try {
            res = await fetch(url, { method, headers, body: payload });
        } catch (e) {
            if (method === 'GET' && attempt < maxAttempts) {
                await sleep(1000 * 2 ** (attempt - 1));
                continue;
            }
            throw new Fail(`${method} ${shown}: network error: ${e.cause?.message ?? e.message}`);
        }
        if (res.status === 429 && attempt < maxAttempts) {
            const wait = rateLimitWait(res);
            await res.arrayBuffer().catch(() => {});
            log(`    rate limited (HTTP 429); retrying ${method} ${shown} in ${Math.round(wait / 1000)} s`);
            await sleep(wait);
            continue;
        }
        if (res.status >= 500 && method === 'GET' && attempt < maxAttempts) {
            await res.arrayBuffer().catch(() => {});
            await sleep(1000 * 2 ** (attempt - 1));
            continue;
        }
        const text = await res.text();
        let data = null;
        if (text) {
            try { data = JSON.parse(text); } catch { data = text; }
        }
        if (!res.ok) throw new ApiError(method, shown, res.status, data);
        if (res.headers.get('x-ratelimit-remaining') === '0') {
            const wait = rateLimitWait(res);
            log(`    rate limit window used up; pausing ${Math.round(wait / 1000)} s`);
            await sleep(wait);
        }
        return data;
    }
}

const api = (method, p, opts) => http(method, API_BASE + p, opts);

function describeError(e) {
    if (e instanceof ApiError) {
        const body = typeof e.body === 'string' ? e.body.trim() : JSON.stringify(e.body, null, 2);
        const hint = e.status === 401 ? '\n  (401: the token is invalid, expired or lacks a scope this request needs; see modrinth/README.md)' : '';
        return `${e.message}\n  Modrinth says: ${String(body ?? '(empty response)').split('\n').join('\n  ')}${hint}`;
    }
    return e instanceof Fail ? e.message : (e.stack ?? String(e));
}

// ---------------------------------------------------------------------------------------------------------------- zip

/** Lists a zip's central directory: name -> { method, compSize, localOffset }. */
function listZip(buf) {
    let eocd = -1;
    for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
        if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error('not a zip/jar file (no end of central directory)');
    const count = buf.readUInt16LE(eocd + 10);
    const cdOffset = buf.readUInt32LE(eocd + 16);
    if (count === 0xffff || cdOffset === 0xffffffff) throw new Error('ZIP64 jars are not supported');
    const entries = new Map();
    for (let n = 0, p = cdOffset; n < count; n++) {
        if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('corrupt zip central directory');
        const nameLen = buf.readUInt16LE(p + 28), extraLen = buf.readUInt16LE(p + 30), commentLen = buf.readUInt16LE(p + 32);
        entries.set(buf.toString('utf8', p + 46, p + 46 + nameLen), {
            method: buf.readUInt16LE(p + 10), compSize: buf.readUInt32LE(p + 20), localOffset: buf.readUInt32LE(p + 42),
        });
        p += 46 + nameLen + extraLen + commentLen;
    }
    return entries;
}

function readZipEntry(buf, entry) {
    const lh = entry.localOffset;
    if (buf.readUInt32LE(lh) !== 0x04034b50) throw new Error('corrupt zip local header');
    const start = lh + 30 + buf.readUInt16LE(lh + 26) + buf.readUInt16LE(lh + 28);
    const raw = buf.subarray(start, start + entry.compSize);
    if (entry.method === 0) return raw;
    if (entry.method === 8) return inflateRawSync(raw);
    throw new Error(`unsupported zip compression method ${entry.method}`);
}

const readZipText = (buf, entry) => readZipEntry(buf, entry).toString('utf8');

// ------------------------------------------------------------------------------------------------ mixin bytecode check

const REDIRECT_DESC = 'Lorg/spongepowered/asm/mixin/injection/Redirect;';

/**
 * Names of the methods of a class file whose @Redirect has an array-valued `at` (element tag '['). javac writes that
 * form when it compiles against sponge-mixin 0.17.4+ (Fabric Loader 0.19.5), where Redirect.at() is At[]; the
 * MixinExtras 0.3.0-0.5.4 bundled with Fabric Loader 0.15.0-0.19.4 and Quilt Loader 0.30.1 then fails the mixin with a
 * ClassCastException. See scripts/target-common.gradle and docs/SPEC.md section 1.
 */
function arrayAtRedirects(cls) {
    if (cls.length < 10 || cls.readUInt32BE(0) !== 0xcafebabe) throw new Error('not a class file');
    let p = 8;
    const cpCount = cls.readUInt16BE(p);
    p += 2;
    const utf8 = [];
    for (let i = 1; i < cpCount; i++) {
        const tag = cls[p++];
        if (tag === 1) {
            const len = cls.readUInt16BE(p);
            utf8[i] = cls.toString('latin1', p + 2, p + 2 + len);   // descriptors and names we compare are ASCII
            p += 2 + len;
        } else if (tag === 5 || tag === 6) { p += 8; i++; }                  // long, double: two slots
        else if (tag === 3 || tag === 4 || (tag >= 9 && tag <= 12) || tag === 17 || tag === 18) p += 4;
        else if (tag === 7 || tag === 8 || tag === 16 || tag === 19 || tag === 20) p += 2;
        else if (tag === 15) p += 3;
        else throw new Error(`unknown constant pool tag ${tag}`);
    }
    p += 6;                                                                   // access, this_class, super_class
    p += 2 + 2 * cls.readUInt16BE(p);                                         // interfaces
    const skipMembers = () => {
        const count = cls.readUInt16BE(p);
        p += 2;
        for (let i = 0; i < count; i++) {
            p += 6;
            const attrs = cls.readUInt16BE(p);
            p += 2;
            for (let a = 0; a < attrs; a++) p += 6 + cls.readUInt32BE(p + 2);
        }
    };
    skipMembers();                                                            // fields
    const found = [];
    const methods = cls.readUInt16BE(p);
    p += 2;
    for (let m = 0; m < methods; m++) {
        const methodName = utf8[cls.readUInt16BE(p + 2)];
        p += 6;
        const attrs = cls.readUInt16BE(p);
        p += 2;
        for (let a = 0; a < attrs; a++) {
            const attrName = utf8[cls.readUInt16BE(p)];
            const end = p + 6 + cls.readUInt32BE(p + 2);
            if (attrName === 'RuntimeVisibleAnnotations' || attrName === 'RuntimeInvisibleAnnotations') {
                let q = p + 6;
                const skipValue = () => {
                    const tag = String.fromCharCode(cls[q++]);
                    if ('BCDFIJSZsc'.includes(tag)) q += 2;
                    else if (tag === 'e') q += 4;
                    else if (tag === '@') skipAnnotation();
                    else if (tag === '[') {
                        const n = cls.readUInt16BE(q);
                        q += 2;
                        for (let i = 0; i < n; i++) skipValue();
                    } else throw new Error(`unknown annotation element tag ${tag}`);
                };
                const skipAnnotation = () => {
                    const type = utf8[cls.readUInt16BE(q)];
                    const pairs = cls.readUInt16BE(q + 2);
                    q += 4;
                    for (let i = 0; i < pairs; i++) {
                        const key = utf8[cls.readUInt16BE(q)];
                        q += 2;
                        if (type === REDIRECT_DESC && key === 'at' && cls[q] === 0x5b) found.push(methodName);
                        skipValue();
                    }
                };
                const count = cls.readUInt16BE(q);
                q += 2;
                for (let i = 0; i < count; i++) skipAnnotation();
            }
            p = end;
        }
    }
    return found;
}

// ------------------------------------------------------------------------------------------------- version predicates

/** Maven range such as [1.21.6,1.21.8] or [26.3] (also unions of ranges) -> predicate, or null if not understood. */
function mavenRange(spec) {
    const s = spec.replace(/\s+/g, '');
    const parts = s.match(/[[(][^\])]*[\])]/g);
    if (!parts || parts.join(',') !== s) return null;
    const preds = [];
    for (const r of parts) {
        const inner = r.slice(1, -1);
        if (!inner.includes(',')) {
            if (!RE_RELEASE_ID.test(inner)) return null;
            preds.push((v) => compareVersions(v, inner) === 0);
            continue;
        }
        const [lo, hi] = inner.split(',');
        if ((lo && !RE_RELEASE_ID.test(lo)) || (hi && !RE_RELEASE_ID.test(hi))) return null;
        const loIncl = r[0] === '[', hiIncl = r.endsWith(']');
        preds.push((v) => (!lo || (loIncl ? compareVersions(v, lo) >= 0 : compareVersions(v, lo) > 0))
            && (!hi || (hiIncl ? compareVersions(v, hi) <= 0 : compareVersions(v, hi) < 0)));
    }
    return (v) => preds.some((p) => p(v));
}

/** Fabric dependency such as ">=1.21.6 <=1.21.8", "26.3" or an array of those -> predicate, or null if not understood. */
function fabricRange(dep) {
    const alternatives = [];
    for (const alt of Array.isArray(dep) ? dep : [dep]) {
        for (const part of String(alt).split('||')) {
            const preds = part.trim().split(/\s+/).filter(Boolean).map((tok) => {
                const m = tok.match(/^(>=|<=|>|<|=)?(\d+(?:\.\d+)*)$/);
                if (!m) return null;
                const [, op = '=', ver] = m;
                return (v) => {
                    const c = compareVersions(v, ver);
                    return op === '>=' ? c >= 0 : op === '<=' ? c <= 0 : op === '>' ? c > 0 : op === '<' ? c < 0 : c === 0;
                };
            });
            if (preds.length === 0 || preds.some((p) => !p)) return null;
            alternatives.push((v) => preds.every((p) => p(v)));
        }
    }
    return alternatives.length ? (v) => alternatives.some((a) => a(v)) : null;
}

/** Reads mod id, version, the declared Minecraft range and (fabric.mod.json) the fabricloader range from the jar's loader metadata. */
function readJarMetadata(buf, zip, loader) {
    const file = LOADER_METADATA[loader].find((f) => zip.has(f));
    if (!file) return null;
    const text = readZipText(buf, zip.get(file));
    if (file.endsWith('.json')) {
        const j = JSON.parse(text);
        const mc = j.depends?.minecraft ?? j.quilt_loader?.depends?.find?.((d) => d.id === 'minecraft')?.versions;
        return { file, id: j.id ?? j.quilt_loader?.id, version: j.version ?? j.quilt_loader?.version, mcSpec: mc, predicate: mc === undefined ? null : fabricRange(mc),
            fabricLoaderSpec: j.depends?.fabricloader };
    }
    // mods.toml / neoforge.mods.toml: the [[mods]] entry and the [[dependencies.<id>]] entry for minecraft
    const tables = text.split(/^\s*(?=\[\[)/m);
    const mods = tables.find((t) => /^\[\[mods\]\]/.test(t)) ?? '';
    const q = (t, key) => t.match(new RegExp(`^\\s*${key}\\s*=\\s*"([^"]*)"`, 'm'))?.[1];
    const mcDep = tables.find((t) => /^\[\[dependencies\./.test(t) && q(t, 'modId') === 'minecraft');
    const mc = mcDep ? q(mcDep, 'versionRange') : undefined;
    return { file, id: q(mods, 'modId'), version: q(mods, 'version'), mcSpec: mc, predicate: mc === undefined ? null : mavenRange(mc) };
}

// ------------------------------------------------------------------------------------------------------------ the kit

async function loadKit(problems) {
    const projectPath = path.join(KIT_DIR, 'project.json');
    if (!existsSync(projectPath)) throw new Fail(`missing ${rel(projectPath)}`);
    let project;
    try {
        project = JSON.parse(await readText(projectPath));
    } catch (e) {
        throw new Fail(`${rel(projectPath)} is not valid JSON: ${e.message}`);
    }
    const gradle = await readText(COMMON_GRADLE);
    const modVersion = gradle.match(/^\s*def\s+MOD_VERSION\s*=\s*['"]([^'"]+)['"]/m)?.[1];
    if (!modVersion) throw new Fail(`cannot find "def MOD_VERSION = '...'" in ${rel(COMMON_GRADLE)}`);

    const kitFile = (key, fallback) => path.join(KIT_DIR, project[key] ?? fallback);
    const need = async (p, reader) => {
        if (!existsSync(p)) { problems.error(`missing file ${rel(p)}`); return null; }
        return reader(p);
    };
    const kit = {
        project,
        modVersion,
        body: await need(kitFile('description_file', 'description.md'), readText),
        changelog: await need(kitFile('changelog_file', 'changelog.md'), readText),
        iconPath: kitFile('icon_file', 'icon.png'),
        icon: null,
        galleryPath: kitFile('gallery_file', 'gallery/gallery.json'),
        gallery: [],
    };
    kit.icon = await need(kit.iconPath, (p) => readFile(p));
    if (existsSync(kit.galleryPath)) {
        try {
            const g = JSON.parse(await readText(kit.galleryPath));
            kit.gallery = Array.isArray(g) ? g : g.images ?? [];
        } catch (e) {
            problems.error(`${rel(kit.galleryPath)} is not valid JSON: ${e.message}`);
        }
    }
    return kit;
}

// Description sources stay local and reproducible. Only these kit assets may be substituted with uploaded URLs.
function kitImageRef(file) {
    const ref = path.relative(KIT_DIR, file).split(path.sep).join('/');
    if (!ref || ref === '..' || ref.startsWith('../') || path.isAbsolute(ref) || !/^[\w./-]+$/.test(ref)) return null;
    return ref;
}

function descriptionAssets(kit) {
    const assets = new Map();
    const icon = kitImageRef(kit.iconPath);
    if (icon) assets.set(icon, { icon: true });
    for (const g of kit.gallery) {
        if (typeof g.file !== 'string') continue;
        const ref = kitImageRef(path.resolve(path.dirname(kit.galleryPath), g.file));
        if (ref) assets.set(ref, { title: g.title });
    }
    return assets;
}

/** Locate inline Markdown image destinations and HTML img src attributes, preserving the surrounding markup. */
function descriptionImages(body) {
    const images = [];
    // Deliberately reject reference-style images: their definitions may also be links and are not safely rewritten.
    const markdown = /!\[(?:\\.|[^\]\\])*\]\(\s*(<[^>\n]*>|[^\s()]*)\s*(?:(?:"[^"\n]*"|'[^'\n]*'|\([^\)\n]*\))\s*)?\)/y;
    for (const start of body.matchAll(/!\[/g)) {
        markdown.lastIndex = start.index;
        const match = markdown.exec(body);
        if (!match) throw new Fail('description.md: unsupported image syntax; use ![alt](gallery/file.gif) or <img src="icon.png">');
        const wrapped = match[1].startsWith('<');
        const offset = match[0].indexOf('](') + 2;
        const index = start.index + offset + match[0].slice(offset).indexOf(match[1]) + Number(wrapped);
        images.push({ ref: wrapped ? match[1].slice(1, -1) : match[1], index,
            length: match[1].length - (wrapped ? 2 : 0), html: false });
    }
    for (const tag of body.matchAll(/<img\b[^>]*>/gi)) {
        const attrs = [...tag[0].matchAll(/\ssrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))/gi)];
        if (attrs.length !== 1 || /\ssrcset\s*=/i.test(tag[0])) {
            throw new Fail('description.md: each img must have exactly one src and no srcset');
        }
        const attr = attrs[0];
        const ref = attr[1] ?? attr[2] ?? attr[3];
        const valueOffset = attr[0].indexOf('=') + 1;
        const rest = attr[0].slice(valueOffset);
        images.push({ ref, index: tag.index + attr.index + valueOffset + rest.indexOf(ref), length: ref.length, html: true });
    }
    return images.sort((a, b) => a.index - b.index);
}

function validateDescriptionImages(kit) {
    const assets = descriptionAssets(kit);
    const images = descriptionImages(kit.body);
    for (const { ref } of images) {
        if (!isHttpsUrl(ref) && !assets.has(ref)) {
            throw new Fail(`description.md: image "${ref}" must be an https:// URL or an exact kit-relative path to icon_file or a gallery.json image`);
        }
    }
    return images;
}

function resolveDescription(kit, remote) {
    const assets = descriptionAssets(kit);
    let body = kit.body;
    for (const image of validateDescriptionImages(kit).reverse()) {
        if (isHttpsUrl(image.ref)) continue;
        const asset = assets.get(image.ref);
        const matches = asset.icon ? [] : (remote.gallery ?? []).filter((g) => g.title === asset.title);
        if (!asset.icon && matches.length !== 1) {
            throw new Fail(`description.md: expected one uploaded gallery image titled "${asset.title}", found ${matches.length}; body was not updated`);
        }
        const url = asset.icon ? remote.icon_url : matches[0].url;
        if (typeof url !== 'string' || !isHttpsUrl(url) || /[\s<>"'`\\]/.test(url)) {
            throw new Fail(`description.md: no safe HTTPS URL returned for "${image.ref}"; body was not updated`);
        }
        const escaped = image.html ? url.replace(/&/g, '&amp;') : url.replace(/\(/g, '%28').replace(/\)/g, '%29');
        body = body.slice(0, image.index) + escaped + body.slice(image.index + image.length);
    }
    if (body.length > BODY_MAX) throw new Fail(`resolved description is ${body.length} characters (max ${BODY_MAX}); body was not updated`);
    return body;
}

/** Checks project.json and the texts/images against Modrinth's rules (no network). */
async function validateKit(kit, problems) {
    const p = kit.project;
    const where = 'modrinth/project.json';
    if (!RE_SLUG.test(p.slug ?? '')) problems.error(`${where}: slug "${p.slug}" must be 3-64 chars of a-z A-Z 0-9 . _ -`);
    if (!lenIn(p.title, TITLE_LEN) || !String(p.title).trim()) problems.error(`${where}: title must be ${TITLE_LEN.join('-')} characters`);
    if (!lenIn(p.summary, SUMMARY_LEN)) problems.error(`${where}: summary must be ${SUMMARY_LEN.join('-')} characters (now ${[...(p.summary ?? '')].length})`);
    if ((p.project_type ?? 'mod') !== 'mod') problems.error(`${where}: project_type must be "mod"`);
    if (!Array.isArray(p.categories) || p.categories.length === 0 || p.categories.length > CATEGORIES_MAX) {
        problems.error(`${where}: categories must list 1-${CATEGORIES_MAX} categories`);
    }
    const extraCats = p.additional_categories ?? [];
    if (!Array.isArray(extraCats) || extraCats.length > ADDITIONAL_CATEGORIES_MAX) problems.error(`${where}: additional_categories must be an array`);
    for (const c of extraCats) if (p.categories?.includes(c)) problems.error(`${where}: "${c}" is in both categories and additional_categories`);
    if (!SIDE_TYPES.includes(p.client_side)) problems.error(`${where}: client_side must be one of ${SIDE_TYPES.join(', ')}`);
    if (!SIDE_TYPES.includes(p.server_side)) problems.error(`${where}: server_side must be one of ${SIDE_TYPES.join(', ')}`);
    if (!ENVIRONMENTS.includes(p.environment)) problems.error(`${where}: environment must be one of ${ENVIRONMENTS.join(', ')}`);
    if (!VERSION_TYPES.includes(p.version_type ?? 'release')) problems.error(`${where}: version_type must be one of ${VERSION_TYPES.join(', ')}`);
    if (typeof p.license_id !== 'string' || !p.license_id.trim()) problems.error(`${where}: license_id is required (SPDX id, e.g. MIT)`);
    for (const f of LINK_FIELDS) {
        const v = p[f];
        if (v === null || v === undefined) continue;
        if (typeof v !== 'string' || !isHttpsUrl(v) || v.length > URL_MAX) problems.error(`${where}: ${f} must be null or an https:// URL`);
    }
    if (p.extra_loaders !== undefined && (typeof p.extra_loaders !== 'object' || Array.isArray(p.extra_loaders) || p.extra_loaders === null)) {
        problems.error(`${where}: extra_loaders must be an object {"<target>": ["<loader>", ...]}`);
    }
    for (const [loader, deps] of Object.entries(p.dependencies_by_loader ?? {})) {
        if (!Array.isArray(deps)) { problems.error(`${where}: dependencies_by_loader.${loader} must be an array`); continue; }
        for (const d of deps) {
            if (!d.project_id && !d.version_id) problems.error(`${where}: a ${loader} dependency needs project_id or version_id`);
            if (!DEPENDENCY_TYPES.includes(d.dependency_type)) problems.error(`${where}: dependency_type must be one of ${DEPENDENCY_TYPES.join(', ')}`);
        }
    }

    if (kit.body !== null) {
        if (kit.body.trim().length === 0) problems.error('description.md is empty');
        if (kit.body.length > BODY_MAX) problems.error(`description.md is ${kit.body.length} characters (max ${BODY_MAX})`);
        try { validateDescriptionImages(kit); } catch (e) { problems.error(e.message); }
    }
    if (kit.changelog !== null) {
        if (kit.changelog.trim().length === 0) problems.error('changelog.md is empty');
        if (kit.changelog.length > CHANGELOG_MAX) problems.error(`changelog.md is ${kit.changelog.length} characters (max ${CHANGELOG_MAX})`);
    }
    if (kit.icon) {
        if (!kitImageRef(kit.iconPath)) problems.error('project.json: icon_file must point to an image inside modrinth/');
        const ext = path.extname(kit.iconPath).slice(1).toLowerCase();
        if (!IMAGE_TYPES[ext]) problems.error(`icon ${rel(kit.iconPath)}: unsupported type .${ext}`);
        if (kit.icon.length > ICON_MAX) problems.error(`icon ${rel(kit.iconPath)} is ${kib(kit.icon.length)} (max ${kib(ICON_MAX)})`);
        if (ext === 'png' && kit.icon.length > 24) {
            const w = kit.icon.readUInt32BE(16), h = kit.icon.readUInt32BE(20);
            if (w !== h) problems.warn(`icon is ${w}x${h}; Modrinth shows icons square`);
            kit.iconSize = `${w}x${h}`;
        }
    }
    const titles = new Set();
    let featured = 0;
    for (const [i, g] of kit.gallery.entries()) {
        const at = `gallery.json entry #${i + 1}`;
        if (typeof g.file !== 'string' || !g.file) { problems.error(`${at}: "file" is required`); continue; }
        const file = path.join(path.dirname(kit.galleryPath), g.file);
        if (!kitImageRef(file)) { problems.error(`${at}: image must be inside modrinth/`); continue; }
        const ext = path.extname(file).slice(1).toLowerCase();
        if (!IMAGE_TYPES[ext]) problems.error(`${at}: unsupported image type .${ext} (${Object.keys(IMAGE_TYPES).join(', ')})`);
        if (!existsSync(file)) problems.error(`${at}: missing file ${rel(file)}`);
        else if ((await stat(file)).size > GALLERY_MAX) problems.error(`${at}: ${rel(file)} is larger than 5 MiB`);
        if (!lenIn(g.title, GALLERY_TITLE_LEN)) problems.error(`${at}: "title" is required (1-255 characters; it is how --sync-project recognises uploaded images)`);
        else if (titles.has(g.title)) problems.error(`${at}: duplicate title "${g.title}"`);
        titles.add(g.title);
        if (g.description !== undefined && g.description !== null && !lenIn(g.description, GALLERY_DESCRIPTION_LEN)) problems.error(`${at}: description must be 1-2048 characters`);
        if (g.featured) featured++;
        if (g.ordering !== undefined && !Number.isInteger(g.ordering)) problems.error(`${at}: ordering must be an integer`);
    }
    if (featured > 1) problems.error('gallery.json: at most one image can be featured');
    // images in the gallery folder that gallery.json does not list would silently never be uploaded
    const galleryDir = path.dirname(kit.galleryPath);
    if (existsSync(galleryDir)) {
        const listed = new Set(kit.gallery.map((g) => (typeof g.file === 'string' ? path.normalize(g.file) : null)));
        for (const f of await readdir(galleryDir)) {
            if (IMAGE_TYPES[path.extname(f).slice(1).toLowerCase()] && !listed.has(path.normalize(f))) {
                problems.warn(`${rel(path.join(galleryDir, f))} is not listed in ${rel(kit.galleryPath)}, so --sync-project will not upload it`);
            }
        }
    }
}

/** Width x height of a PNG or GIF (for the dry-run listing), or '?' for other formats. */
function imageSize(buf) {
    if (buf.length >= 24 && buf.readUInt32BE(0) === 0x89504e47) return `${buf.readUInt32BE(16)}x${buf.readUInt32BE(20)}`;
    if (buf.length >= 10 && buf.toString('latin1', 0, 3) === 'GIF') return `${buf.readUInt16LE(6)}x${buf.readUInt16LE(8)}`;
    return '?';
}

async function printGallery(kit) {
    if (kit.gallery.length === 0) return;
    log('');
    log(`Gallery (${rel(kit.galleryPath)}), uploaded by --create-project / --sync-project in this order:`);
    const rows = [];
    for (const g of [...kit.gallery].sort((a, b) => (a.ordering ?? 0) - (b.ordering ?? 0))) {
        const file = path.join(path.dirname(kit.galleryPath), String(g.file ?? ''));
        const buf = existsSync(file) ? await readFile(file) : null;
        rows.push([String(g.ordering ?? ''), String(g.file ?? ''), buf ? imageSize(buf) : 'MISSING', buf ? kib(buf.length) : '',
            g.featured ? 'yes' : '', String(g.title ?? '')]);
    }
    const head = ['#', 'file', 'size', 'bytes', 'featured', 'title'];
    const widths = head.map((h, c) => Math.max(h.length, ...rows.map((r) => r[c].length)));
    const line = (r) => r.map((cell, c) => (c === 0 || c === 3 ? cell.padStart(widths[c]) : cell.padEnd(widths[c]))).join('  ').trimEnd();
    log(line(head));
    log(widths.map((w) => '-'.repeat(w)).join('  '));
    for (const r of rows) log(line(r));
}

// ------------------------------------------------------------------------------------------------------------ targets

function parseProperties(text) {
    const out = {};
    for (const line of text.split('\n')) {
        const m = line.match(/^\s*([^#!=\s][^=]*?)\s*=\s*(.*?)\s*$/);
        if (m) out[m[1]] = m[2];
    }
    return out;
}

async function loadTargets(kit, problems) {
    const targets = [];
    for (const name of (await readdir(TARGETS_DIR)).sort()) {
        const propsPath = path.join(TARGETS_DIR, name, 'target.properties');
        if (!existsSync(propsPath)) continue;
        const props = parseProperties(await readText(propsPath));
        const range = props['mc.range'], loader = props.loader, anchor = props['mc.anchor'];
        const where = `targets/${name}/target.properties`;
        if (!range || !loader || !anchor) { problems.error(`${where}: mc.range, mc.anchor and loader are required`); continue; }
        const ends = range.split('-');
        if (ends.length > 2 || ends.some((e) => !RE_RELEASE_ID.test(e))) { problems.error(`${where}: cannot parse mc.range "${range}"`); continue; }
        if (!LOADER_NAMES[loader] || loader === 'quilt') { problems.error(`${where}: unknown loader "${loader}"`); continue; }
        const jarName = `${MOD_ID}-${kit.modVersion}+${range}-${loader}.jar`;
        targets.push({ name, range, from: ends[0], to: ends[ends.length - 1], loader, anchor, group: props.group, jarName, jarPath: path.join(DIST_DIR, jarName) });
    }
    if (targets.length === 0) problems.error('no targets/*/target.properties found');
    targets.sort((a, b) => compareVersions(a.to, b.to) || LOADER_ORDER.indexOf(a.loader) - LOADER_ORDER.indexOf(b.loader));
    const seen = new Map();
    for (const t of targets) {
        const key = `${t.range}-${t.loader}`;
        if (seen.has(key)) problems.error(`targets ${seen.get(key)} and ${t.name} both build ${key}`);
        seen.set(key, t.name);
    }
    return targets;
}

/** Expands every target's mc.range into the Minecraft releases of the Mojang version manifest. */
function expandGameVersions(targets, manifest, problems) {
    const releases = manifest.versions.filter((v) => v.type === 'release').map((v) => v.id).filter((id) => RE_RELEASE_ID.test(id));
    const known = new Set(releases);
    for (const t of targets) {
        for (const end of new Set([t.from, t.to])) {
            if (!known.has(end)) problems.error(`targets/${t.name}: mc.range "${t.range}": ${end} is not a Minecraft release in the Mojang version manifest`);
        }
        if (compareVersions(t.from, t.to) > 0) problems.error(`targets/${t.name}: mc.range "${t.range}" is reversed`);
        t.gameVersions = releases.filter((id) => compareVersions(id, t.from) >= 0 && compareVersions(id, t.to) <= 0)
            .sort(compareVersions);
    }
    return releases;
}

/**
 * A Fabric jar whose @Redirect has an array-valued `at` crashes on Fabric Loader 0.15.0-0.19.4 and Quilt Loader 0.30.1
 * (MixinExtras < 0.5.5): refuse it unless its fabricloader dependency starts at 0.19.5.
 */
function checkFabricMixins(t, buf, zip, meta, problems) {
    const bad = [];
    for (const [name, entry] of zip) {
        if (!name.endsWith('.class')) continue;
        try {
            for (const method of arrayAtRedirects(readZipEntry(buf, entry))) bad.push(`${name.slice(0, -'.class'.length).split('/').pop()}.${method}`);
        } catch (e) {
            problems.warn(`dist/${t.jarName}: cannot read ${name}: ${e.message}`);
        }
    }
    if (bad.length === 0) return;
    const spec = meta.fabricLoaderSpec;
    const admits = spec === undefined ? () => true : fabricRange(spec);
    if (!admits) {
        problems.warn(`dist/${t.jarName}: has ${bad.length} @Redirect with an array-valued "at" and a fabricloader range `
            + `${JSON.stringify(spec)} this script cannot check; it must not admit Fabric Loader below 0.19.5`);
    } else if (admits('0.19.4') || admits('0.15.0')) {
        problems.error(`dist/${t.jarName}: ${bad.length} @Redirect with an array-valued "at" (${bad.slice(0, 3).join(', ')}`
            + `${bad.length > 3 ? ', ...' : ''}), but fabric.mod.json accepts fabricloader ${JSON.stringify(spec ?? '*')}: the mixin `
            + 'fails on Fabric Loader 0.15.0-0.19.4 and Quilt Loader 0.30.1 (MixinExtras < 0.5.5). Rebuild with the sponge-mixin '
            + 'pin of scripts/target-common.gradle (docs/SPEC.md section 1).');
    }
}

// Newest modification time under a path (files only; build output and dev run folders skipped), cached per path.
const SKIP_DIRS = new Set(['build', 'run', 'run-prod', 'runs', '.gradle', 'out', 'bin']);
const newestCache = new Map();
async function newestFile(p) {
    if (newestCache.has(p)) return newestCache.get(p);
    let best = null;
    if (existsSync(p)) {
        const st = await stat(p);
        if (st.isFile()) {
            best = { file: p, mtime: st.mtimeMs };
        } else if (st.isDirectory()) {
            for (const e of await readdir(p, { withFileTypes: true })) {
                if (e.isDirectory() && SKIP_DIRS.has(e.name)) continue;
                const sub = await newestFile(path.join(p, e.name));
                if (sub && (!best || sub.mtime > best.mtime)) best = sub;
            }
        }
    }
    newestCache.set(p, best);
    return best;
}

/** Warns when a release jar is older than the newest source file it is built from (it would not match the commit). */
async function checkJarAge(t, problems) {
    const targetDir = path.join(TARGETS_DIR, t.name);
    const sources = [path.join(ROOT, 'core', 'src', 'main'), path.join(ROOT, 'common-resources'), COMMON_GRADLE, path.join(targetDir, 'src'),
        ...['build.gradle', 'gradle.properties', 'settings.gradle', 'target.properties'].map((f) => path.join(targetDir, f))];
    if (t.group) sources.push(path.join(ROOT, 'mc', t.group, 'src', 'main'));
    let newest = null;
    for (const src of sources) {
        const n = await newestFile(src);
        if (n && (!newest || n.mtime > newest.mtime)) newest = n;
    }
    const jarTime = (await stat(t.jarPath)).mtimeMs;
    if (newest && newest.mtime > jarTime + 2000) {
        problems.warn(`dist/${t.jarName} is older than ${rel(newest.file)} (changed ${new Date(newest.mtime).toISOString()}); `
            + `rebuild it (bash scripts/build-all.sh ${t.name}) so that the published file matches the sources`);
    }
}

/** Checks each release jar: present, loader metadata Modrinth accepts, mod id/version, declared Minecraft range, mixin encoding, age. */
async function inspectJars(targets, allTargets, kit, releases, extraLoaders, problems) {
    const missing = [];
    for (const t of targets) {
        if (!existsSync(t.jarPath)) { missing.push(t); continue; }
        const buf = await readFile(t.jarPath);
        t.jarSize = buf.length;
        t.sha1 = createHash('sha1').update(buf).digest('hex');
        let zip;
        try {
            zip = listZip(buf);
        } catch (e) {
            problems.error(`dist/${t.jarName}: ${e.message}`);
            continue;
        }
        let meta = null;
        try {
            meta = readJarMetadata(buf, zip, t.loader);
        } catch (e) {
            problems.error(`dist/${t.jarName}: cannot read loader metadata: ${e.message}`);
            continue;
        }
        if (!meta) { problems.error(`dist/${t.jarName}: no ${LOADER_METADATA[t.loader].join(' / ')} (Modrinth rejects it as a ${LOADER_NAMES[t.loader]} file)`); continue; }
        for (const extra of extraLoaders[t.name] ?? []) {
            if (MODRINTH_ACCEPTS[extra] && !MODRINTH_ACCEPTS[extra](zip)) problems.error(`dist/${t.jarName}: Modrinth would not accept it as a ${extra} file`);
            else if (LOADER_METADATA[extra] && !LOADER_METADATA[extra].some((f) => zip.has(f))) {
                problems.warn(`dist/${t.jarName}: tagged ${extra} (extra_loaders) but has no ${LOADER_METADATA[extra].join(' / ')}; make sure it really loads on ${extra}`);
            }
        }
        if (meta.id !== MOD_ID) problems.error(`dist/${t.jarName}: ${meta.file} has mod id "${meta.id}", expected "${MOD_ID}"`);
        if (meta.version !== kit.modVersion) problems.error(`dist/${t.jarName}: ${meta.file} has version "${meta.version}", expected "${kit.modVersion}" (stale jar? rebuild with scripts/build-all.sh)`);
        if (t.loader === 'fabric') checkFabricMixins(t, buf, zip, meta, problems);
        await checkJarAge(t, problems);
        t.declared = meta.mcSpec;
        if (!meta.predicate) {
            problems.warn(`dist/${t.jarName}: cannot check the declared Minecraft range ${JSON.stringify(meta.mcSpec)}`);
            continue;
        }
        const rejected = (t.gameVersions ?? []).filter((v) => !meta.predicate(v));
        if (rejected.length) problems.error(`dist/${t.jarName}: declares minecraft ${JSON.stringify(meta.mcSpec)}, which excludes ${rejected.join(', ')} listed for ${t.range}`);
        const unlisted = releases.filter((v) => meta.predicate(v) && !(t.gameVersions ?? []).includes(v));
        if (unlisted.length) problems.warn(`dist/${t.jarName}: declares minecraft ${JSON.stringify(meta.mcSpec)}, which also admits ${unlisted.join(', ')} (not listed on Modrinth)`);
    }
    if (missing.length) {
        const cmd = ['bash scripts/build-all.sh', ...(missing.length < targets.length ? missing.map((t) => t.name) : [])].join(' ');
        problems.error(`${missing.length} release jar(s) missing in dist/; build them with "${cmd}":\n`
            + missing.map((t) => `           - dist/${t.jarName}  (targets/${t.name})`).join('\n'));
    }
    if (existsSync(DIST_DIR)) {
        const expected = new Set(allTargets.map((t) => t.jarName));
        const strays = (await readdir(DIST_DIR)).filter((f) => f.endsWith('.jar') && !expected.has(f));
        if (strays.length) problems.warn(`dist/ also has jars that are not part of this release (ignored): ${strays.join(', ')}`);
    }
}

// ------------------------------------------------------------------------------------------------------------ remote

async function fetchTags() {
    const [gameVersions, loaders, categories, licenses] = await Promise.all([
        api('GET', '/tag/game_version'), api('GET', '/tag/loader'), api('GET', '/tag/category'), api('GET', '/tag/license'),
    ]);
    return {
        gameVersions: new Map(gameVersions.map((g) => [g.version, g])),
        loaders: new Map(loaders.filter((l) => l.supported_project_types.includes('mod')).map((l) => [l.name, l])),
        categories: new Set(categories.filter((c) => c.project_type === 'mod').map((c) => c.name)),
        licenses: new Set(licenses.map((l) => l.short)),
    };
}

function validateProjectTags(kit, tags, problems) {
    const p = kit.project;
    for (const c of [...(p.categories ?? []), ...(p.additional_categories ?? [])]) {
        if (!tags.categories.has(c)) problems.error(`project.json: "${c}" is not a Modrinth mod category (${[...tags.categories].join(', ')})`);
    }
    if (p.license_id && !tags.licenses.has(p.license_id)) problems.warn(`project.json: license_id "${p.license_id}" is not in Modrinth's license list (it must still be a valid SPDX expression)`);
}

async function validateDependencies(kit, problems) {
    const ids = new Set(Object.values(kit.project.dependencies_by_loader ?? {}).flat().map((d) => d.project_id).filter(Boolean));
    const found = new Map();
    for (const id of ids) {
        try {
            const proj = await api('GET', `/project/${encodeURIComponent(id)}`);
            found.set(id, proj.title);
        } catch (e) {
            if (e instanceof ApiError && e.status === 404) problems.error(`dependency project ${id} does not exist on Modrinth`);
            else throw e;
        }
    }
    return found;
}

// ------------------------------------------------------------------------------------------------------------ versions

function buildVersions(kit, targets, extraLoaders, problems) {
    const p = kit.project;
    // featured: the newest Minecraft range of every loader (over all targets, not just --only)
    const newest = new Map();
    for (const t of targets) {
        const cur = newest.get(t.loader);
        if (!cur || compareVersions(t.to, cur.to) > 0) newest.set(t.loader, t);
    }
    return targets.map((t) => {
        const loaders = [t.loader, ...(extraLoaders[t.name] ?? []).filter((l) => l !== t.loader)];
        const dependencies = (p.dependencies_by_loader?.[t.loader] ?? []).map((d) => {
            const out = { dependency_type: d.dependency_type };
            if (d.project_id) out.project_id = d.project_id;
            if (d.version_id) out.version_id = d.version_id;
            if (d.file_name) out.file_name = d.file_name;
            return out;
        });
        const data = {
            name: `${kit.modVersion} ${LOADER_NAMES[t.loader]} ${t.range}`,
            version_number: `${kit.modVersion}+${t.range}-${t.loader}`,
            changelog: kit.changelog ?? '',
            dependencies,
            game_versions: t.gameVersions ?? [],
            version_type: p.version_type ?? 'release',
            loaders,
            featured: newest.get(t.loader) === t,
            status: 'listed',
            environment: p.environment,
            file_parts: ['file'],
            primary_file: 'file',
        };
        if (!RE_VERSION_NUMBER.test(data.version_number)) problems.error(`${t.name}: version_number "${data.version_number}" breaks Modrinth's rule (1-32 chars of a-z A-Z 0-9 ! @ $ ( ) \` . + , _ " -)`);
        if (!lenIn(data.name, VERSION_NAME_LEN)) problems.error(`${t.name}: version name "${data.name}" must be 1-64 characters`);
        if (data.game_versions.length === 0) problems.error(`${t.name}: no game versions`);
        return { target: t, data };
    });
}

function validateVersionTags(versions, tags, problems) {
    for (const { target: t, data } of versions) {
        for (const gv of data.game_versions) {
            const tag = tags.gameVersions.get(gv);
            if (!tag) problems.error(`${t.name}: game version ${gv} is not a Modrinth game_version tag`);
            else if (tag.version_type !== 'release') problems.error(`${t.name}: game version ${gv} is a ${tag.version_type} on Modrinth, not a release`);
        }
        for (const l of data.loaders) {
            if (!tags.loaders.has(l)) problems.error(`${t.name}: loader "${l}" is not a Modrinth mod loader tag`);
        }
    }
}

function printTable(versions, depNames) {
    const rows = versions.map(({ target: t, data }, i) => [
        String(i + 1),
        t.name,
        data.version_number,
        data.name,
        data.loaders.join(','),
        data.game_versions.join(', '),
        data.featured ? 'yes' : '',
        data.dependencies.map((d) => `${depNames.get(d.project_id) ?? d.project_id ?? d.version_id} (${d.dependency_type})`).join(', '),
        t.jarSize ? kib(t.jarSize) : 'MISSING',
    ]);
    const head = ['#', 'target', 'version_number', 'name', 'loaders', 'game_versions', 'featured', 'dependencies', 'jar'];
    const widths = head.map((h, c) => Math.max(h.length, ...rows.map((r) => r[c].length)));
    const line = (r) => r.map((cell, c) => (c === 0 || c === r.length - 1 ? cell.padStart(widths[c]) : cell.padEnd(widths[c]))).join('  ').trimEnd();
    log(line(head));
    log(widths.map((w) => '-'.repeat(w)).join('  '));
    for (const r of rows) log(line(r));
}

// ------------------------------------------------------------------------------------------------------------ modes

function requireEnv(name, why) {
    const v = (process.env[name] ?? '').trim();
    if (!v) throw new Fail(`${name} is not set; ${why} (see modrinth/README.md)`);
    return v;
}

function selectTargets(targets, only) {
    if (!only) return targets;
    const unknown = only.filter((n) => !targets.some((t) => t.name === n));
    if (unknown.length) throw new Fail(`--only: unknown target(s) ${unknown.join(', ')}; known: ${targets.map((t) => t.name).join(', ')}`);
    return targets.filter((t) => only.includes(t.name));
}

function abortOnProblems(problems) {
    problems.print();
    if (problems.errors.length) throw new Fail(`\n${problems.errors.length} problem(s) found; nothing was sent to Modrinth.`);
}

/** Everything the dry run checks; returns the prepared versions. Network: Mojang manifest + Modrinth public GETs. */
async function prepareRelease(kit, opts, problems) {
    await validateKit(kit, problems);
    const allTargets = await loadTargets(kit, problems);
    const extraLoaders = {};
    const rawExtra = kit.project.extra_loaders;
    for (const [name, list] of Object.entries(rawExtra && typeof rawExtra === 'object' && !Array.isArray(rawExtra) ? rawExtra : {})) {
        if (!allTargets.some((t) => t.name === name)) problems.error(`project.json: extra_loaders has unknown target "${name}"`);
        if (!Array.isArray(list) || list.some((l) => typeof l !== 'string')) {
            problems.error(`project.json: extra_loaders["${name}"] must be an array of loader tags`);
            continue;
        }
        extraLoaders[name] = list;
    }
    log(`Fetching the Mojang version manifest and Modrinth tags (${API_BASE}) ...`);
    const [manifest, tags] = await Promise.all([http('GET', MOJANG_MANIFEST), fetchTags()]);
    const releases = expandGameVersions(allTargets, manifest, problems);
    const selected = selectTargets(allTargets, opts.only);
    await inspectJars(selected, allTargets, kit, releases, extraLoaders, problems);
    const versions = buildVersions(kit, allTargets, extraLoaders, problems).filter((v) => selected.includes(v.target));
    validateProjectTags(kit, tags, problems);
    validateVersionTags(versions, tags, problems);
    const depNames = await validateDependencies(kit, problems);
    return { versions, allTargets, tags, depNames };
}

async function dryRun(kit, opts, problems) {
    const { versions, allTargets, tags, depNames } = await prepareRelease(kit, opts, problems);
    const p = kit.project;
    log('');
    log(`Project: "${p.title}" (slug ${p.slug}), mod version ${kit.modVersion}, license ${p.license_id}, `
        + `client ${p.client_side} / server ${p.server_side}, environment ${p.environment}`);
    log(`Summary (${[...(p.summary ?? '')].length}/${SUMMARY_LEN[1]}): ${p.summary}`);
    log(`Categories: ${(p.categories ?? []).join(', ')}${p.additional_categories?.length ? ` + ${p.additional_categories.join(', ')}` : ''}`
        + ` | body ${kit.body?.length ?? 0} chars | changelog ${kit.changelog?.length ?? 0} chars`
        + ` | icon ${kit.iconSize ?? '?'} ${kit.icon ? kib(kit.icon.length) : 'MISSING'} | gallery images: ${kit.gallery.length}`);
    const links = LINK_FIELDS.filter((f) => p[f]).map((f) => `${f}=${p[f]}`);
    log(`Links: ${links.length ? links.join(', ') : 'none set'}`);
    await printGallery(kit);
    log('');
    log(`Versions in upload order (${versions.length}${opts.only ? ` of ${allTargets.length}` : ''}); Modrinth lists the last upload first:`);
    printTable(versions, depNames);
    if (opts.json) {
        log('');
        log(JSON.stringify(versions.map(({ target: t, data }) => ({
            target: t.name, file: `dist/${t.jarName}`, sha1: t.sha1 ?? null, jar_declares_minecraft: t.declared ?? null,
            data: { ...data, changelog: `<${rel(path.join(KIT_DIR, p.changelog_file ?? 'changelog.md'))}, ${data.changelog.length} chars>` },
        })), null, 2));
    }
    log('');
    const gvCount = new Set(versions.flatMap((v) => v.data.game_versions)).size;
    const loaderSet = [...new Set(versions.flatMap((v) => v.data.loaders))];
    log(`Checked: ${versions.length} versions, ${versions.filter((v) => v.target.jarSize).length} jars (loader metadata, mod id/version, `
        + `declared Minecraft range, Fabric @Redirect encoding vs. fabricloader floor, jar older than its sources), ${gvCount} game versions and loaders ${loaderSet.join(', ')} against Modrinth tags `
        + `(${tags.gameVersions.size} game versions, ${tags.loaders.size} mod loaders), categories, dependencies.`);
    problems.print();
    if (problems.errors.length) {
        log(`\nDRY RUN FAILED: ${problems.errors.length} error(s).`);
        return 1;
    }
    log(`\nDRY RUN OK${problems.warnings.length ? ` (${problems.warnings.length} warning(s))` : ''}. Nothing was sent to Modrinth.`);
    return 0;
}

function projectFields(kit, body) {
    const p = kit.project;
    const out = {
        title: p.title,
        description: p.summary,
        body,
        categories: p.categories,
        additional_categories: p.additional_categories ?? [],
        license_id: p.license_id,
    };
    for (const f of LINK_FIELDS) if (p[f]) out[f] = p[f];
    return out;
}

async function createProject(kit) {
    TOKEN = requireEnv('MODRINTH_TOKEN', 'creating a project needs a personal access token with the "Create projects", "Read projects" and "Write projects" scopes');
    const problems = new Problems();
    await validateKit(kit, problems);
    validateProjectTags(kit, await fetchTags(), problems);
    abortOnProblems(problems);
    const p = kit.project;
    try {
        const existing = await api('GET', `/project/${encodeURIComponent(p.slug)}/check`, { token: TOKEN });
        throw new Fail(`the slug "${p.slug}" is already taken (project id ${existing?.id}). If it is yours, set `
            + `MODRINTH_PROJECT=${existing?.id} and use --sync-project / --publish instead.`);
    } catch (e) {
        if (!(e instanceof ApiError && e.status === 404)) throw e;
    }
    const data = {
        ...projectFields(kit, p.summary), // CDN URLs exist only after the draft and its images have been created.
        slug: p.slug,
        project_type: 'mod',
        client_side: p.client_side,
        server_side: p.server_side,
        is_draft: true,          // Modrinth: "please always mark this as true"; versions are uploaded afterwards
        initial_versions: [],    // required by the v2 route; deprecated, versions are uploaded with --publish
    };
    const form = new FormData();
    form.append('data', JSON.stringify(data));   // must be the first multipart field
    const ext = path.extname(kit.iconPath).slice(1).toLowerCase();
    form.append('icon', new Blob([kit.icon], { type: IMAGE_TYPES[ext] }), `icon.${ext}`);
    log(`Creating draft project "${p.title}" (slug ${p.slug}) on ${API_BASE} ...`);
    const created = await api('POST', '/project', { token: TOKEN, body: form });
    log('');
    log(`Created project id: ${created.id}  (slug ${created.slug}, status ${created.status})`);
    log(`Page: ${API_BASE.includes('staging') ? 'https://staging.modrinth.com' : 'https://modrinth.com'}/mod/${created.slug}`);
    log(`  To resume after any upload error: set MODRINTH_PROJECT=${created.id} and run --sync-project.`);
    await uploadGallery(kit, created);
    const remote = await getProject(created.id);
    checkSameProject(remote, kit);
    const body = resolveDescription(kit, remote);
    log('  updating the description with uploaded image URLs ...');
    await api('PATCH', `/project/${created.id}`, { token: TOKEN, json: { body } });
    log('');
    log('Next steps:');
    log(`  set MODRINTH_PROJECT=${created.id}`);
    log('  node scripts/modrinth-publish.mjs --publish        (versions)');
    return 0;
}

async function getProject(ref) {
    try {
        return await api('GET', `/project/${encodeURIComponent(ref)}`, { token: TOKEN });
    } catch (e) {
        if (e instanceof ApiError && e.status === 404) {
            throw new Fail(`project "${ref}" not found on ${API_BASE} (check MODRINTH_PROJECT, and that the token has the "Read projects" scope for a draft)`);
        }
        throw e;
    }
}

function checkSameProject(remote, kit) {
    if (remote.slug !== kit.project.slug) {
        throw new Fail(`MODRINTH_PROJECT points to "${remote.title}" (slug ${remote.slug}), but modrinth/project.json has slug `
            + `"${kit.project.slug}". Fix MODRINTH_PROJECT, or update the slug in project.json if you renamed the project.`);
    }
}

async function syncProject(kit) {
    TOKEN = requireEnv('MODRINTH_TOKEN', 'syncing needs a personal access token with the "Read projects" and "Write projects" scopes');
    const ref = requireEnv('MODRINTH_PROJECT', 'set it to the project id (printed by --create-project) or slug');
    const problems = new Problems();
    await validateKit(kit, problems);
    validateProjectTags(kit, await fetchTags(), problems);
    abortOnProblems(problems);

    const remote = await getProject(ref);
    checkSameProject(remote, kit);
    const id = remote.id;
    log(`Project "${remote.title}" (id ${id}, status ${remote.status})`);

    const ext = path.extname(kit.iconPath).slice(1).toLowerCase();
    log(`  uploading icon ${rel(kit.iconPath)} (${kib(kit.icon.length)}) ...`);
    await api('PATCH', `/project/${id}/icon?ext=${ext}`, { token: TOKEN, body: kit.icon, contentType: IMAGE_TYPES[ext] });

    const { added, skipped } = await uploadGallery(kit, remote);
    const refreshed = await getProject(id);
    checkSameProject(refreshed, kit);
    const body = resolveDescription(kit, refreshed);
    log('  updating title, summary, body, categories, license and links ...');
    await api('PATCH', `/project/${id}`, { token: TOKEN, json: projectFields(kit, body) });
    log('');
    log(`SYNC OK: project fields and icon updated; gallery: ${added} uploaded, ${skipped} already there.`);
    return 0;
}

async function uploadGallery(kit, remote) {
    const id = remote.id;
    const existingTitles = new Set((remote.gallery ?? []).map((g) => g.title).filter(Boolean));
    let added = 0, skipped = 0;
    for (const g of kit.gallery) {
        if (existingTitles.has(g.title)) {
            log(`  gallery: "${g.title}" already exists, skipped`);
            skipped++;
            continue;
        }
        const file = path.join(path.dirname(kit.galleryPath), g.file);
        const gext = path.extname(file).slice(1).toLowerCase();
        const q = new URLSearchParams({ ext: gext, featured: String(Boolean(g.featured)), title: g.title });
        if (g.description) q.set('description', g.description);
        if (Number.isInteger(g.ordering)) q.set('ordering', String(g.ordering));
        const bytes = await readFile(file);
        log(`  gallery: uploading "${g.title}" (${rel(file)}, ${kib(bytes.length)}) ...`);
        await api('POST', `/project/${id}/gallery?${q}`, { token: TOKEN, body: bytes, contentType: IMAGE_TYPES[gext] });
        added++;
    }
    return { added, skipped };
}

async function publish(kit, opts) {
    TOKEN = requireEnv('MODRINTH_TOKEN', 'publishing needs a personal access token with the "Read projects", "Read versions" and "Create versions" scopes');
    const ref = requireEnv('MODRINTH_PROJECT', 'set it to the project id (printed by --create-project) or slug');
    const problems = new Problems();
    const { versions } = await prepareRelease(kit, opts, problems);
    abortOnProblems(problems);

    const remote = await getProject(ref);
    checkSameProject(remote, kit);
    const existing = await api('GET', `/project/${remote.id}/version?include_changelog=false`, { token: TOKEN });
    const byNumber = new Map(existing.map((v) => [v.version_number, v]));
    log(`Project "${remote.title}" (id ${remote.id}, status ${remote.status}): ${existing.length} version(s) on Modrinth; `
        + `${versions.length} to check.`);
    let created = 0, skipped = 0;
    for (const [i, v] of versions.entries()) {
        const t = v.target;
        const tag = `[${String(i + 1).padStart(String(versions.length).length)}/${versions.length}] ${v.data.version_number}`;
        const have = byNumber.get(v.data.version_number);
        if (have) {
            const remoteSha1 = have.files?.find((f) => f.primary)?.hashes?.sha1 ?? have.files?.[0]?.hashes?.sha1;
            const note = remoteSha1 && remoteSha1 !== t.sha1 ? ' - NOTE: the jar on Modrinth differs from dist/ (not replaced)' : '';
            log(`${tag}: already on Modrinth (id ${have.id}), skipped${note}`);
            skipped++;
            continue;
        }
        const form = new FormData();
        form.append('data', JSON.stringify({ ...v.data, project_id: remote.id }));
        form.append('file', new Blob([await readFile(t.jarPath)], { type: 'application/java-archive' }), t.jarName);
        log(`${tag}: uploading ${t.jarName} (${kib(t.jarSize)}) ...`);
        let res;
        try {
            res = await api('POST', '/version', { token: TOKEN, body: form });
        } catch (e) {
            log(`${tag}: FAILED`);
            log(describeError(e));
            log(`\nStopped after ${created} new version(s). Fix the problem and run --publish again; versions already `
                + 'on Modrinth are skipped.');
            return 1;
        }
        log(`${tag}: created, version id ${res.id}`);
        created++;
    }
    log('');
    log(`PUBLISH OK: ${created} version(s) created, ${skipped} already on Modrinth.`);
    if (remote.status === 'draft') log('The project is still a draft: open it on Modrinth and press "Submit for review" when everything looks right.');
    return 0;
}

// ------------------------------------------------------------------------------------------------------------ main

async function main() {
    const opts = parseArgs(process.argv.slice(2));
    if (opts.help) {
        console.log(USAGE);
        return 0;
    }
    if (typeof fetch !== 'function' || typeof FormData !== 'function' || typeof Blob !== 'function') {
        throw new Fail(`Node ${process.versions.node} lacks global fetch/FormData/Blob; use Node 18 or newer`);
    }
    API_BASE = (process.env.MODRINTH_API_URL || (opts.staging ? API_STAGING : API_PRODUCTION)).replace(/\/+$/, '');
    const problems = new Problems();
    const kit = await loadKit(problems);
    USER_AGENT = `alext/typing-animation/${kit.modVersion} (release kit: scripts/modrinth-publish.mjs)`;
    switch (opts.mode) {
        case 'create-project': abortOnProblems(problems); return createProject(kit);
        case 'sync-project': abortOnProblems(problems); return syncProject(kit);
        case 'publish': abortOnProblems(problems); return publish(kit, opts);
        default: return dryRun(kit, opts, problems);   // also reports loadKit problems (missing files)
    }
}

main().then(
    (code) => { process.exitCode = code; },
    (e) => {
        console.error(redact(`\n${describeError(e)}`));
        process.exitCode = 1;
    },
);
