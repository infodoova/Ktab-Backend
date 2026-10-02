// Lists the voices in the ElevenLabs account so you can choose the approved trailer narration voices.
// Usage: node ops/trailer-agent/list-voices.mjs [--all]   (default: only voices verified for Arabic)
// Then put your picks in KTAB_TRAILER_VOICES as JSON: [{"id":"...","name":"...","suits":"politics, history"}]
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const envPath = path.resolve(fileURLToPath(import.meta.url), '../../../.env');
if (fs.existsSync(envPath)) {
  fs.readFileSync(envPath, 'utf8').split('\n').forEach(line => {
    const m = line.match(/^\s*([^#=\s][^=]*?)\s*=\s*(.*?)\s*$/);
    if (m) process.env[m[1]] = m[2].replace(/^['"]|['"]$/g, '');
  });
}
const key = process.env.ELEVENLABS_API_KEY;
if (!key) throw new Error('ELEVENLABS_API_KEY is not set');
const all = process.argv.includes('--all');

const voices = [];
let token = null;
do {
  const url = new URL('https://api.elevenlabs.io/v2/voices');
  url.searchParams.set('page_size', '100');
  if (token) url.searchParams.set('next_page_token', token);
  const res = await fetch(url, { headers: { 'xi-api-key': key } });
  if (!res.ok) throw new Error(`ElevenLabs ${res.status}: ${await res.text()}`);
  const body = await res.json();
  voices.push(...(body.voices ?? []));
  token = body.has_more ? body.next_page_token : null;
} while (token);

const isArabic = v => (v.verified_languages ?? []).some(l => (l.language ?? '').toLowerCase() === 'ar'
  || (l.locale ?? '').toLowerCase().startsWith('ar'));
const rows = voices.filter(v => all || isArabic(v));
console.log(`${rows.length} of ${voices.length} voices${all ? '' : ' verified for Arabic'}:\n`);
for (const v of rows) {
  const l = v.labels ?? {};
  console.log(`${v.voice_id}  ${v.name}  [${v.category ?? '?'}]  ${[l.gender, l.age, l.accent, l.descriptive, l.use_case].filter(Boolean).join(', ')}`);
  if (v.description) console.log(`    ${v.description.slice(0, 160)}`);
}
