// Isolated CLI tests: every HTTP request is mocked and every input lives in a temporary release kit.
// Run: node --test scripts/modrinth-publish.test.mjs
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mkdtemp, mkdir, readFile, writeFile, rm, copyFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const source = fileURLToPath(new URL('./modrinth-publish.mjs', import.meta.url));
const body = `<p align="center"><img src="icon.png" width="96" alt="Icon"></p>
![Typing](gallery/chat.gif)
![Styles](<gallery/styles.gif> "Style preview")
<img alt='Chat again' src='gallery/chat.gif' width='640'>
<IMG SRC=gallery/styles.gif width=640>
![Badge](https://example.com/badge.svg)
<img src="https://example.com/badge.svg?style=flat-square&amp;labelColor=21143F" alt="Badge">
`;

const mock = String.raw`
import { appendFileSync } from 'node:fs';
const scenario = process.env.TEST_SCENARIO;
const project = { id: 'TestId', slug: 'test-project', title: 'Test project', status: 'draft',
    icon_url: 'https://cdn.modrinth.com/icon-old.png', gallery: [] };
if (scenario === 'existing' || scenario === 'duplicate') {
    project.gallery.push({ title: 'Typing', url: 'https://cdn.modrinth.com/chat-existing.gif' });
}
if (scenario === 'duplicate') project.gallery.push({ title: 'Typing', url: 'https://cdn.modrinth.com/chat-other.gif' });
globalThis.fetch = async (url, options = {}) => {
    const parsed = new URL(url);
    const route = parsed.pathname.replace(/^\/v2/, '');
    const method = options.method ?? 'GET';
    const data = options.body instanceof FormData ? JSON.parse(options.body.get('data'))
        : options.headers?.['Content-Type'] === 'application/json' ? JSON.parse(options.body) : null;
    appendFileSync(process.env.TEST_LOG, JSON.stringify({ method, route, data }) + '\n');
    const reply = (data, status = 200) => new Response(JSON.stringify(data), { status });
    if (route === '/tag/category') return reply([{ name: 'utility', project_type: 'mod' }]);
    if (route === '/tag/license') return reply([{ short: 'MIT' }]);
    if (route === '/tag/game_version') return reply([{ version: '1.20.1', version_type: 'release' }]);
    if (route === '/tag/loader') return reply([{ name: 'fabric', supported_project_types: ['mod'] }]);
    if (parsed.hostname === 'piston-meta.mojang.com') return reply({ versions: [{ id: '1.20.1', type: 'release' }] });
    if (route.endsWith('/check')) return reply({ error: 'not_found' }, 404);
    if (method === 'POST' && route === '/project') return reply(project);
    if (method === 'PATCH' && route.endsWith('/icon')) {
        project.icon_url = 'https://cdn.modrinth.com/icon-new.png';
        return reply({});
    }
    if (method === 'POST' && route.endsWith('/gallery')) {
        if (scenario === 'upload-failure') return reply({ error: 'upload_failed' }, 400);
        const title = parsed.searchParams.get('title');
        project.gallery.push({ title, url: 'https://cdn.modrinth.com/' + title.toLowerCase() + '.gif' });
        return reply({});
    }
    if (method === 'GET' && route === '/project/TestId') {
        if (scenario === 'missing-url') project.icon_url = null;
        return reply(project);
    }
    if (method === 'PATCH' && route === '/project/TestId') return reply({});
    throw new Error('Unexpected request: ' + method + ' ' + url);
};
`;

