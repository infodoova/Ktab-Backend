# 🚀 Contabo VPS Production Deployment Guide for Ktab Backend

This guide walks you through deploying the **Ktab Backend** to an online **Contabo VPS** (Ubuntu 22.04 or 24.04 LTS) using Docker Compose, PostgreSQL 17, and Nginx.

---

## 🏗️ Architecture Overview

```
                 Internet (Port 80 / 443)
                            │
                            ▼
              ┌───────────────────────────┐
              │   ktab-nginx (Port 80/443)│  <-- Reverse proxy & SSL termination
              └─────────────┬─────────────┘
                            │ (Docker network: ktab-network)
                            ▼
              ┌───────────────────────────┐
              │    ktab-app (Port 8080)   │  <-- Spring Boot 3 / Java 21
              └─────────────┬─────────────┘
                            │ (Internal network only)
                            ▼
              ┌───────────────────────────┐
              │    ktab-db (Port 5432)    │  <-- PostgreSQL 17 (Volume: postgres_data)
              └───────────────────────────┘
```

- **PostgreSQL port 5432** is **never** exposed to the public internet.
- **Nginx** handles incoming traffic on port 80/443, enforces `client_max_body_size 100M`, forwards proxy headers (`X-Real-IP`, `X-Forwarded-*`), and handles SSL termination.
- **Flyway** runs database migrations and seeds automatically upon `ktab-app` startup (currently `V1` through `V35`, see [What changed in the latest release](#-what-changed-in-the-latest-release)).
- **Client IP** reaches the app through the `X-Forwarded-For` and `X-Real-IP` headers that Nginx sets. The rate limiter relies on it (see [Rate limiting](#-rate-limiting)).

---

## 📋 Prerequisites

1. A **Contabo VPS** running **Ubuntu 22.04 LTS** or **Ubuntu 24.04 LTS**. This guide is sized for the **Cloud VPS 8 (2026)**: 8 CPU cores, 24 GB RAM, 300 GB disk (see [Server size and tuning](#-server-size-and-tuning)).
2. Your Contabo VPS **IP Address** and **Root/Admin Password** (found in your Contabo Customer Control Panel email).
3. A terminal / SSH client (Terminal on macOS/Linux, PowerShell or PuTTY on Windows).

---

## Step 1: Connect to Your Contabo VPS via SSH

Open your terminal or PowerShell and run:

```bash
ssh ktabadmin@<YOUR_CONTABO_VPS_IP>
```
*(Enter your password when prompted. Replace `ktabadmin` with your actual VPS username.)*

---

## Step 2: Clone or Copy Your Project to the VPS

### Option A: Via Git (Recommended)
```bash
# Clone the repository
git clone https://github.com/infodoova/Ktab-Backend.git

# Enter project directory
cd Ktab-Backend
```

### Option B: Via SCP from your local machine
```bash
# Run this on your local Windows PowerShell:
scp -r "c:\Users\PC\IdeaProjects\Ktab-Backend" ktabadmin@<YOUR_CONTABO_VPS_IP>:~/Ktab-Backend
```

---

## Step 3: Run the Automated VPS Setup Script

We provided an all-in-one setup script `scripts/vps_setup.sh`. It automatically:
- Updates system packages
- Sets up a **4GB swap space** (a safety net against Out-Of-Memory errors during Java/Maven builds; on the 24 GB server it is rarely used)
- Installs the official **Docker Engine** & **Docker Compose**
- Configures Docker log rotation (prevents server disk full errors)
- Configures **UFW Firewall** (allows only port 22 SSH, 80 HTTP, 443 HTTPS; blocks all other ports including 5432)
- Installs and enables **Fail2Ban** (protects SSH from brute-force attacks)

Run:
```bash
sudo bash scripts/vps_setup.sh
```

This is for a **new** server. On a server that already runs Ktab the script refuses to run (it restarts Docker, which restarts every container, and could change the firewall); to update such a server use `scripts/deploy.sh`. If an active firewall is already configured it keeps its rules and only makes sure ports 22, 80 and 443 are open, and Docker is restarted only when its log settings actually change. It also installs `openssl` (used to generate keys). `SWAP_GB=8 sudo -E bash scripts/vps_setup.sh` changes the swap size.

### Allow running Docker without `sudo` (Recommended)
After the setup script finishes, add your user to the docker group so you don't need `sudo` for every Docker command:
```bash
sudo usermod -aG docker $USER
newgrp docker
```

---

## Step 4: Configure Your Environment Variables

> **Already running an older version?** Do not recreate `.env.production`. Follow [Updating a server that already runs an older version](#-updating-a-server-that-already-runs-an-older-version) instead, which merges the new variables into the file you already have.

Create or edit your `.env.production` file on the VPS:

```bash
nano ~/Ktab-Backend/.env.production
```

Set the variables below. They are grouped by purpose. **Required** means the app does not start or the feature does not work without it; anything not listed keeps its default. Do not set `SPRING_DATASOURCE_*` or `SPRING_PROFILES_ACTIVE`: `docker-compose.yml` sets them from `DB_*` and for the production profile.

> **Old variable names.** Earlier versions of this guide listed `MAIL_USERNAME` and `MAIL_PASSWORD`. The app does not read them: email uses the `ZEPTOMAIL_*` variables in 4.3.

### 4.1 Database and security (required)

```ini
# Database credentials (used by both ktab-db and ktab-app)
DB_NAME=ktab
DB_USER=ktab_user
DB_PASSWORD=choose_a_strong_password_here

# Signs login tokens. The app refuses to start without it. Generate one with:  openssl rand -base64 48
JWT_SECRET=your_high_entropy_secret

# Optional, in milliseconds. Defaults: access token 3 hours, refresh token 24 hours.
#JWT_EXPIRATION_MS=10800000
#JWT_REFRESH_EXPIRATION_MS=86400000
```

### 4.2 Public addresses, CORS and cookies (required)

```ini
# Public address of the API, no trailing slash. QStash (OCR) and the trailer sign-in callback call back to it.
APP_BASE_URL=https://api.ktab.app

# Address of the website. Used to build the links in notification emails (book published or reviewed, storybook ready, trailer ready).
FRONTEND_URL=https://ktab.app

# Browser origins allowed to call the API, comma separated. The default only lists localhost, so the real site is blocked until you set this.
# Example values: use the real addresses of your frontend.
CORS_ALLOWED_ORIGINS=https://ktab-rho.vercel.app,https://ktab.app

# Login cookies are always Secure in production (HTTPS only). Lax works when the website and the API are on the same site
# (ktab.app and api.ktab.app). A website on another site (a vercel.app address, a tunnel) needs COOKIE_SAME_SITE=None,
# and Safari may still refuse those cookies; the same-site setup is the reliable one.
COOKIE_SAME_SITE=Lax
```

### 4.3 Email: ZeptoMail (required for verification and notification emails)

```ini
ZEPTOMAIL_ENABLED=true
ZEPTOMAIL_HOST=smtp.zeptomail.com
ZEPTOMAIL_PORT=587
ZEPTOMAIL_USERNAME=your_zeptomail_username
ZEPTOMAIL_PASSWORD=your_zeptomail_password
ZEPTOMAIL_FROM_EMAIL=noreply@ktab.app
ZEPTOMAIL_FROM_NAME=Ktab
```

### 4.4 File storage: Cloudflare R2 (required)

```ini
CF_ACCOUNT_ID=your_cf_account_id
CF_R2_ACCESS_KEY=your_r2_access_key
CF_R2_SECRET_KEY=your_r2_secret_key
CF_R2_BUCKET_NAME=ktab-bucket
CF_R2_PUBLIC_URL=https://your_public_r2_url.dev
```

Files are served through **signed links** that expire. The default lifetime is 600 minutes (10 hours) and is set by `cloudflare.r2.presigned.expiration-minutes` (environment variable `CLOUDFLARE_R2_PRESIGNED_EXPIRATION_MINUTES`). This also applies to the public cover-image lists, so a page that keeps them for longer than that must fetch them again.

### 4.5 AI providers

```ini
# OpenAI: text features, the storybook writer, talk-to-book, table-of-contents detection (required for those features)
OPENAI_API_KEY=your_openai_key

# Google (Gemini / Vertex): book images, the storybook illustrations and some text features.
GCP_PROJECT_ID=your_gcp_project_id
# The service-account key file as one line of base64:   base64 -w0 service-account.json
GCP_CREDENTIALS_BASE64=
# Alternative to the service account: a Gemini API key
#GEMINI_API_KEY=
```

> ⚠️ **If no Google credential is set the app still starts.** It uses a placeholder credential, and every Google call then fails later (storybook illustrations, image generation). Check the log after the first start for `Failed to load Google credentials`, and test an image-generating feature.

Models can be changed without a rebuild (defaults shown): `KTAB_AI_TEXT_PRIMARY=gpt-6-luna`, `KTAB_AI_TEXT_FALLBACK=gpt-4o`, `KTAB_AI_TEXT_EMBEDDING=text-embedding-3-small`, `KTAB_AI_TEXT_SEARCH=gemini-2.5-flash`, `KTAB_AI_TEXT_REASONING_EFFORT=low`, `KTAB_AI_IMAGE_PRIMARY=gemini-3.1-flash-image`, `KTAB_AI_IMAGE_FALLBACK=gemini-3.1-flash-lite-image`, `KTAB_AI_IMAGE_SIZE=1K`, `TOC_LLM_ENABLED=true`, `TOC_LLM_MODEL=gpt-5`. Talk-to-book has its own optional `KTAB_TALK_TO_BOOK_*` settings (model, temperature, `KTAB_TALK_TO_BOOK_SIMILARITY_THRESHOLD=0.82`).

### 4.6 Sign in with Google

```ini
GOOGLE_CLIENT_ID=your_google_oauth_client_id
GOOGLE_CLIENT_SECRET=your_google_oauth_client_secret
```
Both empty means Google sign-in is unavailable; email and password sign-in still work.

### 4.7 Audio: ElevenLabs, native text-to-speech and Studio

```ini
ELEVENLABS_API_KEY=your_elevenlabs_key
ELEVENLABS_VOICE_ID=
ELEVENLABS_AR_MALE_VOICE_ID=
KTAB_NATIVE_TTS_VOICE_ID=
# true: audiobooks go through ElevenLabs Studio. false (default): Ktab's own text-to-speech.
KTAB_STUDIO_ENABLED=false
```
Native text-to-speech limits (defaults): `KTAB_NATIVE_TTS_MODEL_ID=eleven_multilingual_v2`, `KTAB_NATIVE_TTS_MAX_CHARS=2500` per request, `KTAB_NATIVE_TTS_MAX_CHARS_PER_BOOK=1500000` per book. The Studio settings are the `ELEVENLABS_STUDIO_*` variables (model, quality preset, voices, page size); the defaults work.

### 4.8 OCR, ingestion and QStash

```ini
QSTASH_URL=https://qstash-eu-central-1.upstash.io
QSTASH_TOKEN=your_token
QSTASH_CURRENT_SIGNING_KEY=your_signing_key
QSTASH_NEXT_SIGNING_KEY=your_next_signing_key
QSTASH_OCR_WORKER_ENABLED=true
# Keep true: it rejects calls to the OCR callback that are not signed by QStash.
QSTASH_VERIFY_SIGNATURE=true
```

QStash calls `${APP_BASE_URL}/api/v1/internal/ocr/process`, so that address must be reachable from the internet over HTTPS (it is, through Nginx). `KTAB_OCR_ENABLED` is the on/off switch for running OCR; see "Feature settings" below.

Other optional settings (defaults): `KTAB_INGESTION_CLASSIFICATION_ENABLED=true`, `KTAB_INGESTION_SHADOW_MODE=false`, `KTAB_EXTRACTION_MAX_UPLOAD_BYTES=209715200` (200 MB).

> ⚠️ **Upload size.** The app accepts PDFs up to 200 MB for extraction, but Nginx is set to `client_max_body_size 100M` (in `nginx/conf.d/ktab.conf` **and** in `scripts/setup_ssl.sh`, which rewrites that file). A PDF between 100 MB and 200 MB is refused by Nginx with `413` before it reaches the app. Raise both Nginx values, or lower `KTAB_EXTRACTION_MAX_UPLOAD_BYTES`, so the two agree.

*(Press `Ctrl+O` then `Enter` to save, and `Ctrl+X` to exit nano)*

> Keep the file private: `chmod 600 ~/Ktab-Backend/.env.production`. Never commit it.

### Feature settings

Several features are **off unless you switch them on**. Add only the ones you want in production.

| Feature | Variable | Default | If it is off or missing |
|---|---|---|---|
| OCR | `KTAB_OCR_ENABLED` | `false` | The OCR start, restructure, harmonize, retry and resume endpoints answer **409** and launch nothing |
| Storybook | `KTAB_STORYBOOK_ENABLED` | `false` | No storybook endpoints and no storybook worker |
| Book trailers | `KTAB_TRAILER_ENABLED` | `false` | No trailer endpoints (`/trailers`, the reader trailer and the admin and public trailer-agent endpoints) |
| Studio audiobook | `KTAB_STUDIO_ENABLED` | `false` | The audiobook endpoints use Ktab's own text-to-speech instead of Studio |
| Early-access signup, public cover lists | none | always on | Nothing to configure |

#### Storybook (`KTAB_STORYBOOK_ENABLED=true`)

```ini
KTAB_STORYBOOK_ENABLED=true
# Encrypts uploaded child photos. Exactly 32 random bytes, base64. Generate it once with:  openssl rand -base64 32
# Keep a copy somewhere safe: without it a stored photo cannot be read again. Never commit it.
KTAB_STORYBOOK_PHOTO_KEY=
OPENAI_API_KEY=your_openai_key            # the story writer and checker use it
KTAB_STORYBOOK_MAX_BOOK_COST_USD=5.00     # a book that reaches this cost is stopped
KTAB_STORYBOOK_CREDITS_REQUIRED=true      # false only for internal testing: approvals then never touch credits
KTAB_STORYBOOK_WORKER_CONCURRENCY=4       # drawing and checking jobs at once
```

Optional tuning (these have sensible defaults; set them only to change behavior):

| Variable | Default | What it does |
|---|---|---|
| `KTAB_STORYBOOK_IMAGE_MAX_GENS` | `4` | Most attempts to draw one page before it is flagged for a person |
| `KTAB_STORYBOOK_IMAGE_REPEAT_FAILURE_LIMIT` | `2` | A page that fails the same checks this many times in a row is flagged at once instead of redrawn. `0` turns it off |
| `KTAB_STORYBOOK_IMAGE_WEB_MAX_SIDE_PX` | `1400` | Longest side of the smaller JPEG copy readers load (the full PNG is kept for the PDF) |
| `KTAB_STORYBOOK_IMAGE_AUTO_ACCEPT_LAYOUT_FAILURES` | `true` | When a page runs out of attempts and **only the scene check** failed (the right child, no stray text, sound anatomy, safe), the picture is accepted automatically so the book goes on to the PDF instead of waiting in `QA` for an admin. Any other failure (wrong child, text in the picture, bad anatomy, unsafe) still goes to a person. `false` flags every such page for an admin |
| `KTAB_STORYBOOK_IMAGE_MAX_CONCURRENT_CALLS` | `2` | Most picture requests in flight at once, whatever the number of workers. The image provider's per-minute quota runs out long before the server does, and a book asks for many pictures at once |
| `KTAB_STORYBOOK_IMAGE_RATE_LIMIT_RETRIES` | `4` | How many times one request is repeated after an HTTP 429 before the job goes back to the queue |
| `KTAB_STORYBOOK_IMAGE_RATE_LIMIT_BACKOFF` | `20s` | First wait after an HTTP 429; it doubles on each repeat, and every other picture request pauses during the wait too |
| `KTAB_STORYBOOK_STALL_RECOVERY_EVERY` | `5m` | How often books that stopped drawing are checked and restarted |
| `KTAB_STORYBOOK_STALL_RECOVERY_GRACE` | `3m` | A finished job is only restarted after it has been idle this long |

The image provider keys the app already uses for image generation are used here too. The Docker image already contains Chromium, which the storybook PDF needs, so nothing extra is installed on the VPS.

#### Book trailers (`KTAB_TRAILER_ENABLED=true`)

```ini
KTAB_TRAILER_ENABLED=true
ANTHROPIC_API_KEY=your_anthropic_key
ANTHROPIC_WEBHOOK_SIGNING_KEY=your_webhook_signing_key
KTAB_TRAILER_AGENT_ID=your_agent_id
KTAB_TRAILER_ENVIRONMENT_ID=your_environment_id
KTAB_TRAILER_VAULT_ID=your_vault_id
# Must be the public https address: Higgsfield sends the admin back to it after approving access.
KTAB_PUBLIC_BASE_URL=https://api.ktab.app
```

Limits you may want to change: `KTAB_TRAILER_PER_BOOK_PER_30_DAYS` (default `3`), `KTAB_TRAILER_MAX_CONCURRENT_RUNS` (default `4`) and `KTAB_TRAILER_BUDGET_CENTS` (default `2000`, per trailer).

The agent, its environment and its instructions are defined in `ops/trailer-agent/`. Create them in your Anthropic workspace from those files and put the resulting ids in the variables above.

The app image installs `ffmpeg` (which provides `ffprobe`): the backend checks every finished trailer video with its own `ffprobe`, separately from the agent's own check. After a deploy, confirm it is there:
```bash
docker exec ktab-app ffprobe -version | head -1
```
If you build a different image, install `ffmpeg` in it, or point `KTAB_TRAILER_FFPROBE_PATH` at a binary that exists in the container.

**One-time setup after the first deploy with trailers on:**
1. **Webhook.** In your Anthropic workspace, register this webhook address for the trailer agent and copy its signing key into `ANTHROPIC_WEBHOOK_SIGNING_KEY`:
   ```
   https://api.ktab.app/api/v1/public/trailer-agent/webhook
   ```
   The backend rejects webhook calls that are not signed with that key.
2. **Higgsfield.** Log in as an `ADMIN` user and call `POST https://api.ktab.app/api/v1/admin/trailer-agent/higgsfield/connect`. It returns an address; open it in a browser and approve access. Higgsfield then sends the browser back to `https://api.ktab.app/api/v1/public/trailer-agent/higgsfield/callback` (built from `KTAB_PUBLIC_BASE_URL`, or set `KTAB_TRAILER_CALLBACK_URL` to override it), which shows a confirmation page. Until this is done trailers cannot generate video.
3. **Try one.** Create a trailer for a test book (`POST /api/v1/trailers/books/{bookId}`) and watch `docker compose --env-file .env.production logs -f ktab-app` until it reaches `READY` or `NEEDS_REVIEW`.

#### OCR (`KTAB_OCR_ENABLED=true`)

Set it to `true` only when you want OCR to run (it costs money per page). While it is `false` the rest of the app works normally and the OCR endpoints answer 409 with "OCR is disabled (KTAB_OCR_ENABLED=false)".

---

## Step 5: Deploy the Application

```bash
cd ~/Ktab-Backend
bash scripts/deploy.sh
```

This script will:
1. **Check `.env.production`**: it exists, has Linux line endings, and `DB_PASSWORD` and `JWT_SECRET` are set (not empty, not the `__KEEP_EXISTING_VALUE_FROM_THE_SERVER__` placeholder). It also prints which features are switched on and warns about a missing `CORS_ALLOWED_ORIGINS` or `QSTASH_VERIFY_SIGNATURE` that is not `true`.
2. **Pull the latest code** (`git pull --rebase`). If the pull fails the deploy **stops**: it never builds old code without telling you.
3. **Back up the database** (when it is running) with `scripts/backup_db.sh`, and stop if the backup fails.
4. **Build the new image while the old version keeps serving** (Java 21, Chromium, ffmpeg, fonts), then start `ktab-db`, `ktab-app` and `ktab-nginx`, recreating only what changed.
5. **Wait up to 5 minutes for `ktab-app` to be healthy.** On the first start Flyway runs every pending migration (`V1` through `V35`, including all genres and subgenres). If the app is not healthy in time the script prints the last log lines and exits with an error.
6. Print the Flyway version the database is now at, prune dangling Docker images and show the container status.

Options: `--no-backup` (skip step 3, not recommended), `--no-pull` (deploy the files exactly as they are on the server). Slow-start servers can raise the wait with `DEPLOY_WAIT_RETRIES` (polls of `DEPLOY_WAIT_SLEEP` seconds, default 50 x 6).

> **Permission denied on Docker?** Run `sudo bash scripts/deploy.sh` or add your user to the docker group (see Step 3).

> **Downtime.** The image is built **before** anything is stopped, so the old version keeps serving during the build. The API is unavailable only while `ktab-app` is replaced and restarts, usually about a minute, longer when new migrations have to run. Storybook jobs that were running are picked up again after the restart.

> **Migrations and checksums.** Flyway records a checksum of every migration it has applied and refuses to start if an applied file is edited. Never edit a `V*.sql` file that has already run in production: add a new, higher-numbered one instead.

---

## Step 6: Verify and Test Your Deployment

Once `deploy.sh` finishes, verify that all containers are healthy:

```bash
docker compose --env-file .env.production ps
```

You should see:
- `ktab-db`: `Up (healthy)`
- `ktab-app`: `Up (healthy)`
- `ktab-nginx`: `Up`

### Check the database version
Every migration up to `V35` should be applied and successful:
```bash
docker exec ktab-db psql -U ktab_user -d ktab -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 5;"
```
The first row should be version `35`, `success = t`.

### Test the new public endpoints
```bash
# Top reviewed book covers (an array of image URLs, possibly empty):
curl "https://api.ktab.app/api/v1/public/books/top-reviewed/covers?limit=3"

# Covers of all books, first page:
curl "https://api.ktab.app/api/v1/public/books/cover-images?page=0&size=5"

# Early-access signup. This request is deliberately invalid (no role), so it answers 400 and stores nothing:
curl -i -X POST https://api.ktab.app/api/v1/public/early-access \
  -H "Content-Type: application/json" \
  -d '{"email":"check@example.com","fullName":"Check"}'
```
A `400` with an Arabic message on the last request proves the endpoint is reachable and validating. To also test a real signup, use a throwaway address and remove the row afterwards:
```bash
docker exec ktab-db psql -U ktab_user -d ktab -c "delete from tbl_early_access_signups where col_email = 'check@example.com';"
```

### Test Endpoints via Public IP:
In your browser or curl:
```bash
# Test application health (unauthenticated):
curl http://<YOUR_CONTABO_VPS_IP>/actuator/health

# Test via Nginx reverse proxy:
curl http://<YOUR_CONTABO_VPS_IP>/actuator/health
```

---

## 🛠️ Operational Management & Useful Commands

### 1. View Container Logs in Real-Time
```bash
# Spring Boot application logs:
docker compose --env-file .env.production logs -f ktab-app

# Nginx web server logs:
docker compose --env-file .env.production logs -f ktab-nginx

# PostgreSQL database logs:
docker compose --env-file .env.production logs -f ktab-db
```

### 2. Restart Containers
```bash
docker compose --env-file .env.production restart
```

### 3. Deploy Updates / Rebuild After Code Changes
When a release adds environment variables, merge them first with `scripts/merge_env.sh` (see [Updating a server that already runs an older version](#-updating-a-server-that-already-runs-an-older-version)). Take a backup first: new releases can add or change tables, and a migration cannot be undone by deploying the old code.
```bash
cd ~/Ktab-Backend
bash scripts/backup_db.sh
git pull origin master
bash scripts/deploy.sh
```

### 4. Database Backups
First, ensure the backups directory exists:
```bash
mkdir -p ~/Ktab-Backend/backups
```

To take an instant gzipped PostgreSQL backup on the VPS:
```bash
bash ~/Ktab-Backend/scripts/backup_db.sh
```
*Backups are saved to `~/Ktab-Backend/backups/` and automatically rotated (retaining the last 7 days; set `RETENTION_DAYS=14` to keep longer). Each backup is checked after it is written: a corrupt or empty one is deleted and the script fails, so a bad file is never kept as if it were good. `deploy.sh` and `restore_db.sh` run this script for you before they change anything.*

To schedule **daily automated backups at 3:00 AM**, add this to crontab (`crontab -e`):
```cron
0 3 * * * /bin/bash /home/ktabadmin/Ktab-Backend/scripts/backup_db.sh >> /var/log/ktab_backup.log 2>&1
```

### 5. Migrating / Restoring Local Database to Contabo VPS
1. **On your local Windows machine**, dump the database:
   ```powershell
   powershell -File scripts/backup_local_db.ps1
   ```
2. **Create the backups directory on the VPS** (first time only):
   ```bash
   ssh ktabadmin@<YOUR_CONTABO_VPS_IP> "mkdir -p ~/Ktab-Backend/backups"
   ```
3. **Transfer the dump file to the VPS**:
   ```powershell
   scp backups\ktab_backup.sql ktabadmin@<YOUR_CONTABO_VPS_IP>:~/Ktab-Backend/backups/
   ```
4. **On your Contabo VPS**, run the restore script:
   ```bash
   cd ~/Ktab-Backend
   bash scripts/restore_db.sh backups/ktab_backup.sql
   ```
   *The script restores all tables/data into `ktab-db` and automatically restarts `ktab-app` to refresh connections.*

---

## 🔄 Updating a server that already runs an older version

Use this when Ktab is **already live** on the VPS. The server has its own `.env.production` (with the real database password and JWT secret) and a database full of data. **Do not recreate the env file and do not copy a new one over it:** a different `DB_PASSWORD` stops the app from reaching the existing database, and a different `JWT_SECRET` logs every user out.

### 1. Find out where the server is now
```bash
cd ~/Ktab-Backend
git log -1 --format='%h %ad %s' --date=short        # the version the code is at
docker exec ktab-db psql -U ktab_user -d ktab -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 3;"
```
The second command shows the last migration that ran. Every migration after it (up to `V35`) will run on the next start. All of them should say `success = t`. If one says `f`, stop and fix that first.

### 2. Back up first
```bash
bash scripts/backup_db.sh
```
Copy the file in `backups/` off the VPS (for example with `scp` to your computer). A migration cannot be undone by deploying old code.

### 3. Merge the new variables into the live file
On your computer, `.env.production` was built from the local `.env`. Send it to the server under a **different name**:
```powershell
scp .env.production ktabadmin@<YOUR_CONTABO_VPS_IP>:~/Ktab-Backend/.env.production.new
```
On the VPS, merge it. The script (`scripts/merge_env.sh`) keeps every value already set in the live file, appends only the variables that are missing, backs the live file up first, and prints variable **names only**, never values:
```bash
bash scripts/merge_env.sh
```
Read the report:

| Section of the report | What to do |
|---|---|
| **ADDED** | New variables for features the older version did not have. Nothing to do, but check the values make sense for the server |
| **DIFFERENT, live value kept** | The server already has a value and the new file has another. Decide one by one. Keep the server's value for `DB_*`, `JWT_SECRET` (keeping it keeps users logged in) and the API keys. Look closely at `CORS_ALLOWED_ORIGINS` (must list your real frontends), `COOKIE_SAME_SITE`, `APP_BASE_URL`, `KTAB_PUBLIC_BASE_URL` and `FRONTEND_URL` |
| **ONLY IN THE LIVE FILE** | Variables the new version does not read, for example `MAIL_USERNAME` and `MAIL_PASSWORD` (email now uses `ZEPTOMAIL_*`). Harmless; remove them once email works |
| **MUST BE SET BY YOU** | Exits with an error. Set those variables in the live file, then run the merge again |

To switch a variable to the new file's value, name it with `--take`. Two that are worth taking if the server has the unsafe value:
```bash
bash scripts/merge_env.sh .env.production .env.production.new --take QSTASH_VERIFY_SIGNATURE,KTAB_STORYBOOK_CREDITS_REQUIRED
```
`QSTASH_VERIFY_SIGNATURE` should be `true` in production. `KTAB_STORYBOOK_MAX_BOOK_COST_USD` is the spend cap per storybook; set it to a value you are comfortable with. Afterwards delete `.env.production.new` from the VPS and from your computer: it holds live secrets.

### 4. Deploy
```bash
git pull origin master
bash scripts/deploy.sh
```
`deploy.sh` builds the new image first (a few minutes the first time, because the image now includes Chromium and ffmpeg) while the old version keeps serving, then replaces `ktab-app`. The API is **unavailable for about a minute, longer while new migrations run**. Pick a quiet time. The script already takes a database backup first (step 2 above is still worth doing by hand so you can copy the file off the server). Watch the migrations run:
```bash
docker compose --env-file .env.production logs -f ktab-app
```
`Successfully applied N migrations` means the database is up to date.

### 5. Check it
Follow Step 6 (Flyway version `35`, health, the public endpoints), then log in with an existing account to confirm that sessions and data are intact. If something goes wrong, see "Rolling back" below; the backup from step 2 is what makes that possible.

### Things that are different from a fresh install
- **Existing books and storybooks keep working.** Older storybook images keep serving the full PNG; only new images get the smaller copy.
- **The old `/api/...` paths for OCR, ingestion, structure and studio keep answering** (next to `/api/v1/...`), but their responses now use the standard `ApiResponse` envelope. Update anything that calls them (a dashboard, a script) at the same time.
- **New columns and tables only add.** Nothing existing is dropped or renamed, so the older code could still run on the new database if you had to roll the code back.
- **Features stay off until you switch them on.** If the old `.env.production` had no `KTAB_STORYBOOK_ENABLED`, `KTAB_TRAILER_ENABLED` or `KTAB_OCR_ENABLED`, the merge adds the values from your local `.env`, so check that those three say what you want on the server.

---

## 🆕 What changed in the latest release

| Area | Change | What you need to do |
|---|---|---|
| Database | Migrations `V31` to `V35` | Nothing: they run on startup. Take a backup first |
| Storybook | Each new page image also gets a smaller JPEG copy (`*.web.jpg` in R2) that readers load. Older images keep serving the full PNG | Nothing. Optionally set `KTAB_STORYBOOK_IMAGE_WEB_MAX_SIDE_PX` |
| Storybook | Books whose drawing stalled are restarted automatically every few minutes | Nothing |
| Storybook | A page that fails the same checks twice in a row is flagged instead of redrawn (saves image cost) | Optional: `KTAB_STORYBOOK_IMAGE_REPEAT_FAILURE_LIMIT` |
| Trailers | Reader endpoint `GET /api/v1/books/{id}/trailer`; the author and admin librarian can approve their own trailers; only a queued trailer can be cancelled (admins excepted). The app image now installs `ffmpeg` (for `ffprobe`) | Rebuild the image (`deploy.sh` does) and do the one-time trailer setup in Step 4 |
| Early access | New public signup `POST /api/v1/public/early-access` for readers, authors and admin librarians, stored in `tbl_early_access_signups` (`V32` to `V35`) | Nothing to configure. It is rate limited like login |
| Public catalog | `GET /api/v1/public/books/top-reviewed/covers` and `GET /api/v1/public/books/cover-images` return cover image URLs only | Nothing |
| OCR, ingestion, structure and studio endpoints | Now versioned: they answer at both `/api/v1/...` and the old `/api/...` path, and return the standard `ApiResponse` envelope. `OcrProgress` and `Quota` now require the `ADMIN` or `ADMIN_LIBRARIAN` role | Update any script, dashboard or monitor that reads the old raw responses |
| Storybook (new books only) | The character sheet now keeps the story's locked outfit (before, a photo-based sheet copied the clothes in the photo, so every page failed the check). The writer may no longer invent characters the book does not have, and scenes in a forest or by a stream no longer include city buildings. Editing a story no longer holds a database connection while the text is checked by the AI | Nothing. Books made earlier keep their old sheets and pages |
| Server image | The `Dockerfile` installs `ffmpeg`; `docker-compose.yml` is unchanged | `deploy.sh` rebuilds the image. See "Server size and tuning" for the suggested memory settings |
| Configuration | Email uses the `ZEPTOMAIL_*` variables, not `MAIL_*` (older versions of this guide were wrong). New optional variables are listed in Step 4 | Check `.env.production` against Step 4, especially `GCP_CREDENTIALS_BASE64`, `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` |
| Messages | New Arabic message keys for all of the above | Nothing |

---

## ⚙️ Background workers and schedules

These run inside `ktab-app` on their own; nothing to start. Knowing them helps when reading the logs.

| What | When | What it does |
|---|---|---|
| Storybook job worker | every 2 s (`KTAB_STORYBOOK_WORKER_POLL_DELAY`), up to `KTAB_STORYBOOK_WORKER_CONCURRENCY` jobs at once | Writes stories, draws and checks pages, builds the PDF. Only runs with `KTAB_STORYBOOK_ENABLED=true` |
| Storybook stall recovery | every 5 min (`KTAB_STORYBOOK_STALL_RECOVERY_EVERY`) | Restarts books that stopped drawing (a page with no image and no job, or an image nobody is going to check). Logs `no ... job was going to run ... started one` when it acts, and `stall check failed` if a book could not be checked |
| Storybook orphan sweep | daily at 03:30 | Deletes the R2 files of storybooks that no longer exist in the database |
| Trailer worker | every 15 s (`KTAB_TRAILER_WORKER_TICK`), up to `KTAB_TRAILER_MAX_CONCURRENT_RUNS` at once | Starts queued trailers and collects finished ones |
| Trailer reconciler | every 60 s (`KTAB_TRAILER_RECONCILE_EVERY`) | Checks running trailer sessions; a session that runs longer than `KTAB_TRAILER_MAX_SESSION_AGE` (default 2 h) is stopped |
| OCR | when QStash calls `/api/v1/internal/ocr/process` | Runs the OCR batch job (only with `KTAB_OCR_ENABLED=true`) |

Useful log searches:
```bash
docker compose --env-file .env.production logs --since 1h ktab-app | grep -E "is dead|stall check failed|Rate limit violation|no web copy made|Failed to load Google credentials"
```
`is dead` means a storybook job ran out of retries (the book then shows as failed and can be resumed), `no web copy made` means a smaller image copy could not be created (readers get the full-size image for that page).

---

## 📝 Early-access signups: day-to-day

People who sign up through `POST /api/v1/public/early-access` are stored in `tbl_early_access_signups`. There is no admin screen or endpoint for them yet, so use SQL. Roles are stored as the same codes `tbl_users` uses: **`20` reader, `10` author, `35` admin librarian**.

```bash
# How many signups per role, and how many already granted:
docker exec ktab-db psql -U ktab_user -d ktab -c "select col_role, count(*) as signups, count(*) filter (where col_early_access) as granted from tbl_early_access_signups group by col_role order by col_role;"

# The admin librarians, with the organization they asked to register:
docker exec ktab-db psql -U ktab_user -d ktab -c "select col_email, col_full_name, col_phone_number, col_org_name, col_org_city, col_org_country from tbl_early_access_signups where col_role = '35' order by created_at;"

# Export everything to a file on the VPS (then copy it with scp):
docker exec ktab-db psql -U ktab_user -d ktab -c "\copy (select * from tbl_early_access_signups order by created_at) to '/tmp/signups.csv' csv header"
docker cp ktab-db:/tmp/signups.csv ~/signups.csv

# Grant early access to one person:
docker exec ktab-db psql -U ktab_user -d ktab -c "update tbl_early_access_signups set col_early_access = true, updated_at = now(), version = version + 1 where lower(col_email) = lower('person@example.com');"
```

Notes:
- The export contains names, emails and phone numbers. Treat the file as personal data and delete it when finished.
- A signup has **no password** and cannot log in. When you later turn signups into users, `col_role` and the email can be copied as they are, the organization columns (`col_org_*`) map one-to-one to `tbl_library_organizations`, and the person has to set a password. Back up the database before doing that.
- A signup test row (for example the one from the verification step) can be removed with `delete from tbl_early_access_signups where col_email = '...';`.

---

## 🧯 Troubleshooting

| What you see | Likely cause | What to do |
|---|---|---|
| `ktab-app` exits at start with a Flyway checksum error | An already-applied `V*.sql` file was edited | Restore the original file from git. Never edit an applied migration; add a new one |
| `ktab-app` exits at start with a missing `JWT_SECRET` | The variable is not in `.env.production` | Set it (4.1) and redeploy |
| Website calls fail with a CORS error | `CORS_ALLOWED_ORIGINS` still has only the localhost defaults | Set it to the real website addresses (4.2) |
| Login works but the browser keeps asking to log in again | The website and the API are on different sites and the cookie is dropped | Serve both under the same site (`ktab.app` and `api.ktab.app`), or `COOKIE_SAME_SITE=None` (Safari may still refuse) |
| OCR endpoints answer **409** "OCR is disabled" | `KTAB_OCR_ENABLED` is `false` | Set it to `true` when you want OCR to run |
| PDF upload answers **413** from Nginx | The PDF is larger than Nginx's `client_max_body_size` (100 MB) | Run `sudo CLIENT_MAX_BODY_SIZE=220M bash scripts/setup_ssl.sh api.ktab.app <email>`, and keep `KTAB_EXTRACTION_MAX_UPLOAD_BYTES` in step |
| `deploy.sh` stops with "git pull failed" | Local changes on the server block the pull, most often `nginx/conf.d/ktab.conf` rewritten by `setup_ssl.sh` | Follow "Update an existing server's Nginx" in Nginx notes, or deploy the files as they are with `bash scripts/deploy.sh --no-pull` |
| `deploy.sh` stops with ".env.production has Windows line endings" or a missing `DB_PASSWORD` or `JWT_SECRET` | The file was edited on Windows, or a value is empty or still the placeholder | `sed -i 's/\r$//' .env.production`, and set the missing variable |
| Storybook illustrations or other Google AI calls fail, nothing else is wrong | No Google credential (`Failed to load Google credentials` in the log) | Set `GCP_CREDENTIALS_BASE64` or `GEMINI_API_KEY` (4.5) |
| Storybook drawing is very slow, and the log shows `Image generation failed with HTTP 429` followed by `is dead` and `book is FAILED; this step reruns when it is resumed` | The image provider's quota (HTTP 429) ran out because too many pictures were requested at once. A job that used up its attempts fails the book, and every other waiting job then stops until the book is resumed | Keep `KTAB_STORYBOOK_IMAGE_MAX_CONCURRENT_CALLS` at 2 (or lower it), ask Google for a higher image quota, and resume the book (`POST /api/v1/storybook/books/{id}/resume`). Check `tbl_storybook_ai_calls` and the log for the times of the 429s |
| Storybook stays on "illustrating" | A job died, or the book is waiting for a person | Wait one stall-recovery cycle (5 min). Then look for `is dead` in the log. Pages flagged by the checker wait for an admin in `GET /api/v1/admin/storybook/flagged-pages` |
| Trailer fails at the end with `ffprobe unavailable` | `ffprobe` is missing in the app container | Rebuild the image from the current `Dockerfile` (it installs `ffmpeg`) and check with `docker exec ktab-app ffprobe -version` |
| Trailer never starts producing video | Higgsfield was never connected | Do the one-time setup in Step 4, "Book trailers" |
| The streamed conclusion arrives all at once | Nginx is buffering the stream | Add the streaming block in "Nginx notes" |
| Everyone gets `429` on login or the early-access signup | The limiter sees one IP for all visitors (Cloudflare proxy in front, or Nginx bypassed) | See "Rate limiting" below |
| Cover or storybook images stop loading after a while | The signed link expired | Fetch the page or list again; the lifetime is 10 hours by default (4.4) |
| `ktab-app` is killed or restarts under load | Out of memory (the Java heap plus Chromium for storybook PDFs), most likely with no container memory limit | See "Server size and tuning" below |

---

## ⏪ Rolling back

- **Code only.** Migrations `V31` to `V35` only add tables, columns and constraints, so older code runs fine against the newer database. To go back: `git checkout <previous-commit-or-tag>` then `bash scripts/deploy.sh`. (`deploy.sh` runs `git pull --rebase` first when the branch tracks a remote; stay on a branch that points where you want, or deploy from a detached checkout by running the `docker compose --env-file .env.production up -d --build` line yourself.)
- **Code and data.** Restore the backup you took before the deploy: `bash scripts/restore_db.sh backups/<file>`. The script first takes a safety backup of what is there now (so the restore itself can be undone), stops `ktab-app` while the data is replaced, restores, and starts the app again. Everything written after the backup is lost, including new storybooks, trailers and early-access signups.
- Files in R2 are not part of the database backup. A restored database may point at files that were deleted in the meantime (the nightly orphan sweep deletes the files of storybooks that are not in the database).

---

## 🖥️ Server size and tuning

This guide is written for the **Contabo Cloud VPS 8 (2026)**:

| | |
|---|---|
| CPU | 8 cores |
| RAM | 24 GB |
| Disk | 300 GB |

That is more than the application needs for the database, the API, Nginx, storybook PDF rendering (Chromium) and trailer checks (`ffprobe`) running together. Nothing has to be reduced to fit. Three defaults are worth changing on a server of this size, because they were chosen for a small one.

### 1. Cap the memory of `ktab-app`

`docker-compose.yml` sets no memory limit, and the JVM is told to use up to 75% of the memory it can see (`-XX:MaxRAMPercentage=75` in the `Dockerfile`). On this server that is about **18 GB of heap**, which leaves only about 6 GB for PostgreSQL, Nginx, pgAdmin, Chromium, `ffprobe` and the operating system's file cache. The app does not need that much heap, and a heap that large can starve the database. Give the container a ceiling:

```yaml
  ktab-app:
    mem_limit: 14g        # heap up to about 10.5 GB, about 3.5 GB left for Chromium, ffprobe and the JVM itself
```

### 2. Give PostgreSQL real memory

The PostgreSQL image starts with settings meant for a laptop (`shared_buffers` of 128 MB). On 24 GB of RAM, add to the `ktab-db` service:

```yaml
  ktab-db:
    shm_size: "512mb"
    command:
      - postgres
      - -c
      - shared_buffers=4GB
      - -c
      - effective_cache_size=12GB
      - -c
      - work_mem=32MB
      - -c
      - maintenance_work_mem=512MB
```

The app keeps at most 20 database connections (`spring.datasource.hikari.maximum-pool-size=20`), well under PostgreSQL's default limit of 100. Apply the change with `bash scripts/deploy.sh`. These two compose changes are **suggestions: the repository's `docker-compose.yml` has not been changed**.

### 3. Use the extra cores for background work

The defaults assume a small machine:

| Setting | Default | On 8 cores |
|---|---|---|
| `KTAB_STORYBOOK_WORKER_CONCURRENCY` (storybook jobs at once) | `4` | `6` is safe. Most of the time is spent waiting for the image and AI providers, so the limit that matters is their rate limits, not the CPU |
| `KTAB_TRAILER_MAX_CONCURRENT_RUNS` (trailers at once) | `4` | Keep `4`: a trailer mostly waits for remote services and costs money per run |
| Web server threads | 200 (production profile) | Fine as it is |

### Disk (300 GB)

The books, images, PDFs and trailers are stored in Cloudflare R2, not on the VPS. What uses disk here:

- **Docker images and build cache.** The app image is large (it includes Chromium and ffmpeg). Check with `docker system df`; `deploy.sh` already prunes dangling images.
- **The database** (`postgres_data` volume) and its **backups** in `~/Ktab-Backend/backups/` (the last 7 days are kept). Check the size with `du -sh ~/Ktab-Backend/backups`.
- **Container logs**, capped by the compose settings (50 MB x 5 files for the app).

Check free space now and then with `df -h /`. Copy backups off the VPS: a backup on the same disk does not survive losing the server.

### Memory while it runs

Watch the numbers during a storybook PDF render or a trailer check: `docker stats --no-stream`. `ktab-app` staying well under its limit and no restarts (`docker compose --env-file .env.production ps`) mean the sizing is right. The 4 GB swap from `vps_setup.sh` stays as a safety net.

---

## 🚦 Rate limiting

Requests are limited per client IP: **login, registration and the public early-access signup 10 per minute**, AI endpoints 20 per minute, everything else 100 per minute. Over the limit the API answers `429` with a `Retry-After` header.

The limiter reads the real visitor IP from `X-Forwarded-For` / `X-Real-IP`, which the Nginx config sets. Two things would break that and make every visitor share one limit:
- Putting Cloudflare's **proxy (orange cloud)** in front of `api.ktab.app` without configuring Nginx to trust Cloudflare's real-IP header. Keep the record on **DNS only** (grey cloud), as in Step 7.1.
- Calling the app container directly, skipping Nginx.

---

## 🧩 Nginx notes

- **`scripts/setup_ssl.sh` rewrites `nginx/conf.d/ktab.conf`.** It writes the whole file from a template, so a manual edit to `ktab.conf` is lost the next time the script runs. Make every Nginx change in **both** places: `nginx/conf.d/ktab.conf` and the matching block in `scripts/setup_ssl.sh`.
- **Upload size** is `client_max_body_size 100M`. See the warning in Step 4.8 about PDFs between 100 MB and 200 MB.
- **Timeouts** are 300 s for reading and sending. A request that runs longer ends with `504`. The slow features (storybooks, trailers, OCR) run in the background, so this only matters for a long synchronous AI call.
- **Streamed answers.** `POST /api/v1/conclusion/stream` streams text to the browser (server-sent events). With `proxy_buffering on`, Nginx would hold the stream back and the browser would receive it in large lumps or all at the end. **The repository now handles this**: `nginx/conf.d/ktab.conf` and the HTTPS template in `scripts/setup_ssl.sh` both contain a `location = /api/v1/conclusion/stream` block with `proxy_buffering off`. On a server that already has HTTPS, apply it with the command in "Update an existing server's Nginx" below. For reference, the block is:
  ```nginx
  location = /api/v1/conclusion/stream {
      proxy_pass http://ktab-app:8080;
      proxy_http_version 1.1;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto $scheme;
      proxy_buffering off;
      proxy_cache off;
      proxy_read_timeout 300s;
  }
  ```
- **Update an existing server's Nginx.** On a server where `scripts/setup_ssl.sh` was run, `nginx/conf.d/ktab.conf` has been **rewritten on the server** (it holds the HTTPS configuration), which makes it differ from the version in git. That blocks `git pull` (and `deploy.sh` stops with a message saying so). Put the repository version back, pull, and write the HTTPS file again; the certificate is kept and there is no downtime for a certificate that is still valid:
  ```bash
  cd ~/Ktab-Backend
  git checkout -- nginx/conf.d/ktab.conf
  git pull --rebase
  sudo bash scripts/setup_ssl.sh api.ktab.app admin@ktab.app
  ```
  `setup_ssl.sh` keeps the old file as `nginx/ktab.conf.bak-<time>` and checks the new configuration with `nginx -t` before it finishes. To also allow PDFs larger than 100 MB, run it as `sudo CLIENT_MAX_BODY_SIZE=220M bash scripts/setup_ssl.sh api.ktab.app admin@ktab.app`.
- **Public endpoints that must stay reachable** from outside, with no login: `/api/v1/internal/ocr/process` (QStash), `/api/v1/public/trailer-agent/webhook` and `/api/v1/public/trailer-agent/higgsfield/callback` (trailers), and the `/api/v1/public/...` catalog and early-access endpoints. Do not add IP allow-lists or basic auth in front of them.

---

## 🔒 Hardening checklist

- **pgAdmin.** `docker-compose.yml` publishes `ktab-pgadmin` on port **5050** with the default login `admin@ktab.com` / `changeme` unless you set `PGADMIN_EMAIL` and `PGADMIN_PASSWORD`. Docker publishes ports **around the UFW firewall**, so port 5050 is reachable from the internet even though UFW does not list it. Either remove the service, bind it to `127.0.0.1:5050:80` and reach it over an SSH tunnel, or at least set a strong password.
- **PostgreSQL** is published only on `127.0.0.1:5432`. Keep it that way.
- **`.env.production`** holds every secret: `chmod 600`, keep it out of git, and keep an offline copy of `KTAB_STORYBOOK_PHOTO_KEY` and `JWT_SECRET`.
- **Backups.** Check that the cron job from "Database Backups" is running, and copy a backup off the VPS now and then. Deleting storybooks or signups cannot be undone without one.
- **Orphan files.** The nightly sweep (03:30) deletes the R2 files of storybooks that no longer exist in the database. That is intended, but it means deleting a book row also deletes its images permanently.

---

## 🌐 Attaching Subdomain (`api.ktab.app`) & Free SSL (Let's Encrypt)

To connect `api.ktab.app` to your Contabo VPS and enable HTTPS for your Vercel frontend:

### Step 7.1: Add DNS A Record in your Domain Registrar (Cloudflare, Namecheap, GoDaddy, etc.)
1. Log in to your DNS provider for **ktab.app**.
2. Add a new **A Record**:
   - **Type**: `A`
   - **Name / Host**: `api`
   - **IPv4 Address**: `31.220.94.53` (Your Contabo VPS IP)
   - **TTL**: Auto or 1–5 minutes
   - *(If using Cloudflare: Set Proxy status to **DNS only** (Grey Cloud) during initial certificate issuance).*
3. Verify DNS is pointing to the VPS (from your terminal):
   ```bash
   ping api.ktab.app
   # or
   nslookup api.ktab.app
   ```
   *(It must return `31.220.94.53` before continuing).*

---

### Step 7.2: Generate SSL & Enable HTTPS on VPS
Run this single command on your Contabo VPS:
```bash
sudo bash ~/Ktab-Backend/scripts/setup_ssl.sh api.ktab.app admin@ktab.app
```
*(Replace `admin@ktab.app` with your real email address for renewal notices).*

This automated script:
- Requests a verified Let's Encrypt certificate for `api.ktab.app`
- Configures Nginx with HTTP-to-HTTPS redirect (301)
- Enables HTTP/2, modern TLS 1.2/1.3 ciphers, and a 100M upload limit (`CLIENT_MAX_BODY_SIZE=220M sudo -E bash scripts/setup_ssl.sh ...` raises it)
- Adds the non-buffered block for the streamed conclusion endpoint
- Sets up an automatic certificate renewal hook
- Restarts `ktab-nginx` and checks the configuration with `nginx -t`
- Is safe to run again: a certificate with more than 30 days left is kept (no downtime), the previous `ktab.conf` is saved as `nginx/ktab.conf.bak-<time>`

---

### Step 7.3: Verify HTTPS Endpoint
```bash
curl -I https://api.ktab.app/actuator/health
```
You should see `HTTP/2 200` with `{"status":"UP"}`.

---

### Step 7.4: Connect Frontend (Vercel)
In your **Vercel Dashboard** $\rightarrow$ **ktab-rho** $\rightarrow$ **Settings** $\rightarrow$ **Environment Variables**:
- Update your API base URL to:
  ```
  https://api.ktab.app
  ```
- Trigger a redeployment on Vercel. Now your frontend talks to your backend securely over HTTPS with zero mixed-content issues!

