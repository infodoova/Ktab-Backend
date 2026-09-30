import http from 'http';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { exec } from 'child_process';

// 1. Read .env for Anthropic API Key and Vault ID
const envPath = path.resolve('.env');
const envContent = fs.existsSync(envPath) ? fs.readFileSync(envPath, 'utf8') : '';

function getEnv(key, defaultVal = '') {
  const match = envContent.match(new RegExp(`^${key}=(.*)$`, 'm'));
  return (match ? match[1].trim() : process.env[key]) || defaultVal;
}

const ANTHROPIC_API_KEY = getEnv('ANTHROPIC_API_KEY');
const VAULT_ID = getEnv('KTAB_TRAILER_VAULT_ID');
const MCP_URL = 'https://mcp.higgsfield.ai/mcp';
const PORT = 8888;
const REDIRECT_URI = `http://localhost:${PORT}/callback`;

if (!ANTHROPIC_API_KEY || !VAULT_ID) {
  console.error("Missing ANTHROPIC_API_KEY or KTAB_TRAILER_VAULT_ID in .env");
  process.exit(1);
}

// 2. PKCE helpers
function generatePkce() {
  const verifier = crypto.randomBytes(48).toString('base64url');
  const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
  const state = crypto.randomBytes(24).toString('base64url');
  return { verifier, challenge, state };
}

