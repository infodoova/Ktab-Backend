import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

// Inline .env loader — no npm dependency needed
const envPath = path.resolve(fileURLToPath(import.meta.url), '../../../.env');
if (fs.existsSync(envPath)) {
  fs.readFileSync(envPath, 'utf8').split('\n').forEach(line => {
    const m = line.match(/^\s*([^#=\s][^=]*?)\s*=\s*(.*?)\s*$/);
    if (m) process.env[m[1]] = m[2].replace(/^['"]|['"]$/g, '');
  });
}

const ANTHROPIC_API_KEY = process.env.ANTHROPIC_API_KEY;
const AGENT_ID          = process.env.KTAB_TRAILER_AGENT_ID;
const API               = 'https://api.anthropic.com';

if (!ANTHROPIC_API_KEY) throw new Error('ANTHROPIC_API_KEY is not set');
if (!AGENT_ID)          throw new Error('KTAB_TRAILER_AGENT_ID is not set');

const headers = {
  'content-type':      'application/json',
  'x-api-key':         ANTHROPIC_API_KEY,
  'anthropic-version': '2023-06-01',
  'anthropic-beta':    'managed-agents-2026-04-01',
};

const opsDir = path.resolve(fileURLToPath(import.meta.url), '..');

async function updateAgent() {
  console.log(`Updating agent ${AGENT_ID}...`);

  const systemPrompt  = fs.readFileSync(path.join(opsDir, 'system-prompt.md'), 'utf8');
  const agentPayload  = JSON.parse(fs.readFileSync(path.join(opsDir, 'agent.json'), 'utf8'));
  agentPayload.system = systemPrompt;
  delete agentPayload.name;

  const res = await fetch(`${API}/v1/agents/${AGENT_ID}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(agentPayload),
  });

  if (!res.ok) throw new Error(`Update failed: ${res.status} ${await res.text()}`);

  const data = await res.json();
  console.log('Updated Agent :', data.id);
  console.log('Model         :', data.model?.id ?? data.model);
  console.log('New Version   :', data.version);
  return data;
}

updateAgent().catch(err => { console.error('Error:', err.message); process.exit(1); });
