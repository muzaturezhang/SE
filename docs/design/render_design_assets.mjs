// Usage: bundled-node render_design_assets.mjs /absolute/path/to/node_modules
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readdir } from 'node:fs/promises';
if (!process.argv[2]) throw new Error('Pass the workspace dependency node_modules path');
const require = createRequire(join(resolve(process.argv[2]), '__design_renderer__.cjs'));
const sharp = require('sharp');
const out = dirname(fileURLToPath(import.meta.url));
for (const file of await readdir(out)) {
  if (file.endsWith('.svg')) await sharp(join(out,file), {density:200}).png().toFile(join(out,file.replace(/\.svg$/,'.png')));
}
process.stdout.write('Rendered design SVG files to PNG\n');