async function main() {
  console.log("=== Higgsfield MCP OAuth Connect for Anthropic Vault ===");
  console.log(`Vault ID: ${VAULT_ID}`);

  // Fetch OAuth metadata
  console.log("Fetching Higgsfield OAuth metadata...");
  const metaRes = await fetch("https://mcp.higgsfield.ai/.well-known/oauth-authorization-server");
  if (!metaRes.ok) throw new Error("Failed to fetch Higgsfield OAuth metadata");
  const meta = await metaRes.json();

  // Register client dynamically
  console.log("Registering OAuth client with Higgsfield...");
  const regRes = await fetch(meta.registration_endpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      client_name: "Ktab trailer agent",
      redirect_uris: [REDIRECT_URI],
      grant_types: ["authorization_code", "refresh_token"],
      response_types: ["code"],
      token_endpoint_auth_method: "none",
      scope: "openid email offline_access"
    })
  });
  if (!regRes.ok) throw new Error(`Registration failed: ${regRes.status} ${await regRes.text()}`);
  const reg = await regRes.json();
  const clientId = reg.client_id;
  console.log(`Registered Client ID: ${clientId}`);

  // Prepare PKCE and authorize URL
  const { verifier, challenge, state } = generatePkce();
  const authUrl = new URL(meta.authorization_endpoint);
  authUrl.searchParams.set("response_type", "code");
  authUrl.searchParams.set("client_id", clientId);
  authUrl.searchParams.set("redirect_uri", REDIRECT_URI);
  authUrl.searchParams.set("scope", "openid email offline_access");
  authUrl.searchParams.set("state", state);
  authUrl.searchParams.set("code_challenge", challenge);
  authUrl.searchParams.set("code_challenge_method", "S256");
  authUrl.searchParams.set("resource", MCP_URL);

  const server = http.createServer(async (req, res) => {
    const reqUrl = new URL(req.url, `http://localhost:${PORT}`);
    if (reqUrl.pathname !== '/callback') {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not found');
      return;
    }

    const code = reqUrl.searchParams.get('code');
    const returnedState = reqUrl.searchParams.get('state');

    if (!code || returnedState !== state) {
      res.writeHead(400, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end('<h3>خطأ في التحقق من حالة OAuth (State mismatch). حاول مرة أخرى.</h3>');
      return;
    }

    try {
      console.log("\nReceived authorization code from Higgsfield. Exchanging for tokens...");
      const tokenForm = new URLSearchParams();
      tokenForm.set("grant_type", "authorization_code");
      tokenForm.set("code", code);
      tokenForm.set("redirect_uri", REDIRECT_URI);
      tokenForm.set("client_id", clientId);
      tokenForm.set("code_verifier", verifier);
      tokenForm.set("resource", MCP_URL);

      const tokenRes = await fetch(meta.token_endpoint, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: tokenForm.toString()
      });

      if (!tokenRes.ok) {
        throw new Error(`Token exchange failed: ${tokenRes.status} ${await tokenRes.text()}`);
      }

      const tokenData = await tokenRes.json();
      const accessToken = tokenData.access_token;
      const refreshToken = tokenData.refresh_token;
      const expiresIn = tokenData.expires_in || 3600;
      const expiresAt = new Date(Date.now() + expiresIn * 1000).toISOString();

      if (!refreshToken) {
        throw new Error("No refresh_token returned by Higgsfield. Need offline_access.");
      }

      console.log("Tokens acquired! Saving credential to Anthropic Vault...");

      // Check existing credentials in vault to archive any older active ones
      const existingRes = await fetch(`https://api.anthropic.com/v1/vaults/${VAULT_ID}/credentials`, {
        headers: {
          "x-api-key": ANTHROPIC_API_KEY,
          "anthropic-version": "2023-06-01",
          "anthropic-beta": "managed-agents-2026-04-01"
        }
      });
      if (existingRes.ok) {
        const existing = await existingRes.json();
        for (const c of (existing.data || [])) {
          if (c.auth?.mcp_server_url === MCP_URL && !c.archived_at) {
            console.log(`Archiving previous credential ${c.id}...`);
            await fetch(`https://api.anthropic.com/v1/vaults/${VAULT_ID}/credentials/${c.id}/archive`, {
              method: "POST",
              headers: {
                "x-api-key": ANTHROPIC_API_KEY,
                "anthropic-version": "2023-06-01",
                "anthropic-beta": "managed-agents-2026-04-01"
              }
            });
          }
        }
      }

      // Create new credential in vault
      const vaultPayload = {
        display_name: "Higgsfield MCP (Ktab)",
        auth: {
          type: "mcp_oauth",
          mcp_server_url: MCP_URL,
          access_token: accessToken,
          expires_at: expiresAt,
          refresh: {
            token_endpoint: meta.token_endpoint,
            client_id: clientId,
            refresh_token: refreshToken,
            resource: MCP_URL,
            token_endpoint_auth: {
              type: "none"
            }
          }
        }
      };

      const credRes = await fetch(`https://api.anthropic.com/v1/vaults/${VAULT_ID}/credentials`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-api-key": ANTHROPIC_API_KEY,
          "anthropic-version": "2023-06-01",
          "anthropic-beta": "managed-agents-2026-04-01"
        },
        body: JSON.stringify(vaultPayload)
      });

      if (!credRes.ok) {
        throw new Error(`Saving vault credential failed: ${credRes.status} ${await credRes.text()}`);
      }

      const credData = await credRes.json();
      console.log(`\nSUCCESS! Higgsfield credential saved in Anthropic Vault: ${credData.id}`);

      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(`
        <!DOCTYPE html>
        <html lang="ar" dir="rtl">
        <head>
          <meta charset="utf-8">
          <title>تم الربط بنجاح</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: #0f172a; color: #f8fafc; }
            .card { background: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 2.5rem; text-align: center; max-width: 480px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5); }
            h2 { color: #38bdf8; margin-top: 0; }
            p { color: #94a3b8; font-size: 1.1rem; line-height: 1.6; }
            .badge { display: inline-block; background: #059669; color: white; padding: 0.4rem 1rem; border-radius: 9999px; font-weight: 600; margin-bottom: 1rem; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="badge">&#10003; تم الاتصال بنجاح</div>
            <h2>تم ربط حساب Higgsfield بنظام Anthropic Vault</h2>
            <p>تم تخزين بيانات الاعتماد والـ Refresh Token بنجاح داخل الـ Vault (${VAULT_ID}).</p>
            <p style="font-size: 0.95rem; color: #cbd5e1;">يمكنك الآن إغلاق هذه الصفحة والعودة للـ Terminal / IDE.</p>
          </div>
        </body>
        </html>
      `);

      setTimeout(() => {
        console.log("OAuth flow complete. Exiting helper.");
        server.close();
        process.exit(0);
      }, 1500);

    } catch (err) {
      console.error("Error completing OAuth flow:", err);
      res.writeHead(500, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(`<h3>فشل الربط: ${err.message}</h3>`);
    }
  });

  server.listen(PORT, '127.0.0.1', () => {
    const fullAuthUrl = authUrl.toString();
    console.log(`\nTemporary OAuth receiver listening at http://localhost:${PORT}/callback`);
    console.log("\n========================================================");
    console.log("PLEASE OPEN THIS URL IN YOUR BROWSER TO AUTHORIZE HIGGSFIELD:");
    console.log(fullAuthUrl);
    console.log("========================================================\n");

    // Open browser automatically
    const cmd = process.platform === 'win32' ? `start "" "${fullAuthUrl}"` : `open "${fullAuthUrl}"`;
    exec(cmd, (err) => {
      if (err) console.log("(Could not auto-launch browser; please copy and paste the URL above).");
      else console.log("(Browser window opened automatically)");
    });
  });
}

main().catch(err => {
  console.error("Setup error:", err);
  process.exit(1);
});
