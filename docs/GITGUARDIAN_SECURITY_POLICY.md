# GitGuardian Principles and Security Policies

This repository strictly enforces **GitGuardian Secret Detection and Prevention Standards** to safeguard all credentials, tokens, connection strings, and private keys from entering version control.

---

## 1. Core Principles (Zero Secrets in VCS)

1. **Zero Hardcoded Secrets**:
   - No API keys, passwords, bearer tokens, JWT secrets, database credentials, or private keys may ever be committed to git, including as fallback defaults in `application*.properties` or test files.
2. **Environment Variable Injection**:
   - All sensitive properties must resolve dynamically via environment variables (e.g. `${OPENAI_API_KEY}`, `${JWT_SECRET}`) without embedding production or development secrets in tracked files.
3. **Template-Only Policy (`.env.example`)**:
   - Only `.env.example` and `.env.schema` are tracked in version control, containing placeholder values (e.g., `your_api_key_here`). Actual `.env*` files are strictly ignored.
4. **Shift-Left Detection**:
   - Secrets must be caught and blocked on the developer's workstation before a commit is created, and verified independently in CI/CD before merging.

---

## 2. Multi-Layer Defense Architecture

### Layer 1: Git Ignore Matrix (`.gitignore`)
- Blocks all `.env*` files (except `.env.example` and `.env.schema`).
- Blocks certificates, keystores, and private keys (`*.pem`, `*.key`, `*.p12`, `*.jks`).
- Blocks service account credentials and JSON keys (`*service-account*.json`, `*credentials*.json`).
- Blocks heap dumps, core dumps, and application logs.

### Layer 2: GitGuardian Configuration (`.gitguardian.yaml`)
- Configures `ggshield` secret detector rules.
- Ignores safe paths (test fixtures, markdown documentation, build outputs).
- Sets `minimum-severity: medium` with `exit-zero: false` to reject contaminated changes.

### Layer 3: Local Pre-Commit Hook (`.githooks/pre-commit`)
- Configured via `git config core.hooksPath .githooks`.
- Executes `ggshield secret scan pre-commit` if installed.
- Includes an embedded high-entropy regex scanner fallback that inspects staged files for:
  * OpenAI API keys (`sk-...`)
  * Google API keys (`AIza...`)
  * Upstash tokens & HS256 keys (`eyJVc2VySU...`, `sig_...`)
  * AWS / Cloudflare credentials (`AKIA...`, 32/64-char hex keys)
  * Private keys (`-----BEGIN ... PRIVATE KEY-----`)
- Automatically aborts the commit if an active secret pattern is detected.

### Layer 4: CI/CD Pipeline Scan (`.github/workflows/gitguardian.yml`)
- Automated GitHub Actions workflow using `GitGuardian/ggshield-action@master`.
- Scans full commit history on every push and pull request to `master` and `main`.

---

## 3. Secret Rotation & Incident Response Policy

If a credential or secret is inadvertently pushed or detected by GitGuardian:

1. **Immediate Revocation**:
   - Immediately rotate/revoke the exposed credential in its provider dashboard (OpenAI, Upstash, Cloudflare, Gmail, Google Cloud).
   - Never assume a secret is safe simply by deleting the line in a subsequent commit.
2. **Local Environment Update**:
   - Update `.env.development` or `.env.production` with the new, rotated secret.
3. **History Purge (if needed)**:
   - Use `git filter-repo` or `BFG Repo-Cleaner` if complete history erasure of the commit is required.
