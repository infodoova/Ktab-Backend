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
- **Flyway** runs database migrations and seeds automatically upon `ktab-app` startup.

---

## 📋 Prerequisites

1. A **Contabo VPS** running **Ubuntu 22.04 LTS** or **Ubuntu 24.04 LTS**.
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
- Sets up a **4GB swap space** (essential to prevent Out-Of-Memory errors during Java/Maven builds)
- Installs the official **Docker Engine** & **Docker Compose**
- Configures Docker log rotation (prevents server disk full errors)
- Configures **UFW Firewall** (allows only port 22 SSH, 80 HTTP, 443 HTTPS; blocks all other ports including 5432)
- Installs and enables **Fail2Ban** (protects SSH from brute-force attacks)

Run:
```bash
sudo bash scripts/vps_setup.sh
```

### Allow running Docker without `sudo` (Recommended)
After the setup script finishes, add your user to the docker group so you don't need `sudo` for every Docker command:
```bash
sudo usermod -aG docker $USER
newgrp docker
```

---

## Step 4: Configure Your Environment Variables

Create or edit your `.env.production` file on the VPS:

```bash
nano ~/Ktab-Backend/.env.production
```

Ensure the following critical variables are set:

```ini
# Database credentials (used by both ktab-db and ktab-app)
DB_NAME=ktab
DB_USER=ktab_user
DB_PASSWORD=choose_a_strong_password_here

# Application security
JWT_SECRET=your_high_entropy_256bit_base64_secret

# Mail (SMTP)
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password

# AI & external services
OPENAI_API_KEY=your_openai_key
ELEVENLABS_API_KEY=your_elevenlabs_key

# Cloudflare R2 storage
CF_ACCOUNT_ID=your_cf_account_id
CF_R2_ACCESS_KEY=your_r2_access_key
CF_R2_SECRET_KEY=your_r2_secret_key
CF_R2_BUCKET_NAME=ktab-bucket
CF_R2_PUBLIC_URL=https://your_public_r2_url.dev

# Upstash QStash
QSTASH_TOKEN=your_token
QSTASH_CURRENT_SIGNING_KEY=your_signing_key
QSTASH_OCR_WORKER_ENABLED=true
QSTASH_VERIFY_SIGNATURE=true
```

*(Press `Ctrl+O` then `Enter` to save, and `Ctrl+X` to exit nano)*

---

## Step 5: Deploy the Application

```bash
cd ~/Ktab-Backend
bash scripts/deploy.sh
```

This script will:
1. Build the multi-stage Docker image with Java 21 and fonts for PDFBox.
2. Launch `ktab-db` (PostgreSQL 17), verify its healthcheck, and mount `postgres_data`.
3. Launch `ktab-app`, execute Flyway migrations (`V1` through `V4` including all genres and subgenres).
4. Launch `ktab-nginx` reverse proxy.
5. Prune dangling Docker build artifacts.

> **Permission denied on Docker?** Run `sudo bash scripts/deploy.sh` or add your user to the docker group (see Step 3).

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
```bash
cd ~/Ktab-Backend
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
*Backups are saved to `~/Ktab-Backend/backups/` and automatically rotated (retaining the last 7 days).*

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
- Enables HTTP/2, modern TLS 1.2/1.3 ciphers, and 100M upload limits
- Sets up an automatic certificate renewal hook
- Restarts `ktab-nginx`

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