async function run(t, { mode = '--sync-project', description = body, scenario = '' } = {}) {
    const root = await mkdtemp(path.join(tmpdir(), 'typing-modrinth-test-'));
    t.after(() => {
        assert.equal(path.dirname(root), path.resolve(tmpdir()), 'cleanup stays inside the temporary directory');
        assert.ok(path.basename(root).startsWith('typing-modrinth-test-'));
        return rm(root, { recursive: true, force: true });
    });
    await mkdir(path.join(root, 'scripts'));
    await mkdir(path.join(root, 'modrinth', 'gallery'), { recursive: true });
    await mkdir(path.join(root, 'targets', '1.20.1-fabric'), { recursive: true });
    await mkdir(path.join(root, 'dist'));
    const project = { slug: 'test-project', title: 'Test project', summary: 'Smooth typing in Minecraft.',
        categories: ['utility'], client_side: 'required', server_side: 'unsupported', environment: 'client_only',
        license_id: 'MIT', icon_file: 'icon.png' };
    await Promise.all([
        copyFile(source, path.join(root, 'scripts', 'modrinth-publish.mjs')),
        writeFile(path.join(root, 'scripts', 'target-common.gradle'), "def MOD_VERSION = '1.0.0'\n"),
        writeFile(path.join(root, 'targets', '1.20.1-fabric', 'target.properties'), 'mc.range=1.20.1\nloader=fabric\nmc.anchor=1.20.1\ngroup=fabric\n'),
        writeFile(path.join(root, 'modrinth', 'project.json'), JSON.stringify(project)),
        writeFile(path.join(root, 'modrinth', 'description.md'), description),
        writeFile(path.join(root, 'modrinth', 'changelog.md'), 'Release notes.'),
        writeFile(path.join(root, 'modrinth', 'icon.png'), Buffer.alloc(24)),
        writeFile(path.join(root, 'modrinth', 'gallery', 'chat.gif'), Buffer.alloc(24)),
        writeFile(path.join(root, 'modrinth', 'gallery', 'styles.gif'), Buffer.alloc(24)),
        writeFile(path.join(root, 'modrinth', 'gallery', 'gallery.json'), JSON.stringify([
            { file: 'chat.gif', title: 'Typing' }, { file: 'styles.gif', title: 'Styles' },
        ])),
        writeFile(path.join(root, 'mock.mjs'), mock),
        writeFile(path.join(root, 'requests.jsonl'), ''),
    ]);
    const result = spawnSync(process.execPath, ['--import', pathToFileURL(path.join(root, 'mock.mjs')).href,
        path.join(root, 'scripts', 'modrinth-publish.mjs'), ...(mode ? [mode] : [])], {
        cwd: root, encoding: 'utf8', timeout: 10000,
        env: { ...process.env, MODRINTH_TOKEN: 'test-token', MODRINTH_PROJECT: 'TestId',
            MODRINTH_API_URL: 'https://mock.invalid/v2', TEST_SCENARIO: scenario,
            TEST_LOG: path.join(root, 'requests.jsonl') },
    });
    if (result.error) throw result.error;
    const lines = (await readFile(path.join(root, 'requests.jsonl'), 'utf8')).trim();
    const requests = lines ? lines.split('\n').map(JSON.parse) : [];
    assert.equal(await readFile(path.join(root, 'modrinth', 'description.md'), 'utf8'), description, 'local source must stay unchanged');
    return { ...result, requests };
}

test('sync uploads media before patching the body, reuses existing gallery and keeps markup', async (t) => {
    const result = await run(t, { scenario: 'existing' });
    assert.equal(result.status, 0, result.stderr + result.stdout);
    const mutations = result.requests.filter((r) => r.method !== 'GET');
    assert.deepEqual(mutations.map((r) => r.route), ['/project/TestId/icon', '/project/TestId/gallery', '/project/TestId']);
    const patched = mutations.at(-1).data.body;
    assert.equal(patched, body.replaceAll('icon.png', 'https://cdn.modrinth.com/icon-new.png')
        .replaceAll('gallery/chat.gif', 'https://cdn.modrinth.com/chat-existing.gif')
        .replaceAll('gallery/styles.gif', 'https://cdn.modrinth.com/styles.gif'));
});

test('creation uses a safe draft body, uploads gallery and only then patches resolved images', async (t) => {
    const result = await run(t, { mode: '--create-project' });
    assert.equal(result.status, 0, result.stderr + result.stdout);
    const mutations = result.requests.filter((r) => r.method !== 'GET');
    assert.deepEqual(mutations.map((r) => r.route), ['/project', '/project/TestId/gallery', '/project/TestId/gallery', '/project/TestId']);
    assert.equal(mutations[0].data.body, 'Smooth typing in Minecraft.');
    assert.equal(mutations[0].data.is_draft, true);
    assert.deepEqual(mutations[0].data.initial_versions, []);
    assert.match(mutations.at(-1).data.body, /src="https:\/\/cdn.modrinth.com\/icon-old.png"/);
    assert.match(result.stdout, /Created project id: TestId/);
});

test('a failed upload leaves a recoverable draft and never sends the final description', async (t) => {
    const result = await run(t, { mode: '--create-project', scenario: 'upload-failure' });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /Created project id: TestId/);
    assert.match(result.stdout, /run --sync-project/);
    assert.equal(result.requests.some((r) => r.method === 'PATCH'), false);
});

for (const scenario of ['missing-url', 'duplicate']) {
    test(`sync fails without updating the body when CDN resolution fails: ${scenario}`, async (t) => {
        const result = await run(t, { scenario });
        assert.equal(result.status, 1);
        assert.match(result.stderr, /body was not updated/);
        assert.equal(result.requests.some((r) => r.method === 'PATCH' && r.route === '/project/TestId'), false);
    });
}

for (const description of ['![x](../secret.gif)', '![x](gallery/../icon.png)', '![x](gallery/unlisted.gif)',
    '![x](http://example.com/x.gif)', '<img src="C:/secret.png">', '<img src=gallery/unlisted.gif>',
    '<img src="icon.png" srcset="../secret.png 2x">', '![x][local]\n\n[local]: gallery/chat.gif']) {
    test(`reject unsupported image references before any mutation: ${description.split('\n')[0]}`, async (t) => {
        const result = await run(t, { description });
        assert.equal(result.status, 1);
        assert.equal(result.requests.some((r) => r.method !== 'GET'), false);
        assert.match(result.stdout, /description.md:/);
    });
}

test('the default dry run never mutates Modrinth, including when release jars are missing', async (t) => {
    const result = await run(t, { mode: '' });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /DRY RUN FAILED/);
    assert.equal(result.requests.some((r) => r.method !== 'GET'), false);
});
