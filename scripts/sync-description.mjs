#!/usr/bin/env node
// One public description for GitHub, Modrinth and CurseForge. No API calls.
import { readFile, writeFile } from 'node:fs/promises';
const root = new URL('../', import.meta.url);
const readText = async (url) => (await readFile(url, 'utf8')).replace(/\r\n?/g, '\n');
const source = await readText(new URL('docs/project-description.md', root));
const development = await readText(new URL('docs/readme-development.md', root));
const outputs = new Map([
    ['modrinth/description.md', source],
    ['curseforge/description.md', source],
    ['README.md', source + '\n---\n\n' + development],
]);
for (const [file, content] of outputs) {
    const url = new URL(file, root);
    if (process.argv.includes('--check')) {
        if (await readText(url) !== content) throw new Error(`${file} is out of sync`);
    } else await writeFile(url, content);
}
console.log(process.argv.includes('--check') ? 'Descriptions match.' : 'Descriptions synchronized.');
