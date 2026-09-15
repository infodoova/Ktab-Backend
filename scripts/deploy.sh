#!/usr/bin/env bash
###############################################################################
# Ktab Backend - Production Deployment / Update Script
# Run from repository root: bash scripts/deploy.sh
###############################################################################

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "============================================================"
echo "  🚀 Deploying Ktab Backend to Production"
echo "============================================================"

# 1. Check for .env.production
if [ ! -f ".env.production" ]; then
  echo "[-] ERROR: .env.production file is missing in $ROOT_DIR!"
  echo "    Please create .env.production before deploying."
  exit 1
fi

# 2. Check if Docker and Docker Compose are installed
if ! command -v docker >/dev/null 2>&1; then
  echo "[-] ERROR: Docker is not installed. Please run: sudo bash scripts/vps_setup.sh first."
  exit 1
fi

# 3. Pull latest git changes if in a git repository and branch is tracked
if [ -d ".git" ]; then
  echo "[*] Checking for git updates..."
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD || echo "")
  if [ -n "$CURRENT_BRANCH" ] && [ "$CURRENT_BRANCH" != "HEAD" ]; then
    echo "    Pulling latest commits from git ($CURRENT_BRANCH)..."
    git pull --rebase || echo "    [!] Git pull skipped or encountered conflict; continuing with local files."
  fi
fi

# 4. Build and run containers
echo "[*] Building and starting containers with Docker Compose..."
docker compose --env-file .env.production down --remove-orphans || true
docker compose --env-file .env.production up -d --build

# 5. Wait for application to become healthy
echo "[*] Waiting for services to initialize..."
MAX_RETRIES=15
COUNTER=0

until [ "$COUNTER" -ge "$MAX_RETRIES" ]; do
  STATUS=$(docker inspect --format='{{json .State.Health.Status}}' ktab-app 2>/dev/null || echo "\"starting\"")
  if [ "$STATUS" = "\"healthy\"" ]; then
    echo "    ✅ ktab-app is HEALTHY!"
    break
  fi
  echo "    Waiting for ktab-app to report healthy... ($((COUNTER + 1))/$MAX_RETRIES)"
  sleep 6
  COUNTER=$((COUNTER + 1))
done

# 6. Cleanup old/dangling docker build cache and images
echo "[*] Cleaning up dangling Docker images..."
docker image prune -f >/dev/null 2>&1 || true

# 7. Print running status
echo "============================================================"
docker compose --env-file .env.production ps
echo "============================================================"
echo "  ✅ Ktab Backend is up and running!"
echo "  You can test it by visiting: http://$(curl -s https://api.ipify.org || echo '<YOUR_VPS_IP>')/actuator/health"
echo "============================================================"
