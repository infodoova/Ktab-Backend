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
2. Your Contabo VPS **IP Address** and **Root Password** (found in your Contabo Customer Control Panel email).
3. A terminal / SSH client (Terminal on macOS/Linux, PowerShell or PuTTY on Windows).

---

## Step 1: Connect to Your Contabo VPS via SSH

Open your terminal or PowerShell and run:

```bash
ssh root@<YOUR_CONTABO_VPS_IP>
```
*(Enter your root password when prompted)*

---

## Step 2: Clone or Copy Your Project to the VPS

### Option A: Via Git (Recommended)
```bash
# Clone the repository
git clone https://github.com/your-username/Ktab-Backend.git

# Enter project directory
cd Ktab-Backend
```

### Option B: Via SCP from your local machine
```bash
# Run this on your local Windows PowerShell:
scp -r "c:\Users\PC\IdeaProjects\Ktab-Backend" root@<YOUR_CONTABO_VPS_IP>:/root/Ktab-Backend
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

---

## Step 4: Configure Your Environment Variables

Create or edit your `.env.production` file on the VPS:

```bash
nano .env.production
```

Ensure the following critical variables are set:

```ini
# Database credentials (used by both ktab-db and ktab-app)
DB_NAME=ktab
DB_USER=ktab_user
DB_PASSWORD=choose_a_strong_password_here

# Application security
JWT_SECRET=your_high_entropy_256bit_base64_secret

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

Run the automated deployment script:

```bash
bash scripts/deploy.sh
```

This script will:
1. Build the multi-stage Docker image with Java 21 and fonts for PDFBox.
2. Launch `ktab-db` (PostgreSQL 17), verify its healthcheck, and mount `postgres_data`.
3. Launch `ktab-app`, execute Flyway migrations (`V1` through `V4` including all genres and subgenres).
4. Launch `ktab-nginx` reverse proxy.
5. Prune dangling Docker build artifacts.

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
# Test Nginx status:
curl http://<YOUR_CONTABO_VPS_IP>/health

# Test Ktab Genres API:
curl http://<YOUR_CONTABO_VPS_IP>/api/v1/genres
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
git pull origin master
bash scripts/deploy.sh
```

### 4. Database Backups
To take an instant gzipped PostgreSQL backup on the VPS:
```bash
bash scripts/backup_db.sh
```
*Backups are saved to `./backups/` and automatically rotated (retaining the last 7 days).*

To schedule **daily automated backups at 3:00 AM**, add this to crontab (`crontab -e`):
```cron
0 3 * * * /bin/bash /root/Ktab-Backend/scripts/backup_db.sh >> /var/log/ktab_backup.log 2>&1
```

### 5. Migrating / Restoring Local Database to Contabo VPS
1. **On your local Windows machine**, dump the database:
   ```powershell
   powershell -File scripts/backup_local_db.ps1
   ```
2. **Transfer the dump file to the VPS**:
   ```powershell
   scp backups/ktab_backup.sql root@<YOUR_CONTABO_VPS_IP>:/root/Ktab-Backend/backups/
   ```
3. **On your Contabo VPS**, run the restore script:
   ```bash
   bash scripts/restore_db.sh backups/ktab_backup.sql
   ```
   *The script restores all tables/data into `ktab-db` and automatically restarts `ktab-app` to refresh connections.*

---

## 🌐 Attaching a Domain & Free SSL (Certbot)

When you are ready to link a domain (e.g., `api.ktab.app` or `backend.yourdomain.com`):

1. **Point your domain's DNS A-Record** to `<YOUR_CONTABO_VPS_IP>` in your domain registrar (e.g. Cloudflare, Namecheap, GoDaddy).
2. Wait 2–5 minutes for DNS propagation.
3. Run the automated SSL setup script on the VPS:
   ```bash
   sudo bash scripts/setup_ssl.sh api.yourdomain.com admin@yourdomain.com
   ```
4. Done! Your backend is now serving on `https://api.yourdomain.com` with automatic HTTP-to-HTTPS redirection and auto-renewing SSL certificates.
